package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils

enum class DpadButtonType {
    UP, DOWN, LEFT, RIGHT
}

/** Fraction of half the pad a finger has to leave the centre by to count. */
private const val SLIDE_DEAD_ZONE = 0.35f

/** Output a direction drives through the active bindings. */
private fun dpadMask(type: DpadButtonType, bindings: Map<String, ControllerAction>): Int =
    when (type) {
        DpadButtonType.UP -> bindings["dpad_up"]
        DpadButtonType.DOWN -> bindings["dpad_down"]
        DpadButtonType.LEFT -> bindings["dpad_left"]
        DpadButtonType.RIGHT -> bindings["dpad_right"]
    }?.mask ?: when (type) {
        DpadButtonType.UP -> GameButtons.DPadUp.value
        DpadButtonType.DOWN -> GameButtons.DPadDown.value
        DpadButtonType.LEFT -> GameButtons.DPadLeft.value
        DpadButtonType.RIGHT -> GameButtons.DPadRight.value
    }

/** Source name one direction is held under while the pad is being slid over. */
private fun dpadSlideSource(type: DpadButtonType) = "dpad_slide_${type.name}"

/** The directions a finger at (@p nx, @p ny), each in -1..1 from the centre, is over. */
private fun directionsAt(nx: Float, ny: Float): Set<DpadButtonType> {
    val pressed = mutableSetOf<DpadButtonType>()
    if (nx > SLIDE_DEAD_ZONE) pressed += DpadButtonType.RIGHT
    if (nx < -SLIDE_DEAD_ZONE) pressed += DpadButtonType.LEFT
    if (ny > SLIDE_DEAD_ZONE) pressed += DpadButtonType.DOWN
    if (ny < -SLIDE_DEAD_ZONE) pressed += DpadButtonType.UP
    return pressed
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
    // Forces the lit-up look, for a pad that slides between its buttons: the pad
    // presses the direction the finger is over, so the button must not press a
    // second time on its own account.
    pressedOverride: Boolean? = null,
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
    val sourcePressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressedOverride ?: sourcePressed

    if (pressedOverride == null) {
        // Held for as long as the finger is down; never latches on.
        PressAndHold(
            pressed = isPressed,
            source = "dpad_${type.name}",
            gamepadState = gamepadState,
            mask = buttonMask,
            // The id is the source in lower case: dpad_up, dpad_left, and so on.
            controlKey = "dpad_${type.name.lowercase()}",
        )
    }

    // The skin decides the outline here too, so the pad and the face buttons stay
    // in the same style whatever is chosen.
    val skin = LocalGamepadSkin.current

    OutlinedIconButton(
        modifier = modifier
            .size(size)
            .padding(0.dp),
        onClick = {},
        shape = RoundedCornerShape(skin.cornerRadius),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            // Lit up while held, so the press is visible on screen too.
            containerColor = if (isPressed) pressedBackground else backgroundColour,
            contentColor = if (isPressed) pressedForeground else foregroundColour,
        ),
        border = BorderStroke(
            if (isPressed) skin.pressedBorderWidth else skin.borderWidth,
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

/** The D-pad's four arrows, each bound to its own direction. */
@Composable
private fun BoxScope.DpadButtons(
    buttonSize: Dp,
    gamepadState: GamepadReading,
    bindings: Map<String, ControllerAction>,
    pressed: Set<DpadButtonType>?,
) {
    DpadButton(
        type = DpadButtonType.UP,
        modifier = Modifier.align(Alignment.TopCenter),
        size = buttonSize,
        gamepadState = gamepadState,
        actionMask = dpadMask(DpadButtonType.UP, bindings),
        pressedOverride = pressed?.contains(DpadButtonType.UP),
    )
    DpadButton(
        type = DpadButtonType.DOWN,
        modifier = Modifier.align(Alignment.BottomCenter),
        size = buttonSize,
        gamepadState = gamepadState,
        actionMask = dpadMask(DpadButtonType.DOWN, bindings),
        pressedOverride = pressed?.contains(DpadButtonType.DOWN),
    )
    DpadButton(
        type = DpadButtonType.LEFT,
        modifier = Modifier.align(Alignment.CenterStart),
        size = buttonSize,
        gamepadState = gamepadState,
        actionMask = dpadMask(DpadButtonType.LEFT, bindings),
        pressedOverride = pressed?.contains(DpadButtonType.LEFT),
    )
    DpadButton(
        type = DpadButtonType.RIGHT,
        modifier = Modifier.align(Alignment.CenterEnd),
        size = buttonSize,
        gamepadState = gamepadState,
        actionMask = dpadMask(DpadButtonType.RIGHT, bindings),
        pressedOverride = pressed?.contains(DpadButtonType.RIGHT),
    )
}

/**
 * A directional pad with up, down, left, and right buttons.
 *
 * With [slideBetweenButtons] the four arrows become one surface: the direction
 * follows the finger, so a push can be rolled from one arrow into the next
 * without lifting it — and a diagonal is the finger sitting between two of them.
 */
@Composable
fun Dpad(
    modifier: Modifier = Modifier,
    size: Dp = 360.dp,
    gamepadState: GamepadReading,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
    slideBetweenButtons: Boolean = false,
) {
    val buttonSize = 2 * size / 5

    if (!slideBetweenButtons) {
        Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
            DpadButtons(
                buttonSize = buttonSize,
                gamepadState = gamepadState,
                bindings = bindings,
                pressed = null,
            )
        }
        return
    }

    val pressed = remember { mutableStateOf(emptySet<DpadButtonType>()) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(bindings) {
                val width = this.size.width.toFloat().coerceAtLeast(1f)
                val height = this.size.height.toFloat().coerceAtLeast(1f)

                fun update(position: Offset) {
                    val nx = position.x / width * 2f - 1f
                    val ny = position.y / height * 2f - 1f
                    val next = directionsAt(nx, ny)
                    val current = pressed.value
                    if (next == current) return

                    (current - next).forEach {
                        GamepadButtonDispatcher.release(gamepadState, dpadSlideSource(it))
                    }
                    (next - current).forEach {
                        GamepadButtonDispatcher.press(
                            gamepadState, dpadSlideSource(it), dpadMask(it, bindings)
                        )
                    }
                    // Slide mode skips PressAndHold, so the press buzzes here: rolling
                    // a thumb across the pad should feel like a real D-pad.
                    if (next.isNotEmpty()) {
                        HapticUtils.performButtonPressFeedback(view)
                    }
                    pressed.value = next
                }

                awaitEachGesture {
                    // `false`: a touch that lands on one of the arrows is claimed by
                    // that arrow, and the pad still has to take it over — it is the
                    // pad, not the arrow, that decides what the finger is over.
                    val down = awaitFirstDown(requireUnconsumed = false)
                    update(down.position)
                    var released = false
                    try {
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                released = true
                            } else {
                                update(change.position)
                            }
                        }
                    } finally {
                        pressed.value.forEach {
                            GamepadButtonDispatcher.release(gamepadState, dpadSlideSource(it))
                        }
                        pressed.value = emptySet()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        DpadButtons(
            buttonSize = buttonSize,
            gamepadState = gamepadState,
            bindings = bindings,
            pressed = pressed.value,
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
