import de.lightweb.fernbedienung.net.ClientIdentity
import de.lightweb.fernbedienung.net.PairingSession
import de.lightweb.fernbedienung.net.RemoteClient
import java.io.File
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val store = File(args[1])
    val identity = ClientIdentity.get(store, "Testgeraet")

    when (args[0]) {
        "gencert" -> {
            val pem = StringBuilder("-----BEGIN CERTIFICATE-----\n")
            val b64 = Base64.getEncoder().encodeToString(identity.certificate.encoded)
            b64.chunked(64).forEach { pem.append(it).append('\n') }
            pem.append("-----END CERTIFICATE-----\n")
            File(args[2]).writeText(pem.toString())
            println("[client] Zertifikat geschrieben nach ${args[2]}")
        }

        "run" -> {
            val codeFile = File(args[2])
            val pairing = PairingSession(identity, "127.0.0.1", "Testhandy", port = 16467)
            pairing.start()
            println("[client] Beamer zeigt Code an, warte auf code.txt")
            var waited = 0
            while (!codeFile.exists() && waited < 100) { Thread.sleep(100); waited++ }
            val code = codeFile.readText().trim()
            println("[client] Code gelesen: $code")
            pairing.finish(code)
            pairing.close()
            println("[client] Pairing OK")

            val ready = CountDownLatch(1)
            var volumeSeen = ""
            val listener = object : RemoteClient.Listener {
                override fun onConnected(deviceModel: String, deviceVendor: String) {
                    println("[client] verbunden mit '$deviceVendor $deviceModel'")
                }
                override fun onReady(poweredOn: Boolean) {
                    println("[client] bereit, eingeschaltet=$poweredOn")
                    ready.countDown()
                }
                override fun onVolumeChanged(level: Int, max: Int, muted: Boolean) {
                    volumeSeen = "$level/$max muted=$muted"
                    println("[client] Lautstaerke $volumeSeen")
                }
            }
            val client = RemoteClient(identity, "127.0.0.1", "Testhandy", false, listener, port = 16466)
            val t = thread { runCatching { client.run() }.onFailure { println("[client] Ende: $it") } }
            check(ready.await(15, TimeUnit.SECONDS)) { "Beamer wurde nicht bereit" }
            Thread.sleep(300)
            client.sendKey(23)
            client.sendKey(243)
            client.launchApp("https://www.youtube.com")
            Thread.sleep(700)
            client.close()
            t.join(2000)
            check(volumeSeen == "42/100 muted=false") { "Lautstaerke falsch gelesen: $volumeSeen" }
            println("[client] Lautstaerke korrekt entschluesselt: $volumeSeen")
            println("[client] fertig")
        }
    }
}
