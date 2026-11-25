package id.vern.antigravity.install

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile

object CoreBinaryPatcher {
    private const val TAG = "CoreBinaryPatcher"

    private val FACCESSAT2_TARGET = byteArrayOf(0xe0.toByte(), 0x36.toByte(), 0x80.toByte(), 0xd2.toByte())
    private val FACCESSAT2_REPL = byteArrayOf(0x00.toByte(), 0x06.toByte(), 0x80.toByte(), 0xd2.toByte())

    private val FCHMODAT2_TARGET = byteArrayOf(0x80.toByte(), 0x38.toByte(), 0x80.toByte(), 0xd2.toByte())
    private val FCHMODAT2_REPL = byteArrayOf(0xa0.toByte(), 0x06.toByte(), 0x80.toByte(), 0xd2.toByte())

    private val PTY_SHELL_TARGET = "/bin/bash".toByteArray(Charsets.US_ASCII)
    private val PTY_SHELL_REPL = "/bin///sh".toByteArray(Charsets.US_ASCII)

    private val RESOLV_TARGET = "/etc/resolv.conf".toByteArray(Charsets.US_ASCII)
    private val RESOLV_REPL = "etc//resolv.conf".toByteArray(Charsets.US_ASCII)

    fun isAlreadyPatched(binaryFile: File): Boolean {
        val marker = File(binaryFile.parentFile, ".${binaryFile.name}.patched")
        if (!marker.exists() || !marker.isFile) return false
        val content = try { marker.readText().trim() } catch (e: Exception) { return false }
        val expected = "${binaryFile.length()}_${binaryFile.lastModified()}"
        return content == expected
    }

    private fun markAsPatched(binaryFile: File) {
        try {
            val marker = File(binaryFile.parentFile, ".${binaryFile.name}.patched")
            val content = "${binaryFile.length()}_${binaryFile.lastModified()}"
            marker.writeText(content)
        } catch (e: Exception) {}
    }

    fun patchBinary(context: Context, binaryFile: File): Boolean {
        if (!binaryFile.exists() || !binaryFile.isFile || binaryFile.length() < 1_000_000L) {
            return false
        }

        if (isAlreadyPatched(binaryFile)) {
            return true
        }

        var success = false

        val prefix = BootstrapInstaller.prefixDir(context)
        val candidatePython = listOf(
            File(prefix, "bin/python"),
            File(prefix, "bin/python3"),
            File(prefix, "bin/python3.14")
        ).firstOrNull { it.canExecute() }

        val candidateScript = listOf(
            File(prefix, "patches/pipeline.py"),
            File(prefix, "overlay/patches/pipeline.py")
        ).firstOrNull { it.exists() && it.isFile }

        if (candidatePython != null && candidateScript != null) {
            try {
                val pb = ProcessBuilder(candidatePython.absolutePath, candidateScript.absolutePath, "--allow-missing", binaryFile.absolutePath)
                pb.environment().remove("LD_PRELOAD")
                val proc = pb.start()
                val code = proc.waitFor()
                if (code == 0) {
                    Log.i(TAG, "Successfully patched ${binaryFile.name} via Python patch pipeline")
                    markAsPatched(binaryFile)
                    return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Python patch execution failed, falling back to direct patching: ${e.message}")
            }
        }

        try {
            var patchedAny = false
            if (patchInPlace(binaryFile, FACCESSAT2_TARGET, FACCESSAT2_REPL)) {
                patchedAny = true
                Log.i(TAG, "Patched faccessat2 -> faccessat in ${binaryFile.name}")
            }
            if (patchInPlace(binaryFile, FCHMODAT2_TARGET, FCHMODAT2_REPL)) {
                patchedAny = true
                Log.i(TAG, "Patched fchmodat2 -> fchmodat in ${binaryFile.name}")
            }
            if (patchInPlace(binaryFile, PTY_SHELL_TARGET, PTY_SHELL_REPL)) {
                patchedAny = true
                Log.i(TAG, "Patched pty shell /bin/bash -> /bin///sh in ${binaryFile.name}")
            }
            if (patchInPlace(binaryFile, RESOLV_TARGET, RESOLV_REPL)) {
                patchedAny = true
                Log.i(TAG, "Patched resolv.conf in ${binaryFile.name}")
            }
            success = patchedAny
            if (success) {
                markAsPatched(binaryFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Direct binary patching failed for ${binaryFile.name}: ${e.message}", e)
        }

        return success
    }

    private fun patchInPlace(file: File, target: ByteArray, replacement: ByteArray): Boolean {
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
                        i += target.size
                    } else {
                        i++
                    }
                }
                if (read < windowSize) break
                offset += read - (target.size - 1)
            }
        }
        return patched
    }
}

