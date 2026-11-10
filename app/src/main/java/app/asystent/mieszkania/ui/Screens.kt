package app.asystent.mieszkania.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.Hub
import app.asystent.mieszkania.data.AdviceState
import app.asystent.mieszkania.data.Briefing
import app.asystent.mieszkania.data.Note
import app.asystent.mieszkania.data.Sev
import app.asystent.mieszkania.data.Situation
import app.asystent.mieszkania.data.ForecastState
import app.asystent.mieszkania.data.IndoorState
import app.asystent.mieszkania.data.OutdoorState
import app.asystent.mieszkania.data.weatherText
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.math.max

@Composable
private fun Page(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = ColSpacing,
        content = content,
    )
}

private fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", PL).format(Date(t))

// ======================= START =======================

@Composable
fun StartScreen(vm: Hub) {
    val out by vm.outdoor.collectAsStateWithLifecycle()
    val ind by vm.indoor.collectAsStateWithLifecycle()
    val adv by vm.advice.collectAsStateWithLifecycle()
    val hasKey by vm.hasGeminiKey.collectAsStateWithLifecycle()
    val fc by vm.forecast.collectAsStateWithLifecycle()
    val name = vm.settings.userName.trim()
    val notes by vm.notes.collectAsStateWithLifecycle()
    val sumText = remember(out, ind) { Situation.summary(out, ind) }
    val view = LocalView.current

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val clockFmt = remember { SimpleDateFormat("HH:mm", PL) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", PL) }
    val clock = clockFmt.format(Date(now))
    val date = dateFmt.format(Date(now))
    val hour = clock.substring(0, 2).toInt()
    val hello = when (hour) {
        in 5..11 -> "Dzień dobry"
        in 12..17 -> "Miłego popołudnia"
        in 18..21 -> "Dobry wieczór"
        else -> "Dobrej nocy"
    }
    val greeting = if (name.isEmpty()) "$hello!" else "$hello, $name!"
    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp > cfg.screenHeightDp * 1.25f

    val hero: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(top = 8.dp)) {
            Text(greeting, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Palette.Frost)
            Text(
                clock,
                fontSize = 96.sp,
                lineHeight = 100.sp,
                letterSpacing = (-2).sp,
                fontWeight = FontWeight.Bold,
                color = Palette.Frost,
            )
            Text(date, fontSize = 16.sp, color = Palette.Slate)
        }
        SituationCard(notes, sumText) {
            Haptics.tick(view)
            vm.refreshOutdoor(true)
        }
    }

    val rest: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Na zewnątrz", fmt(out.tempC), "°C", out.tempSource.ifEmpty { "brak danych" }, Modifier.weight(1f).fillMaxHeight())
            Tile("W domu", fmt(ind.tempC), "°C", comfortT(ind), Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile("Wilgotność", fmt(ind.humidity, 0), "%", comfortH(ind), Modifier.weight(1f).fillMaxHeight())
            Tile("Ciśnienie", fmt(ind.pressure, 0), "hPa", comfortP(ind), Modifier.weight(1f).fillMaxHeight())
        }
        WeatherPanel(out)
        AirPanel(out)
        ClapToggleRow(vm)
        OutlinedButton(
            onClick = {
                Haptics.tick(view)
                vm.kiosk.value = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Tryb kiosku (pełny ekran)", color = Palette.Frost, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))
    }

    if (wide) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = ColSpacing, content = hero)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = ColSpacing, content = rest)
        }
    } else {
        Page {
            hero()
            rest()
        }
    }
}

private fun comfortT(i: IndoorState): String = when {
    i.tempC == null -> indoorNote(i)
    i.tempC < 18.0 -> "Chłodno"
    i.tempC <= 24.0 -> "Komfort"
    else -> "Ciepło"
}

private fun comfortH(i: IndoorState): String = when {
    i.humidity == null -> indoorNote(i)
    i.humidity < 30.0 -> "Sucho"
    i.humidity <= 60.0 -> "Optymalna"
    else -> "Wilgotno"
}

private fun comfortP(i: IndoorState): String = when {
    i.pressure == null -> indoorNote(i)
    i.pressure < 1000.0 -> "Niskie"
    i.pressure <= 1025.0 -> "Normalne"
    else -> "Wysokie"
}

private fun indoorNote(i: IndoorState): String =
    if (i.updated == 0L) "Czeka na ESP32" else "Odczyt z ${hhmm(i.updated)}"

