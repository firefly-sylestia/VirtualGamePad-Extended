package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
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
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AnalogStickType {
    LEFT, RIGHT
}

/** Travel, from where the finger landed, that still counts as a tap rather than a drag. */
private val STICK_CLICK_SLOP = 16.dp

/**
 * How long the click is held down.
 *
 * A click is generated after the finger has gone, so it has to outlive at least
 * one polling interval or the packet that would carry it may be sent before or
 * after it exists. The polling interval can be set as high as 200 ms.
 */
private const val STICK_CLICK_HOLD_MS = 250L

/** The sticks tick once when they first reach the edge, and again only after coming off it. */
private const val EDGE_NOTIFY_AT = 0.95f
private const val EDGE_REARM_AT = 0.85f

private class AnalogStickUiState {
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var visualX by mutableFloatStateOf(0f)
    var visualY by mutableFloatStateOf(0f)

    /** Whether the edge tick has already been given for the push in progress. */
    var edgeNotified = false
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
    state.edgeNotified = false
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
    sensitivity: Float,
) {
    state.offsetX += dragX
    state.offsetY += dragY

    val magnitude = sqrt(state.offsetX * state.offsetX + state.offsetY * state.offsetY)
    val normalizedDistance = (magnitude / maxOffset).coerceIn(0f, 1f)

    // One tick as the stick first reaches its edge, then nothing more until it
    // comes back off it. This used to fire for every drag event past the
    // threshold, and drag events arrive dozens of times a second: holding the
    // stick against the edge buzzed the hand continuously.
    if (normalizedDistance >= EDGE_NOTIFY_AT) {
        if (!state.edgeNotified) {
            state.edgeNotified = true
            HapticUtils.performAnalogMovementFeedback(view, normalizedDistance)
        }
    } else if (normalizedDistance <= EDGE_REARM_AT) {
        state.edgeNotified = false
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
    // and the middle of the travel is where it responds. [sensitivity] is
    // applied to that travel first, so a setting above one reaches full speed
    // before the stick's edge and one below it needs a longer push.
    if (maxOffset <= 0f) {
        updateThumbstickValues(type, gamepadState, 0f, 0f)
        return
    }
    val (x, y) = StickResponse.apply(
        state.visualX / maxOffset * sensitivity,
        state.visualY / maxOffset * sensitivity,
    )
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
    sensitivity: Float = 1f,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val clickSlopPx = with(density) { STICK_CLICK_SLOP.toPx() }

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
                                        sensitivity = sensitivity,
                                    )
                                }
                            )
                        }
                        .pointerInput(clickMask, clickSlopPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)

                                // Follow the finger to its release, adding up how far
                                // it travelled from where it landed.
                                var travelled = 0f
                                var lifted = false
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change =
                                        event.changes.firstOrNull { it.id == down.id } ?: break
                                    travelled +=
                                        (change.position - change.previousPosition).getDistance()
                                    if (!change.pressed) {
                                        lifted = true
                                        break
                                    }
                                }

                                // Only a tap that stayed put is a stick click. The
                                // click used to be sent the moment the finger landed,
                                // so pushing the stick to walk also pressed L3 — which
                                // is crouch or sneak in most games — and the player sat
                                // down every time they moved.
                                if (!lifted || travelled > clickSlopPx) return@awaitEachGesture

                                clickHeld = true
                                GamepadButtonDispatcher.press(
                                    gamepadState, "stick_${type.name}", clickMask
                                )
                                HapticUtils.performButtonPressFeedback(view)
                                try {
                                    delay(STICK_CLICK_HOLD_MS)
                                } finally {
                                    GamepadButtonDispatcher.release(
                                        gamepadState, "stick_${type.name}"
                                    )
                                    HapticUtils.performButtonReleaseFeedback(view)
                                    clickHeld = false
                                }
                            }
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
