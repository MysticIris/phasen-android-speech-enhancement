package com.example.headphonecontroller.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AudioRecorder(
    private val context: Context
) {
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    fun isRecording(): Boolean = recorder != null

    fun start(): File {
        check(recorder == null) { "Recorder is already running." }

        val outputDir = File(context.getExternalFilesDir(null), "recordings").apply {
            mkdirs()
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outputFile = File(outputDir, "recording_$timestamp.m4a")

        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128_000)
            setAudioSamplingRate(44_100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }

        recorder = mediaRecorder
        currentFile = outputFile
        return outputFile
    }

    fun stop(): File? {
        val activeRecorder = recorder ?: return currentFile
        return try {
            activeRecorder.stop()
            currentFile
        } catch (_: RuntimeException) {
            currentFile?.delete()
            null
        } finally {
            activeRecorder.reset()
            activeRecorder.release()
            recorder = null
            currentFile = null
        }
    }

    fun release() {
        recorder?.runCatching {
            reset()
            release()
        }
        recorder = null
        currentFile = null
    }
}
