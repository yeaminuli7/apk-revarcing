package id.vern.antigravity.install

import android.content.Context
import android.os.Environment
import android.system.Os
import android.system.OsConstants
import android.util.Log
import id.vern.antigravity.util.AppLogger
import id.vern.antigravity.util.NetworkDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Date
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

object BootstrapInstaller {

    private const val TAG = "BootstrapInstaller"

    private const val ASSET_GLIBC = "antigravity-glibc.zip"
    private const val BOOTSTRAP_URL = "https://github.com/KuatoDev/antigravity-bootstrap/releases/download/core-v1/bootstrap-aarch64.zip"
    private const val BOOTSTRAP_SHA256 = "6a740663ffaca4e63cb5e485c08b150828ef07fd6325f1f2146414a2d256855c"
    private const val MARKER_FILE = ".installed"
    private const val ERROR_LOG_FILE = "bootstrap_error.log"

    @Volatile
    var lastError: String? = null

    @Volatile
    var lastStackTrace: String? = null

    fun prefixDir(context: Context): File = File(context.filesDir, "usr")
    fun engineDir(context: Context): File = File(context.filesDir, "engine")
    fun geminiDir(context: Context): File = File(context.filesDir, ".gemini")
    fun getErrorLogFile(context: Context): File = File(context.filesDir, ERROR_LOG_FILE)

    fun isInstalled(context: Context): Boolean {
        val prefix = prefixDir(context)
        val bash = File(prefix, "bin/bash")
        val sh = File(prefix, "bin/sh")
        val marker = File(prefix, MARKER_FILE)
        val engine = engineDir(context)
        val agyBin = File(engine, "bin/agy")
        val ldBin = File(engine, "glibc/lib/ld-linux-aarch64.so.1")

        val prefixOk = bash.isFile && sh.exists() && marker.isFile
        // A truncated or zero length agy is not "installed": the dynamic loader then reports
        // ".../engine/bin/agy: cannot open shared object file" and boot dies in the shell.
        val engineOk = agyBin.isFile && agyBin.length() > 1_000_000L && ldBin.isFile && ldBin.length() > 100_000L

        return prefixOk && engineOk
    }

    suspend fun installIfNeeded(
        context: Context,
        onProgress: (step: String, progress: Float) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir
        val prefix = prefixDir(context)
        val engine = engineDir(context)

        if (isInstalled(context)) {
            AppLogger.d("Boot", "Linux userspace and engine verified")
            ensurePermissions(context)
            deployOverlayAndTools(context, prefix, engine)
            createAgyWrapper(filesDir, prefix, engine)
            healEngineIfMissing(context) { msg, p -> onProgress(msg, p) }
            return@withContext true
        }

        lastError = null
        lastStackTrace = null

        // The Termux prefix is intact (bash + .installed) but the core engine is missing or
        // broken. Do not re-download the whole 85 MB bootstrap: repair just the engine.
        val prefixOk = File(prefix, "bin/bash").isFile && File(prefix, MARKER_FILE).isFile
        if (prefixOk && engineProblem(context) != null) {
            AppLogger.w("Boot", "Prefix present but engine unusable - repairing engine only")
            val repaired = try {
                engine.mkdirs()
                onProgress("Verifying glibc runtime...", 0.55f)
                if (!File(engine, "glibc/lib/ld-linux-aarch64.so.1").isFile) {
                    extractGlibc(context, engine)
                }
                installCoreEngine(context) { msg, p -> onProgress(msg, 0.6f + p * 0.3f) }
                onProgress("Configuring CLI tools & agy-daemon...", 0.9f)
                deployOverlayAndTools(context, prefix, engine)
                ensurePermissions(context)
                createAgyWrapper(filesDir, prefix, engine)
                verifyEngine(context)
                engineProblem(context) == null
            } catch (e: Throwable) {
                AppLogger.e("Boot", "Engine-only repair threw: ${e.message} - falling back to full install")
                false
            }
            if (repaired) {
                onProgress("Environment ready", 1.0f)
                return@withContext true
            }
            AppLogger.e("Boot", "Engine-only repair failed - falling back to full install")
        }

        try {
            File(filesDir, "home").mkdirs()
            File(filesDir, "tmp").mkdirs()
            geminiDir(context).mkdirs()

            prefix.mkdirs()
            engine.mkdirs()

            extractBootstrap(context, prefix) { msg, p -> onProgress(msg, p * 0.35f) }

            onProgress("Reconstructing POSIX symlinks...", 0.45f)
            restoreSymlinks(prefix)

            onProgress("Deploying glibc runtime...", 0.55f)
            extractGlibc(context, engine)

            installCoreEngine(context) { msg, p -> onProgress(msg, 0.55f + p * 0.3f) }

            onProgress("Configuring CLI tools & agy-daemon...", 0.9f)
            deployOverlayAndTools(context, prefix, engine)

            onProgress("Finalizing environment verification...", 0.97f)
            ensurePermissions(context)
            createAgyWrapper(filesDir, prefix, engine)

            File(prefix, MARKER_FILE).writeText(
                "installed=true\ntimestamp=${System.currentTimeMillis()}\narch=aarch64\n"
            )

            verifyEngine(context)

            val errFile = getErrorLogFile(context)
            if (errFile.exists()) errFile.delete()

            onProgress("Environment ready", 1.0f)
            Log.i(TAG, "Antigravity Linux userspace installed successfully at $prefix")
            true
        } catch (e: Throwable) {
            val errMsg = "${e.javaClass.simpleName}: ${e.message ?: "Unknown error"}"
            val trace = e.stackTraceToString()
            lastError = errMsg
            lastStackTrace = trace
            Log.e(TAG, "Bootstrap installation failed: $errMsg", e)

            try {
                getErrorLogFile(context).writeText(
                    """
                    Bootstrap Installation Failure
                    Timestamp: ${Date()}
                    Error: $errMsg

                    Stack Trace:
                    $trace
                    """.trimIndent()
                )
            } catch (e: Exception) {}

            false
        }
    }

