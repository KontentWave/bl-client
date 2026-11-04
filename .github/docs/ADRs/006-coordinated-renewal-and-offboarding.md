# ADR-006: Coordinated device authorization renewal and offboarding

**Recorded:** 2026-11-03.
**Status:** Accepted by explicit owner decisions on November 3; implemented and
locally tested in both checkouts. This is not deployed or device-qualified behavior.
**Scope:** Native Kotlin/Compose client and coordinated Laravel lifecycle.
**Baselines:** client `6aae2cb499c251326c47c10a24e5530a63e254b1`;
server local/GitHub `a8d2d9a91ac11d55320df434fce68b82470d4de9`.

## Context and agreed requirements

The inspected pre-feature server deleted the ad-bearing OTP challenge after verification,
retaining only phone, key and `verified_at` in the binding. No renewal endpoint,
ad lifecycle or age-based authorization existed. Client recovery is a local
navigation hint, not a server session. CB-11 is complete and separate.

The requested product invariants are explicit manual ad/phone input, received
SMS OTP and signed device-key verification for enrollment; quiet renewal every
24 hours while the enrolled ad remains active; overdue catch-up at an eligible
execution opportunity; confirmed deactivation offboards; subsequent enrollment
requires explicit user action. Keys, app data and community contributions stay.
Quiet renewal does not authorize daily SMS or automatic OTP completion.

No deployed requests, scraping/SMS, migrations against deployed data,
configuration/hosting changes, APK changes/install or Git publication are
authorized. Both local implementation workspaces are available; the server is
accessible through the client's ignored `backend-docs` symlink.

## Agreed product lifecycle

| Concern | Agreed decision |
| --- | --- |
| Renewal transport | Dedicated signed HTTPS operation using the existing device key, with no SMS/OTP, initiation replay or key mutation. |
| Execution roles | Device initiates due renewal; server owns protected portal inspection and lifecycle authority. No scheduled server crawling. An offline device is checked when it returns, not while it is absent. |
| Interval | Initial due time is successful manual verification plus 86,400 seconds of server UTC elapsed time. Each successful due renewal starts the next interval at its server commit time. Due at equality, overdue strictly after; missed intervals collapse into one renewal, not a backlog. Ordinary queries/reports never advance these times. |
| Overdue access | Retain query/report access until confirmed offboarding; overdue status is not revocation. This is an availability-first 24-hour check cadence, not a bounded authorization lease. There is no age/grace access boundary; a device that never renews is not automatically suspended. |
| Confirmed inactivity | One successfully fetched recognized portal-specific explicit disabled/expired/deleted notice for the enrolled ad suffices. Initially support the existing Amaterky disabled marker and narrowly tested expiry notice; deletion only with supported definitive evidence. Missing contact, bare HTTP 404/410, redirected/challenge page or unrecognized HTML is unknown, not confirmation. Unrecognized portal states remain unknown. |
| Contact | Match the originally OTP-verified canonical phone against supported current contact candidates, not merely the first candidate's order. A confidently parsed active ad with a nonempty contact set excluding it requires explicit re-enrollment/OTP to establish the new phone; empty/ambiguous extraction remains unknown. No automatic reassignment of phone/reporter identity. |
| Reactivation | Offboarding is terminal for that enrollment generation; an active ad later cannot silently restore it. Explicit new OTP/signature enrollment with the retained key is required. |
| Phone-only | Keep the existing explicit phone flow, identify it as phone-only, and exempt it from ad checks/24-hour renewal. Removing allowlist eligibility or disabling issuance does not revoke existing phone-only bindings. |
| Existing bindings | Mark missing provenance as legacy/unknown, preserve access/binding/history and do not guess an ad. Explicit future enrollment establishes provenance. A legacy lifecycle-status response is not a successful ad renewal. |
| Timestamps | `verified_at` remains historical for the current manual enrollment. Separate `last_renewed_at` (null initially), `next_renewal_at`, `offboarded_at` and reason. Explicit re-enrollment starts a new generation and retains existing verification semantics; renewal never rewrites `verified_at`. |
| Android catch-up | Persisted network-required JobScheduler job (minSdk 24) plus nonblocking eligible process-startup/resume catch-up and one cancellable timer while the UI is open. Triggers converge on one deduplicated operation using the matching existing key/backend. RECEIVE_BOOT_COMPLETED supports persisted jobs; ACCESS_NETWORK_STATE is the necessary foreground-network eligibility permission. No custom boot receiver, additive dependency, exact alarm, foreground service or SMS permission. Force-stop, Doze, OEM restrictions and unavailable connectivity/key can defer work until the next allowed job/resume; no exact 24-hour guarantee. |
| Retries | One protected portal fetch per eligible attempt, no in-request retries/proxy hopping. After temporary failure wait at least 15 minutes, then 1 hour, then 6 hours; at most four fetch reservations per enrollment per fixed 24-hour window starting at its first reservation. Further attempts wait for that window's end. Success resets the failure ladder, not consumed reservations. Honor longer validated Retry-After. Persist eligibility across process/resume/background. |
| Request/fetch caps | Additional renewal request limits: 20 per key per fixed hour, 3 per IP per minute. At most 100 portal-fetch reservations application-wide per fixed 24-hour window. Reuse shared-store/lock requirements; do not consume or reset SMS budgets. |
| Same-key enrollment | Future explicit OTP verification selects one current enrollment for that key, superseding prior current same-key bindings without deleting rows/contributions. Existing ambiguous multi-phone/same-key bindings are preserved: ordinary legacy query/report behavior stays unchanged; lifecycle bootstrap returns a distinct ambiguity error until explicit enrollment, never guessing a phone/ad. |

