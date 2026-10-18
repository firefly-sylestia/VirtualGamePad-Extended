package com.fireflysylestia.virtualgamepadextended.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fireflysylestia.virtualgamepadextended.data.RECORDED_MACRO_MAX_FRAMES
import com.fireflysylestia.virtualgamepadextended.data.RECORDED_MACRO_SAMPLE_MILLIS
import com.fireflysylestia.virtualgamepadextended.data.RecordedFrame
import com.fireflysylestia.virtualgamepadextended.data.RecordedMacro
import com.fireflysylestia.virtualgamepadextended.data.differsFrom
import com.fireflysylestia.virtualgamepadextended.data.frameOf
import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import kotlinx.coroutines.delay

/**
 * Watches the pad while the player performs a combination, and keeps what they
 * did so it can be played back exactly.
 *
 * It reads the live [GamepadReading] the controls are already writing into, so a
 * recording is the input itself rather than a transcription of it: two buttons
 * held at once stay two buttons held at once, a trigger held half-way stays
 * half-way, and the pauses between them are timed rather than guessed. A frame
 * is stored only when something changes, which is what makes a pause free and a
 * long gesture small.
 *
 * The sampling loop is driven by the pad screen (`sample` is suspend and returns
 * when recording stops), so nothing here owns a thread or a coroutine scope.
 */
class MacroRecorder {

    /** Whether the pad is being watched right now. */
    var isRecording by mutableStateOf(false)
        private set

    /** How long the current recording has been running, in milliseconds. */
    var elapsedMillis by mutableStateOf(0)
        private set

    /** How many sampled changes the current recording holds. */
    var frameCount by mutableStateOf(0)
        private set

    private val frames = mutableListOf<RecordedFrame>()

    /** Whether the ceiling on frames was reached, so the tail is not recorded. */
    var isFull by mutableStateOf(false)
        private set

    private var startedAt = 0L

    /** Starts watching [state], throwing away whatever the last recording held. */
    fun start(state: GamepadReading) {
        frames.clear()
        // The opening frame is what the pad looks like at the tap: without it a
        // replay would not know where it started, and a stick already pushed
        // would look like a sweep from centred.
        frames += frameOf(state, 0)
        frameCount = 1
        elapsedMillis = 0
        isFull = false
        startedAt = nowMs()
        isRecording = true
    }

    /**
     * Samples [state] every [RECORDED_MACRO_SAMPLE_MILLIS] until recording stops.
     *
     * Called from the pad screen while [isRecording] is true. Reaching the frame
     * ceiling does not stop the recording: the player is still holding the
     * combination they are performing, and stopping under their fingers would
     * save a recording that ends in the middle. The tail is simply not kept.
     */
    suspend fun sample(state: GamepadReading) {
        while (isRecording) {
            delay(RECORDED_MACRO_SAMPLE_MILLIS)
            if (!isRecording) break

            elapsedMillis = (nowMs() - startedAt).toInt()
            if (frames.size >= RECORDED_MACRO_MAX_FRAMES) {
                isFull = true
                continue
            }

            val frame = frameOf(state, elapsedMillis)
            if (frame.differsFrom(frames.last())) {
                frames += frame
                frameCount = frames.size
            }
        }
    }

    /**
     * Ends the recording and hands back what was captured, or `null` when nothing
     * was: a recording with one frame is the pad at rest, and playing that back
     * would do nothing.
     *
     * The last frame holds nothing at all, so the replay ends by letting go of
     * whatever the combination was holding, even if the player was still pressing
     * it when they stopped.
     */
    fun stop(name: String): RecordedMacro? {
        if (!isRecording) return null
        isRecording = false

        elapsedMillis = (nowMs() - startedAt).toInt()
        if (frames.size <= 1 && !isFull) return null

        frames += RecordedFrame(atMillis = elapsedMillis)
        return RecordedMacro(
            name = name,
            durationMillis = elapsedMillis,
            frames = frames.toList(),
        )
    }

    /** Abandons the recording, for a screen the player is leaving. */
    fun cancel() {
        isRecording = false
        frames.clear()
        frameCount = 0
        elapsedMillis = 0
        isFull = false
    }

    /** A clock that only moves forward, and does not jump with the wall clock. */
    private fun nowMs(): Long = System.nanoTime() / 1_000_000L
}
