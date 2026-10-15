package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.ButtonComponent
import com.fireflysylestia.virtualgamepadextended.data.ButtonConfig
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.FaceButtonStyle
import com.fireflysylestia.virtualgamepadextended.data.GamepadGeometry
import com.fireflysylestia.virtualgamepadextended.data.MacroDefinition
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher

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
    macros: List<MacroDefinition> = emptyList(),
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
) {
    DisposableEffect(gamepadState) {
        onDispose {
            GamepadButtonDispatcher.releaseAll(gamepadState)
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
    fun getConfig(component: ButtonComponent) =
        buttonConfigs[component] ?: ButtonConfig.default(component)

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

    // First we make a box that will contain the gamepad
    // And put padding around it so that it doesn't touch the edges of the screen
    Surface {
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
                    )
                )
            }

            RenderComponent(ButtonComponent.MACRO_BUTTONS) { config ->
                MacroButtonCluster(
                    macros = macros,
                    gamepadState = gamepadState,
                    metrics = AuxiliaryButtonMetrics.of(baseDp, 4, config.scale),
                    modifier = Modifier.offset(
                        x = (config.offsetX * baseDp).dp,
                        y = (config.offsetY * baseDp).dp
                    )
                )
            }
        }
    }
}
