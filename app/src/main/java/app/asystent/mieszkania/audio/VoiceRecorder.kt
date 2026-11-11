package app.asystent.mieszkania.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

/** Nagrywa pytanie głosowe: zaczyna, gdy ktoś mówi, i kończy po ok. 1,1 s ciszy (albo po stuknięciu mikrofonu). */
object VoiceRecorder {
    @Volatile
    var stopRequested = false

    @SuppressLint("MissingPermission")
    suspend fun capture(maxMs: Int = 10000, onLevel: (Int) -> Unit): ByteArray? = withContext(Dispatchers.IO) {
        val rate = 16000
        val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuf <= 0) return@withContext null
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, max(minBuf, rate),
            )
        } catch (e: Exception) {
            return@withContext null
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return@withContext null
        }
        stopRequested = false
        val out = ByteArrayOutputStream()
        val hop = 320 // 20 ms
        val buf = ShortArray(hop)
        var elapsed = 0
        var noise = 0.0
        var noiseN = 0
        var speechStarted = false
        var speechMs = 0
        var silentMs = 0
        try {
            rec.startRecording()
            while (isActive && elapsed < maxMs && !stopRequested) {
                var got = 0
                while (got < hop) {
                    val n = rec.read(buf, got, hop - got)
                    if (n <= 0) break
                    got += n
                }
                if (got < hop) break
                var sum = 0.0
                val bytes = ByteArray(hop * 2)
                for (i in 0 until hop) {
                    val v = buf[i].toInt()
                    sum += v.toDouble() * v
                    bytes[2 * i] = (v and 0xFF).toByte()
                    bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
                }
                out.write(bytes)
                val rms = sqrt(sum / hop)
                elapsed += 20
                if (elapsed % 100 == 0) onLevel(rms.toInt())
                if (elapsed <= 300) {
                    noise += rms
                    noiseN++
                    continue
                }
                val thr = max((if (noiseN > 0) noise / noiseN else 100.0) * 3.0, 350.0)
                if (rms > thr) {
                    speechStarted = true
                    speechMs += 20
                    silentMs = 0
                } else if (speechStarted) {
                    silentMs += 20
                }
                if (speechStarted && silentMs >= 1100) break
                if (!speechStarted && elapsed >= 5000) break
            }
        } finally {
            try { rec.stop() } catch (_: Exception) { }
            rec.release()
        }
        if (!speechStarted || speechMs < 200) null else out.toByteArray()
    }

    fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray())
        h.putInt(36 + pcm.size)
        h.put("WAVE".toByteArray())
        h.put("fmt ".toByteArray())
        h.putInt(16)
        h.putShort(1.toShort())
        h.putShort(1.toShort())
        h.putInt(rate)
        h.putInt(rate * 2)
        h.putShort(2.toShort())
        h.putShort(16.toShort())
        h.put("data".toByteArray())
        h.putInt(pcm.size)
        return h.array() + pcm
    }
}
