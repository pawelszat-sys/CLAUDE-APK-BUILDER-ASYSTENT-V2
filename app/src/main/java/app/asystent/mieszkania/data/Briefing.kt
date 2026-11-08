package app.asystent.mieszkania.data

import java.util.Locale

/** Krótki briefing pogodowy liczony lokalnie z prognozy (bez Gemini, więc działa od razu). */
object Briefing {
    private val pl: Locale = Locale.forLanguageTag("pl-PL")

    private fun tip(t: Double): String = when {
        t < -5 -> "Bardzo mroźno: ciepła kurtka, czapka i rękawice."
        t < 0 -> "Mroźno: ciepła kurtka i czapka."
        t < 8 -> "Zimno: kurtka i czapka."
        t < 14 -> "Chłodno: weź lekką kurtkę."
        t < 21 -> "Przyjemnie: wystarczy cienka warstwa."
        t < 27 -> "Ciepło: lekki strój."
        else -> "Upał: pij dużo wody i unikaj słońca."
    }

    fun make(tempC: Double?, code: Int?, f: ForecastState): String {
        val today = f.days.firstOrNull()
        val first = buildString {
            val mn = today?.min
            val mx = today?.max
            if (mn != null && mx != null) append("Dziś ${Math.round(mn)}–${Math.round(mx)}°C")
            if (code != null) {
                append(if (isEmpty()) "Teraz " else ", teraz ")
                append(weatherText(code).lowercase(pl))
            }
            if (isNotEmpty()) append(".")
        }
        return listOf(first, f.rainText, tempC?.let { tip(it) } ?: "")
            .filter { it.isNotBlank() }
            .joinToString(" ")
    }
}
