package de.gesturelink.app.core

import kotlin.math.hypot

data class Vec2(val x: Double, val y: Double) {
    operator fun plus(other: Vec2) = Vec2(x + other.x, y + other.y)
    operator fun minus(other: Vec2) = Vec2(x - other.x, y - other.y)
    operator fun times(scale: Double) = Vec2(x * scale, y * scale)
    fun distanceTo(other: Vec2) = hypot(x - other.x, y - other.y)
}

data class Landmark(val x: Double, val y: Double, val z: Double = 0.0)

enum class HandSide { LEFT, RIGHT }

data class HandObservation(
    val landmarks: List<Landmark>,
    val worldLandmarks: List<Landmark> = emptyList(),
    val side: HandSide,
    val handednessConfidence: Double,
    val gesture: String,
    val gestureConfidence: Double,
    val timestampMs: Long,
    val trackId: Int = -1,
) {
    val palmCenter: Vec2 get() = HandMetrics.palmCenter(landmarks)
    val palmWidth: Double get() = HandMetrics.palmWidth(landmarks)
    val spreadRaw: Double get() = HandMetrics.fingerSpread(landmarks)
    val pinchRatio: Double get() = if (palmWidth > 1e-6) {
        HandMetrics.distance(landmarks[4], landmarks[8]) / palmWidth
    } else Double.POSITIVE_INFINITY
}

data class HandFrame(
    val hands: List<HandObservation>,
    val timestampMs: Long,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val inferenceMs: Double,
)

