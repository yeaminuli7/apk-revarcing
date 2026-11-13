# BalaSpeak v2.7 — Source Analysis & APK Build Report

_Generated 2026-11-12 · branch `arena/05c3abe6-apk-revarcing`_

---

## 1. What was uploaded

`BalaSpeak-v2.7-source-builtin (1).zip.xml` is **not XML** — it is a plain ZIP
(`PK\x03\x04`) whose extension was rewritten. It unpacks to a single `balaspeak/`
folder: a complete **Jetpack Compose Android project** exported from Google AI Studio
(app id `fca6caf0-1c3f-41a5-9902-925c6c36b64d`), 96 files, 1.5 MB.

> **Naming caveat:** despite `-builtin` in the filename, the voice models are **not**
> bundled. `app/src/main/assets/tts/` contains only `bn_vocab.json` (927 B), and
> `NeuralVoiceManager` downloads the packs at runtime from Hugging Face / k2-fsa.
> So this is the **normal v2.7 build (~50 MB)**, not the "all voices inside the APK"
> variant described in `BUILT-IN-VOICES-ROADMAP.md` (that one would be ~373 MB).

## 2. Tech stack

| Layer | Implementation |
|---|---|
| Language / UI | Kotlin 2.2.10, 100 % Jetpack Compose + Material 3 (`material-icons-extended`) |
| Build | AGP **9.1.1**, Gradle **9.3.1**, compileSdk **36.1** (`minorApiLevel`), minSdk 24, targetSdk 36 |
| JDK | 21 in CI (project sets Java 11 source/target) |
| Persistence | Room 2.7.0 (+KSP), 3 entities: `documents`, `dub_cue_states`, `media_tasks_history` |
| Networking | OkHttp 4.10 + Retrofit 2.12 + Moshi 1.15 |
| On-device ML | **sherpa-onnx 1.13.8** (TTS), **onnxruntime-android 1.20.0** (MMS Bengali), **ML Kit translate 17.0.3** |
| Media | `MediaExtractor`/`MediaMuxer` hand-written mixer (no FFmpeg dependency) |
| Background work | `DubbingForegroundService` (`mediaProcessing` FGS type) + `StopProcessingReceiver` |

47 Kotlin files, ~16 300 LOC. `DocumentViewModel.kt` alone is **131 KB** (~3 000 LOC) —
the single biggest maintenance risk in the codebase.

## 3. Feature map (code walk-through)

* **Dubber (v1)** — `DubberScreen.kt`, `DocumentViewModel.kt`
  video + SRT → per-cue TTS → `AudioVideoMerger` muxes dubbed audio back into the video.
  Volume (video 0.20 / TTS 2.00), per-cue speed & pitch, cue lock 🔒 / voice pin 📌,
  persisted in Room (`dub_cue_states`).
* **Dubber V2** — `ui/DubberV2Screen.kt`, batch items, block-by-block navigation.
* **Translator** — `TranslatorScreen.kt`, `util/SubtitleTranslator.kt`, `OfflineSubtitleTranslator.kt`
  4-strategy cascade: Gemini API → `translate.googleapis.com/translate_a/single` →
  `clients5.google.com/translate_a/t` → `api.mymemory.translated.net`, plus offline ML Kit.
* **Transcriber (video → subtitles)** — `util/VideoTranscriber.kt`
  audio track extraction, **Groq Whisper** (`api.groq.com/openai/v1/audio/transcriptions`)
  and **Gemini** (`generativelanguage.googleapis.com/v1beta/models/...:generateContent`).
* **Neural voices** — `tts/neural/NeuralVoiceManager.kt` (39 KB)
  * Bangla — MMS-TTS ONNX (~114 MB, `itantra-tts-onnx`), single speaker → 6 personas via pitch/speed
  * Hindi — 3 Piper voices: pratham / rohan / priyamvada (~20 MB each)
  * English — Kokoro v1.0 int8, 53 speakers, default `af_heart` (~132 MB)
  * `PitchTools.kt` = WSOLA pitch-shift + time-stretch, so child/elderly personas keep timing
  * Falls back to system `TextToSpeech` when a pack is absent (with a visible warning)
