package app.asystent.mieszkania.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Wibracje w aplikacji:
 * tick    - lekkie stuknięcie (przyciski, zakładki, przełączniki)
 * scrub   - tykanie przy przesuwaniu punktu po wykresie
 * confirm - potwierdzenie udanej akcji (zapis ustawień, odpowiedź asystenta)
 * pulse   - własny wzór (wykryte klaśnięcia, wynik sterowania światłem, alarm krytyczny)
 */
object Haptics {
    @Volatile
    var enabled: Boolean = true

    fun tick(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun scrub(v: View) {
        if (enabled) v.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm(v: View) {
        if (!enabled) return
        v.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
        )
    }

    @Suppress("DEPRECATION")
    fun pulse(ctx: Context, vararg pattern: Long) {
        if (!enabled) return
        try {
            val vib = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createWaveform(pattern, -1))
            else vib.vibrate(pattern, -1)
        } catch (e: Exception) {
        }
    }
}
