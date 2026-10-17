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
 *
 * ## Why a press is latched until a packet carries it
 *
 * The gamepad screen sends the reading on a timer and each packet is a snapshot,
 * so a tap shorter than one interval used to be invisible to the PC: the bit was
 * set and cleared again between two snapshots, and the packet that did go out
 * carried only a release edge. The PC drops a release for a button it never saw
 * pressed, which is what made a quick tap buzz and do nothing.
 *
 * A press therefore stays in `ButtonsDown` until [afterSend] has been told that a
 * packet carrying it has gone out; only then is the queued release applied, so
 * the PC always sees a press packet and a release packet in that order.
 */
object GamepadButtonDispatcher {

    private class Held {
        /** Output bit each source currently holds. */
        val sources = mutableMapOf<String, Int>()

        /** How many sources hold each output bit. */
        val pressCounts = mutableMapOf<Int, Int>()

        /** Bits pressed since the last packet was handed to the network layer. */
        val pendingPress = mutableSetOf<Int>()

        /** Bits released before their press packet went out, waiting their turn. */
        val pendingRelease = mutableSetOf<Int>()
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
        if (count == 0) {
            state.ButtonsDown = state.ButtonsDown or mask
            bookkeeping.pendingPress.add(mask)
            // Tapped again before the release went out: it is held once more, so
            // the queued release no longer describes this source.
            bookkeeping.pendingRelease.remove(mask)
        }
        bookkeeping.pressCounts[mask] = count + 1
    }

    @Synchronized
    fun release(state: GamepadReading, source: String) {
        val bookkeeping = held[state] ?: return
        val mask = bookkeeping.sources.remove(source) ?: return

        val count = bookkeeping.pressCounts[mask] ?: return
        if (count <= 1) {
            bookkeeping.pressCounts.remove(mask)
            if (bookkeeping.pendingPress.contains(mask)) {
                // The press has not been sent yet. Clearing the bit now would hand
                // the PC a release for a button it never saw pressed, so the bit
                // stays down until [afterSend] has flushed the press.
                bookkeeping.pendingRelease.add(mask)
            } else {
                state.ButtonsDown = state.ButtonsDown and mask.inv()
                state.ButtonsUp = state.ButtonsUp or mask
            }
        } else {
            bookkeeping.pressCounts[mask] = count - 1
        }
    }

    /**
     * Called once the gamepad screen has handed a packet to the network layer.
     *
     * Everything pressed before this point has now been transmitted, so the
     * release edges that were waiting on it are applied: the bit comes off
     * `ButtonsDown` and its release edge goes into `ButtonsUp` for the next
     * packet. `ButtonsUp` is cleared here rather than in the send loop so the
     * edges cannot be cleared before the packet that carries them went out.
     */
    @Synchronized
    fun afterSend(state: GamepadReading) {
        state.ButtonsUp = 0

        val bookkeeping = held[state] ?: return
        bookkeeping.pendingPress.clear()
        if (bookkeeping.pendingRelease.isEmpty()) return

        for (mask in bookkeeping.pendingRelease) {
            // Pressed again in the meantime: still held, so nothing to release.
            if ((bookkeeping.pressCounts[mask] ?: 0) > 0) continue
            state.ButtonsDown = state.ButtonsDown and mask.inv()
            state.ButtonsUp = state.ButtonsUp or mask
        }
        bookkeeping.pendingRelease.clear()
    }

    /**
     * Releasing everything [state] holds, for a gamepad that is leaving the screen:
     * the finger cannot be lifted for the caller, so nothing may be left down.
     */
    @Synchronized
    fun releaseAll(state: GamepadReading) {
        val bookkeeping = held.remove(state) ?: return
        bookkeeping.pendingPress.clear()
        bookkeeping.pendingRelease.clear()
        val downMask = bookkeeping.sources.values.fold(0) { heldBits, mask -> heldBits or mask }
        if (downMask == 0) return
        state.ButtonsDown = state.ButtonsDown and downMask.inv()
        state.ButtonsUp = state.ButtonsUp or downMask
    }
}
