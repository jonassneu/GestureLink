package de.gesturelink.app.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import de.gesturelink.app.core.HandFrame
import java.util.concurrent.atomic.AtomicLong

class CameraAnalyzer(
    private val recognizerProvider: () -> HandRecognizer?,
    private val onFrame: (HandFrame) -> Unit,
    private val onAccepted: () -> Unit = {},
    private val onDropped: () -> Unit = {},
) : ImageAnalysis.Analyzer {
    private val lastTimestamp = AtomicLong(0)

    override fun analyze(image: ImageProxy) {
        try {
            val recognizer = recognizerProvider() ?: run { onDropped(); return }
            val raw = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            val bitmap = if (rotation == 0) raw else {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also {
                    if (it !== raw) raw.recycle()
                }
            }
            var timestamp = SystemClock.elapsedRealtime()
            val previous = lastTimestamp.get()
            if (timestamp <= previous) timestamp = previous + 1
            lastTimestamp.set(timestamp)
            if (recognizer.recognize(bitmap, timestamp) { frame ->
                    try { onFrame(frame) } finally { bitmap.recycle() }
                }) onAccepted() else {
                bitmap.recycle()
                onDropped()
            }
        } catch (_: Throwable) {
            onDropped()
        } finally {
            image.close()
        }
    }
}
