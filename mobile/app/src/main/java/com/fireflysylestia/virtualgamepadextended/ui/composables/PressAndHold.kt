package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.HapticUtils

/**
 * Sends a press while [pressed] is true and a release as soon as it turns false.
 *
 * Every on-screen button uses this: the button is held for exactly as long as the
 * finger is down, with no latching. Remapped controls that share one output are
 * counted by [GamepadButtonDispatcher], so releasing one of them cannot release
 * the other.
 *
 * @param source stable name of the control, used to pair the press with its release
 * @param mask output button flag, or `null`/0 to do nothing
 */
@Composable
fun PressAndHold(
    pressed: Boolean,
    source: String,
    gamepadState: GamepadReading,
    mask: Int?,
    haptics: Boolean = true,
) {
    val view = LocalView.current
    if (!pressed || mask == null || mask == 0) return

    DisposableEffect(source, mask) {
        GamepadButtonDispatcher.press(gamepadState, source, mask)
        if (haptics) HapticUtils.performButtonPressFeedback(view)
        onDispose {
            GamepadButtonDispatcher.release(gamepadState, source)
            if (haptics) HapticUtils.performButtonReleaseFeedback(view)
        }
    }
}
