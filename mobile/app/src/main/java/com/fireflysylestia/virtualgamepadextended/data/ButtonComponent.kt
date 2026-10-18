package com.fireflysylestia.virtualgamepadextended.data

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.fireflysylestia.virtualgamepadextended.R
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

// Centralized value ranges for button configuration
val SCALE_VALUE_RANGE = 0.5f..2f

/**
 * How far a control may be nudged from its anchor, in layout units (× baseDp).
 *
 * The range has to let *any* control reach *any* edge, not just the one its anchor
 * sits on. A layout unit is the screen height (capped), while the screen is up to
 * [GamepadGeometry.MAX_ASPECT] times wider than that, so an edge-anchored control
 * needs more than one unit of travel to cross the middle: at ±1 a control anchored
 * to the right could not be dragged past the centre of the screen, which is
 * exactly the "stuck in the middle" a player runs into when moving a shoulder
 * button to the other side. ±3 covers the widest supported screen with room for a
 * control's own width, and leaves existing saved offsets valid because the old
 * range is a subset of it.
 */
val OFFSET_VALUE_RANGE = -3f..3f

/**
 * Anchor point for button positioning
 */
@Immutable
@Serializable
@Parcelize
enum class ButtonAnchor(@StringRes val nameRes: Int, val displayName: String) : Parcelable {
    TOP_LEFT(R.string.anchor_top_left, "Top Left"),
    TOP_CENTER(R.string.anchor_top_center, "Top Center"),
    TOP_RIGHT(R.string.anchor_top_right, "Top Right"),
    CENTER_LEFT(R.string.anchor_center_left, "Center Left"),
    CENTER(R.string.anchor_center, "Center"),
    CENTER_RIGHT(R.string.anchor_center_right, "Center Right"),
    BOTTOM_LEFT(R.string.anchor_bottom_left, "Bottom Left"),
    BOTTOM_CENTER(R.string.anchor_bottom_center, "Bottom Center"),
    BOTTOM_RIGHT(R.string.anchor_bottom_right, "Bottom Right");

    override fun toString(): String = displayName
}

/**
 * Represents all customizable button components in the gamepad
 */
@Immutable
@Serializable
@Parcelize
enum class ButtonComponent(
    @StringRes val nameRes: Int,
    val displayName: String,
    val defaultAnchor: ButtonAnchor
) : Parcelable {
    LEFT_ANALOG_STICK(R.string.component_left_analog, "Left Analog Stick", ButtonAnchor.TOP_LEFT),
    RIGHT_ANALOG_STICK(
        R.string.component_right_analog,
        "Right Analog Stick",
        ButtonAnchor.BOTTOM_RIGHT
    ),
    DPAD(R.string.component_dpad, "D-Pad", ButtonAnchor.BOTTOM_LEFT),
    FACE_BUTTONS(R.string.component_face_buttons, "Face Buttons (A/B/X/Y)", ButtonAnchor.TOP_RIGHT),
    LEFT_TRIGGER(R.string.component_left_trigger, "Left Trigger (LT)", ButtonAnchor.BOTTOM_LEFT),
    RIGHT_TRIGGER(
        R.string.component_right_trigger,
        "Right Trigger (RT)",
        ButtonAnchor.BOTTOM_RIGHT
    ),
    LEFT_SHOULDER(R.string.component_left_shoulder, "Left Shoulder (LB)", ButtonAnchor.TOP_CENTER),
    RIGHT_SHOULDER(
        R.string.component_right_shoulder,
        "Right Shoulder (RB)",
        ButtonAnchor.TOP_CENTER
    ),
    SELECT_BUTTON(R.string.component_select, "Select (View)", ButtonAnchor.TOP_CENTER),
    START_BUTTON(R.string.component_start, "Start (Menu)", ButtonAnchor.TOP_CENTER),
    PLUS_MINUS(R.string.component_plus_minus, "Plus / Minus (+ / −)", ButtonAnchor.TOP_CENTER),
    HOME_CAPTURE(R.string.component_home_capture, "Home / Capture", ButtonAnchor.TOP_CENTER),
    SIDE_BUTTONS(R.string.component_side_buttons, "Joy-Con SL / SR", ButtonAnchor.TOP_CENTER),
    REAR_PADDLES(R.string.component_rear_paddles, "Rear Paddles (P1–P4)", ButtonAnchor.BOTTOM_CENTER),
    TOUCH_SURFACE(R.string.component_touch_surface, "Touch Surface", ButtonAnchor.TOP_CENTER),
    GYRO_TOGGLE(R.string.component_gyro_toggle, "Gyroscope Aim Toggle", ButtonAnchor.BOTTOM_CENTER),
    MACRO_BUTTONS(R.string.component_macro_buttons, "Macro Triggers (M1–M4)", ButtonAnchor.BOTTOM_CENTER),

    /**
     * A phone-only latch: tapping it keeps whatever is down at that moment down.
     *
     * It is not a controller button and sends no event of its own; it exists
     * because a phone cannot hold two controls at once the way ten fingers on a
     * keyboard can.
     */
    HOLD(R.string.component_hold, "Hold (latch inputs)", ButtonAnchor.TOP_CENTER),

    /**
     * Buttons that send a PC key by name, for games that read a keyboard only.
     *
     * Minecraft is the case this was built for: chat, the inventory, the debug
     * overlay and the HUD toggle have no gamepad equivalent at all.
     */
    CUSTOM_KEYS(R.string.component_custom_keys, "Custom Key Buttons", ButtonAnchor.TOP_CENTER),

    /**
     * A pair of buttons for L3/R3, which are the stick clicks.
     *
     * The sticks still send a click on a deliberate tap, but a stick click is
     * also what a game usually reads as crouch or sneak, and pressing it by
     * moving the stick sat the player down in the middle of walking. Having the
     * pair on its own means sneak can be bound to a button that is not under the
     * thumb that steers.
     */
    STICK_CLICKS(R.string.component_stick_clicks, "Stick Clicks (L3 / R3)", ButtonAnchor.TOP_CENTER);

    override fun toString(): String = displayName
}

/**
 * Configuration for a single button component
 */
@Stable
@Serializable
@Parcelize
data class ButtonConfig(
    val component: ButtonComponent,
    val visible: Boolean = true,
    val scale: Float = 1.0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val anchor: ButtonAnchor = component.defaultAnchor
) : Parcelable {
    companion object {
        fun default(component: ButtonComponent) = ButtonConfig(component)
    }
}
