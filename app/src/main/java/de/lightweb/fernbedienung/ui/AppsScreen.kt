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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.data.AppShortcut

@Composable
fun AppsScreen(
    apps: List<AppShortcut>,
    onSave: (List<AppShortcut>) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Der Beamer öffnet Apps über einen Link. Wenn eine App nicht startet, ist sie entweder " +
                "nicht installiert oder braucht einen anderen Link.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        apps.forEach { app ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(app.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            app.link,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onSave(apps - app) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Löschen")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("Verknüpfung hinzufügen") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text("Standard wiederherstellen") }
        Spacer(Modifier.height(24.dp))
    }

    if (showAdd) {
        var name by remember { mutableStateOf("") }
        var link by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("Neue Verknüpfung") },
            text = {
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        label = { Text("Link, z. B. https://www.youtube.com") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (name.isNotBlank() && link.isNotBlank()) {
                            onSave(apps + AppShortcut(name.trim(), link.trim(), 0xFF455A64))
                        }
                        showAdd = false
                    },
                ) { Text("Speichern") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Abbrechen") } },
        )
    }
}
