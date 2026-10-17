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

        isLatched = true
    }

    /** Lets go of everything the hold was keeping down. */
    fun clear() {
        isLatched = false
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
}
