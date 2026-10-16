package com.fireflysylestia.virtualgamepadextended.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gyro aiming feeds the right-stick axes, so what happens here is what the
 * camera does. These checks pin down the three things that were wrong with the
 * raw reading: it crept while the phone was still, it shook with the sensor's
 * noise, and it went from nothing to everything in one jump.
 */
class GyroResponseTest {

    @Test
    fun `a still phone reports exactly zero`() {
        val gyro = GyroResponse()

        assertEquals(0f to 0f, gyro.apply(0f, 0f))
        assertEquals(0f to 0f, gyro.apply(0.05f, -0.05f))

        // A resting bias is constant, not a first sample, so it must not creep in
        // either: the filter settles onto it and it is still inside the dead zone.
        repeat(200) { gyro.apply(0.05f, -0.05f) }
        assertEquals(0f to 0f, gyro.apply(0.05f, -0.05f))
    }

    @Test
    fun `a hard turn reaches full deflection`() {
        val gyro = GyroResponse()

        // The filter needs a few samples of the same turn to catch up.
        var last = 0f to 0f
        repeat(200) { last = gyro.apply(3f, -3f) }

        assertEquals(1f, last.first, 1e-6f)
        assertEquals(-1f, last.second, 1e-6f)
    }

    @Test
    fun `the first sample does not jump to full speed`() {
        // One noisy sample used to be sent as-is, so a single bad reading threw
        // the camera to the edge of its travel. The filter takes part of it.
        val gyro = GyroResponse()
        val (x, _) = gyro.apply(4f, 0f)

        assertTrue("a single spike reported $x, expected less than full travel", x < 1f)
        assertTrue("a turn should still register, got $x", x > 0f)
    }

    @Test
    fun `holding a turn keeps closing in on the reported value`() {
        val gyro = GyroResponse()

        var previous = 0f
        repeat(30) {
            val (x, _) = gyro.apply(1f, 0f)
            assertTrue("the reading fell back from $previous to $x", x >= previous)
            previous = x
        }
    }

    @Test
    fun `turning the other way is reported the other way`() {
        val gyro = GyroResponse()

        var last = 0f to 0f
        repeat(50) { last = gyro.apply(-2f, 2f) }

        assertTrue("expected a negative reading, got ${last.first}", last.first < 0f)
        assertTrue("expected a positive reading, got ${last.second}", last.second > 0f)
    }

    @Test
    fun `a forgotten reading leaves nothing behind`() {
        val gyro = GyroResponse()
        repeat(50) { gyro.apply(3f, 3f) }

        gyro.reset()

        assertEquals(0f to 0f, gyro.apply(0f, 0f))
        assertEquals(0f to 0f, gyro.apply(0.02f, 0.02f))
    }

    @Test
    fun `the smoothing follows the clock, not the number of samples`() {
        // The same 40 ms of the same turn, delivered as two readings by one
        // sensor and as five by a faster one, has to end up in the same place.
        // The old filter mixed a fixed share of every sample, so a sensor that
        // reported twice as often smoothed each reading half as much and the
        // shake it was there to remove came back on exactly those phones.
        val slow = GyroResponse()
        var onSlow = slow.apply(1.5f, 0f, 1_000_000_000L)
        onSlow = slow.apply(1.5f, 0f, 1_020_000_000L)
        onSlow = slow.apply(1.5f, 0f, 1_040_000_000L)

        val fast = GyroResponse()
        var onFast = fast.apply(1.5f, 0f, 1_000_000_000L)
        for (step in 1..8)
            onFast = fast.apply(1.5f, 0f, 1_000_000_000L + step * 5_000_000L)

        assertEquals(onSlow.first, onFast.first, 1e-4f)
    }

    @Test
    fun `a stalled sensor is treated as a slow one, not a jump`() {
        // Timestamps do not arrive after a pause in the app, and a sensor can hand
        // over a reading from seconds ago. Either way the filter must not decide
        // that all that time passed and send the reading straight to the edge.
        fun afterGap(gapNanos: Long): Float {
            val gyro = GyroResponse()
            gyro.apply(2f, 0f, 1_000_000_000L)
            return gyro.apply(2f, 0f, 1_000_000_000L + gapNanos).first
        }

        // A tenth of a second is already the longest step the filter believes, so
        // ten seconds has to leave it in exactly the same place.
        assertEquals(afterGap(100_000_000L), afterGap(10_000_000_000L), 1e-6f)
        assertTrue("a gap threw the reading to the edge", afterGap(10_000_000_000L) < 1f)
    }

    @Test
    fun `a timestamp that goes backwards is ignored`() {
        val gyro = GyroResponse()
        gyro.apply(1.5f, 0f, 1_000_000_000L)

        // A negative step would work out to a share of the sample outside 0..1.
        val backwards = gyro.apply(1.5f, 0f, 500_000_000L)
        val forwards = GyroResponse().let {
            it.apply(1.5f, 0f, 1_000_000_000L)
            it.apply(1.5f, 0f, 1_020_000_000L)
        }

        assertEquals(forwards.first, backwards.first, 1e-6f)
    }
}
