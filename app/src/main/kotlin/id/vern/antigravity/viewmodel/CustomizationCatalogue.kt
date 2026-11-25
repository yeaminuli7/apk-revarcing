package id.vern.antigravity.viewmodel

import android.content.Context
import android.util.Log
import org.json.JSONArray
import java.io.File
import id.vern.antigravity.util.NetworkDownloader

object CustomizationCatalogue {

    fun getDefaultSkillsCatalogue(): List<SkillCatalogueItem> {
        return listOf(
            SkillCatalogueItem(
                id = "antislop",
                name = "antislop",
                description = "Anti Slop: Rules for AI Coding Agents. The core filter. Load always to stop generic AI slop.",
                category = "Quality & Filter",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop"
            ),
            SkillCatalogueItem(
                id = "antislop-ui",
                name = "antislop-ui",
                description = "UI and visual skill for antislop. Use when building or editing any interface: color, layout, components, motion, and page performance.",
                category = "UI & Visual",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop-ui"
            ),
            SkillCatalogueItem(
                id = "antislop-copywriting",
                name = "antislop-copywriting",
                description = "Copy and text skill for antislop. Use when writing or editing prose: headlines, tone, CTAs, and anti-AI-writing patterns.",
                category = "Copywriting",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop-copywriting"
            ),
            SkillCatalogueItem(
                id = "antislop-human",
                name = "antislop-human",
                description = "Human and accessibility skill for antislop. Contrast, keyboard, focus, states, and interaction affordances for real people.",
                category = "Accessibility",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop-human"
            ),
            SkillCatalogueItem(
                id = "antislop-layoutmobile",
                name = "antislop-layoutmobile",
                description = "Mobile layout skill for antislop. Use for layouts that reflow across screen sizes, phone to desktop: grids, overflow, tap targets.",
                category = "UI & Visual",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop-layoutmobile"
            ),
            SkillCatalogueItem(
                id = "antislop-code",
                name = "antislop-code",
                description = "Code comment hygiene for AI coding agents: remove generic AI-slop comments, keep the valuable ones, never touch the code.",
                category = "Quality & Filter",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                subPath = "skills/antislop-code"
            ),
            SkillCatalogueItem(
                id = "rea-argonaut",
                name = "rea-argonaut",
                description = "Zero-G Reverse Engineering Anything & Verified Rust Reconstruction for Gemini 4 Argon and Google Antigravity 2.0.",
                category = "Reverse Engineering",
                repoUrl = "https://github.com/Vern-01/argonaut.git",
                subPath = "skills/rea-argonaut"
            ),
            SkillCatalogueItem(
                id = "android-cli",
                name = "android-cli",
                description = "Official Android CLI skill for project scaffolding, APK build, emulator orchestration, logcat, and layout inspection.",
                category = "Mobile Dev",
                repoUrl = "https://github.com/Vern-01/antigravity-community-skills.git",
                subPath = "skills/android-cli"
            ),
            SkillCatalogueItem(
                id = "android-device-hub",
                name = "android-device-hub",
                description = "Android Device Hub sidecar skill to mirror, inspect, and control connected Android physical and virtual devices.",
                category = "Mobile Dev",
                repoUrl = "https://github.com/Vern-01/antigravity-community-skills.git",
                subPath = "skills/device-hub"
            ),
            SkillCatalogueItem(
                id = "compose-preview",
                name = "compose-preview",
                description = "Compose Preview sidecar skill to render Jetpack Compose @Preview composables inside Antigravity.",
                category = "UI & Visual",
                repoUrl = "https://github.com/Vern-01/antigravity-community-skills.git",
                subPath = "skills/compose-preview"
            ),
            SkillCatalogueItem(
                id = "flutter-best-practices",
                name = "flutter-best-practices",
                description = "Official Flutter & Dart architectural guidelines, testing patterns, and state management conventions.",
                category = "Mobile Dev",
                repoUrl = "https://github.com/flutter/ai.git",
                subPath = "skills/flutter-apply-architecture-best-practices"
            ),
            SkillCatalogueItem(
                id = "firebase-basics",
                name = "firebase-basics",
                description = "Foundational Firebase CLI setup, project initialization, Firebase Auth, and configuration downloads.",
                category = "Cloud & Backend",
                repoUrl = "https://github.com/firebase/firebase-tools.git",
                subPath = "skills/firebase_basics"
            ),
            SkillCatalogueItem(
                id = "firebase-firestore",
                name = "firebase-firestore",
                description = "Cloud Firestore schema modeling, index configuration, query optimization, and client SDK integration.",
                category = "Cloud & Backend",
                repoUrl = "https://github.com/firebase/firebase-tools.git",
                subPath = "skills/firebase_firestore"
            ),
            SkillCatalogueItem(
                id = "firestore-rules-author",
                name = "firestore-rules-author",
                description = "Specialist skill for authoring, testing, and hardening production-grade Cloud Firestore Security Rules.",
                category = "Security",
                repoUrl = "https://github.com/firebase/firebase-tools.git",
                subPath = "skills/firestore_rules_creation"
            ),
            SkillCatalogueItem(
                id = "gemini-api-dev",
                name = "gemini-api-dev",
                description = "Comprehensive guide for calling Gemini models: multimodal inference, structured outputs, function calling, and streaming.",
                category = "AI & LLM",
                repoUrl = "https://github.com/google-gemini/gemini-api-cookbook.git",
                subPath = "skills/gemini-api-dev"
            ),
            SkillCatalogueItem(
                id = "gemini-live-api-dev",
                name = "gemini-live-api-dev",
                description = "Real-time bidirectional streaming with Gemini Live API: voice activity detection, low-latency audio, and video frames.",
                category = "AI & LLM",
                repoUrl = "https://github.com/google-gemini/gemini-api-cookbook.git",
                subPath = "skills/gemini-live-api-dev"
            ),
            SkillCatalogueItem(
                id = "retrieving-developer-knowledge",
                name = "retrieving-developer-knowledge",
                description = "Searches and synthesizes official Google Cloud, AI/Gemini, Android, Chrome, Flutter, Go, and Firebase documentation.",
                category = "Developer Tools",
                repoUrl = "https://github.com/google-gemini/antigravity-skills.git",
                subPath = "skills/retrieving-developer-knowledge"
            ),
            SkillCatalogueItem(
                id = "reverse-engineer-anything",
                name = "reverse-engineer-anything",
                description = "Reverse engineer native, managed, Electron/JS, APKs, firmware, and browser targets with Zero-G inspection.",
                category = "Reverse Engineering",
                repoUrl = "https://github.com/Vern-01/argonaut.git",
                subPath = "skills/reverse-engineer-anything"
            ),
            SkillCatalogueItem(
                id = "system-architect",
                name = "system-architect",
                description = "High-level system design, distributed systems architecture, microservices decomposition, and cloud infrastructure planning.",
                category = "Architecture",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/system-architect"
            ),
            SkillCatalogueItem(
                id = "database-optimizer",
                name = "database-optimizer",
                description = "SQL and NoSQL database query optimization, indexing strategies, schema normalization, and connection pool tuning.",
                category = "Optimization",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/database-optimizer"
            ),
            SkillCatalogueItem(
                id = "security-auditor",
                name = "security-auditor",
                description = "Automated security code review, OWASP Top 10 vulnerability scanning, dependency auditing, and secrets detection.",
                category = "Security",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/security-auditor"
            ),
            SkillCatalogueItem(
                id = "docker-expert",
                name = "docker-expert",
                description = "Containerization best practices, multi-stage Dockerfiles, Docker Compose setups, and image size minimization.",
                category = "DevOps",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/docker-expert"
            ),
            SkillCatalogueItem(
                id = "api-designer",
                name = "api-designer",
                description = "RESTful and GraphQL API design, OpenAPI 3.0 specification generation, error modeling, and versioning patterns.",
                category = "Architecture",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/api-designer"
            ),
            SkillCatalogueItem(
                id = "git-workflow",
                name = "git-workflow",
                description = "Git branching strategies, conventional commits, rebase workflows, conflict resolution, and release tagging.",
                category = "Developer Tools",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/git-workflow"
            ),
            SkillCatalogueItem(
                id = "ui-ux-designer",
                name = "ui-ux-designer",
                description = "Design system implementation, accessibility auditing (WCAG 2.1), responsive layout patterns, and design tokens.",
                category = "UI & Visual",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/ui-ux-designer"
            ),
            SkillCatalogueItem(
                id = "performance-profiler",
                name = "performance-profiler",
                description = "Application performance profiling, memory leak detection, CPU bottleneck analysis, and startup time optimization.",
                category = "Optimization",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/performance-profiler"
            ),
            SkillCatalogueItem(
                id = "testing-expert",
                name = "testing-expert",
                description = "Test-driven development, unit and integration test design, mock strategies, coverage reporting, and flaky test fixes.",
                category = "Testing",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/testing-expert"
            ),
            SkillCatalogueItem(
                id = "code-reviewer",
                name = "code-reviewer",
                description = "Thorough peer code reviews checking style consistency, potential edge-case bugs, performance traps, and maintainability.",
                category = "Quality & Filter",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/code-reviewer"
            ),
            SkillCatalogueItem(
                id = "documentation-writer",
                name = "documentation-writer",
                description = "Developer documentation, README authoring, API reference docs, migration guides, and architecture diagrams.",
                category = "Developer Tools",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/documentation-writer"
            ),
            SkillCatalogueItem(
                id = "refactoring-specialist",
                name = "refactoring-specialist",
                description = "Legacy code modernization, dead code removal, design pattern application, and code smell elimination without regression.",
                category = "Optimization",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/refactoring-specialist"
            ),
            SkillCatalogueItem(
                id = "linux-admin",
                name = "linux-admin",
                description = "Linux system administration, shell scripting, package management, service daemons, and storage configuration on Android/Termux.",
                category = "DevOps",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = "skills/linux-admin"
            ),
            SkillCatalogueItem(
                id = "antigravity-skills",
                name = "antigravity-skills",
                description = "A curated collection of Agent Skills for Google Antigravity including system architect, db optimizer, and security auditor.",
                category = "Developer Tools",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = null
            )
        )
    }

