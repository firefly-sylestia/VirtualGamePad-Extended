package com.fireflysylestia.virtualgamepadextended.data

/**
 * Which PC key each control sends, when the player has bound one.
 *
 * A control is named by the same id it is remapped by (`face_a`, `dpad_up`,
 * `left_shoulder`, `plus`, `paddle_1`, …), so the two choices sit together in the
 * editor: what gamepad action the button sends, and which PC key it sends
 * instead. A control with no entry sends its gamepad button as before, which is
 * why the default is empty rather than a table of every control.
 *
 * The key travels by name and is resolved by the PC's own key table, so the same
 * names the custom-key buttons use work here (see `CustomKeys.kt`).
 */
val defaultControlKeyBindings: Map<String, String> = emptyMap()

/**
 * Drops blank keys, so clearing a field means "send the gamepad button again"
 * rather than binding a control to an empty name the PC would ignore.
 */
fun normaliseControlKeyBindings(bindings: Map<String, String>): Map<String, String> =
    bindings
        .mapValues { (_, key) -> key.trim() }
        .filterValues { it.isNotEmpty() }

/** How a control id reads in an editor: `left_stick_click` -> `Left Stick Click`. */
fun controlLabel(id: String): String =
    id.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
