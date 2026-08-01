package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AudioClipEntity
import com.example.data.VoiceOption
import com.example.service.TtsManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class TtsUiState(
    val inputText: String = "Hello! Welcome to AI Text to Speech. Type any text here, customize the voice pitch and speed, and tap Generate Voice to listen or download.",
    val selectedVoice: VoiceOption? = null,
    val pitch: Float = 1.0f,
    val speed: Float = 1.0f,
    val isGenerating: Boolean = false,
    val generatedFile: File? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val statusMessage: String? = null,
    val selectedTab: Int = 0,
    val activePlayingClipId: Long? = null
)

class TtsViewModel(application: Application) : AndroidViewModel(application) {

    private val ttsManager = TtsManager(application.applicationContext)
    private val audioClipDao = AppDatabase.getDatabase(application).audioClipDao()

    val availableVoices: List<VoiceOption> = ttsManager.defaultVoices

    private val _uiState = MutableStateFlow(
        TtsUiState(selectedVoice = availableVoices.firstOrNull())
    )
    val uiState: StateFlow<TtsUiState> = _uiState.asStateFlow()

    val savedClips: StateFlow<List<AudioClipEntity>> = audioClipDao.getAllClips()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var playbackProgressJob: Job? = null

    fun onInputTextChanged(newText: String) {
        _uiState.value = _uiState.value.copy(inputText = newText)
    }

    fun onVoiceSelected(voice: VoiceOption) {
        _uiState.value = _uiState.value.copy(selectedVoice = voice)
    }

    fun onPitchChanged(newPitch: Float) {
        _uiState.value = _uiState.value.copy(pitch = newPitch)
    }

    fun onSpeedChanged(newSpeed: Float) {
        _uiState.value = _uiState.value.copy(speed = newSpeed)
    }