@Composable
private fun SituationCard(notes: List<Note>, summary: String, onTap: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    val top = notes.firstOrNull() ?: Note(Sev.OK, "Wszystko w normie.")
    fun col(s: Sev): Color = when (s) {
        Sev.CRIT -> Palette.Alert
        Sev.WARN -> Palette.Lamp
        Sev.OK -> Palette.Signal
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Palette.PanelHigh)
            .border(1.dp, Palette.Line, shape)
            .clickable(onClick = onTap),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(col(top.sev)))
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Caption("Porada dnia", col(top.sev))
            Text(
                top.text,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 6.dp),
            )
            notes.drop(1).take(3).forEach { n ->
                Row(Modifier.padding(top = 8.dp)) {
                    Box(Modifier.padding(top = 8.dp, end = 10.dp).size(8.dp).background(col(n.sev), CircleShape))
                    Text(n.text, fontSize = 15.sp, lineHeight = 21.sp, color = Palette.Frost)
                }
            }
            if (summary.isNotEmpty()) {
                Text(summary, fontSize = 12.sp, color = Palette.Slate, modifier = Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun AdviceCard(adv: AdviceState, hasKey: Boolean, onTap: () -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(Palette.PanelHigh)
            .border(1.dp, Palette.Line, shape)
            .clickable(onClick = onTap),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(Palette.Lamp))
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Caption("Porada dnia", Palette.Lamp)
            val body = when {
                adv.loading -> "Gemini układa poradę…"
                adv.text.isNotEmpty() -> adv.text
                !hasKey -> "Dodaj klucz Gemini w Ustawieniach, żeby tu pojawiła się porada."
                else -> "Klaśnij raz albo stuknij tutaj, żeby dostać poradę."
            }
            Text(
                body,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 6.dp),
            )
            adv.error?.let {
                Text(it, fontSize = 13.sp, color = Palette.Alert, modifier = Modifier.padding(top = 6.dp))
            }
            if (adv.text.isNotEmpty() && adv.time > 0L && !adv.loading) {
                Text(
                    "Gemini, ${hhmm(adv.time)}. Stuknij, żeby odświeżyć.",
                    fontSize = 12.sp,
                    color = Palette.Slate,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun WeatherPanel(out: OutdoorState) {
    Panel {
        Caption("Pogoda na zewnątrz")
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            WeatherGlyph(out.code, 64.dp)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmt(out.tempC),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Frost,
                        softWrap = false,
                    )
                    Text(
                        "°C",
                        fontSize = 20.sp,
                        color = Palette.Slate,
                        softWrap = false,
                        modifier = Modifier.padding(start = 3.dp, bottom = 9.dp),
                    )
                }
                Text(weatherText(out.code).ifEmpty { "Brak opisu" }, fontSize = 15.sp, color = Palette.Slate)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Wiatr: ${fmt(out.windKmh)} km/h", fontSize = 14.sp, color = Palette.Slate)
                Text("Opady: ${out.rainPct?.let { "$it%" } ?: "—"}", fontSize = 14.sp, color = Palette.Slate)
            }
        }
    }
}

