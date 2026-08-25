package de.lightweb.fernbedienung.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Aufgezeichnete Tastenfolge.
 *
 * Manche Beamer haben für Dinge wie den HDMI-Eingang keine Taste und keinen Link,
 * sondern nur einen Punkt in ihrer eigenen Oberfläche. Dorthin führt nur der Weg
 * über das Steuerkreuz - und genau den kann man einmal aufnehmen und danach auf
 * Knopfdruck abspielen.
 */
data class Macro(
    val name: String,
    val steps: List<Int>,
    val delayMs: Int = DEFAULT_DELAY_MS,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name)
        .put("steps", JSONArray().also { array -> steps.forEach(array::put) })
        .put("delayMs", delayMs)

    companion object {
        const val DEFAULT_DELAY_MS = 700

        /** Vorgaben für die Wartezeit zwischen zwei Tasten. */
        val SPEEDS: List<Pair<String, Int>> = listOf(
            "Schnell" to 400,
            "Normal" to 700,
            "Langsam" to 1200,
        )

        fun fromJson(o: JSONObject): Macro {
            val array = o.getJSONArray("steps")
            return Macro(
                name = o.getString("name"),
                steps = (0 until array.length()).map { array.getInt(it) },
                delayMs = o.optInt("delayMs", DEFAULT_DELAY_MS),
            )
        }

        /** Klartext für die Anzeige einer Tastenfolge. */
        fun describe(steps: List<Int>): String = steps.joinToString(" → ") { label(it) }

        fun label(keyCode: Int): String = when (keyCode) {
            KeyCodes.HOME -> "Home"
            KeyCodes.BACK -> "Zurück"
            KeyCodes.DPAD_UP -> "Hoch"
            KeyCodes.DPAD_DOWN -> "Runter"
            KeyCodes.DPAD_LEFT -> "Links"
            KeyCodes.DPAD_RIGHT -> "Rechts"
            KeyCodes.DPAD_CENTER -> "OK"
            KeyCodes.MENU -> "Menü"
            KeyCodes.SETTINGS -> "Einstellungen"
            KeyCodes.SEARCH -> "Suche"
            KeyCodes.TV_INPUT -> "Quelle"
            KeyCodes.MEDIA_PLAY_PAUSE -> "Play/Pause"
            KeyCodes.VOLUME_UP -> "Lauter"
            KeyCodes.VOLUME_DOWN -> "Leiser"
            KeyCodes.VOLUME_MUTE -> "Stumm"
            KeyCodes.POWER -> "Ein/Aus"
            else -> "Taste $keyCode"
        }
    }
}

/**
 * Direkt über ADB abgesetzter Befehl, z. B. "am start -n paket/activity".
 * Liegt als eigene Taste auf der Fernbedienung.
 */
data class AdbAction(val name: String, val command: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("command", command)

    companion object {
        fun fromJson(o: JSONObject): AdbAction = AdbAction(o.getString("name"), o.getString("command"))
    }
}
