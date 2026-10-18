package com.fireflysylestia.virtualgamepadextended.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A control's key binding may hold one key or a two-key chord, so the value
 * grammar and the per-component control lists are what every editor and the pad
 * itself agree on. These checks pin that grammar down.
 */
class ControlKeysTest {

    @Test
    fun `a bound value is one key or an ordered pair`() {
        assertEquals(listOf("Space"), keyChordParts("Space"))
        assertEquals(listOf("LeftShift", "C"), keyChordParts("LeftShift+C"))
        assertEquals(listOf("LeftShift", "C"), keyChordParts(" LeftShift + C "))
        assertEquals("LeftShift+C", keyChordValue(listOf("LeftShift", "C")))
        assertEquals("T", keyChordValue(keyChordParts("T")))
    }

    @Test
    fun `empty halves and extra keys are dropped`() {
        assertEquals(emptyList<String>(), keyChordParts(""))
        assertEquals(emptyList<String>(), keyChordParts("   "))
        assertEquals(listOf("C"), keyChordParts("+C"))
        assertEquals(listOf("C"), keyChordParts("C+"))
        // A tap has two keys at most: a third cannot mean anything and goes.
        assertEquals(listOf("A", "B"), keyChordParts("A+B+C"))
        assertEquals("A+B", keyChordValue(keyChordParts("A+B+C")))
    }

    @Test
    fun `normalising keeps chords, trims halves and drops cleared fields`() {
        val normalised = normaliseControlKeyBindings(
            mapOf(
                "face_a" to " LeftShift + c ",
                "face_b" to "  ",
                "face_x" to "T",
            ),
        )
        assertEquals(
            mapOf("face_a" to "LeftShift+c", "face_x" to "T"),
            normalised,
        )
    }

    @Test
    fun `each part of a chord is canonicalised on its own`() {
        // Canonicalising the whole value at once would find no single character
        // to turn into a name, so the pair has to be split first.
        assertEquals("W+P", canonicalKeyChord("w+p"))
        assertEquals("W", canonicalKeyChord(" w "))
        assertEquals("Digit1+C", canonicalKeyChord("1+c"))
        assertEquals("LeftShift+C", canonicalKeyChord("LeftShift+C"))
    }

    @Test
    fun `every remappable component maps to its controls and the sticks to none`() {
        assertEquals(
            listOf("face_a", "face_b", "face_x", "face_y"),
            controlsForComponent(ButtonComponent.FACE_BUTTONS),
        )
        assertEquals(
            listOf("dpad_up", "dpad_down", "dpad_left", "dpad_right"),
            controlsForComponent(ButtonComponent.DPAD),
        )
        assertEquals(listOf("left_trigger"), controlsForComponent(ButtonComponent.LEFT_TRIGGER))
        assertEquals(listOf("right_trigger"), controlsForComponent(ButtonComponent.RIGHT_TRIGGER))
        assertEquals(listOf("view"), controlsForComponent(ButtonComponent.SELECT_BUTTON))
        assertEquals(listOf("menu"), controlsForComponent(ButtonComponent.START_BUTTON))
        assertEquals(4, controlsForComponent(ButtonComponent.REAR_PADDLES).size)

        assertTrue(controlsForComponent(ButtonComponent.LEFT_ANALOG_STICK).isEmpty())
        assertTrue(controlsForComponent(ButtonComponent.RIGHT_ANALOG_STICK).isEmpty())
        assertTrue(controlsForComponent(ButtonComponent.HOLD).isEmpty())
        assertTrue(controlsForComponent(ButtonComponent.RECORDER).isEmpty())
        // The custom-key cluster has its own editor rows, not per-control ids.
        assertTrue(controlsForComponent(ButtonComponent.CUSTOM_KEYS).isEmpty())

        // Every id either has a gamepad action in the remap dialog or is one of
        // the key-only triggers.
        for (component in ButtonComponent.entries) {
            for (id in controlsForComponent(component)) {
                if (id !in defaultControllerBindings) {
                    assertTrue("$id must be a key-only trigger id", id.endsWith("_trigger"))
                }
            }
        }
    }
}
