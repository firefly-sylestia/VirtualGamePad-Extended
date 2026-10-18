package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.RecordedMacro
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.MacroPlayer
import com.fireflysylestia.virtualgamepadextended.ui.utils.MacroRecorder
import io.github.kitswas.VGP_Data_Exchange.GamepadReading

/**
 * The pad's own record control, sat next to HOLD.
 *
 * It is a phone-only control like HOLD and sends nothing to the PC: tapping it
 * opens the recorder panel, and the icon inside it says what the pad is doing —
 * a filled dot while idle, a stop square while a take is being recorded.
 */
@Composable
fun RecorderButton(
    panelOpen: Boolean,
    recording: Boolean,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
) {
    val view = LocalView.current
    val skin = LocalGamepadSkin.current
    val accent = if (recording) {
        MaterialTheme.colorScheme.error
    } else if (panelOpen) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    OutlinedButton(
        onClick = {
            onTap()
            HapticUtils.performButtonPressFeedback(view)
        },
        modifier = modifier.size(width = metrics.width, height = metrics.height),
        shape = RoundedCornerShape(skin.cornerRadius),
        border = BorderStroke(
            if (recording || panelOpen) skin.pressedBorderWidth else skin.borderWidth,
            accent,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (recording || panelOpen) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (recording) {
                MaterialTheme.colorScheme.error
            } else if (panelOpen) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
            contentDescription = stringResource(R.string.component_recorder),
            modifier = Modifier.size(metrics.height * 0.6f),
        )
    }
}

/**
 * The recorder as a slim pill across the middle of the pad's top edge.
 *
 * It is deliberately not a dialog: the whole point is to perform a combination
 * *on the pad* while the recorder watches, so a modal one would block the very
 * controls being recorded. It is a row of icons — record/stop, repeat, play —
 * and a chevron that puts it away, with no labels to read: the pill stays
 * compact so it hides as little of the pad as possible, and the REC control on
 * the pad (or the chevron) brings it back. The take that plays is the last one
 * saved; takes live on the phone and nothing about them reaches the PC.
 */
@Composable
fun MacroRecorderPanel(
    recorder: MacroRecorder,
    player: MacroPlayer,
    recorded: List<RecordedMacro>,
    gamepadState: GamepadReading,
    onSave: (RecordedMacro) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledIconButton(
                onClick = {
                    if (recorder.isRecording) {
                        recorder.stop(nextRecordingName(recorded))?.let(onSave)
                    } else {
                        player.stop()
                        recorder.start(gamepadState)
                    }
                },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (recorder.isRecording) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = if (recorder.isRecording) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    },
                ),
            ) {
                Icon(
                    imageVector = if (recorder.isRecording) {
                        Icons.Filled.Stop
                    } else {
                        Icons.Filled.FiberManualRecord
                    },
                    contentDescription = if (recorder.isRecording) {
                        "Stop recording and save"
                    } else {
                        "Start recording"
                    },
                )
            }

            FilledIconButton(
                onClick = { player.toggleRepeating() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (player.isRepeating) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    contentColor = if (player.isRepeating) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Repeat,
                    contentDescription = if (player.isRepeating) {
                        "Repeat: on, playback starts over"
                    } else {
                        "Repeat: off"
                    },
                )
            }

            val latest = player.lastRecording
            FilledIconButton(
                onClick = { if (player.isPlaying) player.stop() else latest?.let(player::play) },
                enabled = latest != null,
            ) {
                Icon(
                    imageVector = if (player.isPlaying) {
                        Icons.Filled.Stop
                    } else {
                        Icons.Filled.PlayArrow
                    },
                    contentDescription = if (player.isPlaying) {
                        "Stop playback"
                    } else {
                        "Play the last take"
                    },
                )
            }

            IconButton(
                onClick = {
                    player.stop()
                    recorder.cancel()
                    onDismiss()
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.ExpandLess,
                    contentDescription = "Collapse the recorder",
                )
            }
        }
    }
}

/** `Macro 3` for the third saved take, so the list never reuses a name. */
private fun nextRecordingName(recorded: List<RecordedMacro>): String {
    val used = recorded.mapNotNull { it.name.removePrefix("Macro ").toIntOrNull() }
    return "Macro ${(used.maxOrNull() ?: 0) + 1}"
}
