package com.marco.mlradio.feedback

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.marco.mlradio.data.Settings

/** Piccole vibrazioni per slider e manopole dell'equalizzatore (disattivabili nelle impostazioni). */
class Haptics(context: Context, private val settings: Settings) {

    private val vibrator: Vibrator? = try {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } catch (_: Throwable) {
        null
    }

    private fun fire(effectId: Int) {
        if (!settings.hapticScreen) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            v.vibrate(VibrationEffect.createPredefined(effectId))
        } catch (_: Throwable) {
        }
    }

    fun tapScreen() = fire(VibrationEffect.EFFECT_TICK)

    /** Un passo di 1 dB degli slider. */
    fun softTick() = fire(VibrationEffect.EFFECT_TICK)

    /** Aggancio allo zero. */
    fun detent() = fire(VibrationEffect.EFFECT_CLICK)
}
