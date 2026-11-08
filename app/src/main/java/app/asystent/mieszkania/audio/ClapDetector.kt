package app.asystent.mieszkania.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/**
 * Prosty detektor klaśnięć na mikrofonie telefonu.
 * Działa, gdy aplikacja jest na ekranie. Liczy klaśnięcia w serii i zgłasza ich liczbę
 * po krótkiej ciszy (700 ms od ostatniego).
 */
class ClapDetector(
    private val onClaps: (Int) -> Unit,
    private val onLevel: (Int) -> Unit,
) {
    @Volatile
    var sensitivity: Int = 50

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { listen() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission")
    private suspend fun listen() {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(
            rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf, rate / 2),
            )
        } catch (e: Exception) {
            return
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return
        }

        val frame = ShortArray(rate / 50) // 20 ms
        var baseline = 300.0
        var inClap = false
        var loudSince = 0L
        var lastOnset = 0L
        var count = 0
        var tick = 0

        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                val n = rec.read(frame, 0, frame.size)
                if (n <= 0) continue

                var sum = 0.0
                for (i in 0 until n) {
                    val v = frame[i].toDouble()
                    sum += v * v
                }
                val rms = sqrt(sum / n)
                val now = SystemClock.elapsedRealtime()

                val s = sensitivity.coerceIn(0, 100)
                val ratio = 12.0 - s * 0.09
                val floor = 1600.0 - s * 11.0
                val loud = rms > maxOf(floor, baseline * ratio)

                if (loud) {
                    if (loudSince == 0L) loudSince = now
                    if (now - loudSince > 400) {
                        // Długotrwały hałas (muzyka, rozmowa), a nie klaśnięcie.
                        baseline = rms
                        count = 0
                        inClap = true
                    } else if (!inClap) {
                        inClap = true
                        if (now - lastOnset > 150) {
                            count++
                            lastOnset = now
                        }
                    }
                } else {
                    loudSince = 0L
                    if (inClap && rms < baseline * 2.5 + 50) inClap = false
                    baseline = maxOf(120.0, baseline * 0.98 + rms * 0.02)
                }

                if (count > 0 && now - lastOnset > 700) {
                    val c = count
                    count = 0
                    onClaps(c)
                }

                tick++
                if (tick % 10 == 0) onLevel(rms.toInt())
            }
        } finally {
            try { rec.stop() } catch (_: Exception) { }
            rec.release()
        }
    }
}
