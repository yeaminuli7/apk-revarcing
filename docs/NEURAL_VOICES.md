# 🎙️ BalaSpeak AI Voice Packs (অফলাইন নিউরাল ভয়েস)

BalaSpeak 2.1 থেকে অ্যাপে **অন-ডিভাইস নিউরাল TTS** চলে — ছয়টি চরিত্র (বৃদ্ধ পুরুষ, বৃদ্ধ মহিলা, যুবক, যুবতী, শিশু ছেলে, শিশু মেয়ে) × তিনটি ভাষা (বাংলা · हिन्दी · English)। **সব ইঞ্জিন স্যান্ডবক্সে বাস্তব অডিও তৈরি করে যাচাই করা।**

## প্যাক তালিকা (সব যাচাইকৃত ✓)

| প্যাক | ইঞ্জিন | মডেল | সাইজ | স্যান্ডবক্স টেস্ট |
|---|---|---|---|---|
| বাংলা `bn` | **ONNX Runtime (direct)** | Meta MMS-TTS (transformers single-input export) | ~১০৯ MB | ✓ ৫.৬৬s বাংলা অডিও + child pitch ট্রান্সফর্ম |
| हिन्दी `hi` | **sherpa-onnx (VITS/Piper)** | pratham (পুরুষ) · rohan (পুরুষ) · priyamvada (নারী) | ~৬০ MB (৩×int8) | ✓ ৩.৭৬s হিন্দি অডিও @22.05kHz |
| English `en` | **sherpa-onnx (Kokoro-82M int8)** | ১১টি স্টুডিও স্পিকার | ~৯৮ MB | ✓ ২ স্পিকার টেস্ট @24kHz |

- মডেল সোর্স: `huggingface.co/sriram09764/itantra-tts-onnx` (bn ONNX), `github.com/k2-fsa/sherpa-onnx` release `tts-models` (hi piper int8, en kokoro int8), অফিসিয়াল `facebook/mms-tts-ben` vocab।
- প্যাক APK-তে বান্ডিল হয় না — **সেটিংস → 🎙️ AI Voice Packs** থেকে ডাউনলোড হয়, থাকে `filesDir/tts/<lang>/`-এ। বাংলার vocab (৯২৭ bytes) অ্যাপে বান্ডেল (`assets/tts/bn_vocab.json`)।

## চরিত্র ম্যাপিং

**English (Kokoro) — আসল আলাদা স্টুডিও স্পিকার:**

| চরিত্র | স্পিকার (sid) |
|---|---|
| বৃদ্ধ পুরুষ | bm_george (9) |
| বৃদ্ধ মহিলা | bf_emma (7) |
| যুবক | am_michael (6) |
| যুবতী | af_bella (1) |
| শিশু ছেলে | bm_lewis (10) + pitch ×1.30 |
| শিশু মেয়ে | af_sky (4) + pitch ×1.34 |

**हिन्दी (Piper) — আসল আলাদা স্পিকার:**

| চরিত্র | স্পিকার |
|---|---|
| বৃদ্ধ পুরুষ | pratham + pitch ×0.80 |
| যুবক | rohan |
| শিশু ছেলে | rohan + pitch ×1.30 |
| বৃদ্ধ মহিলা | priyamvada + pitch ×0.92 |
| যুবতী | priyamvada + pitch ×1.06 |
| শিশু মেয়ে | priyamvada + pitch ×1.34 |

**বাংলা (MMS এক-কণ্ঠ) — গতি+পিচ DSP দিয়ে চরিত্র:** বৃদ্ধ পুরুষ (×0.90 spd, ×0.80 pitch), বৃদ্ধ মহিলা (×0.90, ×0.92), যুবক (×0.98, ×0.96), যুবতী (×0.98, ×1.06), শিশু ছেলে (×1.05, ×1.30), শিশু মেয়ে (×1.05, ×1.34)

পিচ বদলানো হয় **PitchTools** দিয়ে: রিস্যাম্পল + WSOLA টাইম-স্ট্রেচ — গলার উচ্চতা বদলালেও দৈর্ঘ্য/টাইমিং ঠিক থাকে (ডাবিং সিঙ্ক নষ্ট হয় না)।

## ইন্টিগ্রেশন (কীভাবে কাজ করে)

1. অ্যাপের ভয়েস স্লট `cast:<role>:<lang>` — প্যাক ইনস্টল হলে **TextToSpeechManager** স্বয়ংক্রিয়ভাবে নিউরালে চলে যায় (UI বদল লাগে না)।
2. ডাবিং (`compileSubtitlesToWav`), Dubber V2 পার-ব্লক ভয়েস, ভয়েস পিকার প্রিভিউ, ব্লক প্রিভিউ — সব পথে নিউরাল।
3. প্যাক না থাকলে/মিশ্র হলে সিস্টেম TTS ফলব্যাক (আগের আচরণ)।
4. সব নিউরাল আউটপুট 16 kHz mono 16-bit — ভিন্ন ভাষার ব্লক এক মাস্টার WAV-এও নির্ভুল মেশে।
5. ইঞ্জিন লাইব্রেরি: `app/libs/sherpa-onnx-1.13.8.aar` (Apache-2.0; হারালে `bash scripts/fetch_sherpa_onnx_aar.sh`) + `com.microsoft.onnxruntime:onnxruntime-android:1.20.0` (Maven, বাংলা MMS-এর জন্য) + `commons-compress` (tar.bz2)।
6. Gradle `abiFilters`: arm64-v8a + x86_64।

