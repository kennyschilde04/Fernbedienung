package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.AdbAction
import de.lightweb.fernbedienung.net.AdbSession

/**
 * Direkter Zugriff auf den Beamer über ADB.
 *
 * Damit lässt sich gezielt eine Activity starten - also genau das, was über die
 * Fernbedienungs-Verbindung fehlt, wenn der HDMI-Eingang nur ein Punkt in der
 * Oberfläche des Geräts ist.
 */
@Composable
fun AdbScreen(
    host: String,
    connected: Boolean,
    busy: Boolean,
    log: String,
    actions: List<AdbAction>,
    onConnect: (String) -> Unit,
    onDisconnect: () -> Unit,
    onReadCurrentActivity: () -> Unit,
    onListPackages: () -> Unit,
    onShell: (String) -> Unit,
    onRunAction: (AdbAction) -> Unit,
    onDeleteAction: (AdbAction) -> Unit,
    foundComponent: String?,
    packages: List<String>,
    onSaveComponent: (String, String) -> Unit,
    onDismissPackages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var address by remember(host) { mutableStateOf(host) }
    var command by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<String?>(null) }

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
                Text("Einmal am Beamer freischalten", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Einstellungen → Geräteeinstellungen → Info → siebenmal auf „Build“ tippen. " +
                        "Danach unter Entwickleroptionen „USB-Debugging“ einschalten. Beim ersten " +
                        "Verbinden fragt der Beamer auf der Leinwand nach Bestätigung – dort " +
                        "„Immer von diesem Computer zulassen“ wählen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    "Zur Sicherheit: Solange das aktiv ist, steht im WLAN ein Debug-Zugang offen. " +
                        "Wer das nicht dauerhaft möchte, schaltet USB-Debugging nach dem Einrichten " +
                        "wieder aus – die aufgezeichneten Tastenfolgen funktionieren weiterhin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it.trim() },
            label = { Text("IP-Adresse des Beamers") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Port ${AdbSession.DEFAULT_PORT}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onConnect(address) }, enabled = !busy && address.isNotBlank()) {
                Text(if (connected) "Neu verbinden" else "Verbinden")
            }
            OutlinedButton(onClick = onDisconnect, enabled = connected) { Text("Trennen") }
        }

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
        }

        if (log.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    log,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(14.dp).heightIn(max = 220.dp).verticalScroll(rememberScrollState()),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        SectionTitle("HDMI-Bildschirm einfangen", Modifier.fillMaxWidth())
        Text(
            "Öffne am Beamer den HDMI-Eingang so, wie du es sonst tust. Tippe dann hier – die App " +
                "liest aus, welcher Bildschirm gerade läuft, und legt ihn als Taste an. Danach " +
                "genügt ein Tippen, ohne Navigation.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onReadCurrentActivity,
            enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Laufenden Bildschirm auslesen") }

        if (foundComponent != null) {
            Spacer(Modifier.height(10.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Gefunden", style = MaterialTheme.typography.titleSmall)
                    Text(foundComponent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { naming = AdbSession.startActivityCommand(foundComponent) }) {
                            Text("Als Taste anlegen")
                        }
                        OutlinedButton(onClick = { onShell(AdbSession.startActivityCommand(foundComponent)) }) {
                            Text("Testen")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Apps des Beamers", Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = onListPackages,
            enabled = connected && !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("App-Liste auslesen") }

        Spacer(Modifier.height(24.dp))
        SectionTitle("Eigener Befehl", Modifier.fillMaxWidth())
        OutlinedTextField(
            value = command,
            onValueChange = { command = it },
            label = { Text("z. B. am start -n paket/activity") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { onShell(command.trim()) }, enabled = connected && !busy && command.isNotBlank()) {
                Text("Ausführen")
            }
            OutlinedButton(onClick = { naming = command.trim() }, enabled = command.isNotBlank()) {
                Text("Als Taste anlegen")
            }
        }

        Spacer(Modifier.height(24.dp))
        Divider()
        Spacer(Modifier.height(16.dp))

        SectionTitle("Angelegte Direktbefehle", Modifier.fillMaxWidth())
        if (actions.isEmpty()) {
            Text(
                "Noch keine angelegt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions.forEach { action ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(action.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            action.command,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRunAction(action) }, enabled = !busy) { Text("Start") }
                    IconButton(onClick = { onDeleteAction(action) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    naming?.let { pendingCommand ->
        NameCommandDialog(
            command = pendingCommand,
            onSave = { name ->
                onSaveComponent(name, pendingCommand)
                naming = null
            },
            onCancel = { naming = null },
        )
    }

    if (packages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = onDismissPackages,
            title = { Text("Apps auf dem Beamer") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    packages.forEach { pkg ->
                        TextButton(onClick = { naming = AdbSession.launchPackageCommand(pkg) }) {
                            Text(pkg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissPackages) { Text("Schließen") } },
        )
    }
}

@Composable
private fun NameCommandDialog(command: String, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("HDMI") }
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Als Taste anlegen") },
        text = {
            Column {
                Text(command, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name der Taste") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
