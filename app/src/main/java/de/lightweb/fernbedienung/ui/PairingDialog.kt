package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp

@Composable
fun PairingDialog(
    deviceName: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Code eingeben") },
        text = {
            Column {
                Text(
                    text = "Auf dem Beamer${if (deviceName.isNotBlank()) " ($deviceName)" else ""} " +
                        "wird jetzt ein 6-stelliger Code angezeigt.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(androidx.compose.ui.Modifier.height(12.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { input ->
                        code = input.uppercase().filter { it.isDigit() || it in 'A'..'F' }.take(6)
                    },
                    label = { Text("Code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(code) }, enabled = code.length == 6) { Text("Koppeln") }
        },
        dismissButton = { TextButton(onClick = onCancel) { Text("Abbrechen") } },
    )
}