* **File access** — `ui/FileExplorerDialog.kt` (39 KB), in-app browser over shared storage.
* **Settings** — `SettingsDialog.kt` (46 KB): theme, thread count, RAM buffer, translation
  concurrency, hardware acceleration, API keys.

## 4. Secrets & network posture

* `.env` in the export holds only the placeholder `GEMINI_API_KEY=MY_GEMINI_API_KEY`
  (`secrets-gradle-plugin` injects it as `BuildConfig`). The built APK therefore ships
  **no working key** — the user must paste a Gemini/Groq key in **Settings**, where it is
  stored via `util/ApiKeyStore.kt` in `getExternalFilesDir()/rec/api.xml`.
* `ApiKeyStore` rejects short strings and placeholders (`MY_GEMINI_API_KEY`, `AIza...`, …),
  so the placeholder can never be mistaken for a real key.
* Runtime endpoints are all HTTPS and are listed in §3 — note that Google's **unofficial**
  `translate_a` endpoints are used as a free fallback; they can rate-limit or break
  without notice and are not covered by any ToS guarantee.

## 5. Permissions — Play Store risk

`MANAGE_EXTERNAL_STORAGE` (all-files access) + `READ_EXTERNAL_STORAGE` /
`WRITE_EXTERNAL_STORAGE` (legacy) + `requestLegacyExternalStorage="true"` are declared.
This is what makes the in-app file explorer work on arbitrary folders, but Google Play
requires a **justification review** for all-files access and routinely rejects media/dubbing
apps that could use the Storage Access Framework / MediaStore instead. Sideloading is unaffected.
`RECORD_AUDIO` and `POST_NOTIFICATIONS` are also requested; the app has no `INTERNET`-less path.

## 6. APK build (what was actually executed)

The sandbox has **no JDK/Android SDK**, and Maven Central, Google Maven, `services.gradle.org`
and GitHub release assets are firewalled — so a local build was impossible. Build was run on
**GitHub Actions** (unrestricted network, preinstalled Android SDK), driven by a rewritten
`.github/workflows/build.yml`:

1. checkout → Temurin **JDK 21** → `sdkmanager` licenses + `platforms;android-36.1`, `build-tools;36.0.0`
2. `bash scripts/fetch_sherpa_onnx_aar.sh` → `app/libs/sherpa-onnx-1.13.8.aar` (48 MB, not on Maven)
3. generate a release keystore (`keytool`, RSA 2048, 30-year validity, alias `upload`)
4. `assembleDebug` + `assembleRelease` (R8 minify + resource shrinking)
5. upload both APKs as workflow artifacts, and publish the installable APK to `dist/` on the branch

Project tweaks made for CI:
* `gradle.properties`: `-Xmx1400m` → `-Xmx3g` (R8 OOMs below that), workers 2 → 4
* `app/build.gradle.kts`: `isMinifyEnabled`/`isShrinkResources` can be disabled with `-PnoMinify`
* `proguard-rules.pro`: added `-keep class ai.onnxruntime.**` — ONNX Runtime's JNI looks up
  Java classes by name, so renaming them would silently break the Bangla MMS voice pack
* `.gitignore`: `/build`, `/staging`

`ABI filter = arm64-v8a` only (project default). The APK will **not** run on x86_64
emulators or on 32-bit-only ARM devices.

## 7. Verification of the produced APK

Final artifact: `dist/BalaSpeak-v2.7-release-arm64.apk` (also published on the branch)

