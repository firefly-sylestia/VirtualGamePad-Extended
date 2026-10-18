package com.fireflysylestia.virtualgamepadextended.data

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A recording is only as good as what it stores, so these pin down the two rules
 * the sampler depends on: a frame is kept when something moved, and a frame that
 * only drifted is not.
 */
class RecordedMacrosTest {

    @Test
    fun `a frame carries the whole reading, not just the buttons`() {
        val state = GamepadReading()
        state.ButtonsDown = GameButtons.A.value
        state.KeysDown = arrayOf("T")
        state.LeftTrigger = 0.5f
        state.RightThumbstickX = -0.25f

        val frame = frameOf(state, atMillis = 120)

        assertEquals(120, frame.atMillis)
        assertEquals(GameButtons.A.value, frame.buttons)
        assertEquals(listOf("T"), frame.keys)
        assertEquals(0.5f, frame.leftTrigger, 0.0001f)
        assertEquals(-0.25f, frame.rightX, 0.0001f)
    }

    @Test
    fun `a frame that only drifted is not worth keeping`() {
        val base = RecordedFrame(atMillis = 0, buttons = 1, leftX = 0.5f)

        assertFalse(RecordedFrame(atMillis = 33, buttons = 1, leftX = 0.5f).differsFrom(base))
        assertFalse(
            "a resting finger is not a sweep",
            RecordedFrame(atMillis = 33, buttons = 1, leftX = 0.505f).differsFrom(base),
        )

        assertTrue(RecordedFrame(atMillis = 33, buttons = 3, leftX = 0.5f).differsFrom(base))
        assertTrue(RecordedFrame(atMillis = 33, buttons = 1, keys = listOf("T")).differsFrom(base))
        assertTrue(RecordedFrame(atMillis = 33, buttons = 1, leftX = 0.8f).differsFrom(base))
    }

    @Test
    fun `a cleared key field means the control sends its gamepad action again`() {
        val stored = mapOf(
            "face_a" to " Space ",
            "face_x" to "",
            "dpad_up" to "   ",
            "plus" to "F1",
        )

        assertEquals(mapOf("face_a" to "Space", "plus" to "F1"), normaliseControlKeyBindings(stored))
        assertEquals(emptyMap<String, String>(), normaliseControlKeyBindings(emptyMap()))
    }

    @Test
    fun `a control id reads as a label`() {
        assertEquals("Face A", controlLabel("face_a"))
        assertEquals("Left Stick Click", controlLabel("left_stick_click"))
        assertEquals("Plus", controlLabel("plus"))
    }
}
