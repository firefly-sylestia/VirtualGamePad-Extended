package io.github.kitswas.virtualgamepadmobile.data

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import kotlinx.serialization.Serializable

/** Digital output action a visible control can emit. Analog sticks/triggers retain their own axes. */
@Serializable
enum class ControllerAction(val mask: Int) {
    MENU(GameButtons.Menu.value),
    VIEW(GameButtons.View.value),
    A(GameButtons.A.value),
    B(GameButtons.B.value),
    X(GameButtons.X.value),
    Y(GameButtons.Y.value),
    DPAD_UP(GameButtons.DPadUp.value),
    DPAD_DOWN(GameButtons.DPadDown.value),
    DPAD_LEFT(GameButtons.DPadLeft.value),
    DPAD_RIGHT(GameButtons.DPadRight.value),
    LEFT_SHOULDER(GameButtons.LeftShoulder.value),
    RIGHT_SHOULDER(GameButtons.RightShoulder.value),
    LEFT_STICK_CLICK(GameButtons.LeftThumbstick.value),
    RIGHT_STICK_CLICK(GameButtons.RightThumbstick.value),
    PLUS(GameButtons.Plus.value),
    MINUS(GameButtons.Minus.value),
    HOME(GameButtons.Home.value),
    CAPTURE(GameButtons.Capture.value),
    LEFT_SIDE(GameButtons.LeftSide.value),
    RIGHT_SIDE(GameButtons.RightSide.value),
    PADDLE_1(GameButtons.Paddle1.value),
    PADDLE_2(GameButtons.Paddle2.value),
    PADDLE_3(GameButtons.Paddle3.value),
    PADDLE_4(GameButtons.Paddle4.value),
}

val defaultControllerBindings: Map<String, ControllerAction> = mapOf(
    "face_a" to ControllerAction.A,
    "face_b" to ControllerAction.B,
    "face_x" to ControllerAction.X,
    "face_y" to ControllerAction.Y,
    "dpad_up" to ControllerAction.DPAD_UP,
    "dpad_down" to ControllerAction.DPAD_DOWN,
    "dpad_left" to ControllerAction.DPAD_LEFT,
    "dpad_right" to ControllerAction.DPAD_RIGHT,
    "left_shoulder" to ControllerAction.LEFT_SHOULDER,
    "right_shoulder" to ControllerAction.RIGHT_SHOULDER,
    "view" to ControllerAction.VIEW,
    "menu" to ControllerAction.MENU,
    "left_stick_click" to ControllerAction.LEFT_STICK_CLICK,
    "right_stick_click" to ControllerAction.RIGHT_STICK_CLICK,
    "plus" to ControllerAction.PLUS,
    "minus" to ControllerAction.MINUS,
    "home" to ControllerAction.HOME,
    "capture" to ControllerAction.CAPTURE,
    "left_side" to ControllerAction.LEFT_SIDE,
    "right_side" to ControllerAction.RIGHT_SIDE,
    "paddle_1" to ControllerAction.PADDLE_1,
    "paddle_2" to ControllerAction.PADDLE_2,
    "paddle_3" to ControllerAction.PADDLE_3,
    "paddle_4" to ControllerAction.PADDLE_4,
)
