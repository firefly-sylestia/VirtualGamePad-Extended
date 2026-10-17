package com.fireflysylestia.virtualgamepadextended.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tilt aiming drives the right-stick axes, so what happens here is what the camera
 * does. This used to be fed a turn rate, which made the stick a rate control: hold
 * a tilt and it kept turning, so the view spun past 360 degrees and never settled.
 * It is now fed the tilt itself, in degrees from the pose the controller was
 * switched on in, and these checks pin down what a stick should do: sit still when
 * the phone is level, hold an offset while it is leaned, and come back to centre
 * when it is level again.
 */
class GyroResponseTest {

    @Test
    fun `a level phone reports exactly zero`() {
        val gyro = GyroResponse()

        assertEquals(0f to 0f, gyro.apply(0f, 0f))

        // Small tilts are the hand's own drift, so they must stay inside the dead
        // zone however long they are held - this is the creep that made the camera
        // wander on its own.
        repeat(200) {
            assertEquals(0f to 0f, gyro.apply(2f, -2f))
        }
    }

    @Test
    fun `a hard tilt reaches full deflection`() {
        val gyro = GyroResponse()

        // The filter needs a few samples of the same tilt to catch up.
        var last = 0f to 0f
        repeat(200) { last = gyro.apply(30f, -30f) }

        assertEquals(1f, last.first, 1e-6f)
        assertEquals(-1f, last.second, 1e-6f)
    }

    @Test
    fun `a tilt past full travel stays clamped`() {
        val gyro = GyroResponse()

        var last = 0f to 0f
        repeat(200) { last = gyro.apply(120f, -120f) }

        assertEquals("a lean past the end of the travel must not overshoot", 1f, last.first, 1e-6f)
        assertEquals(-1f, last.second, 1e-6f)
    }

    @Test
    fun `the first sample does not jump to full deflection`() {
        // One sample used to be sent as-is, so a single bad reading threw the
        // camera to the edge of its travel. The filter takes part of it.
        val gyro = GyroResponse()
        val (x, _) = gyro.apply(30f, 0f)

        assertTrue("a single sample reported $x, expected less than full travel", x < 1f)
        assertTrue("a tilt should still register, got $x", x > 0f)
    }

    @Test
    fun `holding a tilt keeps closing in on the reported value`() {
        val gyro = GyroResponse()

        var previous = 0f
        repeat(30) {
            val (x, _) = gyro.apply(10f, 0f)
            assertTrue("the reading fell back from $previous to $x", x >= previous)
            previous = x
        }
        assertTrue("a held tilt should settle above the dead zone, got $previous", previous > 0f)
    }

    @Test
    fun `returning the phone to level centres the stick`() {
        val gyro = GyroResponse()

        repeat(200) { gyro.apply(20f, -20f) }

        var last = 0f to 0f
        repeat(60) { last = gyro.apply(0f, 0f) }

        // Not just "moves towards zero": a stick that is back level has to report
        // exactly zero, or the camera drifts for as long as the toggle is on.
        assertEquals(0f to 0f, last)
    }

    @Test
    fun `a tilt left mirrors a tilt right`() {
        val right = GyroResponse()
        val left = GyroResponse()

        var rightValue = 0f
        var leftValue = 0f
        repeat(200) {
            rightValue = right.apply(20f, 0f).first
            leftValue = left.apply(-20f, 0f).first
        }

        assertEquals(rightValue, -leftValue, 1e-6f)
    }

    @Test
    fun `the same tilt settles to the same value at any sample rate`() {
        // The filter mixes by elapsed time, not by sample count, so a device that
        // reports twice as often must not feel twice as sharp.
        val slow = GyroResponse()
        val fast = GyroResponse()

        var slowValue = 0f
        var fastValue = 0f

        // 0.4 seconds of the same tilt, reported every 20 ms and every 5 ms.
        for (i in 1..20) {
            slowValue = slow.apply(15f, 0f, i * 20_000_000L).first
        }
        for (i in 1..80) {
            fastValue = fast.apply(15f, 0f, i * 5_000_000L).first
        }

        assertEquals(slowValue, fastValue, 0.02f)
    }
}
