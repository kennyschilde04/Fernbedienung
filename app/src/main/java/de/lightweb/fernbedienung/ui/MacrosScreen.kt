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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * einer Taste ab. Fehlt am Ende ein Schritt, lässt er sich anhängen, statt
 * alles neu aufzunehmen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MacrosScreen(
    macros: List<Macro>,
    connected: Boolean,
    running: String?,
    onStartRecording: () -> Unit,
    onAppendSteps: (Macro) -> Unit,
    onRun: (Macro) -> Unit,
    onStop: () -> Unit,
    onDelete: (Macro) -> Unit,
    onDropLastStep: (Macro) -> Unit,
    onAppendPause: (Macro) -> Unit,
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
                Text("So funktioniert es", style = MaterialTheme.typography.titleSmall)
                Text(
                    "1. „Neue Folge aufnehmen“ tippen – die App springt zur Fernbedienung und " +
                        "beginnt auf dem Startbildschirm.\n" +
                        "2. Ganz normal zum Ziel navigieren. Jeder Tastendruck wird mitgeschrieben.\n" +
                        "3. Oben auf „Aufnahme beenden“ tippen und einen Namen vergeben.\n\n" +
                        "Bleibt die Folge beim Abspielen zu früh stehen, hilft meist ein langsameres " +
                        "Tempo oder eine eingefügte Pause – die Oberfläche des Beamers braucht " +
                        "manchmal länger, als man beim Tippen selbst wartet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onStartRecording,
            enabled = connected,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Neue Folge aufnehmen") }
        if (!connected) {
            Text(
                "Dafür muss die Verbindung zur Fernbedienung stehen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Gespeicherte Folgen", Modifier.fillMaxWidth())

        if (macros.isEmpty()) {
            Text(
                "Noch nichts aufgenommen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        macros.forEach { macro ->
            val isRunning = running == macro.name
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                colors = if (isRunning) {
                    CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                } else {
                    CardDefaults.cardColors()
                },
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(macro.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${macro.steps.size} Schritte",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )

                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        macro.steps.forEachIndexed { position, step ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text("${position + 1}. ${Macro.label(step)}") },
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    if (isRunning) {
                        Button(
                            onClick = onStop,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text("Läuft – anhalten") }
                    } else {
                        Button(
                            onClick = { onRun(macro) },
                            enabled = connected,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Abspielen") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Bearbeiten", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Fehlt am Ende ein Schritt – etwa das letzte „OK“ –, hänge ihn an, " +
                            "statt neu aufzunehmen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { onAppendSteps(macro) }, enabled = connected) {
                            Text("Schritte anhängen")
                        }
                        OutlinedButton(
                            onClick = { onDropLastStep(macro) },
                            enabled = macro.steps.isNotEmpty(),
                        ) { Text("Letzten löschen") }
                        OutlinedButton(onClick = { onAppendPause(macro) }) { Text("Pause anhängen") }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text("Tempo zwischen den Tasten", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Macro.SPEEDS.forEach { (label, delay) ->
                            FilterChip(
                                selected = macro.delayMs == delay,
                                onClick = { onChangeSpeed(macro, delay) },
                                label = { Text("$label (${delay} ms)") },
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onDelete(macro) }) { Text("Folge löschen") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
