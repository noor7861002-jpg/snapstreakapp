Gallery to SnapStreak — Installation & Quick Guide

Files produced
- Signed APK: app/build/outputs/apk/release/app-release-aligned.apk
- Zip bundle: app-release.zip (root of repo)
- Checksums:
  - /workspaces/snapstreakapp/app/build/outputs/apk/release/app-release-aligned.apk.sha256
  - /workspaces/snapstreakapp/app-release.zip.sha256

Quick install (device)
1. Enable app installs from unknown sources on the Android device.
2. Transfer the APK to device (USB, cloud, or use adb):

```bash
adb install -r /path/to/app-release-aligned.apk
```

If using the zip file on your development machine:

```bash
# unzip then install
unzip app-release.zip
adb install -r app-release-aligned.apk
```

Notes
- This build uses Intent-based sharing to open Snapchat (no Snapchat Creative Kit integration included).
- FFmpeg-based transcoding was removed to simplify the build environment; videos are forwarded as-copied.
- If you want the original behavior (Creative Kit + FFmpeg), provide the original keystore password and I'll revert the dependency changes and rebuild.

Verifying the APK
- SHA-256 of the APK is available at `/workspaces/snapstreakapp/app/build/outputs/apk/release/app-release-aligned.apk.sha256`.
- SHA-256 of the zip is `/workspaces/snapstreakapp/app-release.zip.sha256`.

Developer notes
- To rebuild locally:

```bash
# ensure Android SDK installed and ANDROID_SDK_ROOT set
./gradlew assembleRelease
```

- The signed APK in `app/build/outputs/apk/release` is aligned and signed with a local test keystore `my-release-key.jks` (created during this run). Replace with your production keystore and update `app/build.gradle` signingConfigs to sign with your keystore.

Support
- Tell me if you want me to: (A) upload the zip somewhere, (B) revert temporary code changes and re-enable Creative Kit, or (C) produce an emulator/device smoke test script.
