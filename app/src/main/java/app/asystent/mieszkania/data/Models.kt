package app.asystent.mieszkania.data

data class OutdoorState(
    val tempC: Double? = null,
    val tempSource: String = "",
    val humidity: Double? = null,
    val windKmh: Double? = null,
    val rainPct: Int? = null,
    val code: Int? = null,
    val pm25: Double? = null,
    val pm10: Double? = null,
    val pm25Pct: Double? = null,
    val pm10Pct: Double? = null,
    val airLabel: String = "",
    val airSource: String = "",
    val updated: Long = 0L,
    val error: String? = null,
)

/** Dane z ESP32. Na razie puste, uzupełni je przyszły endpoint. */
data class IndoorState(
    val tempC: Double? = null,
    val humidity: Double? = null,
    val pressure: Double? = null,
    val updated: Long = 0L,
)

data class AdviceState(
    val text: String = "",
    val time: Long = 0L,
    val loading: Boolean = false,
    val error: String? = null,
)

data class LightState(
    val on: Boolean? = null,
    val busy: Boolean = false,
    val error: String? = null,
)

data class AssistantState(
    val mode: String = "idle", // idle, think, speak
    val q: String = "",
    val a: String = "",
    val busy: Boolean = false,
    val note: String = "",
    val history: List<Pair<String, String>> = emptyList(),
)

data class HourPoint(
    val label: String,
    val temp: Double?,
    val hum: Double?,
    val rainPct: Int?,
    val code: Int?,
)

data class DayPoint(
    val label: String,
    val min: Double?,
    val max: Double?,
    val rainPct: Int?,
    val code: Int?,
)

data class ForecastState(
    val hours: List<HourPoint> = emptyList(),
    val nowIndex: Int = 0,
    val days: List<DayPoint> = emptyList(),
    val rainText: String = "",
    val updated: Long = 0L,
)
