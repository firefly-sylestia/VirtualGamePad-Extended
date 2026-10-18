package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A control bound to a key sends that key instead of its button, and a bound
 * pair is a chord: both names go down in the picked order inside one packet and
 * come off together. The chord has to survive the same press/release latching a
 * single key does, or a quick tap would reach the PC as a release the game
 * never saw pressed.
 */
class ControlPressTest {

    @After
    fun releaseGlobalBindings() {
        // keyBindings is a global the pad fills from settings; a test must not
        // leak its own into the next one.
        ControlPress.keyBindings = emptyMap()
    }

    @Test
    fun `a control without a binding sends its gamepad button`() {
        val state = GamepadReading()

        ControlPress.press(state, "face_a_source", "face_a", GameButtons.A.value)
        assertEquals(GameButtons.A.value, state.ButtonsDown)
        assertTrue(state.KeysDown.isEmpty())

        ControlPress.release(state, "face_a_source")
        assertEquals(0, state.ButtonsDown)
    }

    @Test
    fun `a bound single key replaces the button and latches until sent`() {
        val state = GamepadReading()
        ControlPress.keyBindings = mapOf("face_x" to "T")

        ControlPress.press(state, "src", "face_x", GameButtons.X.value)
        assertEquals(listOf("T"), state.KeysDown.toList())
        assertEquals("the key replaces the button", 0, state.ButtonsDown)

        // The finger lifts before any packet carried the press: the press stays
        // until one goes out, and the release follows the packet after it.
        ControlPress.release(state, "src")
        assertEquals(listOf("T"), state.KeysDown.toList())
        KeyButtonDispatcher.afterSend(state)
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `a chord goes down in the picked order and comes off together`() {
        val state = GamepadReading()
        ControlPress.keyBindings = mapOf("face_a" to "LeftShift+C")

        ControlPress.press(state, "src", "face_a", GameButtons.A.value)
        assertEquals(
            "the picked order, first key first",
            listOf("LeftShift", "C"),
            state.KeysDown.toList(),
        )

        // The finger lifts before any packet carried the press: both names stay
        // down until one goes out, and the packet after it carries both releases.
        ControlPress.release(state, "src")
        assertEquals("both names still latched for one packet", 2, state.KeysDown.size)
        KeyButtonDispatcher.afterSend(state)
        assertTrue("both keys let go together", state.KeysDown.isEmpty())
    }

    @Test
    fun `releasing still finds the pair when the binding was cleared mid-press`() {
        val state = GamepadReading()
        ControlPress.keyBindings = mapOf("face_a" to "LeftShift+C")

        ControlPress.press(state, "src", "face_a", GameButtons.A.value)
        KeyButtonDispatcher.afterSend(state)

        ControlPress.keyBindings = emptyMap()
        ControlPress.release(state, "src")
        KeyButtonDispatcher.afterSend(state)
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `two controls on the same chord keep it down until the last lets go`() {
        val state = GamepadReading()
        ControlPress.keyBindings = mapOf("face_a" to "LeftShift+C", "face_b" to "LeftShift+C")

        ControlPress.press(state, "src_a", "face_a", GameButtons.A.value)
        ControlPress.press(state, "src_b", "face_b", GameButtons.B.value)
        KeyButtonDispatcher.afterSend(state)

        ControlPress.release(state, "src_a")
        assertEquals(
            "the other control still holds its half",
            listOf("LeftShift", "C"),
            state.KeysDown.toList(),
        )

        ControlPress.release(state, "src_b")
        KeyButtonDispatcher.afterSend(state)
        assertTrue(state.KeysDown.isEmpty())
    }
}
