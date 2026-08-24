package de.lightweb.fernbedienung.net

import de.lightweb.fernbedienung.proto.Frames
import de.lightweb.fernbedienung.proto.ProtoMessage
import de.lightweb.fernbedienung.proto.ProtoWriter
import java.io.Closeable
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

class PairingException(message: String) : Exception(message)

/**
 * Pairing nach dem "Google TV / Android TV Remote Control v2"-Protokoll (Port 6467).
 *
 * Ablauf:
 *  1. PairingRequest  -> PairingRequestAck
 *  2. Options         -> Options
 *  3. Configuration   -> ConfigurationAck   (der Beamer zeigt jetzt einen 6-stelligen Code)
 *  4. Secret          -> SecretAck          (Code vom Nutzer, als Hash ueber beide Zertifikate)
 */
class PairingSession(
    private val identity: ClientIdentity,
    private val host: String,
    private val clientName: String,
    private val port: Int = 6467,
) : Closeable {

    private var socket: SSLSocket? = null
    private var serverCertificate: X509Certificate? = null

    /** Verbindet und laeuft bis zu dem Punkt, an dem der Beamer den Code anzeigt. */
    fun start() {
        val s = Tls.connect(identity, host, port)
        socket = s
        serverCertificate = s.session.peerCertificates.firstOrNull() as? X509Certificate
            ?: throw PairingException("Der Beamer hat kein Zertifikat gesendet.")

        send(
            ProtoWriter.message {
                int(FIELD_PROTOCOL_VERSION, 2)
                int(FIELD_STATUS, STATUS_OK)
                message(FIELD_PAIRING_REQUEST) {
                    string(1, "atvremote")
                    string(2, clientName)
                }
            }
        )

        while (true) {
            val msg = receive()
            when {
                msg.has(FIELD_PAIRING_REQUEST_ACK) -> send(
                    ProtoWriter.message {
                        int(FIELD_PROTOCOL_VERSION, 2)
                        int(FIELD_STATUS, STATUS_OK)
                        message(FIELD_OPTIONS) {
                            message(1) { // input_encodings
                                int(1, ENCODING_HEXADECIMAL)
                                int(2, SYMBOL_LENGTH)
                            }
                            int(3, ROLE_INPUT) // preferred_role
                        }
                    }
                )

                msg.has(FIELD_OPTIONS) -> send(
                    ProtoWriter.message {
                        int(FIELD_PROTOCOL_VERSION, 2)
                        int(FIELD_STATUS, STATUS_OK)
                        message(FIELD_CONFIGURATION) {
                            message(1) { // encoding
                                int(1, ENCODING_HEXADECIMAL)
                                int(2, SYMBOL_LENGTH)
                            }
                            int(2, ROLE_INPUT) // client_role
                        }
                    }
                )

                msg.has(FIELD_CONFIGURATION_ACK) -> return

                else -> throw PairingException("Unerwartete Antwort beim Pairing: $msg")
            }
        }
    }

    /** Schliesst das Pairing mit dem auf dem Beamer angezeigten Code ab. */
    fun finish(code: String) {
        val normalized = code.trim().replace(" ", "").uppercase()
        if (normalized.length != 6 || !normalized.all { it.isDigit() || it in 'A'..'F' }) {
            throw PairingException("Der Code muss aus genau 6 Zeichen (0-9, A-F) bestehen.")
        }

        val serverKey = serverCertificate?.publicKey as? RSAPublicKey
            ?: throw PairingException("Zertifikat des Beamers konnte nicht gelesen werden.")
        val clientKey = identity.certificate.publicKey as RSAPublicKey

        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bigIntegerBytes(clientKey.modulus))
        digest.update(bigIntegerBytes(clientKey.publicExponent))
        digest.update(bigIntegerBytes(serverKey.modulus))
        digest.update(bigIntegerBytes(serverKey.publicExponent))
        digest.update(hexToBytes(normalized.substring(2)))
        val secret = digest.digest()

        val expected = normalized.substring(0, 2).toInt(16)
        if ((secret[0].toInt() and 0xFF) != expected) {
            throw PairingException("Der Code passt nicht. Bitte pruefen und neu eingeben.")
        }

        send(
            ProtoWriter.message {
                int(FIELD_PROTOCOL_VERSION, 2)
                int(FIELD_STATUS, STATUS_OK)
                message(FIELD_SECRET) { bytes(1, secret) }
            }
        )

        val reply = receive()
        if (!reply.has(FIELD_SECRET_ACK)) {
            throw PairingException("Der Beamer hat das Pairing abgelehnt.")
        }
    }

    override fun close() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun send(message: ByteArray) {
        val s = socket ?: throw PairingException("Keine Verbindung.")
        Frames.write(s.outputStream, message)
    }

    private fun receive(): ProtoMessage {
        val s = socket ?: throw PairingException("Keine Verbindung.")
        val msg = ProtoMessage.parse(Frames.read(s.inputStream))
        when (val status = msg.int(FIELD_STATUS)) {
            STATUS_OK -> Unit
            STATUS_BAD_SECRET -> throw PairingException("Falscher Code - bitte noch einmal versuchen.")
            STATUS_BAD_CONFIGURATION -> throw PairingException("Der Beamer akzeptiert diese Pairing-Variante nicht.")
            else -> throw PairingException("Der Beamer meldet Fehler $status.")
        }
        return msg
    }

    companion object {
        private const val FIELD_PROTOCOL_VERSION = 1
        private const val FIELD_STATUS = 2
        private const val FIELD_PAIRING_REQUEST = 10
        private const val FIELD_PAIRING_REQUEST_ACK = 11
        private const val FIELD_OPTIONS = 20
        private const val FIELD_CONFIGURATION = 30
        private const val FIELD_CONFIGURATION_ACK = 31
        private const val FIELD_SECRET = 40
        private const val FIELD_SECRET_ACK = 41

        private const val STATUS_OK = 200
        private const val STATUS_BAD_CONFIGURATION = 401
        private const val STATUS_BAD_SECRET = 402

        private const val ENCODING_HEXADECIMAL = 3
        private const val ROLE_INPUT = 1
        private const val SYMBOL_LENGTH = 6

        /** Wie Pythons bytes.fromhex(f"{n:X}") - fuehrende Null nur zum Auffuellen auf volle Bytes. */
        internal fun bigIntegerBytes(value: BigInteger): ByteArray {
            var hex = value.toString(16).uppercase()
            if (hex.length % 2 != 0) hex = "0$hex"
            return hexToBytes(hex)
        }

        internal fun hexToBytes(hex: String): ByteArray {
            val out = ByteArray(hex.length / 2)
            for (i in out.indices) {
                out[i] = ((digit(hex[2 * i]) shl 4) or digit(hex[2 * i + 1])).toByte()
            }
            return out
        }

        private fun digit(c: Char): Int {
            val v = Character.digit(c, 16)
            require(v >= 0) { "Ungueltiges Hex-Zeichen: $c" }
            return v
        }
    }
}
