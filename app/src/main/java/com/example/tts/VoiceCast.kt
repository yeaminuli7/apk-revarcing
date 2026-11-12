package com.example.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale

/**
 * Six speaking roles per language. Android's TTS engine does not ship six real
 * actors, so each role uses the best installed voice of that language and a
 * small age/gender shape. Pitch stays inside a human range on purpose.
 */
object VoiceCast {
    const val PREFIX = "cast:"

    data class Role(
        val id: String,
        val english: String,
        val bangla: String,
        val gender: String,
        val rate: Float,
        /** null = offered for every language; "en" = English-only character cast. */
        val onlyLang: String? = null,
        val pitch: Float = 1f
    )

    data class SpeakingProfile(
        val voice: Voice?,
        val locale: Locale,
        val pitch: Float,
        val rate: Float
    )

    val roles: List<Role> = listOf(
        // ---- core six, every language
        Role("elderly_male", "Elderly man", "বৃদ্ধ পুরুষ", "male", 0.88f, pitch = 0.88f),
        Role("elderly_female", "Elderly woman", "বৃদ্ধ মহিলা", "female", 0.88f, pitch = 0.88f),
        Role("young_male", "Young man", "যুবক", "male", 0.96f),
        Role("young_female", "Young woman", "যুবতী", "female", 0.96f),
        Role("child_boy", "Boy", "শিশু ছেলে", "male", 1.04f, pitch = 1.20f),
        Role("child_girl", "Girl", "শিশু মেয়ে", "female", 1.05f, pitch = 1.22f),

        // ---- English-only ages (Kokoro v1.0 cast)
        Role("baby_boy", "Baby boy (2-3)", "খুদে ছেলে", "male", 1.10f, "en", 1.45f),
        Role("baby_girl", "Baby girl (2-3)", "খুদে মেয়ে", "female", 1.12f, "en", 1.48f),
        Role("toddler_boy", "Toddler boy", "টগবগে ছেলে", "male", 1.08f, "en", 1.36f),
        Role("toddler_girl", "Toddler girl", "টগবগে মেয়ে", "female", 1.09f, "en", 1.38f),
        Role("teen_boy", "Teenage boy", "কিশোর", "male", 1.04f, "en", 1.12f),
        Role("teen_girl", "Teenage girl", "কিশোরী", "female", 1.04f, "en", 1.10f),
        Role("adult_male", "Adult man", "পরিণত পুরুষ", "male", 0.98f, "en", 0.98f),
        Role("adult_female", "Adult woman", "পরিণত নারী", "female", 1.00f, "en", 1.02f),
        Role("middle_male", "Middle-aged man", "মধ্যবয়সী পুরুষ", "male", 0.96f, "en", 0.93f),
        Role("middle_female", "Middle-aged woman", "মধ্যবয়সী নারী", "female", 0.97f, "en", 0.97f),
        Role("grandpa", "Grandfather", "দাদা", "male", 0.86f, "en", 0.90f),
        Role("grandma", "Grandmother", "দিদিমা", "female", 0.87f, "en", 0.92f),

        // ---- English-only characters
        Role("narrator", "Narrator (deep)", "বর্ণনাকারী", "male", 0.92f, "en", 0.90f),
        Role("hero", "Hero", "নায়ক", "male", 0.96f, "en", 0.94f),
        Role("villain", "Villain", "খলনায়ক", "male", 0.85f, "en", 0.80f),
        Role("monster", "Monster", "দানব", "male", 0.88f, "en", 0.62f),
        Role("demon", "Demon", "শয়তান", "male", 0.84f, "en", 0.60f),
        Role("giant", "Giant", "দৈত্য", "male", 0.80f, "en", 0.58f),
        Role("vampire", "Vampire", "ভ্যাম্পায়ার", "male", 0.84f, "en", 0.72f),
        Role("robot", "Robot", "রোবট", "male", 0.95f, "en", 0.95f),
        Role("alien", "Alien", "ভিনগ্রহী", "male", 1.00f, "en", 1.24f),
        Role("ghost", "Ghost", "ভূত", "female", 0.85f, "en", 0.95f),
        Role("whisper", "Whisper", "ফিসফিস", "female", 0.92f, "en", 0.99f),
        Role("witch", "Witch", "ডাইনি", "female", 0.90f, "en", 0.86f),
        Role("chipmunk", "Chipmunk", "কাঠবিড়ালি", "female", 1.25f, "en", 1.55f)
    )

    fun speakerId(roleId: String, langCode: String): String {
        return "$PREFIX$roleId:${langCode.lowercase(Locale.ROOT)}"
    }

    fun defaultSpeakerId(langCode: String): String = speakerId("young_male", langCode)

