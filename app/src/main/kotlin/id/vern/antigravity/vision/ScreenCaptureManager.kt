package id.vern.antigravity.vision

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Base64
import id.vern.antigravity.util.AppLogger
import java.io.ByteArrayOutputStream

/**
 * ScreenCaptureManager - long lived screen capture session.
 *
 * IMPORTANT: the projection is opened ONCE per granted consent and kept alive. The
 * VirtualDisplay + ImageReader are created a single time and the newest frame is cached,
 * so taking a screenshot never needs another permission dialog.
 *
 * (The previous implementation created and released a VirtualDisplay per capture, which on
 * Android 14+ forced the "Share your screen" dialog on every single action.)
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCapture"

    /** Captured frames are downscaled to this width before being sent to the model. */
    private const val MAX_WIDTH = 720
    private const val JPEG_QUALITY = 70

    /** Do not convert frames faster than this; the reader is still drained every frame. */
    private const val FRAME_MIN_INTERVAL_MS = 250L

    @Volatile
    private var projection: MediaProjection? = null

    @Volatile
    private var virtualDisplay: VirtualDisplay? = null

    @Volatile
    private var imageReader: ImageReader? = null

    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    @Volatile
    private var lastFrame: Bitmap? = null

    @Volatile
    private var lastFrameAt = 0L

    private val frameLock = Any()

    /** True while a granted projection with a live virtual display exists. */
    fun isReady(): Boolean = virtualDisplay != null && imageReader != null

    /**
     * Opens the persistent capture session. Call once, right after the user accepts the
     * system "Share your screen" dialog.
     *
     * Choose "Entire screen" in that dialog: "Share one app" limits the agent to that app.
     */
    fun startSession(context: Context, resultCode: Int, data: Intent): Boolean {
        if (isReady()) {
            AppLogger.d(TAG, "Capture session already running")
            return true
        }
        return try {
            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val granted = manager.getMediaProjection(resultCode, data)
            if (granted == null) {
                AppLogger.e(TAG, "getMediaProjection returned null")
                return false
            }

            val metrics = context.resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            val worker = HandlerThread("agy-screen-capture").apply { start() }
            thread = worker
            val h = Handler(worker.looper)
            handler = h

            // Android 14+ requires a callback to be registered before the first capture.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                granted.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        AppLogger.w(TAG, "MediaProjection stopped by the system")
                        releaseInternal()
                    }
                }, h)
            }

            val reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader.setOnImageAvailableListener({ r -> onFrame(r, width, height) }, h)

            val display = granted.createVirtualDisplay(
                "agy-screen",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface,
                null,
                h
            )

            projection = granted
            imageReader = reader
            virtualDisplay = display
            lastFrameAt = 0L
            AppLogger.i(TAG, "Persistent capture session started (${width}x$height)")
            true
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to start capture session: ${e.message}")
            releaseInternal()
            false
        }
    }

    /** Kept for callers that still use the old name. */
    fun setProjection(context: Context, resultCode: Int, data: Intent): Boolean =
        startSession(context, resultCode, data)

    fun stopSession() {
        releaseInternal()
    }

    private fun releaseInternal() {
        try { virtualDisplay?.release() } catch (e: Exception) {}
        virtualDisplay = null
        try { imageReader?.close() } catch (e: Exception) {}
        imageReader = null
        try { projection?.stop() } catch (e: Exception) {}
        projection = null
        thread?.quitSafely()
        thread = null
        handler = null
        synchronized(frameLock) {
            lastFrame?.recycle()
            lastFrame = null
        }
        lastFrameAt = 0L
    }

    /**
     * Always acquire + close every available image so the producer never stalls, but only
     * convert one frame per [FRAME_MIN_INTERVAL_MS] to keep the CPU cool.
     */
    private fun onFrame(reader: ImageReader, width: Int, height: Int) {
        val image = try {
            reader.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return

        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastFrameAt < FRAME_MIN_INTERVAL_MS) return

            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width

            val raw = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            raw.copyPixelsFromBuffer(plane.buffer)
            val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
            if (cropped !== raw) raw.recycle()

            val scaled = if (cropped.width > MAX_WIDTH) {
                val h = (cropped.height.toFloat() * MAX_WIDTH / cropped.width).toInt()
                val s = Bitmap.createScaledBitmap(cropped, MAX_WIDTH, h, true)
                if (s !== cropped) cropped.recycle()
                s
            } else {
                cropped
            }

            synchronized(frameLock) {
                val old = lastFrame
                lastFrame = scaled
                old?.recycle()
            }
            lastFrameAt = now
        } catch (e: Exception) {
            AppLogger.e(TAG, "Frame conversion failed: ${e.message}")
        } finally {
            try { image.close() } catch (e: Exception) {}
        }
    }

    /**
     * Returns the most recent frame. Waits briefly for a fresh one when the cache is stale.
     */
    fun captureBitmap(): Bitmap? {
        if (!isReady()) return null
        val deadline = SystemClock.elapsedRealtime() + 1500
        while (SystemClock.elapsedRealtime() < deadline) {
            synchronized(frameLock) {
                val frame = lastFrame
                if (frame != null && !frame.isRecycled) {
                    val age = SystemClock.elapsedRealtime() - lastFrameAt
                    if (age < 1200 || SystemClock.elapsedRealtime() > deadline - 400) {
                        return frame.copy(Bitmap.Config.ARGB_8888, false)
                    }
                }
            }
            try { Thread.sleep(80) } catch (e: InterruptedException) { break }
        }
        synchronized(frameLock) {
            val frame = lastFrame ?: return null
            return if (frame.isRecycled) null else frame.copy(Bitmap.Config.ARGB_8888, false)
        }
    }

    /** Base64 JPEG of the current screen, ready for Gemini's inline_data part. */
    fun captureBase64Jpeg(): String? {
        val bitmap = captureBitmap() ?: return null
        return try {
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            AppLogger.e(TAG, "JPEG encode failed: ${e.message}")
            null
        } finally {
            bitmap.recycle()
        }
    }

    fun decodeBase64Jpeg(base64: String): Bitmap? = try {
        val bytes = Base64.decode(base64, Base64.NO_WRAP)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}
