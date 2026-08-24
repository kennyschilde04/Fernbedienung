package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.Status
import de.lightweb.fernbedienung.UiState
import de.lightweb.fernbedienung.data.Device

@Composable
fun ConnectScreen(
    state: UiState,
    devices: List<Device>,
    onConnect: (Device) -> Unit,
    onPair: (Device) -> Unit,
    modifier: Modifier = Modifier,
) {
    var manualHost by remember { mutableStateOf(state.device?.host.orEmpty()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Beamer im WLAN suchen",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Handy und Beamer müssen im selben WLAN sein. Der Beamer muss eingeschaltet sein.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        if (devices.isEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp).padding(end = 12.dp), strokeWidth = 2.dp)
                Text("Suche läuft …", style = MaterialTheme.typography.bodyMedium)
            }
        }

        devices.forEach { device ->
            DeviceRow(
                device = device,
                selected = state.device?.host == device.host,
                onConnect = { onConnect(device) },
                onPair = { onPair(device) },
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Oder IP-Adresse eingeben", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = manualHost,
            onValueChange = { manualHost = it.trim() },
            label = { Text("z. B. 192.168.1.42") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { onPair(Device(manualHost, manualHost)) },
                enabled = manualHost.isNotBlank() && state.status != Status.PAIRING,
            ) { Text("Koppeln") }
            OutlinedButton(
                onClick = { onConnect(Device(manualHost, manualHost)) },
                enabled = manualHost.isNotBlank(),
            ) { Text("Verbinden") }
        }

        Spacer(Modifier.height(24.dp))
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("So funktioniert das Koppeln", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "1. Beamer einschalten\n" +
                        "2. Oben auf „Koppeln“ tippen\n" +
                        "3. Der Beamer zeigt einen 6-stelligen Code\n" +
                        "4. Code hier eingeben – fertig",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: Device,
    selected: Boolean,
    onConnect: () -> Unit,
    onPair: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onConnect() },
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) Icons.Filled.Cast else Icons.Filled.Tv,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    device.host,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onPair) { Text("Koppeln") }
        }
    }
}
