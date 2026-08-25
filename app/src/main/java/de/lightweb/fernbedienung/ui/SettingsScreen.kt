package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    clientName: String,
    haptic: Boolean,
    volumeKeys: Boolean,
    ime: Boolean,
    deviceLabel: String,
    onClientName: (String) -> Unit,
    onHaptic: (Boolean) -> Unit,
    onVolumeKeys: (Boolean) -> Unit,
    onIme: (Boolean) -> Unit,
    onEditApps: () -> Unit,
    onExtraKeys: () -> Unit,
    onHdmiSetup: () -> Unit,
    onMacros: () -> Unit,
    onAdb: () -> Unit,
    onForgetDevice: () -> Unit,
    onResetIdentity: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(clientName) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Verbindung", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            deviceLabel.ifBlank { "Kein Gerät gespeichert" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onClientName(it) },
            label = { Text("Name beim Koppeln") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Dieser Name erscheint auf dem Beamer, wenn gekoppelt wird.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))
        Divider()
        SettingSwitch("Vibration beim Tippen", haptic, onHaptic)
        SettingSwitch("Lautstärketasten des Handys benutzen", volumeKeys, onVolumeKeys)
        SettingSwitch(
            title = "Laufende App anzeigen",
            checked = ime,
            onCheckedChange = onIme,
            subtitle = "Braucht die Tastatur-Funktion des Beamers. Bei manchen Geräten erscheint dann " +
                "„Tastatur auf dem Handy benutzen“ auf dem Bildschirm. Nach dem Ändern neu verbinden.",
        )
        Divider()

        Spacer(Modifier.height(20.dp))
        Text("Anpassen", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onEditApps, modifier = Modifier.fillMaxWidth()) { Text("App-Verknüpfungen bearbeiten") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onExtraKeys, modifier = Modifier.fillMaxWidth()) { Text("Weitere Tasten") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHdmiSetup, modifier = Modifier.fillMaxWidth()) { Text("HDMI-Eingang einrichten") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onMacros, modifier = Modifier.fillMaxWidth()) { Text("Eigene Tasten (Tastenfolgen)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onAdb, modifier = Modifier.fillMaxWidth()) { Text("Direktzugriff (ADB)") }

        Spacer(Modifier.height(28.dp))
        Text("Problembehebung", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onForgetDevice) { Text("Gespeichertes Gerät vergessen") }
        TextButton(onClick = onResetIdentity) { Text("Kopplung zurücksetzen (neues Zertifikat)") }
        Text(
            "Wenn nichts mehr geht: am Beamer unter Einstellungen → Apps → Alle Apps anzeigen → " +
                "„Android TV Remote Service“ → Speicher → Daten löschen, danach hier neu koppeln.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
