package app.asystent.mieszkania.data

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

/** Historia (24 h wstecz) i prognoza (24 h naprzód) z Open-Meteo: temperatura, wilgotność, opady. */
object Forecasts {
    private val pl: Locale = Locale.forLanguageTag("pl-PL")
    private val rainCodes = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99)

    private fun JSONArray.dblAt(i: Int): Double? =
        if (i < length() && !isNull(i)) optDouble(i).takeUnless { it.isNaN() } else null

    private fun JSONArray.intAt(i: Int): Int? =
        if (i < length() && !isNull(i)) optInt(i) else null

    private fun dayLabel(date: String, index: Int): String = when (index) {
        1 -> "Dziś"
        2 -> "Jutro"
        else -> try {
            val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)
            SimpleDateFormat("EEEE", pl).format(d!!).replaceFirstChar { it.uppercase(pl) }
        } catch (e: Exception) {
            date
        }
    }

    private fun rainText(hours: List<HourPoint>, now: Int): String {
        val win = hours.drop(now).take(12)
        val cur = hours.getOrNull(now)?.code
        val first = win.indexOfFirst { (it.rainPct ?: 0) >= 50 }
        val max = win.maxOfOrNull { it.rainPct ?: 0 } ?: 0
        return when {
            win.isEmpty() -> ""
            cur != null && cur in rainCodes -> "Teraz pada."
            first == 0 -> "Opady możliwe już teraz (${win[0].rainPct}%)."
            first > 0 -> "Opady możliwe od ${win[first].label} (${win[first].rainPct}%)."
            max >= 30 -> "Niewielkie ryzyko opadów (do $max%)."
            else -> "Bez opadów w najbliższych 12 godzinach."
        }
    }

    fun fetch(lat: Double, lon: Double): ForecastState? = try {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m" +
            "&hourly=temperature_2m,relative_humidity_2m,precipitation_probability,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code" +
            "&past_days=1&forecast_days=3&timezone=auto"
        val r = Net.request(url, timeoutMs = 15000)
        if (r.code != 200) null else {
            val j = JSONObject(r.body)
            val h = j.getJSONObject("hourly")
            val times = h.getJSONArray("time")
            val temps = h.getJSONArray("temperature_2m")
            val hums = h.getJSONArray("relative_humidity_2m")
            val rain = h.getJSONArray("precipitation_probability")
            val codes = h.getJSONArray("weather_code")
            val nowKey = j.getJSONObject("current").getString("time").substring(0, 13)
            val now = (0 until times.length()).firstOrNull { times.getString(it).startsWith(nowKey) } ?: 24
            val from = maxOf(0, now - 24)
            val to = minOf(times.length() - 1, now + 24)
            val hours = (from..to).map { i ->
                HourPoint(times.getString(i).substring(11, 16), temps.dblAt(i), hums.dblAt(i), rain.intAt(i), codes.intAt(i))
            }
            val nowIdx = now - from

            val d = j.getJSONObject("daily")
            val dt = d.getJSONArray("time")
            val mn = d.getJSONArray("temperature_2m_min")
            val mx = d.getJSONArray("temperature_2m_max")
            val pr = d.getJSONArray("precipitation_probability_max")
            val dc = d.getJSONArray("weather_code")
            val days = (1 until dt.length()).take(3).map { i ->
                DayPoint(dayLabel(dt.getString(i), i), mn.dblAt(i), mx.dblAt(i), pr.intAt(i), dc.intAt(i))
            }
            ForecastState(hours, nowIdx, days, rainText(hours, nowIdx))
        }
    } catch (e: Exception) {
        null
    }
}
