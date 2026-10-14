package io.github.kitswas.virtualgamepadmobile.data

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import kotlinx.serialization.Serializable

@Serializable
enum class MacroAction {
    A, B, X, Y,
    DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT,
    PLUS, MINUS, HOME, CAPTURE,
    LEFT_SHOULDER, RIGHT_SHOULDER,
    LEFT_STICK_CLICK, RIGHT_STICK_CLICK,
    LEFT_TRIGGER, RIGHT_TRIGGER,
    LEFT_STICK_X, LEFT_STICK_Y,
    RIGHT_STICK_X, RIGHT_STICK_Y;

    val buttonMask: Int
        get() = when (this) {
            A -> GameButtons.A.value
            B -> GameButtons.B.value
            X -> GameButtons.X.value
            Y -> GameButtons.Y.value
            DPAD_UP -> GameButtons.DPadUp.value
            DPAD_DOWN -> GameButtons.DPadDown.value
            DPAD_LEFT -> GameButtons.DPadLeft.value
            DPAD_RIGHT -> GameButtons.DPadRight.value
            PLUS -> GameButtons.Plus.value
            MINUS -> GameButtons.Minus.value
            HOME -> GameButtons.Home.value
            CAPTURE -> GameButtons.Capture.value
            LEFT_SHOULDER -> GameButtons.LeftShoulder.value
            RIGHT_SHOULDER -> GameButtons.RightShoulder.value
            LEFT_STICK_CLICK -> GameButtons.LeftThumbstick.value
            RIGHT_STICK_CLICK -> GameButtons.RightThumbstick.value
            LEFT_TRIGGER, RIGHT_TRIGGER, LEFT_STICK_X, LEFT_STICK_Y, RIGHT_STICK_X, RIGHT_STICK_Y -> 0
        }
}

@Serializable
data class MacroStep(
    val action: MacroAction,
    val value: Float = 1f,
    val holdMillis: Int = 120,
    val gapMillis: Int = 40,
)

@Serializable
data class MacroDefinition(
    val name: String = "New macro",
    val steps: List<MacroStep> = emptyList(),
)
