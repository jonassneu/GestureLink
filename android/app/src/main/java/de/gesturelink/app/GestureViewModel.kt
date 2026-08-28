package de.gesturelink.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gesturelink.app.core.ControlEvent
import de.gesturelink.app.core.GestureEngine
import de.gesturelink.app.core.HandFrame
import de.gesturelink.app.vision.HandRecognizer
import de.gesturelink.app.vision.MediaPipeHandRecognizer
import de.gesturelink.app.network.SecureControlClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LiveUiState(
    val frame: HandFrame? = null,
    val recognizerReady: Boolean = false,
    val recognizerError: String? = null,
    val armed: Boolean = false,
    val analyzedFps: Int = 0,
    val droppedFrames: Long = 0,
    val inferenceMs: Double = 0.0,
    val connectionLabel: String = "Nicht verbunden",
    val connectionAuthenticated: Boolean = false,
    val rttMs: Long? = null,
)

class GestureViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = GestureEngine()
    private val transport = SecureControlClient(application, viewModelScope)
    @Volatile private var recognizer: HandRecognizer? = null
    private val _state = MutableStateFlow(LiveUiState())
    val state = _state.asStateFlow()
    private val _events = MutableSharedFlow<ControlEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()
    private var fpsWindowStarted = 0L
    private var fpsFrames = 0

    init {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                recognizer = MediaPipeHandRecognizer(application)
                _state.value = _state.value.copy(recognizerReady = true)
            } catch (error: Throwable) {
                _state.value = _state.value.copy(recognizerError = error.message ?: error.javaClass.simpleName)
            }
        }
        viewModelScope.launch {
            transport.status.collect { network ->
                _state.value = _state.value.copy(
                    connectionLabel = network.error?.let { "${network.label}: $it" } ?: network.label,
                    connectionAuthenticated = network.authenticated,
                )
                if (!network.connected && _state.value.armed) setArmed(false)
            }
        }
        viewModelScope.launch(Dispatchers.IO) { transport.reconnectStored() }
    }

    fun recognizer(): HandRecognizer? = recognizer

    fun onFrame(frame: HandFrame) {
        val now = frame.timestampMs
        if (fpsWindowStarted == 0L) fpsWindowStarted = now
        fpsFrames++
        var fps = _state.value.analyzedFps
        if (now - fpsWindowStarted >= 1_000) {
            fps = fpsFrames
            fpsFrames = 0
            fpsWindowStarted = now
        }
        _state.value = _state.value.copy(frame = frame, analyzedFps = fps, inferenceMs = frame.inferenceMs)
        engine.update(frame).forEach { event ->
            _events.tryEmit(event)
            transport.submit(event)
        }
    }

    fun onDroppedFrame() {
        _state.value = _state.value.copy(droppedFrames = _state.value.droppedFrames + 1)
    }

    fun setArmed(armed: Boolean) {
        engine.setArmed(armed).forEach(_events::tryEmit)
        _state.value = _state.value.copy(armed = armed)
        if (armed) transport.startControl() else transport.stopControl()
    }

    fun connect(pairingUri: String) = transport.connect(pairingUri)

    override fun onCleared() {
        engine.onDisconnect()
        transport.disconnect()
        recognizer?.close()
    }
}
