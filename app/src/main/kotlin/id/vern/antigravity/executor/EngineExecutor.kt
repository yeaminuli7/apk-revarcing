package id.vern.antigravity.executor

import android.content.Context
import android.system.Os
import id.vern.antigravity.install.BootstrapInstaller
import id.vern.antigravity.oauth.OAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.json.JSONArray
import org.json.JSONObject

object EngineExecutor {

    const val ACP_UNAVAILABLE = -42

    @Volatile
    private var isEnvironmentHealed = false

    @Volatile
    private var isGitPermissionsSet = false

    fun invalidateEnvironment() {
        isEnvironmentHealed = false
        isGitPermissionsSet = false
    }

    suspend fun executeCommand(
        context: Context,
        command: String,
        workingDir: File? = null,
        onOutputLine: (String, Boolean) -> Unit
    ): Int = executeCommandWithUpdates(context, command, workingDir) { line, isErr, isUpdate ->
        onOutputLine(line, isErr)
    }

    suspend fun executeCommandWithUpdates(
        context: Context,
        command: String,
        workingDir: File? = null,
        onOutput: (String, Boolean, Boolean) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        healEnvironment(context)

        val filesDir = context.filesDir
        val homeDir = File(filesDir, "home").apply { mkdirs() }
        val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
        val targetCwd = if (workingDir != null && workingDir.exists() && workingDir.isDirectory) workingDir else homeDir

        val prefixBin = File(filesDir, "usr/bin")
        val runtimeBin = File(filesDir, "runtime/bin")
        val engineBin = File(filesDir, "engine/bin")
        val gitExecDir = File(filesDir, "usr/libexec/git-core")
        val validGitExec = if (gitExecDir.exists() && gitExecDir.isDirectory) gitExecDir else null
        val validGitTemplate = File(filesDir, "usr/share/git-core/templates").apply { mkdirs() }

        val pathEnv = buildString {
            if (prefixBin.exists()) append("${prefixBin.absolutePath}:")
            if (validGitExec != null) append("${validGitExec.absolutePath}:")
            if (engineBin.exists()) append("${engineBin.absolutePath}:")
            if (runtimeBin.exists()) append("${runtimeBin.absolutePath}:")
            append("/system/bin")
        }

        val bashFile = File(prefixBin, "bash")
        if (bashFile.exists() && !bashFile.canExecute()) {
            bashFile.setReadable(true, false)
            bashFile.setExecutable(true, false)
            try { Os.chmod(bashFile.absolutePath, 493) } catch (e: Exception) {}
        }
        val shFile = File(prefixBin, "sh")
        if (shFile.exists() && !shFile.canExecute()) {
            shFile.setReadable(true, false)
            shFile.setExecutable(true, false)
            try { Os.chmod(shFile.absolutePath, 493) } catch (e: Exception) {}
        }
        val gitFile = File(prefixBin, "git")
        if (gitFile.exists() && !gitFile.canExecute()) {
            gitFile.setReadable(true, false)
            gitFile.setExecutable(true, false)
            try { Os.chmod(gitFile.absolutePath, 493) } catch (e: Exception) {}
        }

        val bashBin = when {
            File(prefixBin, "bash").canExecute() -> File(prefixBin, "bash").absolutePath
            File(runtimeBin, "bash").canExecute() -> File(runtimeBin, "bash").absolutePath
            File(prefixBin, "sh").canExecute() -> File(prefixBin, "sh").absolutePath
            else -> "/system/bin/sh"
        }

        try {
            OAuthManager.checkAndImportExistingToken(context)
            OAuthManager.syncCompanionStateFiles(context)
        } catch (e: Exception) {}

        try {
            val pb = ProcessBuilder(bashBin, "-c", command)
            pb.directory(targetCwd)

            val env = pb.environment()
            env.remove("LD_PRELOAD")
            val prefixLib = File(filesDir, "usr/lib")
            val certFile = File(filesDir, "usr/etc/tls/cert.pem")
            env["PREFIX"] = File(filesDir, "usr").absolutePath
            env["HOME"] = homeDir.absolutePath
            env["TMPDIR"] = tmpDir.absolutePath
            env["PWD"] = targetCwd.absolutePath
            env["GIT_CONFIG_NOSYSTEM"] = "1"
            env["GIT_ATTR_NOSYSTEM"] = "1"
            env["GIT_TERMINAL_PROMPT"] = "0"
            if (validGitExec != null) {
                env["GIT_EXEC_PATH"] = validGitExec.absolutePath
                if (!isGitPermissionsSet) {
                    validGitExec.listFiles()?.forEach { file ->
                        file.setReadable(true, false)
                        file.setExecutable(true, false)
                        try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
                    }
                    isGitPermissionsSet = true
                }
            }
            env["GIT_TEMPLATE_DIR"] = validGitTemplate.absolutePath
            env["PATH"] = pathEnv
            env["SHELL"] = bashBin
            env["TERM"] = "xterm-256color"
            env["LANG"] = "en_US.UTF-8"
            val ldPaths = mutableListOf(prefixLib.absolutePath, "/system/lib64")
            env["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")
            env["SSL_CERT_FILE"] = certFile.absolutePath
            env["CURL_CA_BUNDLE"] = certFile.absolutePath
            env["GIT_SSL_CAINFO"] = certFile.absolutePath
            env["OPENSSL_CONF"] = File(filesDir, "usr/etc/tls/openssl.cnf").absolutePath
            env["GODEBUG"] = "netdns=go"
            env["AGY_APPLICATION_ID"] = context.packageName
            env["APPLICATION_ID"] = context.packageName
            env["ANTIGRAVITY_APP_DATA_DIR"] = File(homeDir, ".gemini/antigravity-cli").apply { mkdirs() }.absolutePath

            val process = pb.start()

            val stdoutThread = Thread {
                streamOutput(process.inputStream, false, onOutput)
            }

            val stderrThread = Thread {
                streamOutput(process.errorStream, true, onOutput)
            }

            stdoutThread.start()
            stderrThread.start()

            val exitCode = try {
                process.waitFor()
            } finally {
                try { process.outputStream.close() } catch (e: Exception) {}
            }
            stdoutThread.join(1000)
            stderrThread.join(1000)
            exitCode
        } catch (e: Exception) {
            onOutput("Execution error: ${e.message}", true, false)
            -1
        }
    }

