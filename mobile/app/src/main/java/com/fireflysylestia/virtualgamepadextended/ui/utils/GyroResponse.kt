package com.fireflysylestia.virtualgamepadextended.ui.utils

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sign

/**
 * Shapes the phone's tilt into the right-stick axes.
 *
 * This used to be fed angular velocity from the gyroscope, which made the phone a
 * rate control: holding a tilt kept turning the camera for as long as it was held,
 * so the view spun past 360 degrees and never settled. What a player expects from
 * a stick is a position, not a rate: tilt the phone and the camera holds that
 * offset, bring it back level and the camera centres. So the input is now an angle
 * measured against the pose the controller was switched on in, and this class only
 * has to make that angle feel like a stick:
 *
 * - **Filtering.** Each sample is mixed into the previous one by elapsed time, so
 *   the hand's tremor and the sensor's noise do not reach the camera as a shake.
 * - **A dead zone.** Nobody holds a phone perfectly still, and a couple of degrees
 *   of drift should not move the view.
 * - **A curve.** Past the dead zone the remaining travel is rescaled so full
 *   deflection is still reachable, then raised to [GAMMA], which lifts the small
 *   tilts that aiming is actually made of.
 *
 * Angles are in degrees and are signed deltas from the neutral pose, so this class
 * neither knows nor cares which way round the phone is being held.
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
     * @param rollDegrees tilt to the left or right, in degrees, from the neutral pose
     * @param pitchDegrees tilt up or down, in degrees, from the neutral pose
     * @param sampleNanos when the reading was taken, as `SensorEvent.timestamp`
     *        reports it; 0 when the caller has no timestamp, which then uses the
     *        rate the sensor was asked for
     * @return the right-stick value to send, each axis in -1..1
     */
    fun apply(rollDegrees: Float, pitchDegrees: Float, sampleNanos: Long = 0L): Pair<Float, Float> {
        val step = stepSeconds(sampleNanos)

        filteredX = smooth(filteredX, rollDegrees, step)
        filteredY = smooth(filteredY, pitchDegrees, step)
        return shaped(filteredX) to shaped(filteredY)
    }

    /**
     * How long the sample took, in seconds.
     *
     * The first sample has nothing to measure against, so it counts as one
     * [NOMINAL_STEP_SECONDS]; a gap outside [MIN_STEP_SECONDS]..[MAX_STEP_SECONDS]
     * is brought back into it, which is what a sensor that stalls and then
     * delivers everything at once would otherwise throw at the filter.
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

    private fun shaped(degrees: Float): Float {
        val magnitude = abs(degrees)
        if (magnitude <= DEAD_ZONE_DEGREES) return 0f

        val travel = ((magnitude - DEAD_ZONE_DEGREES) /
                (FULL_TRAVEL_DEGREES - DEAD_ZONE_DEGREES)).coerceIn(0f, 1f)
        return (sign(degrees) * travel.pow(GAMMA)).coerceIn(-1f, 1f)
    }

    companion object {
        /** Tilt, in degrees, that is treated as still holding the phone level. */
        const val DEAD_ZONE_DEGREES = 2.5f

        /** Tilt, in degrees, that means full stick deflection. */
        const val FULL_TRAVEL_DEGREES = 28f

        /** Below one lifts the small tilts that aiming is made of. */
        const val GAMMA = 0.8f

        /**
         * How long the filter takes to cover about two thirds of the way to a new
         * reading, in seconds. Long enough to swallow hand tremor, short enough
         * that the camera does not lag behind a deliberate movement.
         */
        const val TIME_CONSTANT_SECONDS = 0.045f

        /** The sample rate the filter falls back to when it is given no timing. */
        const val NOMINAL_STEP_SECONDS = 0.02f

        /** A step shorter than this is treated as noise in the timestamps. */
        const val MIN_STEP_SECONDS = 0.002f

        /** A step longer than this is a stalled sensor, not a slow one. */
        const val MAX_STEP_SECONDS = 0.05f
    }
}
