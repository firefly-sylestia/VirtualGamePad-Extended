package com.fireflysylestia.virtualgamepadextended.data

val defaultColorScheme = ColorScheme.SYSTEM
val defaultBaseColor = BaseColor.BLUE
val defaultGamepadBackground = GamepadBackground.SYSTEM
val defaultCustomGamepadBackground = 0xFF1B2030.toInt() // ARGB
const val defaultPollingDelay = 80 // in milliseconds
const val defaultHapticFeedbackEnabled = false // vibrations

/**
 * Whether vibration the game asks for is played on the phone.
 *
 * On by default, and separate from [defaultHapticFeedbackEnabled]: game rumble is
 * a feature of the game being played rather than feedback from touching a control,
 * so a player who finds the button haptics noisy can still feel the game. The
 * switch exists because the opposite is just as reasonable.
 */
const val defaultGameRumbleEnabled = true
const val defaultSaveConnectionCredentials = false
const val defaultFullScreenEnabled = false
const val defaultKeepScreenOn = true
const val defaultFullScreenCameraEnabled = false
const val defaultSlideBetweenButtons = true

/**
 * Whether the right stick answers a touch anywhere around its knob, rather than
 * only on the knob itself.
 *
 * The camera stick is the one the aiming thumb reaches for without looking, so a
 * target the size of the drawn knob is the one that gets missed; a wider area
 * catches the thumb that lands beside it. It is off by default because the same
 * padding overlaps whatever sits next to the stick on a crowded layout.
 */
const val defaultRightStickLargeTouchArea = false

/**
 * The same wide target for the left stick.
 *
 * The left stick is steered rather than aimed, so a thumb can usually find it,
 * but on a layout where it sits close to the D-pad the wider target is what lets
 * a player pick the stick up without looking. Off by default for the same reason
 * as the right one: the padding can overlap a neighbouring control.
 */
const val defaultLeftStickLargeTouchArea = false

/** 1.0 is the travel the response curve was designed around. */
const val defaultStickSensitivity = 1.0f

/** Below and above [defaultStickSensitivity]: a full push arrives sooner or later. */
val stickSensitivityRange = 0.5f..2.0f
const val stickSensitivityStep = 0.1f

/**
 * 1.0 is the travel the touch surface was mapped with.
 *
 * The surface is the one control that drives the mouse directly, so its gain is a
 * separate taste from the sticks': a small pad under the thumb needs a different
 * amount of movement per millimetre than a thumbstick with travel.
 */
const val defaultTouchSensitivity = 1.0f

/** Below and above [defaultTouchSensitivity]: the pointer moves less or more per touch. */
val touchSensitivityRange = 0.3f..3.0f
const val touchSensitivityStep = 0.1f

/** 1.0 is the turn rate the gyro curve was designed around. */
const val defaultGyroSensitivity = 1.0f

/** Below and above [defaultGyroSensitivity]: aim speed arrives at a smaller or wider turn. */
val gyroSensitivityRange = 0.3f..3.0f
const val gyroSensitivityStep = 0.1f

/**
 * Radians per second a resting gyroscope is assumed to report before it has been
 * calibrated. Zero means the phone is taken at its word, which is also what an
 * install that has never been calibrated should do rather than guessing.
 */
const val defaultGyroBias = 0f

/** How long the phone is held still while the gyroscope's resting bias is measured. */
const val gyroCalibrationMillis = 1200L

/**
 * Default button configurations, laid out so that **no two controls overlap**.
 *
 * Offsets are multipliers of the layout unit (`baseDp`, see
 * [GamepadGeometry]) and are applied as `offset * baseDp` from the anchored
 * edge, so `offsetX > 0` moves right, `offsetY > 0` moves down and so on.
 *
 * The screen is divided into clear bands:
 *
 * | band                        | controls                                            |
 * |-----------------------------|-----------------------------------------------------|
 * | top-left corner             | left shoulder, left trigger beside it                |
 * | top-right corner            | right trigger, right shoulder beside it              |
 * | top-centre rows (two)       | +/− / View, Home/Capture, SL/SR, gyro                |
 * | middle strip                | rear paddles (left of centre), macro triggers (right)|
 * | bottom-left / bottom-right  | D-pad and face buttons in the corners                |
 * | inboard of those            | the two thumbsticks                                  |
 * | bottom centre               | touch surface, in the gap between the sticks         |
 * | centre, beside the sticks   | the L3/R3 stick-click pair (sneak/crouch)            |
 *
 * `GamepadLayoutTest` fails if two visible controls in one of the built-in
 * presets ever overlap, or stray off-screen, so keep this in step with that.
 *
 * Only the controls a controller family actually has are visible by default; the
 * rest start hidden and are switched on by the matching preset in
 * [buttonConfigsForLayout], or by hand in the customisation screen.
 */
