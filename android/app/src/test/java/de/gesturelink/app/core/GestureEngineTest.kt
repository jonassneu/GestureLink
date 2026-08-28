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

    @Test fun rightFistHoldsRightButtonUntilOpen() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.5, "Open_Palm", 0)))
        engine.update(frame(120, hand(0.5, "Open_Palm", 120)))
        engine.update(frame(150, hand(0.5, "Closed_Fist", 150)))
        engine.update(frame(260, hand(0.5, "Closed_Fist", 260)))
        val down = engine.update(frame(360, hand(0.5, "Closed_Fist", 360)))
        assertTrue(down.contains(ControlEvent.SetButton(MouseButton.RIGHT, true)))
        assertEquals(0, engine.update(frame(480, hand(0.5, "Closed_Fist", 480))).count { it is ControlEvent.SetButton })
        engine.update(frame(520, hand(0.5, "Open_Palm", 520)))
        val up = engine.update(frame(640, hand(0.5, "Open_Palm", 640)))
        assertTrue(up.contains(ControlEvent.SetButton(MouseButton.RIGHT, false)))
    }

    @Test fun leftFistHoldsLeftButtonUntilOpen() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.5, "Closed_Fist", 0, HandSide.LEFT)))
        engine.update(frame(120, hand(0.5, "Closed_Fist", 120, HandSide.LEFT)))
        val down = engine.update(frame(220, hand(0.5, "Closed_Fist", 220, HandSide.LEFT)))
        assertTrue(down.contains(ControlEvent.SetButton(MouseButton.LEFT, true)))
        assertEquals(0, engine.update(frame(600, hand(0.5, "Closed_Fist", 600, HandSide.LEFT))).count { it is ControlEvent.SetButton })
        engine.update(frame(640, hand(0.5, "Open_Palm", 640, HandSide.LEFT)))
        val up = engine.update(frame(760, hand(0.5, "Open_Palm", 760, HandSide.LEFT)))
        assertTrue(up.contains(ControlEvent.SetButton(MouseButton.LEFT, false)))
    }

    @Test fun heldButtonSurvivesBriefRecognitionGlitch() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.5, "Closed_Fist", 0, HandSide.LEFT)))
        engine.update(frame(120, hand(0.5, "Closed_Fist", 120, HandSide.LEFT)))
        engine.update(frame(220, hand(0.5, "Closed_Fist", 220, HandSide.LEFT)))
        engine.update(frame(250, hand(0.5, "None", 250, HandSide.LEFT)))
        val glitch = engine.update(frame(370, hand(0.5, "None", 370, HandSide.LEFT)))
        assertEquals(0, glitch.count { it == ControlEvent.SetButton(MouseButton.LEFT, false) })
        engine.update(frame(400, hand(0.5, "Open_Palm", 400, HandSide.LEFT)))
        val open = engine.update(frame(520, hand(0.5, "Open_Palm", 520, HandSide.LEFT)))
        assertTrue(open.contains(ControlEvent.SetButton(MouseButton.LEFT, false)))
    }

    @Test fun twoHandsChangingDistanceProduceZoomWithoutClicks() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.45, "Open_Palm", 0, HandSide.LEFT), hand(0.55, "Open_Palm", 0, HandSide.RIGHT)))
        engine.update(frame(120, hand(0.45, "Open_Palm", 120, HandSide.LEFT), hand(0.55, "Open_Palm", 120, HandSide.RIGHT)))
        val result = engine.update(frame(240, hand(0.35, "Open_Palm", 240, HandSide.LEFT), hand(0.65, "Open_Palm", 240, HandSide.RIGHT)))
        assertTrue(result.contains(ControlEvent.Semantic("zoomIn")))
        assertEquals(0, result.count { it is ControlEvent.SetButton })
    }

    @Test fun smallTwoHandJitterDoesNotZoom() {
        val engine = GestureEngine(settings)
        engine.setArmed(true)
        engine.update(frame(0, hand(0.40, "Open_Palm", 0, HandSide.LEFT), hand(0.60, "Open_Palm", 0, HandSide.RIGHT)))
        engine.update(frame(120, hand(0.40, "Open_Palm", 120, HandSide.LEFT), hand(0.60, "Open_Palm", 120, HandSide.RIGHT)))
        val result = engine.update(frame(260, hand(0.398, "Open_Palm", 260, HandSide.LEFT), hand(0.602, "Open_Palm", 260, HandSide.RIGHT)))
        assertEquals(0, result.count { it is ControlEvent.Semantic })
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
