package app.asystent.mieszkania.data

import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Klient chmury Tuya (podpis HMAC-SHA256, API v2.0). Telefon podaje czas UTC, więc bez korekty. */
class TuyaClient(
    private val base: String,
    private val clientId: String,
    private val secret: String,
    private val device: String,
) {
    private var token: String = ""
    private var tokenExpires: Long = 0L

    private fun sha256Hex(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun hmacHex(msg: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(msg.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun call(method: String, path: String, body: String, withToken: Boolean): JSONObject {
        val t = System.currentTimeMillis().toString()
        val stringToSign = "$method\n${sha256Hex(body)}\n\n$path"
        val toHash = if (withToken) clientId + token + t + stringToSign else clientId + t + stringToSign
        val headers = mutableMapOf(
            "client_id" to clientId,
            "sign" to hmacHex(toHash),
            "t" to t,
            "sign_method" to "HMAC-SHA256",
            "Content-Type" to "application/json",
        )
        if (withToken) headers["access_token"] = token
        val r = Net.request(
            base.trimEnd('/') + path,
            method,
            headers,
            if (method == "GET") null else body,
            timeoutMs = 15000,
        )
        return JSONObject(r.body)
    }

    private fun fail(res: JSONObject): Nothing =
        throw Exception("Tuya: ${res.optString("msg", "błąd")} (kod ${res.optString("code", "?")})")

    private fun ensureToken() {
        if (token.isNotEmpty() && System.currentTimeMillis() < tokenExpires) return
        val res = call("GET", "/v1.0/token?grant_type=1", "", false)
        if (!res.optBoolean("success")) fail(res)
        val result = res.getJSONObject("result")
        token = result.getString("access_token")
        tokenExpires = System.currentTimeMillis() + (result.optLong("expire_time", 7200L) - 60L) * 1000L
    }

    /** Zwraca stan gniazdka albo null, jeśli nie udało się go odczytać. */
    fun getState(): Boolean? {
        ensureToken()
        val res = call("GET", "/v2.0/cloud/thing/$device/shadow/properties", "", true)
        if (!res.optBoolean("success")) return null
        val props = res.optJSONObject("result")?.optJSONArray("properties") ?: return null
        for (i in 0 until props.length()) {
            val o = props.getJSONObject(i)
            if (o.optString("code").startsWith("switch") && o.opt("value") is Boolean) {
                return o.getBoolean("value")
            }
        }
        return null
    }

    fun setState(on: Boolean) {
        ensureToken()
        val inner = JSONObject().put("switch_1", on).toString()
        val body = JSONObject().put("properties", inner).toString()
        val res = call("POST", "/v2.0/cloud/thing/$device/shadow/properties/issue", body, true)
        if (!res.optBoolean("success")) fail(res)
    }

    /** Przełącza światło. Gdy nie da się odczytać stanu, używa ostatniego znanego. */
    fun toggle(lastKnown: Boolean?): Boolean {
        val read: Boolean? = try { getState() } catch (e: Exception) { null }
        val current = read ?: lastKnown ?: false
        val target = !current
        setState(target)
        return target
    }
}
