package de.lightweb.fernbedienung

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import de.lightweb.fernbedienung.data.KeyCodes
import de.lightweb.fernbedienung.ui.AppsScreen
import de.lightweb.fernbedienung.ui.ConnectScreen
import de.lightweb.fernbedienung.ui.ExtraKeysScreen
import de.lightweb.fernbedienung.ui.FernbedienungTheme
import de.lightweb.fernbedienung.ui.HdmiScreen
import de.lightweb.fernbedienung.ui.PairingDialog
import de.lightweb.fernbedienung.ui.RemoteScreen
import de.lightweb.fernbedienung.ui.SettingsScreen

private enum class Screen { REMOTE, CONNECT, SETTINGS, APPS, EXTRA, HDMI }

class MainActivity : ComponentActivity() {

    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FernbedienungTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppRoot(viewModel)
                }
            }
        }
    }

    /** Die Lautstärkewippe des Handys steuert den Beamer, solange die App offen ist. */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (viewModel.volumeKeysEnabled && viewModel.state.value.status == Status.CONNECTED) {
            val code = when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> KeyCodes.VOLUME_UP
                KeyEvent.KEYCODE_VOLUME_DOWN -> KeyCodes.VOLUME_DOWN
                else -> null
            }
            if (code != null) {
                if (event.action == KeyEvent.ACTION_DOWN) viewModel.sendKey(code)
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: RemoteViewModel) {
    val state by viewModel.state.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val apps by viewModel.apps.collectAsState()

    var screen by remember { mutableStateOf(if (state.device == null) Screen.CONNECT else Screen.REMOTE) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    if (screen == Screen.CONNECT) {
        DisposableEffect(Unit) {
            viewModel.startDiscovery()
            onDispose { viewModel.stopDiscovery() }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (screen) {
                                Screen.REMOTE -> state.deviceLabel.ifBlank { "Fernbedienung" }
                                Screen.CONNECT -> "Beamer verbinden"
                                Screen.SETTINGS -> "Einstellungen"
                                Screen.APPS -> "App-Verknüpfungen"
                                Screen.EXTRA -> "Weitere Tasten"
                                Screen.HDMI -> "HDMI-Eingang"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = statusText(state),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    if (screen != Screen.REMOTE) {
                        IconButton(onClick = {
                            screen = if (screen == Screen.APPS || screen == Screen.EXTRA) {
                                Screen.SETTINGS
                            } else {
                                Screen.REMOTE
                            }
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (screen == Screen.REMOTE) {
                        IconButton(onClick = { screen = Screen.CONNECT }) {
                            Icon(Icons.Filled.Cast, contentDescription = "Gerät wählen")
                        }
                        IconButton(onClick = { screen = Screen.SETTINGS }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (screen == Screen.REMOTE && state.status != Status.CONNECTED) {
                ConnectionBanner(
                    state = state,
                    onOpenConnect = { screen = Screen.CONNECT },
                )
            }

            when (screen) {
                Screen.REMOTE -> RemoteScreen(
                    state = state,
                    apps = apps,
                    haptic = viewModel.hapticEnabled,
                    hdmiLink = viewModel.hdmiLink,
                    onKey = viewModel::sendKey,
                    onApp = viewModel::launchApp,
                    onOpenHdmiSetup = { screen = Screen.HDMI },
                )

                Screen.CONNECT -> ConnectScreen(
                    state = state,
                    devices = devices,
                    onConnect = { viewModel.connect(it); screen = Screen.REMOTE },
                    onPair = { viewModel.startPairing(it) },
                )

                Screen.SETTINGS -> SettingsScreen(
                    clientName = viewModel.clientName,
                    haptic = viewModel.hapticEnabled,
                    volumeKeys = viewModel.volumeKeysEnabled,
                    ime = viewModel.imeEnabled,
                    deviceLabel = state.device?.let { "${it.name} (${it.host})" }.orEmpty(),
                    onClientName = { viewModel.clientName = it },
                    onHaptic = { viewModel.hapticEnabled = it },
                    onVolumeKeys = { viewModel.volumeKeysEnabled = it },
                    onIme = { viewModel.imeEnabled = it },
                    onEditApps = { screen = Screen.APPS },
                    onExtraKeys = { screen = Screen.EXTRA },
                    onHdmiSetup = { screen = Screen.HDMI },
                    onForgetDevice = { viewModel.forgetDevice(); screen = Screen.CONNECT },
                    onResetIdentity = { viewModel.resetIdentity(); screen = Screen.CONNECT },
                )

                Screen.APPS -> AppsScreen(
                    apps = apps,
                    onSave = viewModel::saveApps,
                    onReset = viewModel::resetApps,
                )

                Screen.EXTRA -> ExtraKeysScreen(
                    enabled = state.status == Status.CONNECTED,
                    haptic = viewModel.hapticEnabled,
                    onKey = viewModel::sendKey,
                )

                Screen.HDMI -> HdmiScreen(
                    enabled = state.status == Status.CONNECTED,
                    haptic = viewModel.hapticEnabled,
                    runningApp = state.currentApp,
                    savedLink = viewModel.hdmiLink,
                    onSendLink = viewModel::launchApp,
                    onSendKey = viewModel::sendKey,
                    onSave = { viewModel.hdmiLink = it?.takeIf { link -> link.isNotBlank() } },
                )
            }
        }
    }

    if (state.status == Status.PAIRING_CODE) {
        PairingDialog(
            deviceName = state.deviceLabel,
            onSubmit = { viewModel.submitPairingCode(it) },
            onCancel = { viewModel.cancelPairing() },
        )
    }
}

@Composable
private fun ConnectionBanner(state: UiState, onOpenConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.status == Status.CONNECTING || state.status == Status.PAIRING) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.padding(horizontal = 8.dp))
            }
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = onOpenConnect) { Text("Verbinden") }
        }
    }
}

private fun statusText(state: UiState): String = when (state.status) {
    Status.CONNECTED -> if (state.poweredOn) "Verbunden" else "Verbunden – Beamer im Standby"
    Status.CONNECTING -> "Verbinde …"
    Status.PAIRING -> "Koppeln wird gestartet …"
    Status.PAIRING_CODE -> "Code vom Beamer eingeben"
    Status.DISCONNECTED -> if (state.device == null) "Kein Gerät gewählt" else "Nicht verbunden"
}
