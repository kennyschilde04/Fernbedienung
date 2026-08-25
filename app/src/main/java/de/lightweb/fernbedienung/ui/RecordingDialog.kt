package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.Macro

/** Fragt nach dem Namen, wenn eine Aufnahme beendet wird. */
@Composable
fun SaveRecordingDialog(
    steps: List<Int>,
    appendTo: String? = null,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember { mutableStateOf(appendTo ?: "HDMI") }

    AlertDialog(
        onDismissRequest = onDiscard,
        title = { Text(if (appendTo != null) "Schritte anhängen" else "Tastenfolge speichern") },
        text = {
            Column {
                Text(
                    "${steps.size} Schritte aufgenommen:",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    Macro.describe(steps),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (appendTo == null) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name der Taste") },
                        singleLine = true,
                    )
                } else {
                    Text(
                        "Werden an „$appendTo“ angehängt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank() && steps.isNotEmpty()) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDiscard) { Text("Verwerfen") } },
    )
}