val defaultButtonConfigs = mapOf(
    // --- Bottom corners: the pads your thumbs land on first ---------------
    // D-pad owns the bottom-left corner.
    ButtonComponent.DPAD to ButtonConfig(
        component = ButtonComponent.DPAD,
        visible = true,
        scale = 0.85f,
        offsetX = 0f,
        offsetY = 0f,
        anchor = ButtonAnchor.BOTTOM_LEFT
    ),
    // Face buttons own the bottom-right corner.
    ButtonComponent.FACE_BUTTONS to ButtonConfig(
        component = ButtonComponent.FACE_BUTTONS,
        visible = true,
        scale = 0.85f,
        offsetX = 0f,
        offsetY = 0f,
        anchor = ButtonAnchor.BOTTOM_RIGHT
    ),
    // Thumbsticks sit inboard of the pads, so the corners stay for the pads.
    ButtonComponent.LEFT_ANALOG_STICK to ButtonConfig(
        component = ButtonComponent.LEFT_ANALOG_STICK,
        visible = true,
        scale = 1.0f,
        offsetX = 0.44f,
        offsetY = 0f,
        anchor = ButtonAnchor.BOTTOM_LEFT
    ),
    ButtonComponent.RIGHT_ANALOG_STICK to ButtonConfig(
        component = ButtonComponent.RIGHT_ANALOG_STICK,
        visible = true,
        scale = 1.0f,
        offsetX = -0.44f,
        offsetY = 0f,
        anchor = ButtonAnchor.BOTTOM_RIGHT
    ),

    // --- Top corners: shoulder buttons, with the triggers beside them -----
    ButtonComponent.LEFT_SHOULDER to ButtonConfig(
        component = ButtonComponent.LEFT_SHOULDER,
        visible = true,
        scale = 1.0f,
        offsetX = 0f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_LEFT
    ),
    ButtonComponent.LEFT_TRIGGER to ButtonConfig(
        component = ButtonComponent.LEFT_TRIGGER,
        visible = true,
        scale = 1.0f,
        offsetX = 0.155f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_LEFT
    ),
    ButtonComponent.RIGHT_TRIGGER to ButtonConfig(
        component = ButtonComponent.RIGHT_TRIGGER,
        visible = true,
        scale = 1.0f,
        offsetX = -0.155f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_RIGHT
    ),
    ButtonComponent.RIGHT_SHOULDER to ButtonConfig(
        component = ButtonComponent.RIGHT_SHOULDER,
        visible = true,
        scale = 1.0f,
        offsetX = 0f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_RIGHT
    ),

    // --- Centre column: three short rows of small system buttons ----------
    // Row 1. Padding +/− and View share a slot: no controller has both.
    ButtonComponent.PLUS_MINUS to ButtonConfig(
        component = ButtonComponent.PLUS_MINUS,
        visible = false,
        scale = 1.0f,
        offsetX = -0.42f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    ButtonComponent.SELECT_BUTTON to ButtonConfig(
        component = ButtonComponent.SELECT_BUTTON,
        visible = false,
        scale = 1.0f,
        offsetX = -0.65f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    // Gyro aiming lives at the right-hand end of row 1.
    ButtonComponent.GYRO_TOGGLE to ButtonConfig(
        component = ButtonComponent.GYRO_TOGGLE,
        visible = false,
        scale = 1.0f,
        offsetX = 0.42f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    // Row 2. SL/SR and Menu share a slot, for the same reason as +/− and View.
    ButtonComponent.SIDE_BUTTONS to ButtonConfig(
        component = ButtonComponent.SIDE_BUTTONS,
        visible = false,
        scale = 1.0f,
        offsetX = -0.42f,
        offsetY = 0.145f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    ButtonComponent.START_BUTTON to ButtonConfig(
        component = ButtonComponent.START_BUTTON,
        visible = false,
        scale = 1.0f,
        offsetX = -0.65f,
        offsetY = 0.145f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    ButtonComponent.HOME_CAPTURE to ButtonConfig(
        component = ButtonComponent.HOME_CAPTURE,
        visible = false,
        scale = 1.0f,
        offsetX = 0f,
        offsetY = 0.145f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    // Row 3, the middle strip: paddles left of centre, macros right of it.
    ButtonComponent.REAR_PADDLES to ButtonConfig(
        component = ButtonComponent.REAR_PADDLES,
        visible = false,
        scale = 1.0f,
        offsetX = -0.31f,
        offsetY = 0.29f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    ButtonComponent.MACRO_BUTTONS to ButtonConfig(
        component = ButtonComponent.MACRO_BUTTONS,
        visible = false,
        scale = 1.0f,
        offsetX = 0.31f,
        offsetY = 0.29f,
        anchor = ButtonAnchor.TOP_CENTER
    ),
    // Row 3, far right: the custom-key block sits in the band between the
    // shoulder buttons and the right stick, under the thumb that reaches for a
    // quick key. It is a 2x2 block (see GamepadGeometry.footprint), which is why
    // it clears the macro row to its left on every supported screen.
    ButtonComponent.CUSTOM_KEYS to ButtonConfig(
        component = ButtonComponent.CUSTOM_KEYS,
        visible = false,
        scale = 1.0f,
        offsetX = 0.8f,
        offsetY = 0.25f,
        anchor = ButtonAnchor.TOP_CENTER
    ),

    // --- Bottom centre: the touch surface fills the gap between the sticks
    ButtonComponent.TOUCH_SURFACE to ButtonConfig(
        component = ButtonComponent.TOUCH_SURFACE,
        visible = false,
        scale = 0.9f,
        offsetX = 0f,
        offsetY = 0f,
        anchor = ButtonAnchor.BOTTOM_CENTER
    ),

    // --- Middle of the screen: the L3/R3 pair, in the empty band between the
    // sticks and the rows above them. It is the one control that starts visible
    // everywhere the sticks are, because it is the button that keeps a stick
    // click out of the finger that steers.
    ButtonComponent.STICK_CLICKS to ButtonConfig(
        component = ButtonComponent.STICK_CLICKS,
        visible = true,
        scale = 1.0f,
        offsetX = 0f,
        offsetY = 0.45f,
        anchor = ButtonAnchor.TOP_CENTER
    ),

    // --- Top centre, right end of the first row: the phone-only HOLD button.
    // It is visible on every preset because it is a feature of the phone, not of
    // the controller family being imitated, and it sends nothing to the PC.
    ButtonComponent.HOLD to ButtonConfig(
        component = ButtonComponent.HOLD,
        visible = true,
        scale = 1.0f,
        offsetX = 0.65f,
        offsetY = 0f,
        anchor = ButtonAnchor.TOP_CENTER
    )
)
