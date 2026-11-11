package app.asystent.mieszkania

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import app.asystent.mieszkania.data.AssistantState
import app.asystent.mieszkania.server.ServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.NetworkInterface
import app.asystent.mieszkania.audio.ClapDetector
import app.asystent.mieszkania.audio.Speaker
import app.asystent.mieszkania.audio.VoiceRecorder
import app.asystent.mieszkania.data.AdviceState
import app.asystent.mieszkania.data.ForecastState
import app.asystent.mieszkania.data.Forecasts
import app.asystent.mieszkania.data.Briefing
import app.asystent.mieszkania.data.IndoorStore
import app.asystent.mieszkania.data.Gemini
import app.asystent.mieszkania.data.IndoorState
import app.asystent.mieszkania.data.LightState
import app.asystent.mieszkania.data.Locker
import app.asystent.mieszkania.data.Net
import app.asystent.mieszkania.data.Note
import app.asystent.mieszkania.data.Sev
import app.asystent.mieszkania.data.Situation
import app.asystent.mieszkania.ui.Haptics
import app.asystent.mieszkania.data.LockerSource
import app.asystent.mieszkania.data.OpenMeteo
import app.asystent.mieszkania.data.OutdoorState
import app.asystent.mieszkania.data.Air
import app.asystent.mieszkania.data.Settings
import app.asystent.mieszkania.data.TuyaClient
import app.asystent.mieszkania.data.Weather
import app.asystent.mieszkania.data.weatherText
import app.asystent.mieszkania.ui.fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class Hub private constructor(private val app: Application) {
    companion object {
        @Volatile
        private var inst: Hub? = null
        fun get(app: Application): Hub =
            inst ?: synchronized(this) { inst ?: Hub(app).also { inst = it } }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = Settings(app)
    private val pl: Locale = Locale.forLanguageTag("pl-PL")

    private val _outdoor = MutableStateFlow(OutdoorState())
    val outdoor: StateFlow<OutdoorState> = _outdoor.asStateFlow()

    private val _indoor = MutableStateFlow(IndoorState())
    val indoor: StateFlow<IndoorState> = _indoor.asStateFlow()

    private val _advice = MutableStateFlow(AdviceState(settings.adviceText, settings.adviceTime))
    val advice: StateFlow<AdviceState> = _advice.asStateFlow()

    private val _light = MutableStateFlow(LightState())
    val light: StateFlow<LightState> = _light.asStateFlow()

    val hasGeminiKey = MutableStateFlow(settings.geminiKey.isNotBlank())
    val clapsOn = MutableStateFlow(false)
    val micLevel = MutableStateFlow(0)
    val clapDiag = MutableStateFlow("")
    val clapInfo = MutableStateFlow("")
    val kiosk = MutableStateFlow(false)

    private val detector = ClapDetector(
        onClaps = { n -> scope.launch { handleClaps(n) } },
        onLevel = { micLevel.value = it },
        onDiag = { clapDiag.value = it },
    )

    private var foreground = false
    @Volatile private var serverUp = false
    private val outdoorMutex = Mutex()
    private var omWeather: Weather? = null
    private var omAir: Air? = null
    private var omAt = 0L
    private var locker: Locker? = null
    private var lockerAt = 0L
    private var tuya: TuyaClient? = null
    private var tuyaKey = ""

    init {
        scope.launch {
            while (true) {
                if (foreground || serverUp) {
                    refreshOutdoor(false)
                    refreshBriefing(false)
                }
                delay(5 * 60_000L)
            }
        }
    }

    // ---------- cykl życia ----------

    fun onForeground(micGranted: Boolean) {
        foreground = true
        if (settings.clapsEnabled && micGranted) startDetector()
        refreshOutdoor(false)
        refreshBriefing(false)
    }

    fun onBackground() {
        foreground = false
        detector.stop()
        clapsOn.value = false
    }

    // ---------- klaśnięcia ----------

    private fun startDetector() {
        detector.sensitivity = settings.clapSensitivity
        detector.start(scope)
        clapsOn.value = true
    }

    fun startClaps() {
        settings.clapsEnabled = true
        startDetector()
    }

    fun stopClaps() {
        settings.clapsEnabled = false
        detector.stop()
        clapsOn.value = false
    }

    fun setSensitivity(v: Int) {
        settings.clapSensitivity = v
        detector.sensitivity = v
    }

    private fun handleClaps(n: Int) {
        val time = SimpleDateFormat("HH:mm:ss", pl).format(Date())
        clapInfo.value = "Ostatnie wykrycie: $n× o $time"
        when (n) {
            1 -> Haptics.pulse(app, 0, 40)
            2 -> Haptics.pulse(app, 0, 40, 80, 40)
        }
        when (n) {
            1 -> {
                refreshOutdoor(true)
                refreshBriefing(true)
            }
            2 -> toggleLight()
        }
    }

    // ---------- pogoda i powietrze ----------

    fun refreshOutdoor(force: Boolean = false) {
        scope.launch(Dispatchers.IO) {
            outdoorMutex.withLock { doRefreshOutdoor(force) }
        }
    }

    private fun doRefreshOutdoor(force: Boolean) {
        val lat = settings.lat.trim().toDoubleOrNull()
        val lon = settings.lon.trim().toDoubleOrNull()
        if (lat == null || lon == null) {
            _outdoor.update { it.copy(error = "Niepoprawne współrzędne. Popraw je w Ustawieniach.") }
            return
        }
        val now = System.currentTimeMillis()

        if (force || now - omAt > 15 * 60_000L) {
            OpenMeteo.weather(lat, lon)?.let { omWeather = it }
            OpenMeteo.air(lat, lon)?.let { omAir = it }
            Forecasts.fetch(lat, lon)?.let { _forecast.value = it.copy(updated = now) }
            omAt = now
        }

        val url = settings.lockerUrl.trim()
        if (url.isEmpty()) {
            locker = null
        } else {
            val limit = (if (force) 10 else 60) * 60_000L
            if (now - lockerAt > limit) {
                lockerAt = now
                val r = LockerSource.fetch(url)
                if (r == null) {
                    lockerAt = now - 45 * 60_000L // ponowna próba za ok. 15 minut
                    locker = null
                } else {
                    locker = r
                }
            }
        }

        val lk = locker
        val w = omWeather
        val a = omAir

        val preferLocker = settings.outdoorSource == "locker"
        val lkTemp = lk?.temp
        val lkHum = lk?.hum
        val omTemp = w?.tempC
        val omHum = w?.humidity
        val useLkTemp = lkTemp != null && (preferLocker || omTemp == null)
        val temp = if (useLkTemp) lkTemp else omTemp
        val tempSrc = when {
            temp == null -> ""
            useLkTemp -> "Paczkomat"
            else -> "Open-Meteo"
        }
        val useLkHum = lkHum != null && (preferLocker || omHum == null)
        val hum = if (useLkHum) lkHum else omHum

        val lk25 = lk?.pm25
        val lk10 = lk?.pm10
        val lkAir = lk25 != null || lk10 != null
        val useLkAir = lkAir && (preferLocker || a == null)
        val pm25 = if (useLkAir) (lk25 ?: a?.pm25) else a?.pm25
        val pm10 = if (useLkAir) (lk10 ?: a?.pm10) else a?.pm10
        val pct25 = if (useLkAir && lk25 != null) (lk?.pm25Pct ?: (lk25 / 25.0 * 100.0)) else a?.pm25Pct
        val pct10 = if (useLkAir && lk10 != null) (lk?.pm10Pct ?: (lk10 / 50.0 * 100.0)) else a?.pm10Pct
        val airLabel = if (useLkAir) (LockerSource.levelPl(lk?.level) ?: a?.label ?: "") else (a?.label ?: "")
        val airSrc = when {
            useLkAir -> "Paczkomat"
            a != null -> "Open-Meteo"
            else -> ""
        }

        val noData = temp == null && pm25 == null && pm10 == null
        _outdoor.value = OutdoorState(
            tempC = temp,
            tempSource = tempSrc,
            humidity = hum,
            windKmh = w?.windKmh,
            rainPct = w?.rainPct,
            code = w?.code,
            pm25 = pm25,
            pm10 = pm10,
            pm25Pct = pct25,
            pm10Pct = pct10,
            airLabel = airLabel,
            airSource = airSrc,
            updated = now,
            error = if (noData) "Brak danych pogodowych. Sprawdź połączenie z internetem." else null,
        )
    }

    // ---------- porada dnia (Gemini) ----------

    fun refreshAdvice(force: Boolean) {
        val key = settings.geminiKey.trim()
        if (key.isEmpty()) {
            if (force) _advice.update { it.copy(error = "Dodaj klucz Gemini w Ustawieniach.") }
            return
        }
        val now = System.currentTimeMillis()
        if (!force && now - _advice.value.time < 3 * 3600_000L) return
        if (_advice.value.loading) return
        _advice.update { it.copy(loading = true, error = null) }
        val prompt = buildPrompt()
        scope.launch(Dispatchers.IO) {
            try {
                val text = Gemini.advice(key, settings.geminiModel.trim(), prompt)
                settings.adviceText = text
                settings.adviceTime = now
                _advice.value = AdviceState(text, now)
            } catch (e: Exception) {
                _advice.update { it.copy(loading = false, error = e.message ?: "Nie udało się pobrać porady.") }
            }
        }
    }

    private fun buildPrompt(): String {
        val cal = Calendar.getInstance()
        val hh = String.format(pl, "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val day = SimpleDateFormat("EEEE, d MMMM", pl).format(cal.time)
        val o = _outdoor.value
        val ctx = buildString {
            append("Jest $day, godzina $hh. ")
            o.tempC?.let { append("Na zewnątrz jest ${fmt(it)} stopni. ") }
            if (o.code != null) append("Pogoda: ${weatherText(o.code)}. ")
            if (o.airLabel.isNotBlank()) append("Jakość powietrza: ${o.airLabel}. ")
        }
        return "Jesteś asystentem domowym mieszkańca. $ctx" +
            "Podaj jedną krótką, praktyczną poradę na teraz (maksymalnie 20 słów). " +
            "Odpowiedz po polsku, bez wstępu, bez emotikon i bez cudzysłowów."
    }

    // ---------- światło (Tuya) ----------

    private fun tuyaClient(): TuyaClient? {
        val base = settings.tuyaBase.trim()
        val id = settings.tuyaId.trim()
        val secret = settings.tuyaSecret.trim()
        val dev = settings.tuyaDevice.trim()
        if (base.isEmpty() || id.isEmpty() || secret.isEmpty() || dev.isEmpty()) return null
        val key = "$base|$id|$secret|$dev"
        if (tuya == null || key != tuyaKey) {
            tuya = TuyaClient(base, id, secret, dev)
            tuyaKey = key
        }
        return tuya
    }

    fun toggleLight() {
        if (_light.value.busy) return
        val c = tuyaClient()
        if (c == null) {
            _light.update { it.copy(error = "Uzupełnij dane Tuya w Ustawieniach.") }
            return
        }
        _light.update { it.copy(busy = true, error = null) }
        val last = _light.value.on
        scope.launch(Dispatchers.IO) {
            try {
                val target = c.toggle(last)
                _light.value = LightState(on = target)
                Haptics.pulse(app, 0, 50)
            } catch (e: Exception) {
                _light.update { it.copy(busy = false, error = e.message ?: "Błąd połączenia z Tuya.") }
                Haptics.pulse(app, 0, 60, 80, 60)
            }
        }
    }

    fun refreshLight() {
        if (_light.value.busy) return
        val c = tuyaClient() ?: return
        _light.update { it.copy(busy = true, error = null) }
        scope.launch(Dispatchers.IO) {
            try {
                val s = c.getState()
                _light.value = LightState(on = s, error = if (s == null) "Nie udało się odczytać stanu światła." else null)
            } catch (e: Exception) {
                _light.update { it.copy(busy = false, error = e.message ?: "Błąd połączenia z Tuya.") }
                Haptics.pulse(app, 0, 60, 80, 60)
            }
        }
    }

    // ---------- ustawienia ----------

    fun onSettingsSaved() {
        hasGeminiKey.value = settings.geminiKey.isNotBlank()
        tuya = null
        tuyaKey = ""
        omAt = 0L
        lockerAt = 0L
        detector.sensitivity = settings.clapSensitivity
        refreshOutdoor(true)
        scope.launch(Dispatchers.IO) { pollEsp() }
    }

    // ---------- światło: ustawienie wprost ----------

    fun setLight(on: Boolean) {
        val c = tuyaClient()
        if (c == null) {
            _light.update { it.copy(error = "Uzupełnij dane Tuya w Ustawieniach.") }
            return
        }
        _light.update { it.copy(busy = true, error = null) }
        scope.launch(Dispatchers.IO) {
            try {
                c.setState(on)
                _light.value = LightState(on = on)
                Haptics.pulse(app, 0, 50)
            } catch (e: Exception) {
                _light.update { it.copy(busy = false, error = e.message ?: "Błąd połączenia z Tuya.") }
                Haptics.pulse(app, 0, 60, 80, 60)
            }
        }
    }

    // ---------- asystent (tekstowy) ----------

    val assistant = MutableStateFlow(AssistantState())

    private val speaker = Speaker(app)

    private fun buildContext(): String {
        val o = _outdoor.value
        val i = _indoor.value
        val fc = _forecast.value
        return buildString {
            append("Teraz: ${SimpleDateFormat("EEEE, d MMMM yyyy, 'godz.' HH:mm", pl).format(Date())}. ")
            append("Miejsce użytkownika: ${settings.city.ifBlank { "Polska" }}, Polska.\n")
            val now1 = listOfNotNull(
                o.tempC?.let { "${fmt(it)}°C" },
                o.code?.let { weatherText(it).lowercase(pl) },
                o.windKmh?.let { "wiatr ${fmt(it, 0)} km/h" },
                o.rainPct?.let { "szansa opadów $it%" },
            )
            if (now1.isNotEmpty()) append("Pogoda teraz: ${now1.joinToString(", ")}.\n")
            if (o.pm25 != null || o.pm10 != null) {
                append("Jakość powietrza: ${o.airLabel.ifEmpty { "brak oceny" }} (PM2,5 ${fmt(o.pm25)}, PM10 ${fmt(o.pm10)} µg/m³).\n")
            }
            if (fc.hours.isNotEmpty()) {
                val hrs = fc.hours.drop(fc.nowIndex).take(25).filterIndexed { k, _ -> k % 3 == 0 }
                    .joinToString("; ") { "${it.label} ${fmt(it.temp, 0)}°C, opady ${it.rainPct ?: 0}%" }
                append("Prognoza godzinowa na 24 h: $hrs.\n")
            }
            if (fc.days.isNotEmpty()) {
                val ds = fc.days.joinToString("; ") { "${it.label} ${fmt(it.min, 0)} do ${fmt(it.max, 0)}°C, opady ${it.rainPct ?: 0}%" }
                append("Prognoza na kolejne dni: $ds.\n")
            }
            if (i.tempC != null || i.humidity != null) {
                append("Czujniki w domu: ${fmt(i.tempC)}°C, wilgotność ${fmt(i.humidity, 0)}%, ciśnienie ${fmt(i.pressure, 0)} hPa.\n")
            }
            val light = when (_light.value.on) {
                true -> "włączone"
                false -> "wyłączone"
                null -> "stan nieznany"
            }
            append("Światło w domu: $light.\n")
        }
    }

    /** Pytanie jest na pierwszym miejscu. Dane z domu to tylko pomocniczy kontekst na końcu. */
    private fun buildAskPrompt(q: String): String {
        val hist = assistant.value.history.takeLast(6)
            .joinToString("\n") { (r, t) -> (if (r == "u") "Użytkownik: " else "Asystent: ") + t }
        return (if (hist.isNotEmpty()) "Dotychczasowa rozmowa:\n$hist\n\n" else "") +
            "Pytanie użytkownika: $q\n\n" +
            "Kontekst pomocniczy (użyj go tylko wtedy, gdy pytanie dotyczy domu, pogody lub czasu):\n${buildContext()}"
    }

    private fun systemPrompt(): String =
        ASSISTANT_SYSTEM.replace("{imie}", settings.assistantName.ifBlank { "Laura" })

    private fun lightIntent(low: String): Boolean? {
        val off = low.contains("wyłącz") || low.contains("zgaś")
        val on = low.contains("włącz") || low.contains("zapal")
        return if (low.contains("światł") && (on || off)) !off else null
    }

    private fun speakText(text: String) {
        speaker.speak(
            scope, text, settings.ttsGemini, settings.geminiKey.trim(), settings.ttsModel.trim(), settings.ttsVoice,
            onStart = { assistant.update { it.copy(mode = "speak") } },
            onDone = { assistant.update { it.copy(mode = "idle", busy = false) } },
            onNote = { n -> assistant.update { it.copy(note = n) } },
        )
    }

    private fun finishAsk(q: String, a: String, note: String, speak: Boolean) {
        assistant.update { it.copy(q = q, a = a, note = note, mode = "speak", history = it.history + ("u" to q) + ("a" to a)) }
        if (speak && settings.speakReplies) {
            speakText(a)
        } else {
            scope.launch {
                delay(3000)
                assistant.update { it.copy(mode = "idle", busy = false) }
            }
        }
    }

    fun testVoice() {
        speaker.stop()
        speakText("Cześć, tu ${settings.assistantName.ifBlank { "Laura" }}. Tak brzmi mój głos. Mogę odpowiadać na pytania o wszystko, co znajdę w internecie.")
    }

    fun ask(q: String, speak: Boolean = true) {
        val text = q.trim()
        val mode = assistant.value.mode
        if (text.isEmpty() || mode == "think" || mode == "listen") return
        speaker.stop()
        assistant.update { it.copy(q = text, a = "", note = "", busy = true, mode = "think") }
        val li = lightIntent(text.lowercase(pl))
        if (li != null) {
            setLight(li)
            finishAsk(text, "Wysyłam polecenie do światła.", "", speak)
            return
        }
        val key = settings.geminiKey.trim()
        if (key.isEmpty()) {
            assistant.update { it.copy(a = "Dodaj klucz Gemini w Ustawieniach.", mode = "idle", busy = false) }
            return
        }
        val prompt = buildAskPrompt(text)
        val model = settings.geminiModel.trim()
        val system = systemPrompt()
        scope.launch(Dispatchers.IO) {
            try {
                val r = Gemini.askFull(key, model, system, Gemini.textParts(prompt))
                finishAsk(text, r.text, r.note, speak)
            } catch (e: Exception) {
                assistant.update { it.copy(a = e.message ?: "Błąd asystenta.", mode = "idle", busy = false) }
            }
        }
    }

    /** Mikrofon: nagrywa pytanie, wysyła do Gemini (rozumie mowę i odpowiada z wyszukiwarką), mówi odpowiedź. */
    fun listenAndAsk(speak: Boolean = true) {
        val cur = assistant.value.mode
        if (cur == "listen") {
            VoiceRecorder.stopRequested = true
            return
        }
        if (cur == "think") return
        val key = settings.geminiKey.trim()
        if (key.isEmpty()) {
            assistant.update { it.copy(q = "", a = "Dodaj klucz Gemini w Ustawieniach.", note = "") }
            return
        }
        speaker.stop()
        detector.stop()
        clapsOn.value = false
        assistant.update { it.copy(q = "", a = "", note = "", mode = "listen", busy = true) }
        scope.launch(Dispatchers.IO) {
            val pcm = try {
                VoiceRecorder.capture { micLevel.value = it }
            } catch (e: Exception) {
                null
            }
            if (settings.clapsEnabled && foreground) startDetector()
            if (pcm == null) {
                assistant.update { it.copy(a = "Nie usłyszałam nic. Spróbuj jeszcze raz.", mode = "idle", busy = false) }
                return@launch
            }
            assistant.update { it.copy(mode = "think") }
            try {
                val r = Gemini.askAudio(
                    key, settings.geminiModel.trim(), systemPrompt() + AUDIO_RULES,
                    VoiceRecorder.wav(pcm), buildAskPrompt("(pytanie w nagraniu)"),
                )
                var q = ""
                var ans = r.text
                val m = Regex("(?s)^\\s*Q:\\s*(.*?)\\s*\\n\\s*A:\\s*(.*)$").find(r.text)
                if (m != null) {
                    q = m.groupValues[1].trim()
                    ans = m.groupValues[2].trim()
                }
                if (q.isEmpty()) q = "(pytanie głosowe)"
                val li = lightIntent(q.lowercase(pl))
                if (li != null) {
                    setLight(li)
                    finishAsk(q, "Wysyłam polecenie do światła.", "", speak)
                } else {
                    finishAsk(q, ans, r.note, speak)
                }
            } catch (e: Exception) {
                assistant.update { it.copy(a = e.message ?: "Błąd asystenta.", mode = "idle", busy = false) }
            }
        }
    }

    private val _forecast = MutableStateFlow(ForecastState())
    val forecast: StateFlow<ForecastState> = _forecast.asStateFlow()

    // ---------- źródło danych zewnętrznych ----------

    val outdoorSource = MutableStateFlow(settings.outdoorSource)

    fun setOutdoorSource(s: String) {
        if (s != "open" && s != "locker") return
        settings.outdoorSource = s
        outdoorSource.value = s
        refreshOutdoor(false)
    }

    // ---------- czujniki ESP32 i historia ----------

    private val store = IndoorStore(File(app.filesDir, "indoor_history.csv"))
    val indoorHistory = MutableStateFlow(store.all())
    val espStatus = MutableStateFlow("")

    private fun pollEsp() {
        val base = settings.espUrl.trim().trimEnd('/')
        if (base.isEmpty()) {
            espStatus.value = ""
            return
        }
        try {
            val r = Net.request("$base/api/sensors", timeoutMs = 5000)
            if (r.code != 200) {
                espStatus.value = "ESP32 odpowiedział kodem ${r.code}"
                return
            }
            val j = JSONObject(r.body)
            fun d(k: String): Double? = if (j.has(k) && !j.isNull(k)) j.optDouble(k).takeUnless { it.isNaN() } else null
            val t = d("temp")
            val hm = d("hum")
            val p = d("press")
            val now = System.currentTimeMillis()
            _indoor.value = IndoorState(t, hm, p, now)
            espStatus.value = "Połączono z ESP32"
            if (store.add(IndoorStore.Sample(now, t, hm, p))) indoorHistory.value = store.all()
        } catch (e: Exception) {
            espStatus.value = "Brak odpowiedzi ESP32"
        }
    }

    init {
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (foreground || serverUp) pollEsp()
                delay(60_000L)
            }
        }
    }

    fun historyJson(): String {
        val all = store.all()
        val step = maxOf(1, all.size / 400)
        val n = JSONObject.NULL
        val arr = JSONArray()
        all.filterIndexed { i, _ -> i % step == 0 }.forEach {
            arr.put(JSONObject().put("t", it.t).put("tp", it.temp ?: n).put("h", it.hum ?: n).put("p", it.press ?: n))
        }
        return arr.toString()
    }

    // ---------- porada dnia: priorytetowe uwagi z bieżących danych ----------

    val notes: StateFlow<List<Note>> = combine(_outdoor, _indoor, _forecast, indoorHistory, espStatus) { o, i, f, hs, st ->
        Situation.build(o, i, f, hs, settings.espUrl.isNotBlank(), st.startsWith("Połączono"), System.currentTimeMillis())
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // krótki alarm wibracją, gdy pojawi się nowa sytuacja krytyczna
        scope.launch {
            var seen: String? = null
            notes.collect { list ->
                val key = list.filter { it.sev == Sev.CRIT }.joinToString("|") { it.text }
                if (seen != null && key.isNotEmpty() && key != seen && foreground) {
                    Haptics.pulse(app, 0, 90, 70, 90)
                }
                seen = key
            }
        }
    }

    // ---------- krótki briefing dnia: Polska i świat (Gemini + wyszukiwarka) ----------

    private fun parseNews(t: String): List<String> =
        t.lines()
            .map { it.trim().trimStart('-', '*', '•').trim().replace(Regex("\\[\\d+(,\\s*\\d+)*]"), "").trim() }
            .filter { it.isNotBlank() }
            .take(3)

    val news = MutableStateFlow(parseNews(settings.briefText))
    private var newsBusy = false

    fun refreshBriefing(force: Boolean) {
        val key = settings.geminiKey.trim()
        if (key.isEmpty() || newsBusy) return
        val age = System.currentTimeMillis() - settings.briefTime
        if (!force && age < 4 * 3600_000L) return
        if (force && age < 5 * 60_000L) return
        newsBusy = true
        val model = settings.geminiModel.trim()
        scope.launch(Dispatchers.IO) {
            try {
                val day = SimpleDateFormat("EEEE, d MMMM yyyy", pl).format(Date())
                val text = Gemini.ask(key, model, BRIEF_SYSTEM, "Dzisiaj jest $day. Przygotuj briefing.", true)
                settings.briefText = text
                settings.briefTime = System.currentTimeMillis()
                news.value = parseNews(text)
            } catch (e: Exception) {
                // zostaje poprzedni briefing
            } finally {
                newsBusy = false
            }
        }
    }

    // ---------- wibracje ----------

    val hapticsOn = MutableStateFlow(settings.haptics)

    init {
        Haptics.enabled = settings.haptics
    }

    fun setHaptics(b: Boolean) {
        settings.haptics = b
        hapticsOn.value = b
        Haptics.enabled = b
    }

    // ---------- wygląd ----------

    val themeMode = MutableStateFlow(settings.themeMode)
    val kioskScale = MutableStateFlow(settings.kioskScale)

    fun setTheme(m: String) {
        settings.themeMode = m
        themeMode.value = m
    }

    fun setKioskScale(p: Int) {
        val v = p.coerceIn(60, 150)
        settings.kioskScale = v
        kioskScale.value = v
    }

    // ---------- serwer WWW ----------

    val serverUrl = MutableStateFlow("")

    private fun localIp(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress && it.hostAddress?.contains(':') == false }
            ?.hostAddress
    } catch (e: Exception) {
        null
    }

    fun setServer(on: Boolean) {
        settings.serverEnabled = on
        val i = Intent(app, ServerService::class.java)
        if (on) ContextCompat.startForegroundService(app, i) else app.stopService(i)
    }

    fun ensureServer() {
        if (settings.serverEnabled && !serverUp) setServer(true)
    }

    fun onServerStarted(port: Int) {
        serverUp = true
        serverUrl.value = "http://${localIp() ?: "adres-telefonu"}:$port"
        refreshOutdoor(false)
    }

    fun onServerStopped(err: String? = null) {
        serverUp = false
        serverUrl.value = if (err != null) "Błąd serwera: $err" else ""
    }

    fun pinOk(given: String?): Boolean {
        val p = settings.serverPin.trim()
        return p.isEmpty() || p == given
    }

    fun stateJson(): String {
        val o = _outdoor.value
        val i = _indoor.value
        val a = _advice.value
        val l = _light.value
        val s = assistant.value
        val n = JSONObject.NULL
        val fc = _forecast.value
        val hoursJ = JSONArray()
        fc.hours.forEach {
            hoursJ.put(
                JSONObject().put("l", it.label).put("t", it.temp ?: n).put("h", it.hum ?: n)
                    .put("r", it.rainPct ?: n).put("c", it.code ?: n),
            )
        }
        val daysJ = JSONArray()
        fc.days.forEach {
            daysJ.put(
                JSONObject().put("l", it.label).put("min", it.min ?: n).put("max", it.max ?: n)
                    .put("r", it.rainPct ?: n).put("c", it.code ?: n),
            )
        }
        val notesJ = JSONArray()
        notes.value.forEach { notesJ.put(JSONObject().put("s", it.sev.ordinal).put("t", it.text)) }
        val hist = JSONArray()
        s.history.forEach { (r, t) -> hist.put(JSONObject().put("r", r).put("t", t)) }
        return JSONObject()
            .put("userName", settings.userName)
            .put("outdoorSource", settings.outdoorSource)
            .put("notes", notesJ)
            .put("news", JSONArray(news.value))
            .put("summary", Situation.summary(o, i))
            .put("espStatus", espStatus.value)
            .put("briefing", Briefing.make(o.tempC, o.code, _forecast.value))
            .put(
                "forecast",
                JSONObject().put("hours", hoursJ).put("nowIndex", fc.nowIndex)
                    .put("days", daysJ).put("rainText", fc.rainText),
            )
            .put("city", settings.city)
            .put(
                "outdoor",
                JSONObject()
                    .put("tempC", o.tempC ?: n).put("tempSource", o.tempSource)
                    .put("humidity", o.humidity ?: n).put("windKmh", o.windKmh ?: n)
                    .put("rainPct", o.rainPct ?: n).put("code", o.code ?: n)
                    .put("weatherText", weatherText(o.code))
                    .put("pm25", o.pm25 ?: n).put("pm10", o.pm10 ?: n)
                    .put("pm25Pct", o.pm25Pct ?: n).put("pm10Pct", o.pm10Pct ?: n)
                    .put("airLabel", o.airLabel).put("airSource", o.airSource)
                    .put("updated", o.updated).put("error", o.error ?: n),
            )
            .put(
                "indoor",
                JSONObject().put("tempC", i.tempC ?: n).put("humidity", i.humidity ?: n)
                    .put("pressure", i.pressure ?: n).put("updated", i.updated),
            )
            .put(
                "advice",
                JSONObject().put("text", a.text).put("time", a.time)
                    .put("loading", a.loading).put("error", a.error ?: n),
            )
            .put("light", JSONObject().put("on", l.on ?: n).put("busy", l.busy).put("error", l.error ?: n))
            .put(
                "assistant",
                JSONObject().put("mode", s.mode).put("q", s.q).put("a", s.a)
                    .put("busy", s.busy).put("note", s.note).put("history", hist),
            )
            .toString()
    }
}

