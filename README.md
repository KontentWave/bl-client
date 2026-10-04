# Blacklist Client

**Last documentation update:** 2026-10-04 22:01:40 CEST (UTC+02:00) - final CB-02/CB-08 strict whitespace-boundary correction and repeated validation.

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

The preference is excluded from legacy cloud backup and Android 12+ cloud backup/device transfer. A transferred preference still cannot restore access without the matching usable key. Existing caller-diagnostic retention/backup (CB-05) is separate and unchanged. Older installations with a bound key but no recovery record cannot be migrated by guessing server authorization; one explicit verification is needed to record the hint.

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

## Repository boundaries

- Build output, dependency caches, IDE settings, local SDK configuration, environment secrets, and signing keystores are excluded.
- `backend-docs` is a local symbolic link into the separate backend workspace and is intentionally not published here.
- Keep production signing credentials outside version control.
