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
 * ## Letting go has to give the values back
 *
 * A control writes the reading when it changes and then says nothing while the
 * finger rests, so a latched value left behind would be the last word on it: the
 * packet keeps carrying a pulled trigger long after the hold was switched off,
 * and the PC — which holds whatever the last packet asked for — never lets it go.
 * Unlatching therefore takes back what the hold wrote, on the packet that follows.
 *
 * Buttons and custom keys go back through their dispatchers, which count holders,
 * so one the player is still pressing stays down when the hold lets go. Analog
 * values have no such bookkeeping, so they are only taken back while they still
 * hold exactly the value the snapshot wrote; a control that has written something
 * else since owns the value and is left alone.
 */
class InputHold {

    /** Whether a snapshot is currently being re-applied to every packet. */
    var isLatched by mutableStateOf(false)
        private set

    /** One single-bit mask per button in the snapshot, as a hold source each. */
    private var latchedButtons = emptyList<Int>()

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

    /** Hold sources still to be let go of after the snapshot ended. */
    private var buttonsToRelease = 0
    private var keysToRelease = 0

    /** Whether the analog values the snapshot wrote still have to be taken back. */
    private var retractPending = false

    /** Arms the hold with whatever [state] currently contains, or releases it. */
    fun toggle(state: GamepadReading) {
        if (isLatched) clear() else latch(state)
    }

    /** Copies the currently held inputs out of [state]. */
    fun latch(state: GamepadReading) {
        latchedButtons = bitsOf(state.ButtonsDown)

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

        // A latch that follows a release before the next packet re-presses these
        // same hold sources, so nothing is left outstanding from the old snapshot.
        buttonsToRelease = 0
        keysToRelease = 0
        retractPending = false

        isLatched = true
    }

    /**
     * Lets go of everything the hold was keeping down.
     *
     * The snapshot is not forgotten here: the next [applyTo] needs it to give the
     * values back, and the two dispatchers need it to let the sources go.
     */
    fun clear() {
        isLatched = false
        buttonsToRelease = latchedButtons.size
        keysToRelease = latchedKeys.size
        retractPending = true
    }

    /**
     * Writes the latched snapshot back over [state].
     *
     * Called just before a packet is sent, after the controls have written their
     * own values. A release that happened in the meantime clears the live value,
     * and this puts the latched one back, which is what keeps a released stick
     * "down" on the PC.
     */
    fun applyTo(state: GamepadReading) {
        applyButtonsTo(state)
        applyKeysTo(state)

        if (isLatched) {
            applyAnalogTo(state)
            return
        }

        retractAnalog(state)
    }

    /**
     * Holds the snapshot's buttons through the dispatcher that counts holders.
     *
     * A button is not a value the hold can own: several controls can drive the
     * same output, and the dispatcher is what knows which of them are still
     * pressed. Pressing the snapshot's bits under hold sources is therefore what
     * lets one of them be let go on its own when the hold ends, instead of the
     * bit being cleared out from under a finger that never left it.
     *
     * The release edge of a bit a hold source is holding is dropped as well, so a
     * latched button is not briefly reported as both pressed and released.
     */
    private fun applyButtonsTo(state: GamepadReading) {
        if (!isLatched) {
            repeat(buttonsToRelease) { index ->
                GamepadButtonDispatcher.release(state, holdButtonSource(index))
            }
            buttonsToRelease = 0
            return
        }

        latchedButtons.forEachIndexed { index, mask ->
            GamepadButtonDispatcher.press(state, holdButtonSource(index), mask)
        }

        val heldMask = latchedButtons.fold(0) { mask, bit -> mask or bit }
        state.ButtonsUp = state.ButtonsUp and heldMask.inv()
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

    private fun applyAnalogTo(state: GamepadReading) {
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
     * Puts the analog values back to neutral, once, on the packet after the hold
     * ended — but only where they still read as what the snapshot wrote, which is
     * this hold's own writing and nothing else's.
     */
    private fun retractAnalog(state: GamepadReading) {
        if (!retractPending) return
        retractPending = false

        if (leftStickHeld && state.LeftThumbstickX == leftX && state.LeftThumbstickY == leftY) {
            state.LeftThumbstickX = 0f
            state.LeftThumbstickY = 0f
        }
        if (rightStickHeld && state.RightThumbstickX == rightX && state.RightThumbstickY == rightY) {
            state.RightThumbstickX = 0f
            state.RightThumbstickY = 0f
        }
        if (leftTriggerHeld && state.LeftTrigger == leftTrigger) state.LeftTrigger = 0f
        if (rightTriggerHeld && state.RightTrigger == rightTrigger) state.RightTrigger = 0f

        leftStickHeld = false
        rightStickHeld = false
        leftTriggerHeld = false
        rightTriggerHeld = false
    }

    /** The set bits of [mask], one per button the snapshot holds. */
    private fun bitsOf(mask: Int): List<Int> =
        (0 until Int.SIZE_BITS)
            .map { 1 shl it }
            .filter { mask and it != 0 }

    private fun holdButtonSource(index: Int) = "hold_button_$index"

    private fun holdKeySource(index: Int) = "hold_key_$index"
}
