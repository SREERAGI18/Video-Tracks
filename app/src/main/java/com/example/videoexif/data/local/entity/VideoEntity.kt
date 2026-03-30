package com.example.videoexif.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "videos")
data class VideoEntity(
    @PrimaryKey val videoPath: String,
    val videoName: String,
    val gpxPath: String,
    val srtPath: String?,
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