    fun getDefaultPluginsCatalogue(): List<SkillCatalogueItem> {
        return listOf(
            SkillCatalogueItem(
                id = "rtk",
                name = "rtk",
                description = "Rust Token Killer: CLI proxy that reduces LLM token consumption by 60-90% on common dev commands.",
                category = "Optimization",
                repoUrl = "https://github.com/rtk-ai/rtk.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "antislop",
                name = "antislop",
                description = "Rules for an AI coding agent to filter out generic AI-generated UI designs, text, and code.",
                category = "Quality & Filter",
                repoUrl = "https://github.com/miqdadbadjuber/anti-slop.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "superpowers",
                name = "superpowers",
                description = "An agentic skills framework & software development methodology that works.",
                category = "Developer Tools",
                repoUrl = "https://github.com/obra/superpowers.git",
                subPath = null,
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "impeccable",
                name = "impeccable",
                description = "The design vocabulary for your coding agent. Steer with polish and bolder, review with critique and audit, catch AI slop before it ships.",
                category = "UI & Visual",
                repoUrl = "https://github.com/pbakaus/impeccable.git",
                subPath = null,
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "context-mode",
                name = "context-mode",
                description = "Free, local plugin that keeps tool output out of your coding agent's context window. Sandbox and SQLite FTS5 search, 99% fewer bytes in context.",
                category = "Optimization",
                repoUrl = "https://github.com/mksglu/context-mode.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "agents-suite",
                name = "agents-suite",
                description = "Plugins, agents, skills, and commands for AI coding agents written once in Markdown and adapted to each tool by generators with validation.",
                category = "Developer Tools",
                repoUrl = "https://github.com/wshobson/agents.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "planning-with-files",
                name = "planning-with-files",
                description = "Persistent file-based planning for AI coding agents and long-running tasks. Crash-proof markdown plans and session recovery.",
                category = "Productivity",
                repoUrl = "https://github.com/OthmanAdi/planning-with-files.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "sepia",
                name = "sepia",
                description = "De-AI writing skill for AI agents with narrative-architecture repair for fiction and venue-matched rules for prose.",
                category = "Quality & Filter",
                repoUrl = "https://github.com/Nanako0129/sepia.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "gemini-agent-skills",
                name = "gemini-agent-skills",
                description = "A comprehensive collection of expert AI Agent Skills for Google Gemini, featuring tools to convert and optimize Claude Code agents.",
                category = "Developer Tools",
                repoUrl = "https://github.com/saeed-vayghan/gemini-agent-skills.git",
                isPlugin = true
            ),
            SkillCatalogueItem(
                id = "antigravity-skills-manager",
                name = "antigravity-skills-manager",
                description = "A curated collection of Agent Skills for Google Antigravity.",
                category = "Developer Tools",
                repoUrl = "https://github.com/rmyndharis/antigravity-skills.git",
                subPath = null,
                isPlugin = true
            )
        )
    }

