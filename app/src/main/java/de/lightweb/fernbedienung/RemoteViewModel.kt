package de.lightweb.fernbedienung

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.lightweb.fernbedienung.data.AppShortcut
import de.lightweb.fernbedienung.data.Device
import de.lightweb.fernbedienung.data.Prefs
import de.lightweb.fernbedienung.net.ClientIdentity
import de.lightweb.fernbedienung.net.Discovery
import de.lightweb.fernbedienung.net.PairingException
import de.lightweb.fernbedienung.net.PairingSession
import de.lightweb.fernbedienung.net.RemoteClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.net.ssl.SSLException
import kotlin.math.min

enum class Status { DISCONNECTED, CONNECTING, CONNECTED, PAIRING, PAIRING_CODE }

data class VolumeInfo(val level: Int, val max: Int, val muted: Boolean)

data class UiState(
    val status: Status = Status.DISCONNECTED,
    val device: Device? = null,
    val deviceLabel: String = "",
    val message: String? = null,
    val poweredOn: Boolean = true,
    val volume: VolumeInfo? = null,
    val currentApp: String = "",
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = Prefs(application)
    private val identity by lazy { ClientIdentity.get(application) }

    private val _state = MutableStateFlow(UiState(device = prefs.device, deviceLabel = prefs.device?.name.orEmpty()))
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _apps = MutableStateFlow(prefs.apps)
    val apps: StateFlow<List<AppShortcut>> = _apps.asStateFlow()

    // Als Compose-State, damit die Einstellungen sofort sichtbar umschalten.
    private val hapticState = mutableStateOf(prefs.hapticEnabled)
    private val volumeKeysState = mutableStateOf(prefs.volumeKeysEnabled)
    private val imeState = mutableStateOf(prefs.imeEnabled)
    private val clientNameState = mutableStateOf(prefs.clientName)
    private val hdmiLinkState = mutableStateOf(prefs.hdmiLink)

    var hapticEnabled: Boolean
        get() = hapticState.value
        set(value) { hapticState.value = value; prefs.hapticEnabled = value }

    var volumeKeysEnabled: Boolean
        get() = volumeKeysState.value
        set(value) { volumeKeysState.value = value; prefs.volumeKeysEnabled = value }

    var imeEnabled: Boolean
        get() = imeState.value
        set(value) { imeState.value = value; prefs.imeEnabled = value }

    var hdmiLink: String?
        get() = hdmiLinkState.value
        set(value) { hdmiLinkState.value = value; prefs.hdmiLink = value }

    var clientName: String
        get() = clientNameState.value
        set(value) { clientNameState.value = value; prefs.clientName = value }

    private var connectJob: Job? = null
    private var discoveryJob: Job? = null
    private var client: RemoteClient? = null
    private var pairingSession: PairingSession? = null
    private var pairingDevice: Device? = null

    init {
        prefs.device?.let { connect(it) }
    }

    // ---------------------------------------------------------------- Suche

    fun startDiscovery() {
        if (discoveryJob?.isActive == true) return
        discoveryJob = viewModelScope.launch {
            Discovery.devices(getApplication<Application>())
                .catch { _state.value = _state.value.copy(message = "Suche nicht möglich: ${it.message}") }
                .collect { _devices.value = it }
        }
    }

    fun stopDiscovery() {
        discoveryJob?.cancel()
        discoveryJob = null
    }

    // ------------------------------------------------------------ Verbinden

    fun connect(device: Device) {
        connectJob?.cancel()
        closeClient()
        prefs.device = device
        _state.value = _state.value.copy(
            status = Status.CONNECTING,
            device = device,
            deviceLabel = device.name,
            message = null,
        )

        connectJob = viewModelScope.launch(Dispatchers.IO) {
            var backoff = 1_000L
            while (isActive) {
                val listener = object : RemoteClient.Listener {
                    override fun onConnected(deviceModel: String, deviceVendor: String) {
                        val label = listOf(deviceVendor, deviceModel)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { device.name }
                        update { it.copy(status = Status.CONNECTED, deviceLabel = label, message = null) }
                    }

                    override fun onReady(poweredOn: Boolean) {
                        update { it.copy(status = Status.CONNECTED, poweredOn = poweredOn, message = null) }
                    }

                    override fun onPowerStateChanged(poweredOn: Boolean) {
                        update { it.copy(poweredOn = poweredOn) }
                    }

                    override fun onVolumeChanged(level: Int, max: Int, muted: Boolean) {
                        update { it.copy(volume = VolumeInfo(level, max, muted)) }
                    }

                    override fun onCurrentAppChanged(packageName: String) {
                        update { it.copy(currentApp = packageName) }
                    }
                }

                val newClient = RemoteClient(
                    identity = identity,
                    host = device.host,
                    clientName = prefs.clientName,
                    enableIme = prefs.imeEnabled,
                    listener = listener,
                )
                client = newClient

                val failure = try {
                    newClient.run()
                    null
                } catch (t: Throwable) {
                    t
                } finally {
                    newClient.close()
                }

                if (!isActive) return@launch

                if (failure is SSLException) {
                    // Der Beamer kennt unser Zertifikat nicht (mehr) -> neu koppeln.
                    update {
                        it.copy(
                            status = Status.DISCONNECTED,
                            message = "Noch nicht gekoppelt. Bitte auf „Koppeln“ tippen.",
                        )
                    }
                    return@launch
                }

                update {
                    it.copy(
                        status = Status.CONNECTING,
                        message = when (failure) {
                            null -> null
                            is IOException -> "Verbindung verloren – neuer Versuch …"
                            else -> failure.message
                        },
                    )
                }

                delay(backoff)
                backoff = min(backoff * 2, 15_000L)
            }
        }
    }

    fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        closeClient()
        _state.value = _state.value.copy(status = Status.DISCONNECTED, message = null)
    }

    fun forgetDevice() {
        disconnect()
        prefs.device = null
        _state.value = UiState()
    }

    // -------------------------------------------------------------- Pairing

    fun startPairing(device: Device) {
        connectJob?.cancel()
        closeClient()
        pairingDevice = device
        _state.value = _state.value.copy(
            status = Status.PAIRING,
            device = device,
            deviceLabel = device.name,
            message = null,
        )

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                closePairing()
                val session = PairingSession(identity, device.host, prefs.clientName)
                session.start()
                pairingSession = session
            }.onSuccess {
                update { it.copy(status = Status.PAIRING_CODE, message = null) }
            }.onFailure { t ->
                closePairing()
                update {
                    it.copy(
                        status = Status.DISCONNECTED,
                        message = "Koppeln fehlgeschlagen: ${t.message ?: "unbekannter Fehler"}",
                    )
                }
            }
        }
    }

    fun submitPairingCode(code: String) {
        val session = pairingSession
        val device = pairingDevice
        if (session == null || device == null) {
            _state.value = _state.value.copy(status = Status.DISCONNECTED, message = "Kopplung abgelaufen.")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { session.finish(code) }
                .onSuccess {
                    closePairing()
                    withContext(Dispatchers.Main) { connect(device) }
                }
                .onFailure { t ->
                    val message = (t as? PairingException)?.message ?: "Koppeln fehlgeschlagen: ${t.message}"
                    if (t is PairingException && t.message?.contains("Code") == true) {
                        update { it.copy(message = message) }
                    } else {
                        closePairing()
                        update { it.copy(status = Status.DISCONNECTED, message = message) }
                    }
                }
        }
    }

    fun cancelPairing() {
        closePairing()
        _state.value = _state.value.copy(status = Status.DISCONNECTED, message = null)
    }

    /** Vergisst das Client-Zertifikat, z. B. wenn der Beamer zurückgesetzt wurde. */
    fun resetIdentity() {
        disconnect()
        closePairing()
        ClientIdentity.reset(getApplication<Application>())
        _state.value = _state.value.copy(message = "Zertifikat gelöscht – bitte neu koppeln.")
    }

    // -------------------------------------------------------------- Befehle

    fun sendKey(keyCode: Int, direction: Int = RemoteClient.DIRECTION_SHORT) {
        val current = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { current.sendKey(keyCode, direction) }
                .onFailure { update { s -> s.copy(message = "Befehl nicht gesendet – keine Verbindung.") } }
        }
    }

    fun launchApp(link: String) {
        val current = client ?: return
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { current.launchApp(link) }
                .onFailure { update { s -> s.copy(message = "App konnte nicht gestartet werden.") } }
        }
    }

    // ----------------------------------------------------------------- Apps

    fun saveApps(list: List<AppShortcut>) {
        prefs.apps = list
        _apps.value = list
    }

    fun resetApps() {
        prefs.resetApps()
        _apps.value = prefs.apps
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    override fun onCleared() {
        super.onCleared()
        closeClient()
        closePairing()
    }

    private fun closeClient() {
        runCatching { client?.close() }
        client = null
    }

    private fun closePairing() {
        runCatching { pairingSession?.close() }
        pairingSession = null
    }

    private inline fun update(block: (UiState) -> UiState) {
        _state.value = block(_state.value)
    }
}
