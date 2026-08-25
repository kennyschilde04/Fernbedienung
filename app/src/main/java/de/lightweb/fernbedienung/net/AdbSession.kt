package de.lightweb.fernbedienung.net

import android.content.Context
import dadb.AdbKeyPair
import dadb.Dadb
import java.io.Closeable
import java.io.File

class AdbException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * ADB-Verbindung zum Beamer über das WLAN.
 *
 * Das Fernbedienungs-Protokoll kann nur Tasten und App-Links. Über ADB lässt sich
 * dagegen gezielt eine Activity starten, die App-Liste auslesen oder nachsehen,
 * was gerade im Vordergrund läuft - genau das, was für den HDMI-Eingang fehlt.
 *
 * Voraussetzung: Am Beamer sind die Entwickleroptionen und USB-Debugging aktiv.
 * Beim ersten Verbinden fragt der Beamer auf der Leinwand nach Bestätigung.
 */
class AdbSession(context: Context) : Closeable {

    private val privateKeyFile = File(context.filesDir, "adb_key")
    private val publicKeyFile = File(context.filesDir, "adb_key.pub")

    @Volatile
    private var dadb: Dadb? = null

    val isConnected: Boolean get() = dadb != null

    /** Baut die Verbindung auf. Blockiert - gehört in einen Hintergrund-Thread. */
    fun connect(host: String, port: Int = DEFAULT_PORT) {
        close()
        val keyPair = keyPair()
        dadb = try {
            Dadb.create(host, port, keyPair)
        } catch (t: Throwable) {
            throw AdbException(explain(t), t)
        }
    }

    /** Führt einen Shell-Befehl aus und gibt die Ausgabe zurück. */
    fun shell(command: String): String {
        val connection = dadb ?: throw AdbException("Keine ADB-Verbindung.")
        val response = try {
            connection.shell(command)
        } catch (t: Throwable) {
            close()
            throw AdbException("Befehl fehlgeschlagen: ${t.message ?: t::class.java.simpleName}", t)
        }
        if (response.exitCode != 0 && response.errorOutput.isNotBlank()) {
            throw AdbException(response.errorOutput.trim())
        }
        return response.allOutput.trim()
    }

    /** Paket und Activity dessen, was gerade auf dem Beamer im Vordergrund läuft. */
    fun currentActivity(): String? {
        val candidates = listOf(
            "dumpsys activity activities | grep -m1 mResumedActivity",
            "dumpsys window | grep -m1 mCurrentFocus",
            "dumpsys activity activities | grep -m1 ResumedActivity",
        )
        for (command in candidates) {
            val output = runCatching { shell(command) }.getOrNull().orEmpty()
            val match = COMPONENT.find(output)
            if (match != null) return match.value
        }
        return null
    }

    /** Installierte Apps (ohne System-Apps), als Paketnamen. */
    fun packages(includeSystem: Boolean = false): List<String> {
        val flag = if (includeSystem) "" else " -3"
        return shell("pm list packages$flag")
            .lineSequence()
            .mapNotNull { line -> line.trim().removePrefix("package:").takeIf { it.isNotBlank() } }
            .sorted()
            .toList()
    }

    override fun close() {
        runCatching { dadb?.close() }
        dadb = null
    }

    private fun keyPair(): AdbKeyPair {
        if (!privateKeyFile.exists() || !publicKeyFile.exists()) {
            privateKeyFile.delete()
            publicKeyFile.delete()
            AdbKeyPair.generate(privateKeyFile, publicKeyFile)
        }
        return AdbKeyPair.read(privateKeyFile, publicKeyFile)
    }

    private fun explain(t: Throwable): String = when {
        t is java.net.ConnectException ->
            "Der Beamer nimmt auf Port $DEFAULT_PORT keine Verbindung an. " +
                "Sind die Entwickleroptionen und USB-Debugging eingeschaltet?"
        t is java.net.SocketTimeoutException ->
            "Zeitüberschreitung. Bestätige am Beamer die Abfrage nach dem Schlüssel und versuche es erneut."
        else -> t.message ?: "Verbindung fehlgeschlagen (${t::class.java.simpleName})"
    }

    companion object {
        const val DEFAULT_PORT = 5555

        /** paket/activity, so wie es in dumpsys auftaucht. */
        private val COMPONENT = Regex("""[A-Za-z][A-Za-z0-9_.]*\.[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+""")

        /** Befehl, der eine Activity direkt öffnet. */
        fun startActivityCommand(component: String): String = "am start -n $component"

        /** Befehl, der eine App über ihren Startbildschirm-Eintrag öffnet. */
        fun launchPackageCommand(packageName: String): String =
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"
    }
}
