package com.fireflysylestia.virtualgamepadextended.data

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fireflysylestia.virtualgamepadextended.R

/**
 * How the gamepad's controls are drawn.
 *
 * This is separate from [GamepadBackground] on purpose. The background setting only
 * ever changed the colour of the field behind the controls, which is not what a
 * "theme" means to a player: a theme is the look of the buttons themselves - square
 * or round, thin-edged or chunky. The controller now always sits on black, so the
 * skin is what changes.
 *
 * A skin only describes geometry, never colour: the accent colour still comes from
 * the app's base colour, so a skin cannot make the controls unreadable against the
 * palette the player chose.
 *
 * [DEFAULT] reproduces the previous look exactly - pill-shaped buttons with the 2 dp
 * outline they always had - so an untouched install is unchanged.
 */
@Immutable
enum class GamepadSkin(
    @StringRes val nameRes: Int,
    /** Corner radius for a control. A large value makes the control fully round. */
    val cornerRadius: Dp,
    /** Outline thickness at rest. */
    val borderWidth: Dp,
    /** Outline thickness while the control is held, which reads as the press. */
    val pressedBorderWidth: Dp,
) {
    DEFAULT(R.string.gamepad_skin_default, 999.dp, 2.dp, 3.dp),

    /**
     * Square, heavy-edged controls in the spirit of a block-built world. Without
     * artwork this is the part of that look geometry can carry: no rounding and a
     * thick outline, so buttons read as blocks rather than capsules.
     */
    MINECRAFT(R.string.gamepad_skin_minecraft, 0.dp, 4.dp, 4.dp),

    /** Hard corners with a lighter edge: an arcade-cabinet look at small sizes. */
    PIXEL(R.string.gamepad_skin_pixel, 2.dp, 3.dp, 4.dp),

    /** Round, with a heavier rim that stands out on black. */
    NEON(R.string.gamepad_skin_neon, 999.dp, 3.dp, 5.dp),

    /** Softly rounded but squarely built, like a physical arcade button. */
    ARCADE(R.string.gamepad_skin_arcade, 14.dp, 3.dp, 5.dp);

    companion object {
        fun fromStored(value: String?): GamepadSkin =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * The skin the controls are drawn with.
 *
 * Provided once at the top of the gamepad rather than passed down: every control
 * would otherwise need a skin parameter that only ever reaches a corner radius, and
 * the customisation preview has to agree with the live gamepad without threading it
 * through by hand.
 */
val LocalGamepadSkin = staticCompositionLocalOf { GamepadSkin.DEFAULT }
