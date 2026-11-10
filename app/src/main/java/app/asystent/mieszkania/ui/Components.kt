package app.asystent.mieszkania.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.Hub
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

val PL: Locale = Locale.forLanguageTag("pl-PL")

fun fmt(v: Double?, digits: Int = 1): String =
    if (v == null) "—" else String.format(PL, "%.${digits}f", v)

/** Ścieżki ikon (siatka 24x24). */
object Ic {
    const val Home = "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z"
    const val Cloud = "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z"
    const val Grid = "M3 13h8V3H3v10zm0 8h8v-6H3v6zm10 0h8V11h-8v10zm0-18v6h8V3h-8z"
    const val Bulb = "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z"
    const val Mic = "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z"
    const val Tune = "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z"
}

@Composable
fun PathIcon(d: String, tint: Color, box: Dp = 24.dp) {
    val path = remember(d) { PathParser().parsePathString(d).toPath() }
    Canvas(Modifier.size(box)) {
        val s = this.size.width / 24f
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, tint)
        }
    }
}

@Composable
fun WeatherGlyph(code: Int?, box: Dp = 64.dp) {
    if (code == 0 || code == 1) {
        Canvas(Modifier.size(box)) {
            val c = center
            val r = this.size.minDimension * 0.22f
            drawCircle(Palette.Lamp, r, c)
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val inner = r * 1.45f
                val outer = r * 1.9f
                drawLine(
                    Palette.Lamp,
                    Offset(c.x + cos(a).toFloat() * inner, c.y + sin(a).toFloat() * inner),
                    Offset(c.x + cos(a).toFloat() * outer, c.y + sin(a).toFloat() * outer),
                    strokeWidth = r * 0.28f,
                    cap = StrokeCap.Round,
                )
            }
        }
    } else {
        PathIcon(Ic.Cloud, Palette.Slate, box)
    }
}

@Composable
fun Panel(
    modifier: Modifier = Modifier,
    radius: Dp = 20.dp,
    color: Color = Palette.Panel,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color)
            .border(1.dp, Palette.Line, shape)
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun Caption(text: String, color: Color = Palette.Slate) {
    Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.Medium)
}

@Composable
fun Chip(text: String, color: Color) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun Tile(label: String, value: String, unit: String, note: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Caption(label)
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
            Text(value, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Palette.Frost)
            Text(
                unit,
                fontSize = 16.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(start = 3.dp, bottom = 5.dp),
            )
        }
        Text(note, fontSize = 12.sp, color = Palette.Slate, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Przełącznik nasłuchu klaśnięć wraz z prośbą o zgodę na mikrofon. */
@Composable
fun ClapToggleRow(vm: Hub) {
    val on by vm.clapsOn.collectAsStateWithLifecycle()
    val info by vm.clapInfo.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val view = LocalView.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.startClaps()
    }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wykrywanie klaśnięć", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Palette.Frost)
                Text(
                    if (on) "Słucham: 1 klaśnięcie to porada, 2 to światło."
                    else "Wyłączone. Działa tylko, gdy aplikacja jest otwarta.",
                    fontSize = 13.sp,
                    color = Palette.Slate,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { want ->
                    Haptics.tick(view)
                    if (want) {
                        val granted = ContextCompat.checkSelfPermission(
                            ctx, Manifest.permission.RECORD_AUDIO,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) vm.startClaps() else launcher.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        vm.stopClaps()
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Palette.OnLamp,
                    checkedTrackColor = Palette.Lamp,
                ),
            )
        }
        if (on && info.isNotEmpty()) {
            Text(info, fontSize = 12.sp, color = Palette.Slate, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        fontSize = 30.sp,
        fontWeight = FontWeight.Bold,
        color = Palette.Frost,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    )
}

val ColSpacing = Arrangement.spacedBy(12.dp)
