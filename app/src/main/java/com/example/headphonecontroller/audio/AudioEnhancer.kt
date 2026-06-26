package com.example.headphonecontroller.audio

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class AudioEnhancer(private val context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()

    private val session: OrtSession by lazy {
        val bytes = context.assets.open("model_int8.onnx").readBytes()
        env.createSession(bytes, OrtSession.SessionOptions())
    }

    /**
     * Enhances [inputWav] (PCM 16-bit WAV, 16 kHz mono) using the PHASEN model.
     * Returns a new File saved next to the original with the suffix "_enhanced.wav".
     */
    fun enhance(inputWav: File): File {
        val samples = readWavSamples(inputWav)
        val enhanced = runInference(samples)

        val outputFile = File(
            inputWav.parentFile,
            "${inputWav.nameWithoutExtension}_enhanced.wav"
        )
        writeWav(outputFile, enhanced)
        return outputFile
    }

    private fun runInference(samples: FloatArray): FloatArray {
        val shape = longArrayOf(1L, 1L, samples.size.toLong())
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), shape)
        inputTensor.use { tensor ->
            session.run(mapOf("input_wav" to tensor)).use { result ->
                val outTensor = result["est_wav"].get() as OnnxTensor
                val fb = outTensor.floatBuffer
                val out = FloatArray(fb.remaining())
                fb.get(out)
                return out
            }
        }
    }

    // Reads the PCM 16-bit samples from a WAV file and returns them as float32 in [-1, 1].
    private fun readWavSamples(file: File): FloatArray {
        val bytes = file.readBytes()
        val dataOffset = findDataChunkOffset(bytes)
        val numSamples = (bytes.size - dataOffset) / 2  // 16-bit = 2 bytes
        val buf = ByteBuffer.wrap(bytes, dataOffset, bytes.size - dataOffset)
            .order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(numSamples) { buf.short.toFloat() / 32768.0f }
    }

    // Finds the byte offset of the PCM data (immediately after the "data" chunk header).
    private fun findDataChunkOffset(bytes: ByteArray): Int {
        for (i in 0 until bytes.size - 8) {
            if (bytes[i]     == 'd'.code.toByte() &&
                bytes[i + 1] == 'a'.code.toByte() &&
                bytes[i + 2] == 't'.code.toByte() &&
                bytes[i + 3] == 'a'.code.toByte()) {
                return i + 8  // skip "data" marker + 4-byte size field
            }
        }
        return 44  // fallback: standard WAV header size
    }

    // Saves float32 samples as PCM 16-bit WAV at 16 kHz mono.
    private fun writeWav(file: File, samples: FloatArray) {
        val dataSize = samples.size * 2
        val byteRate = SAMPLE_RATE * 1 * 2

        FileOutputStream(file).use { fos ->
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray())
            hdr.putInt(36 + dataSize)
            hdr.put("WAVE".toByteArray())
            hdr.put("fmt ".toByteArray())
            hdr.putInt(16)
            hdr.putShort(1)             // PCM
            hdr.putShort(1)             // mono
            hdr.putInt(SAMPLE_RATE)
            hdr.putInt(byteRate)
            hdr.putShort(2)             // block align
            hdr.putShort(16)            // bits per sample
            hdr.put("data".toByteArray())
            hdr.putInt(dataSize)
            fos.write(hdr.array())

            val dataBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (f in samples) {
                dataBuf.putShort((f.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort())
            }
            fos.write(dataBuf.array())
        }
    }

    fun release() {
        runCatching { session.close() }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
    }
}
