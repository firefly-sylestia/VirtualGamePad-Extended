package com.fireflysylestia.virtualgamepadextended.ui.utils

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign

/**
 * Shapes raw gyroscope readings into the right-stick axes.
 *
 * A gyroscope hands over angular velocity, not an aimed direction, and a raw
 * sample is neither smooth nor still:
 *
 * - **Filtering.** Each sample is mixed into the previous one, so the noise a
 *   gyroscope carries at its sample rate stops reaching the camera as a shake.
 *   Without this the aim visibly trembles, because the sensor's own noise is
 *   larger than the smallest useful turn. The mix is worked out from how long
 *   the sample took rather than being a fixed share of each sample: a device
 *   that hands over its readings twice as often must smooth each one the same
 *   amount in *time*, or the filter all but disappears on a fast sensor and the
 *   shake comes straight back.
 * - **A dead zone.** A resting phone does not read exactly zero, and that bias
 *   has no direction the player asked for: without a dead zone the camera creeps
 *   while the phone is held still.
 * - **A curve.** Past the dead zone the remaining rate is rescaled so full travel
 *   is still reachable, then raised to [GAMMA]. Below one it lifts the slow, small
 *   turns that aiming is actually made of, so a gentle correction moves the view
 *   instead of nothing happening until the phone is swung.
 *
 * The rate is measured in radians per second, which is what
 * `Sensor.TYPE_GYROSCOPE` reports, so [FULL_TRAVEL] is a real turn rate a hand can
 * produce rather than an arbitrary number.
 */
class GyroResponse {

    private var filteredX = 0f
    private var filteredY = 0f

    /** Timestamp of the sample the filter last saw, or 0 before the first one. */
    private var lastSampleNanos = 0L

    /** Drops the filtered value, for a gyro that has just been switched on. */
    fun reset() {
        filteredX = 0f
        filteredY = 0f
        lastSampleNanos = 0L
    }

    /**
     * @param rawX turn rate around the vertical axis, radians per second
     * @param rawY turn rate around the horizontal axis, radians per second
     * @param sampleNanos when the reading was taken, as `SensorEvent.timestamp`
     *        reports it; 0 when the caller has no timestamp, which then uses the
     *        rate the sensor was asked for
     * @return the right-stick value to send, each axis in -1..1
     */
    fun apply(rawX: Float, rawY: Float, sampleNanos: Long = 0L): Pair<Float, Float> {
        val step = stepSeconds(sampleNanos)

        filteredX = smooth(filteredX, rawX, step)
        filteredY = smooth(filteredY, rawY, step)
        return shaped(filteredX) to shaped(filteredY)
    }

    /**
     * How long the sample took, in seconds.
     *
     * The first sample has nothing to measure against, so it counts as one
     * [NOMINAL_STEP_SECONDS]; a gap outside [MIN_STEP_SECONDS]..[MAX_STEP_SECONDS]
     * is brought back into it, which is what a sensor that stalls and then
     * delivers everything at once would otherwise throw at the filter — either
     * smoothing nothing, or a step so large that the value jumps to the reading.
     */
    private fun stepSeconds(sampleNanos: Long): Float {
        if (sampleNanos <= 0L)
            return NOMINAL_STEP_SECONDS

        val previous = lastSampleNanos
        lastSampleNanos = sampleNanos
        if (previous <= 0L || sampleNanos <= previous)
            return NOMINAL_STEP_SECONDS

        val step = (sampleNanos - previous) / 1_000_000_000f
        return step.coerceIn(MIN_STEP_SECONDS, MAX_STEP_SECONDS)
    }

    /**
     * One low-pass step that means the same thing at any sample rate.
     *
     * It is the exact fraction of the way to the new sample that a [TIME_CONSTANT]
     * second filter covers in [step] seconds, so several short steps and one long
     * one that cover the same time leave the filter in the same place.
     */
    private fun smooth(previous: Float, sample: Float, step: Float): Float {
        val share = 1f - exp(-step / TIME_CONSTANT_SECONDS)
        return previous + (sample - previous) * share
    }

    private fun shaped(rate: Float): Float {
        val magnitude = abs(rate)
        if (magnitude <= DEAD_ZONE) return 0f

        val travel = ((magnitude - DEAD_ZONE) / (FULL_TRAVEL - DEAD_ZONE)).coerceIn(0f, 1f)
        return (sign(rate) * travel.pow(GAMMA)).coerceIn(-1f, 1f)
    }

    companion object {
        /** Turn rate, in radians per second, that is treated as staying still. */
        const val DEAD_ZONE = 0.06f

        /** Turn rate, in radians per second, that means full deflection. */
        const val FULL_TRAVEL = 1.6f

        /** Below one lifts the slow, small turns that aiming is made of. */
        const val GAMMA = 0.8f

        /**
         * How long the filter takes to cover about two thirds of the way to a new
         * reading, in seconds.
         *
         * This is the share of each sample the filter used to apply, expressed as
         * a length of time: at the 50 Hz a game-rate sensor asks for it works out
         * to the same smoothing as before, and it holds at any other rate.
         */
        const val TIME_CONSTANT_SECONDS = 0.055f

        /** The sample rate the filter falls back to when it is given no timing. */
        const val NOMINAL_STEP_SECONDS = 0.02f

        /** A step shorter than this is treated as noise in the timestamps. */
        const val MIN_STEP_SECONDS = 0.002f

        /** A step longer than this is a stalled sensor, not a slow one. */
        const val MAX_STEP_SECONDS = 0.05f
    }
}
