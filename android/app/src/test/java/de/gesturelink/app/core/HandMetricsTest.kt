package de.gesturelink.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandMetricsTest {
    @Test fun palmCenterUsesStablePalmLandmarks() {
        val points = MutableList(21) { Landmark(0.0, 0.0) }
        points[0] = Landmark(0.4, 0.6)
        points[5] = Landmark(0.4, 0.4)
        points[9] = Landmark(0.5, 0.4)
        points[13] = Landmark(0.6, 0.4)
        points[17] = Landmark(0.6, 0.6)
        val center = HandMetrics.palmCenter(points)
        assertEquals(0.5, center.x, 0.02)
        assertEquals(0.47, center.y, 0.03)
    }

    @Test fun palmWidthAndSpreadAreScaleInvariant() {
        val small = openHand(scale = 0.2)
        val large = openHand(scale = 0.4)
        assertEquals(0.2, HandMetrics.palmWidth(small), 1e-8)
        assertEquals(HandMetrics.fingerSpread(small), HandMetrics.fingerSpread(large), 1e-8)
    }

    @Test fun spreadNormalizationAndClutchAreBounded() {
        assertEquals(0.0, HandMetrics.normalizedSpread(0.2, 0.4, 1.4), 0.0)
        assertEquals(1.0, HandMetrics.normalizedSpread(2.0, 0.4, 1.4), 0.0)
        assertEquals(0.0, HandMetrics.gain(0.12, 0.3, 2.0, 0.12), 0.0)
        val mid = HandMetrics.gain(0.56, 0.3, 2.0, 0.12)
        assertTrue(mid in 0.3..2.0)
    }

    private fun openHand(scale: Double): List<Landmark> {
        val p = MutableList(21) { Landmark(0.5, 0.7) }
        p[5] = Landmark(0.4, 0.55)
        p[17] = Landmark(0.6, 0.55)
        p[8] = Landmark(0.3, 0.2)
        p[12] = Landmark(0.45, 0.15)
        p[16] = Landmark(0.6, 0.17)
        p[20] = Landmark(0.75, 0.25)
        p[4] = Landmark(0.2, 0.45)
        return p.map { Landmark(0.5 + (it.x - 0.5) * (scale / 0.2), 0.7 + (it.y - 0.7) * (scale / 0.2)) }
    }
}
