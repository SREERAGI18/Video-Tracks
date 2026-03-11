package com.example.videoexif.presentation.gallery

import com.example.videoexif.domain.model.VideoData

sealed class GalleryIntent {
    object LoadVideos : GalleryIntent()
}

data class GalleryState(
    val videos: List<VideoData> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class GalleryEffect {
    data class ShowToast(val message: String) : GalleryEffect()
}
