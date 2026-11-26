package id.vern.antigravity.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import id.vern.antigravity.util.AppLogger
import java.io.ByteArrayOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * ScreenCaptureManager - grabs a single screenshot of the whole display via MediaProjection.
 *
 * The projection has to be granted once from an Activity (`MediaProjectionManager.createScreenCaptureIntent()`);
 * after that this manager can grab frames on demand for the agent loop.
 *
 * Frames are downscaled and JPEG-compressed so a screenshot costs a few tens of KB
 * instead of several MB when it is sent to Gemini.
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCapture"

    /** Max width of the captured frame. Gemini works fine with ~720px for UI understanding. */
    private const val MAX_WIDTH = 720
    private const val JPEG_QUALITY = 70

    @Volatile
    private var projection: MediaProjection? = null

    @Volatile
    private var captureThread: HandlerThread? = null

    @Volatile
    private var captureHandler: Handler? = null

    /** True when a projection has been granted and is usable. */
    fun isReady(): Boolean = projection != null

    /**
     * Stores the granted projection. Call from the Activity's result callback with the resultCode/data
     * returned by the capture intent.
     */
    fun setProjection(context: Context, resultCode: Int, data: android.content.Intent): Boolean {
        return try {
            release()
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val p = manager.getMediaProjection(resultCode, data) ?: return false
            // Android 14+ requires a callback to be registered before the first capture.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                p.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        AppLogger.w(TAG, "MediaProjection stopped by the system")
                        projection = null
                    }
                }, Handler(android.os.Looper.getMainLooper()))
            }
            val thread = HandlerThread("agy-screen-capture").apply { start() }
            captureThread = thread
            captureHandler = Handler(thread.looper)
            projection = p
            AppLogger.i(TAG, "MediaProjection granted")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to register MediaProjection: ${e.message}")
            release()
            false
        }
    }

    fun release() {
        try {
            projection?.stop()
        } catch (e: Exception) {
        }
        projection = null
        captureHandler = null
        captureThread?.quitSafely()
        captureThread = null
    }

    /**
     * Captures one frame and returns it as a downscaled bitmap, or null if no projection
     * has been granted or the frame could not be read.
     *
     * Blocking: call from a background dispatcher.
     */
    fun captureBitmap(context: Context): Bitmap? {
        val p = projection ?: return null
        val handler = captureHandler ?: return null

        val metrics = context.resources.displayMetrics
        val fullWidth = metrics.widthPixels
        val fullHeight = metrics.heightPixels
        val density = metrics.densityDpi

        val reader = try {
            ImageReader.newInstance(fullWidth, fullHeight, android.graphics.PixelFormat.RGBA_8888, 2)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ImageReader failed: ${e.message}")
            return null
        }

        var virtualDisplay: VirtualDisplay? = null
        try {
            val latch = CountDownLatch(1)
            var bitmap: Bitmap? = null

            reader.setOnImageAvailableListener({ r ->
                val image = try {
                    r.acquireLatestImage()
                } catch (e: Exception) {
                    null
                } ?: return@setOnImageAvailableListener
                try {
                    val plane = image.planes[0]
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * fullWidth

                    val raw = Bitmap.createBitmap(
                        fullWidth + rowPadding / pixelStride,
                        fullHeight,
                        Bitmap.Config.ARGB_8888
                    )
                    raw.copyPixelsFromBuffer(plane.buffer)
                    val cropped = Bitmap.createBitmap(raw, 0, 0, fullWidth, fullHeight)
                    if (cropped !== raw) raw.recycle()

                    bitmap = if (cropped.width > MAX_WIDTH) {
                        val h = (cropped.height.toFloat() * MAX_WIDTH / cropped.width).toInt()
                        val scaled = Bitmap.createScaledBitmap(cropped, MAX_WIDTH, h, true)
                        if (scaled !== cropped) cropped.recycle()
                        scaled
                    } else {
                        cropped
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Frame decode failed: ${e.message}")
                } finally {
                    try {
                        image.close()
                    } catch (e: Exception) {
                    }
                    latch.countDown()
                }
            }, handler)

            virtualDisplay = p.createVirtualDisplay(
                "agy-screen",
                fullWidth,
                fullHeight,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                handler
            )

            if (!latch.await(2500, TimeUnit.MILLISECONDS)) {
                AppLogger.w(TAG, "Timed out waiting for a frame")
            }
            return bitmap
        } catch (e: Exception) {
            AppLogger.e(TAG, "Capture failed: ${e.message}")
            return null
        } finally {
            try {
                virtualDisplay?.release()
            } catch (e: Exception) {
            }
            try {
                reader.close()
            } catch (e: Exception) {
            }
        }
    }

    /** Captures the screen and returns base64 JPEG suitable for the Gemini inline_data part. */
    fun captureBase64Jpeg(context: Context): String? {
        val bitmap = captureBitmap(context) ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            android.util.Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.e(TAG, "JPEG encode failed: ${e.message}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    /** Decodes a base64 JPEG back into a bitmap (used to show the captured frame in the chat). */
    fun decodeBase64Jpeg(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
