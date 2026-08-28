package de.gesturelink.app.ui

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import de.gesturelink.app.GestureViewModel
import de.gesturelink.app.core.HandFrame
import de.gesturelink.app.core.HandSide
import de.gesturelink.app.vision.CameraAnalyzer
import java.util.concurrent.Executors
import kotlin.math.max

private val connections = listOf(
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    5 to 9, 9 to 10, 10 to 11, 11 to 12,
    9 to 13, 13 to 14, 14 to 15, 15 to 16,
    13 to 17, 17 to 18, 18 to 19, 19 to 20, 17 to 0,
)

@Composable
fun CameraWithOverlay(viewModel: GestureViewModel, frame: HandFrame?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        val listener = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(
                executor,
                CameraAnalyzer(
                    recognizerProvider = viewModel::recognizer,
                    onFrame = viewModel::onFrame,
                    onDropped = viewModel::onDroppedFrame,
                ),
            )
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, analysis)
        }
        future.addListener(listener, ContextCompat.getMainExecutor(context))
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdownNow()
        }
    }

    Box(modifier) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        HandOverlay(frame = frame, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun HandOverlay(frame: HandFrame?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val current = frame ?: return@Canvas
        val sourceW = current.sourceWidth.toFloat()
        val sourceH = current.sourceHeight.toFloat()
        val scale = max(size.width / sourceW, size.height / sourceH)
        val offsetX = (size.width - sourceW * scale) / 2f
        val offsetY = (size.height - sourceH * scale) / 2f
        fun point(x: Double, y: Double): Offset = Offset(
            offsetX + (1f - x.toFloat()) * sourceW * scale,
            offsetY + y.toFloat() * sourceH * scale,
        )
        current.hands.forEach { hand ->
            val color = if (hand.side == HandSide.RIGHT) Color(0xFF62E6C5) else Color(0xFF8BCBFF)
            connections.forEach { (from, to) ->
                val a = hand.landmarks[from]
                val b = hand.landmarks[to]
                drawLine(color.copy(alpha = 0.85f), point(a.x, a.y), point(b.x, b.y), 4f, StrokeCap.Round)
            }
            hand.landmarks.forEach { landmark -> drawCircle(Color.White, 4.5f, point(landmark.x, landmark.y)) }
            val palm = hand.palmCenter
            drawCircle(color, 17f, point(palm.x, palm.y), style = Stroke(5f))
            if (hand.gesture == "Closed_Fist") {
                drawCircle(Color(0xFFFFC857), 25f, point(palm.x, palm.y), style = Stroke(5f))
            }
        }
        if (current.hands.size == 2) {
            val a = current.hands[0].palmCenter
            val b = current.hands[1].palmCenter
            if (current.hands.all { it.pinchRatio < 0.42 }) {
                drawLine(Color(0xFFFFC857), point(a.x, a.y), point(b.x, b.y), 6f, StrokeCap.Round)
            }
        }
    }
}

