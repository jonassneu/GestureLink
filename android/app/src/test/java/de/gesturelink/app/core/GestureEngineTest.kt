package de.gesturelink.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureEngineTest {
    private val settings = GestureSettings(stabilizationMs = 100, clickMinMs = 80, clickMaxMs = 350, dragThresholdMs = 450)

    @Test fun openMovementNeedsBaselineThenProducesDelta() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.50, "Open_Palm", 0)))
        engine.update(frame(120, hand(0.50, "Open_Palm", 120)))
        val events = engine.update(frame(160, hand(0.56, "Open_Palm", 160)))
        assertTrue(events.any { it is ControlEvent.PointerDelta })
    }

    @Test fun rightFistProducesExactlyOneRightClick() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.5, "Open_Palm", 0)))
        engine.update(frame(120, hand(0.5, "Open_Palm", 120)))
        engine.update(frame(150, hand(0.5, "Closed_Fist", 150)))
        engine.update(frame(260, hand(0.5, "Closed_Fist", 260)))
        val result = engine.update(frame(360, hand(0.5, "Closed_Fist", 360)))
        assertEquals(1, result.count { it == ControlEvent.Click(MouseButton.RIGHT) })
        assertEquals(0, engine.update(frame(480, hand(0.5, "Closed_Fist", 480))).count { it is ControlEvent.Click })
    }

    @Test fun leftFistProducesExactlyOneLeftClick() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.5, "Closed_Fist", 0, HandSide.LEFT)))
        engine.update(frame(120, hand(0.5, "Closed_Fist", 120, HandSide.LEFT)))
        val result = engine.update(frame(220, hand(0.5, "Closed_Fist", 220, HandSide.LEFT)))
        assertEquals(1, result.count { it == ControlEvent.Click(MouseButton.LEFT) })
        assertEquals(0, engine.update(frame(600, hand(0.5, "Closed_Fist", 600, HandSide.LEFT))).count { it is ControlEvent.Click })
    }

    @Test fun twoHandsChangingDistanceProduceZoomWithoutClicks() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.45, "Open_Palm", 0, HandSide.LEFT), hand(0.55, "Open_Palm", 0, HandSide.RIGHT)))
        val result = engine.update(frame(120, hand(0.35, "Open_Palm", 120, HandSide.LEFT), hand(0.65, "Open_Palm", 120, HandSide.RIGHT)))
        assertTrue(result.contains(ControlEvent.Semantic("zoomIn")))
        assertEquals(0, result.count { it is ControlEvent.Click })
    }

    private fun frame(time: Long, vararg hands: HandObservation) = HandFrame(hands.toList(), time, 640, 480, 10.0)

    private fun hand(x: Double, gesture: String, time: Long, side: HandSide = HandSide.RIGHT): HandObservation {
        val points = MutableList(21) { Landmark(x, 0.55) }
        points[5] = Landmark(x - 0.07, 0.52)
        points[17] = Landmark(x + 0.07, 0.52)
        points[8] = Landmark(x - 0.10, 0.18)
        points[12] = Landmark(x - 0.03, 0.14)
        points[16] = Landmark(x + 0.05, 0.16)
        points[20] = Landmark(x + 0.13, 0.21)
        points[4] = Landmark(x - 0.16, 0.34)
        return HandObservation(points, side = side, handednessConfidence = 0.95, gesture = gesture, gestureConfidence = 0.95, timestampMs = time)
    }
}
