package app.asystent.mieszkania.data

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

data class GeminiAnswer(val text: String, val note: String)

object Gemini {
    private fun url(model: String) = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
    private fun headers(key: String) = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key)

    private fun errMsg(body: String): String = try {
        JSONObject(body).optJSONObject("error")?.optString("message", "")?.take(110) ?: ""
    } catch (e: Exception) {
        ""
    }

    fun textParts(prompt: String): JSONArray = JSONArray().put(JSONObject().put("text", prompt))

    /** Prosta odpowiedź tekstowa bez wyszukiwarki (zostawione dla zgodności). */
    fun advice(key: String, model: String, prompt: String): String {
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", textParts(prompt)))).toString()
        val r = Net.request(url(model), "POST", headers(key), body, 25000)
        if (r.code != 200) throw Exception("Gemini odpowiedział kodem ${r.code}. Sprawdź klucz i nazwę modelu.")
        val text = JSONObject(r.body).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
        return text.trim().trim('"')
    }

    fun ask(key: String, model: String, system: String, prompt: String, search: Boolean = true): String =
        askFull(key, model, system, textParts(prompt), search).text

    /** Pytanie z nagraniem głosowym (WAV). Gemini rozumie mowę i odpowiada w jednym wywołaniu. */
    fun askAudio(key: String, model: String, system: String, wav: ByteArray, text: String): GeminiAnswer {
        val parts = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject().put("mime_type", "audio/wav").put("data", Base64.encodeToString(wav, Base64.NO_WRAP)),
                ),
            )
            .put(JSONObject().put("text", text))
        return askFull(key, model, system, parts, true)
    }

    /**
     * Zapytanie z instrukcją systemową i wyszukiwarką Google. Jeśli wyszukiwarka zwróci błąd,
     * ponawia bez niej, ale mówi o tym w notatce (nic nie dzieje się po cichu).
     */
    fun askFull(key: String, model: String, system: String, userParts: JSONArray, search: Boolean = true): GeminiAnswer {
        fun call(withSearch: Boolean): Net.Resp {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", userParts)))
            if (withSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            return Net.request(url(model), "POST", headers(key), body.toString(), 45000)
        }

        var r = call(search)
        var failNote = ""
        if (r.code != 200 && search) {
            failNote = "Wyszukiwarka Google niedostępna (kod ${r.code}" +
                (errMsg(r.body).let { if (it.isEmpty()) "" else ": $it" }) + "). Odpowiedź bez internetu."
            r = call(false)
        }
        if (r.code != 200) {
            val m = errMsg(r.body)
            throw Exception("Gemini odpowiedział kodem ${r.code}" + (if (m.isEmpty()) "" else ": $m"))
        }
        try {
            val cand = JSONObject(r.body).getJSONArray("candidates").getJSONObject(0)
            val parts = cand.getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) throw Exception("pusta odpowiedź")
            val qs = cand.optJSONObject("groundingMetadata")?.optJSONArray("webSearchQueries")
            val note = when {
                failNote.isNotEmpty() -> failNote
                qs != null && qs.length() > 0 ->
                    "Sprawdzono w Google: " + (0 until minOf(qs.length(), 3)).joinToString("; ") { qs.getString(it) }
                search -> "Odpowiedź z wiedzy modelu, bez wyszukiwania w Google."
                else -> ""
            }
            return GeminiAnswer(out, note)
        } catch (e: Exception) {
            throw Exception("Gemini nie zwrócił odpowiedzi. Spróbuj inaczej sformułować pytanie.")
        }
    }
}
