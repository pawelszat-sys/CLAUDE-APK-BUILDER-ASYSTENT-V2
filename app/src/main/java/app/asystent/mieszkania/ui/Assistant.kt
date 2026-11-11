package app.asystent.mieszkania.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.Hub
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun Orb(mode: String, modifier: Modifier = Modifier) {
    val target = when (mode) {
        "listen" -> 0.2f
        "think" -> 0.1f
        "speak" -> 0.26f
        else -> 0.05f
    }
    val amp by animateFloatAsState(target, tween(700), label = "orb")
    val color = when (mode) {
        "listen" -> Palette.Signal
        "think" -> Palette.Lamp
        "speak" -> Palette.Violet
        else -> Palette.Slate
    }
    var t by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { n ->
                if (last != 0L) t += (n - last) / 1_000_000_000f
                last = n
            }
        }
    }
    Canvas(modifier) {
        val r0 = size.minDimension / 2f
        val lvl = if (mode == "speak" || mode == "listen") 0.5f + 0.5f * sin(t * 7f) * sin(t * 2.3f) else 0f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(color.copy(alpha = 0.22f), Color.Transparent),
                center = center,
                radius = r0 * 0.7f,
            ),
            radius = r0 * 0.7f,
            center = center,
        )
        for (k in 0 until 5) {
            val path = Path()
            for (i in 0..120) {
                val a = i / 120f * (2f * PI.toFloat())
                val w = sin(a * (3 + k) + t * (1f + k * 0.4f)) * amp * (1f + lvl) +
                    sin(a * 2f - t * 0.8f + k) * amp * 0.5f
                val rr = r0 * (0.42f + k * 0.1f) * (1f + w)
                val x = center.x + cos(a) * rr
                val y = center.y + sin(a) * rr
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
            drawPath(path, color.copy(alpha = 0.6f - k * 0.1f), style = Stroke(width = max(1.5f, r0 * 0.012f)))
        }
    }
}

private fun modeLabel(m: String) = when (m) {
    "listen" -> "słucham"
    "think" -> "myślę"
    "speak" -> "mówię"
    else -> "czuwam"
}

private val suggestions = listOf(
    "Jaka jest temperatura?",
    "Włącz światło",
    "Jaka będzie pogoda jutro?",
    "Opowiedz ciekawostkę",
)

@Composable
fun AssistantScreen(vm: Hub) {
    val st by vm.assistant.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(st.mode) { if (st.mode == "speak") Haptics.confirm(view) }
    var text by remember { mutableStateOf("") }
    var showHist by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { g ->
        if (g) vm.listenAndAsk()
    }
    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > cfg.screenHeightDp * 1.25f
    val orbSize: Dp = if (wide) (cfg.screenHeightDp * 0.5f).dp else (cfg.screenWidthDp * 0.6f).dp

    fun send() {
        val q = text
        text = ""
        Haptics.tick(view)
        vm.ask(q)
    }

    val controls: @Composable ColumnScope.() -> Unit = {
        Text("${vm.settings.assistantName}: ${modeLabel(st.mode)}", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Palette.Slate)
        Column(Modifier.heightIn(min = 84.dp).padding(horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (st.q.isEmpty() && st.a.isEmpty()) {
                Text(
                    if (st.mode == "listen") "Słucham… Mów, a po skończeniu stuknij mikrofon." else "Stuknij mikrofon, wybierz podpowiedź albo napisz wiadomość.",
                    fontSize = 17.sp,
                    color = Palette.Slate,
                )
            } else {
                if (st.q.isNotEmpty()) Text(st.q, fontSize = 15.sp, color = Palette.Slate)
                if (st.a.isNotEmpty()) {
                    Text(
                        st.a,
                        fontSize = 21.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.Medium,
                        color = Palette.Frost,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
        if (st.note.isNotEmpty()) {
            Text(st.note, fontSize = 12.sp, lineHeight = 17.sp, color = Palette.Slate, textAlign = TextAlign.Center)
        }
        Box(
            Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(if (st.mode == "listen") Palette.Signal else Palette.Lamp)
                .clickable {
                    Haptics.tick(view)
                    val ok = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED
                    if (ok) vm.listenAndAsk() else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
            contentAlignment = Alignment.Center,
        ) {
            PathIcon(Ic.Mic, Palette.OnLamp, 34.dp)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            suggestions.forEach { q ->
                Text(
                    q,
                    fontSize = 14.sp,
                    color = Palette.Frost,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Palette.Panel)
                        .border(1.dp, Palette.Line, RoundedCornerShape(20.dp))
                        .clickable {
                            Haptics.tick(view)
                            vm.ask(q)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Napisz wiadomość…") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Palette.Lamp,
                    cursorColor = Palette.Lamp,
                ),
            )
            Button(
                onClick = { send() },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Lamp, contentColor = Palette.OnLamp),
            ) { Text("Wyślij", fontWeight = FontWeight.SemiBold) }
        }
        TextButton(onClick = { showHist = !showHist }) {
            Text(if (showHist) "Ukryj historię" else "Historia rozmowy", color = Palette.Lamp)
        }
        if (showHist) {
            Panel {
                if (st.history.isEmpty()) {
                    Text("Na razie pusto.", fontSize = 14.sp, color = Palette.Slate)
                } else {
                    st.history.forEach { (r, m) ->
                        Text(
                            m,
                            fontSize = 15.sp,
                            color = if (r == "u") Palette.Slate else Palette.Frost,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (wide) {
        Row(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                Orb(st.mode, Modifier.size(orbSize))
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                content = controls,
            )
        }
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Orb(st.mode, Modifier.size(orbSize))
            controls()
        }
    }
}
