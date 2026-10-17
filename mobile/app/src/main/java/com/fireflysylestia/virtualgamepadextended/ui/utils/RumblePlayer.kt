package com.fireflysylestia.virtualgamepadextended.ui.utils

import android.content.Context
import android.os.Build
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
 */
object RumblePlayer {
    private const val tag = "RumblePlayer"

    /** Lazily fetched, because the system service is not free to look up. */
    private var vibrator: Vibrator? = null

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
     * rumbling, so any running effect is cancelled rather than left buzzing.
     */
    fun rumble(context: Context, large: Int, small: Int) {
        try {
            val v = vibrator(context) ?: return
            if (!v.hasVibrator()) return

            val heavy = large.coerceIn(0, 255)
            val light = small.coerceIn(0, 255)
            val strength = (heavy + light / 2).coerceIn(0, 255)

            if (strength == 0) {
                v.cancel()
                return
            }

            // Short beat, so a held rumble reads as a pulse rather than a drone
            // and the phone cannot be left vibrating if a frame is ever lost.
            val durationMillis = 60L
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(
                    VibrationEffect.createOneShot(durationMillis, strength)
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(durationMillis)
            }
        } catch (e: Exception) {
            // Never let a vibration failure touch the input path.
            Log.w(tag, "Rumble failed: ${e.message}")
        }
    }
}
