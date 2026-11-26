package id.vern.antigravity.install

import android.content.Context
import android.system.Os
import android.util.Log
import id.vern.antigravity.util.NetworkDownloader
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import org.json.JSONObject

data class CoreUpdateInfo(
    val version: String,
    val url: String,
    val sha512: String = "",
    val currentVersion: String = "",
    val isUpdateAvailable: Boolean = false
)

object CoreUpdateManager {
    private const val TAG = "CoreUpdateManager"
    const val MANIFEST_URL = "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_arm64.json"

    fun getCurrentVersion(context: Context): String {
        val engineDir = BootstrapInstaller.engineDir(context)
        val agyBin = File(engineDir, "bin/agy")
        val ldBin = File(engineDir, "glibc/lib/ld-linux-aarch64.so.1")
        val libDir = File(engineDir, "glibc/lib")
        val prefixLib = File(BootstrapInstaller.prefixDir(context), "lib")

        val targetBin = when {
            agyBin.isFile -> agyBin
            File(context.filesDir, "tmp/engine/bin/agy").isFile -> File(context.filesDir, "tmp/engine/bin/agy")
            else -> null
        }

        if (targetBin != null && ldBin.isFile) {
            try {
                val libPaths = listOf(libDir.absolutePath, prefixLib.absolutePath)
                    .filter { File(it).exists() }
                    .joinToString(":")
                val pb = ProcessBuilder(ldBin.absolutePath, "--library-path", libPaths, targetBin.absolutePath, "--version")
                pb.environment().remove("LD_PRELOAD")
                val proc = pb.start()
                val out = proc.inputStream.bufferedReader().readText().trim()
                proc.waitFor()
                if (proc.exitValue() == 0 && out.isNotEmpty()) {
                    return out
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed running agy --version: ${e.message}")
            }
        }

        val verFile = File(engineDir, ".engine_version")
        if (verFile.isFile) {
            val v = verFile.readText().trim()
            if (v.isNotEmpty()) return v
        }

        return "1.5.3"
    }

    fun checkForUpdate(context: Context): CoreUpdateInfo {
        val currentVer = getCurrentVersion(context)
        val (code, jsonStr) = NetworkDownloader.httpGetString(MANIFEST_URL, connectTimeout = 10000, readTimeout = 15000)
        if (code !in 200..299 || jsonStr.isNullOrBlank()) {
            return CoreUpdateInfo(currentVer, "", "", currentVer, false)
        }
        val json = JSONObject(jsonStr)
        val remoteVer = json.getString("version")
        val downloadUrl = json.getString("url")
        val sha512 = json.optString("sha512", "")

        val isAvailable = isVersionNewer(remoteVer, currentVer)
        return CoreUpdateInfo(
            version = remoteVer,
            url = downloadUrl,
            sha512 = sha512,
            currentVersion = currentVer,
            isUpdateAvailable = isAvailable
        )
    }

    fun isVersionNewer(remote: String, current: String): Boolean {
        if (remote == current) return false
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val cParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val c = cParts.getOrElse(i) { 0 }
            if (r > c) return true
            if (r < c) return false
        }
        return false
    }