    private fun streamOutput(
        inputStream: java.io.InputStream,
        isError: Boolean,
        onOutput: (String, Boolean, Boolean) -> Unit
    ) {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8), 8192)
            val buffer = StringBuilder()
            var ch: Int
            while (reader.read().also { ch = it } != -1) {
                val c = ch.toChar()
                if (c == '\r') {
                    if (reader.ready()) {
                        reader.mark(1)
                        val next = reader.read()
                        if (next == '\n'.code) {
                            val line = buffer.toString()
                            buffer.clear()
                            onOutput(line, isError, false)
                            continue
                        } else if (next != -1) {
                            reader.reset()
                        }
                    }
                    val line = buffer.toString()
                    buffer.clear()
                    if (line.isNotEmpty()) {
                        onOutput(line, isError, true)
                    }
                } else if (c == '\n') {
                    val line = buffer.toString()
                    buffer.clear()
                    onOutput(line, isError, false)
                } else {
                    buffer.append(c)
                }
            }
            if (buffer.isNotEmpty()) {
                onOutput(buffer.toString(), isError, false)
            }
        } catch (e: Exception) {}
    }

    suspend fun executeAgy(
        context: Context,
        args: List<String>,
        workingDir: File? = null,
        onOutputLine: (String, Boolean) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir
        val prefixDir = File(filesDir, "usr")
        val engineDir = File(filesDir, "engine")
        val agyBin = listOf(
            File(engineDir, "bin/agy"),
            File(filesDir, "tmp/engine/bin/agy"),
            File(filesDir, "runtime/bin/agy")
        ).firstOrNull { it.exists() && it.isFile } ?: File(engineDir, "bin/agy")
        val ldBin = listOf(
            File(engineDir, "glibc/lib/ld-linux-aarch64.so.1"),
            File(filesDir, "runtime/lib/ld-linux-aarch64.so.1"),
            File(filesDir, "tmp/engine/glibc/lib/ld-linux-aarch64.so.1")
        ).firstOrNull { it.exists() && it.isFile } ?: File(engineDir, "glibc/lib/ld-linux-aarch64.so.1")
        val libDir = listOf(
            File(engineDir, "glibc/lib"),
            File(filesDir, "runtime/lib"),
            File(filesDir, "tmp/engine/glibc/lib")
        ).firstOrNull { it.exists() && it.isDirectory } ?: File(engineDir, "glibc/lib")
        val prefixLib = File(prefixDir, "lib")
        val homeDir = File(filesDir, "home").apply { mkdirs() }
        val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
        val certFile = File(prefixDir, "etc/tls/cert.pem")
        val targetCwd = if (workingDir != null && workingDir.exists() && workingDir.isDirectory) workingDir else homeDir

        try {
            BootstrapInstaller.ensurePermissions(context)
            OAuthManager.checkAndImportExistingToken(context)
            OAuthManager.syncCompanionStateFiles(context)

            val gitConfig = File(homeDir, ".gitconfig")
            val safeLine = "[safe]\n\tdirectory = *\n"
            if (!gitConfig.exists()) {
                gitConfig.writeText(safeLine)
            } else if (!gitConfig.readText().contains("directory = *")) {
                gitConfig.appendText("\n$safeLine")
            }

            val rootConfig = File(filesDir, ".gemini/config")
            val homeConfig = File(homeDir, ".gemini/config")
            if (rootConfig.exists() && (!homeConfig.exists() || homeConfig.list().isNullOrEmpty())) {
                homeConfig.mkdirs()
                rootConfig.copyRecursively(homeConfig, overwrite = false)
            } else if (homeConfig.exists() && (!rootConfig.exists() || rootConfig.list().isNullOrEmpty())) {
                rootConfig.mkdirs()
                homeConfig.copyRecursively(rootConfig, overwrite = false)
            }

            val candidateConfigs = listOf(
                File(rootConfig, "config.json"),
                File(homeConfig, "config.json"),
                File(homeDir, ".gemini/antigravity-cli/settings.json")
            )
            for (cf in candidateConfigs) {
                try {
                    if (!cf.exists() || cf.length() == 0L) {
                        cf.parentFile?.mkdirs()
                        val obj = org.json.JSONObject()
                        val userSettings = org.json.JSONObject().apply {
                            put("planReviewPolicy", "Always Proceed")
                            put("autoExecutionPolicy", "CASCADE_COMMANDS_AUTO_EXECUTION_EAGER")
                        }
                        obj.put("userSettings", userSettings)
                        cf.writeText(obj.toString(2), Charsets.UTF_8)
                    }
                } catch (e: Throwable) {}
            }
            id.vern.antigravity.service.PermissionConfigService.syncSettingsJsonFiles(context)
        } catch (e: Exception) {}

        if (ldBin.exists() && agyBin.exists()) {
            return@withContext try {
                val libraryPaths = listOf(libDir.absolutePath, prefixLib.absolutePath)
                    .filter { File(it).exists() }
                    .joinToString(":")

                val cmdList = mutableListOf(
                    ldBin.absolutePath,
                    "--library-path",
                    libraryPaths,
                    agyBin.absolutePath
                ).apply { addAll(args) }

                val pb = ProcessBuilder(cmdList)
                pb.directory(targetCwd)

                val gitExecDir = File(prefixDir, "libexec/git-core")
                val validGitExec = if (gitExecDir.exists() && gitExecDir.isDirectory) gitExecDir else null
                if (validGitExec != null && !isGitPermissionsSet) {
                    validGitExec.listFiles()?.forEach { file ->
                        file.setReadable(true, false)
                        file.setExecutable(true, false)
                        try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
                    }
                    isGitPermissionsSet = true
                }
                val validGitTemplate = File(prefixDir, "share/git-core/templates").apply { mkdirs() }

                val env = pb.environment()
                env.remove("LD_PRELOAD")
                env["PREFIX"] = prefixDir.absolutePath
                env["HOME"] = homeDir.absolutePath
                env["TMPDIR"] = tmpDir.absolutePath
                env["PWD"] = targetCwd.absolutePath
                env["GIT_CONFIG_NOSYSTEM"] = "1"
                env["GIT_ATTR_NOSYSTEM"] = "1"
                env["GIT_TERMINAL_PROMPT"] = "0"
                if (validGitExec != null) env["GIT_EXEC_PATH"] = validGitExec.absolutePath
                env["GIT_TEMPLATE_DIR"] = validGitTemplate.absolutePath
                env["TERM"] = "xterm-256color"
                env["LANG"] = "en_US.UTF-8"
                val extraGitPath = if (validGitExec != null) ":${validGitExec.absolutePath}" else ""
                env["PATH"] = "${File(prefixDir, "bin").absolutePath}:${File(engineDir, "bin").absolutePath}$extraGitPath:/system/bin"
                val ldPaths = mutableListOf(prefixLib.absolutePath, "/system/lib64")
                env["LD_LIBRARY_PATH"] = ldPaths.joinToString(":")
                if (certFile.exists()) {
                    env["SSL_CERT_FILE"] = certFile.absolutePath
                    env["CURL_CA_BUNDLE"] = certFile.absolutePath
                }
                env["GODEBUG"] = "netdns=cgo"
                env["AGY_APPLICATION_ID"] = context.packageName
                env["APPLICATION_ID"] = context.packageName
                env["ANTIGRAVITY_APP_DATA_DIR"] = File(homeDir, ".gemini/antigravity-cli").apply { mkdirs() }.absolutePath

                streamProcess(pb, onOutputLine)
            } catch (e: Exception) {
                onOutputLine("Direct engine execution failed: ${e.message}", true)
                -1
            }
        }

        val usrBinAgy = File(prefixDir, "bin/agy")
        if (usrBinAgy.exists()) {
            return@withContext try {
                val shellBin = when {
                    File(prefixDir, "bin/bash").canExecute() -> File(prefixDir, "bin/bash").absolutePath
                    File(prefixDir, "bin/sh").canExecute() -> File(prefixDir, "bin/sh").absolutePath
                    else -> "/system/bin/sh"
                }
                val cmdList = mutableListOf(shellBin, usrBinAgy.absolutePath).apply { addAll(args) }
                val pb = ProcessBuilder(cmdList)
                pb.directory(targetCwd)
                val gitExecDir = File(prefixDir, "libexec/git-core")
                val validGitExec = if (gitExecDir.exists() && gitExecDir.isDirectory) gitExecDir else null
                if (validGitExec != null && !isGitPermissionsSet) {
                    validGitExec.listFiles()?.forEach { file ->
                        file.setReadable(true, false)
                        file.setExecutable(true, false)
                        try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
                    }
                    isGitPermissionsSet = true
                }
                val validGitTemplate = File(prefixDir, "share/git-core/templates").apply { mkdirs() }
                val env = pb.environment()
                env.remove("LD_PRELOAD")
                env["PREFIX"] = prefixDir.absolutePath
                env["HOME"] = homeDir.absolutePath
                env["TMPDIR"] = tmpDir.absolutePath
                env["PWD"] = targetCwd.absolutePath
                env["GIT_CONFIG_NOSYSTEM"] = "1"
                env["GIT_ATTR_NOSYSTEM"] = "1"
                env["GIT_TERMINAL_PROMPT"] = "0"
                if (validGitExec != null) env["GIT_EXEC_PATH"] = validGitExec.absolutePath
                env["GIT_TEMPLATE_DIR"] = validGitTemplate.absolutePath
                val extraGitPath = if (validGitExec != null) ":${validGitExec.absolutePath}" else ""
                env["PATH"] = "${File(prefixDir, "bin").absolutePath}:${File(engineDir, "bin").absolutePath}$extraGitPath:/system/bin"
                env["TERM"] = "xterm-256color"
                env["LANG"] = "en_US.UTF-8"
                env["LD_LIBRARY_PATH"] = "${prefixLib.absolutePath}:/system/lib64"
                if (certFile.exists()) {
                    env["SSL_CERT_FILE"] = certFile.absolutePath
                    env["CURL_CA_BUNDLE"] = certFile.absolutePath
                }
                env["GODEBUG"] = "netdns=cgo"
                env["AGY_APPLICATION_ID"] = context.packageName
                env["APPLICATION_ID"] = context.packageName
                env["ANTIGRAVITY_APP_DATA_DIR"] = File(homeDir, ".gemini/antigravity-cli").apply { mkdirs() }.absolutePath
                streamProcess(pb, onOutputLine)
            } catch (e: Exception) {
                onOutputLine("Engine wrapper execution failed: ${e.message}", true)
                -1
            }
        }

        onOutputLine("Antigravity engine binaries not found. Please install via setup wizard.", true)
        -1
    }

    @Volatile
    private var activeAgyProcess: Process? = null

    fun isAgyRunning(): Boolean = activeAgyProcess != null

    fun stopAgyProcess() {
        try {
            activeAgyProcess?.destroyForcibly()
        } catch (e: Exception) {}
        activeAgyProcess = null
    }

    fun writeToAgyStdin(input: String): Boolean {
        val p = activeAgyProcess ?: return false
        return try {
            p.outputStream.write((input.trim() + "\n").toByteArray(Charsets.UTF_8))
            p.outputStream.flush()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun streamProcess(
        pb: ProcessBuilder,
        onOutputLine: (String, Boolean) -> Unit
    ): Int {
        val process = try {
            pb.start()
        } catch (e: Exception) {
            onOutputLine("Failed to launch process: ${e.message}", true)
            return -1
        }
        activeAgyProcess = process

        try {
            val stdoutThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { onOutputLine(it, false) }
                        }
                    }
                } catch (e: Exception) {}
            }

            val stderrThread = Thread {
                try {
                    BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { onOutputLine(it, true) }
                        }
                    }
                } catch (e: Exception) {}
            }

            stdoutThread.start()
            stderrThread.start()

            val exitCode = try {
                process.waitFor()
            } catch (e: Exception) {
                -1
            } finally {
                try { process.outputStream.close() } catch (e: Exception) {}
            }

            try { stdoutThread.join(1000) } catch (e: Exception) {}
            try { stderrThread.join(1000) } catch (e: Exception) {}
            return exitCode
        } finally {
            activeAgyProcess = null
        }
    }

    suspend fun executeAgyAcpTurn(
        context: Context,
        prompt: String,
        cliModelId: String? = null,
        sessionToResume: String? = null,
        workingDir: File? = null,
        onOutputLine: (String, Boolean) -> Unit
    ): Int = withContext(Dispatchers.IO) {
        healEnvironment(context)

        val filesDir = context.filesDir
        val homeDir = File(filesDir, "home").apply { mkdirs() }
        val tmpDir = File(filesDir, "tmp").apply { mkdirs() }
        val targetCwd = if (workingDir != null && workingDir.exists() && workingDir.isDirectory) workingDir else homeDir

        val engineDir = File(filesDir, "engine")
        val prefixDir = File(filesDir, "usr")
        val prefixLib = File(prefixDir, "lib")
        val libDir = File(engineDir, "glibc/lib")
        val ldBin = File(libDir, "ld-linux-aarch64.so.1")
        val agyBin = File(engineDir, "bin/agy")
        val certFile = File(prefixDir, "etc/tls/cert.pem")

        if (!ldBin.exists() || !agyBin.exists()) {
            return@withContext ACP_UNAVAILABLE
        }

        try {
            OAuthManager.checkAndImportExistingToken(context)
            OAuthManager.syncCompanionStateFiles(context)
            id.vern.antigravity.service.PermissionConfigService.syncSettingsJsonFiles(context)
        } catch (_: Exception) {}

        val libraryPaths = listOf(libDir.absolutePath, prefixLib.absolutePath)
            .filter { File(it).exists() }
            .joinToString(":")

        val cmdList = mutableListOf(
            ldBin.absolutePath,
            "--library-path",
            libraryPaths,
            agyBin.absolutePath,
            "acp",
            "--dangerously-skip-permissions"
        )
        if (workingDir != null && workingDir.exists() && workingDir.isDirectory) {
            cmdList.add("--add-dir")
            cmdList.add(workingDir.absolutePath)
        }

        val pb = ProcessBuilder(cmdList)
        pb.directory(targetCwd)

        val gitExecDir = File(prefixDir, "libexec/git-core")
        val validGitExec = if (gitExecDir.exists() && gitExecDir.isDirectory) gitExecDir else null
        val validGitTemplate = File(prefixDir, "share/git-core/templates").apply { mkdirs() }

        val env = pb.environment()
        env.remove("LD_PRELOAD")
        env["PREFIX"] = prefixDir.absolutePath
        env["HOME"] = homeDir.absolutePath
        env["TMPDIR"] = tmpDir.absolutePath
        env["PWD"] = targetCwd.absolutePath
        env["GIT_CONFIG_NOSYSTEM"] = "1"
        env["GIT_ATTR_NOSYSTEM"] = "1"
        env["GIT_TERMINAL_PROMPT"] = "0"
        if (validGitExec != null) env["GIT_EXEC_PATH"] = validGitExec.absolutePath
        env["GIT_TEMPLATE_DIR"] = validGitTemplate.absolutePath
        env["TERM"] = "xterm-256color"
        env["LANG"] = "en_US.UTF-8"
        val extraGitPath = if (validGitExec != null) ":${validGitExec.absolutePath}" else ""
        env["PATH"] = "${File(prefixDir, "bin").absolutePath}:${File(engineDir, "bin").absolutePath}$extraGitPath:/system/bin"
        env["LD_LIBRARY_PATH"] = "${prefixLib.absolutePath}:/system/lib64"
        if (certFile.exists()) {
            env["SSL_CERT_FILE"] = certFile.absolutePath
            env["CURL_CA_BUNDLE"] = certFile.absolutePath
        }
        env["GODEBUG"] = "netdns=cgo"
        env["AGY_APPLICATION_ID"] = context.packageName
        env["APPLICATION_ID"] = context.packageName
        env["ANTIGRAVITY_APP_DATA_DIR"] = File(homeDir, ".gemini/antigravity-cli").apply { mkdirs() }.absolutePath

        val process = try {
            pb.start()
        } catch (_: Exception) {
            return@withContext ACP_UNAVAILABLE
        }
        activeAgyProcess = process

        val stderrThread = Thread {
            try {
                BufferedReader(InputStreamReader(process.errorStream, Charsets.UTF_8)).use { errReader ->
                    var line: String?
                    while (errReader.readLine().also { line = it } != null) {
                        line?.let { onOutputLine(it, true) }
                    }
                }
            } catch (_: Exception) {}
        }
        stderrThread.start()

        var activeSessionId = ""
        val thoughtSb = StringBuilder()
        var thoughtStartMs = 0L
        val responseSb = StringBuilder()

        fun flushThoughtDone() {
            if (thoughtSb.isEmpty()) return
            val durSec = if (thoughtStartMs > 0L) {
                ((System.currentTimeMillis() - thoughtStartMs) / 1000L).toInt().coerceAtLeast(1)
            } else 1
            val stepObj = JSONObject().apply {
                put("step_type", "thought")
                put("state", "DONE")
                put("text", thoughtSb.toString())
                put("duration_seconds", durSec)
            }
            val evt = JSONObject().apply {
                put("event", "step_update")
                if (activeSessionId.isNotBlank()) put("conversation_id", activeSessionId)
                put("step_update", stepObj)
            }
            onOutputLine(evt.toString(), false)
            thoughtSb.clear()
            thoughtStartMs = 0L
        }

        fun extractToolOutput(upd: JSONObject): String? {
            val rawOut = upd.optString("output").ifBlank { upd.optString("rawOutput") }
            if (rawOut.isNotBlank()) return rawOut
            val contentArr = upd.optJSONArray("content") ?: return null
            val sb = StringBuilder()
            for (i in 0 until contentArr.length()) {
                val item = contentArr.optJSONObject(i) ?: continue
                val text = item.optString("text").ifBlank {
                    item.optJSONObject("content")?.optString("text") ?: ""
                }
                if (text.isNotBlank()) {
                    if (sb.isNotEmpty()) sb.append("\n")
                    sb.append(text)
                }
            }
            return sb.toString().ifBlank { null }
        }

        return@withContext try {
            val writer = BufferedWriter(OutputStreamWriter(process.outputStream, Charsets.UTF_8))
            val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))

            fun sendRpc(id: Int, method: String, params: JSONObject) {
                val req = JSONObject().apply {
                    put("jsonrpc", "2.0")
                    put("id", id)
                    put("method", method)
                    put("params", params)
                }
                writer.write(req.toString())
                writer.newLine()
                writer.flush()
            }

            fun readUntilResponse(expectedId: Int): JSONObject? {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmed = line?.trim().orEmpty()
                    if (!trimmed.startsWith("{")) continue
                    val msg = try { JSONObject(trimmed) } catch (_: Exception) { continue }
                    val method = msg.optString("method")
                    if (method == "session/update") {
                        val upd = msg.optJSONObject("params")?.optJSONObject("update") ?: continue
                        when (upd.optString("sessionUpdate")) {
                            "agent_thought_chunk" -> {
                                val chunk = upd.optJSONObject("content")?.optString("text", "") ?: ""
                                if (chunk.isNotEmpty()) {
                                    if (thoughtStartMs == 0L) thoughtStartMs = System.currentTimeMillis()
                                    thoughtSb.append(chunk)
                                    val durSec = ((System.currentTimeMillis() - thoughtStartMs) / 1000L).toInt().coerceAtLeast(1)
                                    val stepObj = JSONObject().apply {
                                        put("step_type", "thought")
                                        put("state", "ACTIVE")
                                        put("text", thoughtSb.toString())
                                        put("text_delta", chunk)
                                        put("duration_seconds", durSec)
                                    }
                                    val evt = JSONObject().apply {
                                        put("event", "step_update")
                                        if (activeSessionId.isNotBlank()) put("conversation_id", activeSessionId)
                                        put("step_update", stepObj)
                                    }
                                    onOutputLine(evt.toString(), false)
                                }
                            }
                            "tool_call", "tool_call_update" -> {
                                flushThoughtDone()
                                val toolName = upd.optString("name").ifBlank { upd.optString("title", "tool") }
                                val status = upd.optString("status").lowercase()
                                val state = if (status == "completed" || status == "failed" || status == "cancelled" || status == "done") {
                                    "DONE"
                                } else {
                                    "ACTIVE"
                                }
                                val argsObj = upd.optJSONObject("args") ?: upd.optJSONObject("rawInput")
                                val toolInfo = JSONObject().apply {
                                    if (argsObj != null) put("args", argsObj)
                                }
                                val stepObj = JSONObject().apply {
                                    put("step_type", "tool")
                                    put("state", state)
                                    put("tool_name", toolName)
                                    put("tool_info", toolInfo)
                                    extractToolOutput(upd)?.let { put("output", it) }
                                }
                                val evt = JSONObject().apply {
                                    put("event", "step_update")
                                    if (activeSessionId.isNotBlank()) put("conversation_id", activeSessionId)
                                    put("step_update", stepObj)
                                }
                                onOutputLine(evt.toString(), false)
                            }
                            "agent_message_chunk" -> {
                                flushThoughtDone()
                                val chunk = upd.optJSONObject("content")?.optString("text", "") ?: ""
                                if (chunk.isNotEmpty()) {
                                    responseSb.append(chunk)
                                    val stepObj = JSONObject().apply {
                                        put("step_type", "agent_response")
                                        put("state", "ACTIVE")
                                        put("text_delta", chunk)
                                    }
                                    val evt = JSONObject().apply {
                                        put("event", "step_update")
                                        if (activeSessionId.isNotBlank()) put("conversation_id", activeSessionId)
                                        put("step_update", stepObj)
                                    }
                                    onOutputLine(evt.toString(), false)
                                }
                            }
                        }
                    } else if (msg.has("id") && msg.optInt("id", -1) == expectedId) {
                        return msg
                    }
                }
                return null
            }

            // 1. Initialize ACP session
            sendRpc(1, "initialize", JSONObject().apply {
                put("protocolVersion", 1)
                put("clientCapabilities", JSONObject())
            })
            val initResp = readUntilResponse(1)
            if (initResp == null || initResp.has("error")) {
                return@withContext ACP_UNAVAILABLE
            }

            // 2. Resume or create session
            var nextId = 2
            var sessionResp: JSONObject? = null
            if (!sessionToResume.isNullOrBlank()) {
                sendRpc(nextId, "session/resume", JSONObject().apply {
                    put("sessionId", sessionToResume)
                    put("cwd", targetCwd.absolutePath)
                    put("mcpServers", JSONArray())
                })
                val resumed = readUntilResponse(nextId++)
                if (resumed != null && !resumed.has("error")) {
                    sessionResp = resumed
                    activeSessionId = resumed.optJSONObject("result")?.optString("sessionId")
                        ?.ifBlank { sessionToResume } ?: sessionToResume
                }
            }
            if (sessionResp == null) {
                sendRpc(nextId, "session/new", JSONObject().apply {
                    put("cwd", targetCwd.absolutePath)
                    put("mcpServers", JSONArray())
                })
                sessionResp = readUntilResponse(nextId++)
                if (sessionResp == null || sessionResp.has("error")) {
                    return@withContext ACP_UNAVAILABLE
                }
                activeSessionId = sessionResp.optJSONObject("result")?.optString("sessionId").orEmpty()
            }
            if (activeSessionId.isBlank()) {
                return@withContext ACP_UNAVAILABLE
            }

            onOutputLine(JSONObject().apply {
                put("event", "init")
                put("conversation_id", activeSessionId)
            }.toString(), false)

            // 3. Select model if specified
            if (!cliModelId.isNullOrBlank()) {
                sendRpc(nextId, "session/set_model", JSONObject().apply {
                    put("sessionId", activeSessionId)
                    put("modelId", cliModelId)
                })
                readUntilResponse(nextId++)
            }

            // 4. Run prompt turn
            val promptId = nextId++
            sendRpc(promptId, "session/prompt", JSONObject().apply {
                put("sessionId", activeSessionId)
                put("prompt", JSONArray().put(JSONObject().apply {
                    put("type", "text")
                    put("text", prompt)
                }))
            })
            val promptResp = readUntilResponse(promptId)
            flushThoughtDone()

            if (promptResp == null) {
                return@withContext -1
            }
            if (promptResp.has("error")) {
                val errMsg = promptResp.optJSONObject("error")?.optString("message") ?: "ACP prompt error"
                onOutputLine(errMsg, true)
                return@withContext 1
            }

            onOutputLine(JSONObject().apply {
                put("event", "result")
                put("conversation_id", activeSessionId)
                put("result", JSONObject().apply {
                    put("status", "SUCCESS")
                    put("response", responseSb.toString())
                })
            }.toString(), false)
            0
        } catch (_: Exception) {
            if (activeSessionId.isBlank()) ACP_UNAVAILABLE else -1
        } finally {
            try { process.outputStream.close() } catch (_: Exception) {}
            try { process.destroy() } catch (_: Exception) {}
            try { stderrThread.join(500) } catch (_: Exception) {}
            activeAgyProcess = null
        }
    }

    fun healEnvironment(context: Context, force: Boolean = false) {
        if (isEnvironmentHealed && !force) return
        try {
            val filesDir = context.filesDir
            val prefix = File(filesDir, "usr")
            val binDir = File(prefix, "bin").apply { mkdirs() }
            val binLib = File(binDir, "lib").apply { mkdirs() }
            val pkgLib = File(prefix, "pkg/lib").apply { mkdirs() }
            val etcPkg = File(prefix, "etc/pkg").apply { mkdirs() }
            val etcTls = File(prefix, "etc/tls").apply { mkdirs() }

            val d = '$'

            val aptWrapper = "#!/system/bin/sh\nexec \"${d}(dirname \"${d}0\")/pkg\" \"${d}@\"\n"

            listOf("apt", "apt-get", "apt-cache", "apt-config", "apt-key", "apt-mark").forEach { name ->
                val target = File(binDir, name)
                var rewrite = false
                if (!target.exists() || target.length() > 2000L) {
                    rewrite = true
                } else {
                    try {
                        val head = target.readText()
                        if (!head.contains("pkg") || head.contains("com.termux") || head.contains("com.agy")) {
                            rewrite = true
                        }
                    } catch (e: Exception) {
                        rewrite = true
                    }
                }
                if (rewrite) {
                    try {
                        target.delete()
                        target.writeText(aptWrapper)
                        target.setReadable(true, false)
                        target.setExecutable(true, false)
                        try { Os.chmod(target.absolutePath, 493) } catch (e: Exception) {}
                    } catch (e: Exception) {}
                }
            }

            val binPkg = File(binDir, "pkg")
            var pkgRewrite = false
            if (!binPkg.exists() || binPkg.length() == 0L) {
                pkgRewrite = true
            } else {
                try {
                    val txt = binPkg.readText()
                    if (txt.contains("/data/data/com.agy") || txt.contains("/data/data/com.termux")) {
                        pkgRewrite = true
                    }
                } catch (e: Exception) {
                    pkgRewrite = true
                }
            }

            if (pkgRewrite) {
                copyAssetFile(context, "pkg/pkg", binPkg)
                try {
                    val txt = binPkg.readText()
                    if (txt.contains("/data/data/com.agy") || txt.contains("/data/data/com.termux")) {
                        val fixed = "#!/system/bin/sh\n" +
                                "if [ -z \"${d}{BASH_VERSION:-}\" ]; then\n" +
                                "    if [ -x \"${d}(dirname \"${d}0\")/bash\" ]; then\n" +
                                "        exec \"${d}(dirname \"${d}0\")/bash\" \"${d}0\" \"${d}@\"\n" +
                                "    elif command -v bash >/dev/null 2>&1; then\n" +
                                "        exec bash \"${d}0\" \"${d}@\"\n" +
                                "    fi\n" +
                                "fi\n" + txt.substringAfter("\n")
                        binPkg.writeText(fixed)
                    }
                } catch (e: Exception) {}
            }
            binPkg.setReadable(true, false)
            binPkg.setExecutable(true, false)
            try { Os.chmod(binPkg.absolutePath, 493) } catch (e: Exception) {}

            val pkgLibNames = listOf(
                "db.sh", "deb.sh", "fetch.sh", "index.awk", "index.sh",
                "resolver.sh", "util.sh", "vercmp.awk", "version.sh"
            )
            for (fName in pkgLibNames) {
                val fBin = File(binLib, fName)
                val fPkg = File(pkgLib, fName)
                if (!fBin.exists() || fBin.length() == 0L) {
                    if (fPkg.exists() && fPkg.length() > 0L) {
                        try { fPkg.copyTo(fBin, overwrite = true) } catch (e: Exception) {}
                    } else {
                        copyAssetFile(context, "pkg/lib/$fName", fBin)
                    }
                }
                if (!fPkg.exists() || fPkg.length() == 0L) {
                    if (fBin.exists() && fBin.length() > 0L) {
                        try { fBin.copyTo(fPkg, overwrite = true) } catch (e: Exception) {}
                    } else {
                        copyAssetFile(context, "pkg/lib/$fName", fPkg)
                    }
                }
                fBin.setReadable(true, false)
                fBin.setExecutable(true, false)
                fPkg.setReadable(true, false)
                fPkg.setExecutable(true, false)
                try { Os.chmod(fBin.absolutePath, 493) } catch (e: Exception) {}
                try { Os.chmod(fPkg.absolutePath, 493) } catch (e: Exception) {}
            }

            val repoConf = File(etcPkg, "repo.conf")
            if (!repoConf.exists() || repoConf.length() == 0L) {
                repoConf.writeText(
                    """
                    # repo.conf -- package repositories.
                    PKG_REPOS=(
                        "https://packages-cf.termux.dev/apt/termux-main"
                        "https://mirrors.bfsu.edu.cn/termux/apt/termux-main"
                        "https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main"
                        "https://packages.termux.dev/apt/termux-main"
                    )
                    PKG_CHANNEL=stable
                    PKG_COMPONENT=main
                    PKG_ARCH=aarch64
                    """.trimIndent() + "\n"
                )
            }

            val certPem = File(etcTls, "cert.pem")
            if (!certPem.exists() || certPem.length() == 0L) {
                copyAssetFile(context, "overlay/etc/tls/cert.pem", certPem)
            }
            if (!certPem.exists() || certPem.length() == 0L) {
                try {
                    val sysCacerts = File("/system/etc/security/cacerts")
                    if (sysCacerts.isDirectory) {
                        certPem.parentFile?.mkdirs()
                        certPem.outputStream().use { out ->
                            sysCacerts.listFiles()?.filter { it.extension == "0" }?.forEach { cFile ->
                                try {
                                    cFile.inputStream().use { it.copyTo(out) }
                                    out.write("\n".toByteArray())
                                } catch (e: Exception) {}
                            }
                        }
                    }
                } catch (e: Exception) {}
            }
            try { certPem.setReadable(true, false) } catch (e: Exception) {}

            binDir.listFiles()?.forEach { binFile ->
                if (!binFile.canExecute()) {
                    binFile.setReadable(true, false)
                    binFile.setExecutable(true, false)
                    try { Os.chmod(binFile.absolutePath, 493) } catch (e: Exception) {}
                }
            }

            val resolvFile = File(filesDir, "resolv.conf")
            if (!resolvFile.exists() || resolvFile.length() == 0L) {
                resolvFile.writeText("nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n", Charsets.UTF_8)
                resolvFile.setReadable(true, false)
            }
            val hostsFile = File(filesDir, "hosts")
            if (!hostsFile.exists() || hostsFile.length() == 0L) {
                hostsFile.writeText("127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n", Charsets.UTF_8)
                hostsFile.setReadable(true, false)
            }
            isEnvironmentHealed = true
        } catch (e: Exception) {}
    }

    private fun copyAssetFile(context: Context, assetPath: String, dest: File) {
        try {
            dest.parentFile?.mkdirs()
            context.assets.open(assetPath).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {}
    }
}

