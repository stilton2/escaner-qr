package es.kalvo.escanerqr.wifis.nucleo

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Dates in plain Spanish for the Wifis tab ("hace 3 días", "14/11", "14/11/2025 18:03"). */
object Cuando {
    private fun fmt(p: String, tz: TimeZone) = SimpleDateFormat(p, Locale.ROOT).apply { timeZone = tz }

    /** Whole calendar days between the two instants in [tz] (0 = same day). */
    fun diasEntre(antes: Long, ahora: Long, tz: TimeZone): Int {
        fun dia(ms: Long) = Calendar.getInstance(tz).apply {
            timeInMillis = ms; set(Calendar.HOUR_OF_DAY, 12); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return Math.round((dia(ahora) - dia(antes)) / 86_400_000.0).toInt()
    }

    /** "hoy", "ayer", "hace 3 días", "hace 2 semanas", "hace 5 meses", "hace 2 años". */
    fun hace(ms: Long, ahora: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val d = diasEntre(ms, ahora, tz)
        return when {
            d <= 0 -> "hoy"
            d == 1 -> "ayer"
            d < 14 -> "hace $d días"
            d < 60 -> "hace ${d / 7} semanas"
            d < 365 -> "hace ${d / 30} meses"
            d < 730 -> "hace 1 año"
            else -> "hace ${d / 365} años"
        }
    }

    /** "14/11" this year, "14/11/2025" other years. */
    fun corta(ms: Long, ahora: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val mismoAño = fmt("yyyy", tz).format(Date(ms)) == fmt("yyyy", tz).format(Date(ahora))
        return fmt(if (mismoAño) "dd/MM" else "dd/MM/yyyy", tz).format(Date(ms))
    }

    /** "14/11/2025 18:03". */
    fun larga(ms: Long, tz: TimeZone = TimeZone.getDefault()): String = fmt("dd/MM/yyyy HH:mm", tz).format(Date(ms))

    /** "1 h 20 min", "3 días 4 h", "45 s". */
    fun duracion(segundos: Long): String {
        val d = segundos / 86_400; val h = (segundos % 86_400) / 3600; val m = (segundos % 3600) / 60
        return when {
            d > 0 -> "$d " + (if (d == 1L) "día" else "días") + if (h > 0) " $h h" else ""
            h > 0 -> "$h h" + if (m > 0) " $m min" else ""
            m > 0 -> "$m min"
            else -> "$segundos s"
        }
    }

    /** "3 mar 2025" (S-J4, the «DATOS DE LA RED» rows). */
    fun fechaCorta(ms: Long, tz: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("d MMM yyyy", Locale.forLanguageTag("es-ES")).apply { timeZone = tz }.format(Date(ms)).replace(".", "")

    /** "Hace 2 días", "Hoy", "Ayer" (capital first letter, for a value on its own). */
    fun haceSolo(ms: Long, ahora: Long, tz: TimeZone = TimeZone.getDefault()): String = hace(ms, ahora, tz).replaceFirstChar { it.uppercase() }

    /** "3 veces · última hoy", "1 vez · hace 2 días", "" if never (newest first in [escaneos]). */
    fun escaneada(escaneos: List<Escaneo>, ahora: Long, tz: TimeZone = TimeZone.getDefault()): String {
        val ultima = escaneos.maxOfOrNull { it.instante } ?: return ""
        val n = escaneos.size
        return (if (n == 1) "1 vez" else "$n veces") + " · " + (if (n == 1) "" else "última ") + hace(ultima, ahora, tz)
    }

    /**
     * The short line under each network in the list: "Última vez: hace 3 días · Escaneada el 14/11".
     * Empty when there is nothing to say.
     */
    fun lineaLista(r: Red, ahora: Long, tz: TimeZone = TimeZone.getDefault()): String = buildList {
        val ultima = r.ultimaVez
        when {
            r.detalle?.conectadaAhora == true -> add("Conectada ahora")
            ultima != null -> add("Última vez: " + hace(ultima, ahora, tz))
            r.uso?.conexiones == 0 || r.detalle?.conectadaAlgunaVez == false -> add("Nunca conectada")
        }
        r.escaneos.firstOrNull()?.let { add("Escaneada el " + corta(it.instante, ahora, tz)) }
    }.joinToString(" · ")
}
