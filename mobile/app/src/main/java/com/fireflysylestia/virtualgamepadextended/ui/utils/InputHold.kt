package com.fireflysylestia.virtualgamepadextended.ui.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.kitswas.VGP_Data_Exchange.GamepadReading

/**
 * Freezes the inputs that are down at the moment it is armed.
 *
 * Some games ask the player to hold two things at once — a stick pushed forward
 * while the other thumb hits a face button, a trigger kept half-pulled — and a
 * phone has no keyboard to lean on. Tapping the HOLD button while those controls
 * are down takes a copy of them and re-applies it to every packet that is sent,
 * so lifting the finger no longer releases them. Tapping HOLD again lets go of
 * everything it was holding.
 *
 * It is deliberately a snapshot rather than a per-control toggle: what is down
 * when the button is tapped is what stays down, and nothing that is pressed
 * afterwards is latched by accident.
 *
 * The controls themselves keep updating the shared reading as usual, so they can
 * still be re-pressed while the hold is on. Only the values captured at arm time
 * are written back on top; the rest of the reading is whatever the player is
 * doing now.
 *
 * Custom-key buttons are part of the snapshot too, but they cannot be written
 * back the same way: the held key names belong to [KeyButtonDispatcher], which
 * rebuilds them whenever a button changes. The snapshot re-presses them under its
 * own hold sources instead, so a key can be latched and pressed by a finger at
 * the same time.
 */
class InputHold {

    /** Whether a snapshot is currently being re-applied to every packet. */
    var isLatched by mutableStateOf(false)
        private set

    private var buttonsDown = 0

    private var leftStickHeld = false
    private var leftX = 0f
    private var leftY = 0f

    private var rightStickHeld = false
    private var rightX = 0f
    private var rightY = 0f

    private var leftTriggerHeld = false
    private var leftTrigger = 0f

    private var rightTriggerHeld = false
    private var rightTrigger = 0f

    /** The custom-key names that were down when the snapshot was taken. */
    private var latchedKeys = emptyList<String>()

    /** How many hold sources still have to be let go after the snapshot ended. */
    private var keysToRelease = 0

    /** Arms the hold with whatever [state] currently contains, or releases it. */
    fun toggle(state: GamepadReading) {
        if (isLatched) clear() else latch(state)
    }

    /** Copies the currently held inputs out of [state]. */
    fun latch(state: GamepadReading) {
        buttonsDown = state.ButtonsDown

        leftX = state.LeftThumbstickX
        leftY = state.LeftThumbstickY
        leftStickHeld = leftX != 0f || leftY != 0f

        rightX = state.RightThumbstickX
        rightY = state.RightThumbstickY
        rightStickHeld = rightX != 0f || rightY != 0f

        leftTrigger = state.LeftTrigger
        leftTriggerHeld = leftTrigger != 0f

        rightTrigger = state.RightTrigger
        rightTriggerHeld = rightTrigger != 0f

        latchedKeys = state.KeysDown.toList()

        isLatched = true
    }

    /** Lets go of everything the hold was keeping down. */
    fun clear() {
        isLatched = false
        // The keys are let go through the dispatcher, which owns the array, so
        // the release happens on the next packet rather than here.
        keysToRelease = latchedKeys.size
        latchedKeys = emptyList()
        buttonsDown = 0
        leftStickHeld = false
        rightStickHeld = false
        leftTriggerHeld = false
        rightTriggerHeld = false
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        leftTrigger = 0f
        rightTrigger = 0f
    }

    /**
     * Writes the latched snapshot back over [state].
     *
     * Called just before a packet is sent, after the controls have written their
     * own values. A release that happened in the meantime clears the live value,
     * and this puts the latched one back, which is what keeps a released stick
     * "down" on the PC. The matching release edge is dropped as well, so a
     * latched button is not briefly reported as both pressed and released.
     */
    fun applyTo(state: GamepadReading) {
        applyKeysTo(state)
        if (!isLatched) return

        state.ButtonsDown = state.ButtonsDown or buttonsDown
        state.ButtonsUp = state.ButtonsUp and buttonsDown.inv()

        if (leftStickHeld) {
            state.LeftThumbstickX = leftX
            state.LeftThumbstickY = leftY
        }
        if (rightStickHeld) {
            state.RightThumbstickX = rightX
            state.RightThumbstickY = rightY
        }
        if (leftTriggerHeld) state.LeftTrigger = leftTrigger
        if (rightTriggerHeld) state.RightTrigger = rightTrigger
    }

    /**
     * Keeps the latched custom keys down, through the dispatcher that owns them.
     *
     * A custom key is not a value on the reading the way a stick or a trigger is:
     * [KeyButtonDispatcher] rebuilds the held names whenever a button changes, so
     * writing the array here would be overwritten by the next press or release.
     * The snapshot is therefore re-pressed under its own hold sources, and one a
     * finger is still on keeps its own source as well — the count in the
     * dispatcher is what lets both hold the same key at once.
     */
    private fun applyKeysTo(state: GamepadReading) {
        if (isLatched) {
            latchedKeys.forEachIndexed { index, key ->
                KeyButtonDispatcher.press(state, holdKeySource(index), key)
            }
            return
        }

        repeat(keysToRelease) { index ->
            KeyButtonDispatcher.release(state, holdKeySource(index))
        }
        keysToRelease = 0
    }

    private fun holdKeySource(index: Int) = "hold_key_$index"
}
