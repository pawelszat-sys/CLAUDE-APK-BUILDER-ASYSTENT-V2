package app.asystent.mieszkania.data

import java.io.File

/** Historia odczytów z ESP32 zapisana w pliku w telefonie (jeden odczyt co 5 minut, 8 dni). */
class IndoorStore(private val file: File) {
    data class Sample(val t: Long, val temp: Double?, val hum: Double?, val press: Double?)

    private val list = ArrayList<Sample>()
    private val keepMs = 8L * 24 * 3600_000L

    init {
        try {
            if (file.exists()) {
                val cutoff = System.currentTimeMillis() - keepMs
                file.forEachLine { ln -> parse(ln)?.let { if (it.t >= cutoff) list.add(it) } }
            }
        } catch (e: Exception) {
        }
    }

    private fun parse(ln: String): Sample? {
        val p = ln.split(",")
        val t = p.getOrNull(0)?.toLongOrNull() ?: return null
        return Sample(t, p.getOrNull(1)?.toDoubleOrNull(), p.getOrNull(2)?.toDoubleOrNull(), p.getOrNull(3)?.toDoubleOrNull())
    }

    private fun line(s: Sample) = "${s.t},${s.temp ?: ""},${s.hum ?: ""},${s.press ?: ""}\n"

    @Synchronized
    fun all(): List<Sample> = ArrayList(list)

    @Synchronized
    fun add(s: Sample): Boolean {
        if (s.temp == null && s.hum == null && s.press == null) return false
        if (list.isNotEmpty() && s.t - list.last().t < 5 * 60_000L) return false
        list.add(s)
        try {
            file.appendText(line(s))
        } catch (e: Exception) {
        }
        if (list.size > 2400) {
            val cutoff = s.t - keepMs
            list.removeAll { it.t < cutoff }
            try {
                file.writeText(list.joinToString("") { line(it) })
            } catch (e: Exception) {
            }
        }
        return true
    }
}
