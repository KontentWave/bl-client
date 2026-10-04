# Client Feature and Module Integration Map

**Last inspected:** 2026-10-04 21:24:44 CEST (UTC+02:00).
**Baseline:** current working-tree source; not a release or deployment certification.
**Scope:** client integrations, including native modules and cooperating features. Backend details below are external documentation, not inspected backend implementation.

## Authority and Status

- [README](../../README.md#L7-L15) identifies `legacy_android_kotlin/` as the active Kotlin / Compose Android beta target. The directory name does not mean it is archived.
- Root Expo code, `android/`, and `modules/` are historical work. The Phase 6 Expo pivot is cancelled; older pivot instructions in [the project sheet](../../backend-docs/blacklist_project_sheet.md) must not override the README.
- [Copilot instructions](../copilot-instructions.md#L3-L11) require updating this map after relevant integration inspections: feature-to-feature, module-to-module, native bridges and client-to-backend contract touchpoints. Coverage includes OS hooks and API adapters. The filename `plugin_integration_map.md` is retained for existing references; the native app is not an application plugin registry.
- `legacy_android_kotlin/` is the active Gradle build root for Android Studio import/sync; repository root `blacklist-client/` is not a Gradle build root.
- `backend-docs/` is a local symbolic link into a separate backend workspace, intentionally unpublished ([README](../../README.md#repository-boundaries)). External links can be unavailable in a fresh clone. This tracked file owns client findings; [the backend map](../../backend-docs/plugin_integration_map.md) owns backend findings.
- **Active** means wired in inspected client source, not physically validated today. **Historical** means outside the shipping beta target. **Stub** means a placeholder. **Documented backend** means referenced documentation only.

## Active Kotlin Hooks and Dependencies

Native source paths below refer to `legacy_android_kotlin/app/src/main/java/com/example/myapplication/`.

| Boundary | Hook / caller -> implementation | Shared dependency and risk | Source |
| --- | --- | --- | --- |
| UI shell and navigation | `MainActivity` owns onboarding, report and query ViewModels through injectable factories; screen callbacks submit explicit commands. Onboarding observes shared `SessionRecovery`. | Matching key-associated metadata restores workspace navigation only, not current backend authorization. Resume performs passive local lookup, never an API/SMS replay. Debug fake-backed subclass can exercise the production shell without production factories. | [MainActivity][native-ui], [session recovery][recovery] |
| Onboarding | Ad URL submission -> `AuthRepository.initiateAuth`; OTP submission -> `SignedRequestFactory.createVerifyRequest` -> `AuthRepository.verifyAuth`. | Server supplies challenge, masked phone and expiry. No free reporter phone entry. Failed/blocked initiation retains the existing client challenge; expiry never automatically submits a command. | [OnboardingViewModel, lines 68-264][onboarding] |
| Auth diagnostics | `AuthRepositoryImpl.verifyAuth` emits debug-only request diagnostics through `logVerifyRequestDiagnostics`. | Debug logs include challenge id and SHA-256 digests/lengths of request values. Keep logs debug-only and avoid leaking logcat captures outside trusted debugging channels. | [AuthRepositoryImpl, lines 70-149][auth-repo] |
| Reporting | `ReportViewModel` submission -> `ReportRepositoryImpl` -> `ReportApi`; well-formed HTTP 403 `device_not_bound` invalidates the matching live/persisted local hint. | Match live identity first, including failed persistence; stale stored identity cannot clear newer differently keyed live metadata. Temporary failures, throttling and `signature_invalid` do not establish binding loss. Existing report normalization/signing defect CB-02 remains separate. | [ReportRepositoryImpl][report-repo], [canonical payloads][canonical], [session recovery][recovery] |
| Manual query and Shield | Query ViewModel and `IncomingCallProcessor.create` use `BlacklistQueryRepositoryProvider` -> `BlacklistQueryRepositoryImpl`, sharing the process-local recovery provider. | Well-formed HTTP 403 `blacklist_query_unauthorized` clears the matching hint; this code combines signature/binding rejection and does not prove revocation. No lookup probe is sent on restore. Call events remain independent triggers, not recovery replays. | [Repository provider][query-provider], [processor][call-processor], [session recovery][recovery] |
| HTTP transport | Repository factories -> `ApiClientFactory` -> Retrofit / OkHttp / Gson. | Disables connection retries and redirects. Network interceptor removes `Retry-After` from 503 responses to prevent OkHttp's zero-delay replay. Re-enabling retries can resend ambiguous initiation requests. | [ApiClientFactory, lines 14-61][api-factory] |
| Error and cooldown policy | `ApiFailure` maps errors for all four operations; UI ViewModels use retry cooldowns. | Accepts empty `errors` as `[]` or `{}`. HTTP 429 uses valid positive integer `meta.retry_after`, then seconds-only header, then 60 seconds; positive delays capped at 86,400 seconds. Generic throttling does not establish limiter scope or challenge exhaustion. | [ApiFailure, lines 16-88][api-failure], [README, lines 34-49](../../README.md#L34-L49) |
| Shared signing | `SecuritySignedRequestFactory` -> normalized PEM -> `CanonicalPayloadFactory` -> `SecurityManager`. | Verify/report/query share ordered UTF-8 JSON, slash escaping, PEM normalization, EC signing and key identity. Report/query require an existing key; only explicit verification can create an absent key. Backend signature compatibility remains unchanged. | [SignedRequestFactory][signing], [CanonicalPayloadFactory][canonical] |
| OS call receiver | Manifest `PHONE_STATE` receiver -> `IncomingCallReceiver.onReceive` on ringing -> `goAsync` / IO coroutine -> processor. | Number availability and permissions depend on Android/device behavior. Receiver constructs its own pipeline, outside the Activity's verified-navigation gate. Broadcast lifetime, repeated events and concurrent requests need device-level validation. | [Manifest, lines 4-7 and 28-35][manifest], [receiver, lines 14-33][call-receiver] |
| Caller lookup | Processor -> `CallerNumberNormalizer` -> SHA-256 hasher -> signed query. Empty features skip warning; matched features invoke presenter. | Default local-number country code is `+421`; formatting changes can change lookup hashes. Do not assume this lightweight normalizer fully validates E.164 numbers. | [Processor, lines 42-109][call-processor], [normalizer, lines 3-30][normalizer] |
| Shield overlay | `WindowManagerShieldWarningPresenter` displays/dismisses warnings. Activity launches overlay settings and requests phone permissions. | Requires `SYSTEM_ALERT_WINDOW`, caller permissions and OS support. Presenter lifetime/dismissal and OEM restrictions are separate from successful API queries. | [Overlay presenter][overlay], [MainActivity][native-ui], [manifest][manifest] |
| Shield status refresh | Receiver pipeline writes `ShieldLiveStatusStore`; Activity reads on creation, permission callback, manual refresh and `ON_RESUME`. | SharedPreferences is a diagnostics handoff, not a continuously observed UI stream or session store. Permission readiness is now explicitly distinguished from authorization/lookup success. Concurrent events can overwrite the latest snapshot. | [Status store][shield-store], [MainActivity][native-ui] |

## API and Canonical Payload Boundaries

Paths include `/api/`; Retrofit interfaces use paths relative to `BuildConfig.API_BASE_URL`.

| POST endpoint | Client interface | Signed canonical fields, in order | Consequence |
| --- | --- | --- | --- |
| `/api/auth/initiate` | [AuthApi][auth-api] | No device signature in this request; sends `ad_url`. | May dispatch SMS. Ambiguous errors must not trigger automatic resend. |
| `/api/auth/verify` | [AuthApi][auth-api] | `challenge_id`, `public_key` | OTP is sent in the request but is not a canonical signed field. Server binding authorizes later signed APIs. |
| `/api/reports` | [ReportApi][report-api] | `client_phone_number`, `feature`, `public_key` | Reporting sends the target phone; backend normalization/hashing and duplicate rules own persistence. |
| `/api/blacklist/check` | [BlacklistApi][blacklist-api] | `target_hash`, `public_key` | Caller lookup sends a hash. Unknown and Level 1-only targets return empty features under the documented contract. |

Evidence: [canonical payload implementation][canonical]; external [API contract](../../backend-docs/BACKEND_API_CONTRACT.md), authentication, reporting and blacklist-check sections. There is no documented session-validation endpoint. Do not change canonical field order, escaping or PEM formatting independently on either side.

## Shared Stores and Tables

| Store / table | Owners and consumers | Coupling / handling rule |
| --- | --- | --- |
| Android Keystore alias `phase1_device_key` | Native `SecurityManager`; shared signing for verify/report/query and Shield. | `lookupExistingKey` is non-creating/non-deleting and checks private-key availability/hardware suitability. Report/query use existing-only signing. Explicit OTP verification may create an absent key; unacceptable existing keys now fail without replacement. Legacy ensure/get/sign APIs still create absent keys and must not be used for recovery. See [SecurityManager][security]. |
| SharedPreferences `device_binding_recovery` | `SessionRecoveryProvider` -> onboarding, report, manual query and Shield query factories. | Version, backend scope, SHA-256 of SPKI public key, historical verification time only. No recipient, OTP, challenge, ad URL or signed request. Excluded from legacy cloud backup and API 31+ cloud/device transfer; matching key is still mandatory. Missing/invalid/mismatched metadata/key removes only the hint; unavailable keys retain disk metadata and block restored navigation. |
| In-memory ViewModel state | Activity and feature screens; `RetryCooldown` for manual command gating. | OTP, challenge and monotonic cooldown are not process-death persistence. Matching recovery metadata restores historical workspace access without commands; backend authorization remains checked only on ordinary signed operations. See [README](../../README.md). |
| SharedPreferences `shield_live_status` | Processor/presenter result writes; Activity/home reads. | Keys: `stage`, `raw_incoming_number`, `normalized_number`, `target_hash`, `features`, `error_message`, `retryable`, `overlay_state`, `overlay_message`, `updated_at`; features use `||` separator. Raw/normalized caller numbers persist locally: review retention, backup and disclosure, rather than claiming a hash-only client. See [store, lines 47-102][shield-store]. |
| Backend `otp_challenges`, `device_bindings` | Documented: initiation/verification own challenges; verification updates bindings; report/query authorize bindings. | Binding replacement affects all signed client operations. These are server tables, not client databases. |
| Backend `clients`, `reports`, `client_feature_levels` | Documented: reporting writes hashes/counts/promotion; query reads promoted features. | Unique reporter/feature semantics and materialized Level 2 state connect reports to query/Shield results. A client key reset is not a report-data reset. |
| Backend cache/locks and `scraper_proxy_attempts` | Documented: API abuse controls/SMS budgets and operator proxy telemetry. | Client countdowns do not certify shared limiter storage or effective hosted budgets. Proxy probe success does not establish onboarding delivery. |

Backend table evidence is **documentation-only**: [backend map, lines 24-36](../../backend-docs/plugin_integration_map.md#L24-L36). Its warning about raw phone storage in OTP/binding tables and enumerable phone hashes supersedes blanket "zero-knowledge" interpretations; no backend migrations or production database were inspected for this map.

## Configuration and Dependency Owners

| Owner | Keys / dependencies | Consumers and boundary |
| --- | --- | --- |
| [Native Gradle][native-gradle] | `BuildConfig.API_BASE_URL`, `applicationId`, SDK levels; Compose, lifecycle, coroutines, Retrofit, OkHttp, Gson, core-library desugaring. Versions live in [the version catalog](../../legacy_android_kotlin/gradle/libs.versions.toml). | All native API factories use the build-time URL, which also scopes recovery metadata. Current source includes `/api/` with a trailing slash. Changing Expo settings does not change Kotlin. Release signing/production readiness remain gates, not established by debug builds. |
| [Native manifest][manifest] and [security manager][security] | `INTERNET`, `READ_PHONE_STATE`, `READ_CALL_LOG`, `SYSTEM_ALERT_WINDOW`; hardware-backed EC key requirement. | Manifest declarations do not grant runtime permissions. TEE/KeyMint or StrongBox backing is accepted; this is not backend-verified hardware attestation. |
| [Historical Expo config, lines 10-24][expo-config] and [env, lines 5-12][expo-env] | `extra.apiBaseUrl`, Android package / iOS bundle ID `com.blacklist.client`; `expo-router`, `expo-dev-client`. | Expo runtime reads Constants/config with a fallback URL. Native Kotlin identity is `com.example.myapplication`; these are separate app sandboxes, not a key/session migration. |
| [Historical root package.json](../../package.json#L6-L39) | Expo, React Native, Expo Modules Core, Router, Axios, TypeScript and related UI dependencies. | Root `npm run android` / prebuild targets historical Expo, not the Kotlin beta. Do not treat CNG regeneration as a native Kotlin build step. |
| Documented backend config | `services.escort_portal.*`, `services.sms.*`, `scraping.proxy.*`, `security.*`, `reporting.{threshold,features}`, cache/lock settings; `APP_ENV`, `ESCORT_PORTAL_DEVELOPMENT_PHONE_OVERRIDE`. | Backend-only controls affect identity, SMS delivery, limits and feature labels. Client must not embed provider credentials or assume old trial/override notes describe current hosted values. See [backend map, lines 38-60](../../backend-docs/plugin_integration_map.md#L38-L60). |

## Historical Expo Native Bridges and Plugins

| Integration | Hook / implementation | Status and risky coupling |
| --- | --- | --- |
| CryptoVault JS wrapper | [Wrapper, lines 10-60][expo-vault-wrapper] looks up `ExpoCryptoVault` through Expo Modules Core. | **Historical.** Missing native module returns unavailable status; key/sign operations throw. Source registration is not evidence of a linked, executed bridge. |
| Native module registration | [expo-module.config.json, lines 1-8][expo-module-config] declares Android/iOS modules; [Android bridge, lines 6-55][expo-vault-native] exposes async functions. | **Historical.** Android uses default alias `phase1_device_key`; `ensureKeyPair`/`deleteKeyPair` accept custom aliases while get/sign use the default. Custom-alias semantics are inconsistent. Same alias text across different app IDs does not mean shared keys. iOS behavior was not inspected here. |
| Shield wrapper | [src/modules/shield/index.ts, lines 3-19][expo-shield] exports `ShieldStub`. | **Stub / historical.** Reports unknown readiness and idle status; it does not connect the Kotlin call receiver or overlay to Expo. |
| Config plugin list | [app.config.ts, line 11][expo-config] registers `expo-router` and `expo-dev-client`. | **Historical.** No custom Shield config plugin is registered in this list. Do not confuse the historical native-module plan with completed Shield bridge wiring. |

## Verification Scope and Open Gates

- Publication-review validation: **80 JVM tests across 13 suites**, zero failures/errors/skips; debug application and instrumentation APK compilation/packaging; lint **0 errors / 35 warnings**. Read-only review found an ignored authorization rejection after failed metadata persistence; live-first matching and two new regressions correct it, including stale persisted identity. Four additional warnings are KTX preference-edit suggestions; direct commits retain the boolean durability result.
- Earlier today, before the publication-review correction, executed **7 filtered UI tests** on the inspected disposable offline Android 14 AVD: four `CooldownScreenTest`, one `CooldownLifecycleTest`, two `SessionNavigationTest`; zero failures/errors/skips. New tests execute the production `MainActivity` navigation/lifecycle code via debug-only fake factories, including retained ViewModel ownership through Activity recreation and authorization-rejection navigation. No production API/Keystore factory is constructed. UI execution was not repeated for the later persistence-edge correction.
- Emulator startup ignored the unusable proxy after a connection timeout; it is not claimed as an enforced isolation layer. Test-time isolation was checked from disabled Wi-Fi/mobile data, airplane mode and no usable external default route, alongside fake-only dependencies.
- A separate **actual OS force-stop/fresh-launch** check of the fake-backed debug subclass confirmed different PIDs (5687 -> 5810), recovered home/trust text, four synthetic metadata fields and zero fake command replay. This is not hardware-key/physical-device/reboot or hosted evidence. A first UI run failed because an older synthetic public fixture was rejected by Android's EC parser; a valid public-only fixture fixed it. A System UI ANR dialog obscured the first process-check assertion; after choosing Wait, the complete process check passed. Neither failed attempt is counted as passing.
- CB-01's supported local recovery portion is implemented; [ADR-001](ADRs/001-key-bound-local-session-recovery.md) records its trust/backup/key boundaries. Real Android Keystore/process restart, physical-device/reboot behavior, live onboarding and signed release remain separate gates. No hosted API, SMS, proxy probe, real key operation or binding reset was performed; the disposable emulator was stopped without wiping/deleting its AVD.
- Documented backend coordination baseline is `6c96c1d4cee869a7cfbe878c24def13206715181`, following the corrective merge of KontentWave/bl-server#5. This is attached handoff evidence, not a deployed revision or independently rerun backend suite. CB-06 requires no Android wire change.
- Review caller diagnostics retention/backup, concurrent/repeated call handling, broadcast/network lifetime and overlay dismissal on real target devices with approval. Source wiring alone is insufficient.
- Treat dated hosted Vonage trial/recipient-override evidence in backend project sheets as history. Current deployment, SMS provider, genuine recipient identity, limiter scope and backend concurrency require backend-owner validation.

## Maintenance Rules

1. After each integration inspection, replace the affected rows and risks with current findings; update the inspection date/scope and source links. Do not append duplicate session findings.
2. Record each boundary's hook/caller, callee, shared store/table, config owner, dependencies, risky coupling and verification limits. Mark new findings as source-inspected, documentation-only or runtime-tested as appropriate.
3. Read linked definitions and consumers before changes. Recheck both manual query and Shield after shared query/signing changes; all APIs after transport/error changes; server compatibility after canonicalization/normalization changes.
4. Move durable architectural decisions and trade-offs into ADRs; leave a short current-state pointer here. Backend-owned decisions belong in the backend ADRs; client decisions live in `.github/docs/ADRs/`. CB-01 is recorded in ADR-001.
5. Preserve the repository boundary: maintain client facts here, link to the backend-owned map rather than copying its internals, and do not edit the external workspace implicitly.
6. Never include API keys, OTP values, private keys, real caller/recipient numbers or sensitive provider payloads. MCP setup credentials belong outside this tracked document.

[native-ui]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/MainActivity.kt
[onboarding]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/ui/onboarding/OnboardingViewModel.kt
[auth-repo]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/AuthRepositoryImpl.kt
[report-repo]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/ReportRepositoryImpl.kt
[query-provider]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/BlacklistQueryRepositoryProvider.kt
[api-factory]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/remote/ApiClientFactory.kt
[api-failure]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/ApiFailure.kt
[signing]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/security/SignedRequestFactory.kt
[canonical]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/security/CanonicalPayloadFactory.kt
[security]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/security/SecurityManager.kt
[manifest]: ../../legacy_android_kotlin/app/src/main/AndroidManifest.xml
[call-receiver]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/shield/IncomingCallReceiver.kt
[call-processor]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/shield/IncomingCallProcessor.kt
[normalizer]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/shield/CallerNumberNormalizer.kt
[overlay]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/shield/WindowManagerShieldWarningPresenter.kt
[shield-store]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/shield/ShieldLiveStatus.kt
[auth-api]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/remote/AuthApi.kt
[report-api]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/remote/ReportApi.kt
[blacklist-api]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/data/remote/BlacklistApi.kt
[native-gradle]: ../../legacy_android_kotlin/app/build.gradle.kts
[recovery]: ../../legacy_android_kotlin/app/src/main/java/com/example/myapplication/session/SessionRecovery.kt
[expo-config]: ../../app.config.ts
[expo-env]: ../../src/config/env.ts
[expo-vault-wrapper]: ../../src/modules/cryptovault/index.ts
[expo-module-config]: ../../modules/expo-crypto-vault/expo-module.config.json
[expo-vault-native]: ../../modules/expo-crypto-vault/android/src/main/java/expo/modules/cryptovault/ExpoCryptoVaultModule.kt
[expo-shield]: ../../src/modules/shield/index.ts
