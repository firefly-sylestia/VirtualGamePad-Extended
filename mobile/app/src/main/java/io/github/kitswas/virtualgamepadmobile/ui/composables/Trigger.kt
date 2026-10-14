package io.github.kitswas.virtualgamepadmobile.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.AbsoluteCutCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import io.github.kitswas.virtualgamepadmobile.R
import io.github.kitswas.virtualgamepadmobile.ui.utils.HapticUtils

enum class TriggerType {
    LEFT, RIGHT
}

@Composable
fun Trigger(
    type: TriggerType,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    gamepadState: GamepadReading,
    labelOverride: String? = null,
) {
    val view = LocalView.current

    // Trigger input is pressure-like: touching near the top sends a high value,
    // dragging toward the bottom lowers it, and releasing always returns to zero.
    val triggerLabel = labelOverride ?: when (type) {
        TriggerType.LEFT -> stringResource(R.string.button_lt)
        TriggerType.RIGHT -> stringResource(R.string.button_rt)
    }

    val triggerShape = when (type) {
        TriggerType.LEFT -> AbsoluteCutCornerShape(topRight = size * 0.4f)
        TriggerType.RIGHT -> AbsoluteCutCornerShape(topLeft = size * 0.4f)
    }

    Box(
        modifier = modifier
            .width(size)
            .height(size * 1.25f),
        contentAlignment = Alignment.Center
    ) {
        Button(
            onClick = {},
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(type, size) {
                    val pointerHeight = this.size.height.coerceAtLeast(1)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        fun updateFromY(y: Float) {
                            val value = (1f - y / pointerHeight).coerceIn(0f, 1f)
                            when (type) {
                                TriggerType.LEFT -> gamepadState.LeftTrigger = value
                                TriggerType.RIGHT -> gamepadState.RightTrigger = value
                            }
                        }
                        updateFromY(down.position.y)
                        HapticUtils.performButtonPressFeedback(view)
                        try {
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    released = true
                                } else {
                                    updateFromY(change.position.y)
                                }
                            }
                        } finally {
                            // Also release on gesture cancellation or composition disposal.
                            when (type) {
                                TriggerType.LEFT -> gamepadState.LeftTrigger = 0f
                                TriggerType.RIGHT -> gamepadState.RightTrigger = 0f
                            }
                            HapticUtils.performButtonReleaseFeedback(view)
                        }
                    }
                },
            shape = triggerShape,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        ) {
            Text(triggerLabel)
        }
    }
}

@Preview
@Composable
fun TriggerPreview() {
    Row {
        Trigger(
            type = TriggerType.LEFT,
            size = 64.dp,
            gamepadState = GamepadReading()
        )
        Trigger(
            type = TriggerType.RIGHT,
            size = 64.dp,
            gamepadState = GamepadReading()
        )
    }
}
