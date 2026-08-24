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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.KeyCodes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtraKeysScreen(
    enabled: Boolean,
    haptic: Boolean,
    onKey: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var custom by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        SectionTitle("Weitere Tasten", Modifier.fillMaxWidth())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KeyCodes.EXTRA.forEach { (label, code) ->
                TextKey(label = label, onClick = { onKey(code) }, enabled = enabled, haptic = haptic)
            }
        }

        Spacer(Modifier.height(28.dp))
        SectionTitle("Eigener Tastencode", Modifier.fillMaxWidth())
        Text(
            "Für Spezialfälle: Nummer eines Android-Tastencodes (z. B. 243 für HDMI 1).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = custom,
                onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                label = { Text("Code") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = { custom.toIntOrNull()?.let(onKey) },
                enabled = enabled && custom.isNotBlank(),
            ) { Text("Senden") }
        }
        Spacer(Modifier.height(24.dp))
    }
}
