package com.fireflysylestia.virtualgamepadextended.data

import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import kotlinx.serialization.Serializable
import kotlin.math.abs

/**
 * How often the recorder samples the pad while it is recording.
 *
 * ~30 Hz: fast enough that a tap is one step rather than none, and slow enough
 * that a ten-second gesture is a few hundred frames. A frame is written only when
 * something changed, so a pause costs nothing.
 */
const val RECORDED_MACRO_SAMPLE_MILLIS = 33L

/** Ceiling on one recording: about 100 seconds at the sampling rate above. */
const val RECORDED_MACRO_MAX_FRAMES = 3000

/** How many recordings are kept; the oldest is dropped past this. */
const val RECORDED_MACRO_LIMIT = 6

/** An analog change smaller than this is left out of the recording. */
private const val ANALOG_EPSILON = 0.01f

/**
 * One sampled instant of the pad.
 *
 * Everything a control can put on the reading travels here, not just the buttons:
 * a recorded macro is meant to be the *same* input the player made, so a stick
 * sweep or a half-pulled trigger is part of it. The frames of a recording are
 * ordered by [atMillis] and replayed on that clock.
 */
@Serializable
data class RecordedFrame(
    val atMillis: Int,
    val buttons: Int = 0,
    val keys: List<String> = emptyList(),
    val leftX: Float = 0f,
    val leftY: Float = 0f,
    val rightX: Float = 0f,
    val rightY: Float = 0f,
    val leftTrigger: Float = 0f,
    val rightTrigger: Float = 0f,
)

/**
 * A recording: the input of a moment, kept so it can be played again.
 *
 * The name is what the recorder's list shows, the duration is what the player
 * held it for, and the frames carry the input itself. A recording always ends
 * with a frame that holds nothing, so a replay cannot leave something pressed.
 */
@Serializable
data class RecordedMacro(
    val name: String,
    val durationMillis: Int,
    val frames: List<RecordedFrame>,
) {
    /** How many sampled changes the recording holds. */
    val stepCount: Int get() = frames.size

    /** The duration as the panel shows it, e.g. `2.4 s`. */
    val durationLabel: String
        get() = "%.1f s".format(durationMillis / 1000f)
}

/** The reading as a frame of a recording. */
fun frameOf(state: GamepadReading, atMillis: Int): RecordedFrame = RecordedFrame(
    atMillis = atMillis,
    buttons = state.ButtonsDown,
    keys = state.KeysDown.toList().sorted(),
    leftX = state.LeftThumbstickX,
    leftY = state.LeftThumbstickY,
    rightX = state.RightThumbstickX,
    rightY = state.RightThumbstickY,
    leftTrigger = state.LeftTrigger,
    rightTrigger = state.RightTrigger,
)

/**
 * Whether this frame is worth storing next to [other].
 *
 * A frame is kept only when something moved, which is what keeps a long pause
 * from filling the recording with copies of itself. Small analog drift is
 * ignored for the same reason: a finger resting on a stick should not be
 * recorded as a sweep.
 */
fun RecordedFrame.differsFrom(other: RecordedFrame): Boolean =
    buttons != other.buttons ||
        keys != other.keys ||
        abs(leftX - other.leftX) > ANALOG_EPSILON ||
        abs(leftY - other.leftY) > ANALOG_EPSILON ||
        abs(rightX - other.rightX) > ANALOG_EPSILON ||
        abs(rightY - other.rightY) > ANALOG_EPSILON ||
        abs(leftTrigger - other.leftTrigger) > ANALOG_EPSILON ||
        abs(rightTrigger - other.rightTrigger) > ANALOG_EPSILON
