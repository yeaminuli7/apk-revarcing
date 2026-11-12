package com.example.util

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AudioVideoMerger: High-fidelity audio extraction, resampling, mixing, and muxing.
 *
 * Implements the equivalent of:
 * ffmpeg -i "%full_video%" -i "%full_audio%" -filter_complex "[0:a]volume=v_vol[a1]; [1:a]volume=t_vol[a2]; [a1][a2]amix=inputs=2:duration=longest" -c:v copy -c:a aac "%final_output%"
 *
 * Highlights:
 * 1. Lossless video stream passthrough (-c:v copy).
 * 2. Proper handling of MediaCodec INFO_OUTPUT_FORMAT_CHANGED and 5.1/7.1 multichannel downmixing to stereo.
 * 3. Continuous-phase TTS resampling to prevent 100ms chunk-boundary clicks/aliasing.
 * 4. 100% linear mixing without non-linear tanh squashing, preserving subtle background sounds (birds chirping, ambience, room acoustics) with crystal clarity.
 * 5. Transparent high-headroom peak limiting only above 30000 to prevent digital clipping.
 * 6. Full "duration=longest" support.
 */
object AudioVideoMerger {

    private const val TAG = "BalaSpeakMerger"

    /**
     * Decodes the audio track of a video file directly to a 16-bit signed stereo (2-channel) raw PCM file.
     * Accurately handles INFO_OUTPUT_FORMAT_CHANGED and downmixes multi-channel audio (mono, stereo, 5.1, 7.1)
     * to high-fidelity stereo.
     * Returns Pair(sampleRate, pcmFile) or null if no audio track exists.
     */
    fun decodeAudioFromVideo(
        videoFile: File,
        tempPcmFile: File,
        preferHardwareDecoder: Boolean = true,
        ioBufferBytes: Int = 64 * 1024
    ): Pair<Int, File>? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var fos: BufferedOutputStream? = null

