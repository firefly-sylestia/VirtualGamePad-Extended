package com.fireflysylestia.virtualgamepadextended.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GamepadReading

/**
 * Sends a control's press as its gamepad button or as the PC key it is bound to.
 *
 * A phone has no keyboard, and some games only listen to one: a control bound to
 * a key is how a pad's A becomes Space, or its X becomes the sneak key, without
 * changing anything about the button itself. The choice is per control and lives
 * in the remap editor, next to the gamepad action each control already sends.
 *
 * The key travels by name, through [KeyButtonDispatcher], exactly like a
 * custom-key button: the PC resolves it with its own table and ignores one it
 * does not know. A bound control sends *only* the key — the point of binding one
 * is to replace what the button sends, not to add to it — and controls that have
 * no binding are untouched.
 *
 * The map is read on every press rather than captured, so a binding edited on the
 * customisation screen applies to the pad the next time a control is touched.
 */
object ControlPress {

    /**
     * Control id (the same ids the remap editor lists) to PC key name.
     *
     * Set by the pad from the saved settings; empty means every control still
     * sends its gamepad button.
     */
    @Volatile
    var keyBindings: Map<String, String> = emptyMap()

    /** The key [controlId] sends, or `null` when it still sends its own button. */
    fun keyFor(controlId: String?): String? {
        val id = controlId ?: return null
        return keyBindings[id]?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Presses [mask], or the key [controlId] is bound to when it has one. */
    fun press(
        state: GamepadReading,
        source: String,
        controlId: String?,
        mask: Int,
    ) {
        val key = keyFor(controlId)
        if (key != null) {
            KeyButtonDispatcher.press(state, source, key)
        } else {
            GamepadButtonDispatcher.press(state, source, mask)
        }
    }

    /**
     * Lets go of [source].
     *
     * Both dispatchers are asked, because a control that was pressed as a key and
     * is released after its binding was cleared — or the other way round — still
     * has its press where it was made. An output the source never held is not
     * touched by the dispatcher that did not see the press.
     */
    fun release(state: GamepadReading, source: String) {
        GamepadButtonDispatcher.release(state, source)
        KeyButtonDispatcher.release(state, source)
    }
}
