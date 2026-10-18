package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.CustomKeyBinding
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.normaliseCustomKeyBindings
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.KeyButtonDispatcher

/**
 * Buttons that send a PC key by name instead of a gamepad button.
 *
 * Minecraft is the reason these exist: it reads a keyboard and a mouse and knows
 * nothing about a controller, so chat, the inventory, the debug overlay and the
 * HUD toggle cannot be reached from a pad button. The key names travel in the
 * reading and the PC injects them through its own key-name table, which is what
 * makes a phone button reach a keyboard-only game.
 *
 * The button is held for as long as the finger is; a key is not a toggle.
 */
@Composable
fun CustomKeyCluster(
    bindings: List<CustomKeyBinding>,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
) {
    val slots = normaliseCustomKeyBindings(bindings)

    // Two rows of two, matching GamepadGeometry's 2x2 footprint for this
    // component: the block is narrow enough to sit beside the pads where a row
    // of four is not.
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(metrics.gap),
    ) {
        slots.chunked(2).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                row.forEachIndexed { columnIndex, binding ->
                    CustomKeyButton(
                        binding = binding,
                        source = "custom_key_${rowIndex * 2 + columnIndex}",
                        gamepadState = gamepadState,
                        metrics = metrics,
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomKeyButton(
    binding: CustomKeyBinding,
    source: String,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
) {
    val view = LocalView.current
    val skin = LocalGamepadSkin.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // The press is announced while the finger is down and taken back when it is
    // not, including when the key is edited mid-press or the control disappears.
    DisposableEffect(isPressed, binding.key) {
        if (isPressed) {
            KeyButtonDispatcher.press(gamepadState, source, binding.key)
            HapticUtils.performButtonPressFeedback(view)
        }
        onDispose {
            if (isPressed) {
                KeyButtonDispatcher.release(gamepadState, source)
                HapticUtils.performButtonReleaseFeedback(view)
            }
        }
    }

    val label = binding.label.ifBlank { binding.key }

    OutlinedButton(
        onClick = {},
        modifier = Modifier.size(width = metrics.width, height = metrics.height),
        shape = RoundedCornerShape(skin.cornerRadius),
        border = BorderStroke(
            if (isPressed) skin.pressedBorderWidth else skin.borderWidth,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),

        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