These decisions were explicitly selected by the owner through focused questions
on November 3. They are not inferred from historical handoff statements.
The exact technical details below were subsequently approved as one contract.

## Agreed exact wire boundary

Add `POST /api/auth/renew`, keeping manual initiation/verification intact.
Request fields: `enrollment_id` (opaque generation UUID), `request_id` (UUID),
`next_renewal_at` (the exact due timestamp from the authoritative snapshot),
normalized `public_key` and Base64 `signature`. Canonical compact JSON
field order: `operation`, `enrollment_id`, `request_id`, `next_renewal_at`, `public_key`, with
`operation` equal to `auth.renew`. Use existing PHP-compatible JSON escaping,
PEM normalization and SHA-256/EC signing. The operation domain-separates this
request from existing verification/query/report signatures. Backend scope is
captured by the client request context and matched before applying responses.

New verification success adds `lifecycle` with `enrollment_id`, `kind`
(`ad`/`phone_only`/`legacy`), `state` (`active`/`offboarded`/`superseded`),
`version` (positive integer), `verified_at`, `last_renewed_at` (nullable),
`next_renewal_at` (nullable for exemptions/terminal state), `offboarded_at`
(nullable) and `reason` (nullable or `ad_disabled`/`ad_expired`/`ad_deleted`/
`contact_changed`/`superseded`). Existing success fields remain.
Renewal success data is `{request_id, server_time, lifecycle}` with all keys
present; server UTC ISO-8601 timestamps, no ad URL/phone/OTP/private material.
Error lifecycle snapshots, where specified below, live in `meta.lifecycle`,
with echoed `meta.request_id` and `meta.server_time`. A monotonic version and
client operation generation prevent older responses from overwriting terminal
state or newer same-key enrollment.

Accepted status/code pairs:

