package de.gesturelink.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import de.gesturelink.app.ui.CameraWithOverlay
import de.gesturelink.app.ui.GestureLinkTheme
import kotlinx.coroutines.delay

private enum class Screen { WELCOME, PERMISSIONS, PAIR, CONTROL, SETTINGS, PRIVACY }

class MainActivity : ComponentActivity() {
    private val viewModel: GestureViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GestureLinkTheme { GestureLinkApp(viewModel) }
        }
    }

    override fun onStop() {
        viewModel.setArmed(false)
        super.onStop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureLinkApp(viewModel: GestureViewModel) {
    var screen by rememberSaveable { mutableStateOf(Screen.WELCOME) }
    val appState by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(appState.connectionAuthenticated) {
        if (appState.connectionAuthenticated && screen in setOf(Screen.WELCOME, Screen.PERMISSIONS, Screen.PAIR)) {
            screen = Screen.CONTROL
        }
    }
    val title = when (screen) {
        Screen.WELCOME -> AppConfig.productName
        Screen.PERMISSIONS -> "Berechtigungen"
        Screen.PAIR -> "PC verbinden"
        Screen.CONTROL -> "Live Control"
        Screen.SETTINGS -> "Einstellungen"
        Screen.PRIVACY -> "Datenschutz"
    }
    Scaffold(
        topBar = {
            if (screen != Screen.CONTROL) {
                TopAppBar(title = { Text(title, fontWeight = FontWeight.Bold) })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(if (screen == Screen.CONTROL) androidx.compose.foundation.layout.PaddingValues() else padding)) {
            when (screen) {
                Screen.WELCOME -> Welcome { screen = Screen.PERMISSIONS }
                Screen.PERMISSIONS -> Permissions { screen = Screen.PAIR }
                Screen.PAIR -> Pairing(viewModel, onContinue = { screen = Screen.CONTROL }, onPrivacy = { screen = Screen.PRIVACY })
                Screen.CONTROL -> CameraPermissionGate(onDeniedBack = { screen = Screen.PERMISSIONS }) {
                    Control(viewModel, onSettings = { screen = Screen.SETTINGS })
                }
                Screen.SETTINGS -> Settings(onBack = { screen = Screen.CONTROL })
                Screen.PRIVACY -> Privacy(onBack = { screen = Screen.PAIR })
            }
        }
    }
}

@Composable
private fun Welcome(onContinue: () -> Unit) {
    Hero(
        icon = { Icon(Icons.Default.Link, null, modifier = Modifier.width(64.dp).height(64.dp)) },
        title = "Steuere deinen PC mit deinen Händen",
        body = "Die Frontkamera erkennt Gesten direkt auf diesem Gerät. Kamerabilder werden weder übertragen noch gespeichert.",
        button = "Los geht’s",
        onContinue = onContinue,
    )
}

@Composable
private fun Permissions(onContinue: () -> Unit) {
    val context = LocalContext.current
    val localPermission = "android.permission.ACCESS_LOCAL_NETWORK"
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onContinue() }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center) {
        Text("Direkt, lokal, privat", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(24.dp))
        PermissionCard("Lokales Netzwerk", "${AppConfig.productName} benötigt Zugriff auf dein lokales Netzwerk, um Steuerbefehle direkt an deinen verbundenen Windows-PC zu senden.")
        Spacer(Modifier.height(12.dp))
        PermissionCard("Frontkamera", "Die Frontkamera erkennt deine Handbewegungen. Das Bild bleibt auf diesem Gerät. Wir fragen erst beim Start der Kalibrierung.")
        Spacer(Modifier.height(24.dp))
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(context, localPermission) != PackageManager.PERMISSION_GRANTED) launcher.launch(localPermission)
            else onContinue()
        }, modifier = Modifier.fillMaxWidth()) { Text("Weiter", fontSize = 18.sp) }
    }
}

@Composable
private fun PermissionCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(body)
        }
    }
}

