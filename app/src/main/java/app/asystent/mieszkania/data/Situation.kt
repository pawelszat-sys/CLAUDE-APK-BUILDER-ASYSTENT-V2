package app.asystent.mieszkania.data

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

enum class Sev { CRIT, WARN, OK }

data class Note(val sev: Sev, val text: String)

/** Silnik reguł: z bieżących danych (dom, zewnątrz, prognoza, trend ciśnienia) wybiera to, co jest teraz ważne. */
object Situation {
    private val pl: Locale = Locale.forLanguageTag("pl-PL")
    private fun n(v: Double, d: Int = 1): String = String.format(pl, "%.${d}f", v)

    fun build(
        out: OutdoorState,
        ind: IndoorState,
        f: ForecastState,
        hist: List<IndoorStore.Sample>,
        espConfigured: Boolean,
        espConnected: Boolean,
        now: Long,
    ): List<Note> {
        val notes = ArrayList<Note>()
        fun add(s: Sev, t: String) { notes.add(Note(s, t)) }

        // --- na zewnątrz
        out.tempC?.let { t ->
            when {
                t <= -10 -> add(Sev.CRIT, "Silny mróz na zewnątrz (${n(t)}°C). Ubierz się bardzo ciepło i uważaj na lód.")
                t <= 0 -> add(Sev.WARN, "Mróz na zewnątrz (${n(t)}°C): możliwa gołoledź.")
                t >= 30 -> add(Sev.CRIT, "Upał na zewnątrz (${n(t)}°C): pij dużo wody i unikaj słońca.")
                t >= 25 -> add(Sev.WARN, "Ciepło na zewnątrz (${n(t)}°C): pamiętaj o nawodnieniu.")
            }
        }
        out.windKmh?.let { w ->
            if (w >= 60) add(Sev.CRIT, "Bardzo silny wiatr (${n(w, 0)} km/h).")
            else if (w >= 40) add(Sev.WARN, "Silny wiatr (${n(w, 0)} km/h).")
        }
        val hasPm = out.pm25 != null || out.pm10 != null
        val worst = max(out.pm25Pct ?: 0.0, out.pm10Pct ?: 0.0)
        if (hasPm) {
            when {
                worst >= 100 -> add(Sev.CRIT, "Smog: pyły przekraczają normę (PM2,5: ${n(out.pm25 ?: 0.0)} µg/m³). Nie wietrz mieszkania i ogranicz wyjścia.")
                worst >= 50 -> add(Sev.WARN, "Pogorszona jakość powietrza (${n(worst, 0)}% normy pyłów). Wietrz krótko.")
            }
        }
        val rain = f.rainText
        if (rain.startsWith("Teraz pada")) add(Sev.WARN, "Teraz pada. Weź parasol.")
        else if (rain.startsWith("Opady możliwe")) add(Sev.WARN, "$rain Weź parasol.")

        val win = f.hours.drop(f.nowIndex).take(13).mapNotNull { it.temp }
        if (win.size > 4) {
            val mn = win.min()
            val mx = win.max()
            if (mx - mn >= 8) add(Sev.WARN, "Duża zmiana temperatury w ciągu doby: od ${n(mn, 0)} do ${n(mx, 0)}°C.")
        }

        // --- w domu
        ind.tempC?.let { t ->
            when {
                t > 30 -> add(Sev.CRIT, "W domu bardzo gorąco (${n(t)}°C).")
                t > 26 -> add(Sev.WARN, "W domu gorąco (${n(t)}°C).")
                t < 16 -> add(Sev.CRIT, "W domu bardzo chłodno (${n(t)}°C).")
                t < 18 -> add(Sev.WARN, "W domu chłodno (${n(t)}°C).")
            }
        }
        ind.humidity?.let { h ->
            when {
                h > 75 -> add(Sev.CRIT, "Bardzo wilgotno w domu (${n(h, 0)}%): ryzyko pleśni, wywietrz i osusz.")
                h > 65 -> add(Sev.WARN, "Wilgotno w domu (${n(h, 0)}%): wywietrz mieszkanie.")
                h < 25 -> add(Sev.CRIT, "Bardzo sucho w domu (${n(h, 0)}%): nawilż powietrze.")
                h < 30 -> add(Sev.WARN, "Powietrze w domu jest suche (${n(h, 0)}%): nawilż lub wywietrz.")
            }
        }
        // sygnał łączony: warto wietrzyć
        val ih = ind.humidity
        val oh = out.humidity
        if (ih != null && oh != null && ih > 60 && oh < ih - 10 && worst < 50) {
            add(Sev.WARN, "Warto wywietrzyć: na zewnątrz jest suchsze i czystsze powietrze (${n(oh, 0)}% wobec ${n(ih, 0)}% w domu).")
        }
        // trend ciśnienia z zapisanej historii
        val p = ind.pressure
        if (p != null) {
            val ref = hist
                .filter { it.press != null && now - it.t in 150 * 60_000L..210 * 60_000L }
                .minByOrNull { abs(now - it.t - 180 * 60_000L) }
            val rp = ref?.press
            if (rp != null && p - rp <= -3.0) {
                add(Sev.WARN, "Ciśnienie spadło o ${n(rp - p)} hPa w 3 godziny: możliwe pogorszenie pogody.")
            }
        }
        // stan czujnika
        if (espConfigured) {
            if (!espConnected) add(Sev.WARN, "Czujnik ESP32 nie odpowiada: brak aktualnych danych z mieszkania.")
            else if (ind.updated > 0 && now - ind.updated > 15 * 60_000L) add(Sev.WARN, "Dane z czujnika ESP32 są nieaktualne.")
        }

        val sorted = notes.sortedBy { it.sev.ordinal }
        return if (sorted.isEmpty()) listOf(Note(Sev.OK, "Wszystko w normie.")) else sorted
    }

    fun summary(out: OutdoorState, ind: IndoorState): String {
        val parts = ArrayList<String>()
        if (ind.tempC != null || ind.humidity != null) {
            val s = listOfNotNull(
                ind.tempC?.let { "${n(it)}°C" },
                ind.humidity?.let { "${n(it, 0)}%" },
            ).joinToString(" i ")
            parts.add("w domu $s")
        }
        out.tempC?.let { parts.add("na zewnątrz ${n(it)}°C") }
        if (out.airLabel.isNotEmpty()) parts.add("powietrze: ${out.airLabel.lowercase(pl)}")
        if (parts.isEmpty()) return ""
        return parts.joinToString(", ").replaceFirstChar { it.uppercase(pl) } + "."
    }
}
