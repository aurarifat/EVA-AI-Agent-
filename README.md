# EVA AI - Autonomous Android Assistant

EVA AI is an autonomous, on-device AI assistant designed for Android phones and tablets (including Infinix Xpad 20). EVA AI combines conversational intelligence with Android's native Accessibility Service to read active screen contents, navigate apps, and execute on-screen automation commands reliably.

---

## 🚀 Automated Debug APK Builds & GitHub Releases

This repository includes a fully automated GitHub Actions CI/CD workflow (`.github/workflows/build-apk.yml`) that builds and publishes a debug APK on every push to `main`.

### How It Works
1. **Trigger:** Every push to the `main` branch (or manual run via `workflow_dispatch`).
2. **Environment:** Ubuntu runner with Temurin Java 17 and Gradle 9.3.1.
3. **Build:** Compiles `:app:assembleDebug`.
4. **Artifacts:**
   - Uploads `app-debug-apk` directly to the workflow run artifacts.
   - Creates a GitHub Release tagged `debug-apk-build-<run_number>-<run_attempt>` with the uncompressed APK (`eva-ai-debug-build-<run_number>.apk`) under the release **Assets** section.
   - Automatically provides standard `Source code (zip)` and `Source code (tar.gz)` release downloads.

### 📲 Downloading and Installing the APK
1. Go to the **Releases** tab of this repository.
2. Under **Assets**, tap the `.apk` file (e.g., `eva-ai-debug-build-1.apk`).
3. Open the downloaded APK on your Android device to install.

---

## 🛠️ Key Capabilities & Architecture

- **Accessibility Service (`EvaAccessibilityService`):**
  - Reads active window node hierarchies (`rootInActiveWindow`).
  - Dispatches touch gestures, text typing, taps, and scrolls.
  - Automatically redacts sensitive and password inputs for privacy.
- **Floating Assistant Overlay (`EvaOverlayService`):**
  - Persistent, non-intrusive floating chat-head overlay providing continuous task transparency and quick controls.
- **Voice & Chat Interface:**
  - Conversational voice commands and intuitive Jetpack Compose Material 3 UI.
- **Multi-Device Support:**
  - Responsive layouts supporting compact phone screens and expanded tablet displays.

---

## 📋 Permissions & Setup

To enable EVA AI's on-screen automation features:
1. Open the app on your device.
2. Grant the required permissions when prompted:
   - **Accessibility Service:** Go to *Settings > Accessibility > Installed Apps > EVA AI* and enable the toggle.
   - **Display over other apps (Overlay):** Enable permission to display floating controls.
   - **Microphone:** Enable for voice commands.