@Composable
private fun Pairing(viewModel: GestureViewModel, onContinue: () -> Unit, onPrivacy: () -> Unit) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scanner = remember {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
        GmsBarcodeScanning.getClient(context, options)
    }
    LaunchedEffect(state.connectionAuthenticated) { if (state.connectionAuthenticated) onContinue() }
    Column(Modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.QrCodeScanner, null, modifier = Modifier.width(72.dp).height(72.dp), tint = MaterialTheme.colorScheme.primary)
        Text("QR-Code am Windows-PC scannen", fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(onClick = {
            scanner.startScan()
                .addOnSuccessListener { code ->
                    val raw = code.rawValue.orEmpty()
                    if (raw.startsWith("gesturelink://pair")) {
                        error = null
                        viewModel.connect(raw)
                    } else error = "Das ist kein GestureLink-QR-Code"
                }
                .addOnFailureListener { error = it.message ?: "Scan fehlgeschlagen" }
        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("QR scannen und verbinden") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        Text(state.connectionLabel, textAlign = TextAlign.Center)
        OutlinedButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text("Kamera offline testen") }
        OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text("Datenschutz") }
    }
}

@Composable
private fun CameraPermissionGate(onDeniedBack: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    if (granted) content() else Hero(
        icon = { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.width(64.dp).height(64.dp)) },
        title = "Frontkamera starten",
        body = "Die Frontkamera erkennt deine Handbewegungen. Das Bild bleibt auf diesem Gerät und wird nicht übertragen.",
        button = "Kamera erlauben",
        onContinue = { launcher.launch(Manifest.permission.CAMERA) },
        secondary = onDeniedBack,
    )
}

@Composable
private fun Control(viewModel: GestureViewModel, onSettings: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val state by viewModel.state.collectAsStateWithLifecycle()
    var countdown by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            viewModel.setArmed(false)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    LaunchedEffect(countdown) {
        if (countdown > 0) {
            delay(1_000)
            countdown--
            if (countdown == 0) viewModel.setArmed(true)
        }
    }
    Box(Modifier.fillMaxSize()) {
        CameraWithOverlay(viewModel, state.frame, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color(0xCC07111F)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(AppConfig.productName, fontWeight = FontWeight.Bold); Text(state.connectionLabel) }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (state.armed) "AKTIV" else "PAUSIERT", fontWeight = FontWeight.Bold, color = if (state.armed) MaterialTheme.colorScheme.primary else Color(0xFFFFC857))
                Text("${state.analyzedFps} FPS · %.0f ms".format(state.inferenceMs))
            }
        }
        state.frame?.hands?.joinToString("  ·  ") { "${if (it.side.name == "RIGHT") "Rechts" else "Links"}: ${it.gesture}" }?.let {
            Text(it, Modifier.align(Alignment.Center).background(Color(0x9907111F), RoundedCornerShape(12.dp)).padding(12.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
        }
        if (countdown > 0) Text("$countdown", Modifier.align(Alignment.Center), fontSize = 88.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xDD07111F)).padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = {
                if (state.armed || countdown > 0) { countdown = 0; viewModel.setArmed(false) } else countdown = 3
            }) {
                Icon(if (state.armed) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                Text(if (state.armed) " STOPP" else " START")
            }
            OutlinedButton(onClick = onSettings) { Text("Einstellungen") }
        }
        state.recognizerError?.let { Text("Erkennung: $it", Modifier.align(Alignment.TopCenter).padding(top = 90.dp).background(MaterialTheme.colorScheme.error).padding(8.dp)) }
    }
}

@Composable
private fun Settings(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Profil: Mouse", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Dominante Hand: Rechts")
        Text("Rechte Faust: Rechtsklick")
        Text("Linke Faust: Linksklick")
        Text("Zwei Hände: Abstand steuert Zoom")
        Text("Haptik: An")
        Text("Preview spiegeln: An")
        Text("Empfindlichkeit: Finger eng = niedrig · weit = sehr hoch")
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack) { Text("Zurück") }
    }
}

@Composable
private fun Privacy(onBack: () -> Unit) {
    Hero(
        icon = { Icon(Icons.Default.CameraAlt, null, modifier = Modifier.width(64.dp).height(64.dp)) },
        title = "Deine Bilder bleiben hier",
        body = "Keine Kamera- oder Mikrofonaufnahmen, kein Cloudkonto, keine Werbung und keine Analytics. Nur Gesten und kleine Steuerdeltas gehen verschlüsselt an den gekoppelten PC. Diagnose-Landmarken werden nur nach ausdrücklicher Zustimmung aufgezeichnet.",
        button = "Verstanden",
        onContinue = onBack,
    )
}

@Composable
private fun Hero(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
    button: String,
    onContinue: () -> Unit,
    secondary: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        icon()
        Spacer(Modifier.height(22.dp))
        Text(title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(body, fontSize = 18.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(button, fontSize = 18.sp) }
        secondary?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Zurück") } }
    }
}
