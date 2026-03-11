package com.example.videoexif.presentation.record

import android.graphics.SurfaceTexture
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoexif.domain.repository.VideoRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class RecordViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RecordState())
    val state: StateFlow<RecordState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<RecordEffect>()
    val effect: SharedFlow<RecordEffect> = _effect.asSharedFlow()

    private var locationJob: Job? = null

    init {
        repository.startBackgroundThread()
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopBackgroundThread()
        repository.closeCamera()
    }

    fun onIntent(intent: RecordIntent) {
        when (intent) {
            is RecordIntent.StartPreview -> startPreview(intent.surfaceTexture)
            is RecordIntent.ToggleRecording -> toggleRecording(intent.surfaceTexture)
            is RecordIntent.StopPreview -> repository.closeCamera()
        }
    }

    private fun startPreview(surfaceTexture: SurfaceTexture) {
        repository.openCamera {
            repository.startPreview(surfaceTexture)
        }
    }

    private fun toggleRecording(surfaceTexture: SurfaceTexture) {
        if (_state.value.isRecording) {
            stopRecording(surfaceTexture)
        } else {
            startRecording(surfaceTexture)
        }
    }

    private fun startRecording(surfaceTexture: SurfaceTexture) {
        viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                repository.startRecording(surfaceTexture)
                
                _state.update { it.copy(
                    isRecording = true,
                    recordingStartTime = startTime,
                    locationPoints = emptyList()
                ) }

                locationJob = repository.getLocationUpdates(1000)
                    .onEach { locationPoint ->
                        _state.update { it.copy(
                            locationPoints = it.locationPoints + locationPoint
                        ) }
                    }
                    .launchIn(viewModelScope)

            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                _effect.emit(RecordEffect.ShowToast("Failed to start recording: ${e.message}"))
            }
        }
    }

    private fun stopRecording(surfaceTexture: SurfaceTexture) {
        viewModelScope.launch {
            try {
                locationJob?.cancel()
                repository.stopRecording(_state.value.recordingStartTime, _state.value.locationPoints)
                
                _state.update { it.copy(isRecording = false) }
                repository.startPreview(surfaceTexture)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
                _effect.emit(RecordEffect.ShowToast("Failed to stop recording: ${e.message}"))
            }
        }
    }
}
