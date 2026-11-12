<div align="center">
<img width="1200" height="475" alt="GHBanner" src="https://ai.google.dev/static/site-assets/images/share-ais-513315318.png" />
</div>

# Run and deploy your AI Studio app

This contains everything you need to run your app locally.

View your app in AI Studio: https://ai.studio/apps/fca6caf0-1c3f-41a5-9902-925c6c36b64d

## Run Locally

**Prerequisites:**  [Android Studio](https://developer.android.com/studio)


1. Open Android Studio
2. Select **Open** and choose the directory containing this project
3. Allow Android Studio to fix any incompatibilities as it imports the project.
4. Create a file named `.env` in the project directory and set `GEMINI_API_KEY` in that file to your Gemini API key (see `.env.example` for an example)
5. Debug builds no longer require `debug.keystore`. A release build is signed only if `my-upload-key.jks` (or `KEYSTORE_PATH`) exists.
6. Run the app on an emulator or physical device
7. If you have already published your app in AI Studio, please [request upload key reset](https://support.google.com/googleplay/android-developer/answer/9842756#zippy=%2Crequest-an-upload-key-reset) in Google Play Console.

---

## 📦 Built APK (v2.7, arm64)

| | |
|---|---|
| File | `dist/BalaSpeak-v2.7-release-arm64.apk` |
| Size | 50.0 MiB (52 462 506 B) |
| SHA-256 | `7cdff9fed2a4004d043d2a8025bc3abfb588d9a58780ba0f288e8ce5cab266f7` |
| Package | `com.aistudio.balaspeak.ytwqzn` · versionName 2.7 · versionCode 18 |
| Requires | Android 7.0+ (minSdk 24), **arm64-v8a** device (most phones since 2017) |
| Signing | APK Signature Scheme v2 (sideload-ready) |

**Install:** copy the APK to the phone → tap it → allow "Install unknown apps" for your
file manager → Install. Or over USB: `adb install -r dist/BalaSpeak-v2.7-release-arm64.apk`.
If an older build signed with a different key is already installed, uninstall it first.

**First run:**
1. Grant microphone / storage / notification permissions.
2. `Settings → API keys` — paste a **Gemini** key (translation + transcription) and,
   optionally, a **Groq** key (fast Whisper transcription). No key is baked into the APK.
3. `Settings → 🎙️ AI Voice Packs` — download Bangla (~114 MB), Hindi (~60 MB) or
   English (~132 MB) for the offline neural voices. Everything else works without them.

A full source analysis and build report is in **[ANALYSIS.md](ANALYSIS.md)**.

## 🔁 How the APK is built (CI)

`.github/workflows/build.yml` runs on every push to any branch:

1. JDK 21 + Android SDK (`platforms;android-36.1`, `build-tools;36.0.0`)
2. `scripts/fetch_sherpa_onnx_aar.sh` → `app/libs/sherpa-onnx-1.13.8.aar` (48 MB, not on Maven)
3. throwaway release keystore → `assembleDebug` + `assembleRelease` (R8)
4. both APKs as workflow artifacts; the signed release APK is committed to `dist/`

The build machine needs ~50 MB of Maven downloads, so the first run takes ~7 minutes.
To rebuild locally, install Android Studio (or JDK 21 + `ANDROID_HOME`) and run:

```bash
bash scripts/fetch_sherpa_onnx_aar.sh
./gradlew assembleRelease          # add -PnoMinify to skip R8 shrinking
```
