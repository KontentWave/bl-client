# ADR-003: Ringing-only call coordination and bounded caller lookup

**Recorded:** 2026-10-05 11:26:06 CEST (UTC+02:00).
**Updated:** 2026-10-05 11:48:25 CEST (UTC+02:00) - user approved source review, publication and PR merge; implementation unchanged.
**Status:** Accepted for CB-03/CB-04 implementation; source publication/PR merge approved, deployment and beta distribution not authorized.
**Scope:** Active native Kotlin/Compose client only.

## Context

At client `4f090dc82863c16a505e503051a874a398b3d802`, every RINGING broadcast constructed a processor, dismissed any warning and independently queried. OFFHOOK/IDLE were ignored. The ordinary HTTP connect/read/write limits were not a receiver-wide deadline.

Rechecked the official [PHONE_STATE contract](https://developer.android.com/reference/android/telephony/TelephonyManager#ACTION_PHONE_STATE_CHANGED): with READ_CALL_LOG and READ_PHONE_STATE, numbered and blank companion broadcasts arrive in unspecified order. A blank event is not evidence of hidden caller ID. [goAsync](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()) retains the broadcast execution allowance through PendingResult.finish(); Android documents a general 10-second allowance, sometimes longer for nonforeground broadcasts. We do not rely on the longer allowance. These are documented platform facts, not observed device behavior.

## Decision

One process-owned coordinator survives manifest receiver instances. Event handling, status writes and synchronous warning presentation are confined to main; lookup preparation and repository work run in a separate application-owned IO worker. The processor returns data only, without overlay or status side effects. Generation invalidation plus cancellation prevents stale workers from publishing.

The user selected **ringing-only warnings**: OFFHOOK cancels lookup/dismisses on answer; IDLE does the same on rejection/end. Both reset the observed ringing session. Equivalent normalized numbers deduplicate throughout that session, including after success, failure or deadline expiry. A different valid number supersedes the lookup, clears an old warning and receives its own budget. An invalid/numberless companion cannot replace a usable number or warning. Before a usable number arrives, record unavailable/invalid input without querying; the unavailable message explicitly permits a later numbered companion and says the call is not checked. No grace timer guesses that a caller is hidden.

The user approved a **5,000 ms end-to-end budget**, using elapsedRealtime from receipt of the usable numbered event. Dispatch delay, normalization, hash preparation, lazy repository/recovery construction, existing-key signing and Retrofit/OkHttp work consume the same budget. The coordinator awaits the worker under the remaining coroutine timeout and checks elapsed time again before publication. Nominal five-second headroom below the documented general receiver allowance is the justification, not a latency measurement or promise.

Each asynchronous lookup broadcast finishes its pending result via job completion on success, failure, cancellation or timeout. Immediate companion/invalid/transition broadcasts finish immediately. Cancellation interrupts signing through runInterruptible(IO), checks activity before sending/processing a response, and Retrofit's suspend-call cancellation cancels OkHttp. A separately owned worker lets the receiver stop awaiting without joining an uninterruptible OEM operation. That operation may physically outlast cancellation if the platform ignores interruption; it cannot publish, and activity checks prevent a subsequent request once it returns. No thread/key/device reset is used to force termination.

Timeout is QueryFailed with explicit **not checked** text, never NoMatch; it schedules no retry. Repeated events do not restart the budget. NoMatch requires a successful empty-feature response within the budget. Ordinary reports/manual queries receive no new deadline or timeout configuration. The shared query repository's interruptible preparation/activity checks are a narrowly necessary cancellation improvement; canonical signing, normalization, existing-key-only behavior and matching authorization-rejection recovery remain unchanged.

Warning presenter methods are synchronous main-thread operations so no call event can interleave between generation validation and window/status publication. Deferred accessibility announcements check the current attached view identity, avoiding announcements for a dismissed/replaced warning. Window failures are surfaced as overlay failures, rather than silently claimed as dismissal. Existing manual dismissal remains available.

## Limits and remaining gates

PHONE_STATE supplies no reliable per-call identity. Call waiting, overlapping multi-SIM calls, a same-number successor without an observed transition, delayed/out-of-order lifecycle transitions, and process death cannot be perfectly disambiguated. State is intentionally memory-only; process recreation does not restore/replay a lookup. An overlay depends on runtime permissions, window service and OEM support. The Activity still refreshes the diagnostic snapshot on its existing creation/resume/permission/manual-refresh hooks, not a new live subscription.

The timeout is cooperative scheduling: a blocked main thread, deep sleep, broadcast delivery delay, or uninterruptible native key/window/storage operation can delay actual execution/finish. A generation is authoritative only for observed events, not the carrier's undisclosed instantaneous state. Supported device/OS/carrier timing, overlay removal, process/background restrictions and actual goAsync behavior require separate disposable-device/physical validation. JVM fakes/local networking cannot certify them.

CB-05 diagnostic retention/backup, CB-09 OTP correction, backend CB-07 and country/length policy remain separate. No backend API/configuration/source change is required. BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED.
