package app.asystent.mieszkania.data

import org.json.JSONArray
import org.json.JSONObject

object Gemini {
    fun advice(key: String, model: String, prompt: String): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val body = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray().put(JSONObject().put("text", prompt)),
                    ),
                ),
            )
            .toString()
        val r = Net.request(
            url,
            method = "POST",
            headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
            body = body,
            timeoutMs = 25000,
        )
        if (r.code != 200) throw Exception("Gemini odpowiedział kodem ${r.code}. Sprawdź klucz i nazwę modelu.")
        val text = JSONObject(r.body)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts").getJSONObject(0)
            .getString("text")
        return text.trim().trim('"')
    }

    /** Pytanie do asystenta: instrukcja systemowa, kontekst domowy i (opcjonalnie) wyszukiwarka Google. */
    fun ask(key: String, model: String, system: String, prompt: String, search: Boolean = true): String {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        fun call(withSearch: Boolean): Net.Resp {
            val body = JSONObject()
                .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                .put(
                    "contents",
                    JSONArray().put(
                        JSONObject().put("role", "user")
                            .put("parts", JSONArray().put(JSONObject().put("text", prompt))),
                    ),
                )
            if (withSearch) body.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            return Net.request(
                url,
                method = "POST",
                headers = mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
                body = body.toString(),
                timeoutMs = 40000,
            )
        }
        var r = call(search)
        if (r.code != 200 && search) r = call(false)
        if (r.code != 200) throw Exception("Gemini odpowiedział kodem ${r.code}. Sprawdź klucz i nazwę modelu.")
        try {
            val parts = JSONObject(r.body).getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            val sb = StringBuilder()
            for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
            val out = sb.toString().trim()
            if (out.isEmpty()) throw Exception("pusta odpowiedź")
            return out
        } catch (e: Exception) {
            throw Exception("Gemini nie zwrócił odpowiedzi. Spróbuj inaczej sformułować pytanie.")
        }
    }
}