## সীমাবদ্ধতা (সৎ প্রকাশ)

- বাংলার মুক্ত অন-ডিভাইস মডেলে MMS-ই সেরা: স্পষ্ট ও প্রাকৃতিক, তবে Kokoro-র স্টুডিও মানের সমান নয়; চরিত্রগুলো পিচ/গতিতে গড়া।
- MMS fp32 RAM বেশি নেয় (~৩০০-৫০০ MB) — ৩ GB+ RAM ফোনে সেরা। Piper/Kokoro int8 হালকা।
- শিশুকণ্ঠ = প্রাপ্তবয়স্ক স্পিকারের পিচ-লিফট (মুক্ত মডেলে শিশু ভয়েস ডেটাসেট নেই)।
- প্রথম ডাউনলোডে ভালো নেটওয়ার্ক লাগে; পরে ১০০% অফলাইন।

## ফাইল

| ফাইল | কাজ |
|---|---|
| `tts/neural/NeuralVoiceManager.kt` | ৩ ইঞ্জিন (sherpa kokoro/piper + direct ORT), প্যাক ডাউনলোড/এক্সট্র্যাক্ট, সিন্থেসিস, প্রিভিউ |
| `tts/neural/VoicePersona.kt` | ৬ চরিত্রের আইডি + ভাষাভেদে প্রিভিউ লেখা |
| `tts/neural/PitchTools.kt` | pitch-preserving WSOLA পিচ-শিফট, টাইম-স্ট্রেচ, রিস্যাম্পল |
| `ui/VoicePackDialog.kt` | প্যাক-ম্যানেজার ডায়ালগ (ডাউনলোড/ডিলিট/৬ কণ্ঠ প্রিভিউ) |
| `tts/TextToSpeechManager.kt` | নিউরাল ব্রাঞ্চ (compile + preview) |
| `assets/tts/bn_vocab.json` | অফিসিয়াল MMS বাংলা vocab (বান্ডেল) |
| `scripts/fetch_sherpa_onnx_aar.sh` | AAR ডাউনলোডার |
| `voice-samples/` (ওয়ার্কস্পেসে) | স্যান্ডবক্স ভ্যালিডেশনের আসল নমুনা WAV |

## ☁️ Google AI voices (Gemini TTS) — BalaSpeak 2.2

Google-এর বড় TTS মডেল (Gemini TTS / Chirp HD / Neural2) **ডাউনলোডযোগ্য নয়** — ওয়েটস কখনো মুক্ত করা হয়নি, ওগুলো শুধু Google-এর ক্লাউডে চলে। তাই সর্বোচ্চ মান পাওয়ার বৈধ পথ হলো **Gemini TTS API** — যা এখন অ্যাপে যুক্ত:

- **সুইচ:** সেটিংস → AI Voice Packs → "☁️ Google AI voices (সেরা মান)"
- **কী:** Settings-এ সেভ করা Gemini key (AIza…, ApiKeyStore-এর একই সোর্স)
- **ভাষা:** বাংলা bn-BD ✓ · हिन्दी hi-IN ✓ · English en-US ✓ (২৪ ভাষা)
- **চরিত্র ম্যাপ:** বৃদ্ধ পুরুষ=Charon, বৃদ্ধ মহিলা=Gacrux, যুবক=Puck, যুবতী=Leda, শিশু ছেলে=Puck+স্টাইল, শিশু মেয়ে=Leda+স্টাইল (প্রাকৃতিক-ভাষা স্টাইল-নির্দেশনা প্রম্পটের শুরুতে যায়)
- **ফলব্যাক চেইন:** Gemini (ক্লাউড) → অফলাইন নিউরাল প্যাক → সিস্টেম TTS
- **মডেল:** gemini-3.1-flash-tts-preview → gemini-2.5-flash-preview-tts (অটো)
- **অডিও:** 24 kHz PCM আসে → 16 kHz মোনো স্ট্যান্ডার্ড + WSOLA speed-fit
- **যাচাই:** এন্ডপয়েন্ট/হেডার/পে-লোড Google-এ রাউট হয় (স্যান্ডবক্সে ডামি-কী টেস্টে AUTH এরর শেপ নিশ্চিত); PCM→WAV পাইপলাইন টেস্টেড ✓
- **খরচ:** Google API-র কোটা/প্রাইসিং প্রযোজ্য (ফ্রি টিয়ার থাকলে সেটাও কাজ করবে)

ফাইল: `tts/neural/GeminiTtsEngine.kt`, `tts/neural/WavIo.kt` (শেয়ার্ড WAV রাইটার)