    fun labelOf(voiceId: String?): String? {
        val parsed = parse(voiceId) ?: return null
        val role = roles.firstOrNull { it.id == parsed.first } ?: return null
        return "${role.english} · ${role.bangla}"
    }

    fun voicesFor(neuralLangs: Set<String> = emptySet()): List<FriendlyVoice> {
        val languages = neuralLangs
            .filter { it.isNotBlank() }
            .sortedWith(compareBy<String> { languageRank(it) }.thenBy { nativeName(it) })
        val out = ArrayList<FriendlyVoice>(languages.size * roles.size)
        for (lang in languages) {
            val engine = engineForLang(lang)
            for (role in roles) {
                if (role.onlyLang != null && role.onlyLang != lang) continue
                out.add(
                    FriendlyVoice(
                        id = speakerId(role.id, lang),
                        displayName = "${role.english} · ${role.bangla} (from ${engineName(engine)})",
                        languageGroup = nativeName(lang),
                        locale = localeFor(lang),
                        sampleText = sample(lang, role.id),
                        isSystemVoice = false,
                        systemVoice = null,
                        rolePitch = pitchFor(role, role.gender),
                        roleRate = role.rate
                    )
                )
            }
        }
        return out
    }

    private fun engineForLang(lang: String): String = when (lang) {
        "en" -> "kokoro"
        "hi" -> "piper"
        "bn" -> "mms"
        else -> "neural"
    }

    private fun engineName(engine: String): String = when (engine) {
        "kokoro" -> "Kokoro"
        "piper" -> "Piper"
        "mms" -> "MMS-TTS"
        else -> "Neural"
    }

    fun resolve(voiceId: String?, fallbackLang: String?): SpeakingProfile {
        val parsed = parse(voiceId)
        if (parsed != null) {
            val role = roles.firstOrNull { it.id == parsed.first } ?: roles[2]
            val lang = parsed.second
            return SpeakingProfile(
                voice = null,
                locale = localeFor(lang),
                pitch = pitchFor(role, role.gender),
                rate = role.rate
            )
        }
        val lang = fallbackLang?.lowercase(Locale.ROOT)?.substringBefore("-").orEmpty().ifBlank { "en" }
        val role = roles.first { it.id == "young_male" }
        return SpeakingProfile(
            voice = null,
            locale = localeFor(lang),
            pitch = pitchFor(role, role.gender),
            rate = role.rate
        )
    }

    fun pickBase(raw: Collection<Voice>?, gender: String, langCode: String): Voice? = null

