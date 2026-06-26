package com.example.headphonecontroller.data

import android.content.Context
import java.io.File

class RecordingRepository(
    private val recordingsDir: File
) {
    fun listRecordings(): List<RecordingItem> {
        if (!recordingsDir.exists()) {
            recordingsDir.mkdirs()
        }

        return recordingsDir
            .listFiles { file ->
                file.isFile && (
                    file.extension.equals("m4a", ignoreCase = true) ||
                    (file.extension.equals("wav", ignoreCase = true) &&
                        !file.nameWithoutExtension.endsWith("_enhanced"))
                )
            }
            ?.sortedByDescending { it.lastModified() }
            ?.map { file ->
                val enhanced = if (file.extension.equals("wav", ignoreCase = true)) {
                    File(recordingsDir, "${file.nameWithoutExtension}_enhanced.wav")
                        .takeIf { it.exists() }
                } else null
                RecordingItem(
                    file = file,
                    fileName = file.name,
                    timestamp = file.lastModified(),
                    sizeBytes = file.length(),
                    enhancedFile = enhanced
                )
            }
            ?: emptyList()
    }

    fun delete(item: RecordingItem): Boolean {
        return item.file.exists() && item.file.delete()
    }

    companion object {
        fun fromContext(context: Context): RecordingRepository {
            val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
            val recordingsDir = File(baseDir, "recordings")
            return RecordingRepository(recordingsDir)
        }
    }
}
