package com.fireflysylestia.virtualgamepadextended.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import com.fireflysylestia.virtualgamepadextended.R

/**
 * The colour the live gamepad paints its background with.
 *
 * The app's own theme is chosen for the app; a player staring at the controller
 * for a whole session usually wants the background darkened or tinted as well —
 * and it has to be visible under the controls, which is why these are all dark.
 * The controls themselves keep the accent colour of [BaseColor].
 *
 * [SYSTEM] keeps the theme's surface, which is what the gamepad has always used,
 * so an untouched install looks exactly as it did before.
 */
enum class GamepadBackground(@StringRes val nameRes: Int) {
    SYSTEM(R.string.gamepad_background_system),
    MIDNIGHT(R.string.gamepad_background_midnight),
    BLACK(R.string.gamepad_background_black),
    FOREST(R.string.gamepad_background_forest),
    MAGMA(R.string.gamepad_background_magma),
    OCEAN(R.string.gamepad_background_ocean),
    CUSTOM(R.string.gamepad_background_custom);

    companion object {
        fun fromStored(value: String?): GamepadBackground =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * The background the gamepad draws, or `null` for the theme's own surface.
 *
 * @param customColor the ARGB colour [GamepadBackground.CUSTOM] stands for
 */
fun gamepadBackgroundColor(background: GamepadBackground, customColor: Int): Color? =
    when (background) {
        GamepadBackground.SYSTEM -> null
        GamepadBackground.MIDNIGHT -> Color(0xFF10162B)
        GamepadBackground.BLACK -> Color(0xFF000000)
        GamepadBackground.FOREST -> Color(0xFF0E1F16)
        GamepadBackground.MAGMA -> Color(0xFF2A1010)
        GamepadBackground.OCEAN -> Color(0xFF071C28)
        GamepadBackground.CUSTOM -> Color(customColor)
    }
