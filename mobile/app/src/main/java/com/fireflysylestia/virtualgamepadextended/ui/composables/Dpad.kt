package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings

enum class DpadButtonType {
    UP, DOWN, LEFT, RIGHT
}

@Composable
fun DpadButton(
    type: DpadButtonType,
    modifier: Modifier = Modifier,
    // The arrows take the on-colour of the container they are drawn on. They used
    // to be `primary` on a darkened `primary`, which is the same colour twice: in
    // the light palette the two came out close enough that the arrows vanished.
    foregroundColour: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    backgroundColour: Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp,
    gamepadState: GamepadReading,
    actionMask: Int? = null,
) {
    // Held down lights the control up, so the pressed pair is the strong one.
    val pressedBackground = MaterialTheme.colorScheme.primary
    val pressedForeground = MaterialTheme.colorScheme.onPrimary
    val view = LocalView.current
    val rotation = when (type) {
        DpadButtonType.UP -> -90f
        DpadButtonType.DOWN -> 90f
        DpadButtonType.LEFT -> 180f
        DpadButtonType.RIGHT -> 0f
    }
    val gameButton = when (type) {
        DpadButtonType.UP -> GameButtons.DPadUp
        DpadButtonType.DOWN -> GameButtons.DPadDown
        DpadButtonType.LEFT -> GameButtons.DPadLeft
        DpadButtonType.RIGHT -> GameButtons.DPadRight
    }
    val buttonMask = actionMask ?: gameButton.value
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Held for as long as the finger is down; never latches on.
    PressAndHold(
        pressed = isPressed,
        source = "dpad_${type.name}",
        gamepadState = gamepadState,
        mask = buttonMask,
    )

    OutlinedIconButton(
        modifier = modifier
            .size(size)
            .padding(0.dp),
        onClick = {},
        colors = IconButtonDefaults.outlinedIconButtonColors(
            // Lit up while held, so the press is visible on screen too.
            containerColor = if (isPressed) pressedBackground else backgroundColour,
            contentColor = if (isPressed) pressedForeground else foregroundColour,
        ),
        border = BorderStroke(
            if (isPressed) 3.dp else 2.dp,
            if (isPressed) pressedForeground else MaterialTheme.colorScheme.outline,
        ),
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_play_arrow),
            contentDescription = stringResource(R.string.content_desc_dpad_button, type.name),
            modifier = Modifier
                .rotate(rotation)
                .size(size),
            tint = if (isPressed) pressedForeground else foregroundColour
        )
    }
}

/**
 * A directional pad with up, down, left, and right buttons.
 */
@Composable
fun Dpad(
    modifier: Modifier = Modifier,
    size: Dp = 360.dp,
    gamepadState: GamepadReading,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
) {
    val buttonSize = 2 * size / 5
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        DpadButton(
            type = DpadButtonType.UP,
            modifier = Modifier.align(Alignment.TopCenter),
            size = buttonSize,
            gamepadState = gamepadState,
            actionMask = bindings["dpad_up"]?.mask,
        )
        DpadButton(
            type = DpadButtonType.DOWN,
            modifier = Modifier.align(Alignment.BottomCenter),
            size = buttonSize,
            gamepadState = gamepadState,
            actionMask = bindings["dpad_down"]?.mask,
        )
        DpadButton(
            type = DpadButtonType.LEFT,
            modifier = Modifier.align(Alignment.CenterStart),
            size = buttonSize,
            gamepadState = gamepadState,
            actionMask = bindings["dpad_left"]?.mask,
        )
        DpadButton(
            type = DpadButtonType.RIGHT,
            modifier = Modifier.align(Alignment.CenterEnd),
            size = buttonSize,
            gamepadState = gamepadState,
            actionMask = bindings["dpad_right"]?.mask,
        )
    }
}

@Preview(showBackground = false)
@Composable
fun DpadPreview() {
    Dpad(
        gamepadState = GamepadReading(),
    )
}
