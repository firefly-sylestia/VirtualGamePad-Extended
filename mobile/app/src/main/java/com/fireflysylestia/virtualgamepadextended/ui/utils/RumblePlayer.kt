package com.fireflysylestia.virtualgamepadextended.ui.utils

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log

/**
 * Turns the two rumble motors a game asks the pad for into phone vibration.
 *
 * A physical controller has a heavy low-frequency motor and a sharp
 * high-frequency one. A phone has one actuator, so the two are blended: the
 * heavy motor drives the overall strength and the light one adds a little on
 * top, which keeps a faint rumble faint and a hard hit hard. Everything here is
 * best effort — a phone with no actuator, or with vibration switched off by the
 * user or the system, simply does nothing.
 *
 * ## Why the rumble is repeated
 *
 * The PC forwards rumble when the game *changes* it, not continuously: a game
 * that holds a steady rumble — an engine idling, tyres on gravel — sends one
 * frame and then nothing more. Playing that frame as a single 60 ms buzz made
 * sustained effects feel like a single click, which reads as "the vibration does
 * not work". So a frame starts a pulse train that repeats while the last frame is
 * still recent, and stops when the game sends zero.
 *
 * The train also stops after [SILENCE_TIMEOUT_MILLIS] without a frame. Without
 * that, a game that closes or a socket that drops before its final zero would
 * leave the phone buzzing until the app was killed — a much worse failure than a
 * rumble that ends early.
 */
object RumblePlayer {
    private const val tag = "RumblePlayer"

    /** How long one pulse vibrates for. */
    private const val PULSE_MILLIS = 60L

    /**
     * How long a rumble survives without a fresh frame from the PC.
     *
     * Longer than [PULSE_MILLIS] so the train keeps going between frames, short
     * enough that a dropped connection stops buzzing almost immediately.
     */
    private const val SILENCE_TIMEOUT_MILLIS = 250L

    /**
     * Whether game rumble may buzz the phone.
     *
     * Separate from [HapticUtils.isEnabled] on purpose: touch feedback on the
     * controls and vibration driven by the game are different things, and a player
     * may well want one without the other. Kept as a plain flag because the network
     * reader that receives rumble frames has no repository to read from, the same
     * way [HapticUtils.isEnabled] is set from the activity.
     */
    @Volatile
    var isEnabled: Boolean = true

    /** The application context, remembered from the first rumble frame. */
    private var context: Context? = null

    /** Lazily fetched, because the system service is not free to look up. */
    private var vibrator: Vibrator? = null

    /** Strength of the most recent frame, 0..255. */
    private var lastStrength = 0

    /** When the most recent frame arrived, in `uptimeMillis`. */
    private var lastFrameAt = 0L

    /** Whether the pulse train is currently scheduled. */
    private var playing = false

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    private val pulse = object : Runnable {
        override fun run() {
            val strength = lastStrength
            val silentFor = SystemClock.uptimeMillis() - lastFrameAt
            if (!isEnabled || strength <= 0 || silentFor > SILENCE_TIMEOUT_MILLIS) {
                // No fresh frame: the game stopped rumbling, or stopped talking.
                stop()
                return
            }
            emit(strength)
            handler.postDelayed(this, PULSE_MILLIS)
        }
    }

    private fun vibrator(context: Context): Vibrator? {
        vibrator?.let { return it }
        return try {
            val obtained = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator = obtained
            obtained
        } catch (e: Exception) {
            Log.w(tag, "No vibrator available: ${e.message}")
            null
        }
    }

    /**
     * Play one rumble. [large] is the heavy motor and [small] the light one,
     * both 0..255 as the PC sends them. A zero for both means the game stopped
     * rumbling, so the pulse train is cancelled rather than left buzzing.
     */
    fun rumble(context: Context, large: Int, small: Int) {
        // The reader that calls this has no other use for the context, and the
        // application context is safe to hold for the life of the process.
        this.context = context.applicationContext

        if (!isEnabled) {
            stop()
            return
        }

        val heavy = large.coerceIn(0, 255)
        val light = small.coerceIn(0, 255)
        lastStrength = (heavy + light / 2).coerceIn(0, 255)
        lastFrameAt = SystemClock.uptimeMillis()

        if (lastStrength == 0) {
            stop()
            return
        }

        if (!playing) {
            playing = true
            handler.post(pulse)
        }
    }

    /** Cancels any running train, for a rumble switched off mid-effect. */
    fun stop() {
        playing = false
        handler.removeCallbacks(pulse)
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.w(tag, "Rumble cancel failed: ${e.message}")
        }
    }

    private fun emit(strength: Int) {
        val context = context ?: return
        try {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(PULSE_MILLIS, strength))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(PULSE_MILLIS)
            }
        } catch (e: Exception) {
            // Never let a vibration failure touch the input path.
            Log.w(tag, "Rumble failed: ${e.message}")
            stop()
        }
    }
}
