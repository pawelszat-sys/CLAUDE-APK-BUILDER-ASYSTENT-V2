package app.asystent.mieszkania.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.asystent.mieszkania.data.ForecastState
import kotlin.math.max

@Composable
fun RainCard(f: ForecastState) {
    Panel {
        Caption("Czy będzie padać?")
        Text(
            if (f.rainText.isEmpty()) "Brak danych prognozy." else f.rainText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Palette.Frost,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
fun HoursCard(f: ForecastState) {
    val next = f.hours.drop(f.nowIndex).take(12)
    Panel {
        Caption("Najbliższe godziny")
        if (next.isEmpty()) {
            Text("Brak danych prognozy.", fontSize = 15.sp, color = Palette.Slate, modifier = Modifier.padding(top = 6.dp))
        } else {
            Row(
                Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                next.forEach { h ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(h.label, fontSize = 13.sp, color = Palette.Slate)
                        Text(
                            "${fmt(h.temp, 0)}°",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Palette.Frost,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Text(
                            if (h.rainPct == null) "" else "${h.rainPct}%",
                            fontSize = 12.sp,
                            color = if ((h.rainPct ?: 0) >= 50) Palette.Lamp else Palette.Slate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendCard(f: ForecastState) {
    var mode by remember { mutableIntStateOf(0) }
    val hum = mode == 1
    val slice = f.hours.drop(f.nowIndex).take(25)
    val n = slice.size
    val vals = slice.map { if (hum) it.hum else it.temp }
    val xs = slice.indices.map { it.toFloat() / (n - 1).coerceAtLeast(1) }
    val ticks = slice.mapIndexedNotNull { i, h ->
        if (h.label.endsWith(":00") && h.label.substring(0, 2).toIntOrNull()?.rem(6) == 0) xs[i] to h.label else null
    }
    Panel {
        Caption("Prognoza na najbliższe 24 godziny")
        ChipRow(listOf("Temperatura", "Wilgotność"), mode) { mode = it }
        if (vals.filterNotNull().size < 2) {
            Text("Brak danych prognozy.", fontSize = 15.sp, color = Palette.Slate, modifier = Modifier.padding(top = 10.dp))
        } else {
            LineChart(
                xs = xs,
                vals = vals,
                tipTimes = slice.map { it.label },
                ticks = ticks,
                unit = if (hum) "%" else "°C",
                tipDecimals = if (hum) 0 else 1,
                defaultIndex = 0,
            )
            Text(
                "Przesuń palcem po wykresie, żeby zobaczyć wartość w danej godzinie. Źródło: Open-Meteo.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun DaysCard(f: ForecastState) {
    if (f.days.isEmpty()) return
    Panel {
        Caption("Najbliższe dni")
        f.days.forEach { d ->
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(d.label, modifier = Modifier.weight(1f), fontSize = 16.sp, color = Palette.Frost)
                Text(
                    "${fmt(d.min, 0)}° / ${fmt(d.max, 0)}°",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Palette.Frost,
                )
                Text(
                    if (d.rainPct == null) "" else "opady ${d.rainPct}%",
                    modifier = Modifier.width(88.dp),
                    textAlign = TextAlign.End,
                    fontSize = 13.sp,
                    color = if ((d.rainPct ?: 0) >= 50) Palette.Lamp else Palette.Slate,
                )
            }
        }
    }
}
