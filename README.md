# Blacklist Client

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

Connected-device UI and instrumentation tests:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Release signing and production backend readiness must be finalized before live beta distribution. A debug build is not a signed beta release.

## Repository boundaries

- Build output, dependency caches, IDE settings, local SDK configuration, environment secrets, and signing keystores are excluded.
- `backend-docs` is a local symbolic link into the separate backend workspace and is intentionally not published here.
- Keep production signing credentials outside version control.