```
file      : BalaSpeak-v2.7-release-arm64.apk     50.0 MiB (52 462 506 B, 455 entries)
sha256    : 7cdff9fe d2a4004d 043d2a80 25bc3abf b588d9a5 8780ba0f 288e8ce5 cab266f7
package   : com.aistudio.balaspeak.ytwqzn
version   : 2.7 (versionCode 18)
sdk       : minSdk 24  targetSdk 36  compileSdk 36.1
signature : APK Signing Block v2 ✓  (v1 absent — expected, minSdk ≥ 24)
cert      : CN=BalaSpeak, O=BalaSpeak, L=Dhaka, C=BD
            SHA-256 D6:E0:B4:44:AA:7F:61:0E:…:3C:F0:51:19 · valid 2026-11-12 → 2056-11-04
zip       : integrity OK · classes.dex 4.3 MB
native    : lib/arm64-v8a/  libonnxruntime.so (21.2 MB) · libtranslate_jni.so (15.6 MB)
            libsherpa-onnx-jni.so (4.6 MB) · libsherpa-onnx-c-api.so · libsherpa-onnx-cxx-api.so
            libonnxruntime4j_jni.so · libandroidx.graphics.path.so
```

Checked with `androguard` 4.1.4 (PyPI): binary `AndroidManifest.xml` parses (package,
versions, sdk levels, 13 permissions, 1 activity + 5 services), certificate chain is
self-consistent, V2 signature block present, ZIP integrity clean, single ABI.

**Keystore note:** the signing key is generated inside the CI job for each build, so the
certificate fingerprint changes every time — an APK built later cannot be installed *over*
this one without uninstalling first. For an app you will keep updating (or publish on
Play), add repository secrets `KEYSTORE_B64`, `APK_STORE_PASSWORD` and decode them in the
workflow instead of generating a throwaway key.


## 9. Built-in voices variant (v2.7-builtin)

Requested after the first build: models **inside** the APK, device TTS not used.

* `NeuralVoiceManager` already supported this — every `PackDownload` carries an
  `assetPath`, and `ensurePacksInstalled()` copies from APK assets when the file is
  present, otherwise downloads. Bundling therefore needed **no engine changes**,
  only the assets plus one script: `scripts/fetch_builtin_voices.sh`.
* The device TTS engine was already unused in v2.7 (no `TextToSpeech` object is
  created anywhere; `TextToSpeechManager` is a neural-only facade). Remaining
  cleanup: the "Open System TTS Voice Settings" button in Settings was removed.
* Build: `assembleRelease -PnoShrink` (resource shrinking disabled so nothing can
  strip `assets/tts`), R8 minification kept.

Verified in CI and through the GitHub API:

```
asset      : BalaSpeak-v2.7-builtin-allvoices-arm64.apk
size       : 353,786,364 B (338 MiB)          (API size == CI size)
sha256     : 99360cc61f27e620242285c94fd5cc5409d67897db10500e1479d71875d81541
             == GitHub's own asset digest → upload intact
assets/tts : bn/model.onnx                114,417,798
             en/kokoro-...-v1_0.tar.bz2   132,353,410
             hi/pratham/...tar.bz2         20,987,965
             hi/rohan/...tar.bz2           21,064,499
             hi/priyamvada/...tar.bz2      21,097,319
             bn_vocab.json                      927
```

Trade-offs to be aware of:

* Install footprint is roughly APK + unpacked models (~650-700 MB) because
  sherpa-onnx needs real file paths, so assets must be extracted to `filesDir`
  on first launch (30-60 s).
* Both APKs are signed with a throwaway CI key generated per run, so switching
  between the slim and built-in builds requires an uninstall first.
* versionCode is still 18 for both — if you publish them side by side, give the
  built-in build its own higher versionCode.


## 10. v2.8 — signing fix and English character cast

**Install failure ("no signature" / "app not installed").** Root cause: every build up to
v2.7 generated a throwaway signing key inside CI, and AGP only produced a v2 signature
(minSdk 24 ⇒ v1 skipped). Consequences:

* a new build can never be installed over the previous one — Android rejects a different
  signing identity;
* installers and OEM package managers that only parse the v1 (JAR) signature report the
  APK as unsigned.

Fixes in v2.8:

1. one **stable keystore** committed at `keystore/balaspeak-release.p12` (alias `upload`,
   RSA 2048, valid to 2056) — documented as a public sideload key in `keystore/README.md`;