| HTTP / code | Meaning and handling |
| --- | --- |
| 200 / `auth.renewed` | Due active-ad inspection succeeded and committed; validate request/key/generation/version and all timestamps before persistence. |
| 200 / `auth.renewal_not_due` | Authoritative unchanged snapshot; no portal fetch or timestamp advance. |
| 200 / `auth.renewal_exempt` | Identified phone-only/legacy exemption; no portal fetch or claim of checked ad/renewal. |
| 403 / `device_offboarded` | Valid signature and matching enrollment; `errors.authorization`, retryable false and complete authoritative terminal snapshot in metadata; no automatic retry/re-enrollment. |
| 401 / `auth.renewal_unauthorized` | Missing/unknown/mismatched binding or invalid signature; `errors.authorization`, retryable false; never proof of offboarding. |
| 409 / `auth.enrollment_changed` | Stale/superseded generation; `errors.enrollment_id`, retryable false, no lifecycle mutation; bootstrap reconciliation, not automatic OTP. |
| 409 / `auth.renewal_cycle_changed` | Supplied due timestamp is not the current cycle; `errors.next_renewal_at`, retryable false, current matched snapshot in metadata; reconcile it without another portal fetch. |
| 409 / `auth.request_id_conflict` | Same retained request ID with different canonical inputs; `errors.request_id`, retryable false; do not retry that ID. |
| 422 / `validation_failed` | Existing field error envelope for UUID/key/signature bounds; no state mutation or automatic retry. |
| 429 / `rate_limited` | Existing validated envelope, retryable true, bounded integer `meta.retry_after` and seconds Retry-After; no early retry. |
| 503 / `auth.ad_status_unavailable` | Network/proxy/parser/unknown ad state; `errors.authorization`, retryable true, integer `meta.retry_after` and seconds Retry-After; retain lifecycle/timestamps. |
| 503 / `abuse_protection_unavailable` | Existing shared-store failure; no fetch or mutation; retry policy applies only if validated. |

Request validation: UUID fields max 36, due timestamp max 64, PEM max 8192,
signature max 4096. No optional form inputs or client-supplied ad/phone.
Renewal exemption accepts nullable `next_renewal_at`; canonical JSON null must
be identical on both sides. Renewal itself never establishes provenance.

For query/report, keep existing signature/binding error pairs and CB-11 hint
invalidation unchanged. Use a distinct validated 403 / `device_offboarded`
only after successful request signature verification; include the terminal
snapshot and field error above, with no request ID (these existing requests
have none). Signature/binding rejection never proves offboarding. Failed call
lookups stay QueryFailed/not checked. No lease/grace suspension code is needed
under the agreed availability-first policy.
Malformed envelopes, unrelated codes and unexpected status/code combinations
cannot establish offboarding or advance a renewal deadline.

Add signed no-scrape `POST /api/auth/status` for a recoverable existing key:
request `{request_id, public_key, signature}`, canonical compact JSON order
`operation`, `request_id`, `public_key`, operation `auth.status`.
200 / `auth.status` returns `{request_id, server_time, lifecycle}`; 401 /
`auth.renewal_unauthorized`, 422 / `validation_failed`, 429 / `rate_limited`
and 503 / `abuse_protection_unavailable` use the same rules. 409 /
`auth.enrollment_ambiguous` has `errors.authorization`, retryable false and
no chosen lifecycle. Status may initialize an opaque legacy generation under
lock, but never infer an ad, renew, scrape, send SMS or revoke. A matching
offboarded selection returns 403 / `device_offboarded`.
Do not invent enrollment IDs from historical local timestamps. Status shares
the new request limits and client attempt schedule; no indefinite bootstrap
loop after terminal/ambiguous/nonretryable rejection.

## Persistence, idempotency and concurrency requirements

Store server-only enrollment provenance (validated ad URL or explicit kind),
generation, lifecycle state/version and agreed timestamps on the binding.
Keep legacy columns nullable on upgrade; no backfill from deleted challenges,
local hints or inferred contacts. No new raw identity in community tables.
Client preferences retain scope/fingerprint/history plus only minimal lifecycle
and retry metadata; no ad URL, phone, challenge/OTP or remembered form inputs.

Serialize renewal per binding/generation with the existing shared-lock store;
lock contention returns existing 429 / `rate_limited`, Retry-After 1.
The implementation's cache-lock lease is at least 120 seconds and otherwise
the configured portal timeout plus 30 seconds. Before I/O, the server atomically
reserves a pending receipt. Receipt row/digest/deadline and binding/key/generation/
version fence completion after a lease expires; retained duplicates cannot
refetch. An indefinitely suspended external request can overlap a later eligible
attempt after unknown settlement/backoff, but its stale result cannot commit.
Authenticate before fetch budgets. Retain request ID/canonical digest/outcome
receipts for 48 hours, bounded to eight receipts per enrollment; requests
already blocked by retry/fetch caps do not create receipts. Duplicate accepted
IDs in that window do not fetch/advance timestamps; return current matched
terminal state in preference to any old active receipt. A success duplicate
returns the stored outcome with current snapshot. A temporary-failure duplicate
returns its stored failure and remaining delay (minimum one second); a fresh
eligible attempt uses a new ID. Early not-due/exempt successes are receipted too:
replaying an early ID when due cannot turn it into a fetch. Persist client pending ID and due cycle before
sending, but never signed bytes/key/form inputs. A lost response can be
reconciled by status without renewing twice. Past receipt retention, the signed
due timestamp prevents an old successful cycle from renewing a newer cycle;
failed-cycle replay remains subject to the same fetch/request caps.
Commit a portal result only after rechecking the same
binding/key/generation/version under a short transaction; no portal I/O inside
database authorization/report locks.

