package es.kalvo.escanerqr.wifis.nucleo

import es.kalvo.escanerqr.nucleo.Contenido

/**
 * «Leídas e importadas» (v2.7): every different wifi code in the history (scanned with this app or imported from another app),
 * one row per name + password + type, with its reads newest first, crossed with the phone's saved networks.
 */
data class WifiLeida(
    val ssid: String,
    val clave: String,
    /** The scanner's normalised type: `WPA`, `SAE`, `WEP` or `nopass`. */
    val tipo: String,
    val oculta: Boolean,
    /** History ids, newest first. */
    val ids: List<Long>,
    val primera: Long,
    val ultima: Long,
    /** Where the reads came from: "" = scanned here, otherwise the app/file name. Distinct, newest first. */
    val origenes: List<String>,
    val enMovil: EnMovil = EnMovil.NO,
) {
    enum class EnMovil { NO, IGUAL, OTRA_CLAVE, SIN_SABER }

    val veces get() = ids.size
    val escaneada get() = "" in origenes
    val importada get() = origenes.any { it.isNotEmpty() }
}

object Leidas {
    /** Groups the history's wifi codes. Codes that are not `WIFI:` or have no name are left out. */
    fun agrupar(filas: List<FilaHistorial>): List<WifiLeida> {
        data class K(val ssid: String, val clave: String, val tipo: String, val oculta: Boolean)
        val grupos = LinkedHashMap<K, MutableList<FilaHistorial>>()
        for (f in filas) {
            val t = f.texto.trim()
            if (!t.startsWith("WIFI:", ignoreCase = true)) continue
            val w = Contenido.leerWifi(t) ?: continue
            if (w.ssid.isEmpty()) continue
            val tipo = w.tipoMisWifis
            val clave = if (tipo == "nopass") "" else w.clave
            grupos.getOrPut(K(w.ssid, clave, tipo, w.oculta)) { ArrayList() } += f
        }
        return grupos.map { (k, l) ->
            val orden = l.sortedWith(compareByDescending<FilaHistorial> { it.instante }.thenByDescending { it.id })
            WifiLeida(k.ssid, k.clave, k.tipo, k.oculta, orden.map { it.id }, orden.last().instante, orden.first().instante,
                orden.map { it.origen }.distinct())
        }.sortedByDescending { it.ultima }
    }

    /** Marks each one against the phone's normal saved networks ([redes] without suggestions; passwords may be unknown). */
    fun cruzar(leidas: List<WifiLeida>, redes: List<Red>): List<WifiLeida> {
        val porNombre = redes.filter { !it.sugerencia }.groupBy { it.ssid }
        return leidas.map { w ->
            val mias = porNombre[w.ssid] ?: return@map w.copy(enMovil = WifiLeida.EnMovil.NO)
            val e = when {
                w.tipo == "nopass" -> WifiLeida.EnMovil.IGUAL
                mias.any { it.clave == w.clave } -> WifiLeida.EnMovil.IGUAL
                mias.all { it.clave != null } -> WifiLeida.EnMovil.OTRA_CLAVE
                else -> WifiLeida.EnMovil.SIN_SABER
            }
            w.copy(enMovil = e)
        }
    }

    /** Search by name, ignoring case and accents. */
    fun filtrar(l: List<WifiLeida>, texto: String): List<WifiLeida> {
        val q = Claves.paraBuscar(texto.trim())
        return if (q.isEmpty()) l else l.filter { Claves.paraBuscar(it.ssid).contains(q) }
    }
}
