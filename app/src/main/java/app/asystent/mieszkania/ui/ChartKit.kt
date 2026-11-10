package app.asystent.mieszkania.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max

@Composable
fun ChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val view = LocalView.current
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
                    .clickable { Haptics.tick(view); onSelect(i) }
                    .padding(vertical = 10.dp),
            )
        }
    }
}

/**
 * Wykres liniowy z osią czasu (x) i wartością (y). Punkt można przesuwać palcem:
 * pokazuje się mała etykieta z godziną i wartością, a każda zmiana punktu daje lekką wibrację.
 * xs: położenia 0..1, ticks: podpisy osi czasu, tipTimes: godzina dla każdego punktu.
 */
@Composable
fun LineChart(
    xs: List<Float>,
    vals: List<Double?>,
    tipTimes: List<String>,
    ticks: List<Pair<Float, String>>,
    unit: String,
    tipDecimals: Int,
    defaultIndex: Int,
    maxGap: Float = 1f,
) {
    val valid = vals.filterNotNull()
    if (valid.size < 2) return
    val view = LocalView.current
    val tm = rememberTextMeasurer()
    var sel by remember { mutableIntStateOf(-1) }
    val lo = valid.min()
    val hi = valid.max()
    val colLine = Palette.Lamp
    val colGrid = Palette.Line
    val colTipBg = Palette.PanelHigh
    val axisStyle = TextStyle(fontSize = 10.sp, color = Palette.Slate, fontFamily = Plex)
    val tipStyle = TextStyle(fontSize = 12.sp, color = Palette.Frost, fontFamily = Plex, fontWeight = FontWeight.Medium)

    fun pick(x: Float, widthPx: Int, leftPx: Float, rightPx: Float) {
        val pw = widthPx - leftPx - rightPx
        val fx = ((x - leftPx) / pw).coerceIn(0f, 1f)
        var best = -1
        var bd = Float.MAX_VALUE
        for (i in xs.indices) {
            if (vals[i] == null) continue
            val d = abs(xs[i] - fx)
            if (d < bd) {
                bd = d
                best = i
            }
        }
        if (best >= 0 && best != sel) {
            sel = best
            Haptics.scrub(view)
        }
    }

    val mod = Modifier
        .fillMaxWidth()
        .height(190.dp)
        .pointerInput(xs, vals) {
            detectTapGestures { o -> pick(o.x, size.width, 40.dp.toPx(), 8.dp.toPx()) }
        }
        .pointerInput(xs, vals) {
            detectHorizontalDragGestures(
                onDragStart = { o -> pick(o.x, size.width, 40.dp.toPx(), 8.dp.toPx()) },
            ) { change, _ ->
                change.consume()
                pick(change.position.x, size.width, 40.dp.toPx(), 8.dp.toPx())
            }
        }

    Canvas(mod) {
        val left = 40.dp.toPx()
        val right = 8.dp.toPx()
        val top = 28.dp.toPx()
        val bottom = 22.dp.toPx()
        val pw = size.width - left - right
        val ph = size.height - top - bottom
        val span = max(hi - lo, 1.0)
        fun px(i: Int): Float = left + xs[i] * pw
        fun py(v: Double): Float = top + (1f - ((v - lo) / span).toFloat()) * ph

        // siatka i oś wartości (y)
        val yd = if (hi - lo < 5) 1 else 0
        listOf(lo, (lo + hi) / 2, hi).forEach { v ->
            val y = py(v)
            drawLine(colGrid, Offset(left, y), Offset(size.width - right, y), strokeWidth = 1.dp.toPx())
            val t = tm.measure(fmt(v, yd), axisStyle)
            drawText(t, topLeft = Offset(left - 6.dp.toPx() - t.size.width, y - t.size.height / 2f))
        }
        // oś czasu (x)
        ticks.forEach { (fx, label) ->
            val t = tm.measure(label, axisStyle)
            drawText(t, topLeft = Offset(left + fx * pw - t.size.width / 2f, size.height - bottom + 5.dp.toPx()))
        }
        // linia
        val path = Path()
        var started = false
        for (i in vals.indices) {
            val v = vals[i]
            if (v == null || (i > 0 && xs[i] - xs[i - 1] > maxGap)) started = false
            if (v != null) {
                if (!started) {
                    path.moveTo(px(i), py(v))
                    started = true
                } else {
                    path.lineTo(px(i), py(v))
                }
            }
        }
        drawPath(path, colLine, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // wybrany punkt i etykieta
        var si = if (sel in vals.indices) sel else defaultIndex.coerceIn(0, vals.lastIndex)
        while (si >= 0 && vals[si] == null) si--
        if (si < 0) si = vals.indexOfFirst { it != null }
        val sv = vals[si] ?: return@Canvas
        val sx = px(si)
        val sy = py(sv)
        drawLine(
            colGrid, Offset(sx, top), Offset(sx, top + ph), strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
        )
        drawCircle(colLine, 5.dp.toPx(), Offset(sx, sy))
        val tl = tm.measure(tipTimes[si] + "  " + fmt(sv, tipDecimals) + " " + unit, tipStyle)
        val bw = tl.size.width + 16.dp.toPx()
        val bh = tl.size.height + 8.dp.toPx()
        val bx = (sx - bw / 2f).coerceIn(0f, size.width - bw)
        drawRoundRect(colTipBg, Offset(bx, 0f), Size(bw, bh), CornerRadius(8.dp.toPx()))
        drawRoundRect(colGrid, Offset(bx, 0f), Size(bw, bh), CornerRadius(8.dp.toPx()), style = Stroke(1.dp.toPx()))
        drawText(tl, topLeft = Offset(bx + 8.dp.toPx(), 4.dp.toPx()))
    }
}