Query/report authorization and lifecycle transitions need an explicit
linearization point and consistent lock order. Operations authorized before an
offboarding commit may complete; operations authorized after it must fail.
Long lifecycle locks must not consume the incoming-call deadline. Preserve
live-before-persisted identity and foreign-backend metadata protections,
including failed writes/removals and same-key newer enrollment.

## Implementation and qualification

The native implementation reuses private recovery storage, shared StateFlow,
signing/canonical JSON and envelope parsing. `RenewalCoordinator` persists a
pending request before sending; validated success commits lifecycle and its
next scheduling state together. `RenewalExecution` is the fake-testable shared
foreground/background trigger path; `AndroidRenewalRuntime` adapts it to
JobScheduler, connectivity callbacks and the bounded JobService. Production
Activity dependency initialization and jobs start this runtime; incoming-call
receiver-only startup intentionally does not initialize it or consume call
deadlines. Existing debug fake Activity dependencies remain network/Keystore
independent.
Initial scheduling uses the authoritative first due timestamp, not a fresh
24-hour interval starting when verification is delivered locally. Subsequent
responses use due-minus-server-time to calculate local delay. Home's existing
verification label remains historical; renewal does not relabel it as a new
OTP verification.

Successful offboarding persists a version-2 tombstone and removes live
verification state without deleting the key or clearing unrelated data.
Onboarding consumes an explicit offboarded state and clears only remembered
in-memory enrollment form/challenge values, requiring new user input.
Same-key new verification invalidates earlier operation tickets; matching
CB-11/report rejections remain effective, while older-generation responses
cannot clear a newer hint.

Failed pre-send persistence sends nothing and stops automatic execution in
that process until foreground storage recheck, avoiding an immediate hot loop.
Failed success persistence retains the old pending cycle/deadline. Failed
terminal persistence remains offboarded in-process with a visible restart
limitation; it never schedules deletion of foreign-backend metadata. A later
process cannot guarantee durable offboarding if storage refused all writes,
but server authorization still denies an offboarded enrollment.

Server migration/API deployment must precede separately approved client
distribution. A client encountering an unsupported/nonretryable endpoint stops
automatic lifecycle requests with visible guidance; it does not invent fallback
enrollment. Android platform jobs, true OS process death, Keystore, OEM latency,
live portal/SMS and hosted concurrent authorization remain separate
qualification. No device/hosted execution or publication/deployment approval.

### Server schema and upgrade compatibility

The sole new migration is
`database/migrations/2026_11_03_210000_add_binding_lifecycle.php` in the WSL
server checkout. It adds 13 binding columns: `enrollment_id`, `enrollment_kind`,
`ad_url`, `lifecycle_state`, `lifecycle_version`, `last_renewed_at`,
`next_renewal_at`, `offboarded_at`, `offboarding_reason`, `fetch_window_at`,
`fetch_count`, `renewal_failures`, `retry_at`.

Three small tables provide required boundaries: `device_key_selections` selects
the current binding under short authorization locks;
`device_enrollment_generations` retains generation/key ownership so stale
same-key requests remain distinguishable from unknown/foreign-key requests
after binding reuse or receipt eviction; `renewal_receipts` holds bounded
pending/outcome deduplication state. Nullable provenance/generation supports
legacy upgrade without guessed associations or forced revocation. Ad URL stays
server-side; no new raw identity enters community tables or client preferences.
Existing binding rows, contributions and device keys are not deleted.
Migration/rollback exercised only in isolated SQLite tests, not deployed data.
No dependency, toolchain, environment configuration or APK identity change.

### Changed-file inventory

