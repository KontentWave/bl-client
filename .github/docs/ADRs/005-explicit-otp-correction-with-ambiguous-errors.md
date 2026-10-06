# ADR-005: Explicit OTP correction under an ambiguous verification contract

**Recorded:** 2026-10-05 14:06:15 CEST (UTC+02:00).
**Updated:** 2026-10-05 14:23:59 CEST (UTC+02:00) - complete source review and client branch/commit/push/PR publication approved; implementation unchanged.
**Updated:** 2026-10-06 11:55:21 CEST (UTC+02:00) - reconcile separately approved PR #5 merge and current exact source tree; OTP decision unchanged.
**Status:** Accepted; reviewed source `67d7a4509fb314fd1ec0ac47ddb4a72fc22472e5` merged through PR #5 as main `4b50be5009ddb52688cbdda0b361b47f65986e6d`. Deployment and distribution are not authorized.
**Scope:** Active native Kotlin/Compose client. Backend source/configuration/server-owned documents remain read-only.

## Context

At client `3e9abde5b297c36d7206e439bb099b93c8729250`, `otp_invalid_or_expired` locks the challenge after one typo. Read-only backend inspection at `6c96c1d4cee869a7cfbe878c24def13206715181` confirms:

- `OtpChallenge.hasValidOtp` combines hash mismatch and strictly-after-expiry in the same rejection. Both leave the row; `meta.retryable:false` applies to the rejected request, not an authoritative challenge-lifecycle discriminator.
- `AuthVerificationService` reserves attempts outside its transaction, before OTP/signature checks. Success binds and deletes atomically; missing/replaced/consumed challenge IDs return `challenge_not_found`.
- Invalid signatures reject verification and leave the row. Existing client signature blocking is a conservative local policy, not proof of server consumption.
- The configurable default challenge budget is five attempts until expiry. Exhaustion, IP middleware and lock contention all use generic HTTP 429 `rate_limited`; neither scope nor remaining attempts is returned.
- Resend reserves budgets and replaces the challenge before SMS dispatch. A failed/ambiguous send cannot prove an older locally retained ID remains usable.

The inspected isolated backend tests assert expired/invalid-signature row retention, unknown challenges, and five wrong attempts followed by a throttled correct attempt, including database-cache accounting. Those tests were read, not executed in this slice. Source and published contracts are not proof of the hosted revision, cache configuration or SMS delivery.

## Decision

Remove only the combined OTP code from client challenge-blocking classification. Preserve ID, masked recipient, expiry and editable OTP in the activity-owned ViewModel. Keep server `retryable` metadata unchanged: correction is a new explicit user command, never an automatic retry of the rejected request. Tell the user the OTP may be incorrect or expired, that server status is unconfirmed and that each submission may consume a server attempt. Keep this guidance separate from transient field/general errors so editing cannot erase the uncertainty.

Each explicit verification checks the parseable recorded expiry using the existing strictly-after boundary. After expiry, or with missing/unreadable expiry, block locally with truthful no-request guidance. This is a client safety bound, not proof of server validity or clock synchronization; the server always decides. No new timer automatically submits or renews a challenge, and no local attempt counter guesses the configurable server budget.

Preserve well-formed `challenge_not_found` and `signature_invalid` blocking and existing signing/key-preparation failure blocking. OTP editing does not unlock these. Do not infer any new terminal discriminator. Temporary/network/unknown/malformed failures retain local challenge state with unknown-status guidance. Generic 429 retains it and applies the existing monotonic cooldown to both verification and initiation. After cooldown expiry, verification still checks expiry and requires a fresh explicit action.

Freeze OTP input during initiation/verification so an in-flight request has one stable captured code. Accept only ASCII OTP digits, bounded to six. Preserve synchronous duplicate/in-flight guards; successful verification cannot be resubmitted through the ViewModel. New challenge/resend stays a separate explicit command, retaining old state until initiation succeeds. Success replaces ID/recipient/expiry, clears OTP/guidance and unlocks; failure cannot bypass an existing block or cooldown.

No OTP, challenge, recipient, guidance or cooldown is persisted. Configuration/navigation retention uses the existing ViewModelStore; a fresh process loses pending state and replays nothing. Recovery continues to store only approved backend/key-bound historical metadata after successful explicit verification. Signature canonicalization, hardware-key policy, authorization rejection, caller lifecycle/privacy and backup exclusions are unchanged.

No material new product/retention policy or backend discriminator is needed for this bounded manual correction. A future authoritative expiry/exhaustion/remaining-attempt UX requires backend-owner contract coordination; never infer it from delay, message text, `retryable:false` or generic 429.

## Evidence and limits

Targeted deterministic JVM/fake/local HTTP tests passed 69 tests / five suites. Fresh full native JVM regression passed 147 tests / 21 suites, zero failures/errors/skips, including existing opt-in actual PHP normalization/signature services (eight report/eight query vectors). These cover corrected same-ID submission, explicit command counts, expiry/boundary/malformed expiry, terminal/malformed/network errors, cooldown gating, in-flight edits, explicit resend, retained owners/fresh-state loss and key-bound success.

Debug application and instrumentation APKs and release Kotlin/resources compiled. Debug lint has zero errors / 35 warnings; release lint has zero errors / 31 warnings. New Compose accessibility/terminal-state and blank-Activity correction/navigation/recreation fixtures compiled only, not executed. JVM retained-owner/fresh-model tests are not device recreation or OS process-death evidence. No new dependency, device operation, real key/binding operation, backend test/database operation, hosted request or live SMS occurred.

Backend CB-07 source is merged per the October 6 handoff; its hosted acceptance remains separate. Signing/distribution, country/length policy, actual Keystore/device/OEM/backup/transfer and hosted/live onboarding remain open. BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED. This decision does not establish overall closed-beta readiness.
