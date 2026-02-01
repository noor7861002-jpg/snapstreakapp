# Gallery to Snap Streak

This repository contains a scaffold Android app that allows selecting media from gallery and sharing to Snapchat with EXIF timestamp adjustment and a local streak counter.

Quick build (from repo root, requires Android SDK + JDK 17):

```bash
./gradlew assembleDebug
./gradlew installDebug
```

APK output: `app/build/outputs/apk/release/app-release.apk` after release build.

Notes:

CI Build

This repository includes a GitHub Actions workflow that builds debug and release APKs on push to `main` or when triggered manually.


If you want a signed release APK in CI, provide these secrets in your repository settings:

If no keystore secrets are provided, the workflow generates an ephemeral keystore and signs the release APK with it.
Completed additions
-------------------
- Added optional Snapchat Creative Kit SDK dependency and a `SnapchatManager` helper that prefers the SDK and falls back to Intent sharing.
- Added `colors.xml` and `dimens.xml` resources and updated ProGuard rules for Snapchat.

Security and notes
------------------
- The Creative Kit SDK may require app registration with Snap for full features; the helper gracefully falls back to Intent sharing if the SDK isn't initialized or available.
- Make sure to test on a device with Snapchat installed. If you want to use Creative Kit fully, register your app at https://kit.snapchat.com/ and add the configuration documented by Snap.
# snapstreakapp