    fun downloadAndApply(
        context: Context,
        updateInfo: CoreUpdateInfo,
        onProgress: (Float, String) -> Unit
    ): Result<String> {
        val engineDir = BootstrapInstaller.engineDir(context)
        val agyBin = File(engineDir, "bin/agy")
        val ldBin = File(engineDir, "glibc/lib/ld-linux-aarch64.so.1")
        val libDir = File(engineDir, "glibc/lib")
        val prefixLib = File(BootstrapInstaller.prefixDir(context), "lib")
        val cacheDir = context.cacheDir

        val tarGzFile = File(cacheDir, "core_update_arm64.tar.gz")
        val stagingFile = File(cacheDir, "agy.staging")
        val backupFile = File(engineDir, "bin/agy.bak")

        try {

            val maxAttempts = 5
            var attempt = 0
            var downloadSucceeded = false
            var lastDownloadError: Throwable? = null

            while (attempt < maxAttempts && !downloadSucceeded) {
                attempt++
                try {
                    onProgress(0.05f, "Connecting to update server (attempt $attempt/$maxAttempts)...")
                    onProgress(0.10f, "Downloading Core Engine (${updateInfo.version})...")
                    var lastReport = System.currentTimeMillis()
                    downloadSucceeded = NetworkDownloader.downloadWithResume(
                        url = updateInfo.url,
                        dest = tarGzFile,
                        maxAttempts = 1,
                        onProgress = { totalRead, totalBytes ->
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 250 && totalBytes > 0) {
                                lastReport = now
                                val progress = 0.10f + 0.55f * (totalRead.toFloat() / totalBytes)
                                val mbRead = totalRead / (1024 * 1024)
                                val mbTotal = totalBytes / (1024 * 1024)
                                onProgress(progress, "Downloading: $mbRead MB / $mbTotal MB")
                            }
                        }
                    )
                } catch (e: Exception) {
                    lastDownloadError = e
                    Log.w(TAG, "Core Engine download attempt $attempt/$maxAttempts failed: ${e.message}", e)
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

            if (!downloadSucceeded) {
                throw IllegalStateException("Failed downloading core engine after $maxAttempts attempts: ${lastDownloadError?.message}", lastDownloadError)
            }

            onProgress(0.70f, "Extracting binary package...")
            var extracted = extractTarGzMember(tarGzFile, "antigravity", stagingFile)
            if (!extracted) {
                val tarBin = File(BootstrapInstaller.prefixDir(context), "bin/tar")
                if (tarBin.isFile) {
                    val pb = ProcessBuilder(
                        tarBin.absolutePath,
                        "-xzf",
                        tarGzFile.absolutePath,
                        "-C",
                        cacheDir.absolutePath,
                        "antigravity"
                    )
                    pb.start().waitFor()
                    val fallbackFile = File(cacheDir, "antigravity")
                    if (fallbackFile.isFile) {
                        fallbackFile.copyTo(stagingFile, overwrite = true)
                        fallbackFile.delete()
                        extracted = true
                    }
                }
            }

            if (!extracted || !stagingFile.isFile || stagingFile.length() < 50_000_000L) {
                return Result.failure(Exception("Failed to extract valid core binary from archive"))
            }

            onProgress(0.85f, "Verifying binary integrity...")
            val header = ByteArray(4)
            FileInputStream(stagingFile).use { it.read(header) }
            if (header[0] != 0x7F.toByte() || header[1] != 'E'.code.toByte() || header[2] != 'L'.code.toByte() || header[3] != 'F'.code.toByte()) {
                return Result.failure(Exception("Downloaded file is not a valid ELF binary"))
            }

            stagingFile.setReadable(true, false)
            stagingFile.setExecutable(true, false)
            try { Os.chmod(stagingFile.absolutePath, 493) } catch (e: Exception) {}

            onProgress(0.88f, "Applying Android platform syscall patches...")
            CoreBinaryPatcher.patchBinary(context, stagingFile)

            onProgress(0.90f, "Installing new Core Engine...")
            if (agyBin.exists()) {
                if (backupFile.exists()) backupFile.delete()
                agyBin.copyTo(backupFile, overwrite = true)
                agyBin.delete()
            } else {
                agyBin.parentFile?.mkdirs()
            }
            stagingFile.copyTo(agyBin, overwrite = true)
            agyBin.setReadable(true, false)
            agyBin.setExecutable(true, false)
            try { Os.chmod(agyBin.absolutePath, 493) } catch (e: Exception) {}

            val tmpAgy = File(context.filesDir, "tmp/engine/bin/agy")
            if (tmpAgy.parentFile?.exists() == true) {
                try {
                    agyBin.copyTo(tmpAgy, overwrite = true)
                    tmpAgy.setReadable(true, false)
                    tmpAgy.setExecutable(true, false)
                    try { Os.chmod(tmpAgy.absolutePath, 493) } catch (e: Exception) {}
                } catch (e: Exception) {}
            }

            BootstrapInstaller.patchGlibcResolvConf(context, engineDir)

            onProgress(0.95f, "Running verification smoke test...")
            val libPaths = listOf(libDir.absolutePath, prefixLib.absolutePath)
                .filter { File(it).exists() }
                .joinToString(":")
            val pb = ProcessBuilder(ldBin.absolutePath, "--library-path", libPaths, agyBin.absolutePath, "--version")
            pb.environment().remove("LD_PRELOAD")
            val proc = pb.start()
            val verifyOut = proc.inputStream.bufferedReader().readText().trim()
            val exitCode = proc.waitFor()

            if (exitCode != 0) {
                Log.e(TAG, "Smoke test failed with code $exitCode ($verifyOut), rolling back...")
                if (backupFile.exists()) {
                    backupFile.copyTo(agyBin, overwrite = true)
                    agyBin.setReadable(true, false)
                    agyBin.setExecutable(true, false)
                    try { Os.chmod(agyBin.absolutePath, 493) } catch (e: Exception) {}
                }
                return Result.failure(Exception("Smoke test failed: $verifyOut"))
            }

            if (backupFile.exists()) backupFile.delete()
            if (stagingFile.exists()) stagingFile.delete()
            if (tarGzFile.exists()) tarGzFile.delete()

            File(engineDir, ".engine_version").writeText(updateInfo.version)

            onProgress(1.0f, "Core engine successfully updated to v${updateInfo.version}!")
            return Result.success(updateInfo.version)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating core engine: ${e.message}", e)
            if (backupFile.exists() && !agyBin.exists()) {
                backupFile.copyTo(agyBin, overwrite = true)
            }
            return Result.failure(e)
        }
    }

    private fun extractTarGzMember(tarGzFile: File, memberName: String, destFile: File): Boolean {
        try {
            GZIPInputStream(BufferedInputStream(FileInputStream(tarGzFile), 65536)).use { tarIn ->
                val header = ByteArray(512)
                while (true) {
                    var bytesRead = 0
                    while (bytesRead < 512) {
                        val r = tarIn.read(header, bytesRead, 512 - bytesRead)
                        if (r == -1) return false
                        bytesRead += r
                    }

                    if (header.all { it == 0.toByte() }) break

                    val nameBytes = header.copyOfRange(0, 100)
                    val zeroIdx = nameBytes.indexOf(0.toByte())
                    val entryName = String(nameBytes, 0, if (zeroIdx >= 0) zeroIdx else nameBytes.size).trim()

                    val sizeBytes = header.copyOfRange(124, 136)
                    val sizeZeroIdx = sizeBytes.indexOf(0.toByte())
                    val sizeStr = String(sizeBytes, 0, if (sizeZeroIdx >= 0) sizeZeroIdx else sizeBytes.size).trim()
                    val entrySize = sizeStr.toLongOrNull(8) ?: 0L

                    if (entryName == memberName || entryName.endsWith("/$memberName")) {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { out ->
                            var remaining = entrySize
                            val buf = ByteArray(65536)
                            while (remaining > 0) {
                                val toRead = minOf(remaining, buf.size.toLong()).toInt()
                                val r = tarIn.read(buf, 0, toRead)
                                if (r == -1) break
                                out.write(buf, 0, r)
                                remaining -= r
                            }
                        }
                        return true
                    } else {
                        val pad = ((entrySize + 511) / 512) * 512
                        var remainingToSkip = pad
                        val skipBuf = ByteArray(65536)
                        while (remainingToSkip > 0) {
                            val toRead = minOf(remainingToSkip, skipBuf.size.toLong()).toInt()
                            val r = tarIn.read(skipBuf, 0, toRead)
                            if (r == -1) break
                            remainingToSkip -= r
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "extractTarGzMember failed: ${e.message}")
        }
        return false
    }
}

