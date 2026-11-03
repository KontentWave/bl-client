# CB-11: Query authorization rejection uses HTTP 401

**Recorded:** 2026-11-03.
**Scope:** Local native Kotlin/Compose implementation, synthetic JVM regressions and focused documentation only. Source publication was separately approved on 2026-11-03; pre-existing enrollment notes are excluded from that commit/PR and retained locally. No server change, deployment or device installation.
**Client HEAD:** `b47ea667d186ccc0b2f5db09ca87517386a9ea13`, matching the reviewed client baseline. The initial worktree contained an existing modification to `.github/docs/plugin_integration_map.md`; its October 23 enrollment inspection was preserved. No checkout reset.
**Reviewed server baseline:** `a8d2d9a91ac11d55320df434fce68b82470d4de9`.

## Cause and verified server contract

`BlacklistQueryRepositoryImpl` decoded the error correctly but called `SessionRecovery.authorizationRejected` only for HTTP 403 / `blacklist_query_unauthorized`. `SessionAuthorizationTest` supplied 403 implicitly through a shared query/report fixture default, so its recovery tests validated the wrong contract. Existing `BlacklistQueryRepositoryTest` already tested HTTP 401 error decoding, but did not inject recovery and could not detect this defect.

Read-only GitHub inspection at the reviewed server baseline confirmed:

