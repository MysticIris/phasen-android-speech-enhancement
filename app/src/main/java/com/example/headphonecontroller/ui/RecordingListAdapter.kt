package com.example.headphonecontroller.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.TextView
import com.example.headphonecontroller.R
import com.example.headphonecontroller.data.RecordingItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingListAdapter(
    private val context: Context,
    private val items: List<RecordingItem>,
    private val onPlayClicked: (RecordingItem) -> Unit,
    private val onStopClicked: (RecordingItem) -> Unit,
    private val onDeleteClicked: (RecordingItem) -> Unit,
    private val onPlayEnhancedClicked: (RecordingItem) -> Unit = {}
) : BaseAdapter() {

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var playingFilePath: String? = null

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): RecordingItem = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_recording, parent, false)

        val item = getItem(position)
        val tvFileName = row.findViewById<TextView>(R.id.tvFileName)
        val tvMeta = row.findViewById<TextView>(R.id.tvMeta)
        val btnPlayStop = row.findViewById<Button>(R.id.btnPlayStop)
        val btnPlayEnhanced = row.findViewById<Button>(R.id.btnPlayEnhanced)
        val btnDelete = row.findViewById<Button>(R.id.btnDelete)

        tvFileName.text = item.fileName
        tvMeta.text = context.getString(
            R.string.recording_meta,
            timestampFormat.format(Date(item.timestamp)),
            formatSize(item.sizeBytes)
        )

        val isPlayingOriginal = item.file.absolutePath == playingFilePath
        btnPlayStop.text = if (isPlayingOriginal) {
            context.getString(R.string.action_stop_playback)
        } else {
            context.getString(R.string.action_play)
        }
        btnPlayStop.setOnClickListener {
            if (isPlayingOriginal) onStopClicked(item) else onPlayClicked(item)
        }

        val enhancedFile = item.enhancedFile
        if (enhancedFile != null) {
            btnPlayEnhanced.visibility = android.view.View.VISIBLE
            val isPlayingEnhanced = enhancedFile.absolutePath == playingFilePath
            btnPlayEnhanced.text = if (isPlayingEnhanced) {
                context.getString(R.string.action_stop_playback)
            } else {
                context.getString(R.string.action_play_enhanced)
            }
            btnPlayEnhanced.setOnClickListener {
                if (isPlayingEnhanced) onStopClicked(item) else onPlayEnhancedClicked(item)
            }
        } else {
            btnPlayEnhanced.visibility = android.view.View.GONE
        }

        btnDelete.setOnClickListener {
            onDeleteClicked(item)
        }

        return row
    }

    fun setPlayingFilePath(path: String?) {
        playingFilePath = path
        notifyDataSetChanged()
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024L -> context.getString(R.string.file_size_bytes, bytes)
            bytes < 1024L * 1024L -> context.getString(R.string.file_size_kb, bytes / 1024.0)
            else -> context.getString(R.string.file_size_mb, bytes / (1024.0 * 1024.0))
        }
    }
}
