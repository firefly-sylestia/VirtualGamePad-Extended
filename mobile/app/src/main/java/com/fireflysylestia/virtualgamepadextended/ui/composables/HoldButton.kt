package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils
import com.fireflysylestia.virtualgamepadextended.ui.utils.InputHold

/**
 * HOLD: keeps the controls that are down at the tap pressed until it is tapped
 * again.
 *
 * It is a phone-only control and sends nothing by itself; [InputHold] re-applies
 * the snapshot to the reading the network layer sends. Because it is not a
 * controller button it is drawn like an auxiliary button, not like the pad's
 * own face buttons.
 */
@Composable
fun HoldButton(
    gamepadState: GamepadReading,
    hold: InputHold,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val skin = LocalGamepadSkin.current
    val latched = hold.isLatched

    OutlinedButton(
        onClick = {
            hold.toggle(gamepadState)
            HapticUtils.performButtonPressFeedback(view)
        },
        modifier = modifier.size(width = metrics.width, height = metrics.height),
        shape = RoundedCornerShape(skin.cornerRadius),
        border = BorderStroke(
            if (latched) skin.pressedBorderWidth else skin.borderWidth,
            if (latched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (latched) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (latched) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        Text(
            if (latched) "HELD" else "HOLD",
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