private const val ASSISTANT_SYSTEM =
    "Jesteś {imie}, osobistą asystentką głosową użytkownika, taką jak Asystent Google, ale mądrzejszą. " +
        "Odpowiadasz na KAŻDE pytanie, na dowolny temat: wiedza ogólna, wiadomości z Polski i świata, sport, " +
        "finanse i kursy, nauka, technologia, zdrowie, prawo, gotowanie, podróże, kultura, tłumaczenia, obliczenia, " +
        "pogoda, dom i wszystko inne, o co ktoś zapyta. " +
        "Masz dostęp do wyszukiwarki Google i MASZ z niej korzystać za każdym razem, gdy pytanie dotyczy czegoś aktualnego " +
        "lub zmiennego (wiadomości, wydarzenia, wyniki, ceny, kursy, godziny otwarcia, osoby, firmy, premiery, prawo) " +
        "albo gdy nie jesteś pewna odpowiedzi. Dane z domu (pogoda, czujniki, światło) dostajesz tylko jako dodatkowy kontekst: " +
        "użyj ich wyłącznie wtedy, gdy pytanie ich dotyczy. W innych sprawach pomijaj je całkowicie i nie wspominaj ani " +
        "o pogodzie, ani o mieszkaniu. Nigdy nie mów, że nie możesz odpowiedzieć albo że nie masz dostępu do danych: " +
        "poszukaj w internecie. Odpowiadaj po polsku, językiem mówionym, zwięźle (zwykle 1 do 4 zdań, przy złożonych " +
        "pytaniach do 6), bez emotikon, bez markdown, bez wypunktowań i bez adresów internetowych. " +
        "Liczby, daty i jednostki zapisuj tak, jak czyta się je na głos."

private const val AUDIO_RULES =
    " Użytkownik zadał pytanie GŁOSEM (nagranie dźwiękowe). Format odpowiedzi: pierwsza linia zaczyna się od 'Q: ' " +
        "i zawiera dokładną transkrypcję tego, co powiedział, druga linia zaczyna się od 'A: ' i zawiera Twoją odpowiedź. " +
        "Jeśli nagranie jest niezrozumiałe, w 'Q:' wpisz 'niezrozumiałe', a w 'A:' poproś krótko o powtórzenie."

private const val BRIEF_SYSTEM =
    "Piszesz bardzo krótki dzienny briefing do panelu w domu. Użyj wyszukiwarki Google i podaj dokładnie dwie linie: " +
        "najpierw 'Polska: ' i jedno najważniejsze lub najciekawsze wydarzenie z Polski z ostatnich 24 godzin, " +
        "potem 'Świat: ' i jedno z całego świata. Każda linia to jedno zdanie, maksymalnie 20 słów, konkretne " +
        "(kto, co, gdzie), neutralne w tonie. Bez wstępów, bez porad, bez ogólników, bez emotikon, " +
        "bez markdown i bez przypisów."
