package com.fireflysylestia.virtualgamepadextended.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The custom-key buttons send key names the PC resolves, so the name the editor
 * produces has to be the name the PC's table knows.
 */
class CustomKeysTest {

    @Test
    fun `single characters become the names the PC resolves`() {
        assertEquals("W", canonicalKeyName("w"))
        assertEquals("W", canonicalKeyName(" W "))
        assertEquals("Digit1", canonicalKeyName("1"))
        assertEquals("Digit0", canonicalKeyName("0"))
        assertEquals("Slash", canonicalKeyName("/"))
        assertEquals("Backslash", canonicalKeyName("\\"))
        assertEquals("Semicolon", canonicalKeyName(";"))
        assertEquals("Apostrophe", canonicalKeyName("'"))
        assertEquals("Comma", canonicalKeyName(","))
        assertEquals("Period", canonicalKeyName("."))
        assertEquals("Minus", canonicalKeyName("-"))
        assertEquals("Equal", canonicalKeyName("="))
        assertEquals("LeftBracket", canonicalKeyName("["))
        assertEquals("RightBracket", canonicalKeyName("]"))
        assertEquals("Grave", canonicalKeyName("`"))
    }

    @Test
    fun `longer names are left as typed and resolved case-insensitively on the PC`() {
        assertEquals("F3", canonicalKeyName("F3"))
        assertEquals("f3", canonicalKeyName("f3"))
        assertEquals("MouseLeft", canonicalKeyName("MouseLeft"))
        assertEquals("ScrollUp", canonicalKeyName(" ScrollUp "))
        assertEquals("", canonicalKeyName("   "))
    }

    @Test
    fun `every offered key is one the PC knows`() {
        for ((group, options) in customKeyOptionGroups) {
            assertTrue("group $group is empty", options.isNotEmpty())
            for (option in options) {
                assertTrue(
                    "${option.key} from $group is not in knownCustomKeyNames",
                    option.key.lowercase() in knownCustomKeyNames,
                )
                // A single-character label has to canonicalise to its own key, so
                // the editor's hint never calls an offered key unknown.
                if (option.label.length == 1 && option.label != option.key) {
                    assertEquals(option.key, canonicalKeyName(option.label))
                }
            }
        }
    }

    @Test
    fun `the cluster always has four bindings`() {
        assertEquals(CUSTOM_KEY_SLOTS, normaliseCustomKeyBindings(emptyList()).size)
        assertEquals(
            defaultCustomKeyBindings,
            normaliseCustomKeyBindings(emptyList()),
        )

        val short = normaliseCustomKeyBindings(listOf(CustomKeyBinding("A", "Space")))
        assertEquals(CustomKeyBinding("A", "Space"), short[0])
        assertEquals(defaultCustomKeyBindings[1], short[1])

        val long = normaliseCustomKeyBindings(
            List(10) { CustomKeyBinding("K$it", "F$it") },
        )
        assertEquals(CUSTOM_KEY_SLOTS, long.size)
        assertEquals("F0", long[0].key)
        assertEquals("F3", long[3].key)
    }
}
