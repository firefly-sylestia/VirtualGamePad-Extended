package io.github.kitswas.virtualgamepadmobile.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import io.github.kitswas.virtualgamepadmobile.data.MacroAction
import io.github.kitswas.virtualgamepadmobile.data.MacroDefinition
import io.github.kitswas.virtualgamepadmobile.data.MacroStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun MacroButton(
    slot: Int,
    macro: MacroDefinition?,
    gamepadState: GamepadReading,
    minSize: Dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(isPressed) {
        if (isPressed && macro != null && macro.steps.isNotEmpty()) {
            scope.launch {
                macro.steps.take(32).forEach { step ->
                    runMacroStep(gamepadState, step)
                }
            }
        }
    }

    OutlinedButton(
        onClick = {},
        enabled = macro != null && macro.steps.isNotEmpty(),
        modifier = Modifier.sizeIn(minWidth = minSize, minHeight = minSize),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        interactionSource = interactionSource,
    ) {
        Text("M${slot + 1}", style = MaterialTheme.typography.labelSmall)
    }
}

private suspend fun runMacroStep(state: GamepadReading, step: MacroStep) {
    val hold = step.holdMillis.coerceIn(10, 5000).toLong()
    val gap = step.gapMillis.coerceIn(0, 5000).toLong()
    val action = step.action
    if (action.buttonMask != 0) {
        val mask = action.buttonMask
        val wasDown = (state.ButtonsDown and mask) != 0
        state.ButtonsDown = state.ButtonsDown or mask
        try {
            delay(hold)
        } finally {
            if (!wasDown) {
                state.ButtonsDown = state.ButtonsDown and mask.inv()
                state.ButtonsUp = state.ButtonsUp or mask
            }
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
    minButtonSize: Dp,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..3).forEach { slot ->
            MacroButton(
                slot = slot,
                macro = macros.getOrNull(slot),
                gamepadState = gamepadState,
                minSize = minButtonSize,
            )
        }
    }
}
