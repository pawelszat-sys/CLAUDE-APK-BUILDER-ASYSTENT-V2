package app.asystent.mieszkania.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.asystent.mieszkania.data.IndoorStore
import kotlin.math.max

@Composable
private fun Chips(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, label ->
            val sel = selected == i
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
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

@Composable
fun HistoryCard(all: List<IndoorStore.Sample>) {
    var metric by remember { mutableIntStateOf(0) }
    var range by remember { mutableIntStateOf(0) }
    val span = if (range == 0) 24L * 3600_000L else 7L * 24 * 3600_000L
    val from = System.currentTimeMillis() - span
    val data = all.filter { it.t >= from }
    val vals = data.map { when (metric) { 0 -> it.temp; 1 -> it.hum; else -> it.press } }
    val valid = vals.filterNotNull()
    val unit = listOf("°C", "%", "hPa")[metric]
    val lineColor = Palette.Lamp
    Panel {
        Caption("Historia z czujnika ESP32")
        Chips(listOf("Temperatura", "Wilgotność", "Ciśnienie"), metric) { metric = it }
        Chips(listOf("24 godziny", "7 dni"), range) { range = it }
        if (valid.size < 2) {
            Text(
                "Za mało odczytów. Zapisujemy jeden co 5 minut, wykres pojawi się po chwili od podłączenia ESP32.",
                fontSize = 14.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 10.dp),
            )
        } else {
            val lo = valid.min()
            val hi = valid.max()
            Canvas(Modifier.padding(top = 12.dp).fillMaxWidth().height(140.dp)) {
                val sp = max(hi - lo, 1.0)
                val padV = 10.dp.toPx()
                val hgt = size.height - 2 * padV
                val path = Path()
                var started = false
                data.forEachIndexed { i, s ->
                    val v = vals[i]
                    val gap = i > 0 && s.t - data[i - 1].t > 30 * 60_000L
                    if (v == null || gap) started = false
                    if (v != null) {
                        val x = (s.t - from).toFloat() / span * size.width
                        val y = padV + (1f - ((v - lo) / sp).toFloat()) * hgt
                        if (!started) {
                            path.moveTo(x, y)
                            started = true
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                }
                drawLine(Palette.Line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
                drawPath(path, lineColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (range == 0) "−24 h" else "−7 dni", fontSize = 12.sp, color = Palette.Slate)
                Text("teraz", fontSize = 12.sp, color = Palette.Slate)
            }
            val d = if (metric == 2) 0 else 1
            Text(
                "Zakres: ${fmt(lo, d)}–${fmt(hi, d)} $unit, odczytów: ${valid.size}.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