        try {
            extractor.setDataSource(videoFile.absolutePath)
            var audioTrackIndex = -1
            var initialFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val fmt = extractor.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    initialFormat = fmt
                    break
                }
            }

            if (audioTrackIndex == -1 || initialFormat == null) {
                Log.w(TAG, "No audio track discovered in video")
                return null
            }

            var actualSampleRate = if (initialFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                initialFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var actualChannels = if (initialFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                initialFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            extractor.selectTrack(audioTrackIndex)
            val mime = initialFormat.getString(MediaFormat.KEY_MIME) ?: ""
            decoder = createCodec(mime, encoder = false, preferHardware = preferHardwareDecoder)
            decoder.configure(initialFormat, null, null, 0)
            decoder.start()

            fos = BufferedOutputStream(FileOutputStream(tempPcmFile), safeIoBuffer(ioBufferBytes))
            val info = MediaCodec.BufferInfo()
            var isExtractorEOS = false
            var isDecoderEOS = false
            val timeoutUs = 5000L

            while (!isDecoderEOS) {
                if (!isExtractorEOS) {
                    val inBufferId = decoder.dequeueInputBuffer(timeoutUs)
                    if (inBufferId >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inBufferId)
                        if (inputBuffer != null) {
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isExtractorEOS = true
                            } else {
                                decoder.queueInputBuffer(inBufferId, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                }

                val outBufferId = decoder.dequeueOutputBuffer(info, timeoutUs)
                if (outBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val newFormat = decoder.outputFormat
                    if (newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        actualSampleRate = newFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        actualChannels = newFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    Log.d(TAG, "Video audio decoder format updated: sampleRate=$actualSampleRate, channels=$actualChannels")
                } else if (outBufferId >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outBufferId)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)

                        val shortBuffer = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val numShorts = shortBuffer.remaining()
                        val decodedShorts = ShortArray(numShorts)
                        shortBuffer.get(decodedShorts)

                        writeDownmixedStereo(decodedShorts, actualChannels, fos)
                    }
                    decoder.releaseOutputBuffer(outBufferId, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderEOS = true
                    }
                }
            }

            fos.flush()
            val finalRate = if (actualSampleRate in 8000..96000) actualSampleRate else 44100
            return Pair(finalRate, tempPcmFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed decoding audio from video: ${e.message}", e)
            return null
        } finally {
            try { extractor.release() } catch (e: Exception) {}
            try {
                decoder?.stop()
                decoder?.release()
            } catch (e: Exception) {}
            try { fos?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Converts decoded audio frames into standard 16-bit stereo (2-channel) PCM bytes and writes to stream.
     * Supports Mono (1), Stereo (2), 5.1 Surround (6), and general multi-channel with ITU-R BS.775 downmixing.
     */
    private fun writeDownmixedStereo(
        shorts: ShortArray,
        channelCount: Int,
        out: BufferedOutputStream
    ) {
        if (shorts.isEmpty() || channelCount <= 0) return

        when (channelCount) {
            1 -> {
                // Mono -> duplicate to Stereo
                val byteBuffer = ByteBuffer.allocate(shorts.size * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (s in shorts) {
                    byteBuffer.putShort(s)
                    byteBuffer.putShort(s)
                }
                out.write(byteBuffer.array(), 0, byteBuffer.position())
            }
            2 -> {
                // Already Stereo
                val byteBuffer = ByteBuffer.allocate(shorts.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                for (s in shorts) {
                    byteBuffer.putShort(s)
                }
                out.write(byteBuffer.array(), 0, byteBuffer.position())
            }
            6 -> {
                // 5.1 Surround Sound: FL, FR, FC, LFE, BL, BR
                val frames = shorts.size / 6
                val byteBuffer = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (f in 0 until frames) {
                    val base = f * 6
                    val fl = shorts[base].toFloat()
                    val fr = shorts[base + 1].toFloat()
                    val fc = shorts[base + 2].toFloat()
                    val lfe = shorts[base + 3].toFloat()
                    val bl = shorts[base + 4].toFloat()
                    val br = shorts[base + 5].toFloat()

                    // ITU-R BS.775 standard matrix downmix
                    val left = ((fl + 0.707f * fc + 0.707f * bl + 0.5f * lfe) / 1.707f).toInt().coerceIn(-32768, 32767).toShort()
                    val right = ((fr + 0.707f * fc + 0.707f * br + 0.5f * lfe) / 1.707f).toInt().coerceIn(-32768, 32767).toShort()

                    byteBuffer.putShort(left)
                    byteBuffer.putShort(right)
                }
                out.write(byteBuffer.array(), 0, byteBuffer.position())
            }
            else -> {
                // Generic N-channel downmix to stereo
                val frames = shorts.size / channelCount
                val byteBuffer = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
                val halfCount = (channelCount / 2).coerceAtLeast(1)
                for (f in 0 until frames) {
                    val base = f * channelCount
                    var leftSum = 0f
                    var rightSum = 0f
                    for (c in 0 until channelCount) {
                        val sample = shorts[base + c].toFloat()
                        if (c % 2 == 0) leftSum += sample else rightSum += sample
                    }
                    val left = (leftSum / halfCount).toInt().coerceIn(-32768, 32767).toShort()
                    val right = (rightSum / halfCount).toInt().coerceIn(-32768, 32767).toShort()

                    byteBuffer.putShort(left)
                    byteBuffer.putShort(right)
                }
                out.write(byteBuffer.array(), 0, byteBuffer.position())
            }
        }
    }

    /**
     * Converts and resamples a TTS WAV file into a 16-bit Stereo (2-channel) PCM file
     * at targetSampleRate with continuous-phase interpolation (no 100ms chunk boundary clicks/aliasing).
     */
    private fun convertTtsWavToStereoPcm(
        ttsWavFile: File,
        targetPcmFile: File,
        targetSampleRate: Int
    ): Boolean {
        var raf: RandomAccessFile? = null
        var fos: BufferedOutputStream? = null

        try {
            if (!ttsWavFile.exists() || ttsWavFile.length() < 44) {
                return false
            }

            raf = RandomAccessFile(ttsWavFile, "r")
            val header = ByteArray(12)
            raf.readFully(header)
            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") {
                Log.w(TAG, "TTS file is not a valid RIFF WAVE")
                return false
            }

            // Locate 'fmt ' chunk and 'data' chunk
            var ttsChannels = 1
            var ttsSampleRate = 22050
            var dataOffset = 44L
            var dataLength = ttsWavFile.length() - 44L

            while (raf.filePointer < raf.length() - 8) {
                val chunkIdBytes = ByteArray(4)
                raf.readFully(chunkIdBytes)
                val chunkId = String(chunkIdBytes)
                val chunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL

                if (chunkId == "fmt ") {
                    val fmtBytes = ByteArray(minOf(chunkSize.toInt(), 16))
                    raf.readFully(fmtBytes)
                    val buffer = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                    val formatTag = buffer.short.toInt()
                    ttsChannels = buffer.short.toInt().coerceIn(1, 2)
                    ttsSampleRate = buffer.int.coerceIn(8000, 96000)
                    val remainingFmt = chunkSize - fmtBytes.size
                    if (remainingFmt > 0) raf.skipBytes(remainingFmt.toInt())
                } else if (chunkId == "data") {
                    dataOffset = raf.filePointer
                    dataLength = chunkSize
                    break
                } else {
                    raf.skipBytes(chunkSize.toInt())
                }
            }

            raf.seek(dataOffset)
            val totalShorts = (dataLength / 2).toInt()
            if (totalShorts <= 0) return false

            fos = BufferedOutputStream(FileOutputStream(targetPcmFile), 64 * 1024)

            // Stream TTS WAV in chunks without allocating large full-file byte arrays
            val ratio = ttsSampleRate.toDouble() / targetSampleRate.toDouble()
            val ttsFrames = totalShorts / ttsChannels
            val outFrames = (ttsFrames / ratio).toInt()

            val chunkShortCapacity = 32768
            val inShortChunk = ShortArray(chunkShortCapacity)
            val inByteChunk = ByteArray(chunkShortCapacity * 2)
            val outByteBuffer = ByteBuffer.allocate(65536).order(ByteOrder.LITTLE_ENDIAN)

            var chunkStartShortIdx = 0
            var currentChunkLoadedShorts = 0

            fun ensureShortLoaded(shortIdx: Int): Short {
                if (shortIdx < 0 || shortIdx >= totalShorts) return 0
                if (shortIdx < chunkStartShortIdx || shortIdx >= chunkStartShortIdx + currentChunkLoadedShorts) {
                    chunkStartShortIdx = shortIdx
                    val shortsRemaining = totalShorts - chunkStartShortIdx
                    val toLoadShorts = minOf(chunkShortCapacity, shortsRemaining)
                    val bytesToRead = toLoadShorts * 2
                    try {
                        raf.seek(dataOffset + (chunkStartShortIdx * 2L))
                        raf.readFully(inByteChunk, 0, bytesToRead)
                        ByteBuffer.wrap(inByteChunk, 0, bytesToRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(inShortChunk, 0, toLoadShorts)
                        currentChunkLoadedShorts = toLoadShorts
                    } catch (e: Exception) {
                        return 0
                    }
                }
                val localIdx = shortIdx - chunkStartShortIdx
                return if (localIdx in 0 until currentChunkLoadedShorts) inShortChunk[localIdx] else 0
            }

            for (i in 0 until outFrames) {
                val srcPos = i * ratio
                val srcIdx = srcPos.toInt()
                val frac = (srcPos - srcIdx).toFloat()

                val (lSample, rSample) = if (ttsChannels == 1) {
                    val s0 = ensureShortLoaded(srcIdx).toFloat()
                    val s1 = ensureShortLoaded(srcIdx + 1).toFloat()
                    val interpolated = (s0 + frac * (s1 - s0)).toInt().coerceIn(-32768, 32767).toShort()
                    Pair(interpolated, interpolated)
                } else {
                    val idx0 = srcIdx * 2
                    val idx1 = (srcIdx + 1) * 2

                    val l0 = ensureShortLoaded(idx0).toFloat()
                    val l1 = ensureShortLoaded(idx1).toFloat()
                    val left = (l0 + frac * (l1 - l0)).toInt().coerceIn(-32768, 32767).toShort()

                    val r0 = ensureShortLoaded(idx0 + 1).toFloat()
                    val r1 = ensureShortLoaded(idx1 + 1).toFloat()
                    val right = (r0 + frac * (r1 - r0)).toInt().coerceIn(-32768, 32767).toShort()

                    Pair(left, right)
                }

                if (outByteBuffer.remaining() < 4) {
                    fos.write(outByteBuffer.array(), 0, outByteBuffer.position())
                    outByteBuffer.clear()
                }
                outByteBuffer.putShort(lSample)
                outByteBuffer.putShort(rSample)
            }

            if (outByteBuffer.position() > 0) {
                fos.write(outByteBuffer.array(), 0, outByteBuffer.position())
            }

            fos.flush()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error converting TTS WAV to stereo PCM: ${e.message}", e)
            return false
        } finally {
            try { raf?.close() } catch (e: Exception) {}
            try { fos?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Dubs the video with synthesized TTS voice audio.
     *
     * Equivalent to FFmpeg:
     * -filter_complex "[0:a]volume=videoAudioVolume[a1]; [1:a]volume=ttsAudioVolume[a2]; [a1][a2]amix=inputs=2:duration=longest"
     * -c:v copy -c:a aac
     *
     * If videoAudioVolume <= 0.001f: directly remuxes TTS audio into the video container (Turbo mode).
     * If videoAudioVolume > 0.001f: decodes video audio to stereo, resamples TTS audio to the exact same rate,
     * performs crystal-clear 100% linear mixing, and muxes with original video frames.
     */
    fun mixAndEncodeDubbedMedia(
        videoFile: File,
        ttsWavFile: File,
        targetOutputFile: File,
        videoAudioVolume: Float = 0.20f, // 0.0f to 1.0f
        ttsAudioVolume: Float = 1.0f,   // 0.0f to 2.0f
        preferHardwareEncoder: Boolean = true,
        ioBufferBytes: Int = 64 * 1024,
        onProgress: (String) -> Unit
    ): Boolean {
        val ioBuffer = safeIoBuffer(ioBufferBytes)
        val rootDir = targetOutputFile.parentFile ?: videoFile.parentFile
        val videoPcmFile = File(rootDir, "temp_vid_audio_${System.currentTimeMillis()}.pcm")
        val ttsPcmFile = File(rootDir, "temp_tts_audio_${System.currentTimeMillis()}.pcm")
        val mixedPcmFile = File(rootDir, "temp_mix_audio_${System.currentTimeMillis()}.pcm")

        var videoIn: BufferedInputStream? = null
        var ttsIn: BufferedInputStream? = null
        var mixedOut: BufferedOutputStream? = null

        try {
            if (targetOutputFile.exists()) {
                targetOutputFile.delete()
            }

            // One step, same as the Windows command: copy the video and mix the
            // created audio in a single pass. No extract-to-file, then sync, then add.
            onProgress("Adding audio to video...")
            if (tryOneStepMix(
                    videoFile = videoFile,
                    audioFile = ttsWavFile,
                    outputFile = targetOutputFile,
                    videoAudioVolume = videoAudioVolume,
                    createdAudioVolume = ttsAudioVolume,
                    preferHardwareEncoder = preferHardwareEncoder,
                    ioBufferBytes = ioBuffer
                )
            ) {
                return true
            }
            if (targetOutputFile.exists()) targetOutputFile.delete()

            // 1. Direct Voice Remuxing (Turbo Mode): When video volume is 0,
            // we bypass extracting/decoding original video audio completely
            if (videoAudioVolume <= 0.001f) {
                onProgress("Adding voice directly to video (Turbo)...")
                val safeRate = 44100
                val convSuccess = convertTtsWavToStereoPcm(ttsWavFile, ttsPcmFile, safeRate)
                val pcmToMux = if (convSuccess && ttsPcmFile.exists() && ttsPcmFile.length() > 0) ttsPcmFile else ttsWavFile
                val skip = if (pcmToMux == ttsWavFile) 44L else 0L

                return muxVideoWithPcmAudio(
                    videoFile = videoFile,
                    pcmFile = pcmToMux,
                    outputFile = targetOutputFile,
                    sampleRate = safeRate,
                    channels = 2,
                    skipHeaderBytes = skip,
                    volume = ttsAudioVolume,
                    preferHardwareEncoder = preferHardwareEncoder,
                    ioBufferBytes = ioBuffer
                )
            }

            // 2. Decode original video audio to 16-bit Stereo PCM
            onProgress("Extracting background audio from video...")
            val videoAudioStats = decodeAudioFromVideo(
                videoFile,
                videoPcmFile,
                preferHardwareDecoder = preferHardwareEncoder,
                ioBufferBytes = ioBuffer
            )
            val hasVideoAudio = (videoAudioStats != null && videoPcmFile.exists() && videoPcmFile.length() > 0)
            val targetSampleRate = if (hasVideoAudio) videoAudioStats!!.first else 44100

            if (!hasVideoAudio) {
                onProgress("No original audio track found. Adding voice directly...")
                convertTtsWavToStereoPcm(ttsWavFile, ttsPcmFile, targetSampleRate)
                return muxVideoWithPcmAudio(
                    videoFile = videoFile,
                    pcmFile = ttsPcmFile,
                    outputFile = targetOutputFile,
                    sampleRate = targetSampleRate,
                    channels = 2,
                    skipHeaderBytes = 0L,
                    volume = ttsAudioVolume,
                    preferHardwareEncoder = preferHardwareEncoder,
                    ioBufferBytes = ioBuffer
                )
            }

            // 3. Resample TTS WAV audio to the exact targetSampleRate & stereo PCM
            onProgress("Preparing voice track for high-quality mix...")
            val ttsConverted = convertTtsWavToStereoPcm(ttsWavFile, ttsPcmFile, targetSampleRate)
            if (!ttsConverted || !ttsPcmFile.exists() || ttsPcmFile.length() == 0L) {
                Log.w(TAG, "TTS conversion failed, falling back to original video audio")
                return muxVideoWithPcmAudio(
                    videoFile, videoPcmFile, targetOutputFile, targetSampleRate, 2, 0L, videoAudioVolume,
                    preferHardwareEncoder, ioBuffer
                )
            }

            // 4. Linear Mixing (Exact equivalent to FFmpeg filter_complex amix duration=longest)
            onProgress("Mixing background sound (${(videoAudioVolume * 100).toInt()}%) & voice (${(ttsAudioVolume * 100).toInt()}%)...")

            videoIn = BufferedInputStream(FileInputStream(videoPcmFile), ioBuffer)
            ttsIn = BufferedInputStream(FileInputStream(ttsPcmFile), ioBuffer)
            mixedOut = BufferedOutputStream(FileOutputStream(mixedPcmFile), ioBuffer)

            val chunkSize = 8192 // 2048 samples (1024 stereo frames)
            val videoBytes = ByteArray(chunkSize)
            val ttsBytes = ByteArray(chunkSize)
            val outByteBuffer = ByteBuffer.allocate(chunkSize).order(ByteOrder.LITTLE_ENDIAN)

            var isVideoDone = false
            var isTtsDone = false

            while (!isVideoDone || !isTtsDone) {
                var vBytesRead = 0
                if (!isVideoDone) {
                    var totalRead = 0
                    while (totalRead < chunkSize) {
                        val r = videoIn.read(videoBytes, totalRead, chunkSize - totalRead)
                        if (r <= 0) {
                            isVideoDone = true
                            break
                        }
                        totalRead += r
                    }
                    vBytesRead = totalRead
                }

                var tBytesRead = 0
                if (!isTtsDone) {
                    var totalRead = 0
                    while (totalRead < chunkSize) {
                        val r = ttsIn.read(ttsBytes, totalRead, chunkSize - totalRead)
                        if (r <= 0) {
                            isTtsDone = true
                            break
                        }
                        totalRead += r
                    }
                    tBytesRead = totalRead
                }

                val maxBytes = maxOf(vBytesRead, tBytesRead)
                if (maxBytes <= 0) break

                val sampleCount = maxBytes / 2
                val vShorts = if (vBytesRead > 0) {
                    val count = vBytesRead / 2
                    val shorts = ShortArray(count)
                    ByteBuffer.wrap(videoBytes, 0, vBytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    shorts
                } else null

                val tShorts = if (tBytesRead > 0) {
                    val count = tBytesRead / 2
                    val shorts = ShortArray(count)
                    ByteBuffer.wrap(ttsBytes, 0, tBytesRead).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    shorts
                } else null

                outByteBuffer.clear()
                for (j in 0 until sampleCount) {
                    val vSample = if (vShorts != null && j < vShorts.size) vShorts[j].toFloat() * videoAudioVolume else 0f
                    val tSample = if (tShorts != null && j < tShorts.size) tShorts[j].toFloat() * ttsAudioVolume else 0f

                    // PURE LINEAR ADDITION (FFmpeg amix)
                    // Does NOT squash or compress quiet sounds like birds chirping or background ambience!
                    val sum = vSample + tSample

                    // Transparent headroom limiter: 100% linear within [-30000, 30000]
                    // Smooth soft asymptotic curve only for extreme peaks above 30000
                    val finalSample = when {
                        sum > 30000f -> {
                            val excess = sum - 30000f
                            (30000f + excess / (1f + excess / 2767f)).coerceAtMost(32767f).toInt().toShort()
                        }
                        sum < -30000f -> {
                            val excess = -sum - 30000f
                            (-30000f - excess / (1f + excess / 2767f)).coerceAtLeast(-32768f).toInt().toShort()
                        }
                        else -> sum.toInt().toShort()
                    }

                    outByteBuffer.putShort(finalSample)
                }

                mixedOut.write(outByteBuffer.array(), 0, sampleCount * 2)
            }

            mixedOut.flush()
            mixedOut.close()
            mixedOut = null

            videoIn?.close()
            videoIn = null
            ttsIn?.close()
            ttsIn = null

            // 5. AAC Encoding and MP4 Muxing
            onProgress("Encoding audio & muxing into final video...")
            return muxVideoWithPcmAudio(
                videoFile = videoFile,
                pcmFile = mixedPcmFile,
                outputFile = targetOutputFile,
                sampleRate = targetSampleRate,
                channels = 2,
                skipHeaderBytes = 0L,
                volume = 1.0f,
                preferHardwareEncoder = preferHardwareEncoder,
                ioBufferBytes = ioBuffer
            )
        } catch (e: Exception) {
            Log.e(TAG, "Dubbing merge pipeline error: ${e.message}", e)
            return false
        } finally {
            try { videoIn?.close() } catch (e: Exception) {}
            try { ttsIn?.close() } catch (e: Exception) {}
            try { mixedOut?.close() } catch (e: Exception) {}
            try { videoPcmFile.delete() } catch (e: Exception) {}
            try { ttsPcmFile.delete() } catch (e: Exception) {}
            try { mixedPcmFile.delete() } catch (e: Exception) {}
        }
    }

    /**
     * Backward-compatibility overload.
     */
    fun mixAndEncodeDubbedMedia(
        videoFile: File,
        ttsWavFile: File,
        targetOutputFile: File,
        ttsAudioVolume: Float,
        onProgress: (String) -> Unit
    ): Boolean {
        return mixAndEncodeDubbedMedia(
            videoFile = videoFile,
            ttsWavFile = ttsWavFile,
            targetOutputFile = targetOutputFile,
            videoAudioVolume = 0.20f,
            ttsAudioVolume = ttsAudioVolume,
            onProgress = onProgress
        )
    }

    /**
     * Encodes 16-bit stereo PCM directly into high-quality AAC (192 kbps) and multiplexes with the original
     * video track into the MP4 container in synchronized chronological order.
     */
    private fun muxVideoWithPcmAudio(
        videoFile: File,
        pcmFile: File,
        outputFile: File,
        sampleRate: Int,
        channels: Int,
        skipHeaderBytes: Long = 0L,
        volume: Float = 1.0f,
        preferHardwareEncoder: Boolean = true,
        ioBufferBytes: Int = 64 * 1024,
        pcmSupplier: ((ByteArray, Int) -> Int)? = null
    ): Boolean {
        val videoExtractor = MediaExtractor()
        var audioEncoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var pcmInputStream: BufferedInputStream? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            var videoTrackIndexInExtractor = -1
            var videoFormat: MediaFormat? = null

            for (i in 0 until videoExtractor.trackCount) {
                val format = videoExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    videoTrackIndexInExtractor = i
                    videoFormat = format
                    break
                }
            }

            val hasVideoTrack = (videoTrackIndexInExtractor >= 0 && videoFormat != null)
            if (hasVideoTrack) {
                videoExtractor.selectTrack(videoTrackIndexInExtractor)
            }

            // Setup AAC Audio Encoder (Studio quality 192 kbps LC-AAC)
            val safeSampleRate = if (sampleRate in 8000..96000) sampleRate else 44100
            val safeChannels = channels.coerceIn(1, 2)
            val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, safeSampleRate, safeChannels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 192000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 32768)
            }

            audioEncoder = createCodec(
                MediaFormat.MIMETYPE_AUDIO_AAC,
                encoder = true,
                preferHardware = preferHardwareEncoder
            )
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = if (hasVideoTrack && videoFormat != null) {
                try {
                    muxer.addTrack(videoFormat)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not add video track as-is: ${e.message}")
                    -1
                }
            } else -1
            var muxerAudioTrack = -1
            var isMuxerStarted = false

            pcmInputStream = if (pcmSupplier == null) {
                BufferedInputStream(FileInputStream(pcmFile), safeIoBuffer(ioBufferBytes))
            } else {
                null
            }
            if (pcmSupplier == null && skipHeaderBytes > 0L && pcmInputStream != null) {
                try {
                    pcmInputStream.skip(skipHeaderBytes)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not skip header bytes: ${e.message}")
                }
            }
            val bufferInfo = MediaCodec.BufferInfo()
            val pcmChunk = ByteArray(4096)
            var totalAudioBytesRead = 0L
            var isPcmEOS = false
            var isAudioEncoderEOS = false
            var isVideoEOS = (muxerVideoTrack < 0)
            val timeoutUs = 5000L

            val videoBuffer = ByteBuffer.allocate(2 * 1024 * 1024)
            val videoInfo = MediaCodec.BufferInfo()
            var currentAudioPtsUs = 0L

            var consecutiveTimeouts = 0

            while (!isAudioEncoderEOS || (!isVideoEOS && muxerVideoTrack >= 0)) {
                var progressed = false

                // 1. Feed PCM to Audio Encoder
                if (!isPcmEOS) {
                    val inBufferId = audioEncoder.dequeueInputBuffer(timeoutUs)
                    if (inBufferId >= 0) {
                        val inBuffer = audioEncoder.getInputBuffer(inBufferId)
                        if (inBuffer != null) {
                            inBuffer.clear()
                            val maxRead = minOf(pcmChunk.size, inBuffer.capacity())
                            val bytesRead = if (pcmSupplier != null) {
                                pcmSupplier(pcmChunk, maxRead)
                            } else {
                                pcmInputStream?.read(pcmChunk, 0, maxRead) ?: -1
                            }
                            if (bytesRead <= 0) {
                                audioEncoder.queueInputBuffer(inBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isPcmEOS = true
                            } else {
                                if (volume != 1.0f) {
                                    val sampleCount = bytesRead / 2
                                    for (s in 0 until sampleCount) {
                                        val idx = s * 2
                                        val low = pcmChunk[idx].toInt() and 0xFF
                                        val high = pcmChunk[idx + 1].toInt() shl 8
                                        val origVal = (low or high).toShort()
                                        val scaled = (origVal * volume).toInt().coerceIn(-32768, 32767).toShort()
                                        pcmChunk[idx] = (scaled.toInt() and 0xFF).toByte()
                                        pcmChunk[idx + 1] = ((scaled.toInt() shr 8) and 0xFF).toByte()
                                    }
                                }
                                inBuffer.put(pcmChunk, 0, bytesRead)
                                val ptsUs = (totalAudioBytesRead * 1000000L) / (safeSampleRate * safeChannels * 2)
                                audioEncoder.queueInputBuffer(inBufferId, 0, bytesRead, ptsUs, 0)
                                totalAudioBytesRead += bytesRead
                            }
                            progressed = true
                        }
                    }
                }

                // 2. Fetch Encoded AAC Audio
                if (!isAudioEncoderEOS) {
                    val outBufferId = audioEncoder.dequeueOutputBuffer(bufferInfo, timeoutUs)
                    if (outBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        if (!isMuxerStarted) {
                            val newFormat = audioEncoder.outputFormat
                            muxerAudioTrack = muxer.addTrack(newFormat)
                            muxer.start()
                            isMuxerStarted = true
                            Log.d(TAG, "MediaMuxer started with videoTrack=$muxerVideoTrack, audioTrack=$muxerAudioTrack")
                        }
                        progressed = true
                    } else if (outBufferId >= 0) {
                        val outBuffer = audioEncoder.getOutputBuffer(outBufferId)
                        if (outBuffer != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            if (bufferInfo.size > 0 && isMuxerStarted && muxerAudioTrack >= 0) {
                                outBuffer.position(bufferInfo.offset)
                                outBuffer.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(muxerAudioTrack, outBuffer, bufferInfo)
                                currentAudioPtsUs = bufferInfo.presentationTimeUs
                            }
                        }

                        audioEncoder.releaseOutputBuffer(outBufferId, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            isAudioEncoderEOS = true
                        }
                        progressed = true
                    }
                }

                // 3. Interleaved Video Frame Writing (Synchronized with Audio PTS)
                if (isMuxerStarted && !isVideoEOS && muxerVideoTrack >= 0) {
                    val maxVideoPtsTargetUs = if (isAudioEncoderEOS) Long.MAX_VALUE else currentAudioPtsUs + 1500000L

                    while (!isVideoEOS) {
                        val videoSampleTime = videoExtractor.sampleTime
                        if (videoSampleTime < 0) {
                            isVideoEOS = true
                            break
                        }
                        if (videoSampleTime > maxVideoPtsTargetUs) {
                            break
                        }

                        videoBuffer.clear()
                        videoInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                        if (videoInfo.size < 0) {
                            isVideoEOS = true
                            break
                        }
                        videoInfo.offset = 0
                        videoInfo.presentationTimeUs = videoSampleTime
                        videoInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoInfo)
                        videoExtractor.advance()
                        progressed = true
                    }
                }

                if (!progressed) {
                    consecutiveTimeouts++
                    if (consecutiveTimeouts > 200) {
                        Log.w(TAG, "Encoder/Extractor timed out repeatedly, breaking mux loop.")
                        break
                    }
                } else {
                    consecutiveTimeouts = 0
                }
            }

            // Fallback if muxer was never started (e.g. Empty audio track or format change delay)
            if (!isMuxerStarted) {
                Log.w(TAG, "Muxer was not started in main loop. Attempting fallback start.")
                if (muxerAudioTrack < 0) {
                    try {
                        val fallbackFormat = audioEncoder.outputFormat
                        muxerAudioTrack = muxer.addTrack(fallbackFormat)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not get encoder output format for fallback: ${e.message}")
                    }
                }
                muxer.start()
                isMuxerStarted = true

                if (muxerVideoTrack >= 0) {
                    while (!isVideoEOS) {
                        val videoSampleTime = videoExtractor.sampleTime
                        if (videoSampleTime < 0) break
                        videoBuffer.clear()
                        videoInfo.size = videoExtractor.readSampleData(videoBuffer, 0)
                        if (videoInfo.size < 0) break
                        videoInfo.offset = 0
                        videoInfo.presentationTimeUs = videoSampleTime
                        videoInfo.flags = videoExtractor.sampleFlags
                        muxer.writeSampleData(muxerVideoTrack, videoBuffer, videoInfo)
                        videoExtractor.advance()
                    }
                }
            }

            if (isMuxerStarted) {
                muxer.stop()
            }
            val outSize = outputFile.length()
            Log.d(TAG, "Dubbed video muxed into ${outputFile.absolutePath} (size: $outSize bytes)")
            return outSize > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error in muxVideoWithPcmAudio: ${e.message}", e)
            return false
        } finally {
            try { videoExtractor.release() } catch (e: Exception) {}
            try {
                audioEncoder?.stop()
                audioEncoder?.release()
            } catch (e: Exception) {}
            try { muxer?.release() } catch (e: Exception) {}
            try { pcmInputStream?.close() } catch (e: Exception) {}
        }
    }

    /**
     * Windows command, done in one pass:
     * ffmpeg -i video -i audio -filter_complex
     * "[0:a]volume=video[a1];[1:a]volume=created[a2];[a1][a2]amix=inputs=2:duration=longest"
     * -c:v copy -c:a aac
     *
     * amix normalize divides by the number of active inputs, so video=0.2 and
     * created=2.0 land at the same loudness as that script.
     */
    private fun tryOneStepMix(
        videoFile: File,
        audioFile: File,
        outputFile: File,
        videoAudioVolume: Float,
        createdAudioVolume: Float,
        preferHardwareEncoder: Boolean,
        ioBufferBytes: Int
    ): Boolean {
        val mixer = DirectAudioMixer(videoFile, audioFile, videoAudioVolume, createdAudioVolume)
        return try {
            if (!mixer.start()) return false
            muxVideoWithPcmAudio(
                videoFile = videoFile,
                pcmFile = audioFile,
                outputFile = outputFile,
                sampleRate = mixer.sampleRate,
                channels = 2,
                skipHeaderBytes = 0L,
                volume = 1f,
                preferHardwareEncoder = preferHardwareEncoder,
                ioBufferBytes = ioBufferBytes,
                pcmSupplier = { buffer, max -> mixer.pull(buffer, max) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "One-step mix failed, using the older path: ${e.message}", e)
            false
        } finally {
            mixer.close()
        }
    }

    private class DirectAudioMixer(
        private val videoFile: File,
        private val audioFile: File,
        private val videoVolume: Float,
        private val createdVolume: Float
    ) : java.io.Closeable {
        private var videoPull: AudioPull? = null
        private var createdPull: AudioPull? = null
        var sampleRate: Int = 44100
            private set
        private var videoDone = false
        private var createdDone = false
        private val videoFrame = ShortArray(2)
        private val createdFrame = ShortArray(2)
        private val divisor: Float
            get() = if (videoPull != null && createdPull != null) 2f else 1f

        fun start(): Boolean {
            createdPull = AudioPull.open(audioFile) ?: return false
            if (videoVolume > 0.001f) {
                videoPull = AudioPull.open(videoFile)
            }
            videoPull?.prime()
            createdPull?.prime()
            sampleRate = videoPull?.sampleRate ?: createdPull?.sampleRate ?: 44100
            if (sampleRate !in 8000..96000) sampleRate = 44100
            videoPull?.setTargetRate(sampleRate)
            createdPull?.setTargetRate(sampleRate)
            return true
        }

        fun pull(dst: ByteArray, maxBytes: Int): Int {
            val frames = maxBytes / 4
            if (frames <= 0) return -1
            var offset = 0
            repeat(frames) {
                if (videoDone && createdDone) return if (offset == 0) -1 else offset
                val hasVideo = !videoDone && videoPull?.readFrame(videoFrame) == true
                if (!hasVideo) videoDone = true
                val hasCreated = !createdDone && createdPull?.readFrame(createdFrame) == true
                if (!hasCreated) createdDone = true
                if (!hasVideo && !hasCreated) return if (offset == 0) -1 else offset
                val div = divisor
                val left = limitSample(
                    ((if (hasVideo) videoFrame[0].toFloat() else 0f) * videoVolume +
                        (if (hasCreated) createdFrame[0].toFloat() else 0f) * createdVolume) / div
                )
                val right = limitSample(
                    ((if (hasVideo) videoFrame[1].toFloat() else 0f) * videoVolume +
                        (if (hasCreated) createdFrame[1].toFloat() else 0f) * createdVolume) / div
                )
                putShort(dst, offset, left)
                putShort(dst, offset + 2, right)
                offset += 4
            }
            return if (offset == 0) -1 else offset
        }

        override fun close() {
            try { videoPull?.close() } catch (e: Exception) {}
            try { createdPull?.close() } catch (e: Exception) {}
        }
    }

    private class AudioPull private constructor(
        private val extractor: MediaExtractor?,
        private val decoder: MediaCodec?,
        private val wav: RandomAccessFile?,
        private val wavDataOffset: Long,
        private val wavDataEnd: Long,
        var sampleRate: Int,
        private var channels: Int
    ) : java.io.Closeable {
        private val decoded = ArrayDeque<Short>()
        private val bufferInfo = MediaCodec.BufferInfo()
        private var extractorEos = false
        private var decoderEos = decoder == null && wav == null
        private var forcedEos = false
        private var wavPos = wavDataOffset
        private var targetRate = sampleRate
        private var resamplePos = 0.0

        fun setTargetRate(rate: Int) {
            targetRate = if (rate in 8000..96000) rate else sampleRate
        }

        fun prime() {
            ensureFrames(1)
        }

        fun readFrame(out: ShortArray): Boolean {
            val step = sampleRate.toDouble() / targetRate.toDouble().coerceAtLeast(1.0)
            ensureFrames(resamplePos.toInt() + 2)
            val available = decoded.size / 2
            if (available <= resamplePos.toInt() && sourceDone()) return false
            val idx = resamplePos.toInt()
            val frac = (resamplePos - idx).toFloat()
            val left = interpolate(idx, 0, frac)
            val right = interpolate(idx, 1, frac)
            out[0] = left
            out[1] = right
            resamplePos += step
            val drop = resamplePos.toInt()
            if (drop > 0) {
                val dropShorts = (drop * 2).coerceAtMost(decoded.size)
                repeat(dropShorts) { decoded.removeFirst() }
                resamplePos -= drop
            }
            return true
        }

        private fun interpolate(frame: Int, channel: Int, frac: Float): Short {
            val a = sampleAt(frame, channel)
            val b = sampleAt(frame + 1, channel)
            return (a + frac * (b - a)).toInt().coerceIn(-32768, 32767).toShort()
        }

        private fun sampleAt(frame: Int, channel: Int): Float {
            val index = frame * 2 + channel
            if (index < 0 || index >= decoded.size) return 0f
            return decoded.elementAt(index).toFloat()
        }

        private fun ensureFrames(minFrames: Int) {
            var spins = 0
            while (decoded.size / 2 < minFrames && !sourceDone() && spins < 80) {
                val before = decoded.size
                pump()
                if (decoded.size == before) spins++ else spins = 0
            }
            if (spins >= 80) forcedEos = true
        }

        private fun sourceDone(): Boolean = forcedEos || (wav != null && wavPos >= wavDataEnd && decoded.size < 4) || (decoder != null && decoderEos && decoded.size < 4) || (decoder == null && wav == null)

        private fun pump() {
            if (wav != null) {
                pumpWav()
            } else if (decoder != null && extractor != null) {
                pumpCodec()
            } else {
                forcedEos = true
            }
        }

        private fun pumpWav() {
            val file = wav ?: return
            if (wavPos >= wavDataEnd) {
                decoderEos = true
                return
            }
            val bytesLeft = (wavDataEnd - wavPos).toInt().coerceAtLeast(0)
            val bytesToRead = minOf(8192, bytesLeft) and 1.inv()
            if (bytesToRead < 2) {
                decoderEos = true
                return
            }
            val raw = ByteArray(bytesToRead)
            file.seek(wavPos)
            file.readFully(raw)
            wavPos += bytesToRead
            val shorts = ShortArray(bytesToRead / 2)
            ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            appendStereo(downmixToStereo(shorts, channels))
            if (wavPos >= wavDataEnd) decoderEos = true
        }

        private fun pumpCodec() {
            val codec = decoder ?: return
            val source = extractor ?: return
            if (!extractorEos) {
                val inId = codec.dequeueInputBuffer(2000)
                if (inId >= 0) {
                    val input = codec.getInputBuffer(inId)
                    if (input != null) {
                        val size = source.readSampleData(input, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorEos = true
                        } else {
                            codec.queueInputBuffer(inId, 0, size, source.sampleTime, 0)
                            source.advance()
                        }
                    }
                }
            }
            val outId = codec.dequeueOutputBuffer(bufferInfo, 2000)
            if (outId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                applyFormat(codec.outputFormat)
            } else if (outId >= 0) {
                val output = codec.getOutputBuffer(outId)
                if (output != null && bufferInfo.size > 1) {
                    output.position(bufferInfo.offset)
                    output.limit(bufferInfo.offset + bufferInfo.size)
                    val count = bufferInfo.size / 2
                    val shorts = ShortArray(count)
                    output.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
                    appendStereo(downmixToStereo(shorts, channels))
                }
                codec.releaseOutputBuffer(outId, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    decoderEos = true
                }
            } else if (extractorEos && outId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Decoder may still be flushing. A later pump can finish it.
            }
        }

        private fun applyFormat(format: MediaFormat) {
            if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                val rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                if (rate in 8000..96000) sampleRate = rate
            }
            if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                val count = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                if (count in 1..8) channels = count
            }
        }

        private fun appendStereo(stereo: ShortArray) {
            for (sample in stereo) decoded.addLast(sample)
        }

        override fun close() {
            try { wav?.close() } catch (e: Exception) {}
            try {
                decoder?.stop()
                decoder?.release()
            } catch (e: Exception) {}
            try { extractor?.release() } catch (e: Exception) {}
        }

        companion object {
            fun open(file: File): AudioPull? {
                if (!file.exists() || file.length() < 16) return null
                return if (isWav(file)) openWav(file) else openDecoded(file)
            }

            private fun isWav(file: File): Boolean {
                return try {
                    RandomAccessFile(file, "r").use { raf ->
                        val header = ByteArray(12)
                        if (raf.length() < 12) return false
                        raf.readFully(header)
                        String(header, 0, 4) == "RIFF" && String(header, 8, 4) == "WAVE"
                    }
                } catch (e: Exception) {
                    false
                }
            }

            private fun openWav(file: File): AudioPull? {
                return try {
                    val raf = RandomAccessFile(file, "r")
                    var channels = 1
                    var rate = 22050
                    var dataOffset = 44L
                    var dataLength = file.length() - 44L
                    raf.seek(12)
                    while (raf.filePointer < raf.length() - 8) {
                        val idBytes = ByteArray(4)
                        raf.readFully(idBytes)
                        val chunkId = String(idBytes)
                        val chunkSize = Integer.reverseBytes(raf.readInt()).toLong() and 0xFFFFFFFFL
                        if (chunkId == "fmt ") {
                            val fmtBytes = ByteArray(minOf(chunkSize.toInt(), 16))
                            raf.readFully(fmtBytes)
                            val buffer = ByteBuffer.wrap(fmtBytes).order(ByteOrder.LITTLE_ENDIAN)
                            buffer.short
                            channels = buffer.short.toInt().coerceIn(1, 8)
                            rate = buffer.int.coerceIn(8000, 96000)
                            val remaining = chunkSize - fmtBytes.size
                            if (remaining > 0) raf.skipBytes(remaining.toInt())
                        } else if (chunkId == "data") {
                            dataOffset = raf.filePointer
                            dataLength = chunkSize
                            break
                        } else {
                            raf.skipBytes(chunkSize.toInt())
                        }
                    }
                    AudioPull(null, null, raf, dataOffset, dataOffset + dataLength, rate, channels)
                } catch (e: Exception) {
                    Log.w(TAG, "WAV open failed, trying decoder: ${e.message}")
                    openDecoded(file)
                }
            }

            private fun openDecoded(file: File): AudioPull? {
                val extractor = MediaExtractor()
                return try {
                    extractor.setDataSource(file.absolutePath)
                    var audioIndex = -1
                    var format: MediaFormat? = null
                    for (i in 0 until extractor.trackCount) {
                        val candidate = extractor.getTrackFormat(i)
                        val mime = candidate.getString(MediaFormat.KEY_MIME) ?: ""
                        if (mime.startsWith("audio/")) {
                            audioIndex = i
                            format = candidate
                            break
                        }
                    }
                    if (audioIndex < 0 || format == null) {
                        extractor.release()
                        return null
                    }
                    extractor.selectTrack(audioIndex)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime == "audio/raw") {
                        extractor.release()
                        return null
                    }
                    val decoder = MediaCodec.createDecoderByType(mime)
                    decoder.configure(format, null, null, 0)
                    decoder.start()
                    val rate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    } else 44100
                    val count = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    } else 2
                    AudioPull(extractor, decoder, null, 0L, 0L, rate, count)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not open audio ${file.name}: ${e.message}")
                    try { extractor.release() } catch (ignored: Exception) {}
                    null
                }
            }
        }
    }

    private fun limitSample(sum: Float): Short {
        return when {
            sum > 30000f -> {
                val excess = sum - 30000f
                (30000f + excess / (1f + excess / 2767f)).coerceAtMost(32767f).toInt().toShort()
            }
            sum < -30000f -> {
                val excess = -sum - 30000f
                (-30000f - excess / (1f + excess / 2767f)).coerceAtLeast(-32768f).toInt().toShort()
            }
            else -> sum.toInt().toShort()
        }
    }

    private fun putShort(dst: ByteArray, offset: Int, value: Short) {
        dst[offset] = (value.toInt() and 0xFF).toByte()
        dst[offset + 1] = ((value.toInt() shr 8) and 0xFF).toByte()
    }

    private fun downmixToStereo(shorts: ShortArray, channelCount: Int): ShortArray {
        if (shorts.isEmpty() || channelCount <= 0) return ShortArray(0)
        val frames = shorts.size / channelCount
        if (frames <= 0) return ShortArray(0)
        if (channelCount == 2) return shorts.copyOf(frames * 2)
        val out = ShortArray(frames * 2)
        when (channelCount) {
            1 -> {
                for (i in 0 until frames) {
                    out[i * 2] = shorts[i]
                    out[i * 2 + 1] = shorts[i]
                }
            }
            6 -> {
                for (f in 0 until frames) {
                    val base = f * 6
                    val fl = shorts[base].toFloat()
                    val fr = shorts[base + 1].toFloat()
                    val fc = shorts[base + 2].toFloat()
                    val lfe = shorts[base + 3].toFloat()
                    val bl = shorts[base + 4].toFloat()
                    val br = shorts[base + 5].toFloat()
                    out[f * 2] = ((fl + 0.707f * fc + 0.707f * bl + 0.5f * lfe) / 1.707f).toInt().coerceIn(-32768, 32767).toShort()
                    out[f * 2 + 1] = ((fr + 0.707f * fc + 0.707f * br + 0.5f * lfe) / 1.707f).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
            else -> {
                val half = (channelCount / 2).coerceAtLeast(1)
                for (f in 0 until frames) {
                    val base = f * channelCount
                    var leftSum = 0f
                    var rightSum = 0f
                    for (c in 0 until channelCount) {
                        val sample = shorts[base + c].toFloat()
                        if (c % 2 == 0) leftSum += sample else rightSum += sample
                    }
                    out[f * 2] = (leftSum / half).toInt().coerceIn(-32768, 32767).toShort()
                    out[f * 2 + 1] = (rightSum / half).toInt().coerceIn(-32768, 32767).toShort()
                }
            }
        }
        return out
    }

    private fun safeIoBuffer(requested: Int): Int = requested.coerceIn(16 * 1024, 1024 * 1024)

    /**
     * Picks a hardware or software codec when the user asks for one.
     * Falls back to whatever the device actually has, then to the platform default.
     */
    private fun createCodec(mime: String, encoder: Boolean, preferHardware: Boolean): MediaCodec {
        try {
            val matches = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder == encoder && info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
            }
            if (matches.isNotEmpty()) {
                val preferred = matches.filter { isHardwareCodec(it) == preferHardware }
                val chosen = preferred.firstOrNull() ?: matches.first()
                return MediaCodec.createByCodecName(chosen.name)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Codec lookup failed for $mime, using platform default: ${e.message}")
        }
        return if (encoder) MediaCodec.createEncoderByType(mime) else MediaCodec.createDecoderByType(mime)
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return info.isHardwareAccelerated
        }
        val name = info.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.startsWith("c2.google."))
    }
}
