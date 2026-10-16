package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.StickResponse
import kotlin.math.min

/** How far a finger has to travel from where it landed for a full stick push. */
private const val FULL_TRAVEL_FRACTION = 0.25f

/**
 * The whole screen as one big right-stick surface.
 *
 * Games whose camera sits on the right stick — Pokémon Legends Z-A is the one
 * this was asked for, but most third-person games behave the same way — are
 * played with the thumb anywhere on the screen rather than pinned to a small
 * stick in the corner. A drag is measured from where the finger landed, not from
 * a fixed centre, because an aiming drag has to start wherever it is convenient
 * and must not jump the camera to the touched position.
 *
 * It draws nothing and sits *behind* the controls, so every button keeps working:
 * a touch that lands on a control is claimed by that control and this area never
 * sees it (see `awaitFirstDown(requireUnconsumed = true)`).
 */
@Composable
fun FullScreenStickArea(
    gamepadState: GamepadReading,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1f,
) {
    val view = LocalView.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(sensitivity) {
                val width = size.width.coerceAtLeast(1)
                val height = size.height.coerceAtLeast(1)
                val travel = min(width, height) * FULL_TRAVEL_FRACTION

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = true)
                    val origin = down.position

                    fun updateFrom(position: Offset) {
                        val dx = (position.x - origin.x) / travel * sensitivity
                        val dy = (position.y - origin.y) / travel * sensitivity
                        // Same dead zone and response curve as the on-screen stick,
                        // so neither feels different from the other.
                        val (x, y) = StickResponse.apply(dx, dy)
                        gamepadState.RightThumbstickX = x
                        gamepadState.RightThumbstickY = y
                    }

                    updateFrom(origin)
                    HapticUtils.performGestureStartFeedback(view)
                    try {
                        var released = false
                        while (!released) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null || !change.pressed) {
                                released = true
                            } else {
                                updateFrom(change.position)
                            }
                        }
                    } finally {
                        // Also stops the camera on gesture cancellation or when the
                        // gamepad leaves the screen.
                        gamepadState.RightThumbstickX = 0f
                        gamepadState.RightThumbstickY = 0f
                        HapticUtils.performGestureEndFeedback(view)
                    }
                }
            }
    )
}
