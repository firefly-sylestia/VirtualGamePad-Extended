package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading

/**
 * Software touch surface. Drag position is mapped to the existing right-stick axes,
 * so it works with the current transport and does not pretend to expose a native HID touchpad.
 */
@Composable
fun TouchSurface(
    gamepadState: GamepadReading,
    size: Dp,
    modifier: Modifier = Modifier,
    sensitivity: Float = 1f,
) {
    // A skin may square the pad off, but it is never rounded further than the
    // surface has always been: a fully round radius would turn it into a pill.
    val skin = LocalGamepadSkin.current
    val shape = RoundedCornerShape(minOf(18.dp, skin.cornerRadius))
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .size(size)
            .pointerInput(sensitivity) {
                val surfaceWidth = this.size.width.coerceAtLeast(1)
                val surfaceHeight = this.size.height.coerceAtLeast(1)

                // The surface drives the pointer through the right stick, so its
                // gain is applied here, at the source: above 1.0 a short drag covers
                // more screen, below it the pointer is steadier for small targets.
                fun update(x: Float, y: Float) {
                    gamepadState.RightThumbstickX =
                        (((x / surfaceWidth) * 2f - 1f) * sensitivity).coerceIn(-1f, 1f)
                    gamepadState.RightThumbstickY =
                        (((y / surfaceHeight) * 2f - 1f) * sensitivity).coerceIn(-1f, 1f)
                }
                detectDragGestures(
                    onDragStart = { offset -> update(offset.x, offset.y) },
                    onDrag = { change, _ ->
                        update(change.position.x, change.position.y)
                        change.consume()
                    },
                    onDragEnd = {
                        gamepadState.RightThumbstickX = 0f
                        gamepadState.RightThumbstickY = 0f
                    },
                    onDragCancel = {
                        gamepadState.RightThumbstickX = 0f
                        gamepadState.RightThumbstickY = 0f
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Without an explicit colour this label is black, which is the same as a
        // dark surfaceVariant: it has to take the surface's own on-colour.
        Text(
            text = "TOUCH",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
