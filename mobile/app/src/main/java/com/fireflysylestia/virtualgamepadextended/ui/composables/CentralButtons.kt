package com.fireflysylestia.virtualgamepadextended.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import androidx.compose.foundation.shape.RoundedCornerShape
import com.fireflysylestia.virtualgamepadextended.data.ButtonComponent
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.ButtonConfig
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.ControllerAction
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings

enum class ShoulderButtonType {
    LEFT, RIGHT
}

enum class MenuButtonType {
    VIEW, MENU
}

@Composable
fun ShoulderButton(
    type: ShoulderButtonType,
    modifier: Modifier = Modifier,
    size: Dp,
    gamepadState: GamepadReading,
    labelOverride: String? = null,
    actionMask: Int? = null,
) {
    val gameButton = when (type) {
        ShoulderButtonType.LEFT -> GameButtons.LeftShoulder
        ShoulderButtonType.RIGHT -> GameButtons.RightShoulder
    }
    val buttonMask = actionMask ?: gameButton.value
    val text = labelOverride ?: when (type) {
        ShoulderButtonType.LEFT -> stringResource(R.string.button_l_shoulder)
        ShoulderButtonType.RIGHT -> stringResource(R.string.button_r_shoulder)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    // Held for as long as the finger is down; never latches on. Going through the
    // dispatcher keeps a remapped second source from releasing this one early.
    PressAndHold(
        pressed = isPressed,
        source = "shoulder_${type.name}",
        gamepadState = gamepadState,
        mask = buttonMask,
        controlKey = when (type) {
            ShoulderButtonType.LEFT -> "left_shoulder"
            ShoulderButtonType.RIGHT -> "right_shoulder"
        },
    )

    Button(
        modifier = modifier
            .heightIn(min = size)
            // Wide and short: this is the bumper, and the trigger sits beside it.
            // The padding is small so a two-letter label still fits at the sizes
            // a phone ends up with.
            .widthIn(min = size * 1.5f),
        // The skin owns the outline of every control, so the bumpers follow it too.
        shape = RoundedCornerShape(LocalGamepadSkin.current.cornerRadius),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        border = BorderStroke(
            if (isPressed) LocalGamepadSkin.current.pressedBorderWidth
            else LocalGamepadSkin.current.borderWidth,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = ButtonDefaults.buttonColors(
            // `surface` on a `surface` gamepad is invisible, and a label that comes
            // out the same shade as its own container is unreadable: take the
            // matching on-colour of whichever container is being used.
            containerColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        onClick = { },
        interactionSource = interactionSource,
    ) {
        Text(text)
    }
}

@Composable
fun MenuButton(
    type: MenuButtonType,
    modifier: Modifier = Modifier,
    size: Dp,
    gamepadState: GamepadReading,
    actionMask: Int? = null,
) {
    val gameButton = when (type) {
        MenuButtonType.VIEW -> GameButtons.View
        MenuButtonType.MENU -> GameButtons.Menu
    }
    val buttonMask = actionMask ?: gameButton.value
    val iconPainter = when (type) {
        MenuButtonType.VIEW -> null
        MenuButtonType.MENU -> painterResource(R.drawable.ic_menu)
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    PressAndHold(
        pressed = isPressed,
        source = "menu_${type.name}",
        gamepadState = gamepadState,
        mask = buttonMask,
        controlKey = when (type) {
            MenuButtonType.VIEW -> "view"
            MenuButtonType.MENU -> "menu"
        },
    )

    OutlinedIconButton(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(LocalGamepadSkin.current.cornerRadius),
        colors = IconButtonDefaults.outlinedIconButtonColors(
            containerColor = if (isPressed) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            contentColor = if (isPressed) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        border = BorderStroke(
            if (isPressed) LocalGamepadSkin.current.pressedBorderWidth
            else LocalGamepadSkin.current.borderWidth,
            if (isPressed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        onClick = { },
        interactionSource = interactionSource,
    ) {
        // The icon follows the button's own content colour, so it stays legible on
        // the pressed and unpressed containers alike.
        val iconTint = if (isPressed) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        if (iconPainter != null) {
            Icon(
                painter = iconPainter,
                contentDescription = stringResource(R.string.content_desc_button, gameButton.name),
                modifier = Modifier.size(size / 2),
                tint = iconTint
            )
        } else {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.screenicon),
                contentDescription = stringResource(R.string.content_desc_button, gameButton.name),
                modifier = Modifier.size(size / 2),
                tint = iconTint
            )
        }
    }
}

/**
 * The central buttons section containing shoulder buttons (L/R bumpers) and menu buttons (View/Menu) for the gamepad.
 */
@Composable
fun CentralButtons(
    modifier: Modifier = Modifier,
    baseDp: Float,
    gamepadState: GamepadReading,
    buttonConfigs: Map<ButtonComponent, ButtonConfig>,
    controllerLayout: ControllerLayout = ControllerLayout.SWITCH_2,
    bindings: Map<String, ControllerAction> = defaultControllerBindings,
) {
    // Helper function to get config for a component
    fun getConfig(component: ButtonComponent) =
        buttonConfigs[component] ?: ButtonConfig.default(component)

    // Helper function to render a button component with its anchor
    @Composable
    fun RenderButton(component: ButtonComponent, content: @Composable (ButtonConfig) -> Unit) {
        val config = getConfig(component)
        if (config.visible) {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = config.anchor.toAlignment()
            ) {
                content(config)
            }
        }
    }

    // Left Shoulder button
    RenderButton(ButtonComponent.LEFT_SHOULDER) { config ->
        ShoulderButton(
            type = ShoulderButtonType.LEFT,
            modifier = Modifier.offset(
                x = (config.offsetX * baseDp).dp,
                y = (config.offsetY * baseDp).dp
            ),
            size = (baseDp / 8 * config.scale).dp,
            gamepadState = gamepadState,
            labelOverride = controllerLayout.leftShoulderLabel,
            actionMask = bindings["left_shoulder"]?.mask,
        )
    }

    // Right Shoulder button
    RenderButton(ButtonComponent.RIGHT_SHOULDER) { config ->
        ShoulderButton(
            type = ShoulderButtonType.RIGHT,
            modifier = Modifier.offset(
                x = (config.offsetX * baseDp).dp,
                y = (config.offsetY * baseDp).dp
            ),
            size = (baseDp / 8 * config.scale).dp,
            gamepadState = gamepadState,
            labelOverride = controllerLayout.rightShoulderLabel,
            actionMask = bindings["right_shoulder"]?.mask,
        )
    }

    // Select button (View)
    RenderButton(ButtonComponent.SELECT_BUTTON) { config ->
        MenuButton(
            type = MenuButtonType.VIEW,
            modifier = Modifier.offset(
                x = (config.offsetX * baseDp).dp,
                y = (config.offsetY * baseDp).dp
            ),
            size = (baseDp / 8 * config.scale).dp,
            gamepadState = gamepadState,
            actionMask = bindings["view"]?.mask,
        )
    }

    // Start button (Menu)
    RenderButton(ButtonComponent.START_BUTTON) { config ->
        MenuButton(
            type = MenuButtonType.MENU,
            modifier = Modifier.offset(
                x = (config.offsetX * baseDp).dp,
                y = (config.offsetY * baseDp).dp
            ),
            size = (baseDp / 8 * config.scale).dp,
            gamepadState = gamepadState,
            actionMask = bindings["menu"]?.mask,
        )
    }
}

@Preview(showBackground = false)
@Composable
fun CentralButtonsPreview() {
    CentralButtons(
        baseDp = 400f,
        gamepadState = GamepadReading(),
        buttonConfigs = com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs,
    )
}
