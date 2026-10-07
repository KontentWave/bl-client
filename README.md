# Blacklist Client

**Last documentation update:** 2026-10-04 22:01:40 CEST (UTC+02:00) - final CB-02/CB-08 strict whitespace-boundary correction and repeated validation.
**Updated:** 2026-10-05 11:30:31 CEST (UTC+02:00) - local CB-03/CB-04 call-lifecycle/deadline guidance; earlier evidence retains its original timestamp.
**Updated:** 2026-10-05 13:19:46 CEST (UTC+02:00) - approved CB-05 caller-diagnostic retention, legacy cleanup and backup guidance.
**Updated:** 2026-10-05 13:27:35 CEST (UTC+02:00) - final CB-05 local evidence and release-test limitation; earlier timestamps/results preserved.
**Updated:** 2026-10-05 14:06:15 CEST (UTC+02:00) - CB-09 explicit OTP correction, truthful retained-challenge guidance and local validation.
**Updated:** 2026-10-06 12:04:48 CEST (UTC+02:00) - current merged-source reconciliation, debug-only HTTP diagnostics and bounded unsigned-candidate qualification.
**Updated:** 2026-10-06 12:18:00 CEST (UTC+02:00) - complete seven-file source review and branch/commit/push/PR publication approved; exact-head merge requires separate approval.
**Updated:** 2026-10-06 15:37:23 CEST (UTC+02:00) - verified disposable offline emulator execution, fixture-only Home corrections, targeted JVM rerun and shutdown; reconcile merged source.
**Updated:** 2026-10-06 16:20:09 CEST (UTC+02:00) - complete three-file review and approved branch/commit/push/PR preparation; exact-head merge requires separate approval.
**Updated:** 2026-10-07 06:01:24 CEST (UTC+02:00) - read-only native identity/signing review, later PR #7 merge reconciliation and owner decision checklist; implementation unchanged.
**Updated:** 2026-10-07 06:05:49 CEST (UTC+02:00) - complete documentation review and owner-approved branch/commit/push/PR preparation; exact-head merge remains separately gated.
**Updated:** 2026-10-07 18:47:24 CEST (UTC+02:00) - recheck PR #8 merged local baseline, record exact owner-controlled ID and first-version allocation, and prepare separately gated ID/version-only implementation; no configuration or qualification execution.
**Updated:** 2026-10-07 18:51:58 CEST (UTC+02:00) - separately approved ID/version-only implementation and offline Kotlin/main-manifest qualification complete; no APK packaging/signing/device execution/publication.
**Updated:** 2026-10-07 19:06:52 CEST (UTC+02:00) - complete three-file publication review and approved feature-branch/commit/push/PR preparation; implementation/evidence unchanged, exact-head merge separately gated.
**Updated:** 2026-10-07 20:12:07 CEST (UTC+02:00) - record owner-only/no-deputy custody and proposed owner-run Android Studio setup; documentation only, no key creation/import/backup/signing authority.
**Updated:** 2026-10-07 20:15:51 CEST (UTC+02:00) - complete two-file documentation review and approved branch/commit/push/PR preparation; proposed signing settings/operations still unapproved, exact-head merge separately gated.

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
$env:ANDROID_SERIAL = '<verified-disposable-emulator-serial>'
.\gradlew.bat --offline --no-daemon --console=plain :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.example.myapplication.ui.CooldownScreenTest,com.example.myapplication.ui.CooldownLifecycleTest,com.example.myapplication.ui.SessionNavigationTest,com.example.myapplication.ui.OtpCorrectionLifecycleTest,com.example.myapplication.ui.home.HomeScreenTest,com.example.myapplication.ui.onboarding.OnboardingScreenTest'
```

Do not run unfiltered instrumentation under the no-key-touch beta validation scope: the separate security tests create/delete test keys. Countdown fixtures use a blank Activity. `SessionNavigationTest` uses the debug-only `RecoveryHarnessActivity`, a fake-backed subclass executing the real `MainActivity` navigation and lifecycle code with injected ViewModel factories. It never constructs production networking or keystore factories, but inherited status-cleanup and permission-read hooks still run in the app sandbox. Verify the dedicated AVD identity, offline interfaces/routes and forwarding before installation; always set `ANDROID_SERIAL` to that verified device. Installed AGP 9.1.1's `--serial` task option fails before installation by mutating an immutable list; use its existing `ANDROID_SERIAL` provider filter instead. Never target an existing/personal installation or launch production MainActivity.

Release signing and production backend readiness must be finalized before live beta distribution. A debug build is not a signed beta release.

## Native candidate qualification - October 6

The October 6 qualification/publication preparation baseline was client main/local origin/main `3fd93aa876c1814b271c04ba840bd162e94ceee5`, containing merged PRs #1-#6 and the CB-01/02/03/04/05/09 and supported CB-08 corrections. Its complete tree `b6838dbe3a789128d61c154d8cc5685fbeaec02f` equalled reviewed source `5d049bbb90e60b0fb99ef8e570a8f0527b894d2f`. The later PR #7 merge checkpoint below supersedes that baseline and preparation-only pending-merge wording. The October 2 review is historical, not a list of still-unfixed source defects. Latest server handoff records backend merged main `b69baa34b39761218d5c7534a61e7800dbd8f81f`; CB-06/07/10 and PHP dependency remediation require PHP 8.4.1+ and no Android wire change. Server suites and hosted revision/configuration were not reverified here.

Merged PR #6 gates auth/report HTTP BASIC diagnostics on `BuildConfig.DEBUG`, consistent with verification/key diagnostics. Shield/manual query HTTP logging remains disabled in both builds. This emulator slice changed only Home instrumentation: exact fixed diagnostic-text assertions and scrolling before visibility checks. No production source, signing, retry, timeout, key, recovery, caller-retention or OTP persistence policy changed. No publication or distribution occurred during emulator qualification. The dated preparation approved subsequent three-file publication while requiring separate exact-head merge approval; the later checkpoint records that approval and completed merge, not distribution.

At the October 6 qualification checkpoint, identity was `com.example.myapplication`, version code `1`, name `1.0`, min/target SDK `24`/`36`, compile SDK `36.1`, with the existing fixed HTTPS API URL. The October 7 separately approved source identity/version change is recorded below; it does not update those existing APKs. No release signing configuration or beta signing identity has been established here. Signing custody needs owner approval before signing; changing the ID isolates existing keys/preferences, and changing the API URL changes recovery scope.

Earlier October 6 offline qualification executed **129 targeted JVM tests / 14 suites**, zero failures/errors/skips, including actual pure-PHP interoperability with eight report/eight query vectors; these results were not rerun in this slice. Debug app/test APKs compiled; an **unsigned release APK** was packaged at `legacy_android_kotlin\app\build\outputs\apk\release\app-release-unsigned.apk` (10,468,831 bytes), SHA-256 `C5E7EE29056DE774077F71D03B3821B236AA7F9659809BA1B2DBEBD45FF1C4B9`, rechecked unchanged after emulator execution. It contains the subsequently merged transport correction, not a signed beta release. Earlier APK inspection confirms non-debuggable release, no debug recovery harness and packaged recovery/diagnostic backup exclusions. Compiled release transport defaults disable diagnostics; this is compiled evidence, not release JVM/device execution.

Current lint XML reports debug **0 errors / 26 warnings**, release **0 errors / 31 warnings**. The debug warning count differs from the previous 35: nine dependency-version warnings are absent in that report; no dependency remediation or nine-warning fix is claimed. The previous 147-test full-suite evidence remains historical; this run was deliberately targeted. `testReleaseUnitTest` is still not configured.

**Scoped emulator fixtures executed:** fresh session-only `CB_FakeUI_Offline_20261006_1517` / `emulator-5580`, installed Android 14/API 34 Google Play x86_64 image revision 14. Actual QEMU Wi-Fi/radio backends both use `restrict=on,ipv6=off`; guest Wi-Fi/mobile data/Bluetooth disabled, eth0/wlan0 DOWN, no active default network, host/DNS/documentation-address route lookups unreachable, no emulator or adb forwarding. No proxy-only or airplane-label-only isolation claim. Existing AVDs/devices and shared host configuration were untouched. Final exact six-class run passed **16/16 cases**, zero failures/errors/skips; initial execution had two Home fixture failures, preserved before correction. Two earlier `--serial` task attempts executed no tests. Fresh affected JVM regressions passed **17 tests / two suites**. Debug application packaging was up-to-date; corrected instrumentation APK recompiled. Emulator shut down and its dedicated AVD retained under session files, not made a general-purpose online AVD.

Exact commands, per-class totals, failures/retries, artifact checksums, isolation evidence, changed-file inventory and remaining gates are in the ignored client-owned [handoff](backend-docs/client-docs/CLIENT_TO_SERVER.md). No security instrumentation or production entrypoint, release signing/install-update, OS process-kill drill, hardware-backed Keystore, physical telephony/OEM/accessibility-service, actual legacy-diagnostic migration/deletion or backup/transfer drill was performed. Owner identity/version/signing/privacy/operator/backup/recovery choices, hosted qualification and genuine-recipient SMS remain separate approvals. **BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED; this local task does not establish overall closed-beta readiness.**

## Native release identity and signing - owner decision checkpoint

**Recorded:** 2026-10-07 06:01:24 CEST (UTC+02:00). Source/artifact-metadata review and decision recording only; no configuration, key, signing or device action.
**Updated:** 2026-10-07 06:05:49 CEST (UTC+02:00) - owner separately approved publication of the two tracked documentation files. Complete diff review found no required correction, new file or migration. Local main/origin/main and live code-remote main rechecked at `b759f206344c2b6f5b399419f5e02ba29fab968b` during this later publication slice; the original read-only inspection made no remote request. Exact-head merge requires separate approval; ignored handoff, artifacts and local configuration remain excluded. No implementation/signing/device/distribution authority.

**Later merge checkpoint (updated 2026-10-07 18:47:24 CEST, UTC+02:00):** PR #8 was separately approved and merged after publication of only README/map. Rechecked local `main`, HEAD and local `origin/main`: `9def62a954625657caea67a14736b1a27b23f59f`; complete tree `269ea1eb5fe5826a4e6b5418160471c45b1209e7` equals reviewed/published head `cbef46907d88da656938282240d9a73b18302979`. The ignored handoff records the actual publication/merge; earlier preparation wording above is historical. No remote request/fetch reverified live GitHub main today. Initial tracked worktree/index were clean, with no untracked deliverables; feature branch retained.

[Active Gradle configuration](legacy_android_kotlin/app/build.gradle.kts), updated under separate approval, now sets application ID **`bl.zafo`**, namespace **`com.example.myapplication`**, code **`1`**, name **`0.1.0-beta.1`**, min/target SDK `24`/`36`, compile SDK `36.1`, with the unchanged fixed HTTPS API URL. Only applicationId/versionName changed. No flavors, application-ID suffix or explicit release signing configuration is declared in inspected active Gradle source; external signing arrangements are unknown and were not inspected. Existing release output metadata/APKs remain the historical `com.example.myapplication` / `1` / `1.0` outputs, not this new source identity. Existing unsigned APK remains 10,468,831 bytes with unchanged SHA-256 `C5E7EE29056DE774077F71D03B3821B236AA7F9659809BA1B2DBEBD45FF1C4B9`; prior unsigned verification is historical, not rerun here. No new APK was packaged; it is not a signed beta candidate.

| Owner decision | Recorded disposition; recommendation versus approval |
| --- | --- |
| Durable application ID | Owner selected exact **`bl.zafo`** and explicitly confirmed control of its naming identity; separately approved implementation is now applied. Earlier `bl-amaterky` answer was not a valid Android application ID (hyphen/no dot-separated segments) and was superseded; no display-name change requested or approved. |
| Namespace/source packages | Currently `com.example.myapplication`; recommendation is to leave them unchanged in an ID-only slice. `namespace` owns generated code/resource names; `applicationId` owns installed identity. No namespace change approved. |
| Version/update policy | Owner confirmed no release under `bl.zafo` has ever been distributed through direct APKs or any other channel and explicitly allocated first code **`1`**. Retain previously conditionally accepted name **`0.1.0-beta.1`** now that history is settled. This table is the central client allocation record: `bl.zafo` / `1` / `0.1.0-beta.1`, allocated and implemented under separate approval, **not packaged/signed/distributed**. Every later distributed beta code must strictly increase across future channels; names do not determine update ordering. |
| Distribution channel | Owner selected owner-controlled direct APK distribution. No distribution action approved. Proposed ordinary updates retain the same application ID and compatible app-signing certificate; do not assume signer rotation support. |
| Signing custody/operator | Owner designated the sole owner/developer as signing custodian/operator and confirms no deputy currently exists. Signing access/availability details still need explicit policy acceptance; recommended behavior is pause signing/releases while owner unavailable. This is accountability, not a credential or present signing authorization, and is separate from the server `admin` appointment/coverage gate. |
| Secure-local method/storage | Owner accepted in principle the existing Android Studio signed-release workflow, manually invoked by that operator, with durable signing material outside both repositories and no secrets in source, Gradle, command arguments or evidence. No existing key assumed; creation/import/rotation and execution remain unapproved. |
| Signing backup/access/loss | Owner explicitly left protected backup custody, authorized access, bounded recoverability check and loss/compromise handling **pending**; signing gate NOT cleared. Do not invent rotation/recovery policy or request secret values. |
| Existing debug installations | Owner explicitly excluded them from beta migration; leave debug/personal sandboxes untouched. No uninstall, key reset, binding change or data-clearing workaround. |

APK signing establishes the publisher/update identity. It is **not** the app-generated, per-installation hardware-backed EC authentication key at Android Keystore alias `phase1_device_key`. Same alias text in another package does not share keys. A different application ID creates a separate sandbox without existing preferences/key access; it does not migrate server authorization. Compatible ordinary signed updates are intended to preserve app data/key access but still need actual qualification. Same-ID debug-to-release transitions with different certificates are normally rejected, not ordinary updates; no installed certificate/device state was inspected.

Recovery uses private `device_binding_recovery` preferences and matches the existing usable key fingerprint plus the configured API URL. Changing API URL changes recovery scope. Manifest backup remains enabled with recovery/legacy-diagnostic exclusions in legacy cloud, modern cloud and device transfer. Source rules do not establish actual transfer, hardware backing or recoverability. Existing-only report/query signing and passive recovery remain unchanged; explicit verification may create an absent authentication key but never automatically replaces an unacceptable existing one.

**Exact proposed implementation, not yet approved (2026-10-07 18:47:24 CEST, UTC+02:00):**

1. In `legacy_android_kotlin\app\build.gradle.kts`, change only `applicationId` from `com.example.myapplication` to `bl.zafo` and `versionName` from `1.0` to `0.1.0-beta.1`; retain `versionCode = 1`. No namespace/source-package or display-name rename.
2. Update only existing client documentation: this README, `.github\docs\plugin_integration_map.md` and ignored client-owned `backend-docs\client-docs\CLIENT_TO_SERVER.md`, recording approval, actual results, allocation status and remaining boundaries.
3. If separately approved, use existing JBR/SDK and wrapper from `legacy_android_kotlin\`: `.\gradlew.bat --offline --no-daemon --console=plain :app:compileReleaseKotlin :app:compileDebugKotlin --dry-run`, inspect the task graph, then the same command without `--dry-run` only if it contains no signing, packaging, installation or connected-device task. Inspect generated release/debug `BuildConfig` and merged manifests for installed ID `bl.zafo`, code `1`, name `0.1.0-beta.1`, namespace/generated code `com.example.myapplication`, and original fully qualified activity/receiver classes. This is compilation/generated-metadata qualification, not APK or device execution. No test/lint/full-suite rerun is proposed for these literal configuration changes.

**Separate approval and actual implementation (updated 2026-10-07 18:51:58 CEST, UTC+02:00):** owner explicitly approved the enumerated slice/qualification through `ask_user`; only the two Gradle literals changed. Initial offline compile-only dry-run succeeded but did not regenerate manifests. Owner then separately approved adding only `:app:processReleaseMainManifest :app:processDebugMainManifest`; expanded dry-run contained no signing, APK packaging, install or device task. Existing wrapper/JBR/SDK offline invocation of those two manifest tasks plus `:app:compileReleaseKotlin :app:compileDebugKotlin` succeeded: 18 actionable tasks, 10 executed / 8 up-to-date. Existing `isInsideSecureHardware` deprecation warning appeared in both compilations; no unrelated correction.

Fresh release/debug generated `BuildConfig` and main merged manifests have exact ID `bl.zafo`, code `1`, name `0.1.0-beta.1`, generated-code package `com.example.myapplication`, and unchanged fully qualified MainActivity/IncomingCallReceiver. AndroidX startup authority and dynamic-receiver permission follow `bl.zafo`; no manual provider/component rename. Release main merged manifest remains non-debuggable with no debug harness/cleartext allowance; debug retains its namespace-qualified harness. This is generated main-manifest/source-compilation evidence only: downstream packaged manifests, instrumentation metadata and existing APKs were not regenerated or qualified.

**Preserved:** API URL, SDKs/dependencies, signing configuration/material, keys, backup/retention/recovery, historical apps and existing installations. No APK signing/packaging, production startup, key access, emulator/device operation, remote request or download. Missing cached tooling/dependencies or an unexpected task graph would have blocked execution, not authorized expansion. No additional implementation/qualification remains authorized by this completed slice; publication and later signing/device operations need separate approval.

**Four remaining top-level milestones:** (1) signed release and install/update qualification; (2) actual Android-device qualification; (3) privacy/operator readiness and required backend controls; (4) hosted qualification and controlled genuine-recipient acceptance. Exact identity/version planning advances milestone 1 only; merged source fixes and scoped synthetic qualification remain completed within their recorded limits. No new top-level milestone or new policy requirement was introduced.
Identity/version implementation and its bounded compilation/main-manifest qualification now advance milestone 1, but signing and actual signed install/update remain incomplete. **Exact next approval:** settle signing backup/access/loss and key provenance, then approve a precisely enumerated secure-local signing setup/execution slice; none of it is authorized here. Publishing these local changes is also separately gated.

**Publication preparation (2026-10-07 19:06:52 CEST, UTC+02:00):** owner approved complete diff review, feature branch, commit/push and PR against main for exactly native app Gradle, README and integration map. Local main/HEAD/origin/main and live code-remote main rechecked at `9def62a954625657caea67a14736b1a27b23f59f`. Full review found no executable-source correction, new file or migration; namespace/API/signing/recovery/SDKs remain unchanged. Ignored linked handoff, generated artifacts and local configuration excluded. Separate approval to merge the exact reviewed published head remains required; no redundant application qualification for unchanged source/documentation-only preparation.

**Later separate gates:** settle signing backup/access/loss arrangements and approve exact signing setup/key provenance and execution; then separately approve a signed candidate and a specifically identified disposable synthetic install/update qualification with network/entrypoint/key-operation boundaries. Production entrypoint can construct recovery/key factories; offline networking alone does not make such execution key-free. No retained AVD reuse is authorized. Store distribution, if later selected, requires a fresh upload-key versus installed app-signing-key design. Server-owned privacy/operator/hosted gates and joint genuine-recipient onboarding remain separate. **BETA-SMS-001 and BETA-BACKEND-001 remain OPEN/BLOCKED; overall closed-beta readiness is not established.**

### Owner-run Android Studio signing setup - proposed procedure only

**Recorded:** 2026-10-07 20:12:07 CEST (UTC+02:00).
**Authority:** owner selected preparation/recording only, explicitly no key generation. The sequence below is a later approval package, NOT instructions to execute now. No wizard opened, password requested, keystore/private key inspected or application action performed.

**Local baseline:** main/HEAD/local origin/main `9cb06944db16b66f482f1357f2d349a14f927452`, committed tree `e7741d3a0ae12e82841c212dda5c09ba1638ee15`, PR #9 merged per prior handoff/local history. Initial worktree/index clean. No remote request today in this planning slice. Installed public Android Studio metadata reports `AI-262.9437.185.2621.16467767` / AndroidStudio2026.2.1; wizard controls/defaults were not inspected, so unavailable controls or differing menu labels are stop conditions, not permission to improvise.

| Proposed item | Exact proposal / unresolved boundary |
| --- | --- |
| New publisher key | Recommend a new dedicated durable key if no established owner-controlled publisher key is being retained. No existing key assumed or inspected; owner still must explicitly choose provenance. This is not a disposable beta key. |
| Container / filename | Request actual JKS keystore format, proposed filename `bl-zafo-release.jks`; extension alone is not proof of format. PKCS#12 is supported in general, not an approved silent substitute/conversion. No raw private key or secret configuration file. |
| Private-key parameters | Proposed RSA 2048-bit, alias `bl-zafo-release`, certificate validity 25 years (use 10,000 days if the approved UI expresses validity in days). These are proposed settings, not verified defaults or owner-approved execution. |
| Primary storage | Proposed durable access-restricted local location outside both repositories, build/session directories and cloud-sync folders. Exact location/access protection pending; never use a Drive/OneDrive sync folder as this proposed primary. |
| Protected backup | Owner expressed preference for Google Drive, with OneDrive an alternative. Recommend locally encrypted archive before upload, separately held recovery secret, and an additional separately stored encrypted offline copy. Exact provider/archive protection/account recovery/retention/access plan pending; upload/copy/account configuration NOT approved. Synchronization alone is not backup. |
| Secret handling | Owner enters passwords privately in trusted Android Studio UI only after approval; keep remember/save-password options off. Independent password-manager recovery proposed, not configured here. No secrets in chat, Git, Gradle/config files, commands, logs, screenshots, PRs or evidence. Do not store archive password beside cloud backup or depend solely on the same PC/cloud account. |
| Certificate subject | Owner chooses intended public certificate subject fields privately before generation; they are public metadata embedded in APK signing, not secrets. No personal details solicited or invented for this procedure. |
| Access / unavailable owner | Sole owner/no deputy is confirmed. Proposed explicit sole-operator restriction: pause signing/releases when unavailable; no shared credentials or implicit backup-custodian signing authority. Still needs policy acceptance. |
| Loss / suspected compromise | Proposed pause signing/distribution; recover original key from protected backup if safe. If lost unrecoverably or compromised, assess separately and communicate with two testers through agreed contact; no promise of ordinary compatible updates with another signer, automatic rotation, uninstall/data clear or device-auth-key/server-binding reset. Contact/escalation and exact policy pending. |

**Deferred generation-only UI sequence - requires resolved decisions AND separate exact execution approval:**

1. Owner confirms provenance, public key/container parameters, public certificate fields, primary storage, encrypted-backup/secret-recovery/access/loss policy and recovery-drill plan. Record only non-secret decisions. Obtain generation-only approval; backup/upload, signing and device operations remain separately bounded.
2. In existing Android Studio open only active `legacy_android_kotlin\` project; module `:app`, installed ID `bl.zafo`, code `1`, name `0.1.0-beta.1`, namespace `com.example.myapplication`. No root Expo project, Gradle signing edit, automatic sync/download, update or device launch. Missing tooling/cache or required downloads stop the procedure.
3. After execution approval, owner opens **Build > Generate Signed App Bundle / APK** (or equivalent installed menu), selects **APK**, then module **app**. Choose **Create new...** in the keystore selector. Navigating this wizard is not APK-signing approval.
4. Specify approved non-synchronized primary path/filename and alias; enter passwords privately, with remembering/saving disabled. Select/establish actual approved JKS/RSA-2048 settings and validity where available; enter owner-approved public certificate fields. If the wizard cannot establish these settings, or effective container/algorithm is unknown, STOP before confirmation and obtain a separately reviewed generation/verification method. Do not assume `.jks` extension establishes JKS and do not substitute shell key-generation commands.
5. Only under the generation-only approval, confirm the new-keystore dialog once. Do not overwrite/import/replace an existing keystore. Then cancel/close the outer signed-APK wizard WITHOUT completing signing or packaging. No APK install, run, permission prompt or production entrypoint.
6. Before any first APK signing/distribution, complete separately approved protected-backup and recovery-check slices. Record only actual container, public certificate SHA-256 fingerprint, public algorithm/validity, alias if approved, non-secret custody/drill outcomes and evidence times; never record passwords, private key bytes or secret location/account details.

**Deferred recovery-check proposal, not authorized:** owner restores/decrypts only the protected backup into an approved controlled temporary location outside repositories/cloud-sync; opens intended entry and compares public certificate fingerprint. Private-key usability additionally needs a separately approved signing/verification of a disposable non-distributed artifact; certificate comparison alone is insufficient. No automatic network/backup upload, personal-device transfer or application installation. Define artifact/tool scope and stop conditions before execution; remove only approved drill copies after success and preserve originals/backups. Record redacted results, not credentials. No recovery drill or signing happens in this documentation slice.

**Next boundary:** settle the pending non-secret choices, then obtain separate approval for generation-only UI execution with verified method/settings; separately approve backup/recovery and eventual candidate signing. Source application configuration is unchanged. This planning advances milestone 1 only; the four remaining top-level milestones and BETA-SMS-001/BETA-BACKEND-001 OPEN/BLOCKED status remain unchanged. No publication of this local documentation without separate approval.

**Publication preparation (2026-10-07 20:15:51 CEST, UTC+02:00):** owner now separately approved complete diff review, feature branch, commit/push and PR against main for README/integration map only, including necessary GitHub code-remote requests. Local main/HEAD/origin/main and live GitHub main rechecked at `9cb06944db16b66f482f1357f2d349a14f927452`. Complete review found no required correction, executable-source change, new file or migration; no application tests/builds rerun for documentation. Ignored linked handoff/artifacts/local configuration excluded. Separate merge approval must identify the exact reviewed published head. Publication does not accept proposed settings/custody policies or authorize key/backup/recovery/signing/device operations.

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
