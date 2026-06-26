package com.example.headphonecontroller.audio

import android.media.MediaPlayer
import java.io.File

class AudioPlayer(
    private val onPlaybackCompleted: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying == true

    fun play(file: File) {
        stop()
        val player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                releasePlayer(fromCompletion = true)
                onPlaybackCompleted()
            }
            prepare()
            start()
        }
        mediaPlayer = player
    }

    fun stop() {
        releasePlayer(fromCompletion = false)
    }

    fun release() {
        releasePlayer(fromCompletion = false)
    }

    private fun releasePlayer(fromCompletion: Boolean) {
        val player = mediaPlayer ?: return
        if (!fromCompletion) {
            runCatching {
                if (player.isPlaying) {
                    player.stop()
                }
            }
        }
        runCatching { player.reset() }
        runCatching { player.release() }
        mediaPlayer = null
    }
}
