package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import java.util.IdentityHashMap

/**
 * Tracks logical button sources so remapped controls that share one output do not
 * release that output while another source is still holding it.
 *
 * Each on-screen control is a [source] and each output it drives is a bit of the
 * reading's `ButtonsDown` mask. Several sources can drive the same bit (a remap, a
 * macro step and the stick click on `A`, say), so every bit is counted and only
 * cleared when its last holder lets go, which also sets the `ButtonsUp` edge the
 * PC-side keymap executor listens for.
 *
 * ## Why the bookkeeping is keyed by identity
 *
 * [GamepadReading]'s generated `equals`/`hashCode` are derived from its mutable
 * fields. Pressing a button writes `ButtonsDown`, which changes the very hash a
 * `WeakHashMap` would use to find that state's entry, so the entry is stranded in
 * its old bucket and the release that follows finds nothing: the press sticks down
 * on the PC until the app is restarted. Identity never changes, so an
 * [IdentityHashMap] finds the entry no matter what the state contains.
 *
 * Its entry is dropped in [releaseAll], which every gamepad calls when it leaves
 * the screen, so a screen that is left and entered again starts from an empty slate.
 */
object GamepadButtonDispatcher {

    private class Held {
        /** Output bit each source currently holds. */
        val sources = mutableMapOf<String, Int>()

        /** How many sources hold each output bit. */
        val pressCounts = mutableMapOf<Int, Int>()
    }

    private val held = IdentityHashMap<GamepadReading, Held>()

    @Synchronized
    fun press(state: GamepadReading, source: String, mask: Int) {
        if (mask == 0) return
        val bookkeeping = held[state] ?: Held().also { held[state] = it }

        val previous = bookkeeping.sources[source]
        if (previous == mask) return
        // One source drives one output at a time, so a remap mid-press swaps them.
        if (previous != null) release(state, source)

        bookkeeping.sources[source] = mask
        val count = bookkeeping.pressCounts[mask] ?: 0
        if (count == 0) state.ButtonsDown = state.ButtonsDown or mask
        bookkeeping.pressCounts[mask] = count + 1
    }

    @Synchronized
    fun release(state: GamepadReading, source: String) {
        val bookkeeping = held[state] ?: return
        val mask = bookkeeping.sources.remove(source) ?: return

        val count = bookkeeping.pressCounts[mask] ?: return
        if (count <= 1) {
            bookkeeping.pressCounts.remove(mask)
            state.ButtonsDown = state.ButtonsDown and mask.inv()
            state.ButtonsUp = state.ButtonsUp or mask
        } else {
            bookkeeping.pressCounts[mask] = count - 1
        }
    }

    /**
     * Releasing everything [state] holds, for a gamepad that is leaving the screen:
     * the finger cannot be lifted for the caller, so nothing may be left down.
     */
    @Synchronized
    fun releaseAll(state: GamepadReading) {
        val bookkeeping = held.remove(state) ?: return
        val downMask = bookkeeping.sources.values.fold(0) { heldBits, mask -> heldBits or mask }
        if (downMask == 0) return
        state.ButtonsDown = state.ButtonsDown and downMask.inv()
        state.ButtonsUp = state.ButtonsUp or downMask
    }
}
