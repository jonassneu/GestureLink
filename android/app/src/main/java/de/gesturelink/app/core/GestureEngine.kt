package de.gesturelink.app.core

import kotlin.math.abs
import kotlin.math.ln

sealed interface ControlEvent {
    data class PointerDelta(val dx: Double, val dy: Double, val confidence: Double) : ControlEvent
    data class Click(val button: MouseButton) : ControlEvent
    data class SetButton(val button: MouseButton, val down: Boolean) : ControlEvent
    data class Scroll(val vertical: Double, val horizontal: Double = 0.0) : ControlEvent
    data class Semantic(val action: String) : ControlEvent
    data class State(val side: HandSide, val gesture: String, val spread01: Double) : ControlEvent
    data object PauseToggle : ControlEvent
    data object ReleaseAll : ControlEvent
}

enum class MouseButton { LEFT, RIGHT, MIDDLE }

class GestureEngine(private var settings: GestureSettings = GestureSettings()) {
    private data class StableGesture(
        var candidate: String = "None",
        var candidateSince: Long = 0,
        var active: String = "None",
    )

    private data class HandState(
        val stable: StableGesture = StableGesture(),
        val xFilter: OneEuroFilter = OneEuroFilter(),
        val yFilter: OneEuroFilter = OneEuroFilter(),
        val spreadFilter: OneEuroFilter = OneEuroFilter(0.8, 0.08),
        var anchor: Vec2? = null,
        var fistSince: Long? = null,
        var heldButton: MouseButton? = null,
        var dragActive: Boolean = false,
        var lastClick: Long = Long.MIN_VALUE / 2,
        var lastSeen: Long = 0,
    )

    private val states = HandSide.entries.associateWith { HandState() }
    private val zoomFilter = OneEuroFilter(minCutoff = 0.7, beta = 0.04)
    private var zoomActive = false
    private var zoomDistance = 0.0
    private var zoomAccumulator = 0.0
    private var lastZoomEmitMs = Long.MIN_VALUE / 2
    private var pauseSince: Long? = null
    private var pauseCooldownUntil = 0L
    private var previousPauseCenters: Pair<Vec2, Vec2>? = null
    var armed: Boolean = false
        private set

    fun setArmed(value: Boolean): List<ControlEvent> {
        if (armed == value) return emptyList()
        armed = value
        resetBaselines()
        return if (value) emptyList() else releaseAll()
    }

    fun update(frame: HandFrame): List<ControlEvent> {
        val now = frame.timestampMs
        val events = mutableListOf<ControlEvent>()
        val bySide = frame.hands.groupBy { it.side }.mapValues { (_, hands) ->
            hands.maxBy { it.handednessConfidence * it.gestureConfidence }
        }

        for (side in HandSide.entries) {
            val hand = bySide[side]
            val state = states.getValue(side)
            if (hand == null) {
                if (state.heldButton != null && now - state.lastSeen >= settings.handLossGraceMs) {
                    events += ControlEvent.SetButton(state.heldButton!!, false)
                    state.heldButton = null
                }
                state.anchor = null
                continue
            }
            state.lastSeen = now
            updateStable(state.stable, hand.gesture, hand.gestureConfidence, now)
            val spread01 = state.spreadFilter.filter(
                HandMetrics.normalizedSpread(hand.spreadRaw, settings.spreadTogether, settings.spreadWide),
                now / 1000.0,
            )
            events += ControlEvent.State(side, state.stable.active, spread01)
        }

        if (!armed) return events
        if (handleZoom(bySide, now, events)) return events

        val right = bySide[HandSide.RIGHT]
        val left = bySide[HandSide.LEFT]
        right?.let { handleDominant(it, states.getValue(HandSide.RIGHT), now, events) }
        left?.let { handleSecondary(it, states.getValue(HandSide.LEFT), now, events, cursorFallback = right == null) }
        return events
    }

    fun onDisconnect(): List<ControlEvent> {
        armed = false
        return releaseAll()
    }

    fun updateSettings(value: GestureSettings): List<ControlEvent> {
        settings = value
        resetBaselines()
        return releaseAll()
    }

