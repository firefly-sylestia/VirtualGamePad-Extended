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
import io.github.kitswas.virtualgamepadmobile.data.ControllerAction
import io.github.kitswas.virtualgamepadmobile.data.defaultControllerBindings
import io.github.kitswas.virtualgamepadmobile.ui.utils.GamepadButtonDispatcher

enum class ExtraButtonClusterType {
    PLUS_MINUS,
    HOME_CAPTURE,
    SIDE_BUTTONS,
    REAR_PADDLES,
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
    minSize: Dp,
    actionMask: Int,
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    if (isPressed) {
        DisposableEffect(actionMask) {
            Log.d("ExtraButton", "${gameButton.name} pressed")
            GamepadButtonDispatcher.press(gamepadState, "extra_${gameButton.name}", actionMask)
            HapticUtils.performButtonPressFeedback(view)
            onDispose {
                GamepadButtonDispatcher.release(gamepadState, "extra_${gameButton.name}")
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
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
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
                actionMask = bindings[spec.bindingKey]?.mask ?: spec.button.value,
            )
        }
    }
}