2. `enableV1Signing` + `enableV2Signing` + `enableV3Signing` in `app/build.gradle.kts`;
3. CI asserts `META-INF/*.SF` exists in every APK and prints the certificate with
   `keytool -printcert -jarfile`;
4. debug builds are signed with the same key, so debug/release APKs can replace each other;
5. versionCode 18 → 19, versionName 2.7 → 2.8.

**Voice cast.** `PersonaSpec` gained 25 English-only roles, `VoiceCast.Role` gained
`onlyLang` so they appear only for the English pack, and `NeuralVoiceManager` maps each role to
a Kokoro speaker id, a speed factor, a pitch factor and an optional `PitchTools.Fx`
(`GROWL` / `METAL` / `WHISPER`, newly implemented). The built-in-voices APK is compiled with
`-PnoMinify` so R8 cannot rename or strip any of that.

Artifact: `v2.8-builtin` release, 365,571,316 B,
sha256 `ba8294…babe2616`, v1+v2+v3 signed, all five model files verified inside the APK.

## 8. Caveats

* **Not runtime-tested** — no device or emulator here. R8 rules for Room/Moshi/sherpa-onnx/
  ONNX Runtime/ML Kit were reviewed and extended, but the only way to be sure is to install
  and dub one short clip. The **debug** APK (no minification) is kept as a fallback.
* **No API key baked in** — subtitle translation, transcription and Gemini "smart" paths
  need a key entered in Settings. Offline neural voices work without any key once downloaded.
* **First-run size** — voice packs add ~114 MB (bn), ~60 MB (hi) or ~132 MB (en) of downloads.
* **Language of the UI/docs** is Bengali; string resources are minimal (`app_name` only),
  so localisation would be a refactor, not a translation job.


## 11. v2.9 — why the new voices were invisible (and the fix)

**Symptom.** v2.8 contained 31 English characters, yet the speaker picker listed none of them.

**Root cause.** The list was computed once, eagerly:

```kotlin
// DocumentViewModel (v2.8) — frozen at construction time
val friendlyVoicesList: StateFlow<List<FriendlyVoice>> = MutableStateFlow(
    ttsManager.getFriendlyVoicesList()   // VoiceCast.voicesFor(neuralVoices.readyLangs())
).asStateFlow()
```

`readyLangs()` only returns languages whose packs pass `isReady()` → `isInstalled()` (a disk
check). On the built-in APK those packs do not exist until `ensurePacksInstalled()` has copied
and untarred ~294 MB from assets — which runs in the background *after* the ViewModel is built.
So the snapshot was empty (or partial), the picker rendered "No voice pack for this language",
and nothing ever recomputed it. The same snapshot fed the per-block voice picker in
`DubberV2Screen`.

**Fix.** Make the dependency reactive:

```kotlin
// NeuralVoiceManager
val readyLangsFlow: StateFlow<Set<String>> = _states
    .map { packs.map { it.langCode }.filter { isReady(it) }.toSet() }
    .stateIn(scope, SharingStarted.Eagerly, readyLangs())

// TextToSpeechManager
val voicesFlow: StateFlow<List<FriendlyVoice>> = neuralVoices.readyLangsFlow
    .map { VoiceCast.voicesFor(it) }
    .stateIn(coroutineScope, SharingStarted.Eagerly, getFriendlyVoicesList())

// DocumentViewModel
val friendlyVoicesList: StateFlow<List<FriendlyVoice>> = ttsManager.voicesFlow
```

`_states` is already written on every transition (`Downloading → Extracting → Ready`, `Failed`)
and `installedCache` is invalidated on install/delete, so each pack that becomes usable
re-emits and the cast grows: 31 English roles appear the moment the English pack is ready.

**UI honesty.** Both pickers now show *"Preparing the voices… first launch unpacks the bundled
models (a minute or two)"* while the list is empty, instead of claiming no pack exists.

**Lost-work hazard (build environment).** The sandbox occasionally re-clones `.git` to the
initial commit while the working tree keeps its files; always `git fetch` +
`git checkout -f -B <branch> origin/<branch>` before editing to avoid committing against a
stale history.
