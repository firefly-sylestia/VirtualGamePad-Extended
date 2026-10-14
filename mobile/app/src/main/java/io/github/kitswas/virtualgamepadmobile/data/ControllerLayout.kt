package io.github.kitswas.virtualgamepadmobile.data

import androidx.annotation.StringRes
import io.github.kitswas.virtualgamepadmobile.R

/** Controller family used to select labels and a starting layout. */
enum class ControllerLayout(val displayName: String) {
    SWITCH_2("Nintendo Switch 2"),
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
            SWITCH_2, SWITCH -> FaceButtonStyle.NINTENDO
            PLAYSTATION -> FaceButtonStyle.PLAYSTATION
            XBOX, STEAM_DECK, CUSTOM -> FaceButtonStyle.XBOX
        }

    val leftShoulderLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH -> "L"
            PLAYSTATION, STEAM_DECK -> "L1"
            XBOX, CUSTOM -> "LB"
        }

    val rightShoulderLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH -> "R"
            PLAYSTATION, STEAM_DECK -> "R1"
            XBOX, CUSTOM -> "RB"
        }

    val leftTriggerLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH -> "ZL"
            PLAYSTATION, STEAM_DECK -> "L2"
            XBOX, CUSTOM -> "LT"
        }

    val rightTriggerLabel: String
        get() = when (this) {
            SWITCH_2, SWITCH -> "ZR"
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
 * Presets are data over the existing component renderer. User changes are saved
 * separately from the built-in preset so choosing a preset never edits the preset itself.
 */
fun buttonConfigsForLayout(layout: ControllerLayout): Map<ButtonComponent, ButtonConfig> {
    val base = defaultButtonConfigs.toMutableMap()

    fun update(component: ButtonComponent, visible: Boolean? = null, anchor: ButtonAnchor? = null,
               x: Float? = null, y: Float? = null, scale: Float? = null) {
        val old = base[component] ?: ButtonConfig.default(component)
        base[component] = old.copy(
            visible = visible ?: old.visible,
            anchor = anchor ?: old.anchor,
            offsetX = x ?: old.offsetX,
            offsetY = y ?: old.offsetY,
            scale = scale ?: old.scale,
        )
    }

    when (layout) {
        ControllerLayout.SWITCH_2 -> {
            update(ButtonComponent.SELECT_BUTTON, visible = false)
            update(ButtonComponent.START_BUTTON, visible = false)
            update(ButtonComponent.PLUS_MINUS, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.38f)
            update(ButtonComponent.HOME_CAPTURE, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.52f)
            update(ButtonComponent.SIDE_BUTTONS, visible = false)
            update(ButtonComponent.REAR_PADDLES, visible = false)
            update(ButtonComponent.TOUCH_SURFACE, visible = false)
            update(ButtonComponent.GYRO_TOGGLE, visible = true)
        }
        ControllerLayout.SWITCH -> {
            update(ButtonComponent.SELECT_BUTTON, visible = false)
            update(ButtonComponent.START_BUTTON, visible = false)
            update(ButtonComponent.PLUS_MINUS, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.38f)
            update(ButtonComponent.HOME_CAPTURE, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.52f)
            update(ButtonComponent.SIDE_BUTTONS, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = -0.12f)
            update(ButtonComponent.REAR_PADDLES, visible = false)
            update(ButtonComponent.TOUCH_SURFACE, visible = false)
            update(ButtonComponent.GYRO_TOGGLE, visible = true)
        }
        ControllerLayout.XBOX -> {
            update(ButtonComponent.SELECT_BUTTON, visible = true)
            update(ButtonComponent.START_BUTTON, visible = true)
            update(ButtonComponent.PLUS_MINUS, visible = false)
            update(ButtonComponent.HOME_CAPTURE, visible = false)
            update(ButtonComponent.SIDE_BUTTONS, visible = false)
            update(ButtonComponent.REAR_PADDLES, visible = false)
            update(ButtonComponent.TOUCH_SURFACE, visible = false)
            update(ButtonComponent.GYRO_TOGGLE, visible = false)
        }
        ControllerLayout.PLAYSTATION -> {
            update(ButtonComponent.SELECT_BUTTON, visible = true)
            update(ButtonComponent.START_BUTTON, visible = true)
            update(ButtonComponent.PLUS_MINUS, visible = false)
            update(ButtonComponent.HOME_CAPTURE, visible = true)
            update(ButtonComponent.SIDE_BUTTONS, visible = false)
            update(ButtonComponent.REAR_PADDLES, visible = false)
            update(ButtonComponent.TOUCH_SURFACE, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.18f, scale = 0.75f)
            update(ButtonComponent.GYRO_TOGGLE, visible = true)
        }
        ControllerLayout.STEAM_DECK -> {
            update(ButtonComponent.SELECT_BUTTON, visible = true)
            update(ButtonComponent.START_BUTTON, visible = true)
            update(ButtonComponent.PLUS_MINUS, visible = false)
            update(ButtonComponent.HOME_CAPTURE, visible = false)
            update(ButtonComponent.SIDE_BUTTONS, visible = false)
            update(ButtonComponent.REAR_PADDLES, visible = true, anchor = ButtonAnchor.BOTTOM_CENTER, x = 0f, y = -0.08f)
            update(ButtonComponent.TOUCH_SURFACE, visible = true, anchor = ButtonAnchor.TOP_CENTER, x = 0f, y = 0.18f, scale = 0.75f)
            update(ButtonComponent.GYRO_TOGGLE, visible = true)
        }
        ControllerLayout.CUSTOM -> Unit
    }
    return base.toMap()
}
