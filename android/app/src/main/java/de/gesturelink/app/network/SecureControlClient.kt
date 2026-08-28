package de.gesturelink.app.network

import android.content.Context
import android.os.Build
import de.gesturelink.app.AppConfig
import de.gesturelink.app.core.ControlEvent
import de.gesturelink.app.core.MouseButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

data class NetworkStatus(val label: String = "Nicht verbunden", val connected: Boolean = false, val authenticated: Boolean = false, val error: String? = null)

class SecureControlClient(private val context: Context, private val scope: CoroutineScope) {
    private data class Pointer(val dx: Double, val dy: Double, val confidence: Double, val frame: Long)
    private data class Scroll(val vertical: Double, val horizontal: Double)
    private val vault = CredentialVault(context)
    private val reliable = Channel<Pair<String, JSONObject>>(64)
    private val priority = Channel<Pair<String, JSONObject>>(8)
    private val pointer = AtomicReference<Pointer?>(null)
    private val scroll = AtomicReference<Scroll?>(null)
    private val _status = MutableStateFlow(NetworkStatus())
    val status = _status.asStateFlow()
    private var connectionJob: Job? = null
    private var sourceFrame = 0L

    fun connect(rawUri: String, deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}") {
        connectionJob?.cancel()
        clearPending()
        connectionJob = scope.launch(Dispatchers.IO) {
            val pairing = try { PairingUri.parse(rawUri) } catch (error: Throwable) {
                _status.value = NetworkStatus(error = error.message ?: "Ungültiger Pairing-Code"); return@launch
            }
            val saved = runCatching { vault.load() }.getOrNull()?.takeIf {
                it.serverId == pairing.serverId && it.fingerprint == pairing.fingerprint
            }
            runConnection(pairing, deviceName, saved)
        }
    }

    suspend fun reconnectStored(deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}") {
        val saved = vault.load() ?: return
        val pairing = PairingUri(saved.host, saved.port, saved.serverId, saved.fingerprint, "reconnect-placeholder-token-000000000000")
        runConnection(pairing, deviceName, saved)
    }

    fun submit(event: ControlEvent) {
        if (!_status.value.authenticated) return
        when (event) {
            is ControlEvent.PointerDelta -> pointer.updateAndGet { old -> Pointer((old?.dx ?: 0.0) + event.dx, (old?.dy ?: 0.0) + event.dy, event.confidence, sourceFrame++) }
            is ControlEvent.Scroll -> scroll.updateAndGet { old -> Scroll((old?.vertical ?: 0.0) + event.vertical, (old?.horizontal ?: 0.0) + event.horizontal) }
            is ControlEvent.Click -> reliable.trySend("mouseClick" to JSONObject().put("button", event.button.wire()))
            is ControlEvent.SetButton -> {
                val message = "mouseButton" to JSONObject().put("button", event.button.wire()).put("down", event.down)
                if (!event.down) priority.trySend(message) else reliable.trySend(message)
            }
            is ControlEvent.Semantic -> reliable.trySend("semanticAction" to JSONObject().put("action", event.action))
            is ControlEvent.ReleaseAll -> priority.trySend("releaseAll" to JSONObject().put("reason", "androidSafety"))
            else -> Unit
        }
    }

    fun startControl() {
        if (_status.value.authenticated) reliable.trySend("startControl" to JSONObject().put("profile", "mouse"))
    }
    fun stopControl() {
        if (_status.value.authenticated) {
            priority.trySend("releaseAll" to JSONObject().put("reason", "androidStop"))
            priority.trySend("stopControl" to JSONObject().put("reason", "androidStop"))
        } else clearPending()
    }

    fun disconnect() {
        stopControl()
        connectionJob?.cancel()
        connectionJob = null
        clearPending()
        _status.value = NetworkStatus()
    }

    private suspend fun runConnection(pairing: PairingUri, deviceName: String, saved: StoredConnection? = null) {
        _status.value = NetworkStatus("TLS wird geprüft …")
        var socket: SSLSocket? = null
        try {
            socket = pinnedSocket(pairing)
            val input = DataInputStream(socket.inputStream)
            val output = DataOutputStream(socket.outputStream)
            val sessionId = UUID.randomUUID().toString()
            val clientId = saved?.clientId ?: UUID.randomUUID().toString()
            val sequence = longArrayOf(0)
            write(output, "clientHello", sessionId, sequence, JSONObject().put("deviceName", deviceName).put("supportedVersions", org.json.JSONArray().put(1)))
            if (saved == null) write(output, "pairRequest", sessionId, sequence, JSONObject().put("token", pairing.token).put("clientId", clientId).put("deviceName", deviceName))
            else write(output, "authenticate", sessionId, sequence, JSONObject().put("clientId", saved.clientId).put("credential", saved.credential))

            fun startWriter() = scope.launch(Dispatchers.IO) {
                var lastHeartbeat = 0L
                while (isActive) {
                    val urgent = priority.tryReceive().getOrNull()
                    val normal = reliable.tryReceive().getOrNull()
                    val p = pointer.getAndSet(null)
                    val s = scroll.getAndSet(null)
                    when {
                        urgent != null -> write(output, urgent.first, sessionId, sequence, urgent.second)
                        normal != null -> write(output, normal.first, sessionId, sequence, normal.second)
                        p != null -> write(output, "pointerDelta", sessionId, sequence, JSONObject().put("dx", p.dx.coerceIn(-0.25, 0.25)).put("dy", p.dy.coerceIn(-0.25, 0.25)).put("confidence", p.confidence.coerceIn(0.0, 1.0)).put("sourceFrame", p.frame))
                        s != null -> write(output, "scroll", sessionId, sequence, JSONObject().put("vertical", s.vertical.coerceIn(-20.0, 20.0)).put("horizontal", s.horizontal.coerceIn(-20.0, 20.0)))
                        System.currentTimeMillis() - lastHeartbeat >= AppConfig.heartbeatMs -> {
                            write(output, "heartbeat", sessionId, sequence, JSONObject().put("timestamp", System.currentTimeMillis()))
                            lastHeartbeat = System.currentTimeMillis()
                        }
                        else -> delay(4)
                    }
                }
            }
            var writer: Job? = null
            try {
                while (scope.isActive) {
                    val message = read(input)
                    when (message.getString("type")) {
                        "pairAccepted" -> {
                            val payload = message.getJSONObject("payload")
                            val credential = payload.getString("credential")
                            vault.save(pairing, clientId, credential)
                            write(output, "authenticate", sessionId, sequence, JSONObject().put("clientId", clientId).put("credential", credential))
                            _status.value = NetworkStatus("Gekoppelt · Authentifizierung …", connected = true)
                        }
                        "pairRejected" -> throw IllegalStateException("Kopplung am PC abgelehnt")
                        "authenticated" -> {
                            if (saved != null && (saved.host != pairing.host || saved.port != pairing.port)) {
                                vault.save(pairing, saved.clientId, saved.credential)
                            }
                            writer = writer ?: startWriter()
                            _status.value = NetworkStatus("Verbunden · Start drücken", connected = true, authenticated = true)
                        }
                        "error" -> throw IllegalStateException(message.getJSONObject("payload").optString("code", "Serverfehler"))
                    }
                }
            } finally { writer?.cancel() }
        } catch (error: CancellationException) {
            priority.trySend("releaseAll" to JSONObject().put("reason", "cancelled"))
            throw error
        } catch (error: Throwable) {
            _status.value = NetworkStatus("Verbindung fehlgeschlagen", error = error.message ?: error.javaClass.simpleName)
        } finally {
            runCatching { socket?.close() }
            clearPending()
            if (_status.value.authenticated) _status.value = NetworkStatus("Getrennt – erneut starten", error = "Verbindung beendet")
        }
    }

    private fun clearPending() {
        while (priority.tryReceive().isSuccess) { /* drain */ }
        while (reliable.tryReceive().isSuccess) { /* drain */ }
        pointer.set(null)
        scroll.set(null)
    }

    private fun pinnedSocket(pairing: PairingUri): SSLSocket {
        val expected = pairing.fingerprint
        val trust = object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = throw java.security.cert.CertificateException("Client-Zertifikat nicht erwartet")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                val leaf = chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Serverzertifikat fehlt")
                val actual = MessageDigest.getInstance("SHA-256").digest(leaf.encoded).joinToString("") { "%02x".format(it) }
                if (!MessageDigest.isEqual(actual.toByteArray(), expected.toByteArray())) throw java.security.cert.CertificateException("Zertifikat-Fingerprint stimmt nicht überein")
                leaf.checkValidity()
            }
        }
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trust), SecureRandom())
        val socket = context.socketFactory.createSocket() as SSLSocket
        socket.enabledProtocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }.toTypedArray()
        socket.connect(InetSocketAddress(pairing.host, pairing.port), 4_000)
        socket.soTimeout = 2_000
        socket.startHandshake()
        socket.soTimeout = 0
        return socket
    }

    private fun write(output: DataOutputStream, type: String, sessionId: String, sequence: LongArray, payload: JSONObject) {
        val message = JSONObject().put("version", 1).put("type", type).put("sessionId", sessionId).put("sequence", sequence[0]++).put("payload", payload)
        val bytes = message.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= AppConfig.maxMessageBytes)
        output.writeInt(bytes.size)
        output.write(bytes)
        output.flush()
    }

    private fun read(input: DataInputStream): JSONObject {
        val length = input.readInt()
        require(length in 1..AppConfig.maxMessageBytes) { "Ungültige Nachrichtengröße" }
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return JSONObject(bytes.toString(Charsets.UTF_8)).also { require(it.getInt("version") == 1) }
    }

    private fun MouseButton.wire() = name.lowercase()
}
