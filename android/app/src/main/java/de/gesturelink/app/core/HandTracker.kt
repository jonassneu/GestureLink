package de.gesturelink.app.core

import java.util.ArrayDeque
import kotlin.math.abs

class HandTracker(private val maxMissingMs: Long = 250) {
    private data class Track(
        val id: Int,
        var center: Vec2,
        var palmWidth: Double,
        var lastSeen: Long,
        val sideHistory: ArrayDeque<HandSide>,
    )

    private val tracks = mutableListOf<Track>()
    private var nextId = 1

    fun update(observations: List<HandObservation>, timestampMs: Long): List<HandObservation> {
        tracks.removeAll { timestampMs - it.lastSeen > maxMissingMs }
        val unused = tracks.toMutableSet()
        return observations.map { observation ->
            val candidate = unused.minByOrNull { track -> matchingCost(track, observation) }
                ?.takeIf { matchingCost(it, observation) < 2.0 }
            val track = candidate ?: Track(
                id = nextId++,
                center = observation.palmCenter,
                palmWidth = observation.palmWidth,
                lastSeen = timestampMs,
                sideHistory = ArrayDeque(),
            ).also(tracks::add)
            unused.remove(track)
            track.center = observation.palmCenter
            track.palmWidth = observation.palmWidth
            track.lastSeen = timestampMs
            track.sideHistory.addLast(observation.side)
            while (track.sideHistory.size > 7) track.sideHistory.removeFirst()
            val stableSide = track.sideHistory.groupingBy { it }.eachCount().maxBy { it.value }.key
            observation.copy(trackId = track.id, side = stableSide)
        }
    }

    fun reset() = tracks.clear()

    private fun matchingCost(track: Track, observation: HandObservation): Double {
        val scale = maxOf(track.palmWidth, observation.palmWidth, 0.02)
        val position = track.center.distanceTo(observation.palmCenter) / scale
        val size = abs(track.palmWidth - observation.palmWidth) / scale
        val majority = track.sideHistory.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val sidePenalty = if (majority != null && majority != observation.side) 0.35 else 0.0
        return position + size * 0.5 + sidePenalty
    }
}

