# Blacklist Client

**Last documentation update:** 2026-10-04 22:01:40 CEST (UTC+02:00) - final CB-02/CB-08 strict whitespace-boundary correction and repeated validation.
**Updated:** 2026-10-05 11:30:31 CEST (UTC+02:00) - local CB-03/CB-04 call-lifecycle/deadline guidance; earlier evidence retains its original timestamp.
**Updated:** 2026-10-05 13:19:46 CEST (UTC+02:00) - approved CB-05 caller-diagnostic retention, legacy cleanup and backup guidance.
**Updated:** 2026-10-05 13:27:35 CEST (UTC+02:00) - final CB-05 local evidence and release-test limitation; earlier timestamps/results preserved.
**Updated:** 2026-10-05 14:06:15 CEST (UTC+02:00) - CB-09 explicit OTP correction, truthful retained-challenge guidance and local validation.

Android client for a privacy-focused caller-warning and community reporting service backed by a Laravel API.

## Project status

The Phase 6 Expo / React Native pivot is cancelled. The native Kotlin / Compose application in `legacy_android_kotlin/` is the active Android beta target; its directory name is retained to avoid moving existing work.

The root Expo application, `android/`, and `modules/` are preserved as historical work, not the shipping beta application. Do not use `npm run android` to build the Kotlin beta target.

## Native Android development

Open `legacy_android_kotlin/` as the project in Android Studio. Install the SDK versions required by its Gradle configuration. Configure your local SDK path using Android Studio or an untracked `local.properties` file.

From PowerShell at the repository root:

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Isolated UI tests (use a disposable offline emulator; do not launch the production entrypoint):

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ui.CooldownScreenTest,com.example.myapplication.ui.CooldownLifecycleTest,com.example.myapplication.ui.SessionNavigationTest'
```

Do not run unfiltered instrumentation under the no-key-touch beta validation scope: the separate security tests create/delete test keys. Countdown fixtures use a blank Activity. `SessionNavigationTest` uses the debug-only `RecoveryHarnessActivity`, a fake-backed subclass executing the real `MainActivity` navigation and lifecycle code with injected ViewModel factories. It never constructs production networking or keystore factories. Target only the disposable emulator (set `ANDROID_SERIAL` if other devices are connected).

Release signing and production backend readiness must be finalized before live beta distribution. A debug build is not a signed beta release.

## Key-bound session recovery

After a successful explicit OTP verification, the native client stores only a schema version, backend scope, SHA-256 public-key fingerprint and historical verification timestamp in `device_binding_recovery`. The fingerprint is derived from the submitted public key and must match the existing usable device key. No OTP, challenge, recipient/masked number, ad URL, signed request or private key is persisted for recovery.

A fresh process with matching metadata/key opens the ordinary workspace without sending SMS or replaying any API command. The home card explicitly labels this as **local recovery**, not current server authorization or a successful Shield lookup. The existing contract has no dedicated session-validation endpoint; restoration does not use reports or synthetic lookups as probes. Only ordinary user-submitted operations or independent incoming-call events make signed requests.

Absent/invalid/mismatched metadata or missing/invalid/changed keys cannot restore workspace access. Temporarily unavailable keys retain the hint on disk and show a recovery error; reopening/resuming rechecks locally. Recovery and report/query signing never create or replace a key. Explicit OTP verification may create an absent key; an unacceptable existing key now fails without replacement. Reset/replacement of an existing key requires a separately approved recovery procedure.

Well-formed HTTP 403 `device_not_bound` (report) or `blacklist_query_unauthorized` (manual/Shield query) removes the matching hint and returns the open UI to onboarding, including when successful verification could not be persisted. The query code conflates signature and binding failures, so it is an authorization rejection, not proof of revocation. Network errors, 429, 503 and malformed errors retain valid hints. Storage failures are surfaced; they are not claimed as successful persistence/removal.

The preference is excluded from legacy cloud backup and Android 12+ cloud backup/device transfer. A transferred preference still cannot restore access without the matching usable key. Caller diagnostics have their own no-persistence policy below; recovery metadata/key behavior is unchanged. Older installations with a bound key but no recovery record cannot be migrated by guessing server authorization; one explicit verification is needed to record the hint.

`Restart onboarding` opens the URL form and removes only the local hint; it no longer resends a remembered URL immediately. It does not reset the key or server binding, and retained monotonic cooldowns remain enforced.

Focused recovery tests:

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat :app:testDebugUnitTest --tests "*SessionRecoveryTest" --tests "*SessionAuthorizationTest"
```

