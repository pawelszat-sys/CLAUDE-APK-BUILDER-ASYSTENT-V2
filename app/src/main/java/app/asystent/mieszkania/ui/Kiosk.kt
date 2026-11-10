package app.asystent.mieszkania.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.Hub
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Kiosk w układzie z wersji Pythona: trzy slajdy (zegar, pogoda, wnętrze) zmieniane co 8 s,
 * kropki na dole i przycisk zamknięcia. Rozmiary liczone od krótszego boku ekranu,
 * więc działa w pionie i w poziomie.
 */
@Composable
fun KioskScreen(vm: Hub) {
    val out by vm.outdoor.collectAsStateWithLifecycle()
    val ind by vm.indoor.collectAsStateWithLifecycle()
    val scale by vm.kioskScale.collectAsStateWithLifecycle()
    val view = LocalView.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var slide by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(8000)
            slide = (slide + 1) % 3
        }
    }
    BackHandler { vm.kiosk.value = false }

    val clock = remember(now / 60000) { SimpleDateFormat("HH:mm", PL).format(Date(now)) }
    val sec = remember(now / 1000) { SimpleDateFormat("ss", PL).format(Date(now)) }
    val date = remember(now / 60000) { SimpleDateFormat("EEEE, d MMMM", PL).format(Date(now)).uppercase(PL) }
    val hour = clock.substring(0, 2).toInt()
    val night = hour >= 23 || hour < 6
    val k = if (night) 0.4f else 1f
    val bg = if (Palette.dark) Color.Black else Color.White
    val fg = if (Palette.dark) Color.White else Color(0xFF111111)
    val main = fg.copy(alpha = k)
    val soft = fg.copy(alpha = 0.45f * k)
    val faint = fg.copy(alpha = 0.32f * k)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                Haptics.tick(view)
                vm.kiosk.value = false
            },
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        val sc = scale / 100f
        val clockSp = minOf(w * 0.30f * sc, h * 0.44f * sc, w * 0.38f)
        val bigSp = minOf(w * 0.26f * sc, h * 0.40f * sc, w * 0.36f)

        Crossfade(targetState = slide, animationSpec = tween(900), label = "kiosk") { s ->
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (s) {
                    0 -> {
                        Text(
                            clock,
                            fontSize = clockSp.sp,
                            lineHeight = (clockSp * 1.05f).sp,
                            letterSpacing = (-2).sp,
                            fontWeight = FontWeight.ExtraLight,
                            color = main,
                        )
                        Text(
                            ":$sec",
                            fontSize = (clockSp * 0.2f).sp,
                            fontWeight = FontWeight.ExtraLight,
                            letterSpacing = 2.sp,
                            color = soft,
                        )
                        Box(Modifier.padding(top = 14.dp).width(40.dp).height(1.dp).background(faint))
                        Text(
                            date,
                            fontSize = (minOf(w * 0.045f, h * 0.07f) * sc).sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 4.sp,
                            color = soft,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    1 -> {
                        WeatherGlyph(out.code, (minOf(w * 0.20f, h * 0.26f) * sc).dp)
                        BigTemp(fmt(out.tempC, 0), bigSp, main, soft)
                        Text(
                            vm.settings.city.uppercase(PL),
                            fontSize = (minOf(w * 0.04f, h * 0.06f) * sc).sp,
                            letterSpacing = 6.sp,
                            color = faint,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    else -> {
                        BigTemp(fmt(ind.tempC), bigSp, main, soft)
                        Text(
                            "${fmt(ind.humidity, 0)} % wilg.",
                            fontSize = (minOf(w * 0.06f, h * 0.09f) * sc).sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 2.sp,
                            color = soft,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        Text(
                            "WEWNĄTRZ",
                            fontSize = (minOf(w * 0.04f, h * 0.06f) * sc).sp,
                            letterSpacing = 6.sp,
                            color = faint,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(if (i == slide) 6.dp else 4.dp)
                        .background(fg.copy(alpha = if (i == slide) 0.8f * k else 0.2f * k), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun BigTemp(value: String, sp: Float, main: Color, soft: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            value,
            fontSize = sp.sp,
            lineHeight = (sp * 1.05f).sp,
            fontWeight = FontWeight.ExtraLight,
            color = main,
            softWrap = false,
        )
        Text(
            "°C",
            fontSize = (sp * 0.3f).sp,
            fontWeight = FontWeight.Light,
            color = soft,
            softWrap = false,
            modifier = Modifier.padding(start = 3.dp, top = (sp * 0.1f).dp),
        )
    }
}
