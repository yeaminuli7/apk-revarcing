# BalaSpeak v2.7 — Built-in Neural Voices: Complete Roadmap

## What This Build Does

This version of BalaSpeak has **all neural TTS voices bundled inside the APK**.
When a user installs the app, every voice works immediately — no internet,
no downloads, no setup. The old system TTS voices are completely removed.

---

## Voice Packs Included

| Language | Engine | Model | Size (compressed) | Voices |
|----------|--------|-------|-------------------|--------|
| English | Kokoro v1.0 | `kokoro-int8-multi-lang-v1_0` | ~126 MB | 53 studio speakers (af_heart etc.) |
| Hindi | Piper (VITS) | pratham / rohan / priyamvada | ~20 MB each | 3 speakers |
| Bangla | MMS-TTS | `itantra-tts-onnx` bn model | ~109 MB | 1 speaker, 6 personas via pitch/speed |

**Total APK size: ~373 MB** (includes ~48 MB sherpa-onnx native library + app code)

---

## How It Works

### 1. Bundled Assets
Voice model files live in `app/src/main/assets/tts/`:

```
app/src/main/assets/tts/
├── en/
│   └── kokoro-int8-multi-lang-v1_0.tar.bz2     (~126 MB)
├── hi/
│   ├── pratham/vits-piper-hi_IN-pratham-medium-int8.tar.bz2   (~20 MB)
│   ├── rohan/vits-piper-hi_IN-rohan-medium-int8.tar.bz2       (~20 MB)
│   └── priyamvada/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2 (~20 MB)
├── bn/
│   └── model.onnx                               (~109 MB)
└── bn_vocab.json                                (Bangla tokenizer vocab)
```

### 2. Auto-Install on First Run
`NeuralVoiceManager` runs `ensurePacksInstalled()` in its `init` block:
- Copies each `.tar.bz2` from assets → `filesDir/tts/<lang>/`
- Extracts the archive (bzip2 + tar)
- Flattens nested directories if needed
- Warms up the engine with a test synthesis
- Marks the pack as `Ready`

This happens automatically the first time the app opens. No user action needed.

### 3. Voice Selection
`VoiceCast.voicesFor()` returns **only** neural voices from installed packs.
Each voice name shows its source engine:

```
Young woman · যুবতী (from Kokoro)
Young man · যুবক (from Piper)
Elderly man · বৃদ্ধ পুরুষ (from MMS-TTS)
```

### 4. No System TTS
`TextToSpeechManager` was fully rewritten:
- All `android.speech.tts.TextToSpeech` references removed
- Speech synthesis goes through `NeuralVoiceManager` + `MediaPlayer`
- Subtitle dubbing uses neural synthesis only
- `VoiceCast.pickBase()` returns `null` (no system voice fallback)

---

## Source Files Changed

| File | Change |
|------|--------|
| `tts/neural/NeuralVoiceManager.kt` | Added `assetPath` field to `PackDownload`; added `ensurePacksInstalled()` + `copyAssetToFile()`; `init` calls `ensurePacksInstalled()` |
| `tts/VoiceCast.kt` | `voicesFor()` returns neural-only voices with `(from Engine)` suffix; `resolve()` works without system voices; `pickBase()` returns null |
| `tts/TextToSpeechManager.kt` | Fully rewritten — neural-only, all system TTS removed, speak/preview via `NeuralVoiceManager` + `MediaPlayer` |
| `ui/VoicePackDialog.kt` | Rewritten for built-in voices only — no download/delete buttons, per-role preview |
| `ui/DocumentViewModel.kt` | Removed `Voice` import; `friendlyVoicesList` uses `MutableStateFlow` |
| `VoiceDialogs.kt` | Removed "Install a clearer voice pack" button |
| `ui/DubberV2Screen.kt` | Removed "Install a clearer voice pack" button |

---

## Build Instructions

### Prerequisites
- JDK 21
- Android SDK (platform 36, build-tools 36.0.0)
- ~2 GB RAM for Gradle (or swap)

### Step 1: Download Dependencies

**sherpa-onnx AAR** (~48 MB) → `app/libs/`:
```
https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.8/sherpa-onnx-1.13.8.aar
```

**Voice packs** → `app/src/main/assets/tts/`:
```
# English — Kokoro v1.0 (~126 MB)
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2
→ app/src/main/assets/tts/en/

# Hindi — Piper pratham (~20 MB)
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-pratham-medium-int8.tar.bz2
→ app/src/main/assets/tts/hi/pratham/

# Hindi — Piper rohan (~20 MB)
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-rohan-medium-int8.tar.bz2
→ app/src/main/assets/tts/hi/rohan/

# Hindi — Piper priyamvada (~20 MB)
https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-piper-hi_IN-priyamvada-medium-int8.tar.bz2
→ app/src/main/assets/tts/hi/priyamvada/

# Bangla — MMS-TTS (~109 MB)
https://huggingface.co/sriram09764/itantra-tts-onnx/resolve/main/bn/model.onnx
→ app/src/main/assets/tts/bn/
```

### Step 2: Configure SDK
Edit `local.properties`:
```
sdk.dir=/path/to/your/android-sdk
```

