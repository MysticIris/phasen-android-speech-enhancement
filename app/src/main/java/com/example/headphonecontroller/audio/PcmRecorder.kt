package com.example.headphonecontroller.audio

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PcmRecorder(private val context: Context) {

    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var currentFile: File? = null
    @Volatile private var recording = false

    fun isRecording(): Boolean = recording

    fun start(): File {
        check(!recording) { "Recorder is already running." }

        val outputDir = File(context.getExternalFilesDir(null), "recordings").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDir, "pcm_$timestamp.wav")

        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        ).coerceAtLeast(4096)

        val record = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            bufferSize * 4
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed." }

        audioRecord = record
        currentFile = outputFile
        recording = true

        recordingThread = Thread {
            val samples = ArrayList<Float>(SAMPLE_RATE * 60) // pre-alloc ~60s
            val buffer = FloatArray(bufferSize)
            record.startRecording()

            while (recording) {
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) {
                    for (i in 0 until read) samples.add(buffer[i])
                }
            }

            record.stop()
            record.release()
            audioRecord = null

            writeWav(outputFile, samples)
        }
        recordingThread!!.start()

        return outputFile
    }

    fun stop(): File? {
        if (!recording) return currentFile
        recording = false
        recordingThread?.join()
        recordingThread = null

        val file = currentFile
        currentFile = null
        // discard empty / header-only files
        return if (file != null && file.exists() && file.length() > 44L) file else {
            file?.delete()
            null
        }
    }

    fun release() {
        recording = false
        audioRecord?.runCatching { stop(); release() }
        audioRecord = null
        recordingThread = null
    }

    // Saves samples as 16-bit PCM WAV (universally supported by Android MediaPlayer).
    private fun writeWav(file: File, samples: ArrayList<Float>) {
        val numSamples = samples.size
        val dataSize = numSamples * 2          // 16-bit = 2 bytes per sample
        val byteRate = SAMPLE_RATE * 1 * 2

        FileOutputStream(file).use { fos ->
            // 44-byte RIFF/WAV header
            val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            hdr.put("RIFF".toByteArray())
            hdr.putInt(36 + dataSize)
            hdr.put("WAVE".toByteArray())
            hdr.put("fmt ".toByteArray())
            hdr.putInt(16)              // chunk size
            hdr.putShort(1)             // PCM format
            hdr.putShort(1)             // mono
            hdr.putInt(SAMPLE_RATE)
            hdr.putInt(byteRate)
            hdr.putShort(2)             // block align
            hdr.putShort(16)            // bits per sample
            hdr.put("data".toByteArray())
            hdr.putInt(dataSize)
            fos.write(hdr.array())

            // Convert float32 [-1,1] → int16 and write
            val dataBuf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (f in samples) {
                dataBuf.putShort((f.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort())
            }
            fos.write(dataBuf.array())
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
    }
}
