# Auto BT Speaker Lock

Android Studio source project for a Hisense HS2000 Bluetooth Classic A2DP speaker.

## Important limitations

This is **not a true exclusive Bluetooth lock**. Android 11+ public SDK cannot force A2DP connect/disconnect, prevent other phones from connecting to the soundbar, block unpairing in system Settings, or guarantee a permanent background process. A best-effort reflection call to hidden `BluetoothA2dp.connect()` is included but is often blocked. The app monitors the real A2DP connection and never treats a request as confirmed connection. The app's hardcoded passwords are not secure against APK inspection; this is local UI gating, not device security.

## Android build

- compileSdk 34, targetSdk 34, minSdk 26, Kotlin 1.9.24, AGP 8.5.2, Gradle 8.7, Java 17.
- GitHub Actions installs Java, Android SDK, Gradle, builds `:app:assembleDebug`, and uploads the installable APK artifact.
- No Gradle wrapper JAR is needed for GitHub Actions because the workflow installs Gradle 8.7 directly. The wrapper properties file is included for future local Android Studio use; a complete local wrapper would also need `gradle-wrapper.jar` and wrapper scripts.

## Tablet-only upload to GitHub

1. Download and unzip the project ZIP on your tablet.
2. Open github.com in Chrome and enable **Desktop site** if necessary. Sign in and create a new **private** repository (e.g. `Auto-BT-Speaker-Lock`).
3. Select **Add file → Upload files**. Upload the extracted project files **preserving their folder structure**. GitHub's browser upload may not preserve folders on all mobile browsers. If so, use a mobile Git client with folder upload support, or use github.dev to create paths manually. Do not upload only the ZIP: Actions needs the extracted files.
4. Ensure `.github/workflows/build.yml` is present in the repository. Commit to `main`.
5. Open the repository **Actions** tab, select **Build Android APK**, then **Run workflow** (or wait for the push-triggered build).
6. When the run succeeds, open its **Artifacts** section and download `Auto-BT-Speaker-Lock-debug-APK`.
7. Unzip that downloaded artifact to obtain `app-debug.apk`. Open it on the tablet and allow installation from your browser/files app when Android prompts.
8. Open the app, enter PIN `7391`, grant Nearby devices permissions, and pair Hisense HS2000 using system Bluetooth settings if not already paired. Android may require disabling battery optimization for more reliable monitoring.

## Security note

This project includes the passwords requested in source code. Use a private repository, and do not use these credentials for anything else. The master reset clears app preferences, **not** Android's system Bluetooth bond.
