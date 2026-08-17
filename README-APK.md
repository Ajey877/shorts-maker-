# Shorts Maker APK

This branch adds a free GitHub Actions debug APK build.

## Build

1. Open the GitHub repository.
2. Select **Actions**.
3. Select **Build APK**.
4. Run the workflow.
5. Open the completed workflow run.
6. Download the `shorts-maker-debug-apk` artifact.
7. Extract the ZIP and install the APK on Android.

## Important

The current Android app is a native Compose app. It is not yet connected to the uploaded Next.js/FFmpeg backend. The APK build proves the Android project can be compiled; YouTube processing still requires a reachable backend service.
