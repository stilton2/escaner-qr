package es.kalvo.escanerqr.wifis.nucleo

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * What Samsung's wifi service keeps per network, from `dumpsys sem_wifi` (read-only, the shell user may dump it):
 * - `SemWifiUsageMonitor` → `[[[Network Usage]]]`: one line per network with when it was saved (`cTime`), when its use was
 *   last written down (`uTime`, i.e. the last time you were connected to it), how many times you connected and for how long.
 * - `SemWifiConfigManager`: per network `cTime: <millis>` (also when it was saved), used when the first part lacks it.
 * Keys are configKeys like `"Casa"WPA_PSK`; they are matched to the networks like the locations ([Ubicaciones]).
 */
data class Uso(
    /** First saved (epoch millis), or null. */
    val creada: Long? = null,
    /** Last time connected (epoch millis); null if never or unknown. */
    val ultimaVez: Long? = null,
    val conexiones: Int? = null,
    /** Total time connected, in seconds. */
    val segundos: Long? = null,
    /** Data received and sent, as Samsung writes it ("1,7 GB"). */
    val recibido: String? = null,
    val enviado: String? = null,
)

object UsoSamsung {
    private val lineaUso = Regex(
        "^(.+?) - cTime (\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}) - uTime (\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}:\\d{2}), " +
            "#ofConnections: (\\d+), ConnectedTime/RX/TX -\\s+total \\[\\s*(\\d+) sec / ([^/\\]]+?) / ([^\\]]+?)\\s*\\]"
    )
    private val cTimeMs = Regex("^\\s*cTime: (\\d+)\\s*$")

    /** Dates before this are Samsung's "never" (it writes 1970/01/01 00:59:59 for a network never used). */
    private const val MINIMO = 946684800000L // 2000-01-01

    fun leer(volcado: String, tz: TimeZone = TimeZone.getDefault()): Map<Pair<String, Ubicaciones.Familia?>, Uso> {
        val out = LinkedHashMap<Pair<String, Ubicaciones.Familia?>, Uso>()
        val creadas = LinkedHashMap<Pair<String, Ubicaciones.Familia?>, Long>()
        var enUso = false
        var enConfig = false
        var claveConfig: Pair<String, Ubicaciones.Familia?>? = null
        for (cruda in volcado.lineSequence()) {
            when {
                cruda.startsWith("[[[Network Usage]]]") -> { enUso = true; continue }
                enUso && (cruda.startsWith("[[[") || cruda.startsWith("****") || cruda.startsWith("SemWifiUsageMonitor - Log")) -> enUso = false
                cruda.startsWith("SemWifiConfigManager - Log End") -> { enConfig = true; continue }
                enConfig && (cruda.startsWith("ScanPool:") || cruda.startsWith("SemWifiConfigManager:")) -> enConfig = false
            }
            if (enUso) {
                val m = lineaUso.find(cruda) ?: continue
                val clave = Ubicaciones.leerClave(m.groupValues[1]) ?: continue
                val creada = fecha(m.groupValues[2], tz)
                val conexiones = m.groupValues[4].toIntOrNull()
                val ultima = fecha(m.groupValues[3], tz)?.takeIf { (conexiones ?: 1) > 0 }
                out.putIfAbsent(clave, Uso(creada, ultima, conexiones, m.groupValues[5].toLongOrNull(),
                    m.groupValues[6].trim(), m.groupValues[7].trim()))
            } else if (enConfig) {
                if (cruda.startsWith("\"") || (cruda.isNotEmpty() && !cruda[0].isWhitespace())) {
                    claveConfig = Ubicaciones.leerClave(cruda.trim())
                    continue
                }
                val k = claveConfig ?: continue
                cTimeMs.find(cruda)?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it >= MINIMO }?.let { creadas.putIfAbsent(k, it) }
            }
        }
        for ((k, ms) in creadas) {
            val u = out[k]
            if (u == null) out[k] = Uso(creada = ms) else if (u.creada == null) out[k] = u.copy(creada = ms)
        }
        return out
    }

    /** `2025/09/16 10:22:56` in the phone's zone → epoch millis; null for Samsung's "never" (1970). */
    fun fecha(t: String, tz: TimeZone): Long? = try {
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.ROOT).apply { timeZone = tz; isLenient = false }.parse(t)?.time
            ?.takeIf { it >= MINIMO }
    } catch (_: ParseException) { null }
}
