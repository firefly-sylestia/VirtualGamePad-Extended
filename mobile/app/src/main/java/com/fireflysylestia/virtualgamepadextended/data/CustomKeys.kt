package com.fireflysylestia.virtualgamepadextended.data

import kotlinx.serialization.Serializable

/**
 * One button in the custom-key cluster: what it says and which PC key it sends.
 *
 * The key travels to the PC by name, exactly as the PC's own key names read
 * ("T", "F1", "Tab"), because the reading has a field for the keys the phone
 * holds. That is what lets a phone button reach a game that only reads a
 * keyboard, which a virtual gamepad cannot do.
 *
 * The names are resolved on the PC: an unknown one is ignored with a warning,
 * so a typo cannot send a random key.
 */
@Serializable
data class CustomKeyBinding(
    val label: String,
    val key: String,
)

/**
 * How many buttons the cluster draws.
 *
 * The layout reserves exactly this many, so a saved list is always trimmed or
 * padded to this length.
 */
const val CUSTOM_KEY_SLOTS = 4

/**
 * The Minecraft quick keys, matching what the PC's Minecraft layout uses:
 * chat, inventory, the debug overlay and hiding the HUD.
 */
val defaultCustomKeyBindings = listOf(
    CustomKeyBinding(label = "CHAT", key = "T"),
    CustomKeyBinding(label = "INV", key = "E"),
    CustomKeyBinding(label = "F3", key = "F3"),
    CustomKeyBinding(label = "F1", key = "F1"),
)

/** Fills a saved list out to [CUSTOM_KEY_SLOTS] so the cluster always has four. */
fun normaliseCustomKeyBindings(bindings: List<CustomKeyBinding>): List<CustomKeyBinding> =
    List(CUSTOM_KEY_SLOTS) { index ->
        bindings.getOrNull(index) ?: defaultCustomKeyBindings[index]
    }

/** One entry in the key picker: the name sent to the PC, and what it looks like. */
data class CustomKeyOption(val key: String, val label: String = key)

/**
 * Every key the PC can resolve, grouped the way a keyboard is.
 *
 * The names mirror `pc/src/settings/key_names.cpp`, which is the table that
 * turns a name into a native key code; the two have to stay in step, like the
 * mobile and PC copies of `GameButtons`. A name missing here still works if it
 * is typed by hand, and one the PC does not know is ignored there rather than
 * pressing something else.
 */
val customKeyOptionGroups: List<Pair<String, List<CustomKeyOption>>> = listOf(
    "Letters" to ('A'..'Z').map { CustomKeyOption(it.toString()) },
    "Digits" to ((1..9) + 0).map { CustomKeyOption("Digit$it", it.toString()) },
    "Symbols" to listOf(
        CustomKeyOption("Slash", "/"),
        CustomKeyOption("Backslash", "\\"),
        CustomKeyOption("Semicolon", ";"),
        CustomKeyOption("Apostrophe", "'"),
        CustomKeyOption("Comma", ","),
        CustomKeyOption("Period", "."),
        CustomKeyOption("Minus", "-"),
        CustomKeyOption("Equal", "="),
        CustomKeyOption("LeftBracket", "["),
        CustomKeyOption("RightBracket", "]"),
        CustomKeyOption("Grave", "`"),
    ),
    "Function keys" to (1..12).map { CustomKeyOption("F$it") },
    "Editing" to listOf(
        CustomKeyOption("Space"),
        CustomKeyOption("Enter"),
        CustomKeyOption("Tab"),
        CustomKeyOption("Escape"),
        CustomKeyOption("Backspace"),
        CustomKeyOption("Delete"),
        CustomKeyOption("Insert"),
        CustomKeyOption("CapsLock"),
        CustomKeyOption("PrintScreen"),
    ),
    // Named rather than labelled 0-9: a label of "1" belongs to Digit1 in the
    // picker's shorthand, and the numeric keypad is a different key.
    "Numpad" to listOf(
        CustomKeyOption("Numpad0"),
        CustomKeyOption("Numpad1"),
        CustomKeyOption("Numpad2"),
        CustomKeyOption("Numpad3"),
        CustomKeyOption("Numpad4"),
        CustomKeyOption("Numpad5"),
        CustomKeyOption("Numpad6"),
        CustomKeyOption("Numpad7"),
        CustomKeyOption("Numpad8"),
        CustomKeyOption("Numpad9"),
        CustomKeyOption("NumpadPlus"),
        CustomKeyOption("NumpadMinus"),
    ),
    "Modifiers" to listOf(
        CustomKeyOption("LeftShift"),
        CustomKeyOption("RightShift"),
        CustomKeyOption("LeftCtrl"),
        CustomKeyOption("RightCtrl"),
        CustomKeyOption("LeftAlt"),
        CustomKeyOption("RightAlt"),
    ),
    "Navigation" to listOf(
        CustomKeyOption("Up"),
        CustomKeyOption("Down"),
        CustomKeyOption("Left"),
        CustomKeyOption("Right"),
        CustomKeyOption("Home"),
        CustomKeyOption("End"),
        CustomKeyOption("PageUp"),
        CustomKeyOption("PageDown"),
    ),
    "Mouse" to listOf(
        CustomKeyOption("MouseLeft"),
        CustomKeyOption("MouseRight"),
        CustomKeyOption("MouseMiddle"),
        CustomKeyOption("ScrollUp"),
        CustomKeyOption("ScrollDown"),
    ),
)

/** Every name above, for the "is this a key the PC knows?" hint. */
val knownCustomKeyNames: Set<String> =
    customKeyOptionGroups.flatMap { (_, options) -> options.map { it.key } }
        .map { it.lowercase() }
        .toSet()

/**
 * The name the PC will actually resolve for what the player typed.
 *
 * Single characters are the shorthand the PC accepts ("w" is W, "1" is
 * Digit1, "/" is Slash), so the editor can show the resolved name and the
 * cluster can check it against the table above. Anything else is sent as
 * typed, trusted to the PC's resolver.
 */
fun canonicalKeyName(input: String): String {
    val trimmed = input.trim()
    if (trimmed.length != 1) return trimmed
    val upper = trimmed.uppercase()
    return when {
        upper[0] in 'A'..'Z' -> upper
        upper[0] in '0'..'9' -> "Digit$upper"
        else -> customKeyOptionGroups
            .flatMap { (_, options) -> options }
            .firstOrNull { it.label == trimmed && it.label != it.key }
            ?.key ?: trimmed
    }
}