@Composable
private fun AirPanel(out: OutdoorState) {
    Panel {
        Caption("Jakość powietrza")
        if (out.pm25 == null && out.pm10 == null) {
            Text(
                "Brak danych o pyłach.",
                fontSize = 15.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            val worst = max(out.pm25Pct ?: 0.0, out.pm10Pct ?: 0.0)
            val tone = when {
                worst < 50.0 -> Palette.Signal
                worst < 100.0 -> Palette.Lamp
                else -> Palette.Alert
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fmt(out.pm25),
                        fontSize = 40.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.Frost,
                    )
                    Text(
                        "µg/m³ PM2,5",
                        fontSize = 14.sp,
                        color = Palette.Slate,
                        modifier = Modifier.padding(start = 6.dp, bottom = 7.dp),
                    )
                }
                if (out.airLabel.isNotEmpty()) Chip(out.airLabel, tone)
            }
            Box(
                Modifier
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Palette.Line),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((worst / 100.0).coerceIn(0.03, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(tone),
                )
            }
            Text(
                "Pasek pokazuje, jaką część normy zajmują pyły.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 6.dp),
            )
            Text(
                "PM2,5: ${fmt(out.pm25)} (${fmt(out.pm25Pct, 0)}% normy), " +
                    "PM10: ${fmt(out.pm10)} (${fmt(out.pm10Pct, 0)}% normy)",
                fontSize = 13.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (out.airSource.isNotEmpty()) {
                Text(
                    "Źródło: ${out.airSource}",
                    fontSize = 12.sp,
                    color = Palette.Slate,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SourceChips(vm: Hub) {
    val src by vm.outdoorSource.collectAsStateWithLifecycle()
    val view = LocalView.current
    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("open" to "Open-Meteo", "locker" to "Paczkomat").forEach { (k, label) ->
            val sel = src == k
            Text(
                label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (sel) Palette.OnLamp else Palette.Frost,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sel) Palette.Lamp else Palette.PanelHigh)
                    .clickable {
                        Haptics.tick(view)
                        vm.setOutdoorSource(k)
                    }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SourceSelector(vm: Hub) {
    Panel {
        Caption("Źródło danych zewnętrznych")
        SourceChips(vm)
        Text(
            "Dotyczy temperatury, wilgotności i pyłów. Gdy wybrane źródło nie odpowie, aplikacja użyje drugiego.",
            fontSize = 12.sp,
            color = Palette.Slate,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ======================= POGODA =======================

@Composable
fun WeatherScreen(vm: Hub) {
    val out by vm.outdoor.collectAsStateWithLifecycle()
    Page {
        SectionTitle("Pogoda")
        val fc by vm.forecast.collectAsStateWithLifecycle()
        SourceSelector(vm)
        WeatherPanel(out)
        RainCard(fc)
        HoursCard(fc)
        TrendCard(fc)
        DaysCard(fc)
        AirPanel(out)
        Panel {
            Caption("Źródła i odświeżanie")
            Text(
                "Temperatura: ${out.tempSource.ifEmpty { "brak" }}. Pyły: ${out.airSource.ifEmpty { "brak" }}. " +
                    "Wiatr i opady: Open-Meteo.",
                fontSize = 14.sp,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 6.dp),
            )
            if (out.updated > 0L) {
                Text(
                    "Ostatnie sprawdzenie: ${hhmm(out.updated)}",
                    fontSize = 13.sp,
                    color = Palette.Slate,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            out.error?.let {
                Text(it, fontSize = 13.sp, color = Palette.Alert, modifier = Modifier.padding(top = 6.dp))
            }
            TextButton(onClick = { vm.refreshOutdoor(true) }) {
                Text("Odśwież teraz", color = Palette.Lamp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ======================= MIESZKANIE =======================

@Composable
fun HomeScreen(vm: Hub) {
    val ind by vm.indoor.collectAsStateWithLifecycle()
    val hist by vm.indoorHistory.collectAsStateWithLifecycle()
    val st by vm.espStatus.collectAsStateWithLifecycle()
    Page {
        SectionTitle("Mieszkanie")
        Panel {
            Caption("Czujnik ESP32")
            Text(
                st.ifEmpty { "Dodaj adres ESP32 w Ustawieniach." },
                fontSize = 15.sp,
                color = if (st.startsWith("Połączono")) Palette.Signal else Palette.Slate,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        IndoorPanel("Temperatura", fmt(ind.tempC), "°C", ind)
        IndoorPanel("Wilgotność", fmt(ind.humidity, 0), "%", ind)
        IndoorPanel("Ciśnienie", fmt(ind.pressure, 0), "hPa", ind)
        HistoryCard(hist)
    }
}

@Composable
private fun IndoorPanel(title: String, value: String, unit: String, ind: IndoorState) {
    Panel {
        Caption(title)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = Palette.Frost)
            Text(
                unit,
                fontSize = 18.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(start = 4.dp, bottom = 7.dp),
            )
        }
        Text(indoorNote(ind), fontSize = 12.sp, color = Palette.Slate)
    }
}

// ======================= ŚWIATŁO =======================

@Composable
fun LightScreen(vm: Hub) {
    val light by vm.light.collectAsStateWithLifecycle()
    val view = LocalView.current
    LaunchedEffect(Unit) { vm.refreshLight() }
    val on = light.on == true
    Page {
        SectionTitle("Światło")
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(if (on) Palette.Lamp else Palette.PanelHigh)
                    .border(2.dp, if (on) Palette.Lamp else Palette.Line, CircleShape)
                    .clickable(enabled = !light.busy) {
                        Haptics.tick(view)
                        vm.toggleLight()
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (light.busy) {
                    CircularProgressIndicator(color = if (on) Palette.OnLamp else Palette.Lamp)
                } else {
                    PathIcon(Ic.Bulb, if (on) Palette.OnLamp else Palette.Slate, 72.dp)
                }
            }
            Text(
                when (light.on) {
                    true -> "Włączone"
                    false -> "Wyłączone"
                    null -> "Stan nieznany"
                },
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Stuknij kółko albo klaśnij dwa razy.",
                fontSize = 14.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
            light.error?.let {
                Text(it, fontSize = 13.sp, color = Palette.Alert, modifier = Modifier.padding(top = 8.dp))
            }
            TextButton(onClick = { vm.refreshLight() }) {
                Text("Odczytaj stan", color = Palette.Lamp, fontWeight = FontWeight.Medium)
            }
        }
        ClapToggleRow(vm)
    }
}

// ======================= USTAWIENIA =======================

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    secret: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboard),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Palette.Lamp,
            focusedLabelColor = Palette.Lamp,
            cursorColor = Palette.Lamp,
        ),
    )
}

@Composable
fun SettingsScreen(vm: Hub) {
    val s = vm.settings
    var name by remember { mutableStateOf(s.userName) }
    var city by remember { mutableStateOf(s.city) }
    var lat by remember { mutableStateOf(s.lat) }
    var lon by remember { mutableStateOf(s.lon) }
    var locker by remember { mutableStateOf(s.lockerUrl) }
    var lockerTemp by remember { mutableStateOf(s.tempFromLocker) }
    var gKey by remember { mutableStateOf(s.geminiKey) }
    var gModel by remember { mutableStateOf(s.geminiModel) }
    var tBase by remember { mutableStateOf(s.tuyaBase) }
    var tId by remember { mutableStateOf(s.tuyaId) }
    var tSecret by remember { mutableStateOf(s.tuyaSecret) }
    var tDev by remember { mutableStateOf(s.tuyaDevice) }
    var sens by remember { mutableStateOf(s.clapSensitivity.toFloat()) }
    var saved by remember { mutableStateOf(false) }
    var espUrl by remember { mutableStateOf(s.espUrl) }
    var sPort by remember { mutableStateOf(s.serverPort) }
    var sPin by remember { mutableStateOf(s.serverPin) }
    var srvOn by remember { mutableStateOf(s.serverEnabled) }
    val theme by vm.themeMode.collectAsStateWithLifecycle()
    val kscale by vm.kioskScale.collectAsStateWithLifecycle()
    val srvUrl by vm.serverUrl.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val view = LocalView.current
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
        vm.setServer(true)
    }
    fun startServer() {
        s.serverPort = sPort
        s.serverPin = sPin
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            vm.setServer(true)
        }
    }
    val level by vm.micLevel.collectAsStateWithLifecycle()
    val listening by vm.clapsOn.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = ColSpacing,
    ) {
        SectionTitle("Ustawienia")

        Panel {
            Caption("Ogólne")
            Spacer(Modifier.height(8.dp))
            Field("Imię w powitaniu", name, { name = it; saved = false })
            Spacer(Modifier.height(8.dp))
            Field("Miasto", city, { city = it; saved = false })
            Spacer(Modifier.height(8.dp))
            Field("Szerokość geograficzna", lat, { lat = it; saved = false }, keyboard = KeyboardType.Decimal)
            Spacer(Modifier.height(8.dp))
            Field("Długość geograficzna", lon, { lon = it; saved = false }, keyboard = KeyboardType.Decimal)
        }

        Panel {
            Caption("Paczkomat (źródło główne)")
            Spacer(Modifier.height(8.dp))
            Field("Pełny adres zapytania", locker, { locker = it; saved = false }, keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(8.dp))
            SourceChips(vm)
            Text(
                "Wybrane źródło dotyczy temperatury, wilgotności i pyłów. Puste pole adresu oznacza tylko Open-Meteo.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Panel {
            Caption("Gemini (porada dnia)")
            Spacer(Modifier.height(8.dp))
            Field("Klucz API", gKey, { gKey = it; saved = false }, secret = true)
            Spacer(Modifier.height(8.dp))
            Field("Nazwa modelu", gModel, { gModel = it; saved = false })
        }

        Panel {
            Caption("Tuya (światło)")
            Spacer(Modifier.height(8.dp))
            Field("Adres serwera", tBase, { tBase = it; saved = false }, keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(8.dp))
            Field("Client ID", tId, { tId = it; saved = false })
            Spacer(Modifier.height(8.dp))
            Field("Client Secret", tSecret, { tSecret = it; saved = false }, secret = true)
            Spacer(Modifier.height(8.dp))
            Field("Device ID", tDev, { tDev = it; saved = false })
        }

        Panel {
            Caption("ESP32 (czujniki w mieszkaniu)")
            Spacer(Modifier.height(8.dp))
            Field("Adres ESP32, np. http://192.168.1.50", espUrl, { espUrl = it; saved = false }, keyboard = KeyboardType.Uri)
            Text(
                "Aplikacja odpytuje adres/api/sensors co minutę i zapisuje historię w telefonie.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Panel {
            Caption("Wygląd")
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("dark" to "Ciemny", "light" to "Jasny", "auto" to "Auto").forEach { (m, label) ->
                    val sel = theme == m
                    Text(
                        label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (sel) Palette.OnLamp else Palette.Frost,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) Palette.Lamp else Palette.PanelHigh)
                            .clickable {
                                Haptics.tick(view)
                                vm.setTheme(m)
                            }
                            .padding(vertical = 12.dp),
                    )
                }
            }
            Text(
                "Rozmiar czcionki w kiosku: $kscale%",
                fontSize = 15.sp,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 12.dp),
            )
            Slider(
                value = kscale.toFloat(),
                onValueChange = { vm.setKioskScale(it.toInt()) },
                valueRange = 60f..150f,
                colors = SliderDefaults.colors(thumbColor = Palette.Lamp, activeTrackColor = Palette.Lamp),
            )
            val hap by vm.hapticsOn.collectAsStateWithLifecycle()
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Wibracje", fontSize = 15.sp, color = Palette.Frost)
                    Text("Reakcje dotykowe, potwierdzenia i ostrzeżenia.", fontSize = 12.sp, color = Palette.Slate)
                }
                Switch(
                    checked = hap,
                    onCheckedChange = {
                        vm.setHaptics(it)
                        if (it) Haptics.tick(view)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Palette.OnLamp, checkedTrackColor = Palette.Lamp),
                )
            }
        }

        Panel {
            Caption("Serwer WWW (podgląd na innych urządzeniach)")
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Uruchom serwer", fontSize = 15.sp, color = Palette.Frost, modifier = Modifier.weight(1f))
                Switch(
                    checked = srvOn,
                    onCheckedChange = { want ->
                        srvOn = want
                        if (want) startServer() else vm.setServer(false)
                    },
                    colors = SwitchDefaults.colors(checkedThumbColor = Palette.OnLamp, checkedTrackColor = Palette.Lamp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Field("Port", sPort, { sPort = it; saved = false }, keyboard = KeyboardType.Number)
            Spacer(Modifier.height(8.dp))
            Field("PIN (puste = bez PIN-u)", sPin, { sPin = it; saved = false }, secret = true)
            Text(
                if (srvUrl.isNotEmpty()) "Adres: $srvUrl" else "Serwer wyłączony.",
                fontSize = 14.sp,
                color = if (srvUrl.startsWith("http")) Palette.Signal else Palette.Slate,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                "Po zmianie portu lub PIN-u wyłącz i włącz serwer. Działa tylko w tej samej sieci WiFi.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Panel {
            Caption("Klaśnięcia")
            Text(
                "Czułość: ${sens.toInt()}",
                fontSize = 15.sp,
                color = Palette.Frost,
                modifier = Modifier.padding(top = 6.dp),
            )
            Slider(
                value = sens,
                onValueChange = { sens = it; vm.setSensitivity(it.toInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Palette.Lamp, activeTrackColor = Palette.Lamp),
            )
            LinearProgressIndicator(
                progress = { (level / 8000f).coerceIn(0f, 1f) },
                color = Palette.Lamp,
                trackColor = Palette.Line,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (listening) "Poziom dźwięku: $level. Większa czułość wykrywa cichsze klaśnięcia."
                else "Włącz wykrywanie klaśnięć na ekranie Start, żeby zobaczyć poziom dźwięku.",
                fontSize = 12.sp,
                color = Palette.Slate,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Button(
            onClick = {
                s.userName = name
                s.city = city
                s.lat = lat
                s.lon = lon
                s.lockerUrl = locker
                s.tempFromLocker = lockerTemp
                s.geminiKey = gKey
                s.geminiModel = gModel
                s.tuyaBase = tBase
                s.tuyaId = tId
                s.tuyaSecret = tSecret
                s.tuyaDevice = tDev
                s.serverPort = sPort
                s.serverPin = sPin
                s.espUrl = espUrl
                vm.onSettingsSaved()
                Haptics.confirm(view)
                saved = true
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Palette.Lamp, contentColor = Palette.OnLamp),
        ) {
            Text("Zapisz ustawienia", fontWeight = FontWeight.SemiBold)
        }
        if (saved) {
            Text("Zapisano.", fontSize = 14.sp, color = Palette.Signal)
        }
        Text(
            "Klucze są zapisane tylko na tym telefonie.",
            fontSize = 12.sp,
            color = Palette.Slate,
        )
        Spacer(Modifier.height(12.dp))
    }
}