    fun openVoiceSettings(context: Context) {
        val settings = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val install = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(settings)
        } catch (e: Exception) {
            try {
                context.startActivity(install)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun parse(voiceId: String?): Pair<String, String>? {
        if (voiceId.isNullOrBlank() || !voiceId.startsWith(PREFIX)) return null
        val body = voiceId.removePrefix(PREFIX)
        val split = body.split(":", limit = 2)
        if (split.size != 2 || split[0].isBlank() || split[1].isBlank()) return null
        return split[0] to split[1].lowercase(Locale.ROOT)
    }

    private fun pitchFor(role: Role, baseGender: String?): Float = role.pitch

    private fun genderOf(voice: Voice?): String? {
        if (voice == null) return null
        val features = voice.features.orEmpty().joinToString(" ").lowercase(Locale.ROOT)
        val name = voice.name.lowercase(Locale.ROOT)
        val blob = "$features $name"
        return when {
            blob.contains("female") || blob.contains("woman") -> "female"
            blob.contains("male") || blob.contains("man") -> "male"
            else -> null
        }
    }

    private fun isNotInstalled(voice: Voice): Boolean {
        return voice.features?.any { it.equals("notInstalled", ignoreCase = true) } == true
    }

    private fun score(voice: Voice, lang: String): Int {
        var value = voice.quality * 10
        if (!voice.isNetworkConnectionRequired) value += 25
        val country = voice.locale.country.uppercase(Locale.ROOT)
        val preferred = preferredCountry(lang)
        if (preferred != null && country == preferred) value += 20
        if (lang == "en" && country == "GB") value += 8
        if (voice.latency <= Voice.LATENCY_NORMAL) value += 4
        return value
    }

    private fun qualityLabel(voice: Voice?): String {
        if (voice == null) return "install voice pack"
        return when {
            voice.quality >= Voice.QUALITY_HIGH -> "high quality"
            voice.quality >= Voice.QUALITY_NORMAL -> "standard"
            else -> "basic"
        }
    }

    private fun preferredCountry(lang: String): String? {
        return when (lang) {
            "bn" -> "BD"
            "en" -> "US"
            "hi" -> "IN"
            "ur" -> "PK"
            "ar" -> "SA"
            "es" -> "ES"
            "pt" -> "BR"
            "zh" -> "CN"
            else -> null
        }
    }

    private fun languageRank(lang: String): Int {
        return when (lang) {
            "bn" -> 0
            "en" -> 1
            "hi" -> 2
            "ur" -> 3
            "ar" -> 4
            else -> 5
        }
    }

    fun localeFor(langCode: String): Locale {
        return when (langCode.lowercase(Locale.ROOT).substringBefore("-")) {
            "bn" -> Locale("bn", "BD")
            "hi" -> Locale("hi", "IN")
            "ur" -> Locale("ur", "PK")
            "ar" -> Locale("ar", "SA")
            "ja" -> Locale.JAPAN
            "ko" -> Locale.KOREA
            "zh" -> Locale.SIMPLIFIED_CHINESE
            "es" -> Locale("es", "ES")
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "it" -> Locale.ITALY
            "pt" -> Locale("pt", "BR")
            "ru" -> Locale("ru", "RU")
            "id" -> Locale("id", "ID")
            "tr" -> Locale("tr", "TR")
            "th" -> Locale("th", "TH")
            "vi" -> Locale("vi", "VN")
            "en" -> Locale.US
            else -> Locale(langCode.substringBefore("-"))
        }
    }

    private fun nativeName(lang: String): String {
        return when (lang) {
            "bn" -> "বাংলা"
            "en" -> "English"
            "hi" -> "हिन्दी"
            "ur" -> "اردو"
            "ar" -> "العربية"
            "es" -> "Español"
            "fr" -> "Français"
            "de" -> "Deutsch"
            "it" -> "Italiano"
            "pt" -> "Português"
            "ru" -> "Русский"
            "ja" -> "日本語"
            "ko" -> "한국어"
            "zh" -> "中文"
            "id" -> "Indonesia"
            "tr" -> "Türkçe"
            "th" -> "ไทย"
            "vi" -> "Tiếng Việt"
            else -> localeFor(lang).displayLanguage.ifBlank { lang }
        }
    }

    private fun sample(lang: String, roleId: String): String {
        val table = samples[lang] ?: samples["en"]
        return table?.get(roleId) ?: "Hello. This is how I speak."
    }

    private val samples: Map<String, Map<String, String>> = mapOf(
        "bn" to mapOf(
            "elderly_male" to "আচ্ছা, আস্তে বলো। আমি শুনছি।",
            "elderly_female" to "বাছা, আগে খেয়ে নাও। তারপর যাবে।",
            "young_male" to "চলো, কাজটা আজকেই শেষ করে ফেলি।",
            "young_female" to "তুমি কি এখনই আসবে? আমি অপেক্ষা করছি।",
            "child_boy" to "আমিও যাব! একটু দাঁড়াও।",
            "child_girl" to "দেখো দেখো, ওই যে পাখিটা!"
        ),
        "en" to mapOf(
            "elderly_male" to "Alright, say that again, a little slower.",
            "elderly_female" to "Come eat first, dear. Then you can go.",
            "young_male" to "Let's finish this today and be done with it.",
            "young_female" to "Are you coming now? I'm waiting.",
            "child_boy" to "I want to come too. Wait for me!",
            "child_girl" to "Look, look, that bird over there!"
        ),
        "hi" to mapOf(
            "elderly_male" to "अच्छा, जरा धीरे बोलो। मैं सुन रहा हूँ।",
            "elderly_female" to "बेटा, पहले खा लो। फिर जाना।",
            "young_male" to "चलो, आज ही काम खत्म कर लेते हैं।",
            "young_female" to "क्या तुम अभी आ रहे हो? मैं इंतज़ार कर रही हूँ।",
            "child_boy" to "मैं भी चलूँगा! ज़रा रुको।",
            "child_girl" to "देखो देखो, वो चिड़िया!"
        ),
        "ur" to mapOf(
            "elderly_male" to "اچھا، ذرا آہستہ بولو۔ میں سن رہا ہوں۔",
            "elderly_female" to "بیٹا، پہلے کھا لو۔ پھر جانا۔",
            "young_male" to "چلو، آج ہی کام ختم کر لیتے ہیں۔",
            "young_female" to "کیا تم ابھی آ رہے ہو؟ میں انتظار کر رہی ہوں۔",
            "child_boy" to "میں بھی چلوں گا! ذرا رکو۔",
            "child_girl" to "دیکھو دیکھو، وہ چڑیا!"
        ),
        "ar" to mapOf(
            "elderly_male" to "حسناً، تكلم ببطء. أنا أسمعك.",
            "elderly_female" to "يا بني، كل أولاً، ثم اذهب.",
            "young_male" to "هيا ننهي هذا العمل اليوم.",
            "young_female" to "هل ستأتي الآن؟ أنا أنتظرك.",
            "child_boy" to "أنا أيضاً أريد أن أذهب. انتظرني.",
            "child_girl" to "انظري، ذلك العصفور!"
        )
    )
}
