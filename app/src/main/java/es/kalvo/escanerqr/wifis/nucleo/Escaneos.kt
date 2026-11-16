package es.kalvo.escanerqr.wifis.nucleo

import es.kalvo.escanerqr.nucleo.Contenido

/** One read of a wifi code in the history (scanned with this app or imported from QR Bot's CSV). */
data class Escaneo(val id: Long, val instante: Long)

/** A history row, only what is needed to cross it with the networks (the text is never kept after this). */
data class FilaHistorial(val id: Long, val instante: Long, val texto: String)

object Escaneos {
    /** Network name → its reads, newest first. Only `WIFI:` codes with a name count. */
    fun porNombre(filas: List<FilaHistorial>): Map<String, List<Escaneo>> {
        val out = LinkedHashMap<String, MutableList<Escaneo>>()
        for (f in filas) {
            val t = f.texto.trim()
            if (!t.startsWith("WIFI:", ignoreCase = true)) continue
            val w = Contenido.leerWifi(t) ?: continue
            out.getOrPut(w.ssid) { ArrayList() } += Escaneo(f.id, f.instante)
        }
        return out.mapValues { (_, v) -> v.sortedByDescending { it.instante } }
    }

    /** Puts each network's reads on it (same name, exact, as Android compares names). */
    fun asignar(redes: List<Red>, porNombre: Map<String, List<Escaneo>>): List<Red> {
        if (porNombre.isEmpty()) return redes
        return redes.map { r -> porNombre[r.ssid]?.let { r.copy(escaneos = it) } ?: r }
    }
}
