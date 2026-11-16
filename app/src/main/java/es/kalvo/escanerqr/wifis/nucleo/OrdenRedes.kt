package es.kalvo.escanerqr.wifis.nucleo

import java.util.Calendar
import java.util.TimeZone

/** How the Wifis tab sorts (S-J4, v2.3). The order is kept in the settings by its [ordinal]. */
enum class Orden(val nombre: String) {
    NOMBRE("Nombre A-Z"),
    ULTIMA_CONEXION("Última conexión"),
    GUARDADA("Guardada por primera vez"),
    ESCANEADA("Última vez escaneada"),
}

/** Date range, applied to the date of the chosen order (with «Nombre A-Z» there is no date: it does not apply). */
enum class Rango(val nombre: String) {
    SIEMPRE("Siempre"),
    SEMANA("Últimos 7 días"),
    MES("Último mes"),
    AÑO("Este año"),
}

/**
 * What the Wifis tab shows. [origen]: 0 all, 1 normal networks, 2 «De otras apps» (suggestions of any app, S-J5). [conUbicacion]: only the ones Samsung knows where
 * you use them. [escaneadas]: only the ones with a read of their QR in the history. [texto]: search by name.
 */
data class FiltroRedes(
    val origen: Int = 0,
    val conUbicacion: Boolean = false,
    val escaneadas: Boolean = false,
    val rango: Rango = Rango.SIEMPRE,
    val texto: String = "",
)

/** Sort and filter of the networks (pure Kotlin, tested on the laptop). */
object OrdenRedes {
    private const val DIA = 86_400_000L

    /** The date each order uses: last connection (now if connected), first saved, last read in the history. */
    fun fecha(r: Red, orden: Orden, ahora: Long): Long? = when (orden) {
        Orden.NOMBRE -> null
        Orden.ULTIMA_CONEXION -> if (r.detalle?.conectadaAhora == true) ahora else r.ultimaVez
        Orden.GUARDADA -> r.uso?.creada
        Orden.ESCANEADA -> r.escaneos.maxOfOrNull { it.instante }
    }

    /** Is [ms] inside [rango]? «Último mes» = last 30 days; «Este año» = since 1 January in [tz]. */
    fun dentro(ms: Long?, rango: Rango, ahora: Long, tz: TimeZone): Boolean = when (rango) {
        Rango.SIEMPRE -> true
        Rango.SEMANA -> ms != null && ms >= ahora - 7 * DIA
        Rango.MES -> ms != null && ms >= ahora - 30 * DIA
        Rango.AÑO -> ms != null && ms >= Calendar.getInstance(tz).apply {
            timeInMillis = ahora; set(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun aplicar(redes: List<Red>, f: FiltroRedes, orden: Orden, ahora: Long, tz: TimeZone = TimeZone.getDefault()): List<Red> {
        val q = Claves.paraBuscar(f.texto.trim())
        val col = java.text.Collator.getInstance(java.util.Locale("es", "ES")).apply { strength = java.text.Collator.PRIMARY }
        val porNombre = Comparator<Red> { a, b -> col.compare(a.ssid, b.ssid).takeIf { it != 0 } ?: a.sugerencia.compareTo(b.sugerencia) }
        val filtradas = redes.filter { r ->
            when (f.origen) { 1 -> !r.sugerencia; 2 -> r.sugerencia; else -> true } &&
                (!f.conUbicacion || r.ubicacion != null) &&
                (!f.escaneadas || r.escaneos.isNotEmpty()) &&
                (orden == Orden.NOMBRE || dentro(fecha(r, orden, ahora), f.rango, ahora, tz)) &&
                (q.isEmpty() || Claves.paraBuscar(r.ssid).contains(q))
        }
        if (orden == Orden.NOMBRE) return filtradas.sortedWith(porNombre)
        // Newest first; the ones without that date at the end, by name.
        return filtradas.sortedWith(compareByDescending<Red, Long?>(nullsFirst<Long>()) { fecha(it, orden, ahora) }.thenComparing(porNombre))
    }
}
