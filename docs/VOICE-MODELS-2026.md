# Voice models worth using — 2026 evaluation for BalaSpeak

_Researched 2026-11-12. Scope: offline, on-device TTS for Bangla / Hindi / English that can
actually be shipped inside an Android APK on an arm64 phone._

---

## 0. TL;DR — ranked by quality gain per unit of effort

| # | Change | Gain | Effort | Verdict |
|---|---|---|---|---|
| 1 | **Bangla: MMS-TTS → Indic Tiny TTS Bengali** (22.5 M, 24 kHz, female + male) | 2.2–2.5× fewer character errors than MMS on the same test; real female/male voices instead of pitch-shifted ones; **commercial-friendly licence** | 1 new ONNX engine (same pattern as the existing `mms_direct` path) | **Do it** |
| 2 | **Bangla: add a text normaliser** (digits, Banglish/Latin, dates) before synthesis | On the public Bangla bench MMS's number CER is **55.8 % → 2.7 %** with normalisation; MMS cannot read digits at all today | small Kotlin util | **Do it first** — cheapest win |
| 3 | **Hindi: Piper ×3 → Indic Tiny TTS Hindi** (same engine as #1) | modern architecture, male + female, one model instead of three archives, −60 MB | free once #1 exists | **Do it** |
| 4 | **English: keep Kokoro v1.0** (`af_heart`) | already the best on-device English quality/size ratio; nothing better fits in an APK | none | **Keep** |
| 5 | Optional: **Supertonic-3** for English (+ 30 other languages) | 44.1 kHz output, 99 M params, RTF ≈ 0.3 on weak hardware, 145 MB int8 | supported by sherpa-onnx ≥ 1.12.29 (our AAR is 1.13.8) | Try, A/B vs Kokoro |
| 6 | Optional: **ZipVoice** zero-shot voice cloning | any voice from a 3–10 s clip, on-device | sherpa-onnx ≥ 1.12.11, EN/ZH only | Nice feature, not for Bangla |

**Licence landmine:** the Bengali pack we ship today is **Meta MMS-TTS, which is CC-BY-**N**C-4.0
(non-commercial)**. That is fine for personal use but must change before any Play Store /
paid release — see §4.

---

## 1. Bangla — where the quality actually is

Public, reproducible benchmark on Bangla text-to-speech (CER % on 50 news clips, CPU runner):

| System | Licence | CER % | RTF (CPU) | Phone-feasible |
|---|---|---:|---:|---|
| OmniVoice | CC-BY-NC | **8.3** | ≈ 30× slower than real time | ✗ |
| Indic-Parler-TTS (880 M) | Apache-2.0 | 14.3 | ≈ 7.5× slower | ✗ |
| **Meta MMS-TTS (what we ship)** | **CC-BY-NC-4.0** | **27.3** | 0.33 (fast) | ✓ |
| VoxCPM2 + Bangla LoRA | Apache-2.0 (base) | 37.0 | needs GPU | ✗ |

Source: [bangla-tts-bench](https://github.com/muhib-karim/bangla-tts-bench).
Two things fall out of it: (a) the models that sound best are **far too slow for a phone**,
and (b) **most of MMS's error comes from its text front-end, not its voice** — it cannot read
digits or Latin script at all (numbers 55.8 %, Banglish 61.5 %), and normalising the input drops
numbers to 2.7 %.

### The one model that fits: Indic Tiny TTS Bengali

`uploader: sulabhkatiyar/indic-tiny-tts-bengali` — 22.5 M params, 24 kHz, **female + male**
voice, ONNX Runtime, no GPU.

| Model | Parameters | CER % (FLORES-200 news) | CER % (held-out Rasa) |
|---|---:|---:|---:|
| **Indic Tiny TTS Bengali** | 22.5 M | **2.10** | **3.58** |
| ai4bharat/IndicF5 | 350.6 M | 1.70 | 3.78 |
| ai4bharat/indic-parler-tts | 937.8 M | 2.27 | 7.05 |
| ai4bharat/vits_rasa_13 | 31.3 M | 3.18 | 5.00 |
| **facebook/mms-tts-ben (current)** | 29.1 M | 4.86 | 9.01 |
| piper bn_BD-google-medium | 15.8 M | 5.48 | 7.13 |

Same error rate as IndicF5 at **1/16 the size and ~180× faster**; ~7× faster than real time on a
single x86 core, so a phone runs it comfortably. Licence: CC-BY-4.0 + IITM IndicTTS terms
(attribution required, commercial use allowed).

Caveats: it is **not** a sherpa-onnx model family, so it needs a direct ONNX Runtime path (the
app already has one for `mms_direct`); the published audio samples are read-speech news style,
so prosody on dramatic dialogue must be A/B'd by ear; and the numbers/CER claims come from the
model author's own benchmark.

---

## 2. Hindi

| Option | Size | Notes |
|---|---|---|
| **Indic Tiny TTS Hindi** (22.5 M) | ~90 MB fp32 / less if int8 | same family as the Bangla winner, male + female, one model, ONNX |
| Kokoro v1.0 Hindi (`hf_alpha`, `hf_beta`, `hm_omega`, `hm_psi`) | already shipped for English | free to add (voices exist in the model we bundle) but community grades these Hindi voices only **C** |
| Piper ×3 (current) | 60 MB | dated VITS voices, works, no licence risk |

Cheapest experiment: enable the four Kokoro Hindi voices we *already* ship (zero new bytes) and
compare against the current Piper trio. If quality is close, dropping Piper saves 60 MB; if not,
Indic Tiny TTS Hindi is the upgrade path.

---

## 3. English / multilingual

* **Kokoro v1.0 (current)** — 82 M, Apache-2.0, 53 voices, `af_heart`. Still the best
  quality-per-byte on-device English model; keep it.
* **Supertonic-3** — 99 M, 31 languages, 44.1 kHz, RTF ≈ 0.3 on an e-reader CPU, sherpa-onnx
  support since 1.12.29, 145 MB int8, MIT code / OpenRAIL-M weights. **No Bengali** in the 31
  languages (Hindi is there). Good candidate if we want 44.1 kHz and one model for many
  languages.
* **Kitten TTS** (nano 33 MB / mini) — Apache-2.0, English only, very fast, quality below Kokoro.
* **ZipVoice / PocketTTS** — zero-shot cloning (3–10 s reference clip). EN/ZH focused, so a
  useful *feature* for English dubbing, not a Bengali solution.
* **Chatterbox** (Resemble, MIT, 110 M "nano") — genuinely good English, ~3× real time on 8 CPU
  cores, but **not a sherpa-onnx model** → a second runtime and a much larger native footprint.

---

## 4. Licences (check before publishing)

| Model | Licence | Commercial? |
|---|---|---|
| Meta MMS-TTS `ben` (current Bangla) | **CC-BY-NC-4.0** | ✗ non-commercial only |
| Indic Tiny TTS (bn, hi, …) | CC-BY-4.0 + IITM IndicTTS | ✓ with attribution |
| Kokoro-82M | Apache-2.0 | ✓ |
| Piper voices | varies per voice (many public domain / CC-BY) | ✓ check per voice |
| Indic-Parler-TTS | Apache-2.0 | ✓ |
| IndicF5 | MIT (weights card says commercial use restricted — verify) | ⚠ check |
| Supertonic-3 | MIT code / OpenRAIL-M weights | ✓ with use restrictions |
| OmniVoice | CC-BY-NC | ✗ |
| Groq Whisper / Gemini (transcription, translation) | vendor terms | ✓ as service |

---

## 5. "Best quality, cloud" — if offline is not a hard requirement

Benchmarked MOS on Bengali, 2026: **Sarvam Bulbul 4.3** > ElevenLabs 4.0 > Google Cloud 3.8 >
AI4Bharat open models 3.7. ElevenLabs is the strongest alternative for Bengali specifically.
A hybrid design (offline packs by default + an opt-in "premium cloud voice" for final renders)
would beat anything we can package, at the cost of an API key and per-minute billing — worth
considering as a separate feature, not as a replacement for the built-in packs.

---

## 6. Suggested next step

1. Add the Bangla/Hindi **text normaliser** (pure Kotlin, no model) and ship it in the existing
   builds — measurable improvement today.
2. Add an `indic_tiny` engine next to `mms_direct` in `NeuralVoiceManager`, download
   `indic-tiny-tts-bengali` + `-hindi` as new voice packs, keep MMS as a fallback until the new
   voices are verified by ear on real subtitle lines.
3. Rebuild the built-in APK: Bangla (tiny, 2 voices) + Hindi (tiny, 2 voices) + English (Kokoro).
   Expected size ≈ **50 MB base + 90 MB bn + 90 MB hi + 132 MB en ≈ 360 MB** — about the same as
   today, with better voices and a clean licence.
