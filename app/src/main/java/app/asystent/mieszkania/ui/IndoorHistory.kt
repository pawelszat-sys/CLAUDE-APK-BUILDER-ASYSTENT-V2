package app.asystent.mieszkania.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.asystent.mieszkania.data.IndoorStore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** Historia z czujnika ESP32: tylko ostatnie 24 godziny. */
@Composable
fun HistoryCard(all: List<IndoorStore.Sample>) {
    var metric by remember { mutableIntStateOf(0) }
    val span = 24L * 3600_000L
    val now = System.currentTimeMillis()
    val from = now - span
    val data = all.filter { it.t >= from }
    val vals = data.map { s -> when (metric) { 0 -> s.temp; 1 -> s.hum; else -> s.press } }
    val unit = listOf("°C", "%", "hPa")[metric]
    val xs = data.map { ((it.t - from).toDouble() / span).toFloat() }
    val timeFmt = remember { SimpleDateFormat("HH:mm", PL) }
    val tips = data.map { timeFmt.format(Date(it.t)) }
    val ticks = ArrayList<Pair<Float, String>>()
    val cal = Calendar.getInstance().apply {
        timeInMillis = from
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.HOUR_OF_DAY, 1)
    }
    while (cal.timeInMillis <= now) {
        val hr = cal.get(Calendar.HOUR_OF_DAY)
        if (hr % 6 == 0) {
            ticks.add(((cal.timeInMillis - from).toDouble() / span).toFloat() to String.format(PL, "%02d:00", hr))
        }
        cal.add(Calendar.HOUR_OF_DAY, 1)
    }
    Panel {
        Caption("Historia z czujnika ESP32, ostatnie 24 godziny")
        ChipRow(listOf("Temperatura", "Wilgotność", "Ciśnienie"), metric) { metric = it }
        if (vals.filterNotNull().size < 2) {
            Text(
                "Za mało odczytów. Zapisujemy jeden co 5 minut, wykres pojawi się po chwili od podłączenia ESP32.",
                fontSize = 14.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            LineChart(
                xs = xs,
                vals = vals,
                tipTimes = tips,
                ticks = ticks,
                unit = unit,
                tipDecimals = if (metric == 0) 1 else 0,
                defaultIndex = data.lastIndex,
                maxGap = 0.03f,
            )
            Text(
                "Przesuń palcem po wykresie, żeby zobaczyć wartość w danej godzinie.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
