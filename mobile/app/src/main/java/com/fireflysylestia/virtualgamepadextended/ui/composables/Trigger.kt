package com.fireflysylestia.virtualgamepadextended.ui.composables

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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.KeyButtonDispatcher

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
    // The control's id plus the key (or two-key chord) bound to it. A trigger
    // with a key bound sends that instead of its axis: a key has no pressure, so
    // it answers a pull the way a button answers a press — past half the travel.
    controlId: String? = null,
    controlKey: String? = null,
) {
    val view = LocalView.current

    // Trigger input is pressure-like: a touch is a full pull, and dragging down
    // from where the finger landed eases it off toward zero. Releasing always
    // returns the value to zero.
    val triggerLabel = labelOverride ?: when (type) {
        TriggerType.LEFT -> stringResource(R.string.button_lt)
        TriggerType.RIGHT -> stringResource(R.string.button_rt)
    }

    // How far the trigger currently hangs, kept even while it is remapped so the
    // half-pull threshold has something to read. Written from the gesture below
    // (pointer input runs on the main thread), so its change recomposes and the
    // effect underneath can press or release.
    var pull by remember { mutableStateOf(0f) }
    val pulledToKey = controlKey != null && pull >= 0.5f

    fun setTriggerValue(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        if (controlKey == null) {
            when (type) {
                TriggerType.LEFT -> gamepadState.LeftTrigger = clamped
                TriggerType.RIGHT -> gamepadState.RightTrigger = clamped
            }
        }
        pull = clamped
    }

    // The bound key, or the chord, is held for as long as the trigger hangs past
    // half. Release happens through this effect's disposal: pulling back, lifting
    // the finger (the gesture's finally sets the value to zero) and leaving the
    // screen all dispose it, and a binding cleared mid-pull releases the key the
    // same way.
    DisposableEffect(controlKey, pulledToKey, gamepadState) {
        if (controlKey != null && pulledToKey && controlId != null) {
            KeyButtonDispatcher.pressKeys(gamepadState, controlId, controlKey)
        }
        onDispose {
            if (controlId != null) {
                KeyButtonDispatcher.releaseKeys(gamepadState, controlId)
            }
        }
    }

    // The cut corner is part of what makes a trigger look like a trigger, so a skin
    // only decides the case where it wants the whole controller squared off. Any
    // softer skin keeps the cut, which is why the shape is not simply the skin's
    // corner radius.
    val skin = LocalGamepadSkin.current
    val triggerShape = when {
        skin.cornerRadius < 3.dp -> AbsoluteCutCornerShape(0.dp)

        else -> when (type) {
            TriggerType.LEFT -> AbsoluteCutCornerShape(topRight = size * 0.4f)
            TriggerType.RIGHT -> AbsoluteCutCornerShape(topLeft = size * 0.4f)
        }
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
                // Keyed on the binding too: the gesture below captures
                // setTriggerValue, and a pointerInput block keyed only on type
                // and size would keep running against the binding it was created
                // with after the player rebinds the trigger.
                .pointerInput(type, size, controlKey) {
                    val travel = this.size.height.toFloat().coerceAtLeast(1f)
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startY = down.position.y
                        // The pull is measured from where the finger landed rather
                        // than from the top of the button: a tap is a full pull,
                        // exactly like a real trigger squeezed to the stop. The
                        // value used to come from the touch position instead, so a
                        // tap anywhere below the middle — most of the button —
                        // stayed under the PC's press threshold and did nothing,
                        // which is what made ZL and ZR look broken on a tap.
                        fun updateFromDrag(y: Float) {
                            setTriggerValue(1f - (y - startY) / travel)
                        }
                        updateFromDrag(startY)
                        HapticUtils.performButtonPressFeedback(view)
                        try {
                            var released = false
                            while (!released) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id }
                                if (change == null || !change.pressed) {
                                    released = true
                                } else {
                                    updateFromDrag(change.position.y)
                                }
                            }
                        } finally {
                            // Also release on gesture cancellation or composition disposal.
                            setTriggerValue(0f)
                            HapticUtils.performButtonReleaseFeedback(view)
                        }
                    }
                },
            shape = triggerShape,
            border = BorderStroke(skin.borderWidth, MaterialTheme.colorScheme.outline),
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
