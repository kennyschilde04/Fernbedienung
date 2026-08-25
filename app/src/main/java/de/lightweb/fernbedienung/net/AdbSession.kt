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

    @Volatile
    private var preferredActivityCommand: String? = null

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
        val commands = listOfNotNull(preferredActivityCommand) + ACTIVITY_COMMANDS
        for (command in commands) {
            val output = runCatching { shell(command) }.getOrNull().orEmpty()
            val match = COMPONENT.find(output)
            if (match != null) {
                preferredActivityCommand = command
                return match.value
            }
        }
        return null
    }

    /**
     * Beobachtet [seconds] Sekunden lang, welche Bildschirme auf dem Beamer laufen.
     *
     * Das ist der verlässliche Weg, den HDMI-Bildschirm einzufangen: Beobachtung
     * starten, dann in Ruhe am Beamer auf HDMI wechseln. Alles, was in der Zeit im
     * Vordergrund war, landet in der Liste.
     */
    fun watchActivities(seconds: Int, onSample: (String) -> Unit): List<String> {
        val seen = LinkedHashSet<String>()
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            val current = runCatching { currentActivity() }.getOrNull()
            if (current != null && seen.add(current)) onSample(current)
            Thread.sleep(1200)
        }
        return seen.toList()
    }

    /**
     * Sammelt alles, was Aufschluss über den HDMI-Eingang geben kann: die
     * Eingangsverwaltung von Android TV, zuletzt geöffnete Passthrough-Links und
     * Pakete, deren Name nach Eingangsquelle klingt.
     */
    fun diagnose(): String = buildString {
        val steps = listOf(
            "Eingänge (tv_input)" to
                "dumpsys tv_input | grep -iE 'inputId|HW[0-9]|hdmi' | head -30",
            "Zuletzt geöffnete Passthrough-Links" to
                "dumpsys activity starter | grep -i passthrough | head -10",
            "Pakete rund um Eingänge" to
                "pm list packages | grep -iE 'tvinput|hdmi|source|inputservice|mediatek|droidlogic' | head -20",
            "Empfänger für Passthrough-Links" to
                "cmd package query-activities -a android.intent.action.VIEW " +
                "-d content://android.media.tv/passthrough 2>/dev/null | grep -iE 'name=|packageName' | head -20",
            "Laufender Bildschirm" to (preferredActivityCommand ?: ACTIVITY_COMMANDS.first()),
        )
        for ((title, command) in steps) {
            append("### ").append(title).append('\n')
            val output = runCatching { shell(command) }.getOrElse { "(nicht verfügbar: ${it.message})" }
            append(output.ifBlank { "(keine Ausgabe)" }).append("\n\n")
        }
    }.trim()

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

    private fun explain(t: Throwable): String = when (t) {
        is java.net.ConnectException ->
            "Der Beamer nimmt auf Port $DEFAULT_PORT keine Verbindung an. " +
                "Sind die Entwickleroptionen und USB-Debugging eingeschaltet?"
        is java.net.SocketTimeoutException ->
            "Zeitüberschreitung. Bestätige am Beamer die Abfrage nach dem Schlüssel und versuche es erneut."
        else -> t.message ?: "Verbindung fehlgeschlagen (${t::class.java.simpleName})"
    }

    companion object {
        const val DEFAULT_PORT = 5555

        private val ACTIVITY_COMMANDS = listOf(
            "dumpsys activity activities | grep -m1 mResumedActivity",
            "dumpsys window | grep -m1 mCurrentFocus",
            "dumpsys activity activities | grep -m1 ResumedActivity",
        )

        /** paket/activity, so wie es in dumpsys auftaucht. */
        private val COMPONENT = Regex("[A-Za-z][A-Za-z0-9_.]*\\.[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")

        /** Passthrough-Links, so wie sie in dumpsys auftauchen. */
        private val PASSTHROUGH = Regex("content://android\\.media\\.tv/passthrough/[^\\s'\"}\\]]+")

        /** Befehl, der eine Activity direkt öffnet. */
        fun startActivityCommand(component: String): String = "am start -n $component"

        /** Befehl, der eine App über ihren Startbildschirm-Eintrag öffnet. */
        fun launchPackageCommand(packageName: String): String =
            "monkey -p $packageName -c android.intent.category.LAUNCHER 1"

        /**
         * Befehl, der einen Link öffnet. Über ADB klappt das auch dort, wo der
         * Fernbedienungs-Dienst daran scheitert und die Verbindung kappt.
         */
        fun openLinkCommand(uri: String): String = "am start -a android.intent.action.VIEW -d '$uri'"

        /** Passender Startbefehl - je nachdem, ob es ein Link oder eine Komponente ist. */
        fun commandFor(candidate: String): String =
            if (candidate.startsWith("content://")) openLinkCommand(candidate) else startActivityCommand(candidate)

        /** Zieht Komponenten und Passthrough-Links aus einer Ausgabe, ohne den Startbildschirm. */
        fun candidatesFrom(text: String): List<String> =
            (PASSTHROUGH.findAll(text).map { it.value }.toList() +
                COMPONENT.findAll(text).map { it.value }.toList())
                .distinct()
                .filterNot { it.contains("tvlauncher") || it.contains("de.lightweb.fernbedienung") }
                .take(30)
    }
}
