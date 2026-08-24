package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.lightweb.fernbedienung.Status
import de.lightweb.fernbedienung.UiState
import de.lightweb.fernbedienung.data.AppShortcut
import de.lightweb.fernbedienung.data.KeyCodes

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteScreen(
    state: UiState,
    apps: List<AppShortcut>,
    haptic: Boolean,
    onKey: (Int) -> Unit,
    onApp: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabled = state.status == Status.CONNECTED

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Ein/Aus, Zurück, Home, Suche
        KeyRow {
            IconKey(
                icon = Icons.Filled.PowerSettingsNew,
                label = "Ein/Aus",
                onClick = { onKey(KeyCodes.POWER) },
                enabled = enabled,
                haptic = haptic,
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.22f),
                contentColor = MaterialTheme.colorScheme.error,
            )
            IconKey(
                icon = Icons.Filled.ArrowBack,
                label = "Zurück",
                onClick = { onKey(KeyCodes.BACK) },
                enabled = enabled,
                haptic = haptic,
            )
            IconKey(
                icon = Icons.Filled.Home,
                label = "Home",
                onClick = { onKey(KeyCodes.HOME) },
                enabled = enabled,
                haptic = haptic,
            )
            IconKey(
                icon = Icons.Filled.Search,
                label = "Suche",
                onClick = { onKey(KeyCodes.SEARCH) },
                enabled = enabled,
                haptic = haptic,
            )
        }

        Spacer(Modifier.height(20.dp))

        DPad(enabled = enabled, haptic = haptic, onKey = onKey)

        Spacer(Modifier.height(20.dp))

        // Lautstärke
        KeyRow {
            IconKey(
                icon = Icons.Filled.VolumeDown,
                label = "Leiser",
                onClick = { onKey(KeyCodes.VOLUME_DOWN) },
                enabled = enabled,
                haptic = haptic,
                repeatable = true,
            )
            IconKey(
                icon = Icons.Filled.VolumeOff,
                label = "Stumm",
                onClick = { onKey(KeyCodes.VOLUME_MUTE) },
                enabled = enabled,
                haptic = haptic,
            )
            IconKey(
                icon = Icons.Filled.VolumeUp,
                label = "Lauter",
                onClick = { onKey(KeyCodes.VOLUME_UP) },
                enabled = enabled,
                haptic = haptic,
                repeatable = true,
            )
        }

        state.volume?.let { volume ->
            Text(
                text = if (volume.muted) {
                    "Ton aus"
                } else {
                    "Lautstärke ${volume.level}${if (volume.max > 0) " / ${volume.max}" else ""}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Wiedergabe
        KeyRow {
            IconKey(
                icon = Icons.Filled.SkipPrevious,
                label = "",
                onClick = { onKey(KeyCodes.MEDIA_PREVIOUS) },
                size = 48.dp,
                enabled = enabled,
                haptic = haptic,
                showLabel = false,
            )
            IconKey(
                icon = Icons.Filled.FastRewind,
                label = "",
                onClick = { onKey(KeyCodes.MEDIA_REWIND) },
                size = 48.dp,
                enabled = enabled,
                haptic = haptic,
                showLabel = false,
            )
            IconKey(
                icon = Icons.Filled.PlayArrow,
                label = "",
                onClick = { onKey(KeyCodes.MEDIA_PLAY_PAUSE) },
                size = 64.dp,
                enabled = enabled,
                haptic = haptic,
                showLabel = false,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            IconKey(
                icon = Icons.Filled.FastForward,
                label = "",
                onClick = { onKey(KeyCodes.MEDIA_FAST_FORWARD) },
                size = 48.dp,
                enabled = enabled,
                haptic = haptic,
                showLabel = false,
            )
            IconKey(
                icon = Icons.Filled.SkipNext,
                label = "",
                onClick = { onKey(KeyCodes.MEDIA_NEXT) },
                size = 48.dp,
                enabled = enabled,
                haptic = haptic,
                showLabel = false,
            )
        }

        Spacer(Modifier.height(26.dp))

        SectionTitle("Apps", Modifier.fillMaxWidth())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            apps.forEach { app ->
                TextKey(
                    label = app.name,
                    onClick = { onApp(app.link) },
                    enabled = enabled,
                    haptic = haptic,
                    color = Color(app.color).copy(alpha = 0.9f),
                    contentColor = Color.White,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionTitle("Eingang", Modifier.fillMaxWidth())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextKey("Quelle", { onKey(KeyCodes.TV_INPUT) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 1", { onKey(KeyCodes.TV_INPUT_HDMI_1) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 2", { onKey(KeyCodes.TV_INPUT_HDMI_2) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 3", { onKey(KeyCodes.TV_INPUT_HDMI_3) }, enabled = enabled, haptic = haptic)
            TextKey("HDMI 4", { onKey(KeyCodes.TV_INPUT_HDMI_4) }, enabled = enabled, haptic = haptic)
        }

        if (state.currentApp.isNotBlank()) {
            Text(
                text = "Läuft gerade: ${state.currentApp}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 20.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DPad(enabled: Boolean, haptic: Boolean, onKey: (Int) -> Unit) {
    Box(
        modifier = Modifier.size(250.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 1f else 0.4f),
            content = {},
        )

        HoldButton(
            onClick = { onKey(KeyCodes.DPAD_UP) },
            modifier = Modifier.size(78.dp).align(Alignment.TopCenter),
            enabled = enabled,
            haptic = haptic,
            repeatable = true,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Hoch",
                modifier = Modifier.size(38.dp),
            )
        }

        HoldButton(
            onClick = { onKey(KeyCodes.DPAD_DOWN) },
            modifier = Modifier.size(78.dp).align(Alignment.BottomCenter),
            enabled = enabled,
            haptic = haptic,
            repeatable = true,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Runter",
                modifier = Modifier.size(38.dp),
            )
        }

        HoldButton(
            onClick = { onKey(KeyCodes.DPAD_LEFT) },
            modifier = Modifier.size(78.dp).align(Alignment.CenterStart),
            enabled = enabled,
            haptic = haptic,
            repeatable = true,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Links",
                modifier = Modifier.size(38.dp),
            )
        }

        HoldButton(
            onClick = { onKey(KeyCodes.DPAD_RIGHT) },
            modifier = Modifier.size(78.dp).align(Alignment.CenterEnd),
            enabled = enabled,
            haptic = haptic,
            repeatable = true,
            color = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            androidx.compose.material3.Icon(
                Icons.Filled.KeyboardArrowRight,
                contentDescription = "Rechts",
                modifier = Modifier.size(38.dp),
            )
        }

        HoldButton(
            onClick = { onKey(KeyCodes.DPAD_CENTER) },
            modifier = Modifier.size(96.dp),
            enabled = enabled,
            haptic = haptic,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Text("OK", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}
