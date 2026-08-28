package de.gesturelink.app.core

import org.junit.Assert.assertEquals
import org.junit.Test

class HandTrackerTest {
    @Test fun aSingleWrongHandednessFrameDoesNotFlipTrack() {
        val tracker = HandTracker()
        val first = tracker.update(listOf(hand(0.3, HandSide.RIGHT, 0)), 0).single()
        tracker.update(listOf(hand(0.31, HandSide.RIGHT, 20)), 20)
        val noisy = tracker.update(listOf(hand(0.32, HandSide.LEFT, 40)), 40).single()
        assertEquals(first.trackId, noisy.trackId)
        assertEquals(HandSide.RIGHT, noisy.side)
    }

    private fun hand(x: Double, side: HandSide, time: Long): HandObservation {
        val points = MutableList(21) { Landmark(x, 0.5) }
        points[5] = Landmark(x - 0.05, 0.5)
        points[17] = Landmark(x + 0.05, 0.5)
        points[8] = Landmark(x - 0.06, 0.2)
        points[12] = Landmark(x - 0.02, 0.18)
        points[16] = Landmark(x + 0.03, 0.19)
        points[20] = Landmark(x + 0.08, 0.22)
        points[4] = Landmark(x - 0.1, 0.35)
        return HandObservation(points, side = side, handednessConfidence = 0.9, gesture = "Open_Palm", gestureConfidence = 0.9, timestampMs = time)
    }
}