    private fun handleDominant(hand: HandObservation, state: HandState, now: Long, out: MutableList<ControlEvent>) {
        when (state.stable.active) {
            "Closed_Fist" -> {
                val fistStart = state.fistSince ?: now.also { state.fistSince = it }
                if (state.heldButton == null && now - fistStart >= settings.clickMinMs) {
                    state.heldButton = MouseButton.RIGHT
                    out += ControlEvent.SetButton(MouseButton.RIGHT, true)
                }
            }
            "Open_Palm" -> {
                releaseHeldButton(state, out)
                state.fistSince = null
                val rawSpread = HandMetrics.normalizedSpread(hand.spreadRaw, settings.spreadTogether, settings.spreadWide)
                val spread = state.spreadFilter.filter(rawSpread, now / 1000.0)
                val gain = HandMetrics.gain(spread, settings.minGain, settings.maxGain, settings.clutchThreshold)
                    .coerceAtLeast(settings.minGain)
                pointer(hand, state, now, gain, out)
            }
            else -> {
                // Keep an already pressed button down through short recognition glitches.
                // It is released by an explicitly open palm, hand loss, stop or disconnect.
                if (state.heldButton == null) state.fistSince = null
            }
        }
        if (state.stable.active != "Open_Palm" && !state.dragActive) {
            pointer(hand, state, now, settings.dragGain, out)
        }
    }

    private fun handleSecondary(hand: HandObservation, state: HandState, now: Long, out: MutableList<ControlEvent>, cursorFallback: Boolean = false) {
        when (state.stable.active) {
            "Closed_Fist" -> {
                val fistStart = state.fistSince ?: now.also { state.fistSince = it }
                if (state.heldButton == null && now - fistStart >= settings.clickMinMs) {
                    state.heldButton = MouseButton.LEFT
                    out += ControlEvent.SetButton(MouseButton.LEFT, true)
                }
            }
            "Open_Palm" -> {
                releaseHeldButton(state, out)
                state.fistSince = null
                if (cursorFallback) {
                    pointer(hand, state, now, settings.dragGain, out)
                } else {
                    val palm = filteredPalm(hand, state, now)
                    val old = state.anchor
                    state.anchor = palm
                    if (old != null) {
                        val dy = palm.y - old.y
                        val dx = palm.x - old.x
                        if (abs(dy) >= settings.deadzone || abs(dx) >= settings.deadzone) {
                            val spread = HandMetrics.normalizedSpread(hand.spreadRaw, settings.spreadTogether, settings.spreadWide)
                            val gain = 0.4 + 0.6 * spread
                            out += ControlEvent.Scroll(-dy * settings.scrollGain * gain, dx * settings.scrollGain * gain)
                        }
                    }
                }
            }
            else -> {
                // Keep an already pressed button down through short recognition glitches.
                // It is released by an explicitly open palm, hand loss, stop or disconnect.
                if (state.heldButton == null) state.fistSince = null
            }
        }
        if (cursorFallback && state.stable.active != "Open_Palm") pointer(hand, state, now, settings.dragGain, out)
    }

    private fun releaseHeldButton(state: HandState, out: MutableList<ControlEvent>) {
        state.heldButton?.let { out += ControlEvent.SetButton(it, false) }
        state.heldButton = null
    }

    private fun pointer(hand: HandObservation, state: HandState, now: Long, gain: Double, out: MutableList<ControlEvent>) {
        val palm = filteredPalm(hand, state, now)
        val old = state.anchor
        state.anchor = palm
        if (old == null || gain == 0.0 || hand.gestureConfidence < settings.stayConfidence) return
        var dx = palm.x - old.x
        var dy = palm.y - old.y
        if (abs(dx) < settings.deadzone) dx = 0.0
        if (abs(dy) < settings.deadzone) dy = 0.0
        dx = (dx * gain).coerceIn(-settings.maxDelta, settings.maxDelta)
        dy = (dy * gain).coerceIn(-settings.maxDelta, settings.maxDelta)
        // Analysis frames are unmirrored while the front preview behaves like a mirror.
        if (dx != 0.0 || dy != 0.0) out += ControlEvent.PointerDelta(-dx, dy, hand.gestureConfidence)
    }

    private fun filteredPalm(hand: HandObservation, state: HandState, now: Long): Vec2 {
        val p = hand.palmCenter
        return Vec2(state.xFilter.filter(p.x, now / 1000.0), state.yFilter.filter(p.y, now / 1000.0))
    }

