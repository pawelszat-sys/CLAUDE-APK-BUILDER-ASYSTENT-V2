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
}
