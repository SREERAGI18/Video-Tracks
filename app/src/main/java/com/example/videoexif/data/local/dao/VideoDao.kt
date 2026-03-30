package com.example.videoexif.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.videoexif.data.local.entity.VideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM videos ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<VideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: VideoEntity)

    @Query("UPDATE videos SET isSynced = :isSynced WHERE videoPath = :videoPath")
    suspend fun updateSyncStatus(videoPath: String, isSynced: Boolean)

    @Query("DELETE FROM videos WHERE videoPath = :videoPath")
    suspend fun deleteVideo(videoPath: String)
}