    private fun handleZoom(hands: Map<HandSide, HandObservation>, now: Long, out: MutableList<ControlEvent>): Boolean {
        val left = hands[HandSide.LEFT]
        val right = hands[HandSide.RIGHT]
        if (left != null && right != null &&
            states.getValue(HandSide.LEFT).stable.active == "Open_Palm" &&
            states.getValue(HandSide.RIGHT).stable.active == "Open_Palm"
        ) {
            val rawDistance = left.palmCenter.distanceTo(right.palmCenter).coerceAtLeast(0.04)
            if (!zoomActive) {
                states.values.forEach { releaseHeldButton(it, out) }
                zoomFilter.reset()
                zoomActive = true
                zoomDistance = zoomFilter.filter(rawDistance, now / 1000.0)
                zoomAccumulator = 0.0
                lastZoomEmitMs = now
                states.values.forEach { it.anchor = null }
            } else {
                val distance = zoomFilter.filter(rawDistance, now / 1000.0)
                val change = ln(distance / zoomDistance.coerceAtLeast(0.04)).coerceIn(-0.12, 0.12)
                zoomDistance = distance
                zoomAccumulator = (zoomAccumulator + change).coerceIn(-0.35, 0.35)
                val step = 0.055
                if (now - lastZoomEmitMs >= 120 && abs(zoomAccumulator) >= step) {
                    val direction = if (zoomAccumulator > 0) 1.0 else -1.0
                    out += ControlEvent.Semantic(if (direction > 0) "zoomIn" else "zoomOut")
                    zoomAccumulator -= direction * step
                    lastZoomEmitMs = now
                }
            }
        } else if (zoomActive) {
            zoomActive = false
            zoomFilter.reset()
            zoomAccumulator = 0.0
            states.values.forEach { it.anchor = null }
        }
        return zoomActive
    }

    private fun handlePause(hands: Map<HandSide, HandObservation>, now: Long, out: MutableList<ControlEvent>): Boolean {
        val left = hands[HandSide.LEFT]
        val right = hands[HandSide.RIGHT]
        val bothWideOpen = left?.gesture == "Open_Palm" && right?.gesture == "Open_Palm" &&
            HandMetrics.normalizedSpread(left.spreadRaw, settings.spreadTogether, settings.spreadWide) > 0.72 &&
            HandMetrics.normalizedSpread(right.spreadRaw, settings.spreadTogether, settings.spreadWide) > 0.72
        if (!bothWideOpen || now < pauseCooldownUntil) {
            pauseSince = null
            previousPauseCenters = null
            return false
        }
        val centers = left!!.palmCenter to right!!.palmCenter
        val old = previousPauseCenters
        previousPauseCenters = centers
        val still = old == null || (centers.first.distanceTo(old.first) + centers.second.distanceTo(old.second)) < 0.018
        if (!still) pauseSince = now
        pauseSince = pauseSince ?: now
        if (now - pauseSince!! >= settings.pauseDwellMs) {
            out += releaseAll()
            armed = !armed
            out += ControlEvent.PauseToggle
            pauseSince = null
            pauseCooldownUntil = now + settings.pauseCooldownMs
            resetBaselines()
        }
        return true
    }

    private fun updateStable(state: StableGesture, label: String, confidence: Double, now: Long) {
        val required = if (label == state.active) settings.stayConfidence else settings.enterConfidence
        val accepted = if (confidence >= required) label else "None"
        if (accepted != state.candidate) {
            state.candidate = accepted
            state.candidateSince = now
        }
        if (accepted != state.active && now - state.candidateSince >= settings.stabilizationMs) {
            state.active = accepted
        }
    }

    private fun releaseAll(): List<ControlEvent> {
        states.values.forEach { it.dragActive = false; it.heldButton = null }
        resetBaselines()
        return listOf(ControlEvent.ReleaseAll)
    }

    private fun resetBaselines() {
        zoomActive = false
        zoomFilter.reset()
        zoomAccumulator = 0.0
        pauseSince = null
        states.values.forEach(::resetHand)
    }

    private fun resetHand(state: HandState) {
        state.anchor = null
        state.fistSince = null
        state.heldButton = null
        state.xFilter.reset()
        state.yFilter.reset()
        state.spreadFilter.reset()
    }
}
