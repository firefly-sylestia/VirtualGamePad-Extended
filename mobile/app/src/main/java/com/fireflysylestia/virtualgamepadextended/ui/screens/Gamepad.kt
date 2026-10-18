package com.fireflysylestia.virtualgamepadextended.ui.screens

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices.DESKTOP
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Devices.TABLET
import androidx.compose.ui.tooling.preview.Preview
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.R
import com.fireflysylestia.virtualgamepadextended.data.PreviewBase
import com.fireflysylestia.virtualgamepadextended.data.PreviewHeightDp
import com.fireflysylestia.virtualgamepadextended.data.PreviewWidthDp
import com.fireflysylestia.virtualgamepadextended.data.SettingsRepository
import com.fireflysylestia.virtualgamepadextended.data.ControllerLayout
import com.fireflysylestia.virtualgamepadextended.data.GamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.LocalGamepadSkin
import com.fireflysylestia.virtualgamepadextended.data.defaultControllerBindings
import com.fireflysylestia.virtualgamepadextended.data.defaultButtonConfigs
import com.fireflysylestia.virtualgamepadextended.data.defaultFullScreenCameraEnabled
import com.fireflysylestia.virtualgamepadextended.data.RECORDED_MACRO_LIMIT
import com.fireflysylestia.virtualgamepadextended.data.defaultCustomKeyBindings
import com.fireflysylestia.virtualgamepadextended.data.defaultLeftStickLargeTouchArea
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroBias
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroSensitivity
import com.fireflysylestia.virtualgamepadextended.data.defaultPollingDelay
import com.fireflysylestia.virtualgamepadextended.data.defaultRightStickLargeTouchArea
import com.fireflysylestia.virtualgamepadextended.data.defaultSlideBetweenButtons
import com.fireflysylestia.virtualgamepadextended.data.defaultStickSensitivity
import com.fireflysylestia.virtualgamepadextended.data.defaultTouchSensitivity
import com.fireflysylestia.virtualgamepadextended.network.ConnectionViewModel
import com.fireflysylestia.virtualgamepadextended.ui.composables.DrawGamepad
import com.fireflysylestia.virtualgamepadextended.ui.composables.MacroRecorderPanel
import com.fireflysylestia.virtualgamepadextended.ui.utils.GamepadButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.InputHold
import com.fireflysylestia.virtualgamepadextended.ui.utils.KeyButtonDispatcher
import com.fireflysylestia.virtualgamepadextended.ui.utils.MacroPlayer
import com.fireflysylestia.virtualgamepadextended.ui.utils.MacroRecorder
import com.fireflysylestia.virtualgamepadextended.ui.utils.findActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val tag = "GamePadScreen"

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun GamePad(
    connectionViewModel: ConnectionViewModel?,
    onNavigateBack: () -> Unit,
) {
    // Plain `remember`, not `rememberSaveable`: GamepadReading is a generated Colfer
    // class and is not Parcelable/Serializable, so saving it crashes on backgrounding
    // (white screen). It is transient input state and is rebuilt on every entry.
    val gamepadState by remember { mutableStateOf(GamepadReading()) }
    val context = LocalContext.current
    val settingsRepository = remember { SettingsRepository(context) }
    val pollingDelay =
        settingsRepository.pollingDelay.collectAsState(defaultPollingDelay).value.toLong()
    val buttonConfigs =
        settingsRepository.buttonConfigs.collectAsState(defaultButtonConfigs).value
    val controllerLayout =
        settingsRepository.controllerLayout.collectAsState(ControllerLayout.SWITCH_2).value
    val bindings = settingsRepository.controllerBindings.collectAsState(defaultControllerBindings).value
    val controlKeyBindings =
        settingsRepository.controlKeyBindings.collectAsState(emptyMap()).value
    val recordedMacros = settingsRepository.recordedMacros.collectAsState(emptyList()).value
    val stickSensitivity =
        settingsRepository.stickSensitivity.collectAsState(defaultStickSensitivity).value
    val gyroSensitivity =
        settingsRepository.gyroSensitivity.collectAsState(defaultGyroSensitivity).value
    val gyroCalibration =
        settingsRepository.gyroCalibration.collectAsState(defaultGyroBias to defaultGyroBias).value
    val fullScreenCamera =
        settingsRepository.fullScreenCameraEnabled.collectAsState(defaultFullScreenCameraEnabled).value
    val slideBetweenButtons =
        settingsRepository.slideBetweenButtons.collectAsState(defaultSlideBetweenButtons).value

    val rightStickLargeTouchArea = settingsRepository.rightStickLargeTouchArea
        .collectAsState(defaultRightStickLargeTouchArea).value

    val leftStickLargeTouchArea = settingsRepository.leftStickLargeTouchArea
        .collectAsState(defaultLeftStickLargeTouchArea).value

    val customKeyBindings = settingsRepository.customKeyBindings
        .collectAsState(defaultCustomKeyBindings).value

    val touchSensitivity = settingsRepository.touchSensitivity
        .collectAsState(defaultTouchSensitivity).value
    val gamepadSkin = settingsRepository.gamepadSkin.collectAsState(GamepadSkin.DEFAULT).value

    val configuration = LocalConfiguration.current

    val screenHeight = configuration.screenHeightDp
    val screenWidth = configuration.screenWidthDp

    val isStopping = remember { mutableStateOf(false) }

    // Holds the controls that were down when the HOLD button was tapped. Kept
    // here, above the renderer, so the send loop can re-apply the snapshot to
    // every packet rather than only to the one the tap happened on.
    val inputHold = remember { InputHold() }

    // The recorder and its player live here, above the renderer, because both own
    // loops that outlive a recomposition: the recorder samples while a take is
    // running, and the player walks a recording's clock.
    val recorder = remember { MacroRecorder() }
    val player = remember { MacroPlayer() }
    var showRecorder by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(recorder.isRecording) {
        if (recorder.isRecording) recorder.sample(gamepadState)
    }

    // Keyed on the player's session: starting a take again restarts the walk.
    LaunchedEffect(player.session) { player.run(gamepadState) }

    // The skin decides how the controls are drawn; the field behind them stays
    // black, so the two no longer compete.
    CompositionLocalProvider(LocalGamepadSkin provides gamepadSkin) {
      Box(modifier = Modifier.fillMaxSize()) {
        DrawGamepad(
            widthDp = screenWidth,
            heightDp = screenHeight,
            gamepadState = gamepadState,
            buttonConfigs = buttonConfigs,
            controllerLayout = controllerLayout,
            bindings = bindings,
            controlKeyBindings = controlKeyBindings,
            recorderOpen = showRecorder,
            isRecording = recorder.isRecording,
            onRecorderTap = { showRecorder = !showRecorder },
            stickSensitivity = stickSensitivity,
            gyroSensitivity = gyroSensitivity,
            gyroBiasX = gyroCalibration.first,
            gyroBiasY = gyroCalibration.second,
            fullScreenCamera = fullScreenCamera,
            slideBetweenButtons = slideBetweenButtons,
            rightStickLargeTouchArea = rightStickLargeTouchArea,
            leftStickLargeTouchArea = leftStickLargeTouchArea,
            hold = inputHold,
            customKeyBindings = customKeyBindings,
            touchSensitivity = touchSensitivity,
        )

        // The recorder sits over the pad rather than in a dialog: the player has
        // to be able to perform the combination on the controls while it watches.
        if (showRecorder) {
            MacroRecorderPanel(
                recorder = recorder,
                player = player,
                recorded = recordedMacros,
                gamepadState = gamepadState,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                onSave = { take ->
                    scope.launch {
                        settingsRepository.setRecordedMacros(
                            (recordedMacros + take).takeLast(RECORDED_MACRO_LIMIT)
                        )
                    }
                },
                onDelete = { take ->
                    player.stop()
                    scope.launch {
                        settingsRepository.setRecordedMacros(recordedMacros - take)
                    }
                },
                onDismiss = { showRecorder = false },
            )
        }
      }
    }

    val activity = LocalContext.current.findActivity()
    // disconnect on back press
    androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
        .addObserver(androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_DESTROY
                && activity?.isChangingConfigurations != true // ignore screen rotation
            ) {
                // Released on the main thread, where the hold's Compose state is
                // allowed to change.
                inputHold.clear()
                player.stop()
                recorder.cancel()
                if (connectionViewModel != null) {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            isStopping.value = true
                            // unset all keys before disconnecting
                            gamepadState.ButtonsUp = gamepadState.ButtonsDown
                            gamepadState.ButtonsDown = 0
                            gamepadState.LeftThumbstickX = 0F
                            gamepadState.LeftThumbstickY = 0F
                            gamepadState.RightThumbstickX = 0F
                            gamepadState.RightThumbstickY = 0F
                            gamepadState.LeftTrigger = 0F
                            gamepadState.RightTrigger = 0F
                            gamepadState.KeysDown = emptyArray()
                            connectionViewModel.enqueueGamepadState(gamepadState)
                            connectionViewModel.disconnect()
                            Log.d(tag, "Disconnected")
                        } catch (e: Exception) {
                            Log.d(tag, "Error during disconnect: ${e.message}")
                        }
                    }
                }
            }
        })

    // Monitor connection state and handle errors
    val connectionState by connectionViewModel?.uiState?.collectAsState()
        ?: remember { mutableStateOf(null) }

    val connectionLostMessage = connectionState?.takeIf { !it.connected }?.let { state ->
        state.error?.let {
            stringResource(R.string.gamepad_connection_lost_error, it)
        } ?: stringResource(R.string.gamepad_connection_lost)
    }

    LaunchedEffect(connectionLostMessage) {
        if (connectionLostMessage != null) {
            Log.d(tag, connectionLostMessage)
            Toast.makeText(context, connectionLostMessage, Toast.LENGTH_LONG).show()
            onNavigateBack()
        }
    }

    val startAfter = 100L // in milliseconds

    // Send gamepad state updates periodically
    LaunchedEffect(gamepadState, pollingDelay) {
        delay(startAfter)

        // Start sending updates
        while (connectionViewModel != null && !isStopping.value) {
            // Re-apply a latched hold just before the snapshot is taken, so a
            // control released while the hold is on still reaches the PC down.
            inputHold.applyTo(gamepadState)

            // Queue the update in the ViewModel
            connectionViewModel.enqueueGamepadState(gamepadState)

            // The packet above carried whatever was pressed before it, so any
            // release that was waiting for it can go out now. This also clears
            // ButtonsUp, and only after the edges have been transmitted.
            GamepadButtonDispatcher.afterSend(gamepadState)
            // The same for the custom-key names: a tapped key stays in the array
            // until the packet that carried it has gone out.
            KeyButtonDispatcher.afterSend(gamepadState)

            // Wait before next update
            delay(pollingDelay)
        }
    }
}

@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
@Preview(
    name = "Design Preview (Light)",
    device = "spec:width=${PreviewWidthDp}dp,height=${PreviewHeightDp}dp,orientation=landscape,dpi=420",
)
@Preview(
    name = "Design Preview (Dark)",
    device = "spec:width=${PreviewWidthDp}dp,height=${PreviewHeightDp}dp,orientation=landscape,dpi=420",
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "Phone - Landscape (Light)",
    device = "spec:width=411dp,height=891dp,orientation=landscape,dpi=420",
    showSystemUi = true
)
@Preview(
    name = "Phone - Landscape (Dark)",
    device = "spec:width=411dp,height=891dp,orientation=landscape,dpi=420",
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "Tablet (Light)",
    device = TABLET,
    showSystemUi = true
)
@Preview(
    name = "Tablet (Dark)",
    device = TABLET,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "Desktop (Light)",
    device = DESKTOP,
    showSystemUi = true
)
@Preview(
    name = "Desktop (Dark)",
    device = DESKTOP,
    showSystemUi = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class MultiDevicePreview

@MultiDevicePreview
@Composable
fun GamePadPreview() {
    PreviewBase {
        GamePad(null) {}
    }
}
