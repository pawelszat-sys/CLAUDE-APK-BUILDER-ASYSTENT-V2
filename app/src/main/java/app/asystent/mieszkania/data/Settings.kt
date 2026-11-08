package app.asystent.mieszkania.data

import android.content.Context
import android.content.SharedPreferences

/** Ustawienia zapisane lokalnie na telefonie. Żadnych kluczy w kodzie. */
class Settings(context: Context) {
    private val p: SharedPreferences =
        context.getSharedPreferences("asystent", Context.MODE_PRIVATE)

    private fun str(key: String, def: String): String = p.getString(key, def) ?: def
    private fun putStr(key: String, v: String) = p.edit().putString(key, v).apply()

    var userName: String
        get() = str("userName", "Paweł")
        set(v) = putStr("userName", v)

    var city: String
        get() = str("city", "Bydgoszcz")
        set(v) = putStr("city", v)

    var lat: String
        get() = str("lat", "53.1235")
        set(v) = putStr("lat", v)

    var lon: String
        get() = str("lon", "18.0084")
        set(v) = putStr("lon", v)

    var lockerUrl: String
        get() = str("lockerUrl", "https://inpost.pl/shipx-point-data/60133/BYD01BM/air_index_level")
        set(v) = putStr("lockerUrl", v)

    var tempFromLocker: Boolean
        get() = p.getBoolean("tempFromLocker", true)
        set(v) = p.edit().putBoolean("tempFromLocker", v).apply()

    var geminiKey: String
        get() = str("geminiKey", "")
        set(v) = putStr("geminiKey", v)

    var geminiModel: String
        get() = str("geminiModel", "gemini-2.5-flash-lite")
        set(v) = putStr("geminiModel", v)

    var tuyaBase: String
        get() = str("tuyaBase", "https://openapi.tuyaeu.com")
        set(v) = putStr("tuyaBase", v)

    var tuyaId: String
        get() = str("tuyaId", "")
        set(v) = putStr("tuyaId", v)

    var tuyaSecret: String
        get() = str("tuyaSecret", "")
        set(v) = putStr("tuyaSecret", v)

    var tuyaDevice: String
        get() = str("tuyaDevice", "")
        set(v) = putStr("tuyaDevice", v)

    var clapSensitivity: Int
        get() = p.getInt("clapSensitivity", 50)
        set(v) = p.edit().putInt("clapSensitivity", v).apply()

    var clapsEnabled: Boolean
        get() = p.getBoolean("clapsEnabled", false)
        set(v) = p.edit().putBoolean("clapsEnabled", v).apply()

    var adviceText: String
        get() = str("adviceText", "")
        set(v) = putStr("adviceText", v)

    var adviceTime: Long
        get() = p.getLong("adviceTime", 0L)
        set(v) = p.edit().putLong("adviceTime", v).apply()

    var serverEnabled: Boolean
        get() = p.getBoolean("serverEnabled", false)
        set(v) = p.edit().putBoolean("serverEnabled", v).apply()

    var serverPort: String
        get() = str("serverPort", "8080")
        set(v) = putStr("serverPort", v)

    var serverPin: String
        get() = str("serverPin", "")
        set(v) = putStr("serverPin", v)

    var themeMode: String
        get() = str("themeMode", "dark")
        set(v) = putStr("themeMode", v)

    var kioskScale: Int
        get() = p.getInt("kioskScale", 100)
        set(v) = p.edit().putInt("kioskScale", v).apply()
}
