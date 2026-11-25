package id.vern.antigravity.util

import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object NetworkDownloader {
    private const val TAG = "NetworkDownloader"
    private const val CONNECT_TIMEOUT = 30000
    private const val READ_TIMEOUT = 60000
    private const val MAX_REDIRECTS = 10
    private const val BUFFER_SIZE = 65536

    fun downloadWithResume(
        url: String,
        dest: File,
        maxAttempts: Int = 3,
        userAgent: String = "Antigravity-Android/2.0",
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): Boolean {
        dest.parentFile?.mkdirs()
        var attempt = 0
        while (attempt < maxAttempts) {
            attempt++
            try {
                var currentUrl = url
                var redirectCount = 0
                var conn: HttpURLConnection? = null
                val existingSize = if (dest.exists()) dest.length() else 0L

                while (redirectCount < MAX_REDIRECTS) {
                    val targetUrl = URL(currentUrl)
                    conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                        connectTimeout = CONNECT_TIMEOUT
                        readTimeout = READ_TIMEOUT
                        requestMethod = "GET"
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", userAgent)
                        if (existingSize > 0L) {
                            setRequestProperty("Range", "bytes=$existingSize-")
                        }
                    }

                    val code = conn.responseCode
                    if (code in 300..399) {
                        val location = conn.getHeaderField("Location")
                        conn.disconnect()
                        if (!location.isNullOrBlank()) {
                            currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                                location
                            } else {
                                URL(targetUrl, location).toString()
                            }
                            redirectCount++
                            continue
                        }
                    }
                    break
                }

                val finalConn = conn ?: throw IllegalStateException("Failed connecting to $url")
                val responseCode = finalConn.responseCode
                val appendMode: Boolean
                val totalLength: Long

                when (responseCode) {
                    HttpURLConnection.HTTP_PARTIAL -> {
                        appendMode = true
                        val contentRange = finalConn.getHeaderField("Content-Range")
                        val fullSizeFromHeader = contentRange?.substringAfterLast('/')?.toLongOrNull()
                        val chunkLength = finalConn.contentLengthLong
                        totalLength = fullSizeFromHeader ?: if (chunkLength > 0) existingSize + chunkLength else -1L
                        Log.i(TAG, "Resuming download from byte $existingSize (total: $totalLength)")
                    }
                    HttpURLConnection.HTTP_OK -> {
                        appendMode = false
                        totalLength = finalConn.contentLengthLong
                        Log.i(TAG, "Starting fresh download (total: $totalLength)")
                    }
                    416 -> {
                        finalConn.disconnect()
                        if (dest.length() > 0) {
                            onProgress?.invoke(dest.length(), dest.length())
                            return true
                        }
                        dest.delete()
                        continue
                    }
                    else -> {
                        finalConn.disconnect()
                        throw IOException("HTTP request failed with status code $responseCode")
                    }
                }

                finalConn.inputStream.use { input ->
                    FileOutputStream(dest, appendMode).use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var read: Int
                        var totalRead = if (appendMode) existingSize else 0L
                        var lastReport = 0L

                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                            totalRead += read
                            val now = System.currentTimeMillis()
                            if (now - lastReport > 200) {
                                lastReport = now
                                onProgress?.invoke(totalRead, totalLength)
                            }
                        }
                        out.flush()
                        onProgress?.invoke(totalRead, totalLength)
                    }
                }
                finalConn.disconnect()
                return true
            } catch (e: Exception) {
                Log.e(TAG, "Download attempt $attempt failed for $url: ${e.message}")
                if (attempt >= maxAttempts) throw e
                try {
                    Thread.sleep(1000L * attempt)
                } catch (_: InterruptedException) {}
            }
        }
        return false
    }

    fun httpGetString(
        url: String,
        headers: Map<String, String> = emptyMap(),
        userAgent: String = "Antigravity-Android/2.0",
        connectTimeout: Int = CONNECT_TIMEOUT,
        readTimeout: Int = READ_TIMEOUT
    ): Pair<Int, String?> {
        return try {
            var currentUrl = url
            var redirectCount = 0
            var conn: HttpURLConnection? = null

            while (redirectCount < MAX_REDIRECTS) {
                val targetUrl = URL(currentUrl)
                conn = (targetUrl.openConnection() as HttpURLConnection).apply {
                    this.connectTimeout = connectTimeout
                    this.readTimeout = readTimeout
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", userAgent)
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                }

                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location
                        } else {
                            URL(targetUrl, location).toString()
                        }
                        redirectCount++
                        continue
                    }
                }
                break
            }

            val finalConn = conn ?: return Pair(-1, null)
            val code = finalConn.responseCode
            val body = if (code in 200..299) {
                finalConn.inputStream.bufferedReader().use { it.readText() }
            } else {
                finalConn.errorStream?.bufferedReader()?.use { it.readText() }
            }
            finalConn.disconnect()
            Pair(code, body)
        } catch (e: Exception) {
            Log.e(TAG, "httpGetString failed for $url: ${e.message}")
            Pair(-1, null)
        }
    }

    fun cleanPath(path: String): String {
        return path.trim().removeSurrounding("\"").removeSurrounding("'")
    }
}
