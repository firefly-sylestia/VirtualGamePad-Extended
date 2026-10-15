package com.fireflysylestia.virtualgamepadextended.data

import kotlin.math.min

/**
 * A control's on-screen rectangle, in dp, measured from the screen's top-left.
 */
data class GamepadRect(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
) {
    /** True when the two rectangles share any pixels (they may touch exactly). */
    fun overlaps(other: GamepadRect): Boolean =
        left < other.left + other.width &&
            other.left < left + width &&
            top < other.top + other.height &&
            other.top < top + height
}

/**
 * The single source of truth for the gamepad's geometry.
 *
 * Every control is anchored inside a box that is inset from the screen edges,
 * then nudged by offsets measured in *layout units*: `baseDp` is the landscape
 * height, so one offset of `0.25` moves a control a quarter of the screen
 * height. The renderer (`DrawGamepad` and friends) and the customisation screen
 * both go through this file, so the tap targets in the customiser land exactly
 * on the controls they edit, and `GamepadLayoutTest` can prove that no built-in
 * preset has two controls on top of each other.
 *
 * Keep the constants here in step with the sizes the composables are given: the
 * numbers in this file *are* what the renderer uses.
 */
object GamepadGeometry {

    /** Inset from the screen edge, as a fraction of the layout unit. */
    const val DEAD_ZONE_FRACTION = 1f / 18f

    /**
     * Widest screen aspect the layout is designed for. Beyond it `baseDp` is
     * driven by the width so a tablet does not scale every control up until they
     * collide.
     */
    const val MAX_ASPECT = 2.2f

    /** One auxiliary button, as a fraction of the layout unit. */
    const val EXTRA_WIDTH_FRACTION = 0.14f
    const val EXTRA_HEIGHT_FRACTION = 0.105f

    /** Gap between the buttons inside one auxiliary cluster. */
    const val EXTRA_GAP_FRACTION = 0.012f

    private const val ANALOG_OUTER_FRACTION = 1f / 8f
    private const val ANALOG_INNER_FRACTION = 1f / 12f
    private const val ANALOG_RING_DP = 4f
    private const val DPAD_FRACTION = 0.45f
    private const val TRIGGER_FRACTION = 1f / 6f
    private const val MENU_FRACTION = 1f / 8f
    private const val TOUCH_FRACTION = 1f / 3f

    /**
     * The layout unit for a screen: its height in landscape, capped on very wide
     * screens so the gamepad keeps a sane size there too.
     */
    fun baseDp(widthDp: Int, heightDp: Int): Float =
        min(heightDp.toFloat(), widthDp / MAX_ASPECT)

    fun deadZoneDp(widthDp: Int, heightDp: Int): Float =
        baseDp(widthDp, heightDp) * DEAD_ZONE_FRACTION

    fun extraButtonWidth(baseDp: Float, scale: Float = 1f): Float =
        baseDp * EXTRA_WIDTH_FRACTION * scale

    fun extraButtonHeight(baseDp: Float, scale: Float = 1f): Float =
        baseDp * EXTRA_HEIGHT_FRACTION * scale

    /** Width of a row of [count] auxiliary buttons, including the gaps between them. */
    fun extraClusterWidth(count: Int, baseDp: Float, scale: Float = 1f): Float =
        count * extraButtonWidth(baseDp, scale) +
            (count - 1) * EXTRA_GAP_FRACTION * baseDp * scale

    /** How many small buttons a clustered component draws. */
    fun buttonsIn(component: ButtonComponent): Int = when (component) {
        ButtonComponent.PLUS_MINUS,
        ButtonComponent.HOME_CAPTURE,
        ButtonComponent.SIDE_BUTTONS -> 2

        ButtonComponent.REAR_PADDLES,
        ButtonComponent.MACRO_BUTTONS -> 4

        else -> 1
    }

