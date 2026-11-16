package es.kalvo.escanerqr.wifis.nucleo

/** A network in the list. */
data class Red(
    /** networkId for `forget-network`; -1 when it exists only as another app's suggestion. */
    val id: Int,
    val ssid: String,
    val seguridad: Seguridad,
    /** null = no password (open) or unknown (suggestion not connected since the phone started). */
    val clave: String?,
    val oculta: Boolean,
    val origen: Origen,
    /** Saved by another app as a suggestion: neither Ajustes nor this app can edit or forget it. */
    val sugerencia: Boolean,
    /** Where Samsung says the phone uses it (see [Ubicaciones]); null if unknown. */
    val ubicacion: Ubicacion? = null,
    /** What Android keeps about it (`dumpsys wifi`, see [Detalles]); null for suggestions or if unknown. */
    val detalle: Detalle? = null,
    /** Samsung's dates and use (`dumpsys sem_wifi`, see [UsoSamsung]); null if unknown. */
    val uso: Uso? = null,
    /** Reads of a wifi code with this name in the history, newest first (see [Escaneos]). */
    val escaneos: List<Escaneo> = emptyList(),
) {
    /** Last time connected: the newest of Samsung's and Android's (Android's only lasts until the phone restarts). */
    val ultimaVez: Long? get() = listOfNotNull(uso?.ultimaVez, detalle?.ultimaConexion).maxOrNull()

    val clavePerdida get() = seguridad.llevaClave && clave == null
    val olvidable get() = !sugerencia && id >= 0
    fun datos() = DatosRed(ssid, seguridad, clave, oculta)
    fun clavePara(): String = "$id|${origen.paquete}|$ssid|${seguridad.name}"
}

object Combinar {
    /** Names and types from `cmd wifi list-networks` (this app never reads passwords) plus the other apps' suggestions. */
    fun sinClaves(listadas: List<RedListada>, sugerencias: List<Sugerencia>, creadores: Map<Int, String> = emptyMap()): List<Red> {
        val base = listadas.map {
            Red(it.id, it.ssid, Seguridad.desdeTipos(it.tipos), null, false, Origen.desde(creadores[it.id]), false)
        }
        val vistas = HashSet<String>()
        val sug = sugerencias.filter { vistas.add(it.paquete + "\u0000" + it.ssid) }.map {
            Red(-1, it.ssid, it.seguridad, null, it.oculta, Origen(Origen.Tipo.APP, it.paquete, it.paquete), true)
        }
        return ordenar(base + sug)
    }

    private fun ordenar(l: List<Red>): List<Red> {
        val col = java.text.Collator.getInstance(java.util.Locale("es", "ES")).apply { strength = java.text.Collator.PRIMARY }
        return l.sortedWith { a, b -> col.compare(a.ssid, b.ssid).takeIf { it != 0 } ?: a.sugerencia.compareTo(b.sugerencia) }
    }

    /** Search by name, ignoring case and accents. */
    fun filtrar(l: List<Red>, texto: String, soloQrBot: Boolean): List<Red> {
        val q = Claves.paraBuscar(texto.trim())
        return l.filter { (!soloQrBot || it.origen.esQrBot) && (q.isEmpty() || Claves.paraBuscar(it.ssid).contains(q)) }
    }
}