`SessionNavigationTest` executes production navigation and Activity recreation with fakes; it is not an OS process-kill test. A separate offline emulator `am force-stop`/fresh-launch check of the debug harness was executed on 2026-10-04, confirming persistent synthetic metadata, local-recovery text and zero fake-command replay. This is actual OS process termination/relaunch with a fixed fake key, not Android Keystore, physical-device/reboot, hosted authorization or live-OTP evidence. Exact commands/results and remaining gates are in the client-owned [handoff](backend-docs/client-docs/CLIENT_TO_SERVER.md); the durable decision is [ADR-001](.github/docs/ADRs/001-key-bound-local-session-recovery.md).

## Report and caller-number normalization

Reporting and incoming-call lookup share a formatting-only subset of the inspected backend policy. Compact/spaced `+421900000001`, `00421 900-000-001`, Slovak trunk `0900 000 001`, and bare country prefix `421900000001` all become `+421900000001` before report signing/transmission or caller SHA-256 hashing. Explicit international `+`/`00` forms retain their supplied country prefix; this is not worldwide numbering-plan, carrier or SMS certification.

ASCII digits, spaces and internal hyphens are supported. Bare local numbers such as `900000001`, letters, extensions, parentheses, dots, non-ASCII digits/separators, unavailable caller IDs and empty/incomplete supported prefixes are rejected locally. Reports display a field error without signing/sending; invalid caller input never queries. The client no longer guesses `+421` for bare nine-digit caller IDs.

The canonical report contains exactly the normalized number sent on the wire. Field order, JSON/slash escaping, UTF-8 signing and Base64 DER ECDSA encoding remain unchanged. PEM cleanup matches backend CRLF conversion, per-line trimming and blank-line removal. Normalization does not replay a corrected input or introduce retries.

The backend currently has no country/national-length validation and strips arbitrary non-digits. With explicit user approval, this slice preserves its prefix mappings but does not copy that broad stripping or invent a new country/length rule. A coordinated numbering-plan/format decision remains open; syntactic normalization is not proof a number exists. See [ADR-002](.github/docs/ADRs/002-shared-phone-normalization-before-signing.md).

Focused local normalization/signing tests:

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat :app:testDebugUnitTest --tests '*PhoneNumberNormalizerTest' --tests '*CallerNumberNormalizerTest' --tests '*ReportNormalizationSigningTest' --tests '*CanonicalPayloadFactoryTest' --tests '*PublicKeyPemEncoderTest' --tests '*IncomingCallProcessorTest'
```

Optional actual PHP verifier interoperability (Windows + existing WSL PHP/OpenSSL and a read-only backend checkout):

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:CB02_BACKEND_ROOT = '/home/marcel/projects/blacklist/backend-laravel'
$env:CB02_INTEROP_SCRIPT = '/mnt/c/Users/Marcel-PC/AndroidStudioProjects/blacklist-client/legacy_android_kotlin/app/src/test/interop/verify-report.php'
Set-Location legacy_android_kotlin
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests '*BackendReportInteropTest' --rerun
```

These two environment values are WSL filesystem paths passed to PHP, not PowerShell navigation paths. Set them for your explicitly selected local checkouts; `CB02_WSL_DISTRO` defaults to `Ubuntu-22.04`. Without both values, the optional JVM interoperability test is skipped rather than claimed as passing. Its client-owned PHP harness loads only the actual backend normalizer/signature service files: no Laravel boot, `.env`, database, cache, hosted request or SMS. Ephemeral private keys stay in JVM memory; only synthetic signed fixtures go through stdin. Successful PHP verification is stronger than Java self-verification, but does not establish HTTP authorization, database reporting, hardware-backed Android signing or hosted compatibility.

## Incoming-call lifecycle and lookup deadline

