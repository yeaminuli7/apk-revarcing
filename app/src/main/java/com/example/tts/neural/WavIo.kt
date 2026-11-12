package com.example.tts.neural

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Shared WAV writer: 16 kHz, mono, 16-bit PCM — the app-wide neural format. */
object WavIo {

    fun write16kMono(outFile: File, pcm: ShortArray) {
        val header = ByteArray(44)
        val totalDataLen = pcm.size * 2 + 36
        fun putInt(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = ((v shr 8) and 0xff).toByte()
            header[off + 2] = ((v shr 16) and 0xff).toByte(); header[off + 3] = ((v shr 24) and 0xff).toByte()
        }
        fun putShort(off: Int, v: Int) {
            header[off] = (v and 0xff).toByte(); header[off + 1] = ((v shr 8) and 0xff).toByte()
        }
        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        putInt(4, totalDataLen)
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        putInt(16, 16)
        putShort(20, 1)                    // PCM
        putShort(22, 1)                    // mono
        putInt(24, NeuralVoiceManager.TARGET_SAMPLE_RATE)
        putInt(28, NeuralVoiceManager.TARGET_SAMPLE_RATE * 2) // byte rate
        putShort(32, 2)                    // block align
        putShort(34, 16)                   // bits per sample
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        putInt(40, pcm.size * 2)
        FileOutputStream(outFile).use { fos ->
            fos.write(header)
            val buf = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
            for (s in pcm) buf.putShort(s)
            fos.write(buf.array())
        }
    }
}
