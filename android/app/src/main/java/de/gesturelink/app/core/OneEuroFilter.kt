package de.gesturelink.app.core

import kotlin.math.PI
import kotlin.math.abs

class OneEuroFilter(
    private val minCutoff: Double = 1.0,
    private val beta: Double = 0.12,
    private val derivativeCutoff: Double = 1.0,
) {
    private var previousValue: Double? = null
    private var previousDerivative = 0.0
    private var previousTimeSeconds: Double? = null

    fun filter(value: Double, timestampSeconds: Double): Double {
        val oldValue = previousValue
        val oldTime = previousTimeSeconds
        if (oldValue == null || oldTime == null || timestampSeconds <= oldTime) {
            previousValue = value
            previousTimeSeconds = timestampSeconds
            previousDerivative = 0.0
            return value
        }
        val dt = (timestampSeconds - oldTime).coerceIn(1.0 / 240.0, 1.0)
        val derivative = (value - oldValue) / dt
        val dAlpha = alpha(dt, derivativeCutoff)
        val filteredDerivative = dAlpha * derivative + (1.0 - dAlpha) * previousDerivative
        val cutoff = minCutoff + beta * abs(filteredDerivative)
        val valueAlpha = alpha(dt, cutoff)
        val filtered = valueAlpha * value + (1.0 - valueAlpha) * oldValue
        previousValue = filtered
        previousDerivative = filteredDerivative
        previousTimeSeconds = timestampSeconds
        return filtered
    }

    fun reset() {
        previousValue = null
        previousDerivative = 0.0
        previousTimeSeconds = null
    }

    private fun alpha(dt: Double, cutoff: Double): Double {
        val tau = 1.0 / (2.0 * PI * cutoff.coerceAtLeast(0.001))
        return 1.0 / (1.0 + tau / dt)
    }
}