The native receiver shares one process-owned call coordinator across receiver instances. Warnings are **ringing-only**: OFFHOOK (answer) and IDLE (reject/end) cancel lookup and dismiss the warning. Repeated ringing events and equivalent number formats do not query again. A different valid number replaces the current lookup; cancelled/old results cannot overwrite its warning or status.

Android documents numbered and numberless companion PHONE_STATE broadcasts in unspecified order when both caller permissions are held. A blank companion never erases an available number/warning. Numberless-only calls are **not checked**, without claiming hidden caller ID; a later numbered companion can still initiate lookup. Invalid input never queries.

Caller lookup has a **5-second overall budget** from receipt of the usable numbered event, including preparation/signing and network work. Timeout is an explicit failed/not-checked outcome, not a successful no-match, and never automatically retries. Cancellation reaches signing preparation and Retrofit/OkHttp. Ordinary manual queries/reports keep their existing timeout policy. No call lookup is persisted or replayed after process recreation.

This is an initial safety policy, not device latency certification. PHONE_STATE cannot reliably distinguish call waiting, overlapping multi-SIM calls, or same-number successors without an observed transition. Main-thread stalls, broadcast delivery delays and uninterruptible OEM operations remain timing assumptions. See [ADR-003](.github/docs/ADRs/003-ringing-call-coordination-and-lookup-deadline.md).

Focused synthetic/fake/local-network tests (no device, hosted API or real keys):

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests '*IncomingCallCoordinatorTest' --tests '*IncomingCallProcessorTest' --tests '*CallerLookupDeadlineTest' --tests '*BlacklistQueryRepositoryTest' --tests '*SessionAuthorizationTest' --tests '*SessionRecoveryTest'
```

## Caller-diagnostic privacy, cleanup and backup

Debug and release persist **no caller status**: no raw/canonical numbers, hashes, matched labels, arbitrary messages, operational flags or timestamps. Caller details are transient lookup/active-warning data; observed OFFHOOK/IDLE cancels/invalidates lookup, clears coordinator identity and dismisses the warning. The active warning retains number/hash/matched labels; its guarded accessibility announcement retains matched labels. Home snapshots and caller-lookup logs contain none of these details.

Independently created receiver/Activity stores share one redacted process-memory operational snapshot. Home uses fixed messages, distinguishing unavailable/failed/not checked from successful no-match, and retains its existing opening/resume/permission/manual-refresh hooks rather than subscribing to live calls. Its caption explicitly describes snapshot timing. Fresh processes start Idle/not checked and never restore/replay a caller lookup.

First status-store initialization deletes only the app-owned legacy `shield_live_status` preferences through Android's `deleteSharedPreferences`, including XML/backup-file cleanup. A false result or security denial remains an explicit cleanup warning and retries on subsequent local status hooks; old values are never decoded even if removal fails. Recovery preferences, keys/bindings, challenges, cooldowns and unrelated data are untouched. This is upgrade behavior, not permission to clear an existing personal installation.

Legacy cloud backup and Android 12+ cloud/device transfer explicitly exclude `shield_live_status.xml` and `shield_live_status.xml.bak`, including residual old diagnostics. Recovery exclusions remain intact. XML exclusions and JVM fakes are not an actual backup/transfer or Android durable-deletion drill; old uploaded backups, forensic erasure and external accessibility-service retention are not certified. See [ADR-004](.github/docs/ADRs/004-caller-diagnostic-retention-and-legacy-cleanup.md).

Focused synthetic JVM privacy/lifecycle regressions:

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat --no-daemon --console=plain :app:testDebugUnitTest --tests '*ShieldLiveStatusStoreTest' --tests '*CallerDiagnosticLoggingTest' --tests '*IncomingCallCoordinatorTest' --tests '*IncomingCallProcessorTest' --tests '*CallerLookupDeadlineTest' --tests '*SessionAuthorizationTest' --tests '*SessionRecoveryTest'
```

Final local CB-05 regression: **133 JVM tests / 20 suites**, zero failures/errors/skips, with actual PHP service interoperability enabled (eight report/eight query vectors). Debug application and instrumentation APKs built; debug lint **0 errors / 35 warnings**. Release Kotlin/resources compiled and release lint has **0 errors / 31 warnings**; compiled release XML retains diagnostic/recovery exclusions. The existing Gradle configuration exposes only debug JVM tests: the attempted `testReleaseUnitTest` task does not exist, so no release JVM execution is claimed. The new fake-backed Home fixture compiled but was not executed. No device was used, and no actual Android deletion/backup/transfer evidence was obtained. Exact commands and publication boundary are in the local client-owned handoff.