    private fun openAssetStream(context: Context, assetName: String, fallbackPaths: List<String>): Pair<InputStream, ZipFile?> {

        try {
            val apkPath = context.applicationInfo.sourceDir
            if (!apkPath.isNullOrEmpty()) {
                val apkFile = File(apkPath)
                if (apkFile.isFile && apkFile.canRead()) {
                    val apkZip = ZipFile(apkFile)
                    val entry = apkZip.getEntry("assets/$assetName")
                    if (entry != null) {
                        return Pair(apkZip.getInputStream(entry), apkZip)
                    }
                    apkZip.close()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading $assetName from APK directly: ${e.message}")
        }

        try {
            return Pair(context.assets.open(assetName), null)
        } catch (e: Exception) {
            Log.w(TAG, "AssetManager.open failed for $assetName: ${e.message}")
        }

        for (path in fallbackPaths) {
            val file = File(path)
            if (file.isFile && file.canRead()) {
                return Pair(file.inputStream(), null)
            }
        }

        throw IllegalStateException("Cannot locate asset '$assetName' in APK assets or external fallbacks.")
    }

    private fun extractBootstrap(context: Context, targetDir: File, onProgress: (String, Float) -> Unit) {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val local = if (downloadDir != null) File(downloadDir, "bootstrap-aarch64.zip").takeIf { it.isFile && it.canRead() } else null
        if (local != null) {
            val localHash = calculateFileSha256(local)
            if (localHash.equals(BOOTSTRAP_SHA256, ignoreCase = true)) {
                onProgress("Extracting Linux userspace bootstrap...", 0.9f)
                local.inputStream().use { extractZipStream(it, targetDir) }
                return
            } else {
                Log.w(TAG, "Local bootstrap at ${local.absolutePath} failed checksum verification, downloading from network")
            }
        }
        // 1) Prefer the copy bundled inside the APK: first launch then needs no network.
        val bundled = runCatching {
            openAssetStream(context, "bootstrap-aarch64.zip", emptyList()).let { (stream, owner) ->
                try {
                    onProgress("Installing bundled Linux userspace...", 0.6f)
                    stream.use { extractZipStream(it, targetDir) }
                } finally {
                    try { owner?.close() } catch (e: Exception) {}
                }
            }
            true
        }.getOrElse { e ->
            Log.w(TAG, "Bundled bootstrap unavailable (${e.message}); falling back to download")
            false
        }
        if (bundled) return

        val zipFile = File(context.cacheDir, "bootstrap-aarch64.zip")
        downloadVerified(BOOTSTRAP_URL, BOOTSTRAP_SHA256, zipFile) { p ->
            onProgress("Downloading Linux userspace bootstrap...", p * 0.85f)
        }
        try {
            onProgress("Extracting Linux userspace bootstrap...", 0.9f)
            zipFile.inputStream().use { extractZipStream(it, targetDir) }
        } finally {
            try { zipFile.delete() } catch (e: Exception) {}
        }
    }

    private fun downloadVerified(url: String, expectedSha256: String, dest: File, onProgress: (Float) -> Unit) {
        val maxAttempts = 5
        var attempt = 0
        var lastException: Throwable? = null

        if (dest.isFile && dest.length() > 0) {
            val existingHash = calculateFileSha256(dest)
            if (existingHash.equals(expectedSha256, ignoreCase = true)) {
                Log.i(TAG, "Pre-cached bootstrap archive at ${dest.absolutePath} is valid; skipping download.")
                onProgress(1.0f)
                return
            }
        }

        dest.parentFile?.mkdirs()

        while (attempt < maxAttempts) {
            attempt++
            try {
                NetworkDownloader.downloadWithResume(
                    url = url,
                    dest = dest,
                    maxAttempts = 1,
                    onProgress = { bytesRead, totalBytes ->
                        if (totalBytes > 0) {
                            onProgress(bytesRead.toFloat() / totalBytes)
                        }
                    }
                )

                val actual = calculateFileSha256(dest)
                if (actual.equals(expectedSha256, ignoreCase = true)) {
                    onProgress(1.0f)
                    Log.i(TAG, "Bootstrap download verified successfully on attempt $attempt")
                    return
                } else {
                    Log.w(TAG, "Bootstrap checksum mismatch (got $actual, expected $expectedSha256). Deleting and retrying...")
                    dest.delete()
                    throw IllegalStateException("Bootstrap download failed checksum verification")
                }
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Bootstrap download attempt $attempt/$maxAttempts failed: ${e.message}", e)
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(attempt * 2000L)
                    } catch (ie: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }

        try { dest.delete() } catch (e: Exception) {}
        throw IllegalStateException("Failed downloading bootstrap after $maxAttempts attempts: ${lastException?.message}", lastException)
    }

    private fun calculateFileSha256(file: File): String {
        if (!file.exists() || !file.canRead()) return ""
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(65536)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns a human readable reason why the core engine is unusable, or null when it is fine.
     * A zero length / non-ELF `engine/bin/agy` triggers the confusing loader error
     * "error while loading shared libraries: .../agy: cannot open shared object file".
     */
    fun engineProblem(context: Context): String? {
        val engine = engineDir(context)
        val agy = File(engine, "bin/agy")
        val ld = File(engine, "glibc/lib/ld-linux-aarch64.so.1")
        if (!ld.isFile || ld.length() < 100_000L) {
            return "glibc loader missing (${ld.absolutePath})"
        }
        if (!agy.isFile) return "core engine binary missing (${agy.absolutePath})"
        if (agy.length() < 1_000_000L) return "core engine binary is truncated (${agy.length()} bytes)"
        val header = ByteArray(4)
        try {
            java.io.FileInputStream(agy).use { it.read(header) }
        } catch (e: Exception) {
            return "core engine binary unreadable: ${e.message}"
        }
        if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() ||
            header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()
        ) {
            return "core engine binary is not an ELF executable"
        }
        if (!agy.canExecute()) return "core engine binary is not executable"
        return null
    }

    private suspend fun healEngineIfMissing(context: Context, onProgress: (String, Float) -> Unit) {
        val problem = engineProblem(context)
        if (problem == null) {
            AppLogger.d("Boot", "Core engine verified: ${File(engineDir(context), "bin/agy").length()} bytes")
            return
        }
        AppLogger.w("Boot", "Core engine check failed ($problem) - reinstalling")
        try {
            installCoreEngine(context) { msg, p -> onProgress(msg, 0.6f + p * 0.3f) }
            val after = engineProblem(context)
            if (after != null) AppLogger.e("Boot", "Core engine still unusable after reinstall: $after")
            else AppLogger.d("Boot", "Core engine repaired")
        } catch (e: Exception) {
            AppLogger.e("Boot", "Core engine reinstall failed: ${e.message}")
        }
    }

    private fun verifyEngine(context: Context) {
        val problem = engineProblem(context)
        if (problem != null) {
            AppLogger.e("Boot", "Engine verification failed after install: $problem")
        } else {
            AppLogger.d("Boot", "Engine verified, agy=${File(engineDir(context), "bin/agy").length()} bytes")
        }
    }

    private fun installCoreEngine(context: Context, onProgress: (String, Float) -> Unit) {
        // The CI build packs the engine into the APK, so a normal install is offline.
        if (CoreUpdateManager.hasBundledEngine(context)) {
            AppLogger.d("Boot", "Installing core engine from bundled payload")
            val bundled = CoreUpdateManager.installBundledEngine(context) { p, msg -> onProgress(msg, p) }
            if (bundled.isSuccess) return
            AppLogger.w("Boot", "Bundled engine install failed (${bundled.exceptionOrNull()?.message}); downloading")
        } else {
            AppLogger.d("Boot", "No bundled engine in this APK - downloading")
        }

        val info = CoreUpdateManager.checkForUpdate(context)
        val result = CoreUpdateManager.downloadAndApply(context, info) { p, msg -> onProgress(msg, p) }
        if (result.isFailure) {
            throw IllegalStateException(result.exceptionOrNull()?.message ?: "Core engine download failed")
        }
    }

    private fun extractGlibc(context: Context, targetDir: File) {
        val (stream, zipOwner) = openAssetStream(context, ASSET_GLIBC, emptyList())
        try {
            extractZipStream(stream, targetDir)
            patchGlibcResolvConf(context, targetDir)
        } finally {
            try { stream.close() } catch (e: Exception) {}
            try { zipOwner?.close() } catch (e: Exception) {}
        }
    }

    private fun extractZipStream(stream: InputStream, targetDir: File) {
        val targetCanonical = targetDir.canonicalPath
        val buffer = ByteArray(65536)

        ZipInputStream(stream.buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val destFile = File(targetDir, entryName)
                val destCanonical = destFile.canonicalPath
                if (!destCanonical.startsWith(targetCanonical)) {
                    entry = zis.nextEntry
                    continue
                }

                if (entry.isDirectory) {
                    destFile.mkdirs()
                } else {
                    destFile.parentFile?.mkdirs()

                    if (destFile.exists()) {
                        destFile.delete()
                    } else {
                        try { Os.remove(destFile.absolutePath) } catch (e: Exception) {}
                    }

                    FileOutputStream(destFile).use { fos ->
                        var read: Int
                        while (zis.read(buffer).also { read = it } != -1) {
                            fos.write(buffer, 0, read)
                        }
                    }

                    if (entryName.startsWith("bin/") ||
                        entryName.startsWith("lib/") ||
                        entryName.startsWith("libexec/") ||
                        entryName.contains("ld-linux") ||
                        entryName.endsWith(".so") ||
                        entryName.contains(".so.")
                    ) {
                        destFile.setReadable(true, false)
                        destFile.setExecutable(true, false)
                        try {
                            Os.chmod(destFile.absolutePath, 493)
                        } catch (e: Exception) {}
                    }
                }
                entry = zis.nextEntry
            }
        }
    }

    private fun restoreSymlinks(prefix: File) {
        val symlinkFile = File(prefix, "SYMLINKS.txt")
        if (!symlinkFile.isFile) {
            Log.w(TAG, "SYMLINKS.txt not found in prefix")
            return
        }

        try {
            BufferedReader(InputStreamReader(symlinkFile.inputStream(), Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line?.trim() ?: continue
                    if (l.isEmpty()) continue

                    val parts = l.split("←")
                    if (parts.size != 2) continue

                    val target = parts[0].trim()
                    var relPath = parts[1].trim()
                    if (relPath.startsWith("./")) {
                        relPath = relPath.substring(2)
                    }

                    val linkFile = File(prefix, relPath)
                    linkFile.parentFile?.mkdirs()

                    try { Os.remove(linkFile.absolutePath) } catch (e: Exception) {}
                    if (linkFile.exists()) {
                        linkFile.delete()
                    }

                    try {
                        Os.symlink(target, linkFile.absolutePath)
                    } catch (e: Exception) {
                        Log.d(TAG, "Symlink creation skipped: ${linkFile.name} -> $target (${e.message})")
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error restoring symlinks: ${e.message}")
        }
    }

    private fun deployOverlayAndTools(context: Context, prefix: File, engine: File) {

        var deployedFromApk = false
        try {
            val apkPath = context.applicationInfo.sourceDir
            if (!apkPath.isNullOrEmpty()) {
                val apkFile = File(apkPath)
                if (apkFile.isFile && apkFile.canRead()) {
                    ZipFile(apkFile).use { apkZip ->
                        val entries = apkZip.entries()
                        val buffer = ByteArray(32768)
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            val name = entry.name
                            if (name.startsWith("assets/overlay/")) {
                                val relPath = name.removePrefix("assets/overlay/")
                                if (relPath.isNotEmpty()) {
                                    val dest = File(prefix, relPath)
                                    extractApkEntryToFile(apkZip, entry, dest, buffer)
                                }
                            } else if (name.startsWith("assets/pkg/")) {
                                val relPath = name.removePrefix("assets/pkg/")
                                if (relPath.isNotEmpty()) {
                                    val dest = File(File(prefix, "pkg"), relPath)
                                    extractApkEntryToFile(apkZip, entry, dest, buffer)
                                    if (relPath == "pkg") {
                                        val binPkg = File(prefix, "bin/pkg")
                                        extractApkEntryToFile(apkZip, entry, binPkg, buffer)
                                    } else if (relPath.startsWith("lib/")) {
                                        val binLib = File(prefix, "bin/$relPath")
                                        extractApkEntryToFile(apkZip, entry, binLib, buffer)
                                    }
                                }
                            }
                        }
                        deployedFromApk = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed deploying overlay from APK: ${e.message}")
        }

        if (!deployedFromApk) {
            copyAssetDirectory(context, "overlay", prefix)
            copyAssetDirectory(context, "pkg", File(prefix, "pkg"))
            copyAssetDirectory(context, "pkg/lib", File(prefix, "bin/lib"))
            val binPkg = File(prefix, "bin/pkg")
            val srcPkg = File(prefix, "pkg/pkg")
            if (srcPkg.exists()) srcPkg.copyTo(binPkg, overwrite = true)
        }

        val rtkBin = File(prefix, "bin/rtk")
        if (rtkBin.isFile) {
            rtkBin.setReadable(true, false)
            rtkBin.setExecutable(true, false)
            try { Os.chmod(rtkBin.absolutePath, 493) } catch (e: Exception) {}
        }

        val binDir = File(prefix, "bin").apply { mkdirs() }
        val aptWrapper = "#!/system/bin/sh\nexec \"\$(dirname \"\$0\")/pkg\" \"\$@\"\n"

        listOf("apt", "apt-get", "apt-cache", "apt-config", "apt-key", "apt-mark").forEach { wrapperName ->
            val target = File(binDir, wrapperName)
            try {
                if (target.exists()) target.delete()
                target.writeText(aptWrapper)
                target.setReadable(true, false)
                target.setExecutable(true, false)
                try { Os.chmod(target.absolutePath, 493) } catch (e: Exception) {}
            } catch (e: Exception) {}
        }

        val binLib = File(prefix, "bin/lib").apply { mkdirs() }
        val pkgLib = File(prefix, "pkg/lib").apply { mkdirs() }
        val allLibDirs = listOf(binLib, pkgLib)
        val candidate = if (pkgLib.listFiles()?.isNotEmpty() == true) pkgLib else binLib
        candidate.listFiles()?.forEach { f ->
            allLibDirs.forEach { d ->
                val dest = File(d, f.name)
                if (!dest.exists() || dest.length() == 0L) {
                    try { f.copyTo(dest, overwrite = true) } catch (e: Exception) {}
                }
                dest.setReadable(true, false)
                dest.setExecutable(true, false)
                try { Os.chmod(dest.absolutePath, 493) } catch (e: Exception) {}
            }
        }

        val etcPkg = File(prefix, "etc/pkg").apply { mkdirs() }
        val repoConf = File(etcPkg, "repo.conf")
        if (!repoConf.exists() || repoConf.length() == 0L) {
            try {
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
            } catch (e: Exception) {}
        }
    }

    private fun extractApkEntryToFile(apkZip: ZipFile, entry: ZipEntry, dest: File, buffer: ByteArray) {
        if (entry.isDirectory) {
            dest.mkdirs()
        } else {
            dest.parentFile?.mkdirs()
            if (dest.exists()) {
                dest.delete()
            } else {
                try { Os.remove(dest.absolutePath) } catch (e: Exception) {}
            }
            apkZip.getInputStream(entry).use { input ->
                FileOutputStream(dest).use { output ->
                    var len: Int
                    while (input.read(buffer).also { len = it } != -1) {
                        output.write(buffer, 0, len)
                    }
                }
            }
            if (dest.parentFile?.name == "bin" || dest.name.endsWith(".sh") || dest.name == "pkg") {
                dest.setReadable(true, false)
                dest.setExecutable(true, false)
                try { Os.chmod(dest.absolutePath, 493) } catch (e: Exception) {}
            }
        }
    }

    private fun copyAssetDirectory(context: Context, assetPath: String, targetDir: File) {
        try {
            val list = context.assets.list(assetPath) ?: return
            if (list.isEmpty()) {
                targetDir.parentFile?.mkdirs()
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(targetDir).use { output ->
                        input.copyTo(output)
                    }
                }
                if (targetDir.parentFile?.name == "bin" || targetDir.name.endsWith(".sh")) {
                    targetDir.setReadable(true, false)
                    targetDir.setExecutable(true, false)
                    try { Os.chmod(targetDir.absolutePath, 493) } catch (e: Exception) {}
                }
            } else {
                targetDir.mkdirs()
                for (item in list) {
                    val subAsset = if (assetPath.isEmpty()) item else "$assetPath/$item"
                    val subTarget = File(targetDir, item)
                    copyAssetDirectory(context, subAsset, subTarget)
                }
            }
        } catch (e: Exception) {}
    }

    private fun copyDirectory(source: File, destination: File) {
        if (!source.exists()) return
        if (source.isDirectory) {
            destination.mkdirs()
            source.listFiles()?.forEach { file ->
                copyDirectory(file, File(destination, file.name))
            }
        } else {
            source.copyTo(destination, overwrite = true)
            if (destination.parentFile?.name == "bin" || destination.name.endsWith(".sh")) {
                destination.setReadable(true, false)
                destination.setExecutable(true, false)
                try { Os.chmod(destination.absolutePath, 493) } catch (e: Exception) {}
            }
        }
    }

    fun createAgyWrapper(filesDir: File, prefix: File, engine: File) {
        val agyWrapper = File(prefix, "bin/agy")
        val ldBin = File(engine, "glibc/lib/ld-linux-aarch64.so.1")
        val libDir = File(engine, "glibc/lib")
        val agyBin = File(engine, "bin/agy")
        val prefixLib = File(prefix, "lib")
        val certFile = File(prefix, "etc/tls/cert.pem")

        val libraryPath = "${libDir.absolutePath}:${prefixLib.absolutePath}"
        val certPath = certFile.absolutePath

        val scriptContent = """
            #!/system/bin/sh
            unset LD_PRELOAD
            export PREFIX="${prefix.absolutePath}"
            export HOME="${filesDir.absolutePath}/home"
            export TMPDIR="${filesDir.absolutePath}/tmp"
            export PATH="${prefix.absolutePath}/bin:${engine.absolutePath}/bin:${'$'}PATH"
            export LD_LIBRARY_PATH="${prefixLib.absolutePath}:/system/lib64"
            export SSL_CERT_FILE="$certPath"
            export CURL_CA_BUNDLE="$certPath"
            export GIT_SSL_CAINFO="$certPath"
            export OPENSSL_CONF="${prefix.absolutePath}/etc/tls/openssl.cnf"
            export GODEBUG="netdns=go"
            export GIT_CONFIG_NOSYSTEM=1
            export GIT_ATTR_NOSYSTEM=1
            export GIT_TERMINAL_PROMPT=0
            export ANTIGRAVITY_APP_DATA_DIR="${filesDir.absolutePath}/home/.gemini/antigravity-cli"
            exec "${ldBin.absolutePath}" --library-path "$libraryPath" "${agyBin.absolutePath}" "${'$'}@"
        """.trimIndent() + "\n"

        try {
            agyWrapper.parentFile?.mkdirs()
            agyWrapper.writeText(scriptContent, Charsets.UTF_8)
            agyWrapper.setReadable(true, false)
            agyWrapper.setExecutable(true, false)
            try { Os.chmod(agyWrapper.absolutePath, 493) } catch (e: Exception) {}
            Log.i(TAG, "Created agy wrapper at ${agyWrapper.absolutePath} with system sh interpreter")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create agy wrapper: ${e.message}")
        }
    }

    fun patchGlibcResolvConf(context: Context, engineDir: File) {
        val filesDir = context.filesDir
        val resolvFile = File(filesDir, "resolv.conf")
        val resolvContent = "nameserver 8.8.8.8\nnameserver 1.1.1.1\nnameserver 8.8.4.4\n"
        val hostsFile = File(filesDir, "hosts")
        val hostsContent = "127.0.0.1 localhost\n::1 localhost ip6-localhost ip6-loopback\n"
        try {
            resolvFile.writeText(resolvContent, Charsets.UTF_8)
            resolvFile.setReadable(true, false)
            File(File(filesDir, "home"), "resolv.conf").apply {
                parentFile?.mkdirs()
                writeText(resolvContent, Charsets.UTF_8)
                setReadable(true, false)
            }
            File(File(filesDir, "usr/etc"), "resolv.conf").apply {
                parentFile?.mkdirs()
                writeText(resolvContent, Charsets.UTF_8)
                setReadable(true, false)
            }

            hostsFile.writeText(hostsContent, Charsets.UTF_8)
            hostsFile.setReadable(true, false)
            File(File(filesDir, "home"), "hosts").apply {
                parentFile?.mkdirs()
                writeText(hostsContent, Charsets.UTF_8)
                setReadable(true, false)
            }
            File(File(filesDir, "usr/etc"), "hosts").apply {
                parentFile?.mkdirs()
                writeText(hostsContent, Charsets.UTF_8)
                setReadable(true, false)
            }
        } catch (e: Exception) {}

        val agyBin = File(engineDir, "bin/agy")
        if (agyBin.isFile) {
            try {
                val restored = patchFileInPlace(
                    agyBin,
                    "/sdcard/dns.conf".toByteArray(Charsets.US_ASCII),
                    "/etc/resolv.conf".toByteArray(Charsets.US_ASCII),
                    replaceAll = true
                )
                if (restored) {
                    agyBin.setReadable(true, false)
                    agyBin.setExecutable(true, false)
                    try { Os.chmod(agyBin.absolutePath, 493) } catch (e: Exception) {}
                    Log.i(TAG, "Reverted legacy /sdcard/dns.conf in agy binary back to clean state")
                }
            } catch (e: Exception) {}
        }

        val candidateLibcFiles = listOf(
            File(engineDir, "glibc/lib/libc.so.6"),
            File(engineDir, "lib/libc.so.6"),
            File(filesDir, "runtime/lib/libc.so.6"),
            File(filesDir, "tmp/engine/glibc/lib/libc.so.6")
        ).filter { it.isFile }

        candidateLibcFiles.forEach { libcFile ->
            try {
                var modified = false

                val targetResolv = "/data/data/com.termux/files/usr/glibc/etc/resolv.conf".toByteArray(Charsets.US_ASCII)
                val replacementResolv = resolvFile.absolutePath.toByteArray(Charsets.US_ASCII)
                if (replacementResolv.size <= targetResolv.size) {
                    val repBytes = ByteArray(targetResolv.size)
                    System.arraycopy(replacementResolv, 0, repBytes, 0, replacementResolv.size)
                    if (patchFileInPlace(libcFile, targetResolv, repBytes, replaceAll = false)) {
                        modified = true
                        Log.i(TAG, "Successfully patched ${libcFile.name} resolv.conf path to ${resolvFile.absolutePath}")
                    }
                }

                val targetHosts = "/data/data/com.termux/files/usr/glibc/etc/hosts\u0000".toByteArray(Charsets.US_ASCII)
                val replacementHosts = hostsFile.absolutePath.toByteArray(Charsets.US_ASCII)
                if (replacementHosts.size + 1 <= targetHosts.size) {
                    val repBytes = ByteArray(targetHosts.size)
                    System.arraycopy(replacementHosts, 0, repBytes, 0, replacementHosts.size)
                    if (patchFileInPlace(libcFile, targetHosts, repBytes, replaceAll = false)) {
                        modified = true
                        Log.i(TAG, "Successfully patched ${libcFile.name} hosts path to ${hostsFile.absolutePath}")
                    }
                }

                if (modified) {
                    libcFile.setReadable(true, false)
                    libcFile.setExecutable(true, false)
                    try { Os.chmod(libcFile.absolutePath, 493) } catch (e: Exception) {}
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed patching ${libcFile.absolutePath}: ${e.message}")
            }
        }
    }

    private fun patchFileInPlace(file: File, target: ByteArray, replacement: ByteArray, replaceAll: Boolean): Boolean {
        if (target.isEmpty() || replacement.size != target.size) return false
        var patched = false
        RandomAccessFile(file, "rw").use { raf ->
            val length = raf.length()
            val windowSize = (1 shl 20) + target.size - 1
            val buffer = ByteArray(windowSize)
            var offset = 0L
            while (offset < length) {
                raf.seek(offset)
                val toRead = minOf(windowSize.toLong(), length - offset).toInt()
                var read = 0
                while (read < toRead) {
                    val r = raf.read(buffer, read, toRead - read)
                    if (r < 0) break
                    read += r
                }
                if (read < target.size) break
                var i = 0
                var stop = false
                while (i <= read - target.size) {
                    var matches = true
                    for (j in target.indices) {
                        if (buffer[i + j] != target[j]) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        raf.seek(offset + i)
                        raf.write(replacement)
                        patched = true
                        if (!replaceAll) {
                            stop = true
                            break
                        }
                        i += target.size
                    } else {
                        i++
                    }
                }
                if (stop || read < windowSize) break
                offset += read - (target.size - 1)
            }
        }
        return patched
    }

    fun ensurePermissions(context: Context) {
        val filesDir = context.filesDir
        val prefix = prefixDir(context)
        val engine = engineDir(context)

        createAgyWrapper(filesDir, prefix, engine)

        patchGlibcResolvConf(context, engine)

        listOf(
            File(engine, "bin/agy"),
            File(filesDir, "tmp/engine/bin/agy"),
            File(filesDir, "runtime/bin/agy")
        ).forEach { f ->
            if (f.exists() && f.isFile) {
                CoreBinaryPatcher.patchBinary(context, f)
            }
        }


        TermuxShebangRewriter.rewriteDirectory(File(prefix, "bin"), prefix.absolutePath)
        TermuxShebangRewriter.rewriteDirectory(File(prefix, "pkg"), prefix.absolutePath)

        File(prefix, "share/git-core/templates").mkdirs()
        val dirs = listOf(
            File(prefix, "bin"),
            File(prefix, "bin/lib"),
            File(prefix, "lib"),
            File(prefix, "libexec"),
            File(prefix, "libexec/git-core"),
            File(prefix, "pkg"),
            File(prefix, "pkg/lib"),
            File(engine, "bin"),
            File(engine, "glibc/lib")
        )

        val glibcLib = File(engine, "glibc/lib")
        if (glibcLib.isDirectory) {
            val libc = File(glibcLib, "libc.so")
            val libm = File(glibcLib, "libm.so")
            if (libc.exists() && libc.length() < 1000L) {
                try {
                    libc.delete()
                    Os.symlink("libc.so.6", libc.absolutePath)
                } catch (e: Exception) {}
            }
            if (libm.exists() && libm.length() < 1000L) {
                try {
                    libm.delete()
                    Os.symlink("libm.so.6", libm.absolutePath)
                } catch (e: Exception) {}
            }
        }

        for (dir in dirs) {
            if (dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    file.setReadable(true, false)
                    val name = file.name
                    if (name.endsWith(".so") || name.contains(".so.") ||
                        dir.name == "bin" || dir.name == "libexec" || dir.name == "git-core" ||
                        name.endsWith(".sh") || name == "pkg" || name == "agy"
                    ) {
                        file.setExecutable(true, false)
                        try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
                    }
                }
            }
        }

        try {
        } catch (e: Exception) {}
    }
}

object TermuxShebangRewriter {
    private const val TAG = "TermuxShebangRewriter"
    private val PREFIX_PATTERN = Regex("""/data/(?:data|user/\d+)/[^/\s"']+/files/usr""")
    private val ENV_SHEBANG_PATTERN = Regex("""^#!\s*/usr/bin/env\s+(\S+)""")

    fun isSymlink(file: File): Boolean {
        return try {
            Files.isSymbolicLink(file.toPath())
        } catch (e: Throwable) {
            try {
                val stat = Os.lstat(file.absolutePath)
                OsConstants.S_ISLNK(stat.st_mode)
            } catch (e: Throwable) {
                false
            }
        }
    }

    private fun isLikelyText(bytes: ByteArray): Boolean {
        val checkLen = minOf(bytes.size, 1024)
        for (i in 0 until checkLen) {
            if (bytes[i] == 0.toByte()) return false
        }
        return true
    }

    fun rewriteFile(file: File, newPrefix: String): Boolean {
        if (!file.isFile || !file.canRead() || file.length() > 5 * 1024 * 1024) return false
        if (isSymlink(file)) return false

        val bytes = try { file.readBytes() } catch (e: Exception) { return false }
        if (bytes.size < 2) return false

        val isShebang = bytes[0] == '#'.code.toByte() && bytes[1] == '!'.code.toByte()
        if (!isShebang && !isLikelyText(bytes)) return false

        val content = try { String(bytes, Charsets.UTF_8) } catch (e: Exception) { return false }
        var modified = content.replace(PREFIX_PATTERN, newPrefix)

        if (isShebang) {
            val lines = modified.split("\n", limit = 2)
            val firstLine = lines[0]
            val rewrittenFirstLine = firstLine.replace(ENV_SHEBANG_PATTERN, "#!$newPrefix/bin/$1")
            if (rewrittenFirstLine != firstLine) {
                modified = if (lines.size > 1) rewrittenFirstLine + "\n" + lines[1] else rewrittenFirstLine
            }
        }

        if (modified != content) {
            return try {
                file.writeText(modified, Charsets.UTF_8)
                file.setReadable(true, false)
                file.setExecutable(true, false)
                try { Os.chmod(file.absolutePath, 493) } catch (e: Exception) {}
                true
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing rewritten file ${file.name}: ${e.message}")
                false
            }
        }
        return false
    }

    fun rewriteDirectory(dir: File, newPrefix: String): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        try {
            dir.walkTopDown().forEach { file ->
                if (file.isFile && !isSymlink(file)) {
                    if (rewriteFile(file, newPrefix)) {
                        count++
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "rewriteDirectory error on $dir: ${e.message}")
        }
        return count
    }
}