    /**
     * Width and height in dp of one control, matching what the composables are given.
     */
    fun footprint(component: ButtonComponent, baseDp: Float, scale: Float): Pair<Float, Float> {
        val count = buttonsIn(component)
        return when (component) {
            ButtonComponent.LEFT_ANALOG_STICK,
            ButtonComponent.RIGHT_ANALOG_STICK -> {
                val side = 2f * (
                    baseDp * ANALOG_INNER_FRACTION * scale +
                        baseDp * ANALOG_OUTER_FRACTION * scale +
                        ANALOG_RING_DP
                    )
                side to side
            }

            ButtonComponent.DPAD,
            ButtonComponent.FACE_BUTTONS -> {
                val side = DPAD_FRACTION * baseDp * scale
                side to side
            }

            ButtonComponent.LEFT_TRIGGER,
            ButtonComponent.RIGHT_TRIGGER -> {
                val width = TRIGGER_FRACTION * baseDp * scale
                width to width * 1.25f
            }

            ButtonComponent.LEFT_SHOULDER,
            ButtonComponent.RIGHT_SHOULDER -> {
                val side = MENU_FRACTION * baseDp * scale
                side to side
            }

            ButtonComponent.SELECT_BUTTON,
            ButtonComponent.START_BUTTON -> {
                val side = MENU_FRACTION * baseDp * scale
                side to side
            }

            ButtonComponent.TOUCH_SURFACE -> {
                val side = TOUCH_FRACTION * baseDp * scale
                side to side
            }

            ButtonComponent.GYRO_TOGGLE ->
                extraButtonWidth(baseDp, scale) to extraButtonHeight(baseDp, scale)

            else ->
                extraClusterWidth(count, baseDp, scale) to extraButtonHeight(baseDp, scale)
        }
    }

    /**
     * Where a control ends up on a [widthDp] by [heightDp] screen, in dp from the
     * top-left corner. This mirrors `Modifier.align(anchor).offset(...)`: the
     * anchor places the control inside the inset box, then the offset moves it.
     */
    fun bounds(
        component: ButtonComponent,
        config: ButtonConfig,
        widthDp: Int,
        heightDp: Int,
    ): GamepadRect {
        val unit = baseDp(widthDp, heightDp)
        val inset = unit * DEAD_ZONE_FRACTION
        val boxWidth = widthDp - 2f * inset
        val boxHeight = heightDp - 2f * inset
        val (width, height) = footprint(component, unit, config.scale)

        val alignedLeft = when (config.anchor.horizontal) {
            HorizontalAnchor.START -> 0f
            HorizontalAnchor.CENTER -> (boxWidth - width) / 2f
            HorizontalAnchor.END -> boxWidth - width
        }
        val alignedTop = when (config.anchor.vertical) {
            VerticalAnchor.TOP -> 0f
            VerticalAnchor.CENTER -> (boxHeight - height) / 2f
            VerticalAnchor.BOTTOM -> boxHeight - height
        }

        return GamepadRect(
            left = inset + alignedLeft + config.offsetX * unit,
            top = inset + alignedTop + config.offsetY * unit,
            width = width,
            height = height,
        )
    }
}

/** Which edge or centre of the inset box an anchor lines a control up with. */
enum class HorizontalAnchor { START, CENTER, END }

enum class VerticalAnchor { TOP, CENTER, BOTTOM }

val ButtonAnchor.horizontal: HorizontalAnchor
    get() = when (this) {
        ButtonAnchor.TOP_LEFT, ButtonAnchor.CENTER_LEFT, ButtonAnchor.BOTTOM_LEFT ->
            HorizontalAnchor.START

        ButtonAnchor.TOP_CENTER, ButtonAnchor.CENTER, ButtonAnchor.BOTTOM_CENTER ->
            HorizontalAnchor.CENTER

        ButtonAnchor.TOP_RIGHT, ButtonAnchor.CENTER_RIGHT, ButtonAnchor.BOTTOM_RIGHT ->
            HorizontalAnchor.END
    }

val ButtonAnchor.vertical: VerticalAnchor
    get() = when (this) {
        ButtonAnchor.TOP_LEFT, ButtonAnchor.TOP_CENTER, ButtonAnchor.TOP_RIGHT ->
            VerticalAnchor.TOP

        ButtonAnchor.CENTER_LEFT, ButtonAnchor.CENTER, ButtonAnchor.CENTER_RIGHT ->
            VerticalAnchor.CENTER

        ButtonAnchor.BOTTOM_LEFT, ButtonAnchor.BOTTOM_CENTER, ButtonAnchor.BOTTOM_RIGHT ->
            VerticalAnchor.BOTTOM
    }
