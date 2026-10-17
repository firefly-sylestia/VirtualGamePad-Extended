package com.fireflysylestia.virtualgamepadextended.ui.composables

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.WindowManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroBias
import com.fireflysylestia.virtualgamepadextended.data.defaultGyroSensitivity
import com.fireflysylestia.virtualgamepadextended.ui.utils.GyroResponse

private const val tag = "GyroControl"

/**
 * Optional tilt-aim toggle, heard as the right stick.
 *
 * The sensor read here is the rotation vector, not the gyroscope. A gyroscope
 * reports how fast the phone is turning, which makes the stick a rate control:
 * hold a tilt and the camera keeps rotating for as long as it is held, so the view
 * spins past 360 degrees and never settles. A rotation vector reports which way the
 * phone is pointing, so the stick follows the tilt itself - lean the phone and the
 * camera holds that offset, bring it back level and it centres, exactly like a
 * stick held at an angle.
 *
 * The pose the controller was switched on in becomes neutral, so it does not
 * matter whether the phone is held in landscape or portrait, or tilted at the
 * moment the button is pressed. Tilt is measured from there, which also means the
 * calibration offset [biasX]/[biasY] measured is no longer needed - it described a
 * resting rate, and there is no rate here to correct.
 *
 * The axes are written only when the shaped value changes, so the on-screen right
 * stick still works while the phone is held level instead of being overwritten with
 * a centre value on every sample.
 *
 * @param sensitivity how far a tilt is amplified before it is shaped: above 1.0
 *        reaches full aim speed with a smaller lean, below 1.0 needs a wider one
 * @param biasX retained so callers keep compiling; superseded by the neutral pose
 * @param biasY retained so callers keep compiling; superseded by the neutral pose
 */
@Composable
fun GyroControl(
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
    sensitivity: Float = defaultGyroSensitivity,
    @Suppress("UNUSED_PARAMETER") biasX: Float = defaultGyroBias,
    @Suppress("UNUSED_PARAMETER") biasY: Float = defaultGyroBias,
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    // The game rotation vector leaves the magnetometer out, so it does not drift
    // when the phone passes a speaker or a metal desk. Devices without it fall back
    // to the full rotation vector, which fuses the compass as well.
    val sensor = remember {
        sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    var enabled by remember { mutableStateOf(false) }

    // Set when the sensor exists but the system would not hand it over, which
    // happens while another app is holding it. The button then says so instead of
    // toggling something that does nothing.
    var refused by remember { mutableStateOf(false) }

    // What the gyro last wrote, so a sample that shapes to the same value does not
    // overwrite a stick the finger is pushing.
    var writtenX by remember { mutableFloatStateOf(0f) }
    var writtenY by remember { mutableFloatStateOf(0f) }

    DisposableEffect(enabled, sensor, sensitivity) {
        if (!enabled || sensor == null) {
            // The axes are only ever written while tilt aiming is on, so switching
            // it off has to hand them back to the sticks and the touch surface.
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
            return@DisposableEffect onDispose { }
        }

        // A new response per switch-on, so turning it back on starts from rest.
        val response = GyroResponse()

        // Captured from the first sample after switch-on; NaN means "not yet".
        var neutralPitch = Float.NaN
        var neutralRoll = Float.NaN

        // The screen's rotation, so a lean means the same thing whichever way the
        // phone is being held. The sensor's axes are fixed to the device, not the
        // screen, so they have to be remapped before they mean left/right and up/down.
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        @Suppress("DEPRECATION")
        val displayRotation = windowManager?.defaultDisplay?.rotation
            ?: android.view.Surface.ROTATION_0

        val rotationMatrix = FloatArray(9)
        val remappedMatrix = FloatArray(9)
        val orientation = FloatArray(3)

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 3) return

                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

                val (axisX, axisY) = when (displayRotation) {
                    android.view.Surface.ROTATION_90 ->
                        SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X

                    android.view.Surface.ROTATION_180 ->
                        SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y

                    android.view.Surface.ROTATION_270 ->
                        SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X

                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(
                    rotationMatrix, axisX, axisY, remappedMatrix,
                )
                SensorManager.getOrientation(remappedMatrix, orientation)

                val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()

                // The pose the phone was in when tilt aiming was switched on is the
                // centre of the stick, so no separate calibration step is needed.
                if (neutralPitch.isNaN()) {
                    neutralPitch = pitch
                    neutralRoll = roll
                    return
                }

                val deltaPitch = wrapDegrees(pitch - neutralPitch)
                val deltaRoll = wrapDegrees(roll - neutralRoll)

                val (x, y) = response.apply(
                    deltaRoll * sensitivity,
                    deltaPitch * sensitivity,
                    event.timestamp,
                )

                // The stick's Y grows downwards, so tipping the phone's top edge up
                // aims up.
                val stickX = x
                val stickY = -y

                if (stickX != writtenX) {
                    gamepadState.RightThumbstickX = stickX
                    writtenX = stickX
                }
                if (stickY != writtenY) {
                    gamepadState.RightThumbstickY = stickY
                    writtenY = stickY
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (!sensorManager.registerListener(
                listener, sensor, SensorManager.SENSOR_DELAY_GAME,
            )
        ) {
            Log.w(tag, "Motion sensor is present but could not be opened")
            refused = true
            enabled = false
            return@DisposableEffect onDispose { }
        }
        refused = false

        onDispose {
            sensorManager.unregisterListener(listener)
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
            writtenX = 0f
            writtenY = 0f
        }
    }

    // Nothing can ever be sent without a sensor, so the toggle stays off and says
    // why rather than looking like a control that does nothing.
    val unavailable = sensor == null || refused

    Button(
        onClick = { if (!unavailable) enabled = !enabled },
        enabled = !unavailable,
        modifier = modifier.size(width = metrics.width, height = metrics.height),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        // Short labels: the button keeps a fixed, predictable footprint so the
        // customisation screen can draw its tap target in the same place.
        Text(
            text = when {
                unavailable -> "N/A"
                enabled -> "ON"
                else -> "GYRO"
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

/**
 * Brings an angle difference back into -180..180.
 *
 * Pitch and roll can each wrap as the phone passes a right angle, and a wrapped
 * difference read straight would throw the stick to the opposite edge for a
 * fraction of a second.
 */
private fun wrapDegrees(degrees: Float): Float {
    var value = degrees
    while (value > 180f) value -= 360f
    while (value < -180f) value += 360f
    return value
}
