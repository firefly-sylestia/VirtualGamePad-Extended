package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GameButtons
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reading the transport sends carries the live `ButtonsDown` mask, so a press
 * whose release cannot be found stays pressed on the PC until the app restarts.
 * These checks pin down that a release is always found, including after the state
 * itself has been written to.
 *
 * They also pin down the opposite fault: the gamepad screen sends snapshots on a
 * timer, so a tap that starts and ends between two of them used to be invisible to
 * the PC - it buzzed and did nothing. A press is therefore held until a packet has
 * carried it, which is what [send] models here: the gamepad screen calls
 * [GamepadButtonDispatcher.afterSend] once a packet has gone out.
 */
class GamepadButtonDispatcherTest {

    private val a = GameButtons.A.value
    private val b = GameButtons.B.value
    private val dpadUp = GameButtons.DPadUp.value

    /** Stands in for the send loop having handed a packet to the network layer. */
    private fun send(state: GamepadReading) = GamepadButtonDispatcher.afterSend(state)

    @Test
    fun `releasing a button clears its bit and raises the up edge`() {
        val state = GamepadReading()

        GamepadButtonDispatcher.press(state, "face_A", a)
        assertEquals(a, state.ButtonsDown)
        send(state)

        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals(0, state.ButtonsDown)
        assertEquals(a, state.ButtonsUp)

        // The edge only lives for the packet that carries it.
        send(state)
        assertEquals(0, state.ButtonsUp)
    }

    @Test
    fun `a tap shorter than one send interval still sends a press`() {
        // The reported fault: press and release both happen before the loop ever
        // samples the state. The PC must still see a press before the release, or
        // the button buzzes and nothing happens in the game.
        val state = GamepadReading()

        GamepadButtonDispatcher.press(state, "face_A", a)
        GamepadButtonDispatcher.release(state, "face_A")

        assertEquals("the press is held back for the next packet", a, state.ButtonsDown)
        assertEquals("no release edge before the press went out", 0, state.ButtonsUp)

        // That packet carries the press...
        send(state)
        assertEquals("released now the press has gone out", 0, state.ButtonsDown)
        assertEquals(a, state.ButtonsUp)

        // ...and the next one carries the release.
        send(state)
        assertEquals(0, state.ButtonsUp)
    }

    @Test
    fun `tapping again before the press packet goes out keeps the button down`() {
        val state = GamepadReading()

        GamepadButtonDispatcher.press(state, "face_A", a)
        GamepadButtonDispatcher.release(state, "face_A")
        GamepadButtonDispatcher.press(state, "face_A", a)

        assertEquals(a, state.ButtonsDown)

        send(state)
        // Still held by the second tap, so the queued release must be dropped.
        assertEquals(a, state.ButtonsDown)
        assertEquals(0, state.ButtonsUp)

        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals(0, state.ButtonsDown)
        assertEquals(a, state.ButtonsUp)
    }

    @Test
    fun `a release is still found after the state moved on`() {
        // The state hashes on every mutable field, so the entry that tracks the
        // press must not go looking for it by value.
        val state = GamepadReading()
        GamepadButtonDispatcher.press(state, "face_A", a)
        send(state)

        state.LeftTrigger = 0.5f
        state.RightTrigger = 1f
        state.LeftThumbstickX = 0.25f
        state.RightThumbstickY = -0.5f

        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals(0, state.ButtonsDown)
    }

    @Test
    fun `pressing and releasing repeatedly leaves nothing down`() {
        val state = GamepadReading()

        repeat(5) {
            GamepadButtonDispatcher.press(state, "face_A", a)
            GamepadButtonDispatcher.press(state, "dpad_up", dpadUp)
            assertEquals(a or dpadUp, state.ButtonsDown)
            send(state)

            GamepadButtonDispatcher.release(state, "face_A")
            GamepadButtonDispatcher.release(state, "dpad_up")
            assertEquals(0, state.ButtonsDown)
            send(state)
        }
    }

    @Test
    fun `two sources on one output only release it when the last one lets go`() {
        val state = GamepadReading()
        GamepadButtonDispatcher.press(state, "face_A", a)
        GamepadButtonDispatcher.press(state, "macro_0_0", a)
        send(state)

        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals("the macro still holds A", a, state.ButtonsDown)

        GamepadButtonDispatcher.release(state, "macro_0_0")
        assertEquals(0, state.ButtonsDown)
        assertEquals(a, state.ButtonsUp)
    }

    @Test
    fun `pressing the same source twice does not count as two holders`() {
        val state = GamepadReading()
        GamepadButtonDispatcher.press(state, "face_A", a)
        GamepadButtonDispatcher.press(state, "face_A", a)
        send(state)

        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals(0, state.ButtonsDown)
    }

    @Test
    fun `releasing a source that was never pressed does nothing`() {
        val state = GamepadReading()
        GamepadButtonDispatcher.press(state, "face_A", a)

        GamepadButtonDispatcher.release(state, "face_B")
        assertEquals(a, state.ButtonsDown)
        assertEquals(0, state.ButtonsUp)
    }

    @Test
    fun `releaseAll clears everything held and forgets the gamepad`() {
        val state = GamepadReading()
        GamepadButtonDispatcher.press(state, "face_A", a)
        GamepadButtonDispatcher.press(state, "face_B", b)
        send(state)

        GamepadButtonDispatcher.releaseAll(state)
        assertEquals(0, state.ButtonsDown)
        assertEquals(a or b, state.ButtonsUp)

        // The counters went with it too, so a fresh press starts from zero again.
        GamepadButtonDispatcher.press(state, "face_A", a)
        assertEquals(a, state.ButtonsDown)
        send(state)
        GamepadButtonDispatcher.release(state, "face_A")
        assertEquals(0, state.ButtonsDown)
    }

    @Test
    fun `each gamepad keeps its own bookkeeping`() {
        val phone = GamepadReading()
        val customiser = GamepadReading()

        GamepadButtonDispatcher.press(phone, "face_A", a)
        GamepadButtonDispatcher.press(customiser, "face_A", a)

        GamepadButtonDispatcher.releaseAll(phone)
        assertEquals(0, phone.ButtonsDown)
        assertTrue("the other gamepad is untouched", customiser.ButtonsDown != 0)
        assertEquals(0, customiser.ButtonsUp)

        GamepadButtonDispatcher.releaseAll(customiser)
        assertEquals(0, customiser.ButtonsDown)
    }
}