### Step 3: Build
```bash
./gradlew assembleDebug --no-daemon \
  -Dorg.gradle.jvmargs="-Xmx1536m -XX:MaxMetaspaceSize=512m" \
  -Dorg.gradle.daemon=false
```

Output: `app/build/outputs/apk/debug/app-debug.apk` (~373 MB)

### Step 4: Install
- Enable "Install from unknown sources" on the phone
- Copy the APK to the phone and install
- First launch: voices extract automatically (takes ~30-60 seconds)
- After that: all voices work offline

---

## Technical Details

### Pack Directory Layout (on device)
```
filesDir/tts/
├── en/          ← Kokoro: model.int8.onnx, voices.bin, tokens.txt,
│                    lexicon-us-en.txt, espeak-ng-data/
├── hi/
│   ├── pratham/ ← Piper: hi_IN-pratham-medium.onnx, tokens.txt, espeak-ng-data/
│   ├── rohan/   ← Piper: hi_IN-rohan-medium.onnx, tokens.txt, espeak-ng-data/
│   └── priyamvada/ ← Piper: hi_IN-priyamvada-medium.onnx, tokens.txt, espeak-ng-data/
└── bn/          ← MMS: model.onnx (direct ONNX Runtime inference)
```

### Engines
- **Kokoro** (English): sherpa-onnx `OfflineTts` with `OfflineTtsKokoroModelConfig`
- **Piper** (Hindi): sherpa-onnx `OfflineTts` with `OfflineTtsVitsModelConfig`
- **MMS** (Bangla): Direct ONNX Runtime (`OrtSession`) with custom tokenization
  - Tokenization replicates HuggingFace `VitsTokenizer` (char ids + intersperse pad)
  - Vocab from `tts/bn_vocab.json` (bundled asset)

### Persona System
6 roles per language, mapped to engine parameters:
- **Kokoro**: Different speaker IDs (af_heart=3, am_michael=16, etc.) + pitch shaping
- **Piper**: Different voice directories (pratham/rohan/priyamvada) + speed/pitch
- **MMS**: Single model, personas via DSP pitch-shift + time-stretch

### Audio Output
All engines normalize to **16 kHz mono 16-bit PCM** so the dubbing merger
can mix segments of any language without format conflicts.

---

## Project Info

- **App ID:** `com.aistudio.balaspeak.ytwqzn`
- **Version:** 2.7 (versionCode 18)
- **minSdk:** 24 | **targetSdk:** 36
- **ABI:** arm64-v8a only
- **API Key:** `GEMINI_API_KEY` in `.env` files (placeholder — add real key for AI features)

---

## Known Limitations

1. **APK size (~373 MB)** — large because all voice models are embedded.
   Google Drive / Dropbox upload limit is 100 MB, so the APK cannot be
   uploaded to those services directly. Use file.io, WeTransfer, or a
   direct download server instead.

2. **Debug build** — unsigned. For Play Store release, create a release
   keystore and use `assembleRelease`.

3. **First-run extraction** — takes 30-60 seconds and needs ~1 GB free
   storage (extracted models are larger than compressed archives).

4. **Low-memory devices** — engine warm-up may fail on devices with <2 GB RAM.
   The app shows an error message in this case.

---

## File Structure

```
balaspeak/
├── app/
│   ├── libs/
│   │   └── sherpa-onnx-1.13.8.aar          ← DOWNLOAD SEPARATELY (~48 MB)
│   ├── src/main/
│   │   ├── assets/tts/                      ← DOWNLOAD SEPARATELY (~300 MB)
│   │   │   ├── en/kokoro-int8-multi-lang-v1_0.tar.bz2
│   │   │   ├── hi/pratham/...tar.bz2
│   │   │   ├── hi/rohan/...tar.bz2
│   │   │   ├── hi/priyamvada/...tar.bz2
│   │   │   ├── bn/model.onnx
│   │   │   └── bn_vocab.json
│   │   ├── java/com/example/
│   │   │   ├── tts/
│   │   │   │   ├── TextToSpeechManager.kt   ← REWRITTEN (neural-only)
│   │   │   │   ├── VoiceCast.kt             ← MODIFIED (neural-only + source labels)
│   │   │   │   └── neural/
│   │   │   │       ├── NeuralVoiceManager.kt ← MODIFIED (auto-install from assets)
│   │   │   │       ├── PitchTools.kt
│   │   │   │       ├── VoicePersona.kt
│   │   │   │       └── WavIo.kt
│   │   │   ├── ui/
│   │   │   │   ├── VoicePackDialog.kt       ← REWRITTEN (built-in UI)
│   │   │   │   ├── DocumentViewModel.kt     ← MODIFIED
│   │   │   │   └── DubberV2Screen.kt        ← MODIFIED (removed system TTS button)
│   │   │   └── VoiceDialogs.kt              ← MODIFIED (removed system TTS button)
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradlew
```

---

## Changelog (Built-in Voices)

- **v2.7-builtin**: All voice packs bundled in APK; auto-install on first run;
  system TTS voices removed; voice names show source engine
  (e.g. `garl_20_from kokoro` style → `Young woman · যুবতী (from Kokoro)`)
