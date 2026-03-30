package com.example.videoexif.presentation.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoexif.domain.repository.VideoRepository
import com.example.videoexif.util.NetworkUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SyncViewModel(
    private val repository: VideoRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<SyncEffect>()
    val effect: SharedFlow<SyncEffect> = _effect.asSharedFlow()

    init {
        loadVideos()
    }

    fun onIntent(intent: SyncIntent) {
        when (intent) {
            is SyncIntent.LoadVideos -> loadVideos()
            is SyncIntent.UploadAll -> uploadAll(intent)
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.getVideos()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    _effect.emit(SyncEffect.ShowToast("Error loading videos: ${e.message}"))
                }
                .collect { videos ->
                    val (synced, unsynced) = videos.partition { it.isSynced }
                    _state.update { it.copy(
                        isLoading = false,
                        syncedVideos = synced,
                        unsyncedVideos = unsynced
                    ) }
                }
        }
    }

    private fun uploadAll(intent: SyncIntent.UploadAll) {
        viewModelScope.launch {
            if (!NetworkUtils.isNetworkAvailable(context)) {
                _effect.emit(SyncEffect.ShowToast("No internet connection. Please check your network and try again."))
                return@launch
            }

            val srtFile = intent.videoData.srtFile
            if (srtFile == null) {
                _effect.emit(SyncEffect.ShowToast("SRT file not found for sync"))
                return@launch
            }
            _state.update { it.copy(isUploading = true) }
            repository.uploadAll(
                gpxFile = intent.videoData.gpxFile,
                srtFile = srtFile,
                mp4File = intent.videoData.videoFile
            ).onSuccess {
                repository.markAsSynced(intent.videoData.videoFile.name)
                _effect.emit(SyncEffect.ShowToast("Sync successful"))
                loadVideos()
            }
            .onFailure { e ->
                _effect.emit(SyncEffect.ShowToast("Sync failed: ${e.message}"))
            }
            _state.update { it.copy(isUploading = false) }
        }
    }
}