## Rate-limit compatibility

The Kotlin client accepts both empty API error representations (`[]` and `{}`), preserves field errors, and handles HTTP 429 with a manual retry countdown. Valid integer `meta.retry_after` takes precedence over the seconds-only `Retry-After` header; invalid/missing values use a 60-second fallback, with positive delays bounded to 24 hours. This is client policy, not a guarantee of server limiter scope or SMS delivery.

Cooldown deadlines survive navigation and configuration changes in activity-owned ViewModels, but are not persisted across process death. Countdown expiry enables commands without submitting them. Blocked resends retain the existing challenge; generic throttling does not imply challenge exhaustion. Initiation is never automatically replayed after ambiguous errors, including OkHttp's zero-delay 503 follow-up case.

Focused local mock/state tests (no hosted API or SMS):

```powershell
Set-Location legacy_android_kotlin
.\gradlew.bat :app:testDebugUnitTest --tests "*BetaCompatibilityTest" --tests "*RetryStateTest"
```

`CooldownScreenTest` contains isolated Compose UI fixtures; `CooldownLifecycleTest` exercises a fake-backed countdown through navigation away/back and actual blank-Activity recreation. Its content is explicitly reattached after recreation; it does not test the production navigation shell. Building a test APK does not establish that UI tests have executed.

Fresh-ViewModel JVM regressions model process-state loss, not an OS process kill. OTPs, pending challenges and cooldowns remain memory-only; no command is replayed. Production restoration now uses the minimal key-bound hint described above. Actual Android Keystore/physical-device restart and reboot behavior, live backend/device onboarding and release readiness remain separate approval-gated checks; do not reset a bound key to work around them.

## OTP correction without another SMS

An `otp_invalid_or_expired` response no longer automatically closes the local challenge. The contract combines wrong-code and expiry outcomes: the UI says the OTP **may be incorrect or expired**, not that the challenge is still valid. You can edit the code and explicitly tap **Verify OTP** against the same challenge, masked recipient and recorded expiry. Each submission may consume a server attempt; neither remaining attempts nor authoritative exhaustion can be inferred from generic `rate_limited`.

Verification checks the recorded expiry at each explicit submission and sends nothing after it has passed. Missing/unreadable expiry blocks submission with an explicit error, without claiming server consumption. A well-formed `challenge_not_found` or `signature_invalid` still blocks verification; signature/key failures may require support and are not proof the server deleted the challenge. Temporary/network/unknown/malformed failures retain the challenge with server-status-unknown guidance. OTP edits clear the field error, not that uncertainty guidance or the cooldown.

**Start a new challenge** is a separate explicit SMS action, subject to the same retained cooldown and in-flight guards. A failed resend retains local state but cannot guarantee server validity: the backend can replace a challenge before an ambiguous SMS failure. OTP input is disabled during initiation/verification and after a local block; duplicate commands cannot submit again in flight. No edits, countdown expiry, navigation, recreation or restoration initiate or verify automatically. Nothing new is persisted, and successful verification retains the existing key-bound recovery/authorization policy.

See [ADR-005](.github/docs/ADRs/005-explicit-otp-correction-with-ambiguous-errors.md). Local CB-09 evidence: **69 targeted JVM tests / five suites**, then a fresh **147 JVM tests / 21 suites**, zero failures/errors/skips, including actual eight-report/eight-query PHP service interoperability. Debug app/test APKs and release Kotlin/resources compiled; debug/release lint remain **0 errors / 35 warnings** and **0 errors / 31 warnings**. The new fake-backed OTP correction/accessibility/lifecycle fixtures compiled but were **not executed on a device**. No hosted verification or live SMS was performed; this is not overall closed-beta readiness.

## Repository boundaries

- Build output, dependency caches, IDE settings, local SDK configuration, environment secrets, and signing keystores are excluded.
- `backend-docs` is a local symbolic link into the separate backend workspace and is intentionally not published here.
- Keep production signing credentials outside version control.
