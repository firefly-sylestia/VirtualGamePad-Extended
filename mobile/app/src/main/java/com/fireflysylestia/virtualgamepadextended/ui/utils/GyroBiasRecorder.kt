package com.fireflysylestia.virtualgamepadextended.ui.utils

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Measures the bias a gyroscope reports while the phone is held still.
 *
 * Every gyroscope reads something other than zero at rest, and that offset is
 * what makes a held phone creep. Subtracting it is the one part of gyro aiming
 * that cannot be worked out from a single reading, because at rest the bias and a
 * very slow turn look identical — so it is measured instead, over a second or so
 * of the phone being put down or held steady.
 *
 * The average is taken on the two axes the aim uses, in the same order the
 * control reads them: X first (`values[1]`, the turn that aims left and right),
 * then Y (`values[0]`).
 */
class GyroBiasRecorder(
    private val sensorManager: SensorManager,
    private val sensor: Sensor,
) : SensorEventListener {

    private var sumX = 0.0
    private var sumY = 0.0
    private var samples = 0
    private var registered = false

    /** Starts the measurement, discarding anything measured before. */
    fun start(): Boolean {
        sumX = 0.0
        sumY = 0.0
        samples = 0
        registered =
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        return registered
    }

    /** The average reading so far, or `null` when the sensor has said nothing. */
    fun bias(): Pair<Float, Float>? =
        if (samples == 0) null else (sumX / samples).toFloat() to (sumY / samples).toFloat()

    /** How many readings went into the average. */
    fun sampleCount(): Int = samples

    /** Stops the measurement. Harmless to call when it was never started. */
    fun stop() {
        if (!registered) return
        registered = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.values.size < 2) return
        sumX += event.values[1]
        sumY += event.values[0]
        samples++
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
