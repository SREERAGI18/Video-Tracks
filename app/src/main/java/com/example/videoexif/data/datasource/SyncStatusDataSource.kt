package com.example.videoexif.data.datasource

import android.content.Context
import android.content.SharedPreferences

class SyncStatusDataSource(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
    private val KEY_SYNCED_VIDEOS = "synced_videos"

    fun markAsSynced(videoName: String) {
        val synced = getSyncedVideos().toMutableSet()
        synced.add(videoName)
        prefs.edit().putStringSet(KEY_SYNCED_VIDEOS, synced).apply()
    }

    fun isSynced(videoName: String): Boolean {
        return getSyncedVideos().contains(videoName)
    }

    fun getSyncedVideos(): Set<String> {
        return prefs.getStringSet(KEY_SYNCED_VIDEOS, emptySet()) ?: emptySet()
    }
    
    fun removeFromSync(videoName: String) {
        val synced = getSyncedVideos().toMutableSet()
        synced.remove(videoName)
        prefs.edit().putStringSet(KEY_SYNCED_VIDEOS, synced).apply()
    }
}
