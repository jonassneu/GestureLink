package de.gesturelink.app.core

import kotlin.math.hypot

object HandMetrics {
    private val palmIndices = intArrayOf(0, 5, 9, 13, 17)
    private val palmWeights = doubleArrayOf(1.0, 1.2, 1.4, 1.2, 1.0)

    fun palmCenter(landmarks: List<Landmark>): Vec2 {
        require(landmarks.size >= 21) { "21 landmarks required" }
        val weight = palmWeights.sum()
        return Vec2(
            palmIndices.indices.sumOf { palmWeights[it] * landmarks[palmIndices[it]].x } / weight,
            palmIndices.indices.sumOf { palmWeights[it] * landmarks[palmIndices[it]].y } / weight,
        )
    }

    fun palmWidth(landmarks: List<Landmark>): Double {
        require(landmarks.size >= 21) { "21 landmarks required" }
        return distance(landmarks[5], landmarks[17])
    }

    fun fingerSpread(landmarks: List<Landmark>): Double {
        val width = palmWidth(landmarks)
        if (width <= 1e-6) return 0.0
        val distances = doubleArrayOf(
            distance(landmarks[8], landmarks[12]),
            distance(landmarks[12], landmarks[16]),
            distance(landmarks[16], landmarks[20]),
            0.75 * distance(landmarks[4], landmarks[8]),
        )
        return distances.average() / width
    }

    fun normalizedSpread(raw: Double, together: Double, wide: Double): Double {
        if (wide - together < 0.05) return 0.0
        return ((raw - together) / (wide - together)).coerceIn(0.0, 1.0)
    }

    fun gain(spread01: Double, minGain: Double, maxGain: Double, clutch: Double): Double {
        if (spread01 <= clutch) return 0.0
        val t = ((spread01 - clutch) / (1.0 - clutch)).coerceIn(0.0, 1.0)
        val smooth = t * t * (3.0 - 2.0 * t)
        return minGain + (maxGain - minGain) * smooth
    }

    fun distance(a: Landmark, b: Landmark): Double = hypot(a.x - b.x, a.y - b.y)
}

