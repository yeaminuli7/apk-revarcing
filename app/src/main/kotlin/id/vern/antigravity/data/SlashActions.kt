package id.vern.antigravity.data

data class SlashAction(
    val cmd: String,
    val descText: String? = null,
    val category: String = "",
)

data class CliSlashCommand(
    val cmd: String,
    val description: String,
)

val SYSTEM_SLASH_COMMANDS: List<SlashAction> = listOf(
    SlashAction("goal", "Run long-running task until completion", "System"),
    SlashAction("schedule", "Schedule a one-shot timer or recurring background task", "System"),
    SlashAction("browser", "Web browsing, searching, or interacting with web applications", "System"),
    SlashAction("grill-me", "Interactive interview to align on plan and design decisions", "System"),
    SlashAction("teamwork-preview", "Coordinate autonomous subagents working together in parallel", "System"),
    SlashAction("learn", "Persist current guidelines, corrections, or setup for future tasks", "System"),
    SlashAction("deep-plan", "Deep thinking, strategic planning, and rigorous verification", "System"),
    SlashAction("code-review", "Deep review of local changes, branch, or specific files", "System"),
    SlashAction("clear", "Clear chat conversation history", "System"),
    SlashAction("help", "Show help and guidance", "System"),
)

object SlashCommandParser {
    private val ansiRegex = Regex("\\u001B\\[[0-9;]*[A-Za-z]")
    private val slashLineRegex = Regex("""^/([A-Za-z][A-Za-z0-9_-]*)\b\s*(.*)$""")
    private val subcommandRegex = Regex("""^\s{2,}([a-z][a-z0-9_-]*)\s{2,}(.*)$""")
    private val leadingSepRegex = Regex("""^[,;:–—\-]+\s*""")

    fun parse(text: String): List<CliSlashCommand> {
        val clean = ansiRegex.replace(text, "")
        val seen = LinkedHashMap<String, String>()
        var inSubcommands = false

        for (rawLine in clean.lines()) {
            val trimmed = rawLine.trim()
            if (trimmed.equals("Available subcommands:", ignoreCase = true) || trimmed.equals("Subcommands:", ignoreCase = true)) {
                inSubcommands = true
                continue
            }
            if (inSubcommands) {
                if (rawLine.isNotBlank() && !rawLine.startsWith(" ")) {
                    inSubcommands = false
                } else {
                    val subMatch = subcommandRegex.matchEntire(rawLine)
                    if (subMatch != null) {
                        val subCmd = subMatch.groupValues[1].lowercase()
                        val desc = subMatch.groupValues[2].trim()
                        if (subCmd.isNotEmpty() && !seen.containsKey(subCmd)) {
                            seen[subCmd] = desc
                        }
                        continue
                    }
                }
            }

            if (!trimmed.startsWith("/")) continue
            val match = slashLineRegex.matchEntire(trimmed) ?: continue
            val cmd = match.groupValues[1].lowercase()
            var rest = match.groupValues[2].trim()
            rest = leadingSepRegex.replace(rest, "").trim()
            if (cmd.isNotEmpty() && !seen.containsKey(cmd)) {
                seen[cmd] = rest
            }
        }
        return seen.map { (cmd, desc) -> CliSlashCommand(cmd, desc) }
    }
}

fun mergeDynamicSlashActions(
    systemCommands: List<SlashAction>,
    cliCommands: List<CliSlashCommand>,
    skills: List<SlashAction>,
    workflows: List<SlashAction>
): List<SlashAction> {
    val seen = LinkedHashMap<String, SlashAction>()

    for (cli in cliCommands) {
        val key = cli.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty()) {
            seen[key] = SlashAction(key, cli.description.ifBlank { null }, "CLI")
        }
    }

    for (sys in systemCommands) {
        val key = sys.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = sys.copy(cmd = key)
        }
    }

    for (wf in workflows) {
        val key = wf.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = wf.copy(cmd = key)
        }
    }

    for (skill in skills) {
        val key = skill.cmd.lowercase().trimStart('/')
        if (key.isNotEmpty() && !seen.containsKey(key)) {
            seen[key] = skill.copy(cmd = key)
        }
    }

    return seen.values.toList()
}

fun cliSlashActions(cli: List<CliSlashCommand>): List<SlashAction> {
    return cli.distinctBy { it.cmd }.map { SlashAction(it.cmd, it.description.ifBlank { null }, "CLI") }
}

