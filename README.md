# HUNG CAM — Professional Native Android Camera App

- **Application ID:** `com.hungbell.hungcam`
- **Tech Stack:** Native Android (Kotlin, Jetpack Compose, Material 3, Camera2 API, MediaCodec, MediaMuxer, MediaStore, Room, DataStore)
- **Target Device Class:** Xiaomi Redmi Note 13 Pro 5G & modern Android devices (minSdk 24, targetSdk 36)

## Important Note About Building an APK from ZIP

This is a **native Android Gradle project** (NOT a static HTML/JS website).  
Websites that convert HTML ZIPs (e.g. Website2APK / AppsGeyser) cannot compile native Kotlin/Camera2 code. To turn this project into an installable `.apk`, use one of the options below:

### Option 1: Build APK directly in Google AI Studio (Easiest)
Open the **Settings / Menu** in AI Studio and choose **Generate APK / AAB**.

### Option 2: Automatic Cloud Build via GitHub Actions (Free)
1. Push this project (or upload the exported ZIP) to a GitHub repository.
2. Open the **Actions** tab and run **Build HUNG CAM Debug APK** (`.github/workflows/build-apk.yml` is already included).
3. Download `hung-cam-debug-apk` (`app-debug.apk`) from the workflow artifacts.

### Option 3: Local Build with Android Studio or Command Line
1. Ensure **JDK 21** and **Android SDK (API 36)** are installed.
2. Prepare the debug keystore from `debug.keystore.base64`:
   ```bash
   base64 -d debug.keystore.base64 > debug.keystore
   ```
3. Build the APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
   The generated APK will be at:
   `app/build/outputs/apk/debug/app-debug.apk`
