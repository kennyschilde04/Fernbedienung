package de.lightweb.fernbedienung.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Taste, die beim Gedrückthalten den Befehl wiederholt (Pfeiltasten, Lautstärke). */
@Composable
fun HoldButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
    repeatable: Boolean = false,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable () -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    Surface(
        modifier = modifier.pointerInput(enabled, repeatable, onClick) {
            if (!enabled) return@pointerInput
            detectTapGestures(
                onPress = {
                    if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                    if (repeatable) {
                        coroutineScope {
                            val repeater = launch {
                                delay(450)
                                while (true) {
                                    onClick()
                                    delay(140)
                                }
                            }
                            tryAwaitRelease()
                            repeater.cancel()
                        }
                    } else {
                        tryAwaitRelease()
                    }
                },
            )
        },
        shape = shape,
        color = if (enabled) color else color.copy(alpha = 0.35f),
        contentColor = contentColor,
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun IconKey(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    enabled: Boolean = true,
    haptic: Boolean = true,
    repeatable: Boolean = false,
    showLabel: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        HoldButton(
            onClick = onClick,
            modifier = Modifier.size(size),
            enabled = enabled,
            haptic = haptic,
            repeatable = repeatable,
            color = color,
            contentColor = contentColor,
        ) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(size * 0.44f))
        }
        if (showLabel) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun TextKey(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    haptic: Boolean = true,
    color: Color = MaterialTheme.colorScheme.surfaceVariant,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    HoldButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        haptic = haptic,
        shape = RoundedCornerShape(14.dp),
        color = color,
        contentColor = contentColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(bottom = 8.dp),
    )
}

@Composable
fun KeyRow(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
