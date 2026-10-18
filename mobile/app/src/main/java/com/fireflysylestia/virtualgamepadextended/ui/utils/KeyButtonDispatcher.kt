package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import java.util.IdentityHashMap

/**
 * Tracks the PC key names the custom-key buttons are holding.
 *
 * The protocol carries the keys the phone holds by name, so the reading's
 * `KeysDown` array is rebuilt every time a button is pressed or released. Two
 * buttons may be given the same key; a count keeps that key down until the last
 * of them lets go.
 *
 * ## Why a press is latched until a packet carries it
 *
 * The gamepad screen sends the reading on a timer and each packet is a snapshot,
 * so a tap shorter than one interval would otherwise be invisible to the PC: the
 * name would appear and disappear between two packets and the game would never
 * see the key. A press therefore stays in `KeysDown` until [afterSend] has been
 * told that a packet carrying it has gone out, exactly as
 * [GamepadButtonDispatcher] does for the button mask.
 *
 * ## Why the bookkeeping is keyed by identity
 *
 * The generated `GamepadReading` hashes on its mutable fields, so a value-keyed
 * map would lose the entry the moment the reading changed. Identity never
 * changes, and the entry is dropped in [releaseAll], which every gamepad calls
 * when it leaves the screen.
 */
object KeyButtonDispatcher {

    private class Held {
        /** The key each source currently holds. */
        val sources = mutableMapOf<String, String>()

        /** How many sources hold each key. */
        val counts = mutableMapOf<String, Int>()

        /** Keys pressed since the last packet was handed to the network layer. */
        val pendingPress = mutableSetOf<String>()

        /** Keys released before their press packet went out, waiting their turn. */
        val pendingRelease = mutableSetOf<String>()
    }

    private val held = IdentityHashMap<GamepadReading, Held>()

    @Synchronized
    fun press(state: GamepadReading, source: String, key: String) {
        val name = key.trim()
        if (name.isEmpty()) return

        val bookkeeping = held[state] ?: Held().also { held[state] = it }

        val previous = bookkeeping.sources[source]
        if (previous == name) return
        // A control remapped while it is held swaps to its new key.
        if (previous != null) release(state, source)

        bookkeeping.sources[source] = name
        val count = bookkeeping.counts[name] ?: 0
        if (count == 0) {
            bookkeeping.counts[name] = 1
            bookkeeping.pendingPress.add(name)
            // Pressed again before the release went out: it is held once more, so
            // the queued release no longer describes this source.
            bookkeeping.pendingRelease.remove(name)
        } else {
            bookkeeping.counts[name] = count + 1
        }
        publish(state, bookkeeping)
    }

    @Synchronized
    fun release(state: GamepadReading, source: String) {
        val bookkeeping = held[state] ?: return
        val name = bookkeeping.sources.remove(source) ?: return

        val count = bookkeeping.counts[name] ?: return
        if (count <= 1) {
            bookkeeping.counts.remove(name)
            if (bookkeeping.pendingPress.contains(name)) {
                // The press has not been sent yet. Dropping the name now would
                // hand the game a key it never saw pressed, so it stays down until
                // [afterSend] has flushed the press.
                bookkeeping.pendingRelease.add(name)
            }
        } else {
            bookkeeping.counts[name] = count - 1
        }
        publish(state, bookkeeping)
    }

    /**
     * Called once the gamepad screen has handed a packet to the network layer.
     *
     * Everything pressed before this point has now been transmitted, so the
     * releases that were waiting on it are applied: the names come off
     * `KeysDown` for the next packet, which the game reads as the key being let
     * go.
     */
    @Synchronized
    fun afterSend(state: GamepadReading) {
        val bookkeeping = held[state] ?: return
        bookkeeping.pendingPress.clear()
        if (bookkeeping.pendingRelease.isEmpty()) return

        for (name in bookkeeping.pendingRelease) {
            // Pressed again in the meantime: still held, so nothing to release.
            if ((bookkeeping.counts[name] ?: 0) > 0) continue
            bookkeeping.counts.remove(name)
        }
        bookkeeping.pendingRelease.clear()
        publish(state, bookkeeping)
    }

    /** Lets go of every key this gamepad holds, for a screen being left. */
    @Synchronized
    fun releaseAll(state: GamepadReading) {
        val bookkeeping = held.remove(state)
        bookkeeping?.pendingPress?.clear()
        bookkeeping?.pendingRelease?.clear()
        state.KeysDown = emptyArray()
    }

    /**
     * Writes the array the PC sees: every key a source holds, plus every key whose
     * release is still waiting for the packet that carries its press.
     */
    private fun publish(state: GamepadReading, bookkeeping: Held) {
        state.KeysDown = (bookkeeping.counts.keys + bookkeeping.pendingRelease).toTypedArray()
    }
}
