package de.lightweb.fernbedienung.net

import java.net.InetSocketAddress
import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager

/**
 * KeyManager, der immer unser einziges Client-Zertifikat liefert.
 * Der Beamer schickt beim Handshake keine brauchbaren CA-Hinweise, deshalb
 * wuerde die Standard-Auswahl das Zertifikat verwerfen.
 */
private class FixedKeyManager(
    private val chain: Array<X509Certificate>,
    private val key: PrivateKey,
) : X509ExtendedKeyManager() {
    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?) = arrayOf(ALIAS)
    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?) = ALIAS
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun getCertificateChain(alias: String?): Array<X509Certificate> = chain
    override fun getPrivateKey(alias: String?): PrivateKey = key

    companion object {
        const val ALIAS = "fernbedienung"
    }
}

/**
 * Der Beamer benutzt ein selbstsigniertes Zertifikat, das keiner CA bekannt ist.
 * Die Absicherung passiert stattdessen ueber das Pairing-Geheimnis (Hash ueber
 * beide Zertifikate), das nur mit dem echten Geraet zusammenpasst.
 */
private object AcceptAllServersTrustManager : X509TrustManager {
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

object Tls {

    fun connect(identity: ClientIdentity, host: String, port: Int, timeoutMs: Int = 8000): SSLSocket {
        val context = SSLContext.getInstance("TLS")
        context.init(
            arrayOf(FixedKeyManager(arrayOf(identity.certificate), identity.privateKey)),
            arrayOf(AcceptAllServersTrustManager),
            null,
        )
        val plain = Socket()
        plain.connect(InetSocketAddress(host, port), timeoutMs)
        plain.tcpNoDelay = true
        val socket = context.socketFactory.createSocket(plain, host, port, true) as SSLSocket
        val protocols = socket.supportedProtocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
        if (protocols.isNotEmpty()) socket.enabledProtocols = protocols.toTypedArray()
        socket.useClientMode = true
        socket.startHandshake()
        return socket
    }
}
