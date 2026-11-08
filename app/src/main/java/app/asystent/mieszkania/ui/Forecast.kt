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
    val pts = f.hours.map { if (hum) it.hum else it.temp }
    val valid = pts.filterNotNull()
    val lineColor = Palette.Lamp
    val lineMuted = Palette.Lamp.copy(alpha = 0.55f)
    val gridColor = Palette.Line
    Panel {
        Caption("Ostatnie 24 h i prognoza na 24 h")
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Temperatura", "Wilgotność").forEachIndexed { i, label ->
                val sel = mode == i
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (sel) Palette.OnLamp else Palette.Frost,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (sel) Palette.Lamp else Palette.PanelHigh)
                        .clickable { mode = i }
                        .padding(vertical = 10.dp),
                )
            }
        }
        if (valid.size < 2) {
            Text("Brak danych.", fontSize = 15.sp, color = Palette.Slate, modifier = Modifier.padding(top = 10.dp))
        } else {
            val lo = valid.min()
            val hi = valid.max()
            val ni = f.nowIndex.coerceIn(0, pts.size - 1)
            Canvas(Modifier.padding(top = 12.dp).fillMaxWidth().height(140.dp)) {
                val span = max(hi - lo, 1.0)
                val padV = 10.dp.toPx()
                val hgt = size.height - 2 * padV
                val n = pts.size
                fun px(i: Int): Float = i * size.width / (n - 1)
                fun py(v: Double): Float = padV + (1f - ((v - lo) / span).toFloat()) * hgt
                fun line(from: Int, to: Int): Path {
                    val p = Path()
                    var started = false
                    for (i in from..to) {
                        val v = pts[i]
                        if (v == null) {
                            started = false
                            continue
                        }
                        if (!started) {
                            p.moveTo(px(i), py(v))
                            started = true
                        } else {
                            p.lineTo(px(i), py(v))
                        }
                    }
                    return p
                }
                val nx = px(ni)
                val w = 3.dp.toPx()
                drawLine(gridColor, Offset(nx, 0f), Offset(nx, size.height), strokeWidth = 1.dp.toPx())
                drawPath(line(0, ni), lineColor, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawPath(
                    line(ni, n - 1),
                    lineMuted,
                    style = Stroke(
                        w,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f)),
                    ),
                )
                pts[ni]?.let { drawCircle(lineColor, 5.dp.toPx(), Offset(nx, py(it))) }
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("−24 h", fontSize = 12.sp, color = Palette.Slate)
                Text("teraz", fontSize = 12.sp, color = Palette.Slate)
                Text("+24 h", fontSize = 12.sp, color = Palette.Slate)
            }
            Text(
                "Zakres: ${fmt(lo, 0)}–${fmt(hi, 0)} ${if (hum) "%" else "°C"}. " +
                    "Linia ciągła to historia, przerywana to prognoza. Źródło: Open-Meteo.",
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
