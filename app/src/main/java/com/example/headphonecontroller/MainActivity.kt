package com.example.headphonecontroller

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ListView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.headphonecontroller.audio.AudioEnhancer
import com.example.headphonecontroller.audio.AudioPlayer
import com.example.headphonecontroller.audio.AudioRecorder
import com.example.headphonecontroller.audio.PcmRecorder
import com.example.headphonecontroller.data.RecordingItem
import com.example.headphonecontroller.data.RecordingRepository
import com.example.headphonecontroller.ui.RecordingListAdapter
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvStateValue: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnStartRecording: Button
    private lateinit var btnStopRecording: Button
    private lateinit var btnStopPlayback: Button
    private lateinit var switchEnhance: Switch
    private lateinit var lvRecordings: ListView

    private lateinit var listAdapter: RecordingListAdapter
    // AudioRecorder (MediaRecorder path) kept but not used for new recordings.
    private lateinit var audioRecorder: AudioRecorder
    private lateinit var pcmRecorder: PcmRecorder
    private lateinit var audioEnhancer: AudioEnhancer
    private lateinit var audioPlayer: AudioPlayer
    private lateinit var recordingRepository: RecordingRepository

    private val recordings = mutableListOf<RecordingItem>()
    private var currentState = AppState.IDLE
    private var playingFilePath: String? = null
    private var pendingStartRecording = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()

        audioRecorder = AudioRecorder(this)
        pcmRecorder = PcmRecorder(this)
        audioEnhancer = AudioEnhancer(this)
        audioPlayer = AudioPlayer(
            onPlaybackCompleted = {
                playingFilePath = null
                listAdapter.setPlayingFilePath(null)
                setState(AppState.IDLE, getString(R.string.status_playback_completed))
            }
        )
        recordingRepository = RecordingRepository.fromContext(this)

        listAdapter = RecordingListAdapter(
            context = this,
            items = recordings,
            onPlayClicked = ::playRecording,
            onStopClicked = { stopPlayback(manualAction = true) },
            onDeleteClicked = ::deleteRecording,
            onPlayEnhancedClicked = ::playEnhancedRecording
        )
        lvRecordings.adapter = listAdapter

        btnStartRecording.setOnClickListener { startRecording() }
        btnStopRecording.setOnClickListener { stopRecording() }
        btnStopPlayback.setOnClickListener { stopPlayback(manualAction = true) }

        refreshRecordings()
        setState(AppState.IDLE, getString(R.string.status_ready))
    }

    private fun bindViews() {
        tvStateValue = findViewById(R.id.tvStateValue)
        tvStatus = findViewById(R.id.tvStatus)
        btnStartRecording = findViewById(R.id.btnStartRecording)
        btnStopRecording = findViewById(R.id.btnStopRecording)
        btnStopPlayback = findViewById(R.id.btnStopPlayback)
        switchEnhance = findViewById(R.id.switchEnhance)
        lvRecordings = findViewById(R.id.lvRecordings)
    }

    private fun startRecording() {
        if (!hasRecordPermission()) {
            pendingStartRecording = true
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_CODE_RECORD_AUDIO
            )
            return
        }
        if (pcmRecorder.isRecording()) {
            return
        }

        stopPlayback(manualAction = false)

        runCatching { pcmRecorder.start() }
            .onSuccess { file ->
                setState(AppState.RECORDING, getString(R.string.status_recording, file.name))
                Toast.makeText(this, R.string.toast_recording_started, Toast.LENGTH_SHORT).show()
            }
            .onFailure {
                setState(AppState.IDLE, getString(R.string.status_recording_failed))
                Toast.makeText(this, R.string.toast_recording_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopRecording() {
        if (!pcmRecorder.isRecording()) {
            return
        }

        val savedFile = pcmRecorder.stop()
        if (savedFile != null) {
            refreshRecordings()
            setState(AppState.IDLE, getString(R.string.status_record_saved, savedFile.name))
            Toast.makeText(
                this,
                getString(R.string.toast_recording_saved, savedFile.name),
                Toast.LENGTH_SHORT
            ).show()

            if (switchEnhance.isChecked) {
                startEnhancement(savedFile)
            }
        } else {
            setState(AppState.IDLE, getString(R.string.status_recording_discarded))
            Toast.makeText(this, R.string.toast_recording_discarded, Toast.LENGTH_SHORT).show()
        }
    }

    private fun startEnhancement(file: File) {
        setState(AppState.ENHANCING, getString(R.string.status_enhancing, file.name))
        Thread {
            val result = runCatching { audioEnhancer.enhance(file) }
            runOnUiThread {
                result
                    .onSuccess { enhanced ->
                        refreshRecordings()
                        setState(
                            AppState.IDLE,
                            getString(R.string.status_enhance_done, enhanced.name)
                        )
                    }
                    .onFailure {
                        setState(AppState.IDLE, getString(R.string.status_enhance_failed))
                        Toast.makeText(
                            this,
                            R.string.status_enhance_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }.start()
    }

    private fun playRecording(item: RecordingItem) {
        if (pcmRecorder.isRecording()) {
            Toast.makeText(this, R.string.toast_stop_recording_first, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching { audioPlayer.play(item.file) }
            .onSuccess {
                playingFilePath = item.file.absolutePath
                listAdapter.setPlayingFilePath(playingFilePath)
                setState(AppState.PLAYING, getString(R.string.status_playing, item.fileName))
            }
            .onFailure {
                playingFilePath = null
                listAdapter.setPlayingFilePath(null)
                setState(AppState.IDLE, getString(R.string.status_playback_failed))
                Toast.makeText(this, R.string.toast_playback_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun playEnhancedRecording(item: RecordingItem) {
        val enhancedFile = item.enhancedFile ?: return
        if (pcmRecorder.isRecording()) {
            Toast.makeText(this, R.string.toast_stop_recording_first, Toast.LENGTH_SHORT).show()
            return
        }

        runCatching { audioPlayer.play(enhancedFile) }
            .onSuccess {
                playingFilePath = enhancedFile.absolutePath
                listAdapter.setPlayingFilePath(playingFilePath)
                setState(AppState.PLAYING, getString(R.string.status_playing, enhancedFile.name))
            }
            .onFailure {
                playingFilePath = null
                listAdapter.setPlayingFilePath(null)
                setState(AppState.IDLE, getString(R.string.status_playback_failed))
                Toast.makeText(this, R.string.toast_playback_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun stopPlayback(manualAction: Boolean) {
        val wasPlaying = audioPlayer.isPlaying()
        audioPlayer.stop()
        playingFilePath = null
        listAdapter.setPlayingFilePath(null)
        if (currentState == AppState.PLAYING || wasPlaying) {
            val message = if (manualAction) {
                getString(R.string.status_playback_stopped)
            } else {
                getString(R.string.status_ready)
            }
            setState(AppState.IDLE, message)
        }
    }

    private fun deleteRecording(item: RecordingItem) {
        if (item.file.absolutePath == playingFilePath ||
            item.enhancedFile?.absolutePath == playingFilePath) {
            stopPlayback(manualAction = false)
        }
        // Also delete the enhanced file if it exists.
        item.enhancedFile?.delete()

        val deleted = recordingRepository.delete(item)
        if (deleted) {
            refreshRecordings()
            val message = getString(R.string.status_deleted, item.fileName)
            if (pcmRecorder.isRecording()) {
                tvStatus.text = message
            } else {
                setState(AppState.IDLE, message)
            }
        } else {
            tvStatus.text = getString(R.string.status_delete_failed, item.fileName)
            Toast.makeText(this, R.string.toast_delete_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun refreshRecordings() {
        recordings.clear()
        recordings += recordingRepository.listRecordings()
        listAdapter.notifyDataSetChanged()
    }

    private fun setState(state: AppState, statusMessage: String) {
        currentState = state
        tvStateValue.text = when (state) {
            AppState.IDLE -> getString(R.string.state_idle)
            AppState.RECORDING -> getString(R.string.state_recording)
            AppState.PLAYING -> getString(R.string.state_playing)
            AppState.ENHANCING -> getString(R.string.state_enhancing)
        }
        tvStatus.text = statusMessage
        btnStartRecording.isEnabled = state == AppState.IDLE
        btnStopRecording.isEnabled = state == AppState.RECORDING
        btnStopPlayback.isEnabled = state == AppState.PLAYING
        switchEnhance.isEnabled = state == AppState.IDLE
    }

    private fun hasRecordPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_RECORD_AUDIO) {
            return
        }

        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        val shouldStart = pendingStartRecording
        pendingStartRecording = false

        if (!granted) {
            tvStatus.text = getString(R.string.status_record_permission_required)
            return
        }

        if (shouldStart) {
            startRecording()
        }
    }

    override fun onDestroy() {
        audioPlayer.release()
        audioRecorder.release()
        pcmRecorder.release()
        audioEnhancer.release()
        super.onDestroy()
    }

    private enum class AppState {
        IDLE,
        RECORDING,
        PLAYING,
        ENHANCING
    }

    companion object {
        private const val REQUEST_CODE_RECORD_AUDIO = 101
    }
}
