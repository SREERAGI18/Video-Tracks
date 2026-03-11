package com.example.videoexif.presentation.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.videoexif.domain.repository.VideoRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class GalleryViewModel(
    private val repository: VideoRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GalleryState())
    val state: StateFlow<GalleryState> = _state.asStateFlow()

    private val _effect = MutableSharedFlow<GalleryEffect>()
    val effect: SharedFlow<GalleryEffect> = _effect.asSharedFlow()

    init {
        loadVideos()
    }

    fun onIntent(intent: GalleryIntent) {
        when (intent) {
            is GalleryIntent.LoadVideos -> loadVideos()
        }
    }

    private fun loadVideos() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            repository.getVideos()
                .catch { e ->
                    _state.update { it.copy(isLoading = false, error = e.message) }
                    _effect.emit(GalleryEffect.ShowToast("Error loading videos: ${e.message}"))
                }
                .collect { videos ->
                    _state.update { it.copy(isLoading = false, videos = videos) }
                }
        }
    }
}
