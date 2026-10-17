package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The HOLD button keeps whatever is down at the tap pressed. These checks pin
 * down that a release happening afterwards cannot clear it, and that tapping
 * HOLD again really does let go.
 */
class InputHoldTest {

    private val a = GameButtons.A.value

    /** Stands in for a control being released, which zeroes its own values. */
    private fun releaseEverything(state: GamepadReading) {
        state.ButtonsDown = 0
        state.ButtonsUp = 0
        state.LeftThumbstickX = 0f
        state.LeftThumbstickY = 0f
        state.RightThumbstickX = 0f
        state.RightThumbstickY = 0f
        state.LeftTrigger = 0f
        state.RightTrigger = 0f
    }

    @Test
    fun `a latched stick stays down after the finger is lifted`() {
        val state = GamepadReading()
        state.LeftThumbstickX = 0.4f
        state.LeftThumbstickY = -1f

        val hold = InputHold()
        hold.latch(state)
        releaseEverything(state)
        hold.applyTo(state)

        assertEquals(0.4f, state.LeftThumbstickX, 0.0001f)
        assertEquals(-1f, state.LeftThumbstickY, 0.0001f)
    }

    @Test
    fun `a latched button stays down and its release edge is dropped`() {
        val state = GamepadReading()
        state.ButtonsDown = a

        val hold = InputHold()
        hold.latch(state)

        // The control released the button while the hold was armed.
        state.ButtonsDown = 0
        state.ButtonsUp = a

        hold.applyTo(state)

        assertEquals(a, state.ButtonsDown)
        assertEquals("a latched button must not also be reported released", 0, state.ButtonsUp)
    }

    @Test
    fun `unlatching lets a released control stay released`() {
        val state = GamepadReading()
        state.RightTrigger = 0.8f

        val hold = InputHold()
        hold.latch(state)
        releaseEverything(state)
        hold.applyTo(state)
        assertEquals(0.8f, state.RightTrigger, 0.0001f)

        hold.toggle(state)
        assertFalse(hold.isLatched)
        hold.applyTo(state)

        assertEquals(0f, state.RightTrigger, 0.0001f)
        assertEquals(0, state.ButtonsDown)
    }

    @Test
    fun `inputs pressed after arming are not latched by accident`() {
        val state = GamepadReading()
        state.ButtonsDown = a

        val hold = InputHold()
        hold.latch(state)

        state.ButtonsDown = a or GameButtons.B.value
        hold.applyTo(state)
        assertEquals(a or GameButtons.B.value, state.ButtonsDown)

        // B was not down at the tap, so letting it go lets it stay up.
        state.ButtonsDown = a
        hold.applyTo(state)
        assertEquals(a, state.ButtonsDown)
    }

    @Test
    fun `arming with nothing held is harmless and still toggles`() {
        val state = GamepadReading()
        val hold = InputHold()

        hold.toggle(state)
        assertTrue(hold.isLatched)
        hold.applyTo(state)
        assertEquals(0, state.ButtonsDown)

        hold.toggle(state)
        assertFalse(hold.isLatched)
    }

    @Test
    fun `clear forgets the snapshot`() {
        val state = GamepadReading()
        state.LeftThumbstickY = 1f

        val hold = InputHold()
        hold.latch(state)
        hold.clear()
        releaseEverything(state)
        hold.applyTo(state)

        assertEquals(0f, state.LeftThumbstickY, 0.0001f)
    }
}
