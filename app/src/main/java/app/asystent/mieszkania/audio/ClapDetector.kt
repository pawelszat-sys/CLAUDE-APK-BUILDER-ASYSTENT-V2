package app.asystent.mieszkania.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detektor klaśnięć odporny na gwizd, głos i stuknięcia.
 * Samo przekroczenie głośności nie wystarcza. Po wykryciu nagłego wzrostu (narastanie w kilka ms)
 * analizujemy 150 ms dźwięku:
 *  - klaśnięcie szybko zanika (gwizd, głos i muzyka trwają dłużej),
 *  - widmo jest szerokie i szumowe (gwizd to jeden ostry pik),
 *  - energia nie siedzi w niskich tonach (stuknięcie, tupnięcie),
 *  - jasność dźwięku (centroid) mieści się w typowym zakresie dłoni.
 * Wynik każdego zdarzenia trafia do diagnostyki (Ustawienia), żeby dało się dostroić progi.
 */
class ClapDetector(
    private val onClaps: (Int) -> Unit,
    private val onLevel: (Int) -> Unit,
    private val onDiag: (String) -> Unit,
) {
    @Volatile
    var sensitivity: Int = 40

    private var job: Job? = null
    private val pl: Locale = Locale.forLanguageTag("pl-PL")

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) { listen() }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    @SuppressLint("MissingPermission")
    private fun openRecord(rate: Int, minBuf: Int): AudioRecord? {
        // UNPROCESSED omija automatyczną regulację głośności, która zniekształca impuls
        val sources = if (Build.VERSION.SDK_INT >= 24) {
            intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        } else {
            intArrayOf(MediaRecorder.AudioSource.MIC)
        }
        for (src in sources) {
            try {
                val r = AudioRecord(
                    src, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, rate),
                )
                if (r.state == AudioRecord.STATE_INITIALIZED) return r
                r.release()
            } catch (e: Exception) {
            }
        }
        return null
    }

    private suspend fun listen() {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return
        val rec = openRecord(rate, minBuf) ?: return

        val hop = 80 // 5 ms
        val ringSize = 4096
        val ring = ShortArray(ringSize)
        val buf = ShortArray(hop)
        var total = 0L
        var baseline = 300.0
        var prev = 0.0
        var state = 0 // 0 czeka na zdarzenie, 1 zbiera 150 ms, 2 krótka przerwa
        var onset = 0L
        val post = ArrayList<Double>()
        var coolUntil = 0
        var count = 0
        var lastClap = 0L
        var hopN = 0
        var winMax = 0.0

        try {
            rec.startRecording()
            while (currentCoroutineContext().isActive) {
                var got = 0
                while (got < hop) {
                    val n = rec.read(buf, got, hop - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < hop) continue

                var sum = 0.0
                for (i in 0 until hop) {
                    ring[((total + i) % ringSize).toInt()] = buf[i]
                    val v = buf[i].toDouble()
                    sum += v * v
                }
                total += hop
                val rms = sqrt(sum / hop)
                hopN++
                if (rms > winMax) winMax = rms
                if (hopN % 40 == 0) {
                    onLevel(winMax.toInt())
                    winMax = 0.0
                }

                val s = sensitivity.coerceIn(0, 100)
                val floor = 1900.0 - s * 14.0
                val ratio = 14.0 - s * 0.1

                when (state) {
                    0 -> {
                        val thr = max(floor, baseline * ratio)
                        if (rms > thr && rms > prev * 3.0 + 50.0) {
                            state = 1
                            onset = total - hop
                            post.clear()
                            post.add(rms)
                        } else if (rms < thr) {
                            baseline = max(100.0, baseline * 0.995 + rms * 0.005)
                        }
                    }
                    1 -> {
                        post.add(rms)
                        if (post.size >= 30) {
                            val res = classify(ring, onset, post)
                            onDiag(res.second)
                            if (res.first) {
                                val now = SystemClock.elapsedRealtime()
                                if (count == 0 || now - lastClap > 120) {
                                    count++
                                    lastClap = now
                                }
                            }
                            state = 2
                            coolUntil = hopN + 20
                        }
                    }
                    else -> if (hopN >= coolUntil) state = 0
                }
                prev = rms

                val now = SystemClock.elapsedRealtime()
                if (count > 0 && now - lastClap > 700) {
                    val c = count
                    count = 0
                    onClaps(c)
                }
            }
        } finally {
            try { rec.stop() } catch (_: Exception) { }
            rec.release()
        }
    }

    private fun classify(ring: ShortArray, onset: Long, post: List<Double>): Pair<Boolean, String> {
        val peak = post.max()
        val peakIdx = post.indexOf(peak)
        val tail = post.subList(12, post.size).average() / peak

        val n = 512
        val re = DoubleArray(n)
        val im = DoubleArray(n)
        for (i in 0 until n) {
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (n - 1))
            re[i] = ring[((onset + i) % ring.size).toInt()] * w
        }
        fft(re, im)
        val bins = n / 2
        val mag = DoubleArray(bins) { sqrt(re[it] * re[it] + im[it] * im[it]) }

        var tot = 0.0
        var low = 0.0
        for (k in 1 until bins) {
            val p = mag[k] * mag[k]
            tot += p
            if (k <= 16) low += p // poniżej 500 Hz
        }
        val lowFrac = if (tot > 0) low / tot else 1.0

        var sumM = 0.0
        var sumLn = 0.0
        var mx = 0.0
        var sumF = 0.0
        var cnt = 0
        for (k in 10..239) { // ok. 310 Hz do 7500 Hz
            val m = mag[k] + 1e-9
            sumM += m
            sumLn += ln(m)
            if (m > mx) mx = m
            sumF += k * 31.25 * m
            cnt++
        }
        val mean = sumM / cnt
        val flat = exp(sumLn / cnt) / mean
        val peaky = mx / mean
        val centroid = sumF / sumM

        val info = String.format(
            pl,
            "szczyt %.0f, szczytowość %.1f, płaskość %.2f, niskie %.0f%%, jasność %.0f Hz, ogon %.0f%%",
            peak, peaky, flat, lowFrac * 100, centroid, tail * 100,
        )
        val reason = when {
            tail > 0.30 -> "za długi dźwięk (gwizd, głos, muzyka)"
            peakIdx > 4 -> "wolne narastanie"
            lowFrac > 0.55 -> "niskie tony (stuknięcie, tupnięcie)"
            peaky > 14 -> "dźwięk tonalny (gwizd, głos)"
            centroid < 1200 -> "za ciemny dźwięk"
            flat < 0.06 -> "dźwięk niepodobny do szumu klaśnięcia"
            else -> null
        }
        return if (reason == null) true to "Klaśnięcie wykryte: $info" else false to "Odrzucono: $reason ($info)"
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wlr = cos(ang)
            val wli = sin(ang)
            var i = 0
            while (i < n) {
                var wr = 1.0
                var wi = 0.0
                for (k in 0 until len / 2) {
                    val ur = re[i + k]
                    val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr
                    im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr
                    im[i + k + len / 2] = ui - vi
                    val nwr = wr * wlr - wi * wli
                    wi = wr * wli + wi * wlr
                    wr = nwr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
