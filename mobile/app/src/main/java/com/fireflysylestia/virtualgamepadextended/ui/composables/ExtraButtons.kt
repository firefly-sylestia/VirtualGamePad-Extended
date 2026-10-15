package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.GamepadGeometry
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings

enum class ExtraButtonClusterType {
    PLUS_MINUS,
    HOME_CAPTURE,
    SIDE_BUTTONS,
    REAR_PADDLES,
}

/**
 * The fixed footprint of one auxiliary button.
 *
 * These buttons use a size of their own instead of wrapping their text, so the
 * customisation screen can draw a tap target in exactly the same place. The
 * numbers come from [GamepadGeometry], which is also what
 * `GamepadLayoutTest` checks for overlaps.
 */
data class AuxiliaryButtonMetrics(
    val width: Dp,
    val height: Dp,
    val gap: Dp,
) {
    companion object {
        /** Metrics for a cluster of [count] buttons at the given layout unit. */
        fun of(baseDp: Float, count: Int, scale: Float = 1f) = AuxiliaryButtonMetrics(
            width = GamepadGeometry.extraButtonWidth(baseDp, scale).dp,
            height = GamepadGeometry.extraButtonHeight(baseDp, scale).dp,
            gap = (GamepadGeometry.EXTRA_GAP_FRACTION * baseDp * scale).dp,
        )
    }
}

private data class ExtraButtonSpec(val label: String, val button: GameButtons, val bindingKey: String)

private fun ExtraButtonClusterType.buttons(): List<ExtraButtonSpec> = when (this) {
    ExtraButtonClusterType.PLUS_MINUS -> listOf(
        ExtraButtonSpec("+", GameButtons.Plus, "plus"),
        ExtraButtonSpec("−", GameButtons.Minus, "minus"),
    )
    ExtraButtonClusterType.HOME_CAPTURE -> listOf(
        ExtraButtonSpec("HOME", GameButtons.Home, "home"),
        ExtraButtonSpec("CAP", GameButtons.Capture, "capture"),
    )
    ExtraButtonClusterType.SIDE_BUTTONS -> listOf(
        ExtraButtonSpec("SL", GameButtons.LeftSide, "left_side"),
        ExtraButtonSpec("SR", GameButtons.RightSide, "right_side"),
    )
    ExtraButtonClusterType.REAR_PADDLES -> listOf(
        ExtraButtonSpec("P1", GameButtons.Paddle1, "paddle_1"),
        ExtraButtonSpec("P2", GameButtons.Paddle2, "paddle_2"),
        ExtraButtonSpec("P3", GameButtons.Paddle3, "paddle_3"),
        ExtraButtonSpec("P4", GameButtons.Paddle4, "paddle_4"),
    )
}

@Composable
private fun ExtraButton(
    label: String,
    gameButton: GameButtons,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    actionMask: Int,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Held for as long as the finger is down; never latches on.
    PressAndHold(
        pressed = isPressed,
        source = "extra_${gameButton.name}",
        gamepadState = gamepadState,
        mask = actionMask,
    )

    OutlinedButton(
        onClick = {},
        modifier = Modifier.size(width = metrics.width, height = metrics.height),
        border = BorderStroke(
            if (isPressed) 3.dp else 2.dp,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            // A `surface` button on the `surface` gamepad has no visible shape, and
            // the label has to take the on-colour of whichever container it lands on.
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

/** Small independent controls for Nintendo system buttons and configurable auxiliary inputs. */
@Composable
fun ExtraButtonCluster(
    type: ExtraButtonClusterType,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(metrics.gap),
    ) {
        type.buttons().forEach { spec ->
            ExtraButton(
                label = spec.label,
                gameButton = spec.button,
                gamepadState = gamepadState,
                metrics = metrics,
                actionMask = bindings[spec.bindingKey]?.mask ?: spec.button.value,
            )
        }
    }
}
