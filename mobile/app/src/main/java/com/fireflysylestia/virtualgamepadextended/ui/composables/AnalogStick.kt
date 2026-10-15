package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.ui.theme.darken
import com.fireflysylestia.virtualgamepadextended.ui.theme.lighten
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.StickResponse
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AnalogStickType {
    LEFT, RIGHT
}

private class AnalogStickUiState {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var visualX by mutableFloatStateOf(0f)
    var visualY by mutableFloatStateOf(0f)
}

private fun updateThumbstickValues(
    type: AnalogStickType,
    gamepadState: GamepadReading,
    x: Float,
    y: Float,
) {
    when (type) {
        AnalogStickType.LEFT -> {
            gamepadState.LeftThumbstickX = x
            gamepadState.LeftThumbstickY = y
        }

        AnalogStickType.RIGHT -> {
            gamepadState.RightThumbstickX = x
            gamepadState.RightThumbstickY = y
        }
    }
}

private fun thumbstickButtonMask(type: AnalogStickType): Int = when (type) {
    AnalogStickType.LEFT -> GameButtons.LeftThumbstick.value
    AnalogStickType.RIGHT -> GameButtons.RightThumbstick.value
}

private fun resetAnalogStick(
    state: AnalogStickUiState,
    type: AnalogStickType,
    gamepadState: GamepadReading,
) {
    state.offsetX = 0f
    state.offsetY = 0f
    state.visualX = 0f
    state.visualY = 0f
    updateThumbstickValues(type, gamepadState, 0f, 0f)
}

private fun handleAnalogDrag(
    state: AnalogStickUiState,
    dragX: Float,
    dragY: Float,
    maxOffset: Float,
    type: AnalogStickType,
    gamepadState: GamepadReading,
    view: android.view.View,
) {
    state.offsetX += dragX
    state.offsetY += dragY

    val magnitude = sqrt(state.offsetX * state.offsetX + state.offsetY * state.offsetY)
    val normalizedDistance = (magnitude / maxOffset).coerceIn(0f, 1f)
    if (normalizedDistance > 0.3f) {
        HapticUtils.performAnalogMovementFeedback(view, normalizedDistance)
    }

    if (magnitude <= 0f) {
        return
    }

    val scaleFactor = if (magnitude > maxOffset) maxOffset / magnitude else 1f
    state.visualX = state.offsetX * scaleFactor
    state.visualY = state.offsetY * scaleFactor

    // The knob follows the finger exactly, so it always lands where it was put,
    // but the value the PC acts on goes through the dead zone and response
    // curve: a resting finger reports zero instead of creeping the character,
    // and the middle of the travel is where it responds.
    if (maxOffset <= 0f) {
        updateThumbstickValues(type, gamepadState, 0f, 0f)
        return
    }
    val (x, y) = StickResponse.apply(state.visualX / maxOffset, state.visualY / maxOffset)
    updateThumbstickValues(type, gamepadState, x, y)
}

@Composable
fun AnalogStick(
    modifier: Modifier = Modifier,
    ringColor: Color = MaterialTheme.colorScheme.outline,
    ringWidth: Dp = 4.dp,
    outerCircleColor: Color = darken(MaterialTheme.colorScheme.primary, 0.8f),
    outerCircleWidth: Dp = 4.dp,
    innerCircleColor: Color = lighten(MaterialTheme.colorScheme.primary, 0.2f),
    innerCircleRadius: Dp = 32.dp,
    gamepadState: GamepadReading,
    type: AnalogStickType,
    clickButtonMask: Int? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current

    Box(
        modifier = modifier.testTag("AnalogStick_${type.name}"),
        contentAlignment = Alignment.Center
    ) {
        // First draw the glow ring
        Circle(
            colour = ringColor,
            modifier = Modifier
                .size((innerCircleRadius + outerCircleWidth + ringWidth) * 2),
            contentAlignment = Alignment.Center
        ) {
            // Then draw the outer circle
            Circle(
                modifier = Modifier
                    .size((innerCircleRadius + outerCircleWidth) * 2),
                contentAlignment = Alignment.Center,
                colour = outerCircleColor,
            ) {
                val state = remember { AnalogStickUiState() }
                // L3/R3 are held, like every other button, and light up while held.
                var clickHeld by remember { mutableStateOf(false) }
                val clickMask = clickButtonMask ?: thumbstickButtonMask(type)

                // Calculate maximum offset once
                val maxOffset = with(density) {
                    (innerCircleRadius + outerCircleWidth).toPx()
                }

                // Then draw the inner circle
                Circle(
                    colour = if (clickHeld) lighten(innerCircleColor, 0.45f) else innerCircleColor,
                    modifier = Modifier
                        .testTag("AnalogStick_${type.name}_Handle")
                        .size(innerCircleRadius * 2)
                        .offset {
                            IntOffset(
                                state.visualX.roundToInt(),
                                state.visualY.roundToInt()
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { _ ->
                                    HapticUtils.performGestureStartFeedback(view)
                                },
                                onDragEnd = {
                                    resetAnalogStick(state, type, gamepadState)
                                    HapticUtils.performGestureEndFeedback(view)
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    handleAnalogDrag(
                                        state = state,
                                        dragX = dragAmount.x,
                                        dragY = dragAmount.y,
                                        maxOffset = maxOffset,
                                        type = type,
                                        gamepadState = gamepadState,
                                        view = view,
                                    )
                                }
                            )
                        }
                        .pointerInput(clickMask) {
                            detectTapGestures(
                                onPress = {
                                    clickHeld = true
                                    GamepadButtonDispatcher.press(
                                        gamepadState, "stick_${type.name}", clickMask
                                    )
                                    HapticUtils.performButtonPressFeedback(view)
                                    // Returns false when a drag takes over, and the
                                    // click is released either way.
                                    tryAwaitRelease()
                                    GamepadButtonDispatcher.release(
                                        gamepadState, "stick_${type.name}"
                                    )
                                    HapticUtils.performButtonReleaseFeedback(view)
                                    clickHeld = false
                                }
                            )
                        }
                ) {}
            }
        }
    }
}

@Preview(showBackground = false)
@Composable
fun AnalogStickPreview() {
    AnalogStick(
        gamepadState = GamepadReading(),
        type = AnalogStickType.LEFT,
    )
}
