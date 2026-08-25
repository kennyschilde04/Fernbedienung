package de.lightweb.fernbedienung.data

/**
 * Android-TV-Geräte schalten den HDMI-Eingang nicht über Tastencodes um, sondern
 * über einen "Passthrough"-Link des TV-Input-Frameworks:
 *
 *     content://android.media.tv/passthrough/<Paket>/<Dienst>/<Kennung>
 *
 * Paket, Dienst und Kennung hängen vom Chipsatz des Geräts ab. Weil es keine
 * Möglichkeit gibt, sie über die Fernbedienungs-Verbindung abzufragen, probiert
 * die App die bekannten Varianten der Reihe nach durch.
 */
data class HdmiCandidate(val label: String, val family: String, val packageName: String, val link: String)

object HdmiInputs {

    private fun passthrough(pkg: String, service: String, hw: String) =
        "content://android.media.tv/passthrough/$pkg/$service/$hw"

    private fun amlogic(): List<HdmiCandidate> {
        val pkg = "com.droidlogic.tvinput"
        val result = mutableListOf<HdmiCandidate>()
        listOf(1, 2, 3).forEach { port ->
            listOf("HW${port + 4}", "HW$port").forEach { hw ->
                result += HdmiCandidate(
                    label = "HDMI $port (Amlogic, $hw)",
                    family = "Amlogic / droidlogic",
                    packageName = pkg,
                    link = passthrough(pkg, ".services.Hdmi${port}InputService", hw),
                )
            }
        }
        return result
    }

    private fun mediatek(): List<HdmiCandidate> {
        val pkg = "com.mediatek.tvinput"
        return (1..4).map { port ->
            HdmiCandidate(
                label = "HDMI $port (MediaTek, HW${port + 4})",
                family = "MediaTek",
                packageName = pkg,
                link = passthrough(pkg, ".hdmi.HDMIInputService", "HW${port + 4}"),
            )
        }
    }

    private fun hisense(): List<HdmiCandidate> {
        val pkg = "com.hisense.tv.hitvinput"
        return (1..4).map { port ->
            HdmiCandidate(
                label = "HDMI $port (Hisense, HW${port + 4})",
                family = "Hisense",
                packageName = pkg,
                link = passthrough(pkg, ".hdmi.HdmiTvInputService", "HW${port + 4}"),
            )
        }
    }

    private fun tcl(): List<HdmiCandidate> {
        val pkg = "com.tcl.tvinput"
        return listOf("HW1413744128", "HW5", "HW6").mapIndexed { index, hw ->
            HdmiCandidate(
                label = "HDMI ${index + 1} (TCL, $hw)",
                family = "TCL",
                packageName = pkg,
                link = passthrough(pkg, ".passthroughinput.TvPassThroughService", hw),
            )
        }
    }

    /** Alle bekannten Varianten. Amlogic zuerst - das benutzen die meisten Beamer. */
    val ALL: List<HdmiCandidate> = amlogic() + mediatek() + hisense() + tcl()

    /**
     * Sortiert die Varianten so, dass die zum gemeldeten Paket passenden zuerst kommen.
     * [runningApp] stammt aus der Anzeige "Laufende App" - wer einmal mit der
     * Original-Fernbedienung auf HDMI wechselt, verrät der App damit den Hersteller.
     */
    fun orderedFor(runningApp: String?): List<HdmiCandidate> {
        val hint = runningApp?.substringBefore('/')?.takeIf { it.isNotBlank() } ?: return ALL
        val hintVendor = vendorOf(hint)
        val (matching, rest) = ALL.partition { candidate ->
            candidate.packageName == hint || (hintVendor != null && vendorOf(candidate.packageName) == hintVendor)
        }
        return matching + rest
    }

    /** "com.droidlogic.tvinput" -> "droidlogic". Der Herstellerteil des Paketnamens. */
    private fun vendorOf(packageName: String): String? =
        packageName.split('.').getOrNull(1)?.takeIf { it.isNotBlank() }
}
