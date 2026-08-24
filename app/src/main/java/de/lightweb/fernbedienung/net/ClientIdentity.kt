package de.lightweb.fernbedienung.net

import android.content.Context
import android.os.Build
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date

/**
 * Selbstsigniertes Client-Zertifikat, mit dem sich die App beim Beamer ausweist.
 * Es wird einmalig erzeugt und in einem PKCS12-Keystore im App-Verzeichnis abgelegt.
 * Wichtig: Nach dem Pairing darf es sich nicht mehr aendern, sonst muss neu gepairt werden.
 */
class ClientIdentity private constructor(
    val privateKey: PrivateKey,
    val certificate: X509Certificate,
) {

    companion object {
        private const val ALIAS = "fernbedienung"
        private val PASSWORD = "fernbedienung".toCharArray()
        private const val FILE_NAME = "client.p12"

        @Volatile
        private var cached: ClientIdentity? = null

        fun get(context: Context): ClientIdentity =
            get(File(context.filesDir, FILE_NAME), sanitize(runCatching { Build.MODEL }.getOrNull()))

        /** Laedt die Identitaet aus der Datei oder erzeugt sie beim ersten Mal. */
        internal fun get(file: File, label: String): ClientIdentity {
            cached?.let { return it }
            synchronized(this) {
                cached?.let { return it }
                val identity = load(file) ?: create(file, label)
                cached = identity
                return identity
            }
        }

        /** Loescht die Identitaet - danach ist ein neues Pairing noetig. */
        fun reset(context: Context) {
            synchronized(this) {
                File(context.filesDir, FILE_NAME).delete()
                cached = null
            }
        }

        private fun load(file: File): ClientIdentity? {
            if (!file.exists()) return null
            return try {
                val store = KeyStore.getInstance("PKCS12")
                file.inputStream().use { store.load(it, PASSWORD) }
                val key = store.getKey(ALIAS, PASSWORD) as? PrivateKey ?: return null
                val cert = store.getCertificate(ALIAS) as? X509Certificate ?: return null
                ClientIdentity(key, cert)
            } catch (t: Throwable) {
                file.delete()
                null
            }
        }

        private fun create(file: File, label: String): ClientIdentity {
            val generator = KeyPairGenerator.getInstance("RSA")
            generator.initialize(2048, SecureRandom())
            val keyPair = generator.generateKeyPair()

            val name = X500Name("CN=Fernbedienung, O=Fernbedienung, OU=$label")
            val now = System.currentTimeMillis()
            val notBefore = Date(now - 24L * 60 * 60 * 1000)
            val notAfter = Date(now + 30L * 365 * 24 * 60 * 60 * 1000)
            val serial = BigInteger(64, SecureRandom())

            val builder = JcaX509v3CertificateBuilder(name, serial, notBefore, notAfter, name, keyPair.public)
            val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
            val certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))

            val store = KeyStore.getInstance("PKCS12")
            store.load(null, null)
            store.setKeyEntry(ALIAS, keyPair.private, PASSWORD, arrayOf(certificate))
            file.outputStream().use { store.store(it, PASSWORD) }

            return ClientIdentity(keyPair.private, certificate)
        }

        private fun sanitize(value: String?): String =
            (value ?: "Android").replace(Regex("[^A-Za-z0-9 _-]"), "").take(30).ifEmpty { "Android" }
    }

    fun publicKeyNumbers(): Pair<BigInteger, BigInteger> {
        val key = certificate.publicKey as RSAPublicKey
        return key.modulus to key.publicExponent
    }
}
