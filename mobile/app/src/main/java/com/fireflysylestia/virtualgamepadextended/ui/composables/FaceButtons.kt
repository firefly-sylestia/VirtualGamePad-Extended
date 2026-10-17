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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.times
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.PreviewBase
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.ui.theme.darken
import com.fireflysylestia.virtualgamepadextended.ui.theme.faceButtonTextStyle
import com.fireflysylestia.virtualgamepadextended.ui.theme.lighten
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import kotlin.math.abs
import kotlin.math.max


enum class FaceButtonType {
    A, B, X, Y
}

/** Physical face-button placement used by controller presets. */
enum class FaceButtonLayout {
    XBOX,
    NINTENDO_SWITCH,
    PLAYSTATION,
}

private val faceButtonColourMap = mapOf(
    FaceButtonType.A to Color(0xFF00FF00),
    FaceButtonType.B to Color(0xFFFF0000),
    FaceButtonType.X to Color(0xFF0055FF),
    FaceButtonType.Y to Color(0xFFFFFF00),
)

/** Fraction of half the cluster a finger has to leave the centre by to count. */
private const val SLIDE_DEAD_ZONE = 0.35f

/** The four ways a finger can leave the middle of a cluster. */
private enum class SlideDirection { UP, DOWN, LEFT, RIGHT }

private val faceBindingKeys = mapOf(
    FaceButtonType.A to "face_a",
    FaceButtonType.B to "face_b",
    FaceButtonType.X to "face_x",
    FaceButtonType.Y to "face_y",
)

private fun faceGameButton(type: FaceButtonType) = when (type) {
    FaceButtonType.A -> GameButtons.A
    FaceButtonType.B -> GameButtons.B
    FaceButtonType.X -> GameButtons.X
    FaceButtonType.Y -> GameButtons.Y
}

private fun faceMask(type: FaceButtonType, bindings: Map<String, ControllerAction>): Int =
    faceBindingKeys[type]?.let { bindings[it]?.mask } ?: faceGameButton(type).value

private fun faceSource(type: FaceButtonType) = "face_slide_${type.name}"

/** Where a button sits in the cluster, which is also which way it is reached. */
private fun faceAlignment(type: FaceButtonType, layout: FaceButtonLayout): Alignment =
    when (layout) {
        FaceButtonLayout.NINTENDO_SWITCH -> when (type) {
            FaceButtonType.A -> Alignment.CenterEnd
            FaceButtonType.B -> Alignment.BottomCenter
            FaceButtonType.X -> Alignment.TopCenter
            FaceButtonType.Y -> Alignment.CenterStart
        }

        FaceButtonLayout.XBOX,
        FaceButtonLayout.PLAYSTATION -> when (type) {
            FaceButtonType.A -> Alignment.BottomCenter
            FaceButtonType.B -> Alignment.CenterEnd
            FaceButtonType.X -> Alignment.CenterStart
            FaceButtonType.Y -> Alignment.TopCenter
        }
    }

/**
 * The button a finger at (@p nx, @p ny), each in -1..1 from the centre, is over.
 *
 * Exactly one button is chosen — the nearer of the two axes — because a real pad
 * cannot be asked for A and B with one thumb, and guessing a pair out of a corner
 * between them would fire combinations the player never meant to press. The
 * D-pad is the other way round on purpose: a diagonal there is a direction, not
 * an accident.
 */
private fun faceButtonsAt(
    nx: Float,
    ny: Float,
    layout: FaceButtonLayout,
): Set<FaceButtonType> {
    val horizontal = abs(nx)
    val vertical = abs(ny)
    if (max(horizontal, vertical) <= SLIDE_DEAD_ZONE) return emptySet()

    val direction = if (horizontal >= vertical) {
        if (nx > 0f) SlideDirection.RIGHT else SlideDirection.LEFT
    } else {
        if (ny > 0f) SlideDirection.DOWN else SlideDirection.UP
    }
    return setOf(faceAt(direction, layout))
}

/**
 * Which button a direction lands on for a family.
 *
 * The label positions are what a player reads: A sits on the right on a Nintendo
 * pad and at the bottom on an Xbox or PlayStation one, so the same finger
 * position has to mean a different button.
 */
private fun faceAt(direction: SlideDirection, layout: FaceButtonLayout): FaceButtonType =
    when (layout) {
        FaceButtonLayout.NINTENDO_SWITCH -> when (direction) {
            SlideDirection.RIGHT -> FaceButtonType.A
            SlideDirection.DOWN -> FaceButtonType.B
            SlideDirection.UP -> FaceButtonType.X
            SlideDirection.LEFT -> FaceButtonType.Y
        }

        FaceButtonLayout.XBOX,
        FaceButtonLayout.PLAYSTATION -> when (direction) {
            SlideDirection.DOWN -> FaceButtonType.A
            SlideDirection.RIGHT -> FaceButtonType.B
            SlideDirection.LEFT -> FaceButtonType.X
            SlideDirection.UP -> FaceButtonType.Y
        }
    }

