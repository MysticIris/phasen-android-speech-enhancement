package com.example.headphonecontroller.data

import java.io.File

data class RecordingItem(
    val file: File,
    val fileName: String,
    val timestamp: Long,
    val sizeBytes: Long,
    val enhancedFile: File? = null
)
