package io.github.kitswas.virtualgamepadmobile.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
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
) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(2.dp, MaterialTheme.colorScheme.outline, shape)
            .fillMaxSize()
            .pointerInput(Unit) {
                fun update(x: Float, y: Float) {
                    gamepadState.RightThumbstickX =
                        ((x / this.size.width.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
                    gamepadState.RightThumbstickY =
                        ((y / this.size.height.coerceAtLeast(1)) * 2f - 1f).coerceIn(-1f, 1f)
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
        Text("TOUCH", style = MaterialTheme.typography.labelMedium)
    }
}
