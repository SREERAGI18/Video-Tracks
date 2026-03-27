package com.example.videoexif.presentation.sync

import com.example.videoexif.domain.model.VideoData

sealed class SyncIntent {
    object LoadVideos : SyncIntent()
    data class UploadAll(val videoData: VideoData) : SyncIntent()
}

data class SyncState(
    val syncedVideos: List<VideoData> = emptyList(),
    val unsyncedVideos: List<VideoData> = emptyList(),
    val isLoading: Boolean = false,
    val isUploading: Boolean = false,
    val error: String? = null
)

sealed class SyncEffect {
    data class ShowToast(val message: String) : SyncEffect()
}
