package de.lightweb.fernbedienung.data

import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject

data class Device(val name: String, val host: String, val port: Int = 6466)

data class AppShortcut(val name: String, val link: String, val color: Long) {
    companion object {
        /**
         * Standard-Verknuepfungen. Der Beamer oeffnet die App ueber diesen Deep-Link,
         * die App muss dafuer installiert sein.
         */
        val DEFAULTS: List<AppShortcut> = listOf(
            AppShortcut("YouTube", "https://www.youtube.com", 0xFFFF0000),
            AppShortcut("Netflix", "https://www.netflix.com/title", 0xFFE50914),
            AppShortcut("Prime Video", "https://app.primevideo.com", 0xFF00A8E1),
            AppShortcut("Disney+", "https://www.disneyplus.com", 0xFF113CCF),
            AppShortcut("ARD", "https://www.ardmediathek.de", 0xFF0A85D1),
            AppShortcut("ZDF", "https://www.zdf.de", 0xFFFA7D19),
            AppShortcut("Spotify", "spotify://", 0xFF1DB954),
            AppShortcut("Play Store", "https://play.google.com/store/apps", 0xFF34A853),
        )
    }
}

class Prefs(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("fernbedienung", Context.MODE_PRIVATE)

    var device: Device?
        get() {
            val host = prefs.getString(KEY_HOST, null) ?: return null
            return Device(
                name = prefs.getString(KEY_NAME, host).orEmpty().ifEmpty { host },
                host = host,
                port = prefs.getInt(KEY_PORT, 6466),
            )
        }
        set(value) {
            val editor = prefs.edit()
            if (value == null) {
                editor.remove(KEY_HOST)
                editor.remove(KEY_NAME)
                editor.remove(KEY_PORT)
            } else {
                editor.putString(KEY_HOST, value.host)
                editor.putString(KEY_NAME, value.name)
                editor.putInt(KEY_PORT, value.port)
            }
            editor.apply()
        }

    /** Name, der beim Pairing auf dem Beamer angezeigt wird. */
    var clientName: String
        get() = prefs.getString(KEY_CLIENT_NAME, null) ?: defaultClientName()
        set(value) = prefs.edit().putString(KEY_CLIENT_NAME, value).apply()

    /** IME meldet die gerade laufende App, kann auf manchen Geraeten die Bildschirmtastatur oeffnen. */
    var imeEnabled: Boolean
        get() = prefs.getBoolean(KEY_IME, false)
        set(value) = prefs.edit().putBoolean(KEY_IME, value).apply()

    /** Lautstärketasten des Handys an den Beamer weiterreichen. */
    var volumeKeysEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOLUME_KEYS, true)
        set(value) = prefs.edit().putBoolean(KEY_VOLUME_KEYS, value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC, value).apply()

    /** Gefundener Passthrough-Link fuer den HDMI-Eingang, siehe HdmiInputs. */
    var hdmiLink: String?
        get() = prefs.getString(KEY_HDMI_LINK, null)
        set(value) {
            val editor = prefs.edit()
            if (value.isNullOrBlank()) editor.remove(KEY_HDMI_LINK) else editor.putString(KEY_HDMI_LINK, value)
            editor.apply()
        }

    var apps: List<AppShortcut>
        get() {
            val raw = prefs.getString(KEY_APPS, null) ?: return AppShortcut.DEFAULTS
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    AppShortcut(o.getString("name"), o.getString("link"), o.optLong("color", 0xFF3F51B5))
                }
            }.getOrDefault(AppShortcut.DEFAULTS)
        }
        set(value) {
            val array = JSONArray()
            value.forEach {
                array.put(JSONObject().put("name", it.name).put("link", it.link).put("color", it.color))
            }
            prefs.edit().putString(KEY_APPS, array.toString()).apply()
        }

    fun resetApps() = prefs.edit().remove(KEY_APPS).apply()

    /** Aufgezeichnete Tastenfolgen, siehe Macro. */
    var macros: List<Macro>
        get() {
            val raw = prefs.getString(KEY_MACROS, null) ?: return emptyList()
            return runCatching {
                val array = JSONArray(raw)
                (0 until array.length()).map { Macro.fromJson(array.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
        set(value) {
            val array = JSONArray()
            value.forEach { array.put(it.toJson()) }
            prefs.edit().putString(KEY_MACROS, array.toString()).apply()
        }

    private fun defaultClientName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Handy"
        return "Fernbedienung ($model)"
    }

    companion object {
        private const val KEY_HOST = "host"
        private const val KEY_NAME = "name"
        private const val KEY_PORT = "port"
        private const val KEY_CLIENT_NAME = "client_name"
        private const val KEY_IME = "ime"
        private const val KEY_HAPTIC = "haptic"
        private const val KEY_VOLUME_KEYS = "volume_keys"
        private const val KEY_APPS = "apps"
        private const val KEY_HDMI_LINK = "hdmi_link"
        private const val KEY_MACROS = "macros"
    }
}
