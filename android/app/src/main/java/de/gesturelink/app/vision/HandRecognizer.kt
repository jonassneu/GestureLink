package de.gesturelink.app.vision

import android.graphics.Bitmap
import de.gesturelink.app.core.HandFrame

interface HandRecognizer : AutoCloseable {
    fun recognize(bitmap: Bitmap, timestampMs: Long, onResult: (HandFrame) -> Unit): Boolean
}

