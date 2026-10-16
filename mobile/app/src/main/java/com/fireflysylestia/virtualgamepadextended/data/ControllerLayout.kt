package com.fireflysylestia.virtualgamepadextended.data

import androidx.annotation.StringRes
import com.fireflysylestia.virtualgamepadextended.R

/** Controller family used to select labels and a starting layout. */
enum class ControllerLayout(val displayName: String) {
    SWITCH_2("Nintendo Switch 2"),
    POKEMON_ZA("Pokémon Legends Z-A (easy)"),
    SWITCH("Nintendo Switch"),
    XBOX("Xbox"),
    PLAYSTATION("PlayStation DualSense"),
    STEAM_DECK("Steam Deck"),
    CUSTOM("Custom");

    companion object {
        fun fromStored(value: String?): ControllerLayout =
            entries.firstOrNull { it.name == value } ?: SWITCH_2
    }

    val faceButtonStyle: FaceButtonStyle
        get() = when (this) {
            SWITCH_2, SWITCH, POKEMON_ZA -> FaceButtonStyle.NINTENDO
            PLAYSTATION -> FaceButtonStyle.PLAYSTATION
            XBOX, STEAM_DECK, CUSTOM -> FaceButtonStyle.XBOX
        }

    val leftShoulderLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH, POKEMON_ZA -> "L"
            PLAYSTATION, STEAM_DECK -> "L1"
            XBOX, CUSTOM -> "LB"
        }

    val rightShoulderLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH, POKEMON_ZA -> "R"
            PLAYSTATION, STEAM_DECK -> "R1"
            XBOX, CUSTOM -> "RB"
        }

    val leftTriggerLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH, POKEMON_ZA -> "ZL"
            PLAYSTATION, STEAM_DECK -> "L2"
            XBOX, CUSTOM -> "LT"
        }

    val rightTriggerLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH, POKEMON_ZA -> "ZR"
            PLAYSTATION, STEAM_DECK -> "R2"
            XBOX, CUSTOM -> "RT"
        }
}

enum class FaceButtonStyle {
    XBOX,
    NINTENDO,
    PLAYSTATION,
}

/**
 * Presets are data over the existing component renderer, and they only turn
 * controls on or off: every control keeps the position it has in
 * [defaultButtonConfigs], so a preset can never stack two controls on top of
 * each other. User changes are saved separately from the built-in preset, so
 * choosing a preset never edits the preset itself.
 *
 * Handy rule of thumb when adding a family: show the controls the controller
 * really has, and nothing else. `GamepadLayoutTest` checks that rule for every
 * preset it knows about.
 */
fun buttonConfigsForLayout(layout: ControllerLayout): Map<ButtonComponent, ButtonConfig> {
    val base = defaultButtonConfigs.toMutableMap()

    fun show(component: ButtonComponent, visible: Boolean) {
        val old = base[component] ?: ButtonConfig.default(component)
        base[component] = old.copy(visible = visible)
    }

    /** The controls every one of these controllers has in the same place. */
    fun coreControls() {
        show(ButtonComponent.LEFT_ANALOG_STICK, true)
        show(ButtonComponent.RIGHT_ANALOG_STICK, true)
        // The mobile app's own L3/R3 pair travels with the sticks: wherever there
        // are sticks to click, the click also gets a button of its own, so a game
        // that reads it as crouch does not crouch every time the player walks.
        show(ButtonComponent.STICK_CLICKS, true)
        show(ButtonComponent.DPAD, true)
        show(ButtonComponent.FACE_BUTTONS, true)
        show(ButtonComponent.LEFT_TRIGGER, true)
        show(ButtonComponent.RIGHT_TRIGGER, true)
        show(ButtonComponent.LEFT_SHOULDER, true)
        show(ButtonComponent.RIGHT_SHOULDER, true)
    }

    /** Turn everything the family does not have back off. */
    fun onlySharedExtras() {
        show(ButtonComponent.SELECT_BUTTON, false)
        show(ButtonComponent.START_BUTTON, false)
        show(ButtonComponent.PLUS_MINUS, false)
        show(ButtonComponent.HOME_CAPTURE, false)
        show(ButtonComponent.SIDE_BUTTONS, false)
        show(ButtonComponent.REAR_PADDLES, false)
        show(ButtonComponent.TOUCH_SURFACE, false)
        show(ButtonComponent.GYRO_TOGGLE, false)
        show(ButtonComponent.MACRO_BUTTONS, false)
    }

    when (layout) {
        ControllerLayout.SWITCH_2, ControllerLayout.SWITCH -> {
            // A Switch is played with a Joy-Con pair as often as with a Pro
            // Controller, and the rail buttons are on both generations' Joy-Con,
            // so SL/SR belong to the family's layout. The M1-M4 macros stay off:
            // no Nintendo pad has them, and they are one tap away in the
            // customisation screen. The Switch 2 Pro Controller's GL/GR back
            // buttons are left off for the same reason (no Joy-Con has them).
            coreControls()
            onlySharedExtras()
            show(ButtonComponent.PLUS_MINUS, true)
            show(ButtonComponent.HOME_CAPTURE, true)
            show(ButtonComponent.SIDE_BUTTONS, true)
            show(ButtonComponent.GYRO_TOGGLE, true)
        }

        ControllerLayout.POKEMON_ZA -> {
            // Relaxed, turn-based preset: the same controls as a Switch, but
            // without the extras the game never asks for. It uses no touch
            // surface and no gyro aiming, so neither is shown.
            coreControls()
            onlySharedExtras()
            show(ButtonComponent.PLUS_MINUS, true)
            show(ButtonComponent.HOME_CAPTURE, true)
        }

        ControllerLayout.XBOX -> {
            coreControls()
            onlySharedExtras()
            show(ButtonComponent.SELECT_BUTTON, true)
            show(ButtonComponent.START_BUTTON, true)
        }

        ControllerLayout.PLAYSTATION -> {
            coreControls()
            onlySharedExtras()
            show(ButtonComponent.SELECT_BUTTON, true)
            show(ButtonComponent.START_BUTTON, true)
            show(ButtonComponent.HOME_CAPTURE, true)
            show(ButtonComponent.GYRO_TOGGLE, true)
            show(ButtonComponent.TOUCH_SURFACE, true)
        }

        ControllerLayout.STEAM_DECK -> {
            coreControls()
            onlySharedExtras()
            show(ButtonComponent.SELECT_BUTTON, true)
            show(ButtonComponent.START_BUTTON, true)
            show(ButtonComponent.GYRO_TOGGLE, true)
            show(ButtonComponent.TOUCH_SURFACE, true)
            show(ButtonComponent.REAR_PADDLES, true)
        }

        ControllerLayout.CUSTOM -> Unit
    }
    return base.toMap()
}
