package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The custom-key buttons reach the PC as an array of key names on the reading, so
 * these checks pin down the two faults the names have to survive:
 *
 * - a release must never arrive for a key the game never saw pressed, and
 * - a tap shorter than one send interval must still arrive as a press.
 */
class KeyButtonDispatcherTest {

    /** Stands in for the send loop having handed a packet to the network layer. */
    private fun send(state: GamepadReading) = KeyButtonDispatcher.afterSend(state)

    @Test
    fun `a held key is in the array and a sent release takes it out`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_0", "T")
        assertEquals(listOf("T"), state.KeysDown.toList())
        send(state)

        KeyButtonDispatcher.release(state, "custom_key_0")
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `a tap shorter than one send interval still sends the press`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_1", "E")
        KeyButtonDispatcher.release(state, "custom_key_1")

        assertEquals("the press is held back for the next packet", listOf("E"), state.KeysDown.toList())

        send(state)
        assertTrue("the release follows the press it was waiting for", state.KeysDown.isEmpty())
    }

    @Test
    fun `two buttons on the same key keep it down until the last one lets go`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_0", "T")
        KeyButtonDispatcher.press(state, "custom_key_1", "T")
        send(state)

        KeyButtonDispatcher.release(state, "custom_key_0")
        assertEquals(listOf("T"), state.KeysDown.toList())

        KeyButtonDispatcher.release(state, "custom_key_1")
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `an edited key swaps while it is held`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_2", "T")
        send(state)

        KeyButtonDispatcher.press(state, "custom_key_2", "Y")
        assertEquals(listOf("Y"), state.KeysDown.toList())
    }

    @Test
    fun `leaving the gamepad lets go of every key`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_0", "T")
        KeyButtonDispatcher.press(state, "custom_key_3", "F3")
        send(state)
        assertEquals(setOf("T", "F3"), state.KeysDown.toSet())

        KeyButtonDispatcher.releaseAll(state)
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `a blank key sends nothing`() {
        val state = GamepadReading()

        KeyButtonDispatcher.press(state, "custom_key_0", "   ")
        assertTrue(state.KeysDown.isEmpty())
    }

    @Test
    fun `the hold latch keeps a custom key down after the finger lifts`() {
        val state = GamepadReading()
        val hold = InputHold()

        KeyButtonDispatcher.press(state, "custom_key_0", "T")
        send(state)

        // The snapshot is taken with the key down, then the finger leaves it.
        hold.latch(state)
        KeyButtonDispatcher.release(state, "custom_key_0")
        hold.applyTo(state)
        send(state)
        assertEquals("the latch is still holding T", listOf("T"), state.KeysDown.toList())

        // Tapping HOLD again hands the key back to the finger, which is gone.
        hold.clear()
        hold.applyTo(state)
        send(state)
        assertTrue(state.KeysDown.isEmpty())
    }
}