    fun parseCatalogueJson(jsonStr: String, isPluginDefault: Boolean): List<SkillCatalogueItem> {
        val list = mutableListOf<SkillCatalogueItem>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val sub = if (obj.has("subPath") && !obj.isNull("subPath")) obj.getString("subPath").ifBlank { null } else null
                list.add(
                    SkillCatalogueItem(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        description = obj.optString("description", ""),
                        category = obj.optString("category", if (isPluginDefault) "Optimization" else "Development"),
                        isPlugin = obj.optBoolean("isPlugin", isPluginDefault),
                        defaultPrompt = obj.optString("defaultPrompt", ""),
                        repoUrl = obj.optString("repoUrl", ""),
                        subPath = sub
                    )
                )
            }
        } catch (e: Exception) {
            Log.w("CustomizationCatalogue", "Failed parsing catalogue JSON: ${e.message}")
        }
        return list
    }

    fun loadCachedCatalogue(context: Context): Pair<List<SkillCatalogueItem>, List<SkillCatalogueItem>> {
        var skills = emptyList<SkillCatalogueItem>()
        var plugins = emptyList<SkillCatalogueItem>()
        try {
            val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }
            val skillsFile = File(cacheDir, "skills_catalogue.json")
            if (skillsFile.exists() && skillsFile.length() > 0) {
                val parsed = parseCatalogueJson(skillsFile.readText(), false)
                if (parsed.isNotEmpty()) {
                    skills = parsed
                }
            }
            val pluginsFile = File(cacheDir, "plugins_catalogue.json")
            if (pluginsFile.exists() && pluginsFile.length() > 0) {
                val parsed = parseCatalogueJson(pluginsFile.readText(), true)
                if (parsed.isNotEmpty()) {
                    plugins = parsed
                }
            }
        } catch (e: Exception) {
            Log.w("CustomizationCatalogue", "Failed loading cached catalogue: ${e.message}")
        }
        return Pair(skills, plugins)
    }

    fun fetchRemoteCatalogue(context: Context): Pair<List<SkillCatalogueItem>, List<SkillCatalogueItem>>? {
        return try {
            val cacheDir = File(context.filesDir, "cache").apply { mkdirs() }

            val (_, skillsJson) = NetworkDownloader.httpGetString("https://raw.githubusercontent.com/KuatoDev/antigravity-catalogue/main/skills.json", connectTimeout = 10000, readTimeout = 10000)
            val parsedSkills = if (!skillsJson.isNullOrBlank()) parseCatalogueJson(skillsJson, false) else emptyList()
            if (parsedSkills.isNotEmpty() && skillsJson != null) {
                try { File(cacheDir, "skills_catalogue.json").writeText(skillsJson) } catch (e: Exception) {}
            }

            val (_, pluginsJson) = NetworkDownloader.httpGetString("https://raw.githubusercontent.com/KuatoDev/antigravity-catalogue/main/plugins.json", connectTimeout = 10000, readTimeout = 10000)
            val parsedPlugins = if (!pluginsJson.isNullOrBlank()) parseCatalogueJson(pluginsJson, true) else emptyList()
            if (parsedPlugins.isNotEmpty() && pluginsJson != null) {
                try { File(cacheDir, "plugins_catalogue.json").writeText(pluginsJson) } catch (e: Exception) {}
            }

            Pair(parsedSkills, parsedPlugins)
        } catch (e: Exception) {
            Log.w("CustomizationCatalogue", "Remote catalogue fetch failed: ${e.message}")
            null
        }
    }

    fun calculateTokenUsage(context: Context, configDir: File, builtinDir: File): TokenUsageStats {
        val rulesBreakdown = mutableListOf<Pair<String, Int>>()
        val skillsBreakdown = mutableListOf<Pair<String, Int>>()

        val agentsFile = File(configDir, "AGENTS.md")
        if (agentsFile.exists() && agentsFile.length() > 0) {
            val tokens = (agentsFile.length() / 4).toInt()
            rulesBreakdown.add("AGENTS.md" to tokens)
        }

        val pluginsDir = File(configDir, "plugins")
        if (pluginsDir.isDirectory) {
            pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pDir ->
                val ruleFile = File(pDir, "rules/AGENTS.md")
                if (ruleFile.exists()) {
                    val tokens = (ruleFile.length() / 4).toInt()
                    rulesBreakdown.add("${pDir.name}/rules/AGENTS.md" to tokens)
                }
            }
        }

        val skillsDir = File(configDir, "skills")
        if (skillsDir.isDirectory) {
            skillsDir.listFiles()?.filter { it.isDirectory }?.forEach { sDir ->
                val sFile = File(sDir, "SKILL.md")
                if (sFile.exists()) {
                    val tokens = (sFile.length() / 4).toInt()
                    skillsBreakdown.add("skills/${sDir.name}" to tokens)
                }
            }
        }

        if (pluginsDir.isDirectory) {
            pluginsDir.listFiles()?.filter { it.isDirectory }?.forEach { pDir ->
                val pSkillsDir = File(pDir, "skills")
                if (pSkillsDir.isDirectory) {
                    pSkillsDir.listFiles()?.filter { it.isDirectory }?.forEach { sDir ->
                        val sFile = File(sDir, "SKILL.md")
                        if (sFile.exists() && skillsBreakdown.none { it.first == "${pDir.name}/${sDir.name}" || it.first == "skills/${sDir.name}" }) {
                            val tokens = (sFile.length() / 4).toInt()
                            skillsBreakdown.add("${pDir.name}/${sDir.name}" to tokens)
                        }
                    }
                }
            }
        }

        if (builtinDir.isDirectory) {
            builtinDir.listFiles()?.filter { it.isDirectory }?.take(6)?.forEach { sDir ->
                val sFile = File(sDir, "SKILL.md")
                if (sFile.exists() && skillsBreakdown.none { it.first == "skills/${sDir.name}" || it.first.endsWith("/${sDir.name}") }) {
                    val tokens = (sFile.length() / 4).toInt()
                    skillsBreakdown.add("builtin/${sDir.name}" to tokens)
                }
            }
        }

        val totalRulesTokens = rulesBreakdown.sumOf { it.second }
        val totalSkillsTokens = skillsBreakdown.sumOf { it.second }

        return TokenUsageStats(
            rulesTokens = totalRulesTokens,
            rulesBreakdowns = rulesBreakdown,
            skillsTokens = totalSkillsTokens,
            skillsBreakdowns = skillsBreakdown
        )
    }
}
