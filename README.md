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

## ⚡ v3.3 — nothing runs at first launch: the installer places the models

The old flow copied 414 MB into the app's folder the first time you opened it — slow, and
the step that kept failing. Now the models ship as **native libraries**:

* `scripts/prepare_builtin_assets.sh` unpacks every archive and writes each file as
  `app/src/main/jniLibs/arm64-v8a/libvNNNN.so`, plus a manifest
  `assets/tts/libmap.tsv` (`lang ⇥ relative-path ⇥ lib-name`);
* the manifest sets `android:extractNativeLibs="true"` and Gradle uses
  `jniLibs { useLegacyPackaging = true; keepDebugSymbols += "**/*.so" }`, so the
  **package manager itself** extracts all ~414 MB into `/data/app/…/lib/arm64/` while
  installing the APK;
* on first launch `materializeBundledPack()` only creates **symlinks** from
  `files/tts/<lang>/…` to those files — about 1,456 links, well under a second, no data
  copy. If a device refuses symlinks it copies from the lib dir, and if the libs were
  never extracted it streams them out of the APK — but there is no "preparing voices"
  wait any more.

Install takes a little longer (that is the point — it is the step that now does the work),
and the app opens with every voice ready.

| | |
|---|---|
| **Built-in** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.3-builtin |
| Asset | `BalaSpeak-v3.3-builtin-allvoices-arm64.apk` — 340,417,734 B (325 MiB) |
| SHA-256 | `6d5480e625c3ad7fef3203acc3e34228c1349968d994ffc98002562097bb29ae` |
| Contents | 1,455 model libs + `assets/tts/libmap.tsv` · `extractNativeLibs=true` (verified in the built APK) |
| **Slim** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.3-slim (22,454,958 B) |
| Version | 3.3 (versionCode 24) · same key as v2.8–v3.2 |

## 🩺 v3.2 — the real reason English/Hindi never worked (and the crash on टेस्ट)

**Root cause found.** `findFirst()` matched *files* only:

```kotlin
private fun findFirst(dir: File, suffix: String) =
    dir.walkTopDown().firstOrNull { it.isFile && it.name.endsWith(suffix) }
```

but `espeak-ng-data` is a **directory**. So `isInstalled("en")` / `isInstalled("hi")` were
always `false`, every copy ended with *"Pack files missing after extraction"*, and the
engine was handed `dataDir = ""`. Only Bangla (which has no espeak data) ever installed —
exactly the "one voice model copied, the rest error" behaviour. Fixed with a
directory-aware `findDirNamed()` used by install-detection, `filesStatus()` and the
engine config; Kokoro/Piper now get a real `dataDir` (and a nested `dict`).

**Crash on টেস্ট.** Three hardening changes:

* every voice hot path catches `Throwable`, not `Exception` — OOM and
  `UnsatisfiedLinkError` no longer escape into a coroutine and kill the app;
* the MMS engine no longer feeds a hardcoded `"x"` tensor: it asks the model for its
  input name, and the 114 MB direct-ONNX model is excluded from auto warm-up;
* a rolling diagnostics log (`com.example.util.CrashLog`, uncaught-exception handler +
  explicit entries) is readable in **Settings → 🎙️ AI Voice Packs → লগ**.

Auto warm-up is now off (`autoWarmup = false`): loading 100–140 MB of model immediately
after writing 400+ MB was the main memory-pressure crash.

| | |
|---|---|
| **Built-in** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.2-builtin |
| Asset | `BalaSpeak-v3.2-builtin-allvoices-arm64.apk` — 433,527,759 B (414 MiB) |
| SHA-256 | `fcd3c016893970359d88532fe4958b5c351e4bd8752f533955f377a80a6e8bc8` |
| **Slim** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.2-slim (52,677,567 B, sha256 `e5ea41f5…8725e`) |
| Version | 3.2 (versionCode 23) · same key as v2.8–v3.1 |

## 🔧 v3.1 — the picker can no longer be empty, and the app explains itself

v3.0 unbundled the bzip2 step, but three things could still leave the voice list empty
or the app silent with no explanation. All three are fixed:

1. **A failed engine warm-up erased the whole cast.** `isReady()` was
   `isInstalled() && state !is Failed`; the automatic warm-up after provisioning sets
   `Failed` when a small phone runs out of memory, which removed **all 31 English
   characters** from the picker. Readiness now depends on the files only — a warm-up
   problem is shown as a warning, never as a missing voice.
2. **The list only contained *ready* languages.** Now every language the APK ships
   (`assets/tts/extracted/<lang>` or a bundled model file) is listed immediately, with a
   "⏳ মডেল কপি হচ্ছে" badge until its files land. Voices exist from the first frame.
3. **Provisioning could fail silently.** Copying now checks free storage first, reports
   `Preparing(copied, total)` progress, is single-flight per language, and can be
   restarted from the UI — including by simply tapping a voice, which now triggers the
   copy instead of doing nothing.

**Settings → 🎙️ AI Voice Packs** is now a diagnostics panel: per language it shows state,
`n ফাইল / x MB` on disk, where the files come from (bundled vs download), the exact pack
path, the missing-file list, the last error, plus **এখনই কপি করুন** (re-provision) and
**টেস্ট** (synthesise one sentence and report `✅ ms/KB` or `❌ reason`).

| | |
|---|---|
| **Built-in** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.1-builtin |
| Asset | `BalaSpeak-v3.1-builtin-allvoices-arm64.apk` — 433,527,754 B (414 MiB) |
| SHA-256 | `6d4ba93180862605825ab6a3834b62575bf9a7d3360e595ca7a17dc4593586f7` |
| **Slim** | https://github.com/yeaminuli7/apk-revarcing/releases/tag/v3.1-slim (52,661,195 B, sha256 `c146ccda…d0a7d`) |
| Version | 3.1 (versionCode 22) · same signing key as v2.8–v3.0 |

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
