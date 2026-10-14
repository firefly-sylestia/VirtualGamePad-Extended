package io.github.kitswas.virtualgamepadmobile.ui.composables

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import io.github.kitswas.virtualgamepadmobile.ui.utils.HapticUtils

enum class ExtraButtonClusterType {
    PLUS_MINUS,
    HOME_CAPTURE,
    SIDE_BUTTONS,
    REAR_PADDLES,
}

private data class ExtraButtonSpec(val label: String, val button: GameButtons)

private fun ExtraButtonClusterType.buttons(): List<ExtraButtonSpec> = when (this) {
    ExtraButtonClusterType.PLUS_MINUS -> listOf(
        ExtraButtonSpec("+", GameButtons.Plus),
        ExtraButtonSpec("−", GameButtons.Minus),
    )
    ExtraButtonClusterType.HOME_CAPTURE -> listOf(
        ExtraButtonSpec("HOME", GameButtons.Home),
        ExtraButtonSpec("CAP", GameButtons.Capture),
    )
    ExtraButtonClusterType.SIDE_BUTTONS -> listOf(
        ExtraButtonSpec("SL", GameButtons.LeftSide),
        ExtraButtonSpec("SR", GameButtons.RightSide),
    )
    ExtraButtonClusterType.REAR_PADDLES -> listOf(
        ExtraButtonSpec("P1", GameButtons.Paddle1),
        ExtraButtonSpec("P2", GameButtons.Paddle2),
        ExtraButtonSpec("P3", GameButtons.Paddle3),
        ExtraButtonSpec("P4", GameButtons.Paddle4),
    )
}

@Composable
private fun ExtraButton(
    label: String,
    gameButton: GameButtons,
    gamepadState: GamepadReading,
    minSize: Dp,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    if (isPressed) {
        DisposableEffect(gameButton) {
            Log.d("ExtraButton", "${gameButton.name} pressed")
            gamepadState.ButtonsDown = gamepadState.ButtonsDown or gameButton.value
            HapticUtils.performButtonPressFeedback(view)
            onDispose {
                gamepadState.ButtonsDown = gamepadState.ButtonsDown and gameButton.value.inv()
                gamepadState.ButtonsUp = gamepadState.ButtonsUp or gameButton.value
                HapticUtils.performButtonReleaseFeedback(view)
            }
        }
    }

    OutlinedButton(
        onClick = {},
        modifier = Modifier.sizeIn(minWidth = minSize, minHeight = minSize),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interactionSource,
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** Small independent controls for Nintendo system buttons and configurable auxiliary inputs. */
@Composable
fun ExtraButtonCluster(
    type: ExtraButtonClusterType,
    gamepadState: GamepadReading,
    minButtonSize: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        type.buttons().forEach { spec ->
            ExtraButton(
                label = spec.label,
                gameButton = spec.button,
                gamepadState = gamepadState,
                minSize = minButtonSize,
            )
        }
    }
}