Client: 26 files (22 source/test files and four documentation files). Native
Java/Kotlin paths below are relative to
`legacy_android_kotlin/app/src/main/java/com/example/myapplication/`.

| Surface | Changed files |
| --- | --- |
| App wiring | `legacy_android_kotlin/app/src/main/AndroidManifest.xml`, `MainActivity.kt` |
| Repositories/envelopes | `data/ApiFailure.kt`, `data/AuthRepository.kt`, `data/AuthRepositoryImpl.kt`, `data/BlacklistQueryRepositoryImpl.kt`, `data/ReportRepositoryImpl.kt` |
| Wire DTO/client | `data/remote/ApiClientFactory.kt`, new `data/remote/LifecycleApi.kt`, `data/remote/model/VerifyAuthData.kt` |
| Existing-key signing | `security/CanonicalPayloadFactory.kt`, `security/SignedRequestFactory.kt` |
| Lifecycle/recovery | `session/SessionRecovery.kt`, new `session/EnrollmentLifecycle.kt`, new `session/RenewalCoordinator.kt`, new `session/RenewalExecution.kt`, new `session/AndroidRenewalRuntime.kt` |
| Open navigation | `ui/onboarding/OnboardingViewModel.kt` |
| JVM tests, relative to equivalent `src/test/java` package | `data/SessionAuthorizationTest.kt`, `security/EphemeralSignedRequestFactory.kt`, new `data/BackendLifecycleInteropTest.kt`, new `session/RenewalCoordinatorTest.kt` |
| Client docs | `README.md`, `.github/docs/plugin_integration_map.md`, `.github/docs/ADRs/001-key-bound-local-session-recovery.md`, this new ADR |

Server: 22 files (19 PHP source/test/migration files and three documentation
files), all relative to the separate WSL checkout:

| Surface | Changed files |
| --- | --- |
| Routes/envelopes | `routes/api.php`, `bootstrap/app.php`, `app/Http/Controllers/Api/Auth/VerifyAuthController.php`, new `app/Http/Controllers/Api/Auth/LifecycleController.php`, new `app/Http/Requests/Auth/LifecycleRequest.php` |
| Binding/signing/enrollment | `app/Models/DeviceBinding.php`, `app/Services/DeviceSignatureService.php`, `app/Services/AuthVerificationService.php` |
| Lifecycle/authorization | new `app/Services/AuthLifecycleService.php`, new `app/Services/BindingLifecycle.php`, `app/Services/BlacklistQueryService.php`, `app/Services/ReportSubmissionService.php` |
| Protected portal/budgets | `app/Services/HttpEscortPortalClient.php`, `app/Scraper/Portals/AmaterkySkPhoneExtractor.php`, new `app/Services/RenewalAdInspector.php`, new `app/Services/RenewalProtection.php` |
| Migration | new `database/migrations/2026_11_03_210000_add_binding_lifecycle.php` |
| Tests | new `tests/Feature/Auth/LifecycleTest.php`, `tests/Feature/Services/HttpEscortPortalClientTest.php` |
| Server docs | `.github/docs/BACKEND_API_CONTRACT.md`, `.github/docs/plugin_integration_map.md`, new `.github/docs/ADRs/006_coordinated_renewal_and_offboarding.md` |

### Executed local evidence

Client commands, from `legacy_android_kotlin`, using the existing Android Studio
JBR and `BLACKLIST_LIFECYCLE_BACKEND` set to the explicit local WSL checkout:

```powershell
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin
.\gradlew.bat --offline --no-daemon --console=plain :app:lintDebug
```

Final full JVM XML: **217 discovered, 216 passed, one skipped**, 26 suites,
zero failures/errors. The skip is the older optional `BackendReportInteropTest`;
the new `BackendLifecycleInteropTest` actually executed. Its synthetic Java EC
status/null-due/offset-due vectors matched the real PHP `DeviceSignatureService`
canonical payloads and signature verifier, without Laravel environment/database
bootstrap or hosted requests. Debug lint passed: zero errors, 19 warnings.
Instrumentation source had compiled earlier; the final compilation task was
up-to-date. No Android instrumentation was executed.

The server agent executed this focused selection against isolated memory SQLite:

