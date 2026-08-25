package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.HdmiInputs
import de.lightweb.fernbedienung.data.KeyCodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Ergebnis eines Versuchs. */
private enum class Probe { SURVIVED, DROPPED }

/**
 * Einrichtung des HDMI-Eingangs.
 *
 * Android-TV-Beamer reagieren nicht auf die HDMI-Tastencodes eines Fernsehers.
 * Der Eingang wird über einen Passthrough-Link geöffnet, dessen genaue Form vom
 * Chipsatz abhängt - deshalb probiert dieser Bildschirm die bekannten Varianten durch.
 *
 * Kann der Beamer mit einem Link nichts anfangen, wirft sein Fernbedienungs-Dienst
 * eine Ausnahme und kappt die Verbindung. Das ist kein Fehler der App: sie verbindet
 * sich neu und macht weiter. Gleichzeitig ist genau das die beste Trefferanzeige -
 * bleibt die Verbindung nach einem Versuch bestehen, hat der Beamer den Link verstanden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdmiScreen(
    connected: Boolean,
    haptic: Boolean,
    runningApp: String,
    savedLink: String?,
    onSendLink: (String) -> Unit,
    onSendKey: (Int) -> Unit,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val candidates = remember(runningApp) { HdmiInputs.orderedFor(runningApp.ifBlank { null }) }
    val results = remember { mutableStateMapOf<String, Probe>() }
    var index by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var ownLink by remember { mutableStateOf(savedLink.orEmpty()) }

    val liveConnected by rememberUpdatedState(connected)
    val scope = rememberCoroutineScope()
    val current = candidates[index.coerceIn(0, candidates.lastIndex)]

    /**
     * Wartet auf die Verbindung, sendet den Link und schaut, ob die Verbindung hält.
     * Gibt zurück, ob die Verbindung bestehen blieb.
     */
    suspend fun probe(link: String): Boolean {
        busy = true
        try {
            var waited = 0
            while (!liveConnected && waited < 60) {
                delay(500)
                waited++
            }
            if (!liveConnected) return false
            onSendLink(link)
            delay(2500)
            val survived = liveConnected
            results[link] = if (survived) Probe.SURVIVED else Probe.DROPPED
            return survived
        } finally {
            busy = false
        }
    }

    LaunchedEffect(auto) {
        if (!auto) return@LaunchedEffect
        while (auto) {
            probe(candidates[index].link)
            if (!auto) break
            if (index < candidates.lastIndex) index++ else auto = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Beamer mit Android TV sind keine Fernseher – die HDMI-Tasten einer TV-Fernbedienung " +
                "laufen dort ins Leere. Der Eingang wird über einen internen Link geöffnet, der je " +
                "nach Chipsatz anders heißt. Die App probiert die bekannten Varianten durch.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Text(
                "Bei jedem Fehlversuch kappt der Beamer die Verbindung – das ist normal und kein " +
                    "Fehler der App. Sie verbindet sich automatisch neu und macht weiter. Genau " +
                    "darin liegt der Trick: Bleibt die Verbindung nach einem Versuch bestehen, hat " +
                    "der Beamer den Link verstanden.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (savedLink != null) {
            Spacer(Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gespeicherter Eingang", style = MaterialTheme.typography.titleSmall)
                    Text(
                        savedLink,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSendLink(savedLink) }, enabled = connected) { Text("Umschalten") }
                        TextButton(onClick = { onSave(null) }) { Text("Vergessen") }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Varianten durchprobieren", Modifier.fillMaxWidth())
        Text(
            "Schau dabei auf die Leinwand. Sobald das HDMI-Bild erscheint, auf „Das war’s“ tippen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Versuch ${index + 1} von ${candidates.size}", style = MaterialTheme.typography.labelMedium)
                Text(current.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 2.dp))
                Text(
                    current.link,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )

                val status = when {
                    busy && !liveConnected -> "Verbindung wird neu aufgebaut …"
                    busy -> "Wird gesendet …"
                    results[current.link] == Probe.SURVIVED ->
                        "Verbindung blieb bestehen – sehr wahrscheinlich der richtige Link"
                    results[current.link] == Probe.DROPPED ->
                        "Verbindung wurde gekappt – der Beamer kennt diesen Link nicht"
                    else -> "Noch nicht versucht"
                }
                Text(
                    status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (results[current.link] == Probe.SURVIVED) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 10.dp),
                )

                LinearProgressIndicator(
                    progress = { (index + 1f) / candidates.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )

                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { scope.launch { probe(current.link) } },
                        enabled = !busy && !auto,
                    ) { Text("Senden") }
                    OutlinedButton(
                        onClick = { if (index < candidates.lastIndex) index++ },
                        enabled = !auto && index < candidates.lastIndex,
                    ) { Text("Nächster") }
                    OutlinedButton(
                        onClick = { if (index > 0) index-- },
                        enabled = !auto && index > 0,
                    ) { Text("Zurück") }
                    Button(
                        onClick = { auto = false; onSave(current.link); ownLink = current.link },
                    ) { Text("Das war’s") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Automatisch durchprobieren",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }

                val dropped = results.count { it.value == Probe.DROPPED }
                val survived = results.entries.filter { it.value == Probe.SURVIVED }
                if (results.isNotEmpty()) {
                    Text(
                        "Bisher: $dropped abgelehnt, ${survived.size} überstanden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                survived.forEach { (link, _) ->
                    TextButton(onClick = { onSave(link); ownLink = link }) {
                        Text(
                            "Diesen merken: ${link.substringAfterLast('/')}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        SectionTitle("Eigenen Link eintragen", Modifier.fillMaxWidth())
        Text(
            "Wer den genauen Link kennt, trägt ihn hier ein. Am Rechner lässt er sich auslesen: " +
                "am Beamer einmal mit der Original-Fernbedienung auf HDMI wechseln, dann per ADB\n\n" +
                "adb shell dumpsys activity starter | grep passthrough",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = ownLink,
            onValueChange = { ownLink = it },
            label = { Text("content://android.media.tv/passthrough/…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { scope.launch { probe(ownLink.trim()) } },
                enabled = !busy && !auto && ownLink.isNotBlank(),
            ) { Text("Testen") }
            OutlinedButton(
                onClick = { onSave(ownLink.trim()) },
                enabled = ownLink.isNotBlank(),
            ) { Text("Speichern") }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        SectionTitle("Klassische Tasten", Modifier.fillMaxWidth())
        Text(
            "Diese Tastencodes funktionieren nur bei echten Fernsehern, nicht bei den meisten Beamern.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextKey("Quelle", { onSendKey(KeyCodes.TV_INPUT) }, enabled = connected, haptic = haptic)
            TextKey("HDMI 1", { onSendKey(KeyCodes.TV_INPUT_HDMI_1) }, enabled = connected, haptic = haptic)
            TextKey("HDMI 2", { onSendKey(KeyCodes.TV_INPUT_HDMI_2) }, enabled = connected, haptic = haptic)
            TextKey("HDMI 3", { onSendKey(KeyCodes.TV_INPUT_HDMI_3) }, enabled = connected, haptic = haptic)
            TextKey("HDMI 4", { onSendKey(KeyCodes.TV_INPUT_HDMI_4) }, enabled = connected, haptic = haptic)
        }

        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Wenn keine Variante passt", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Dann bleibt der Weg über den Startbildschirm: Home drücken und mit dem " +
                        "Steuerkreuz zur Eingangs-Kachel navigieren. Und in den Einstellungen " +
                        "„Laufende App anzeigen“ einschalten, neu verbinden und einmal mit der " +
                        "Original-Fernbedienung auf HDMI wechseln – die App zeigt dann, welche App " +
                        "zuständig ist, und sortiert die passenden Varianten nach oben.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                if (runningApp.isNotBlank()) {
                    Text(
                        "Zuletzt gemeldet: $runningApp",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
