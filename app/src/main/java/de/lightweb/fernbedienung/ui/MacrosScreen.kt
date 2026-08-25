package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.Macro

/**
 * Verwaltung der aufgezeichneten Tastenfolgen.
 *
 * Der Weg zum HDMI-Eingang führt bei vielen Beamern nur über deren eigene
 * Oberfläche. Diese Navigation nimmt man einmal auf und ruft sie danach mit
 * einer Taste ab.
 */
@Composable
fun MacrosScreen(
    macros: List<Macro>,
    connected: Boolean,
    onStartRecording: () -> Unit,
    onRun: (Macro) -> Unit,
    onDelete: (Macro) -> Unit,
    onChangeSpeed: (Macro, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("Wozu Tastenfolgen?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Wenn der HDMI-Eingang bei deinem Beamer nur ein Punkt in dessen eigener " +
                        "Oberfläche ist, gibt es dafür weder eine Taste noch einen Link – man muss " +
                        "hinnavigieren. Diesen Weg nimmst du einmal auf, danach genügt ein Tippen.\n\n" +
                        "Die Aufnahme beginnt auf dem Startbildschirm, damit der Ausgangspunkt beim " +
                        "Abspielen derselbe ist.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStartRecording,
            enabled = connected,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Neue Tastenfolge aufnehmen") }
        if (!connected) {
            Text(
                "Dafür muss die Verbindung stehen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Gespeichert", Modifier.fillMaxWidth())

        if (macros.isEmpty()) {
            Text(
                "Noch nichts aufgenommen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        macros.forEach { macro ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(macro.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${macro.steps.size} Schritte: ${Macro.describe(macro.steps)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        IconButton(onClick = { onDelete(macro) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text("Tempo", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Macro.SPEEDS.forEach { (label, delay) ->
                            FilterChip(
                                selected = macro.delayMs == delay,
                                onClick = { onChangeSpeed(macro, delay) },
                                label = { Text(label) },
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { onRun(macro) }, enabled = connected) { Text("Abspielen") }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Läuft die Folge zu schnell für den Beamer, stell das Tempo langsamer. " +
                "Bricht sie mittendrin ab, war die Verbindung weg – einfach noch einmal abspielen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
