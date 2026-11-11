package app.asystent.mieszkania.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/** Naturalny głos z Gemini TTS. Zwraca surowe PCM 16-bit mono 24 kHz. */
object GeminiTts {
    fun synth(key: String, model: String, voice: String, text: String): ByteArray {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put(
                "generationConfig",
                JSONObject()
                    .put("responseModalities", JSONArray().put("AUDIO"))
                    .put(
                        "speechConfig",
                        JSONObject().put(
                            "voiceConfig",
                            JSONObject().put("prebuiltVoiceConfig", JSONObject().put("voiceName", voice)),
                        ),
                    ),
            )
            .toString()
        val r = Net.request(
            url, "POST",
            mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
            body, 45000,
        )
        if (r.code != 200) throw Exception("kod ${r.code}")
        val parts = JSONObject(r.body).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val inl = p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data")
            if (inl != null) return Base64.decode(inl.getString("data"), Base64.DEFAULT)
        }
        throw Exception("brak dźwięku w odpowiedzi")
    }
}
