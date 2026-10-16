package com.fireflysylestia.virtualgamepadextended.ui.composables

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
 * Optional gyro-aim toggle. Gyroscope angular velocity is translated to the existing
 * right-stick axes, so no motion fields or protocol-version change is required.
 *
 * The raw reading is not sent: [GyroResponse] filters it, holds a dead zone and
 * shapes what is left, which is the difference between an aim that drifts and
 * shakes and one that follows the phone. The filter is told when each reading was
 * taken, so it smooths by elapsed time and feels the same on a phone that reports
 * its sensor twice as often. While the phone is still the gyro reports nothing at
 * all, so the on-screen right stick keeps working underneath it instead of being
 * overwritten fifty times a second: the axes are only written when there is a turn
 * to report.
 *
 * @param sensitivity how far a turn is amplified before it is shaped: above 1.0
 *        reaches full aim speed with a smaller turn, below 1.0 needs a wider one
 * @param biasX resting reading of the left/right axis, as calibration measured it
 * @param biasY resting reading of the up/down axis, as calibration measured it
 */
@Composable
fun GyroControl(
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
    sensitivity: Float = defaultGyroSensitivity,
    biasX: Float = defaultGyroBias,
    biasY: Float = defaultGyroBias,
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val gyro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var enabled by remember { mutableStateOf(false) }

    // Set when the sensor exists but the system would not hand it over, which
    // happens while another app is holding it. The button then says so instead of
    // toggling something that does nothing.
    var refused by remember { mutableStateOf(false) }

    DisposableEffect(enabled, gyro, sensitivity, biasX, biasY) {
        if (!enabled || gyro == null) {
            // The axes are only ever written while the gyro is on, so switching it
            // off has to hand them back to the sticks and the touch surface.
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
            return@DisposableEffect onDispose { }
        }

        // A new response per switch-on, so turning the gyro back on starts from
        // rest rather than from whatever the last session happened to be turning
        // at.
        val response = GyroResponse()

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.values.size < 2) return

                // values[1] is the turn that aims left and right on a phone held in
                // landscape; values[0] is the turn that aims up and down, and the
                // stick's Y grows downwards.
                // The bias measured during calibration comes off first: it is an
                // offset in the reading itself, not something the player's turn
                // scaling should touch.
                val (x, y) = response.apply(
                    (event.values[1] - biasX) * sensitivity,
                    (event.values[0] - biasY) * sensitivity,
                    event.timestamp,
                )

                if (x != 0f || y != 0f) {
                    gamepadState.RightThumbstickX = x
                    gamepadState.RightThumbstickY = -y
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (!sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)) {
            Log.w(tag, "Gyroscope is present but could not be opened")
            refused = true
            enabled = false
            return@DisposableEffect onDispose { }
        }
        refused = false

        onDispose {
            sensorManager.unregisterListener(listener)
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
        }
    }

    // Nothing can ever be sent without a sensor, so the toggle stays off and says
    // why rather than looking like a control that does nothing.
    val unavailable = gyro == null || refused

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
