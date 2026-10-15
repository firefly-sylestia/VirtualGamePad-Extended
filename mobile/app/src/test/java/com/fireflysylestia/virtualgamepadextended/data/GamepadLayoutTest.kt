package com.fireflysylestia.virtualgamepadextended.data

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the promise that no two controls sit on top of each other.
 *
 * [GamepadGeometry] is what the renderer and the customisation screen both use
 * for placement, so these checks cover what a player actually sees: every
 * built-in preset, on phone and tablet shaped screens.
 */
class GamepadLayoutTest {

    /** Landscape width to height, in dp: phones, a small 16:9 screen, and a tablet. */
    private val screens = listOf(
        915 to 412,
        892 to 411,
        960 to 432,
        800 to 360,
        780 to 360,
        740 to 360,
        640 to 360,
        // Small, cheap phones in landscape: the layout has to survive these too,
        // and they are the ones where the auxiliary buttons used to fall off the
        // right-hand edge.
        600 to 320,
        568 to 320,
        533 to 320,
        480 to 320,
        1024 to 460,
        1280 to 800,
    )

    private fun describe(screen: Pair<Int, Int>) = "${screen.first}x${screen.second}dp"

    private fun rectanglesFor(
        configs: Map<ButtonComponent, ButtonConfig>,
        screen: Pair<Int, Int>,
    ): Map<ButtonComponent, GamepadRect> {
        val (width, height) = screen
        return configs
            .filterValues { it.visible }
            .mapValues { (component, config) ->
                GamepadGeometry.bounds(component, config, width, height)
            }
    }

    private fun assertNoOverlaps(
        configs: Map<ButtonComponent, ButtonConfig>,
        label: String,
    ) {
        for (screen in screens) {
            val rectangles = rectanglesFor(configs, screen)
            val components = rectangles.keys.toList()
            for (i in components.indices) {
                for (j in i + 1 until components.size) {
                    val first = rectangles.getValue(components[i])
                    val second = rectangles.getValue(components[j])
                    assertTrue(
                        "${components[i]} overlaps ${components[j]} in $label on " +
                            "${describe(screen)}: $first vs $second",
                        !first.overlaps(second),
                    )
                }
            }
        }
    }

    private fun assertOnScreen(
        configs: Map<ButtonComponent, ButtonConfig>,
        label: String,
    ) {
        for (screen in screens) {
            val (width, height) = screen
            rectanglesFor(configs, screen).forEach { (component, rect) ->
                val inside = rect.left > -1f &&
                    rect.top > -1f &&
                    rect.left + rect.width < width + 1f &&
                    rect.top + rect.height < height + 1f
                assertTrue(
                    "$component in $label is not fully on screen on ${describe(screen)}: $rect",
                    inside,
                )
            }
        }
    }

    @Test
    fun `every built-in preset fits the screen without overlaps`() {
        for (layout in ControllerLayout.entries) {
            if (layout == ControllerLayout.CUSTOM) continue
            val configs = buttonConfigsForLayout(layout)
            assertNoOverlaps(configs, layout.displayName)
            assertOnScreen(configs, layout.displayName)
        }
    }

    @Test
    fun `showing every control at once still leaves room for all of them`() {
        // The customisation screen can switch every control on at once. The rows
        // in the centre column are chosen so that even that does not stack two
        // controls on the same pixels.
        val everything = defaultButtonConfigs
            .mapValues { (_, config) -> config.copy(visible = true) }
        assertNoOverlaps(everything, "all controls")
        assertOnScreen(everything, "all controls")
    }

    @Test
    fun `presets only change visibility, never position`() {
        // If a preset moved a control it could break the layout above without
        // this test noticing, so pin the rule down directly.
        for (layout in ControllerLayout.entries) {
            if (layout == ControllerLayout.CUSTOM) continue
            buttonConfigsForLayout(layout).forEach { (component, config) ->
                val base = defaultButtonConfigs.getValue(component)
                assertTrue(
                    "${layout.name} moved $component: ${config.anchor} ${config.offsetX}, " +
                        "${config.offsetY} scale ${config.scale} vs ${base.anchor} " +
                        "${base.offsetX}, ${base.offsetY} scale ${base.scale}",
                    config.anchor == base.anchor &&
                        config.offsetX == base.offsetX &&
                        config.offsetY == base.offsetY &&
                        config.scale == base.scale,
                )
            }
        }
    }

    @Test
    fun `the Switch 2 preset hides controls the controller does not have`() {
        val switch2 = buttonConfigsForLayout(ControllerLayout.SWITCH_2)
        listOf(
            ButtonComponent.MACRO_BUTTONS,
            ButtonComponent.SIDE_BUTTONS,
            ButtonComponent.REAR_PADDLES,
            ButtonComponent.TOUCH_SURFACE,
            ButtonComponent.SELECT_BUTTON,
            ButtonComponent.START_BUTTON,
        ).forEach { component ->
            assertTrue(
                "$component should start hidden on Switch 2",
                switch2.getValue(component).visible.not(),
            )
        }
    }

    @Test
    fun `the Pokemon preset drops touch and gyro`() {
        val za = buttonConfigsForLayout(ControllerLayout.POKEMON_ZA)
        assertTrue("touch surface should be hidden", za.getValue(ButtonComponent.TOUCH_SURFACE).visible.not())
        assertTrue("gyro should be hidden", za.getValue(ButtonComponent.GYRO_TOGGLE).visible.not())
        assertTrue("sticks should stay", za.getValue(ButtonComponent.LEFT_ANALOG_STICK).visible)
        assertTrue("face buttons should stay", za.getValue(ButtonComponent.FACE_BUTTONS).visible)
    }
}
