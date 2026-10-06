# ADR-004: No persisted caller diagnostics; narrowly scoped legacy deletion

**Recorded:** 2026-10-05 13:19:46 CEST (UTC+02:00).
**Updated:** 2026-10-05 13:27:35 CEST (UTC+02:00) - local validation and release-test availability recorded; policy unchanged.
**Updated:** 2026-10-05 13:40:01 CEST (UTC+02:00) - source review/branch/commit/push/PR publication approved; exact-head merge approval pending.
**Updated:** 2026-10-06 11:55:21 CEST (UTC+02:00) - reconcile PR #4 merge/current source; caller-retention decision unchanged.
**Status:** Accepted; CB-05 source merged through PR #4 and retained in main `4b50be5009ddb52688cbdda0b361b47f65986e6d`. Deployment and beta distribution are separate unapproved gates.
**Scope:** Active native Kotlin/Compose client, debug and release alike.

## Context

At client `e5fd93f54a6911e5dc7bdccdc984cdb3451f957a`, `shield_live_status` stores raw/normalized callers, target hashes, matched labels, arbitrary query/overlay messages, timestamps and operational flags. Numbers, enumerable hashes and labels are sensitive. The manifest enables backup and excludes recovery only. Independently constructed receiver/Activity stores share disk rather than process memory.

MainActivity refreshes a remembered Home snapshot on creation/resume/permissions/manual refresh. Keeping detailed objects in that snapshot would outlive a ringing call even if the store cleared them. A live UI subscription is not needed to remove that exposure.

## Decision

Persist **no Shield status fields**, including no migration marker or operational timestamp, in either build type. Detailed caller data remains transient lookup/coordinator/active-warning data, not a last-call history. OFFHOOK/IDLE cancel/invalidate workers, dismiss the warning and release coordinator caller identity, invalid-input deduplication value and completed job references. A different usable caller supersedes the prior generation. Garbage collection/cancellation is not secure memory erasure; an uninterruptible OEM worker may physically outlast cancellation, as described in ADR-003.

One process-owned, synchronized `ShieldLiveStatusMemory` is shared by every `ShieldLiveStatusStore`, regardless of receiver/Activity construction order. It retains **only redacted process-memory operational state**: stage, retryable flag, overlay enum and update time. Publication discards raw/normalized numbers, hash, features and arbitrary error/overlay strings. Home renders fixed, stage-specific messages, plus cleanup health, never caller-specific text. Existing refresh hooks remain unchanged; explicitly label this a snapshot rather than a live feed. Stale snapshots can describe a prior operational outcome, but never retain caller details.

Active-call window content keeps the existing number/hash/matched-label warning; its guarded accessibility announcement keeps matched labels. No query, warning or checked result is recovered/replayed in a new process. Fresh memory starts Idle/not checked even if legacy cleanup fails. Failed/unavailable/deadline outcomes remain distinct from successful empty-feature NoMatch.

### Legacy migration

On first store construction (including receiver-before-Activity), synchronously call Android API 24+ `Context.deleteSharedPreferences("shield_live_status")`, deleting the dedicated XML and its backup file through the platform API. Minimum SDK is 24; no dependency/config change is needed. Never decode legacy preference values or enumerate other app storage. Deleting the complete **dedicated diagnostics file** also removes stale/unknown diagnostic keys, not unrelated preferences.

Trust only a successful deletion result; handle SecurityException as explicit failure without copying exception text. Failed cleanup is logged with a fixed message and shown separately in Home. It cannot change the lookup outcome or enable a legacy read path. Retry on subsequent store construction/read/record hooks; no background retry loop, API replay or timed work is added. A process-local success latch avoids repeated disk work once confirmed, but is not persisted: a fresh process attempts deletion again, including old restored preferences.

The same monitor serializes cleanup, reads and redacted publication. Independent initialization never resets a newer status. No current writer writes diagnostic preferences, so status updates cannot repersist sensitive fields. Failed platform cleanup may leave data on disk; never claim deletion succeeded or forensic erasure. Android preference deletion may perform synchronous filesystem work and remains an OEM/main-thread scheduling risk inside the caller budget.

This migration does not access recovery preferences, key aliases, bindings, challenges, cooldowns, databases or unrelated files. It is application upgrade code, not authorization to operate on an existing personal installation.

### Backup and logging

Keep the manifest's explicit legacy/full-backup and API 31+ extraction-rule hooks. Exclude both `shield_live_status.xml` and `shield_live_status.xml.bak` under sharedpref in legacy cloud backup, modern cloud backup and device transfer, including residual upgrade data after failed cleanup. Keep `device_binding_recovery.xml` exclusions intact. There is no replacement persistent diagnostics store to exclude.

Disable the HTTP logging interceptor specifically for blacklist query clients in debug and release: even BASIC logging can copy arbitrary response reasons/transport exception text. Manual queries share this client; their request/timeouts/retry semantics are unchanged. Presenter and cleanup logging use fixed messages only. Verification-specific debug logs and auth/report HTTP policy are outside this caller-diagnostic slice.

## Evidence boundaries

JVM fakes can prove redaction, ownership, synchronization, deletion-result handling, fresh-memory semantics and no command replay. Source/XML inspection can prove configured exclusions; local HTTP mocks can exercise the actual query factory logging policy. Compiled fake-backed Compose fixtures can cover the intended Home semantics only when executed separately.

These are not actual Android preference deletion/cache/filesystem failure, backup/restore/device-transfer, Android Keystore, OS process-kill, telephony/carrier/OEM or physical-device/reboot evidence. No retroactive deletion of previously uploaded backups, forensic filesystem erasure, or external accessibility-service retention is promised. An upgrade cannot clean storage before its first Activity/receiver initialization; exclusions protect eligibility, not proof of a completed backup drill.

Final local validation passed 133 debug JVM tests / 20 suites with PHP interoperability enabled and compiled both debug APK targets. Release Kotlin/resource compilation and release lint passed; compiled resource inspection confirms exclusion wiring. Only debug JVM tests are configured; attempted release JVM execution failed at task discovery, not within tests. The new fake-backed Home fixture was compiled, not executed. Exact commands/totals and limitations are recorded in the client-owned handoff.

CB-09 and backend CB-07 source are now merged per the later handoffs; country/length policy and live/device validation stay separate. BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED. Separate linked-handoff publication is not authorized by this local qualification. This is not overall closed-beta readiness.
