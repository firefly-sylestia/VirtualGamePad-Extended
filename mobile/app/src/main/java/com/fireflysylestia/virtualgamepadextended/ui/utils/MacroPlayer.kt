package com.fireflysylestia.virtualgamepadextended.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fireflysylestia.virtualgamepadextended.data.RecordedFrame
import com.fireflysylestia.virtualgamepadextended.data.RecordedMacro
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import kotlinx.coroutines.delay

/**
 * Plays a recording back onto the pad.
 *
 * A recording is a clock and the input for each instant of it, so playing one is
 * a matter of walking it on that clock: press what the frame holds, let go of
 * what it does not, and put the sticks and triggers where it says. Every output
 * goes through the same dispatchers the controls use, under sources of its own,
 * so a recording that presses A while the player is holding A counts as two
 * holders and neither one lifts the other's press.
 *
 * The loop is driven by the pad screen (`run` is suspend and returns when
 * playback ends), which keeps this class free of a coroutine scope of its own.
 */
class MacroPlayer {

    /** Whether a recording is playing right now. */
    var isPlaying by mutableStateOf(false)
        private set

    /** Whether playback starts over when it reaches the end. */
    var isRepeating by mutableStateOf(false)
        private set

    /** The name of what is playing, for the panel's status line. */
    var playingName by mutableStateOf<String?>(null)
        private set

    /**
     * Bumped whenever playback should start over; the pad screen keys its
     * `LaunchedEffect` on it so a new recording replaces the one playing.
     */
    var session by mutableStateOf(0)
        private set

    // Snapshot state, so the panel's play-last button enables itself as soon as
    // something has been played once rather than waiting for the next change.
    private var recording: RecordedMacro? by mutableStateOf(null)

    /** Owns the playback that is running, so a cancelled one stays quiet. */
    private var runToken = 0

    /** The last recording played, which the panel plays again on demand. */
    val lastRecording: RecordedMacro? get() = recording

    fun toggleRepeating() {
        isRepeating = !isRepeating
    }

    /** Starts [recorded], replacing anything already playing. */
    fun play(recorded: RecordedMacro) {
        recording = recorded
        playingName = recorded.name
        isPlaying = true
        session++
    }

    /** Stops playback; the running loop lets go of what it was holding. */
    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        playingName = null
    }

    /**
     * Walks the recording, either once or over and over while [isRepeating].
     *
     * Called from the pad screen; it returns when playback stops, and is
     * cancelled when a different recording is started (`session` changes). Only
     * the run that still owns the playback resets the flags, so a cancelled run
     * cannot switch off the one that replaced it.
     */
    suspend fun run(state: GamepadReading) {
        val recorded = recording ?: return
        if (!isPlaying) return

        val token = ++runToken
        val heldButtons = mutableSetOf<Int>()
        val heldKeys = mutableSetOf<String>()
        var lastApplied: RecordedFrame? = null

        fun releaseHeld() {
            for (bit in heldButtons) GamepadButtonDispatcher.release(state, buttonSource(bit))
            heldButtons.clear()
            for (key in heldKeys) KeyButtonDispatcher.release(state, keySource(key))
            heldKeys.clear()
        }

        fun apply(frame: RecordedFrame) {
            val wantedButtons = bitsOf(frame.buttons)
            for (bit in wantedButtons - heldButtons) {
                GamepadButtonDispatcher.press(state, buttonSource(bit), bit)
            }
            for (bit in heldButtons - wantedButtons) {
                GamepadButtonDispatcher.release(state, buttonSource(bit))
            }
            heldButtons.clear()
            heldButtons += wantedButtons

            val wantedKeys = frame.keys.toSet()
            for (key in wantedKeys - heldKeys) {
                KeyButtonDispatcher.press(state, keySource(key), key)
            }
            for (key in heldKeys - wantedKeys) {
                KeyButtonDispatcher.release(state, keySource(key))
            }
            heldKeys.clear()
            heldKeys += wantedKeys

            state.LeftThumbstickX = frame.leftX
            state.LeftThumbstickY = frame.leftY
            state.RightThumbstickX = frame.rightX
            state.RightThumbstickY = frame.rightY
            state.LeftTrigger = frame.leftTrigger
            state.RightTrigger = frame.rightTrigger
            lastApplied = frame
        }

        try {
            do {
                // A repeat starts where the recording does, so the pass before it
                // must not leave a button down.
                releaseHeld()

                val startedAt = nowMs()
                for (frame in recorded.frames) {
                    if (!isPlaying) return
                    waitUntil(startedAt + frame.atMillis)
                    if (!isPlaying) return
                    apply(frame)
                }
            } while (isRepeating)
        } finally {
            releaseHeld()
            retractAnalog(state, lastApplied)
            if (token == runToken) {
                isPlaying = false
                playingName = null
            }
        }
    }

    /**
     * Takes back the analog values the playback wrote, and only where they still
     * read as the last frame wrote them: a control that has written something
     * else since owns those axes now.
     */
    private fun retractAnalog(state: GamepadReading, lastApplied: RecordedFrame?) {
        val frame = lastApplied ?: return
        if (state.LeftThumbstickX == frame.leftX && state.LeftThumbstickY == frame.leftY) {
            state.LeftThumbstickX = 0f
            state.LeftThumbstickY = 0f
        }
        if (state.RightThumbstickX == frame.rightX && state.RightThumbstickY == frame.rightY) {
            state.RightThumbstickX = 0f
            state.RightThumbstickY = 0f
        }
        if (state.LeftTrigger == frame.leftTrigger) state.LeftTrigger = 0f
        if (state.RightTrigger == frame.rightTrigger) state.RightTrigger = 0f
    }

    /** Waits until [target], in slices, so a stop is noticed while waiting. */
    private suspend fun waitUntil(target: Long) {
        while (true) {
            val remaining = target - nowMs()
            if (remaining <= 0) return
            delay(remaining.coerceAtMost(50L))
        }
    }

    /** The set bits of [mask], one per button the frame holds. */
    private fun bitsOf(mask: Int): Set<Int> =
        (0 until Int.SIZE_BITS).map { 1 shl it }.filterTo(mutableSetOf()) { mask and it != 0 }

    /** One playback source per output bit, so two frames neither add nor drop a press. */
    private fun buttonSource(bit: Int) = "replay_button_$bit"

    /** One playback source per key name, for the same reason. */
    private fun keySource(key: String) = "replay_key_$key"

    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
