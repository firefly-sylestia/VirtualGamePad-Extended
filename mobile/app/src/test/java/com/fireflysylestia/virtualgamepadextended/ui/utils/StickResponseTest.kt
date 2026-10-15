package com.fireflysylestia.virtualgamepadextended.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * The stick value goes straight to the PC, so the shape applied here is the
 * control's feel. These checks pin down the parts that decide it: a resting
 * finger is silent, the travel that is left is not thrown away, and full
 * deflection still reaches the edge.
 */
class StickResponseTest {

    private fun radius(x: Float, y: Float) = sqrt(x * x + y * y)

    @Test
    fun `a finger resting inside the dead zone reports exactly zero`() {
        assertEquals(0f to 0f, StickResponse.apply(0f, 0f))
        assertEquals(0f to 0f, StickResponse.apply(0.01f, -0.02f))

        // Exactly on the boundary is still inside it.
        assertEquals(0f to 0f, StickResponse.apply(StickResponse.DEAD_ZONE, 0f))
        assertEquals(0f to 0f, StickResponse.apply(0f, -(StickResponse.DEAD_ZONE * 0.99f)))
    }

    @Test
    fun `just past the dead zone starts reporting again`() {
        val (x, y) = StickResponse.apply(StickResponse.DEAD_ZONE + 0.01f, 0f)

        assertTrue("expected a non-zero reading past the dead zone", x > 0f)
        assertEquals(0f, y, 1e-6f)
    }

    @Test
    fun `full deflection still reaches the edge`() {
        assertEquals(1f, StickResponse.apply(1f, 0f).first, 1e-6f)

        val (_, downY) = StickResponse.apply(0f, -1f)
        assertEquals(-1f, downY, 1e-6f)

        // Past full travel it is clamped rather than reported out of range.
        val (clampedX, clampedY) = StickResponse.apply(2f, 0f)
        assertEquals(1f, clampedX, 1e-6f)
        assertEquals(0f, clampedY, 1e-6f)
    }

    @Test
    fun `the middle of the travel is lifted`() {
        // This is the "the character crawls unless the stick is buried" fix: at
        // half deflection the reported value must be past half, not behind it.
        val (x, _) = StickResponse.apply(0.5f, 0f)

        assertTrue("half deflection reported $x, expected more than 0.5", x > 0.5f)
        assertTrue("half deflection reported $x, expected to stay in range", x <= 1f)
    }

    @Test
    fun `the response never falls as the stick is pushed further`() {
        var previous = 0f
        var at = StickResponse.DEAD_ZONE
        while (at <= 1f) {
            val (x, y) = StickResponse.apply(at, 0f)
            val reported = radius(x, y)

            assertTrue("radius fell between pushes: $previous then $reported", reported >= previous)
            previous = reported
            at += 0.01f
        }
    }

    @Test
    fun `the curve is radial, so a diagonal push is not reshaped per axis`() {
        // A push at 45 degrees and a push along one axis of the same length must
        // come out the same length, or diagonals would be pulled off the ring.
        val alongAxis = StickResponse.apply(0.5f, 0f)
        val diagonal = StickResponse.apply(0.3535534f, 0.3535534f)

        assertEquals(
            radius(alongAxis.first, alongAxis.second),
            radius(diagonal.first, diagonal.second),
            1e-4f,
        )
        assertEquals(diagonal.first, diagonal.second, 1e-6f)
    }

    @Test
    fun `direction is preserved`() {
        val (x, y) = StickResponse.apply(-0.4f, 0.4f)

        assertEquals(-x, y, 1e-6f)
        assertTrue(x < 0f)
    }
}
