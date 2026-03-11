package com.example.videoexif.presentation.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import com.example.videoexif.domain.model.LocationPoint
import com.example.videoexif.domain.model.VideoData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

class PlaybackViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    fun init(videoData: VideoData) {
        try {
            val gpxContent = videoData.gpxFile.readText()
            val (startTime, points) = parseGpxContent(gpxContent)
            _state.update { it.copy(
                startTime = startTime,
                locationPoints = points
            ) }
        } catch (e: Exception) {
            Log.e("PlaybackViewModel", "Error loading GPX", e)
        }
    }

    fun onIntent(intent: PlaybackIntent) {
        when (intent) {
            is PlaybackIntent.UpdatePosition -> updatePosition(intent.position)
        }
    }

    private fun updatePosition(position: Long) {
        val targetTime = _state.value.startTime + position
        val closest = findClosestLocation(_state.value.locationPoints, targetTime)
        _state.update { it.copy(
            currentPosition = position,
            currentLocation = closest
        ) }
    }

    private fun findClosestLocation(points: List<LocationPoint>, targetTime: Long): LocationPoint? {
        if (points.isEmpty()) return null
        var left = 0
        var right = points.size - 1
        while (left <= right) {
            val mid = (left + right) / 2
            val midTime = points[mid].timestamp
            when {
                midTime < targetTime -> left = mid + 1
                midTime > targetTime -> right = mid - 1
                else -> return points[mid]
            }
        }
        val after = if (left < points.size) points[left] else null
        val before = if (left - 1 >= 0) points[left - 1] else null
        return when {
            before == null -> after
            after == null -> before
            else -> if (targetTime - before.timestamp <= after.timestamp - targetTime) before else after
        }
    }

    private fun parseGpxContent(gpxContent: String): Pair<Long, List<LocationPoint>> {
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val locationPoints = mutableListOf<LocationPoint>()
        var startTime = 0L
        try {
            val parser = XmlPullParserFactory.newInstance().newPullParser()
            parser.setInput(gpxContent.reader())
            var eventType = parser.eventType
            var currentLat = 0.0
            var currentLon = 0.0
            var currentTime: Long = 0
            var currentOffset: Long? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "trkpt" -> {
                                currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                                currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                                currentOffset = null
                                currentTime = 0
                            }
                            "time" -> {
                                val text = parser.nextText()
                                currentTime = try { dateFormat.parse(text)?.time ?: 0 } catch (e: Exception) { 0 }
                                if (startTime == 0L && currentTime > 0) startTime = currentTime
                            }
                            "video:offset" -> currentOffset = parser.nextText().toLongOrNull()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "trkpt") {
                            val timestamp = when {
                                currentOffset != null && startTime > 0 -> startTime + currentOffset
                                currentTime > 0 -> currentTime
                                else -> 0L
                            }
                            if (timestamp > 0) {
                                locationPoints.add(LocationPoint(timestamp, currentLat, currentLon))
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PlaybackViewModel", "Error parsing GPX", e)
        }
        return startTime to locationPoints
    }
}