    fun onTabSelected(tabIndex: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = tabIndex)
    }

    fun clearStatusMessage() {
        _uiState.value = _uiState.value.copy(statusMessage = null)
    }

    fun selectPresetText(preset: String) {
        _uiState.value = _uiState.value.copy(inputText = preset)
    }

    fun generateVoice() {
        val currentState = _uiState.value
        val text = currentState.inputText.trim()
        val voice = currentState.selectedVoice ?: availableVoices.first()

        if (text.isBlank()) {
            _uiState.value = currentState.copy(statusMessage = "Please enter some text to generate voice.")
            return
        }

        ttsManager.stopPlayback()
        stopProgressTracker()

        _uiState.value = currentState.copy(
            isGenerating = true,
            statusMessage = "Generating speech audio...",
            isPlaying = false,
            isPaused = false,
            currentPositionMs = 0L,
            activePlayingClipId = null
        )

        viewModelScope.launch {
            val result = ttsManager.generateAudioFile(
                text = text,
                voice = voice,
                pitch = currentState.pitch,
                speed = currentState.speed
            )

            result.fold(
                onSuccess = { file ->
                    val duration = ttsManager.getAudioDuration(file)
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        generatedFile = file,
                        totalDurationMs = duration,
                        statusMessage = "Voice generated successfully!"
                    )
                    // Save to history automatically
                    saveCurrentClipToDatabase(file, text, voice, duration)
                    // Auto-start playing generated voice
                    startPlaybackForFile(file, duration)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isGenerating = false,
                        statusMessage = error.message ?: "Failed to generate speech."
                    )
                }
            )
        }
    }

    private suspend fun saveCurrentClipToDatabase(
        file: File,
        text: String,
        voice: VoiceOption,
        durationMs: Long
    ) {
        val title = if (text.length > 25) text.take(25) + "..." else text
        val entity = AudioClipEntity(
            title = title,
            text = text,
            filePath = file.absolutePath,
            durationMs = durationMs,
            pitch = _uiState.value.pitch,
            speed = _uiState.value.speed,
            voiceName = voice.name
        )
        audioClipDao.insertClip(entity)
    }

    fun togglePlayPause() {
        val currentState = _uiState.value
        val file = currentState.generatedFile ?: return

        if (currentState.isPlaying) {
            ttsManager.pausePlayback()
            stopProgressTracker()
            _uiState.value = currentState.copy(isPlaying = false, isPaused = true)
        } else if (currentState.isPaused) {
            ttsManager.resumePlayback()
            startProgressTracker()
            _uiState.value = currentState.copy(isPlaying = true, isPaused = false)
        } else {
            startPlaybackForFile(file, currentState.totalDurationMs)
        }
    }

    private fun startPlaybackForFile(file: File, durationMs: Long) {
        _uiState.value = _uiState.value.copy(
            isPlaying = true,
            isPaused = false,
            totalDurationMs = durationMs
        )
        ttsManager.startPlayback(
            file = file,
            onCompletion = {
                stopProgressTracker()
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    currentPositionMs = 0L,
                    activePlayingClipId = null
                )
            },
            onError = { error ->
                stopProgressTracker()
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    statusMessage = error,
                    activePlayingClipId = null
                )
            }
        )
        startProgressTracker()
    }

    fun seekTo(positionMs: Long) {
        ttsManager.seekTo(positionMs)
        _uiState.value = _uiState.value.copy(currentPositionMs = positionMs)
    }

    fun downloadAudio() {
        val file = _uiState.value.generatedFile ?: return
        val text = _uiState.value.inputText.ifBlank { "speech" }

        viewModelScope.launch {
            val result = ttsManager.saveAudioToDownloads(file, text)
            result.fold(
                onSuccess = { msg ->
                    _uiState.value = _uiState.value.copy(statusMessage = msg)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(statusMessage = err.message ?: "Failed to save file.")
                }
            )
        }
    }

    fun shareAudio() {
        val file = _uiState.value.generatedFile ?: return
        ttsManager.shareAudioFile(file)
    }

    fun playClip(clip: AudioClipEntity) {
        val file = File(clip.filePath)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Audio file no longer exists.")
            return
        }

        ttsManager.stopPlayback()
        stopProgressTracker()

        _uiState.value = _uiState.value.copy(
            generatedFile = file,
            inputText = clip.text,
            activePlayingClipId = clip.id,
            totalDurationMs = clip.durationMs
        )
        startPlaybackForFile(file, clip.durationMs)
    }

    fun downloadClip(clip: AudioClipEntity) {
        val file = File(clip.filePath)
        if (!file.exists()) {
            _uiState.value = _uiState.value.copy(statusMessage = "Audio file no longer exists.")
            return
        }
        viewModelScope.launch {
            val result = ttsManager.saveAudioToDownloads(file, clip.title)
            result.fold(
                onSuccess = { msg ->
                    _uiState.value = _uiState.value.copy(statusMessage = msg)
                },
                onFailure = { err ->
                    _uiState.value = _uiState.value.copy(statusMessage = err.message ?: "Failed to save file.")
                }
            )
        }
    }

    fun shareClip(clip: AudioClipEntity) {
        val file = File(clip.filePath)
        if (file.exists()) {
            ttsManager.shareAudioFile(file)
        }
    }

    fun deleteClip(clip: AudioClipEntity) {
        viewModelScope.launch {
            if (_uiState.value.activePlayingClipId == clip.id) {
                ttsManager.stopPlayback()
                stopProgressTracker()
                _uiState.value = _uiState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    activePlayingClipId = null
                )
            }
            audioClipDao.deleteClip(clip)
            try {
                File(clip.filePath).delete()
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
            _uiState.value = _uiState.value.copy(statusMessage = "Clip deleted.")
        }
    }

    private fun startProgressTracker() {
        stopProgressTracker()
        playbackProgressJob = viewModelScope.launch {
            while (isActive) {
                val pos = ttsManager.getCurrentPosition()
                _uiState.value = _uiState.value.copy(currentPositionMs = pos)
                delay(100)
            }
        }
    }

    private fun stopProgressTracker() {
        playbackProgressJob?.cancel()
        playbackProgressJob = null
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.shutdown()
    }
}
