package de.lightweb.fernbedienung

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.lightweb.fernbedienung.data.AppShortcut
import de.lightweb.fernbedienung.data.AdbAction
import de.lightweb.fernbedienung.data.Device
import de.lightweb.fernbedienung.data.KeyCodes
import de.lightweb.fernbedienung.data.Macro
import de.lightweb.fernbedienung.data.Prefs
import de.lightweb.fernbedienung.net.AdbSession
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

    private val _adbActions = MutableStateFlow(prefs.adbActions)
    val adbActions: StateFlow<List<AdbAction>> = _adbActions.asStateFlow()

    private val adbSession by lazy { AdbSession(getApplication<Application>()) }
    private val adbConnectedState = mutableStateOf(false)
    private val adbBusyState = mutableStateOf(false)
    private val adbLogState = mutableStateOf("")

    val adbConnected: Boolean get() = adbConnectedState.value
    val adbBusy: Boolean get() = adbBusyState.value
    val adbLog: String get() = adbLogState.value

    var adbHost: String
        get() = prefs.adbHost ?: prefs.device?.host.orEmpty()
        set(value) { prefs.adbHost = value }

    private val _macros = MutableStateFlow(prefs.macros)
    val macros: StateFlow<List<Macro>> = _macros.asStateFlow()

    /** Läuft gerade eine Aufnahme? Dann wandert jeder Tastendruck zusätzlich in die Liste. */
    private val recordingState = mutableStateOf(false)
    private val recordedState = mutableStateOf<List<Int>>(emptyList())

    private val appendTargetState = mutableStateOf<Macro?>(null)
    private val macroRunningState = mutableStateOf<String?>(null)

    val isRecording: Boolean get() = recordingState.value
    val recordedSteps: List<Int> get() = recordedState.value

    /** Makro, an das die laufende Aufnahme angehängt wird (statt ein neues anzulegen). */
    val appendTarget: Macro? get() = appendTargetState.value

    /** Name des gerade abgespielten Makros, sonst null. */
    val runningMacro: String? get() = macroRunningState.value

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
    private var macroJob: Job? = null

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
                var wasEstablished = false
                val listener = object : RemoteClient.Listener {
                    override fun onConnected(deviceModel: String, deviceVendor: String) {
                        val label = listOf(deviceVendor, deviceModel)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                            .ifBlank { device.name }
                        update { it.copy(status = Status.CONNECTED, deviceLabel = label, message = null) }
                    }

                    override fun onReady(poweredOn: Boolean) {
                        wasEstablished = true
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

                // Stand die Verbindung schon einmal, war es ein Abbruch und kein
                // Verbindungsproblem - dann sofort wieder von vorn anfangen. Das
                // passiert regelmaessig, wenn der Beamer mit einem App-Link nichts
                // anfangen kann, und darf die Suche nicht ausbremsen.
                if (wasEstablished) backoff = 1_000L

                update {
                    it.copy(
                        status = Status.CONNECTING,
                        // Ein normaler Abbruch braucht keine Meldung, der Status zeigt es schon.
                        message = if (failure == null || failure is IOException) null else failure.message,
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
        if (recordingState.value && direction == RemoteClient.DIRECTION_SHORT) {
            recordedState.value = recordedState.value + keyCode
        }
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

    // --------------------------------------------------------------- Makros

    /**
     * Startet die Aufnahme. Mit [startWithHome] beginnt die Folge auf dem
     * Startbildschirm - nur dann ist beim Abspielen der Ausgangspunkt derselbe.
     */
    fun startRecording(startWithHome: Boolean = true) {
        appendTargetState.value = null
        recordedState.value = emptyList()
        recordingState.value = true
        if (startWithHome) sendKey(KeyCodes.HOME)
    }

    /**
     * Nimmt weitere Schritte auf und hängt sie an ein bestehendes Makro an.
     * Nützlich, wenn die Folge fast stimmt und nur der letzte Schritt fehlt.
     */
    fun startAppending(macro: Macro) {
        appendTargetState.value = macro
        recordedState.value = emptyList()
        recordingState.value = true
    }

    /** Fügt eine Wartezeit in die laufende Aufnahme ein. */
    fun recordPause() {
        if (recordingState.value) recordedState.value = recordedState.value + Macro.STEP_PAUSE
    }

    fun cancelRecording() {
        recordingState.value = false
        recordedState.value = emptyList()
        appendTargetState.value = null
    }

    /** Beendet die Aufnahme und speichert sie. Gibt zurück, ob etwas zu speichern war. */
    fun saveRecording(name: String, delayMs: Int = Macro.DEFAULT_DELAY_MS): Boolean {
        val steps = recordedState.value
        val target = appendTargetState.value
        recordingState.value = false
        recordedState.value = emptyList()
        appendTargetState.value = null
        if (steps.isEmpty()) return false
        if (target != null) {
            val updated = target.copy(steps = target.steps + steps)
            saveMacros(_macros.value.map { if (it == target) updated else it })
            return true
        }
        if (name.isBlank()) return false
        saveMacros(_macros.value + Macro(name.trim(), steps, delayMs))
        return true
    }

    /** Entfernt den letzten Schritt eines Makros. */
    fun dropLastStep(macro: Macro) {
        if (macro.steps.isEmpty()) return
        val updated = macro.copy(steps = macro.steps.dropLast(1))
        saveMacros(_macros.value.map { if (it == macro) updated else it })
    }

    /** Hängt eine Wartezeit an ein Makro an. */
    fun appendPause(macro: Macro) {
        val updated = macro.copy(steps = macro.steps + Macro.STEP_PAUSE)
        saveMacros(_macros.value.map { if (it == macro) updated else it })
    }

    fun saveMacros(list: List<Macro>) {
        prefs.macros = list
        _macros.value = list
    }

    fun deleteMacro(macro: Macro) = saveMacros(_macros.value - macro)

    /** Spielt eine aufgezeichnete Folge ab. */
    fun runMacro(macro: Macro) {
        macroJob?.cancel()
        runCatching { adbSession.close() }
        macroJob = viewModelScope.launch(Dispatchers.IO) {
            for (step in macro.steps) {
                val current = client ?: break
                try {
                    current.sendKey(step)
                } catch (t: Throwable) {
                    // Verbindung weg - Rest der Folge waere ohnehin wirkungslos
                    break
                }
                delay(macro.delayMs.toLong())
            }
        }
    }

    // -------------------------------------------------------------------- ADB

    private fun adbLog(text: String) {
        adbLogState.value = text
    }

    /** Führt etwas über ADB aus und hält Status und Ausgabe fest. */
    private fun adbRun(description: String, block: (AdbSession) -> String?) {
        if (adbBusyState.value) return
        adbBusyState.value = true
        adbLog("$description …")
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { block(adbSession) }
            adbConnectedState.value = adbSession.isConnected
            adbBusyState.value = false
            result
                .onSuccess { output -> adbLog(output?.ifBlank { "$description: erledigt." } ?: "$description: erledigt.") }
                .onFailure { t -> adbLog(t.message ?: "Fehlgeschlagen: $description") }
        }
    }

    fun adbConnect(host: String) {
        prefs.adbHost = host
        adbRun("Verbinde mit $host") { session ->
            session.connect(host)
            "Verbunden. Der Beamer hat den Schlüssel akzeptiert."
        }
    }

    fun adbDisconnect() {
        adbSession.close()
        adbConnectedState.value = false
        adbLog("Verbindung getrennt.")
    }

    /** Liest aus, was gerade auf dem Beamer im Vordergrund läuft. */
    fun adbReadCurrentActivity(onFound: (String) -> Unit) {
        adbRun("Lese den laufenden Bildschirm aus") { session ->
            val component = session.currentActivity()
                ?: throw de.lightweb.fernbedienung.net.AdbException(
                    "Konnte nichts erkennen. Ist auf dem Beamer der gewünschte Bildschirm offen?",
                )
            onFound(component)
            "Gefunden: $component"
        }
    }

    /**
     * Beobachtet eine Weile, welche Bildschirme auf dem Beamer laufen.
     * Damit laesst sich der HDMI-Bildschirm einfangen, ohne Handy und Leinwand
     * gleichzeitig im Blick haben zu muessen.
     */
    fun adbWatchActivities(seconds: Int, onResult: (List<String>) -> Unit) {
        adbRun("Beobachte $seconds Sekunden lang") { session ->
            val seen = mutableListOf<String>()
            session.watchActivities(seconds) { component ->
                seen.add(component)
                adbLog("Gesehen (${seen.size}):\n" + seen.joinToString("\n"))
            }
            onResult(seen)
            if (seen.isEmpty()) "Nichts erkannt." else "Fertig. ${seen.size} Bildschirme gesehen."
        }
    }

    /** Sammelt Hinweise auf den HDMI-Eingang und zieht Kandidaten daraus. */
    fun adbDiagnose(onCandidates: (List<String>) -> Unit) {
        adbRun("Suche nach dem HDMI-Eingang") { session ->
            val report = session.diagnose()
            onCandidates(AdbSession.candidatesFrom(report))
            report
        }
    }

    fun adbListPackages(onResult: (List<String>) -> Unit) {
        adbRun("Lese die App-Liste") { session ->
            val list = session.packages()
            onResult(list)
            "${list.size} Apps gefunden."
        }
    }

    fun adbShell(command: String) {
        adbRun("Führe aus: $command") { session -> session.shell(command) }
    }

    fun runAdbAction(action: AdbAction) {
        adbRun(action.name) { session ->
            if (!session.isConnected) session.connect(adbHost)
            session.shell(action.command)
        }
    }

    fun saveAdbActions(list: List<AdbAction>) {
        prefs.adbActions = list
        _adbActions.value = list
    }

    fun addAdbAction(action: AdbAction) = saveAdbActions(_adbActions.value.filterNot { it.name == action.name } + action)

    fun deleteAdbAction(action: AdbAction) = saveAdbActions(_adbActions.value - action)

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
        macroJob?.cancel()
        runCatching { adbSession.close() }
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
