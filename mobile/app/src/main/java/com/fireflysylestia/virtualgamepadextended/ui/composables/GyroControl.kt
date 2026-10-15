package com.fireflysylestia.virtualgamepadextended.ui.composables

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.abs

/**
 * Optional gyro-aim toggle. Gyroscope angular velocity is translated to the existing
 * right-stick axes, so no motion fields or protocol-version change is required.
 */
@Composable
fun GyroControl(
    gamepadState: GamepadReading,
    metrics: AuxiliaryButtonMetrics,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sensorManager = remember {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val gyro = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) }
    var enabled by remember { mutableStateOf(false) }

    DisposableEffect(enabled, gyro) {
        if (enabled && gyro != null) {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    if (event.values.size >= 2) {
                        // Sensitivity is intentionally conservative; axes are clamped by design.
                        gamepadState.RightThumbstickX = (event.values[1] * 1.6f).coerceIn(-1f, 1f)
                        gamepadState.RightThumbstickY = (-event.values[0] * 1.6f).coerceIn(-1f, 1f)
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            sensorManager.registerListener(listener, gyro, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                sensorManager.unregisterListener(listener)
                gamepadState.RightThumbstickX = 0f
                gamepadState.RightThumbstickY = 0f
            }
        } else {
            gamepadState.RightThumbstickX = 0f
            gamepadState.RightThumbstickY = 0f
            onDispose { }
        }
    }

    Button(
        onClick = { if (gyro != null) enabled = !enabled },
        enabled = gyro != null,
        modifier = modifier.size(width = metrics.width, height = metrics.height),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 0.dp),
    ) {
        // Short labels: the button keeps a fixed, predictable footprint so the
        // customisation screen can draw its tap target in the same place.
        Text(
            text = when {
                gyro == null -> "N/A"
                enabled -> "ON"
                else -> "GYRO"
            },
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}
