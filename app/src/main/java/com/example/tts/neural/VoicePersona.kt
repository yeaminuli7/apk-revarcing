package com.example.tts.neural

/**
 * The six speaking roles (personas) BalaSpeak offers per language.
 * Engine mapping (Kokoro speaker ids, Piper voice dirs, speed/pitch shaping)
 * lives in [NeuralVoiceManager]; this type carries the shared ids and the
 * per-language preview sentences used by the voice-pack dialog.
 */
object PersonaSpec {
    const val ELDERLY_MALE = "elderly_male"
    const val ELDERLY_FEMALE = "elderly_female"
    const val YOUNG_MALE = "young_male"
    const val YOUNG_FEMALE = "young_female"
    const val CHILD_BOY = "child_boy"
    const val CHILD_GIRL = "child_girl"

    val allRoles = listOf(
        ELDERLY_MALE, ELDERLY_FEMALE, YOUNG_MALE, YOUNG_FEMALE, CHILD_BOY, CHILD_GIRL
    )

    fun sampleText(langCode: String, roleId: String): String {
        val isMale = roleId.endsWith("male")
        return when (langCode.lowercase()) {
            "bn" -> if (isMale) "আসসালামু আলাইকুম, আমি বালাস্পিকের কণ্ঠ। আজকের খবরটা শুনুন।"
                    else "আসসালামু আলাইকুম, আমার কথা শুনে দেখুন। আজকের গল্পটা সুন্দর।"
            "hi" -> if (isMale) "नमस्ते, मैं बालास्पीक की आवाज़ हूँ। आज की खबर सुनिए।"
                    else "नमस्ते, मेरी आवाज़ सुनकर देखिए। आज की कहानी बहुत अच्छी है।"
            else -> if (isMale) "Hello, this is BalaSpeak. Here is today's top story for you."
                    else "Hello, listen to my voice. Today's story is truly wonderful."
        }
    }
}
