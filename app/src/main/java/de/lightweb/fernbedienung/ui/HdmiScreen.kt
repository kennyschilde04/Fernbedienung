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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.HdmiInputs
import de.lightweb.fernbedienung.data.KeyCodes
import kotlinx.coroutines.delay

/**
 * Einrichtung des HDMI-Eingangs. Android-TV-Geräte (auch Beamer) reagieren meist
 * nicht auf die HDMI-Tastencodes eines Fernsehers - der Eingang wird stattdessen
 * über einen Passthrough-Link geöffnet, dessen genaue Form vom Gerät abhängt.
 * Deshalb probiert dieser Bildschirm die bekannten Varianten durch.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HdmiScreen(
    enabled: Boolean,
    haptic: Boolean,
    runningApp: String,
    savedLink: String?,
    onSendLink: (String) -> Unit,
    onSendKey: (Int) -> Unit,
    onSave: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val candidates = remember(runningApp) { HdmiInputs.orderedFor(runningApp.ifBlank { null }) }
    var index by remember { mutableStateOf(0) }
    var auto by remember { mutableStateOf(false) }
    var ownLink by remember { mutableStateOf(savedLink.orEmpty()) }

    val current = candidates[index.coerceIn(0, candidates.lastIndex)]

    LaunchedEffect(auto, index, enabled) {
        if (!auto || !enabled) return@LaunchedEffect
        onSendLink(current.link)
        delay(2500)
        if (index < candidates.lastIndex) {
            index++
        } else {
            auto = false
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
                "laufen dort meist ins Leere. Der Eingang wird stattdessen über einen internen Link " +
                "geöffnet, der je nach Chipsatz anders heißt. Die App probiert die bekannten Varianten durch.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
                        Button(onClick = { onSendLink(savedLink) }, enabled = enabled) { Text("Umschalten") }
                        TextButton(onClick = { onSave(null) }) { Text("Vergessen") }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Schritt für Schritt suchen", Modifier.fillMaxWidth())
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
                LinearProgressIndicator(
                    progress = { (index + 1f) / candidates.size },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSendLink(current.link) }, enabled = enabled) { Text("Senden") }
                    OutlinedButton(
                        onClick = { if (index < candidates.lastIndex) index++ },
                        enabled = index < candidates.lastIndex,
                    ) { Text("Nächster") }
                    OutlinedButton(onClick = { if (index > 0) index-- }, enabled = index > 0) { Text("Zurück") }
                    Button(
                        onClick = { onSave(current.link); ownLink = current.link },
                        enabled = enabled,
                    ) { Text("Das war’s") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Automatisch durchprobieren", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { auto = it }, enabled = enabled)
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
                onClick = { onSendLink(ownLink.trim()) },
                enabled = enabled && ownLink.isNotBlank(),
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
            TextKey("Quelle", { onSendKey(KeyCodes.TV_INPUT) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 1", { onSendKey(KeyCodes.TV_INPUT_HDMI_1) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 2", { onSendKey(KeyCodes.TV_INPUT_HDMI_2) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 3", { onSendKey(KeyCodes.TV_INPUT_HDMI_3) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 4", { onSendKey(KeyCodes.TV_INPUT_HDMI_4) }, enabled = enabled, haptic = haptic)
        }

        Spacer(Modifier.height(20.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Tipp zur Eingrenzung", style = MaterialTheme.typography.titleSmall)
                Text(
                    "In den Einstellungen „Laufende App anzeigen“ einschalten, neu verbinden und einmal " +
                        "mit der Original-Fernbedienung auf HDMI wechseln. Die App zeigt dann, welche App " +
                        "dafür zuständig ist – die passenden Varianten rutschen hier nach oben.",
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
