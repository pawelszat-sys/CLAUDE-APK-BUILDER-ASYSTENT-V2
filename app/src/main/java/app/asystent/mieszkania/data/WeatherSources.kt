package app.asystent.mieszkania.data

import org.json.JSONObject

data class Weather(
    val tempC: Double?,
    val humidity: Double?,
    val windKmh: Double?,
    val rainPct: Int?,
    val code: Int?,
)

data class Air(
    val pm25: Double?,
    val pm10: Double?,
    val pm25Pct: Double?,
    val pm10Pct: Double?,
    val label: String,
)

data class Locker(
    val temp: Double?,
    val hum: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val pm25Pct: Double?,
    val pm10Pct: Double?,
    val level: String?,
)

fun weatherText(code: Int?): String = when (code) {
    null -> ""
    0 -> "Słonecznie"
    1 -> "Przeważnie słonecznie"
    2 -> "Częściowe zachmurzenie"
    3 -> "Pochmurno"
    45, 48 -> "Mgła"
    in 51..57 -> "Mżawka"
    in 61..67 -> "Deszcz"
    in 71..77 -> "Śnieg"
    in 80..82 -> "Przelotny deszcz"
    85, 86 -> "Przelotny śnieg"
    in 95..99 -> "Burza"
    else -> "Zmienna pogoda"
}

private fun JSONObject.dbl(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeUnless { it.isNaN() } else null

/** Źródło zapasowe: oficjalne API Open-Meteo. */
object OpenMeteo {
    fun weather(lat: Double, lon: Double): Weather? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m" +
            "&hourly=precipitation_probability&forecast_hours=1&wind_speed_unit=kmh&timezone=auto"
        val r = Net.request(url)
        if (r.code != 200) null else {
            val j = JSONObject(r.body)
            val cur = j.getJSONObject("current")
            val rain = j.optJSONObject("hourly")?.optJSONArray("precipitation_probability")
                ?.let { if (it.length() > 0 && !it.isNull(0)) it.getInt(0) else null }
            Weather(
                tempC = cur.dbl("temperature_2m"),
                humidity = cur.dbl("relative_humidity_2m"),
                windKmh = cur.dbl("wind_speed_10m"),
                rainPct = rain,
                code = if (cur.has("weather_code") && !cur.isNull("weather_code")) cur.getInt("weather_code") else null,
            )
        }
    } catch (e: Exception) {
        null
    }

    fun air(lat: Double, lon: Double): Air? = try {
        val url = "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=$lat&longitude=$lon" +
            "&current=pm10,pm2_5,european_aqi"
        val r = Net.request(url)
        if (r.code != 200) null else {
            val cur = JSONObject(r.body).getJSONObject("current")
            val pm25 = cur.dbl("pm2_5")
            val pm10 = cur.dbl("pm10")
            val aqi = cur.dbl("european_aqi")
            val label = when {
                aqi == null -> ""
                aqi <= 20 -> "Bardzo dobra"
                aqi <= 40 -> "Dobra"
                aqi <= 60 -> "Umiarkowana"
                aqi <= 80 -> "Dostateczna"
                aqi <= 100 -> "Zła"
                else -> "Bardzo zła"
            }
            Air(pm25, pm10, pm25?.let { it / 25.0 * 100.0 }, pm10?.let { it / 50.0 * 100.0 }, label)
        }
    } catch (e: Exception) {
        null
    }
}

/**
 * Główne źródło: czujniki Paczkomatu InPost. To nieoficjalny endpoint,
 * więc każdy błąd oznacza po prostu "brak danych" i aplikacja wraca do Open-Meteo.
 */
object LockerSource {
    fun levelPl(level: String?): String? = when (level) {
        null, "" -> null
        "VERY_GOOD" -> "Bardzo dobra"
        else -> level
    }

    fun fetch(url: String): Locker? = try {
        val r = Net.request(
            url,
            method = "POST",
            headers = mapOf(
                "X-Requested-With" to "XMLHttpRequest",
                "Accept" to "application/json",
            ),
        )
        if (r.code != 200) null else {
            val j = JSONObject(r.body)
            val arr = j.optJSONArray("air_sensors")
            if (arr == null) null else {
                var temp: Double? = null
                var hum: Double? = null
                var pm25: Double? = null
                var pm10: Double? = null
                var p25: Double? = null
                var p10: Double? = null
                for (i in 0 until arr.length()) {
                    val parts = arr.getString(i).split(":")
                    val value = parts.getOrNull(1)?.toDoubleOrNull()
                    val pct = parts.getOrNull(2)?.toDoubleOrNull()
                    when (parts[0]) {
                        "TEMPERATURE" -> temp = value?.takeIf { it in -50.0..60.0 }
                        "HUMIDITY" -> hum = value?.takeIf { it in 0.0..100.0 }
                        "PM25" -> { pm25 = value?.takeIf { it in 0.0..1000.0 }; p25 = pct }
                        "PM10" -> { pm10 = value?.takeIf { it in 0.0..1000.0 }; p10 = pct }
                    }
                }
                val lvl = j.optString("air_index_level", "").ifEmpty { null }
                if (temp == null && hum == null && pm25 == null && pm10 == null) null
                else Locker(temp, hum, pm25, pm10, p25, p10, lvl)
            }
        }
    } catch (e: Exception) {
        null
    }
}