@Composable
fun FaceButton(
    type: FaceButtonType,
    modifier: Modifier = Modifier,
    foregroundColour: Color = lighten(faceButtonColourMap[type]!!, 0.2f),
    backgroundColour: Color = darken(faceButtonColourMap[type]!!, 0.8f),
    size: Dp,
    gamepadState: GamepadReading,
    labelOverride: String? = null,
    actionMask: Int? = null,
    // Forces the held look, for a cluster that slides between its buttons: the
    // cluster presses the button the finger is over, so the button must not press
    // a second time on its own account.
    pressedOverride: Boolean? = null,
) {
    val gameButton = faceGameButton(type)
    val buttonMask = actionMask ?: gameButton.value
    val label = labelOverride ?: when (type) {
        FaceButtonType.A -> stringResource(R.string.button_a)
        FaceButtonType.B -> stringResource(R.string.button_b)
        FaceButtonType.X -> stringResource(R.string.button_x)
        FaceButtonType.Y -> stringResource(R.string.button_y)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val sourcePressed by interactionSource.collectIsPressedAsState()
    val isPressed = pressedOverride ?: sourcePressed

    if (pressedOverride == null) {
        // Held for as long as the finger is down; never latches on.
        PressAndHold(
            pressed = isPressed,
            source = "face_${type.name}",
            gamepadState = gamepadState,
            mask = buttonMask,
        )
    }

    // The skin only decides the outline: a face button keeps its own colour for
    // each letter, so a skin can square a button off without recolouring it.
    val skin = LocalGamepadSkin.current

    OutlinedButton(
        modifier = modifier
            .size(size)
            .padding(0.dp),
        onClick = {},
        shape = RoundedCornerShape(skin.cornerRadius),
        colors = ButtonDefaults.outlinedButtonColors(
            // Brighter while held, so the press is visible on screen too.
            containerColor = if (isPressed) lighten(backgroundColour, 0.45f) else backgroundColour,
            contentColor = foregroundColour,
        ),
        interactionSource = interactionSource,
        border = BorderStroke(
            if (isPressed) skin.pressedBorderWidth else skin.borderWidth,
            if (isPressed) foregroundColour else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Text(
            text = label,
            color = foregroundColour,
            textAlign = TextAlign.Center,
            style = faceButtonTextStyle(size),
        )
    }
}

/** The four buttons in their family's arrangement, each bound to its own output. */
@Composable
private fun BoxScope.FaceButtonCluster(
    buttonSize: Dp,
    gamepadState: GamepadReading,
    layout: FaceButtonLayout,
    bindings: Map<String, ControllerAction>,
    pressed: Set<FaceButtonType>?,
) {
    FaceButtonType.entries.forEach { type ->
        FaceButton(
            type = type,
            modifier = Modifier.align(faceAlignment(type, layout)),
            size = buttonSize,
            gamepadState = gamepadState,
            labelOverride = if (layout == FaceButtonLayout.PLAYSTATION) {
                when (type) {
                    FaceButtonType.A -> "×"
                    FaceButtonType.B -> "○"
                    FaceButtonType.X -> "□"
                    FaceButtonType.Y -> "△"
                }
            } else {
                null
            },
            actionMask = faceMask(type, bindings),
            pressedOverride = pressed?.contains(type),
        )
    }
}

/**
 * The A, B, X, Y buttons on a gamepad, also known as the face buttons.
 *
 * With [slideBetweenButtons] the four buttons become one surface: the button
 * follows the finger, so a press can be rolled from one button into the next
 * without lifting it.
 */
@Composable
fun FaceButtons(
    modifier: Modifier = Modifier,
    size: Dp = 360.dp,
    gamepadState: GamepadReading,
    layout: FaceButtonLayout = FaceButtonLayout.NINTENDO_SWITCH,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
    slideBetweenButtons: Boolean = false,
) {
    val buttonSize = 2 * size / 5

    if (!slideBetweenButtons) {
        Box(
            modifier = modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            FaceButtonCluster(
                buttonSize = buttonSize,
                gamepadState = gamepadState,
                layout = layout,
                bindings = bindings,
                pressed = null,
            )
        }
        return
    }

    val pressed = remember { mutableStateOf(emptySet<FaceButtonType>()) }
    val view = LocalView.current

    Box(
        modifier = modifier
            .size(size)
            .pointerInput(layout, bindings) {
                val width = this.size.width.toFloat().coerceAtLeast(1f)
                val height = this.size.height.toFloat().coerceAtLeast(1f)

                fun update(position: Offset) {
                    val nx = position.x / width * 2f - 1f
                    val ny = position.y / height * 2f - 1f
                    val next = faceButtonsAt(nx, ny, layout)
                    val current = pressed.value
                    if (next == current) return

                    (current - next).forEach {
                        GamepadButtonDispatcher.release(gamepadState, faceSource(it))
                    }
                    (next - current).forEach {
                        GamepadButtonDispatcher.press(
                            gamepadState, faceSource(it), faceMask(it, bindings)
                        )
                    }
                    // Slide mode skips PressAndHold, so the press buzzes here instead:
                    // rolling a thumb across A/B/X/Y should feel like a real pad.
                    if (next.isNotEmpty()) {
                        HapticUtils.performButtonPressFeedback(view)
                    }
                    pressed.value = next
                }

                awaitEachGesture {
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
                            GamepadButtonDispatcher.release(gamepadState, faceSource(it))
                        }
                        pressed.value = emptySet()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        FaceButtonCluster(
            buttonSize = buttonSize,
            gamepadState = gamepadState,
            layout = layout,
            bindings = bindings,
            pressed = pressed.value,
        )
    }
}

@Preview(showBackground = false)
@Composable
fun FaceButtonsPreview() {
    PreviewBase {
        FaceButtons(
            gamepadState = GamepadReading(),
        )
    }
}
