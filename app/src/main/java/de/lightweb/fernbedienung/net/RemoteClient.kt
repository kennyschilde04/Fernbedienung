package de.lightweb.fernbedienung.net

import android.os.Build
import de.lightweb.fernbedienung.proto.Frames
import de.lightweb.fernbedienung.proto.ProtoMessage
import de.lightweb.fernbedienung.proto.ProtoWriter
import java.io.Closeable
import javax.net.ssl.SSLSocket

/**
 * Steuerverbindung zum Beamer (Port 6466) nach dem Android-TV-Remote-Protokoll v2.
 * Alle Methoden ausser [send*] blockieren - [run] gehoert in einen Hintergrund-Thread.
 */
class RemoteClient(
    private val identity: ClientIdentity,
    private val host: String,
    private val clientName: String,
    private val enableIme: Boolean,
    private val listener: Listener,
    private val port: Int = 6466,
) : Closeable {

    interface Listener {
        fun onConnected(deviceModel: String, deviceVendor: String) {}
        fun onReady(poweredOn: Boolean) {}
        fun onPowerStateChanged(poweredOn: Boolean) {}
        fun onVolumeChanged(level: Int, max: Int, muted: Boolean) {}
        fun onCurrentAppChanged(packageName: String) {}
    }

    @Volatile
    private var socket: SSLSocket? = null

    @Volatile
    private var closed = false

    private var activeFeatures = requestedFeatures()
    private val writeLock = Any()

    /** Verbindet und liest bis zum Verbindungsende. Wirft bei Fehlern. */
    fun run() {
        val s = Tls.connect(identity, host, port)
        s.soTimeout = READ_TIMEOUT_MS
        socket = s
        while (!closed) {
            val msg = ProtoMessage.parse(Frames.read(s.inputStream))
            handle(msg)
        }
    }

    fun sendKey(keyCode: Int, direction: Int = DIRECTION_SHORT) = send(
        ProtoWriter.message {
            message(FIELD_KEY_INJECT) {
                int(1, keyCode)
                int(2, direction)
            }
        }
    )

    fun launchApp(link: String) = send(
        ProtoWriter.message {
            message(FIELD_APP_LINK_LAUNCH) { string(1, link) }
        }
    )

    override fun close() {
        closed = true
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(msg: ProtoMessage) {
        when {
            msg.has(FIELD_CONFIGURE) -> {
                val configure = msg.message(FIELD_CONFIGURE)!!
                val supported = configure.int(1)
                val info = configure.message(2)
                activeFeatures = requestedFeatures() and supported
                listener.onConnected(info?.string(1) ?: "", info?.string(2) ?: "")
                send(
                    ProtoWriter.message {
                        message(FIELD_CONFIGURE) {
                            int(1, activeFeatures)
                            message(2) {
                                string(1, deviceProperty { Build.MODEL })
                                string(2, deviceProperty { Build.MANUFACTURER })
                                int(3, 1)
                                string(4, "1")
                                string(5, "atvremote")
                                string(6, "1.0.0")
                            }
                        }
                    }
                )
            }

            msg.has(FIELD_SET_ACTIVE) -> send(
                ProtoWriter.message {
                    message(FIELD_SET_ACTIVE) { int(1, activeFeatures) }
                }
            )

            msg.has(FIELD_PING_REQUEST) -> {
                val value = msg.message(FIELD_PING_REQUEST)!!.int(1)
                send(ProtoWriter.message { message(FIELD_PING_RESPONSE) { int(1, value) } })
            }

            msg.has(FIELD_START) -> {
                val started = msg.message(FIELD_START)!!.bool(1)
                listener.onReady(started)
                listener.onPowerStateChanged(started)
            }

            msg.has(FIELD_SET_VOLUME_LEVEL) -> {
                val volume = msg.message(FIELD_SET_VOLUME_LEVEL)!!
                listener.onVolumeChanged(volume.int(7), volume.int(6), volume.bool(8))
            }

            msg.has(FIELD_IME_KEY_INJECT) -> {
                val app = msg.message(FIELD_IME_KEY_INJECT)?.message(1)?.string(12).orEmpty()
                if (app.isNotEmpty()) listener.onCurrentAppChanged(app)
            }

            msg.has(FIELD_ERROR) -> Unit // Fehler werden ueber den Verbindungsabbruch sichtbar
        }
    }

    private fun send(message: ByteArray) {
        val s = socket ?: throw IllegalStateException("Nicht verbunden")
        synchronized(writeLock) { Frames.write(s.outputStream, message) }
    }

    private fun deviceProperty(block: () -> String?): String =
        runCatching { block() }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Android"

    private fun requestedFeatures(): Int =
        FEATURE_PING or FEATURE_KEY or FEATURE_POWER or FEATURE_VOLUME or FEATURE_APP_LINK or
            (if (enableIme) FEATURE_IME else 0)

    companion object {
        const val DIRECTION_SHORT = 3
        const val DIRECTION_START_LONG = 1
        const val DIRECTION_END_LONG = 2

        // Der Beamer pingt alle 5 Sekunden; nach 20 Sekunden Stille gilt die Verbindung als tot.
        private const val READ_TIMEOUT_MS = 20_000

        private const val FIELD_CONFIGURE = 1
        private const val FIELD_SET_ACTIVE = 2
        private const val FIELD_ERROR = 3
        private const val FIELD_PING_REQUEST = 8
        private const val FIELD_PING_RESPONSE = 9
        private const val FIELD_KEY_INJECT = 10
        private const val FIELD_IME_KEY_INJECT = 20
        private const val FIELD_START = 40
        private const val FIELD_SET_VOLUME_LEVEL = 50
        private const val FIELD_APP_LINK_LAUNCH = 90

        private const val FEATURE_PING = 1 shl 0
        private const val FEATURE_KEY = 1 shl 1
        private const val FEATURE_IME = 1 shl 2
        private const val FEATURE_POWER = 1 shl 5
        private const val FEATURE_VOLUME = 1 shl 6
        private const val FEATURE_APP_LINK = 1 shl 9
    }
}
