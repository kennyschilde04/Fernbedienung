package de.lightweb.fernbedienung.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import de.lightweb.fernbedienung.data.Device
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.ArrayDeque

/**
 * Sucht per mDNS nach Android-TV-/Google-TV-Geraeten im gleichen WLAN.
 * Der Dienst heisst "_androidtvremote2._tcp" und wird von Beamern mit
 * Chromecast/Google TV angeboten, sobald der Remote-Dienst laeuft.
 */
object Discovery {

    const val SERVICE_TYPE = "_androidtvremote2._tcp."

    fun devices(context: Context): Flow<List<Device>> = callbackFlow {
        val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val found = LinkedHashMap<String, Device>()

        // NsdManager kann immer nur einen resolve() gleichzeitig, deshalb eine Warteschlange.
        val queue = ArrayDeque<NsdServiceInfo>()
        val lock = Any()
        var resolving = false

        fun emit() {
            trySend(synchronized(lock) { found.values.sortedBy { it.name.lowercase() } })
        }

        fun resolveNext() {
            val next = synchronized(lock) {
                if (resolving) return
                val item = queue.poll() ?: return
                resolving = true
                item
            }
            nsd.resolveService(next, object : NsdManager.ResolveListener {
                override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }

                override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                    val host = serviceInfo.host?.hostAddress
                    if (host != null) {
                        synchronized(lock) {
                            found[serviceInfo.serviceName] = Device(
                                name = serviceInfo.serviceName ?: host,
                                host = host,
                                port = if (serviceInfo.port > 0) serviceInfo.port else 6466,
                            )
                        }
                        emit()
                    }
                    synchronized(lock) { resolving = false }
                    resolveNext()
                }
            })
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                close(IllegalStateException("Gerätesuche konnte nicht gestartet werden ($errorCode)"))
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
            override fun onDiscoveryStarted(serviceType: String?) = Unit
            override fun onDiscoveryStopped(serviceType: String?) = Unit

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { queue.add(serviceInfo) }
                resolveNext()
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                synchronized(lock) { found.remove(serviceInfo.serviceName) }
                emit()
            }
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        emit()

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
    }
}
