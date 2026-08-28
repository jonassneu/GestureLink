package de.gesturelink.app.vision

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate as MpDelegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizer
import com.google.mediapipe.tasks.vision.gesturerecognizer.GestureRecognizerResult
import de.gesturelink.app.core.HandFrame
import de.gesturelink.app.core.HandObservation
import de.gesturelink.app.core.HandSide
import de.gesturelink.app.core.HandTracker
import de.gesturelink.app.core.Landmark
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class MediaPipeHandRecognizer(
    context: Context,
    delegate: Delegate = Delegate.CPU,
    private val swapHandedness: Boolean = false,
) : HandRecognizer {
    enum class Delegate { CPU, GPU }

    private data class Pending(
        val image: MPImage,
        val startedAt: Long,
        val width: Int,
        val height: Int,
        val callback: (HandFrame) -> Unit,
    )

    private val inFlight = AtomicBoolean(false)
    private val pending = ConcurrentHashMap<Long, Pending>()
    private val tracker = HandTracker()
    private val recognizer: GestureRecognizer

    init {
        val base = BaseOptions.builder()
            .setModelAssetPath("gesture_recognizer.task")
            .setDelegate(if (delegate == Delegate.GPU) MpDelegate.GPU else MpDelegate.CPU)
            .build()
        val options = GestureRecognizer.GestureRecognizerOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumHands(2)
            .setMinHandDetectionConfidence(0.55f)
            .setMinHandPresenceConfidence(0.55f)
            .setMinTrackingConfidence(0.55f)
            .setResultListener(::onResult)
            .setErrorListener { clearPending() }
            .build()
        recognizer = GestureRecognizer.createFromOptions(context, options)
    }

    override fun recognize(bitmap: Bitmap, timestampMs: Long, onResult: (HandFrame) -> Unit): Boolean {
        if (!inFlight.compareAndSet(false, true)) return false
        val image = BitmapImageBuilder(bitmap).build()
        pending[timestampMs] = Pending(image, SystemClock.elapsedRealtime(), bitmap.width, bitmap.height, onResult)
        return try {
            recognizer.recognizeAsync(image, timestampMs)
            true
        } catch (error: Throwable) {
            pending.remove(timestampMs)?.image?.close()
            inFlight.set(false)
            false
        }
    }

    private fun onResult(result: GestureRecognizerResult, input: MPImage) {
        val timestamp = result.timestampMs()
        val item = pending.remove(timestamp)
        try {
            if (item == null) return
            val observations = result.landmarks().indices.mapNotNull { index ->
                val landmarks = result.landmarks().getOrNull(index)?.map { Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }
                    ?: return@mapNotNull null
                if (landmarks.size < 21) return@mapNotNull null
                val world = result.worldLandmarks().getOrNull(index)?.map { Landmark(it.x().toDouble(), it.y().toDouble(), it.z().toDouble()) }.orEmpty()
                val handedness = result.handedness().getOrNull(index)?.firstOrNull()
                val rawRight = handedness?.categoryName() == "Right"
                // MediaPipe handedness assumes mirrored selfie input; CameraX analysis is unmirrored.
                val shouldSwap = !swapHandedness
                val isRight = if (shouldSwap) !rawRight else rawRight
                val gesture = result.gestures().getOrNull(index)?.firstOrNull()
                HandObservation(
                    landmarks = landmarks,
                    worldLandmarks = world,
                    side = if (isRight) HandSide.RIGHT else HandSide.LEFT,
                    handednessConfidence = handedness?.score()?.toDouble() ?: 0.0,
                    gesture = gesture?.categoryName() ?: "None",
                    gestureConfidence = gesture?.score()?.toDouble() ?: 0.0,
                    timestampMs = timestamp,
                )
            }
            val tracked = tracker.update(observations, timestamp)
            item.callback(
                HandFrame(
                    hands = tracked,
                    timestampMs = timestamp,
                    sourceWidth = item.width,
                    sourceHeight = item.height,
                    inferenceMs = (SystemClock.elapsedRealtime() - item.startedAt).toDouble(),
                ),
            )
        } finally {
            item?.image?.close() ?: input.close()
            inFlight.set(false)
        }
    }

    private fun clearPending() {
        pending.values.forEach { it.image.close() }
        pending.clear()
        inFlight.set(false)
    }

    override fun close() {
        clearPending()
        tracker.reset()
        recognizer.close()
    }
}
