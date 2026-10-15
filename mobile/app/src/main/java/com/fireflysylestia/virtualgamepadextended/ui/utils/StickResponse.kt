package com.fireflysylestia.virtualgamepadextended.ui.utils

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Shapes a raw stick position into the value the PC acts on.
 *
 * A touchstick under a finger is not a potentiometer: the fingertip never sits
 * exactly on the centre, so a small amount of drift reaches the host as a
 * non-zero axis. Sent raw, that drift creeps the character or drags the camera,
 * and there is no way to hold a direction partially. Two things are applied:
 *
 * - A **radial dead zone**: below [DEAD_ZONE] of full travel the stick reports
 *   exactly zero, so a resting finger is silent. It is measured on the distance
 *   from the centre, not per axis, so pushing diagonally does not produce a
 *   snapped cross shape.
 * - A **response curve**: the remaining travel is rescaled so full deflection
 *   still reaches 1.0, then raised to [GAMMA]. A gamma below one lifts the
 *   middle of the range, which is where a touchstick spends most of its time,
 *   so small movements move the character instead of crawling. Diagonal
 *   magnitude is preserved (this is not a per-axis curve), so a push at 45
 *   degrees still reads as one direction at the right strength.
 *
 * `A = 1` therefore maps to `1`, and only the region between the dead zone and
 * the edge is reshaped. This runs on the phone, before the value is sent, so
 * both the native gamepad path and the keyboard/mouse path see the same shaped
 * input.
 */
object StickResponse {

    /** Distance from centre, as a fraction of full travel, that reports zero. */
    const val DEAD_ZONE = 0.12f

    /**
     * Exponent applied to the travel left after the dead zone. Below 1 lifts the
     * middle of the range; 1 is a straight line.
     */
    const val GAMMA = 0.65f

    /**
     * @param rawX horizontal stick position, -1..1, measured from the centre
     * @param rawY vertical stick position, -1..1, measured from the centre
     * @return the shaped `(x, y)`, zero inside the dead zone and unchanged in
     *         direction. Inputs beyond full travel are clamped to it.
     */
    fun apply(rawX: Float, rawY: Float): Pair<Float, Float> {
        val radius = sqrt(rawX * rawX + rawY * rawY)
        if (radius <= DEAD_ZONE) return 0f to 0f

        // Rescale what is left of the travel so the edge still reaches exactly 1.
        val travel = (radius - DEAD_ZONE) / (1f - DEAD_ZONE)
        val shaped = travel.coerceIn(0f, 1f).pow(GAMMA)

        // Scale the vector rather than each axis, which keeps the direction and
        // the diagonal magnitude intact.
        val scale = shaped / radius
        return rawX * scale to rawY * scale
    }
}
