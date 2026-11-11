package app.asystent.mieszkania.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import app.asystent.mieszkania.data.GeminiTts
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Mówi odpowiedzi. Najpierw próbuje naturalnego głosu Gemini TTS, a gdy się nie uda
 * (brak internetu, limit), czyta resztę głosem systemowym Androida.
 */
class Speaker(ctx: Context) {
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false
    @Volatile private var sysDone: (() -> Unit)? = null
    @Volatile private var track: AudioTrack? = null
    @Volatile private var gen = 0
    private var job: Job? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            val t = tts
            if (status == TextToSpeech.SUCCESS && t != null) {
                val r = t.setLanguage(Locale("pl", "PL"))
                ttsReady = r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
                t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { sysDone?.invoke() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { sysDone?.invoke() }
                })
            }
        }
    }

    private fun chunks(text: String, max: Int = 450): List<String> {
        val sentences = text.trim().split(Regex("(?<=[.!?…])\\s+")).filter { it.isNotBlank() }
        val out = ArrayList<String>()
        var cur = StringBuilder()
        for (s in sentences) {
            if (cur.isNotEmpty() && cur.length + s.length + 1 > max) {
                out.add(cur.toString())
                cur = StringBuilder()
            }
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(s)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun speak(
        scope: CoroutineScope,
        text: String,
        useGemini: Boolean,
        key: String,
        model: String,
        voice: String,
        onStart: () -> Unit,
        onDone: () -> Unit,
        onNote: (String) -> Unit,
    ) {
        stop()
        val my = ++gen
        job = scope.launch(Dispatchers.IO) {
            onStart()
            val parts = chunks(text)
            var idx = 0
            if (useGemini && key.isNotBlank()) {
                try {
                    while (idx < parts.size && my == gen) {
                        val pcm = GeminiTts.synth(
                            key, model, voice,
                            "Powiedz po polsku ciepło, naturalnie i swobodnie, jak życzliwa osoba w rozmowie: ${parts[idx]}",
                        )
                        if (my != gen) break
                        play(pcm, my)
                        idx++
                    }
                } catch (e: Exception) {
                    if (my == gen) onNote("Głos Gemini niedostępny (${e.message}). Użyto głosu systemowego.")
                }
            }
            if (my == gen && idx < parts.size) speakSystem(parts.drop(idx).joinToString(" "), onNote)
            if (my == gen) onDone()
        }
    }

    private suspend fun speakSystem(text: String, onNote: (String) -> Unit) {
        val t = tts
        if (t == null || !ttsReady) {
            onNote("Brak polskiego głosu systemowego. Zainstaluj go w ustawieniach telefonu (zamiana tekstu na mowę).")
            return
        }
        val d = CompletableDeferred<Unit>()
        sysDone = { d.complete(Unit) }
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "laura")
        withTimeoutOrNull(60000L) { d.await() }
        sysDone = null
    }

    private suspend fun play(pcm: ByteArray, my: Int) {
        val rate = 24000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(rate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(maxOf(min, 8192))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        try {
            t.play()
            var off = 0
            while (off < pcm.size && my == gen) {
                val n = t.write(pcm, off, minOf(4096, pcm.size - off))
                if (n <= 0) break
                off += n
            }
            val frames = pcm.size / 2
            var waited = 0
            val limit = frames * 1000L / rate + 1500
            while (my == gen && t.playbackHeadPosition < frames && waited < limit) {
                delay(20)
                waited += 20
            }
        } catch (e: Exception) {
        } finally {
            try { t.stop() } catch (_: Exception) { }
            t.release()
            if (track === t) track = null
        }
    }

    fun stop() {
        gen++
        job?.cancel()
        job = null
        try { tts?.stop() } catch (_: Exception) { }
        try { track?.pause(); track?.flush() } catch (_: Exception) { }
        sysDone?.invoke()
    }
}