```powershell
wsl -d Ubuntu-22.04 --cd /home/marcel/projects/blacklist/backend-laravel -- php vendor/bin/phpunit --do-not-cache-result tests/Feature/Auth tests/Feature/Blacklist tests/Feature/Reports tests/Feature/Scraper/Portals tests/Feature/Services/HttpEscortPortalClientTest.php
```

**200 tests / 1,331 assertions passed**, including 31 lifecycle tests /
296 assertions. This is not the full repository PHPUnit suite. Changed-PHP Pint
and whitespace checks passed. Portal/SMS/network behavior used existing fakes
and synthetic fixtures; time and interleavings were deterministic.

Meaningful executed failing-before/passing-after evidence:

| Regression | Before | After |
| --- | --- | --- |
| Valid incoming-query 403 offboarding | Original client retained recovery metadata; one new test failed. | Matching authoritative state removes navigation hint, fails lookup, and opens blank explicit onboarding without SMS/key operations. |
| Delayed verification delivery | Initial implementation incorrectly postponed first due time by a fresh local day; regression failed. | Authoritative first due time remains unchanged; overdue catch-up executes once. |
| Unrelated HTTP 429 code | Initial implementation scheduled an automatic retry; regression failed. | Only the agreed retryable status/code pairs permit automatic retry; unrelated code stops it without offboarding. |
| Legacy lifecycle bootstrap | Original server endpoint returned 404 instead of 200; one test/one assertion failed. | Legacy exemption/ambiguity handling passes without portal fetch or provenance inference. |
| Expired renewal cache lease | Initial server implementation fetched twice; dedicated regression failed. | Durable pending receipt prevents duplicate refetch and fences stale completion; regression passes. |

Client before/after XML is retained outside Git in this session's `files`
directory (`renewal-client-failing-before.xml`,
`renewal-client-passing-after.xml`, `renewal-client-edge-failing-before.xml`,
`renewal-client-edge-passing-after.xml`, `renewal-client-php-interop.xml`).
Server baseline failures were executed and summarized in its integration map;
raw server baseline output was not retained.

Fakes cover due/overdue and exact boundaries, bounded foreground/background
triggers, interruption/recreation, retries/caps, deduplication, stale key/backend/
generation responses, persistence refusal, contact/inactivity/reactivation,
phone-only/legacy, malformed envelopes and existing onboarding/CB-11/report/call
regressions. This does not qualify OS process death or actual platform scheduling.
Real MariaDB concurrent-process tests were not executed: no isolated provisioned
database/container was available. No full server suite, device/Keystore,
live provider/SMS/scraping, hosted API, release build/signing/install or deployment
qualification. Server migration/API rollout, separately approved client
distribution and hosted/device qualification remain prerequisites.

At the November 3 local closeout, both baseline HEADs/main branches and remotes were unchanged; all feature work
is local and unstaged. Pre-existing client enrollment-map notes remain intact.
Server-owned `SERVER_TO_CLIENT.md` was not edited. No commit/push/PR/merge,
hosted configuration/database/budget/key operations, or broad data clearing.

### November 4 client publication scope

Owner authorized a new client branch, commit, push, PR merge and return to main.
The client implementation is preliminarily complete within local verification,
not deployed/device-qualified. Publish only its 26 feature/source/documentation
files; preserve unrelated pre-existing October 23 enrollment-map notes unstaged.
The client-owned `CLIENT_TO_SERVER.md` is maintained locally through the ignored
server-docs symlink, not force-added to this repository. Server publication,
migration/API deployment, APK signing/distribution/install and live operations
remain separately gated.

Pre-publication review extended the delayed-verification test to refresh and
process recreation. It genuinely failed because initial `windowStart` was later
than an already-overdue `eligibleAt`, causing the persisted attempt to be rejected.
Initializing the empty scheduling window no later than its authoritative due time
preserves the original interval and valid recovery across both paths; no wire
or server change is required.

The final November 4 candidate reran the full offline JVM suite with actual local
PHP lifecycle interoperability enabled: 217 discovered / 216 passed / one older
optional report-interop skip, zero failures/errors. The extended delayed-delivery
refresh/recreation regression passes. Instrumentation compilation was up-to-date,
not executed; sequential debug lint passed with zero errors / 19 warnings.
No server suite or hosted/device acceptance was rerun for client publication.
