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
Which voice models are worth switching to in 2026 — **[docs/VOICE-MODELS-2026.md](docs/VOICE-MODELS-2026.md)**.

## 📦 v3.0 — models pre-unpacked inside the APK (no bzip2 on the phone)

v2.8/v2.9 shipped the models as `.tar.bz2` archives: on first launch the phone copied
132 MB + 3×21 MB into its private folder and then spent minutes running bzip2, which is
both slow and the most fragile step on low-storage devices. Now the archives are unpacked
**on the build machine** and the APK carries the ready-made tree:

```
assets/tts/bn/model.onnx                      (plain file, as before)
assets/tts/extracted/en/…                     Kokoro: model.int8.onnx, voices.bin,
                                              tokens.txt, lexicon-*.txt, espeak-ng-data/
assets/tts/extracted/hi/{pratham,rohan,priyamvada}/…
```

`NeuralVoiceManager.ensurePacksInstalled()` / `downloadPack()` check
`assets/tts/extracted/<lang>` first and, when it exists, just **copy** that tree into
`filesDir` — no download, no decompression, no temporary archive, so nothing can fail
half-way. The slim build has no such folder and keeps the download+bzip2 path unchanged.
`scripts/prepare_builtin_assets.sh` does the unpacking and deletes the archives, so the
APK never carries the models twice.

| | |
|---|---|
| **Built-in (all models inside)** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.0-builtin |
| Asset | `BalaSpeak-v3.0-builtin-allvoices-arm64.apk` — 433,527,779 B (414 MiB) · versionCode 21 |
| SHA-256 | `d48d6950f0501c4a0a8cd041253b379b857880a0bcc6e49dbcf9e35e5787ead9` |
| Bundle | 1,456 voice files · **no `.tar.bz2` inside** (CI asserts it) |
| **Slim (packs on demand)** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.0-slim |
| Slim asset | `BalaSpeak-v3.0-release-arm64.apk` — 52,661,185 B, sha256 `65ffcd36…a1416` |
| Storage | APK is 414 MB (models uncompressed). Budget ~1 GB free for the install plus the one-time copy. Before, the phone additionally held 294 MB of archives and needed room to unzip them — that transient step is gone |

## 🆕 v2.9 — the character voices are now selectable

v2.8 shipped the 31 English characters inside the APK, but the picker stayed empty: the voice
list was a **one-shot snapshot** taken in the ViewModel constructor (`getFriendlyVoicesList()`),
while the bundled models are unpacked **asynchronously** on first launch. v2.9 makes the list
reactive, so it fills itself in as packs become ready:

| | |
|---|---|
| **Built-in (all models inside)** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v2.9-builtin |
| Asset | `BalaSpeak-v2.9-builtin-allvoices-arm64.apk` — 365,571,324 B · versionCode 20 |
| SHA-256 | `1acfb252b1ec3606659ac064868963aabd166eff878d6e8d45bde2be33344165` |
| **Slim (packs on demand)** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v2.9-slim |
| Asset | `BalaSpeak-v2.9-release-arm64.apk` (52,500,113 B, sha256 `ca67618a…e36a4`) + unminified `debug` |
| Verified | `versionName 2.9 / versionCode 20`, v1+v2+v3 signatures, both pickers contain the new empty-state text |

What changed in code: `NeuralVoiceManager.readyLangsFlow` (StateFlow derived from the pack
states) → `TextToSpeechManager.voicesFlow` → `DocumentViewModel.friendlyVoicesList`. The picker
now says *"Preparing the voices…"* while the first-launch unpack runs instead of pretending no
packs exist. Updates install straight over v2.8 — same signing key.

## 🎙️ Built-in voices APK v2.8 (all models inside, fully offline)

**Download:** https://github.com/yeaminuli7/apk-revarcing/releases/tag/v2.8-builtin

| | |
|---|---|
| Asset | `BalaSpeak-v2.8-builtin-allvoices-arm64.apk` |
| Size | 365,571,316 B (349 MiB) · versionCode 19 |
| SHA-256 | `ba829400873a40bd47334d3e2314d35ef222620519de6b29cc7217a51abe2616` |
| Signature | v1 + v2 + v3, **stable key** (see [keystore/README.md](keystore/README.md)) |
| Voices | **English 31 characters**, Bangla 6, Hindi 6 — all inside the APK |
| Engine | Neural only — the Android system TTS engine is never used |
| Built with | `-PnoMinify` (no R8) so character voices cannot be mangled |

### English character cast (v2.8)

Built from the 28 real English speakers in Kokoro v1.0 with per-voice pitch, speed and DSP
timbres — 31 selectable voices, no robotic system voice anywhere:

**Ages** — baby boy/girl, toddler boy/girl, child boy/girl, teen boy/girl, young man/woman,
adult man/woman, middle-aged man/woman, elderly man/woman, grandfather, grandmother.
**Characters** — narrator, hero, villain, **monster**, **demon**, giant, vampire, **robot**,
**alien**, **ghost**, whisper, witch, chipmunk.

Timbre effects live in `PitchTools.applyFx()`: `GROWL` (ring modulation + saturation) for
monster/demon/giant/vampire/witch/villain, `METAL` (ring mod + bit reduction) for robot/alien,
`WHISPER` (low-pass + tremolo) for ghost/whisper.

The slim equivalent (models downloaded on demand) is on the **v2.8-slim** release, which also
carries an unminified `debug` APK for troubleshooting. Install problems → **[INSTALL.md](INSTALL.md)**.

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
