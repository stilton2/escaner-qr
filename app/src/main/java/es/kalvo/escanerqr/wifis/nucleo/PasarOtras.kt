package es.kalvo.escanerqr.wifis.nucleo

/**
 * «Pasarlas todas a redes normales» (v2.8): the networks other apps saved as suggestions (QR Bot saved 405 like that) disappear
 * when that app is uninstalled. Android does not give their passwords, but the history usually has the same wifi code
 * (scanned or imported from QR Bot's CSV): its password is used. Pure logic; saving is done by Redes.guardarVarias.
 */
object PasarOtras {
    /**
     * [listas] can be saved now, the most recently read first (v2.9: only the first ones that fit are passed, see Cupo);
     * [sinClave] = names with no usable password in the history (they stay as they are).
     */
    data class Plan(val listas: List<DatosRed>, val sinClave: List<String>)

    fun plan(redes: List<Red>, leidas: List<WifiLeida>): Plan {
        val normales = redes.filter { !it.sugerencia }.mapTo(HashSet()) { it.ssid }
        // Newest read first (Leidas.agrupar already sorts by the last read).
        val porNombre = leidas.sortedByDescending { it.ultima }.groupBy { it.ssid }
        val listas = ArrayList<Pair<DatosRed, Long>>()
        val sinClave = ArrayList<String>()
        val vistas = HashSet<String>()
        for (r in redes) {
            if (!r.sugerencia || r.ssid in normales || !vistas.add(r.ssid)) continue
            if (!r.seguridad.sencilla) { sinClave += r.ssid; continue }
            val ultima = porNombre[r.ssid]?.firstOrNull()?.ultima ?: 0L
            if (!r.seguridad.llevaClave) { listas += r.datos() to ultima; continue }
            val clave = r.clave ?: porNombre[r.ssid].orEmpty()
                .firstOrNull { it.tipo != "nopass" && Claves.problemaClave(r.seguridad, it.clave) == null }?.clave
            if (clave == null) sinClave += r.ssid else listas += r.datos().copy(clave = clave) to ultima
        }
        return Plan(listas.sortedByDescending { it.second }.map { it.first }, sinClave)
    }
}
