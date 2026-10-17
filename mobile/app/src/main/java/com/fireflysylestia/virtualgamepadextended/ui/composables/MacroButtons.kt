package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.MacroAction
import com.fireflysylestia.virtualgamepadextended.data.MacroDefinition
import com.fireflysylestia.virtualgamepadextended.data.MacroStep
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun MacroButton(
    slot: Int,
    macro: MacroDefinition?,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isPressed) {
        if (isPressed && macro != null && macro.steps.isNotEmpty()) {
            scope.launch {
                macro.steps.take(32).forEachIndexed { index, step ->
                    runMacroStep(gamepadState, step, "macro_${slot}_${index}")
                }
            }
        }
    }

    OutlinedButton(
        onClick = {},
        enabled = macro != null && macro.steps.isNotEmpty(),
        modifier = Modifier.size(width = metrics.width, height = metrics.height),
        shape = RoundedCornerShape(LocalGamepadSkin.current.cornerRadius),
        border = BorderStroke(
            if (isPressed) LocalGamepadSkin.current.pressedBorderWidth
            else LocalGamepadSkin.current.borderWidth,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            // Same pairing as the other auxiliary buttons: a container that stands
            // off the gamepad's surface, with its own on-colour for the label.
            containerColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        interactionSource = interactionSource,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text("M${slot + 1}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private suspend fun runMacroStep(state: GamepadReading, step: MacroStep, source: String) {
    val hold = step.holdMillis.coerceIn(10, 5000).toLong()
    val gap = step.gapMillis.coerceIn(0, 5000).toLong()
    val action = step.action
    if (action.buttonMask != 0) {
        val mask = action.buttonMask
        GamepadButtonDispatcher.press(state, source, mask)
        try {
            delay(hold)
        } finally {
            GamepadButtonDispatcher.release(state, source)
        }
    } else {
        val previous = readAxisOrTrigger(state, action)
        writeAxisOrTrigger(state, action, step.value)
        try {
            delay(hold)
        } finally {
            writeAxisOrTrigger(state, action, previous)
        }
    }
    delay(gap)
}

private fun readAxisOrTrigger(state: GamepadReading, action: MacroAction): Float = when (action) {
    MacroAction.LEFT_TRIGGER -> state.LeftTrigger
    MacroAction.RIGHT_TRIGGER -> state.RightTrigger
    MacroAction.LEFT_STICK_X -> state.LeftThumbstickX
    MacroAction.LEFT_STICK_Y -> state.LeftThumbstickY
    MacroAction.RIGHT_STICK_X -> state.RightThumbstickX
    MacroAction.RIGHT_STICK_Y -> state.RightThumbstickY
    else -> 0f
}

private fun writeAxisOrTrigger(state: GamepadReading, action: MacroAction, value: Float) {
    when (action) {
        MacroAction.LEFT_TRIGGER -> state.LeftTrigger = value.coerceIn(0f, 1f)
        MacroAction.RIGHT_TRIGGER -> state.RightTrigger = value.coerceIn(0f, 1f)
        MacroAction.LEFT_STICK_X -> state.LeftThumbstickX = value.coerceIn(-1f, 1f)
        MacroAction.LEFT_STICK_Y -> state.LeftThumbstickY = value.coerceIn(-1f, 1f)
        MacroAction.RIGHT_STICK_X -> state.RightThumbstickX = value.coerceIn(-1f, 1f)
        MacroAction.RIGHT_STICK_Y -> state.RightThumbstickY = value.coerceIn(-1f, 1f)
        else -> Unit
    }
}

@Composable
fun MacroButtonCluster(
    macros: List<MacroDefinition>,
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
        (0..3).forEach { slot ->
            MacroButton(
                slot = slot,
                macro = macros.getOrNull(slot),
                gamepadState = gamepadState,
                metrics = metrics,
            )
        }
    }
}
