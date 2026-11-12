package com.example.tts.neural

/**
 * Speaking roles (personas) BalaSpeak offers.
 *
 * Six "core" roles exist for every language (bn / hi / en). English additionally
 * gets a full character cast — ages from toddler to grandparent plus stylised
 * characters (monster, robot, ghost, ...) — because Kokoro v1.0 ships 28 real
 * English speakers to build them from.
 *
 * Engine mapping (Kokoro speaker ids, Piper voice dirs, speed/pitch/FX shaping)
 * lives in [NeuralVoiceManager]; this type carries the shared ids and the
 * per-language preview sentences used by the voice-pack dialog.
 */
object PersonaSpec {
    // ---------------------------------------------------------- core roles
    const val ELDERLY_MALE = "elderly_male"
    const val ELDERLY_FEMALE = "elderly_female"
    const val YOUNG_MALE = "young_male"
    const val YOUNG_FEMALE = "young_female"
    const val CHILD_BOY = "child_boy"
    const val CHILD_GIRL = "child_girl"

    val allRoles = listOf(
        ELDERLY_MALE, ELDERLY_FEMALE, YOUNG_MALE, YOUNG_FEMALE, CHILD_BOY, CHILD_GIRL
    )

    // ------------------------------------------------- English-only cast
    // Ages
    const val BABY_BOY = "baby_boy"
    const val BABY_GIRL = "baby_girl"
    const val TODDLER_BOY = "toddler_boy"
    const val TODDLER_GIRL = "toddler_girl"
    const val TEEN_BOY = "teen_boy"
    const val TEEN_GIRL = "teen_girl"
    const val ADULT_MALE = "adult_male"
    const val ADULT_FEMALE = "adult_female"
    const val MIDDLE_MALE = "middle_male"
    const val MIDDLE_FEMALE = "middle_female"
    const val GRANDPA = "grandpa"
    const val GRANDMA = "grandma"
    // Characters
    const val NARRATOR = "narrator"
    const val HERO = "hero"
    const val VILLAIN = "villain"
    const val MONSTER = "monster"
    const val DEMON = "demon"
    const val GIANT = "giant"
    const val VAMPIRE = "vampire"
    const val ROBOT = "robot"
    const val ALIEN = "alien"
    const val GHOST = "ghost"
    const val WHISPER = "whisper"
    const val WITCH = "witch"
    const val CHIPMUNK = "chipmunk"

    val characterRoles = listOf(
        BABY_BOY, BABY_GIRL, TODDLER_BOY, TODDLER_GIRL, TEEN_BOY, TEEN_GIRL,
        ADULT_MALE, ADULT_FEMALE, MIDDLE_MALE, MIDDLE_FEMALE, GRANDPA, GRANDMA,
        NARRATOR, HERO, VILLAIN, MONSTER, DEMON, GIANT, VAMPIRE,
        ROBOT, ALIEN, GHOST, WHISPER, WITCH, CHIPMUNK
    )

    /** Roles offered for a language, in display order. */
    fun rolesFor(langCode: String): List<String> =
        if (langCode.lowercase().startsWith("en")) allRoles + characterRoles else allRoles

    fun isCharacterRole(roleId: String): Boolean = characterRoles.contains(roleId)

    /** True when the role only makes sense for English (needs the Kokoro cast). */
    fun isEnglishOnly(roleId: String): Boolean =
        isCharacterRole(roleId) && !allRoles.contains(roleId)

    fun sampleText(langCode: String, roleId: String): String {
        if (isEnglishOnly(roleId)) return characterSample(roleId)
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

    private fun characterSample(roleId: String): String = when (roleId) {
        BABY_BOY, BABY_GIRL, TODDLER_BOY, TODDLER_GIRL ->
            "Hello! I am very small, but I can talk too. Do you like my voice?"
        TEEN_BOY -> "Hey, what's up? This is my voice. Pretty cool, right?"
        TEEN_GIRL -> "Hi there! This is how I sound. What do you think?"
        ADULT_MALE -> "Good evening. This is the voice I will use for your video."
        ADULT_FEMALE -> "Good evening. I hope you enjoy the story I am about to tell."
        MIDDLE_MALE -> "Let me tell you something worth listening to, my friend."
        MIDDLE_FEMALE -> "Take your time, and listen carefully to what I have to say."
        GRANDPA -> "When I was your age, the world was a very different place, child."
        GRANDMA -> "Come closer, dear. Let me tell you how it all happened."
        NARRATOR -> "In a world long forgotten, a single voice began to tell the story."
        HERO -> "Do not be afraid. Stand behind me, and we will face this together."
        VILLAIN -> "You have walked into my trap, and there is no way out for you now."
        MONSTER -> "Run, little one. The night belongs to me, and I am hungry."
        DEMON -> "Your fear has a name, and that name is mine. Bow before me."
        GIANT -> "Who dares to enter my mountain? Speak, before I crush you."
        VAMPIRE -> "Come closer. I promise it will only hurt for a moment, my dear."
        ROBOT -> "System online. Voice module engaged. Please state your command."
        ALIEN -> "Greetings, human. We have travelled far to hear your language."
        GHOST -> "Do not be afraid. I was once like you, not so long ago."
        WHISPER -> "Listen closely. I have a secret that no one else can hear."
        WITCH -> "Come into my little house, dearie, and taste what I have cooked."
        CHIPMUNK -> "That is hilarious! I cannot stop laughing at what you just said!"
        else -> "Hello, this is BalaSpeak. Here is today's top story for you."
    }
}