- [`BlacklistQueryUnauthorizedException.php`](https://github.com/KontentWave/bl-server/blob/a8d2d9a91ac11d55320df434fce68b82470d4de9/app/Exceptions/BlacklistQueryUnauthorizedException.php): HTTP 401, code `blacklist_query_unauthorized`, message `The blacklist query could not be authorized.`, `errors.authorization` containing that message, and `meta.retryable=false`.
- [`bootstrap/app.php`](https://github.com/KontentWave/bl-server/blob/a8d2d9a91ac11d55320df434fce68b82470d4de9/bootstrap/app.php): the domain-exception renderer passes through status, field errors and metadata.
- [`CheckBlacklistTest.php`](https://github.com/KontentWave/bl-server/blob/a8d2d9a91ac11d55320df434fce68b82470d4de9/tests/Feature/Blacklist/CheckBlacklistTest.php): missing authorization and invalid signatures both assert unauthorized and the same API code.

The server correctly denies access. This is a client recovery defect, not an authorization bypass. No server implementation change was necessary; no deployed beta API was contacted.

## Changed behavior and preserved boundaries

The production correction is one condition: require HTTP **401**, the exact code **`blacklist_query_unauthorized`**, and the existing `ApiFailure` envelope validation before passing the **rejected request's public key** to `authorizationRejected`. Report rejection remains HTTP **403** / **`device_not_bound`** and its production implementation is untouched.

`SessionRecovery` is unchanged: current live identity takes precedence over persisted identity, stale/different keys cannot invalidate a newer hint, and a rejection cannot erase another backend's recovery record. Same-process failed-removal blocking and visible storage-failure messages remain intact. The query code also covers invalid signatures, so it does not prove that a binding was revoked.

`OnboardingViewModel` collects the shared recovery StateFlow. Removing verification metadata makes `isVerified` false; `MainActivity` already resets its verified destination and renders onboarding. The corrected HTTP regression exercises an already-owned onboarding ViewModel with fake auth and asserts that the rejected state survives local refresh without initiation or verification.

Background `IncomingCallCoordinatorProvider` injects the same process-owned recovery into the data-only query worker. `IncomingCallProcessor` returns QueryFailed/not checked for the rejection, never NoMatch; coordinator warning presentation is restricted to MatchFound. The inspected lookup/recovery path does not launch an Activity.

No key generation/replacement/deletion, automatic SMS/enrollment, request replay/retries, data clearing, schema/config/dependency changes, quiet renewal or offboarding were added. Explicit user-controlled onboarding and existing device keys are preserved.

## Regression evidence

Fixtures now require an explicit HTTP status. Query tests use the representative server message, `errors.authorization` and `meta.retryable=false`; compatibility with empty Laravel `errors: []` and `{}` remains covered.

Before changing production, the following corrected tests were **executed** against the old 403 condition:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Set-Location legacy_android_kotlin
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests '*SessionAuthorizationTest.queryAuthorizationRejectionClearsHintButDoesNotAssertBindingRevocation' --tests '*SessionAuthorizationTest.incomingCallerAuthorizationRejectionClearsRecoveryWithoutReplay'
```

**Failing before:** 2 executed, 2 failed, build exit 1 (41 seconds). Both failures were `assertNull` assertions: live `BindingMetadata` remained after the valid HTTP 401 response. Test sources compiled successfully; these were genuine behavioral failures, not compilation/tooling failures.

After the one-line production correction:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
Set-Location legacy_android_kotlin
.\gradlew.bat --offline --no-daemon --console=plain :app:testDebugUnitTest --tests '*SessionAuthorizationTest' --tests '*SessionRecoveryTest' --tests '*BlacklistQueryRepositoryTest' --tests '*IncomingCallProcessorTest' --tests '*IncomingCallCoordinatorTest' --tests '*CallerLookupDeadlineTest' :app:compileDebugAndroidTestKotlin
```

**Passing after:** build exit 0 (35 seconds); `:app:testDebugUnitTest` actually executed. Fresh JUnit XML reported **64 tests, zero failures/errors/skips** across six suites:

| Executed JVM suite | Tests |
| --- | ---: |
| SessionAuthorizationTest | 8 |
| SessionRecoveryTest | 22 |
| BlacklistQueryRepositoryTest | 2 |
| IncomingCallProcessorTest | 6 |
| IncomingCallCoordinatorTest | 22 |
| CallerLookupDeadlineTest | 4 |

Both corrected previously failing regressions passed in that run. Authorization coverage checks matching live and persisted hint removal, one request/no replay, already-open onboarding state invalidation, background QueryFailed with no overlay, unchanged report 403 rejection, eight malformed 401 envelope cases, unrelated 401 codes, unexpected query 403, 429, 503 and transport failure retaining the exact stored hint. Existing recovery cases were reused for different-key/backend, unsaved newer identity and storage-failure protections.

Before/after authorization XML was preserved in this session's non-repository artifact storage; the final six-suite reports remain under `legacy_android_kotlin\app\build\test-results\testDebugUnitTest`. `git diff --check` passed. Existing Gradle 9.6.0 / AGP 9.4.1 and configured Java 21 daemon toolchain were used without upgrades or dependency installation.

## Checks not executed and limits

- Android instrumentation was **not executed**. `:app:compileDebugAndroidTestKotlin` succeeded as **UP-TO-DATE**, reusing existing compiled artifacts rather than freshly compiling or running them. `SessionNavigationTest.ordinaryQueryRejectionReturnsToOnboardingWithoutSms` and its debug-only fake harness were inspected: they exercise real MainActivity rendering after synthetic recovery invalidation, not the HTTP discriminator. The new HTTP-to-onboarding-state connection was executed on the JVM; no fresh Compose rendering/device claim is made.
- No emulator/real-phone operation, Android Keystore access, SMS, deployed API request, APK packaging/signing, release build/lint or installation was performed. Those are outside this narrow fix; no repository instruction required a broader release qualification.
- Server tests were inspected read-only, **not executed**. GitHub CLI lacked authentication; the available authenticated GitHub source tools supplied the three baseline files instead.
- The full JVM suite and optional PHP interoperability were not run; focused suites cover the changed query/recovery/call integration.
- Persistent-hint removal assertions use the existing synthetic `MemoryBindingStore`, not real Android preferences. Existing storage-failure coverage remains: if removal cannot persist, a later process may still read an old unconfirmed hint. The fix does not claim to resolve that existing storage limitation.

These results establish the narrow CB-11 contract correction only, not overall beta readiness. The broader project sheet and roadmap were not modified.

## Files changed

- `legacy_android_kotlin\app\src\main\java\com\example\myapplication\data\BlacklistQueryRepositoryImpl.kt`: HTTP discriminator only.
- `legacy_android_kotlin\app\src\test\java\com\example\myapplication\data\SessionAuthorizationTest.kt`: explicit status fixtures, backend-shaped query errors and focused regressions.
- `README.md`: corrected query/report status contracts and recovery/background guidance.
- `.github\docs\ADRs\001-key-bound-local-session-recovery.md`: corrected status contract without changing the recovery decision.
- `.github\docs\plugin_integration_map.md`: corrected query boundary and recorded inspected recovery/navigation/background hooks while preserving existing edits.
- `.github\docs\audit\fixes\CB-11-query-authorization-401.md`: this fix record.
