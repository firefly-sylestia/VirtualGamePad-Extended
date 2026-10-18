package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.ButtonComponent
import com.fireflysylestia.virtualgamepadextended.data.ButtonConfig
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.FaceButtonStyle
import com.fireflysylestia.virtualgamepadextended.data.GamepadGeometry
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.CustomKeyBinding
import com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs
import com.fireflysylestia.virtualgamepadextended.data.defaultCustomKeyBindings
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroBias
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroSensitivity
import com.fireflysylestia.virtualgamepadextended.data.defaultStickSensitivity
import com.fireflysylestia.virtualgamepadextended.data.defaultTouchSensitivity
import com.fireflysylestia.virtualgamepadextended.ui.utils.ControlPress
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.InputHold
import com.fireflysylestia.virtualgamepadextended.ui.utils.KeyButtonDispatcher

/** How far the background is pushed toward the accent colour in camera mode. */
private const val CAMERA_TINT = 0.35f

/**
 * The live gamepad.
 *
 * Sizes and positions come from [GamepadGeometry] and the per-control
 * [ButtonConfig]s, so that the customisation screen can draw tap targets over
 * exactly the same rectangles.
 */
@Composable
fun DrawGamepad(
    widthDp: Int,
    heightDp: Int,
    gamepadState: GamepadReading,
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    controllerLayout: ControllerLayout = ControllerLayout.SWITCH_2,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
    // How far a stick push is amplified before it is sent.
    stickSensitivity: Float = defaultStickSensitivity,
    // How far a phone turn is amplified before it is sent.
    gyroSensitivity: Float = defaultGyroSensitivity,
    // The gyro's measured resting reading, taken off before it is shaped.
    gyroBiasX: Float = defaultGyroBias,
    gyroBiasY: Float = defaultGyroBias,
    // Whole screen aims the right stick, for games whose camera is on it.
    fullScreenCamera: Boolean = false,
    // A D-pad or face cluster follows a finger slid across it.
    slideBetweenButtons: Boolean = false,
    // The right stick answers a touch around its knob, not only on it.
    rightStickLargeTouchArea: Boolean = false,
    // The same wide target for the left stick.
    leftStickLargeTouchArea: Boolean = false,
    // Keeps whatever is down when the HOLD button is tapped held.
    hold: InputHold? = null,
    // Label and PC key for each custom-key button.
    customKeyBindings: List<CustomKeyBinding> = defaultCustomKeyBindings,
    // Control id to PC key, for the controls the player has bound to a key.
    controlKeyBindings: Map<String, String> = emptyMap(),
    // Whether the recorder panel is open, and whether a take is being recorded.
    recorderOpen: Boolean = false,
    isRecording: Boolean = false,
    onRecorderTap: () -> Unit = {},
    // How far a touch on the surface is amplified before it drives the pointer.
    touchSensitivity: Float = defaultTouchSensitivity,
) {
    val activeHold = hold ?: remember { InputHold() }

    // The controls route their presses through this, so it has to know the
    // bindings before any of them draws. Assigned on every recomposition: the
    // bindings flow can emit while the pad is on screen.
    ControlPress.keyBindings = controlKeyBindings

    DisposableEffect(gamepadState) {
        onDispose {
            // A hold must not outlive the gamepad, or the next visit would start
            // with the previous session's controls pressed.
            activeHold.clear()
            GamepadButtonDispatcher.releaseAll(gamepadState)
            KeyButtonDispatcher.releaseAll(gamepadState)
            gamepadState.LeftTrigger = 0f
            gamepadState.RightTrigger = 0f
            gamepadState.LeftThumbstickX = 0f
            gamepadState.LeftThumbstickY = 0f
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
        }
    }

    // Assuming landscape orientation. baseDp is the layout unit: the screen
    // height, capped on very wide screens so controls stay a sane size there.
    val baseDp = GamepadGeometry.baseDp(widthDp, heightDp)

    val deadZonePadding = GamepadGeometry.deadZoneDp(widthDp, heightDp)

    // Helper function to get config for a component
    // A configuration saved before a control existed does not contain it. Falling
    // back to its designed default (not a bare ButtonConfig) keeps the control
    // where the layout puts it instead of stacking it on the centre of the screen.
    fun getConfig(component: ButtonComponent) =
        buttonConfigs[component]
            ?: defaultButtonConfigs[component]
            ?: ButtonConfig.default(component)

    // Helper function to render a button component
    @Composable
    fun RenderComponent(component: ButtonComponent, content: @Composable (ButtonConfig) -> Unit) {
        val config = getConfig(component)
        if (config.visible) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = config.anchor.toAlignment()
            ) {
                content(config)
            }
        }
    }

    // The controller sits on black. A tinted background used to be the only thing a
    // "theme" changed, which is not what the word means to a player and competed
    // with the control styling that does mean it. Black also keeps the controls
    // legible whatever accent colour the app is set to.
    //
    // The one exception is the whole-screen camera, which still takes a hint of the
    // accent colour: that is a state of the controls, not decoration.
    val surfaceColour =
        if (fullScreenCamera) lerp(Color.Black, MaterialTheme.colorScheme.primary, CAMERA_TINT)
        else Color.Black

    Surface(color = surfaceColour) {
        // Declared before the controls, so it sits behind them: a touch that lands
        // on a control belongs to that control and never reaches this area.
        if (fullScreenCamera) {
            FullScreenStickArea(
                gamepadState = gamepadState,
                sensitivity = stickSensitivity,
            )
        }

        // First we make a box that will contain the gamepad
        // And put padding around it so that it doesn't touch the edges of the screen
        Box(
            modifier = Modifier
                .padding(deadZonePadding.dp)
                .fillMaxSize()
        ) {
            // Left Analog Stick
            RenderComponent(ButtonComponent.LEFT_ANALOG_STICK) { config ->
                AnalogStick(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    outerCircleWidth = (baseDp / 8 * config.scale).dp,
                    innerCircleRadius = (baseDp / 12 * config.scale).dp,
                    gamepadState = gamepadState,
                    type = AnalogStickType.LEFT,
                    clickButtonMask = bindings["left_stick_click"]?.mask,
                    clickControlKey = "left_stick_click",
                    sensitivity = stickSensitivity,
                    largeTouchArea = leftStickLargeTouchArea,
                )
            }

            // Right Analog Stick
            RenderComponent(ButtonComponent.RIGHT_ANALOG_STICK) { config ->
                AnalogStick(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    outerCircleWidth = (baseDp / 8 * config.scale).dp,
                    innerCircleRadius = (baseDp / 12 * config.scale).dp,
                    gamepadState = gamepadState,
                    type = AnalogStickType.RIGHT,
                    clickButtonMask = bindings["right_stick_click"]?.mask,
                    clickControlKey = "right_stick_click",
                    sensitivity = stickSensitivity,
                    largeTouchArea = rightStickLargeTouchArea,
                )
            }

            // D-Pad
            RenderComponent(ButtonComponent.DPAD) { config ->
                Dpad(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    size = (0.45 * baseDp * config.scale).dp,
                    gamepadState = gamepadState,
                    bindings = bindings,
                    slideBetweenButtons = slideBetweenButtons,
                )
            }

            // Face Buttons
            RenderComponent(ButtonComponent.FACE_BUTTONS) { config ->
                FaceButtons(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    size = (0.45 * baseDp * config.scale).dp,
                    gamepadState = gamepadState,
                    layout = when (controllerLayout.faceButtonStyle) {
                        FaceButtonStyle.NINTENDO -> FaceButtonLayout.NINTENDO_SWITCH
                        FaceButtonStyle.PLAYSTATION -> FaceButtonLayout.PLAYSTATION
                        FaceButtonStyle.XBOX -> FaceButtonLayout.XBOX
                    },
                    bindings = bindings,
                    slideBetweenButtons = slideBetweenButtons,
                )
            }

            // Left Trigger
            RenderComponent(ButtonComponent.LEFT_TRIGGER) { config ->
                Trigger(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    type = TriggerType.LEFT,
                    size = (baseDp / 6 * config.scale).dp,
                    gamepadState = gamepadState,
                    labelOverride = controllerLayout.leftTriggerLabel,
                )
            }

            // Right Trigger
            RenderComponent(ButtonComponent.RIGHT_TRIGGER) { config ->
                Trigger(
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    type = TriggerType.RIGHT,
                    size = (baseDp / 6 * config.scale).dp,
                    gamepadState = gamepadState,
                    labelOverride = controllerLayout.rightTriggerLabel,
                )
            }

            // Central Buttons (LB, RB, Select, Start)
            CentralButtons(
                baseDp = baseDp,
                gamepadState = gamepadState,
                buttonConfigs = buttonConfigs,
                controllerLayout = controllerLayout,
                bindings = bindings,
            )

            // Auxiliary controls used by Switch 2, Joy-Con-inspired, and Steam Deck layouts.
            RenderComponent(ButtonComponent.PLUS_MINUS) { config ->
                ExtraButtonCluster(
                    type = ExtraButtonClusterType.PLUS_MINUS,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 2, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    bindings = bindings,
                )
            }

            RenderComponent(ButtonComponent.HOME_CAPTURE) { config ->
                ExtraButtonCluster(
                    type = ExtraButtonClusterType.HOME_CAPTURE,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 2, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    bindings = bindings,
                )
            }

            RenderComponent(ButtonComponent.SIDE_BUTTONS) { config ->
                ExtraButtonCluster(
                    type = ExtraButtonClusterType.SIDE_BUTTONS,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 2, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    bindings = bindings,
                )
            }

            // L3/R3 on their own buttons, so sneak/crouch is not the stick the
            // player is steering with.
            RenderComponent(ButtonComponent.STICK_CLICKS) { config ->
                ExtraButtonCluster(
                    type = ExtraButtonClusterType.STICK_CLICKS,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 2, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    bindings = bindings,
                )
            }

            // Phone-only latch: holds the inputs that were down at the tap.
            RenderComponent(ButtonComponent.HOLD) { config ->
                HoldButton(
                    gamepadState = gamepadState,
                    hold = activeHold,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 1, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                )
            }

            RenderComponent(ButtonComponent.REAR_PADDLES) { config ->
                ExtraButtonCluster(
                    type = ExtraButtonClusterType.REAR_PADDLES,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 4, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    bindings = bindings,
                )
            }

            // Touch surface maps gestures onto the existing right-stick axes.
            RenderComponent(ButtonComponent.TOUCH_SURFACE) { config ->
                TouchSurface(
                    gamepadState = gamepadState,
                    size = (baseDp / 3 * config.scale).dp,
                    sensitivity = touchSensitivity,
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    )
                )
            }

            RenderComponent(ButtonComponent.GYRO_TOGGLE) { config ->
                GyroControl(
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 1, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    sensitivity = gyroSensitivity,
                    biasX = gyroBiasX,
                    biasY = gyroBiasY,
                )
            }

            // Custom key buttons: the Minecraft-style quick keys, drawn as a 2x2
            // block in the free band above the right stick.
            RenderComponent(ButtonComponent.CUSTOM_KEYS) { config ->
                CustomKeyCluster(
                    bindings = customKeyBindings,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 4, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                )
            }

            // The recorder: tapping it opens the panel the combination is
            // performed into. Like HOLD it sends nothing to the PC.
            RenderComponent(ButtonComponent.RECORDER) { config ->
                RecorderButton(
                    panelOpen = recorderOpen,
                    recording = isRecording,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 1, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    ),
                    onTap = onRecorderTap,
                )
            }
        }
    }
}
