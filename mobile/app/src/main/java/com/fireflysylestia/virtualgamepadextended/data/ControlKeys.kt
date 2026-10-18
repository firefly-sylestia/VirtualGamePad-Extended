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
 * Joins a control's keys into one bound value, so a value may hold one key or
 * the two keys of a chord (`LeftShift+C`), in the order the player picked them.
 *
 * `+` is safe as the separator because no PC key name contains it: the names
 * are things like `LeftShift`, `NumpadPlus` and `Space`, and a literal plus is
 * not offered anywhere in the picker either.
 */
const val KEY_CHORD_SEPARATOR = "+"

/**
 * The keys a bound value presses: zero, one, or two (a chord).
 *
 * Trims each part, drops empty ones, and keeps at most two — a button has one
 * tap, so a third name cannot mean anything and is discarded rather than
 * silently sent.
 */
fun keyChordParts(value: String): List<String> =
    value.split(KEY_CHORD_SEPARATOR)
        .map { part -> part.trim() }
        .filter { part -> part.isNotEmpty() }
        .take(2)

/** Joins picked keys back into a bound value. */
fun keyChordValue(parts: List<String>): String =
    parts.joinToString(KEY_CHORD_SEPARATOR)

/**
 * The chord as the PC will resolve it, part by part.
 *
 * Canonicalising the whole string at once would be wrong: `canonicalKeyName`
 * turns a single character into a name ("w" -> "W", "1" -> "Digit1"), and
 * applied to `w+p` there is no single character to turn.
 */
fun canonicalKeyChord(value: String): String =
    keyChordValue(keyChordParts(value).map(::canonicalKeyName))

/**
 * Drops blank keys, so clearing a field means "send the gamepad button again"
 * rather than binding a control to an empty name the PC would ignore. Parts are
 * normalised too, so a value typed as ` A + B ` is stored as `A+B`.
 */
fun normaliseControlKeyBindings(bindings: Map<String, String>): Map<String, String> =
    bindings
        .mapValues { (_, key) -> keyChordValue(keyChordParts(key)) }
        .filterValues { it.isNotEmpty() }

/**
 * The remappable controls a tapped component stands for, one row each in the
 * per-control editor.
 *
 * A cluster (the four face buttons, the D-pad) is one component to the layout
 * editor — it is moved and scaled as one thing — but each of its buttons is its
 * own control where bindings are concerned, so tapping a cluster opens an editor
 * with a remap row per button. Analog sticks, the touch surface and the
 * phone-only controls have no digital output of their own and stand for no
 * remappable controls, hence the empty list.
 */
fun controlsForComponent(component: ButtonComponent): List<String> = when (component) {
    ButtonComponent.FACE_BUTTONS -> listOf("face_a", "face_b", "face_x", "face_y")
    ButtonComponent.DPAD -> listOf("dpad_up", "dpad_down", "dpad_left", "dpad_right")
    ButtonComponent.LEFT_SHOULDER -> listOf("left_shoulder")
    ButtonComponent.RIGHT_SHOULDER -> listOf("right_shoulder")
    ButtonComponent.SELECT_BUTTON -> listOf("view")
    ButtonComponent.START_BUTTON -> listOf("menu")
    ButtonComponent.PLUS_MINUS -> listOf("plus", "minus")
    ButtonComponent.HOME_CAPTURE -> listOf("home", "capture")
    ButtonComponent.SIDE_BUTTONS -> listOf("left_side", "right_side")
    ButtonComponent.REAR_PADDLES -> listOf("paddle_1", "paddle_2", "paddle_3", "paddle_4")
    ButtonComponent.STICK_CLICKS -> listOf("left_stick_click", "right_stick_click")
    // Triggers are analog by default but accept a key like any other control:
    // bound, a pull past half sends the key instead of the axis.
    ButtonComponent.LEFT_TRIGGER -> listOf("left_trigger")
    ButtonComponent.RIGHT_TRIGGER -> listOf("right_trigger")
    else -> emptyList()
}

/** How a control id reads in an editor: `left_stick_click` -> `Left Stick Click`. */
fun controlLabel(id: String): String =
    id.split('_').joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
