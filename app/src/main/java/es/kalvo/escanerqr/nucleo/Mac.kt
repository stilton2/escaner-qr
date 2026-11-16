package es.kalvo.escanerqr.nucleo

/**
 * MAC addresses on device labels (cameras, routers, recorders): `MAC:AA:BB:CC:DD:EE:FF`, `AA-BB-CC-DD-EE-FF`,
 * `AABB.CCDD.EEFF`, 12 hex digits in a longer text, or a JSON label with a MAC key (`mac`, `lanMac`…, or short keys like `{"dm": "<modelo>","dma": "<mac>"}` that some camera makers print).
 * Pure Kotlin (tested on the laptop).
 *
 * Against false positives: 12 hex digits in a row with no separators only count when they carry the word MAC before them,
 * sit in a JSON key that means MAC, or mix letters and digits (an EAN, a serial number made only of digits, does not count).
 */
object Mac {
    /** What a label tells: MACs (normalised `AA:BB:CC:DD:EE:FF`, in order, no repeats), model and serial if they come. */
    data class Etiqueta(val macs: List<String>, val modelo: String, val serie: String) {
        val vacia get() = macs.isEmpty()
    }

    private const val H = "[0-9A-Fa-f]"
    // Not the tail of a longer chain (7 groups), but "MAC:AA:…" is fine: the "AC:" before belongs to a word.
    private val CON_SEPARADOR = Regex("""(?<![0-9A-Fa-f])(?<![^0-9A-Za-z][0-9A-Fa-f]{2}[:-])(?<!^[0-9A-Fa-f]{2}[:-])($H{2})([:-])($H{2})\2($H{2})\2($H{2})\2($H{2})\2($H{2})(?![0-9A-Fa-f])(?![:-][0-9A-Fa-f])""")
    private val CON_PUNTOS = Regex("""(?<![0-9A-Fa-f])(?<![0-9A-Fa-f]\.)($H{4})\.($H{4})\.($H{4})(?![0-9A-Fa-f])(?!\.[0-9A-Fa-f])""")
    private val SEGUIDOS = Regex("""(?<![0-9A-Za-z])($H{12})(?![0-9A-Za-z])""")
    /** "MAC", "MAC:", "mac address =", "MAC ID#", "LAN MAC: " right before the 12 digits. */
    private val ETIQUETA_MAC = Regex("""(?i)\bmac(?:[\s_-]*(?:address|addr|adr|id|lan|wifi|wlan))?\s*[:=#]?\s*"?$""")

    /** Simple JSON "key": "value" (or number) pairs; enough for flat label QR codes, no JSON library needed. */
    private val PAR_JSON = Regex(""""([^"\\]{1,40})"\s*:\s*(?:"((?:[^"\\]|\\.)*)"|([0-9A-Za-z.:-]+))""")

    private val CLAVES_MAC = setOf("dma", "mac", "macaddr", "macaddress", "mac_address", "mac-address", "ethmac", "lanmac", "wifimac", "wlanmac")
    private val CLAVES_MODELO = setOf("dm", "model", "modelo", "devicemodel", "device_model", "md")
    private val CLAVES_SERIE = setOf("sn", "serial", "serialno", "serialnumber", "serial_number", "ds", "dsn")

    private val ETIQ_MODELO = Regex("""(?i)(?:^|[\s,;|])(?:model|modelo|m/n|mod)\s*[:=]\s*([^\s,;|]{2,40})""")
    private val ETIQ_SERIE = Regex("""(?i)(?:^|[\s,;|])(?:s/n|sn|serial(?:\s*no\.?)?|n[ºo°]\s*serie)\s*[:=]\s*([^\s,;|]{3,40})""")

    fun normalizar(hex12: String) = hex12.uppercase().chunked(2).joinToString(":")

    /** A value that is a MAC by itself (any of the forms, including 12 digits in a row); null if not. */
    fun comoMac(v: String): String? {
        val t = v.trim()
        CON_SEPARADOR.matchEntire(t)?.let { return normalizar(t.filter { it != ':' && it != '-' }) }
        CON_PUNTOS.matchEntire(t)?.let { return normalizar(t.replace(".", "")) }
        if (Regex("$H{12}").matches(t)) return normalizar(t)
        return null
    }

    /**
     * Only for plain text (and JSON, which is plain text): links, wifis, contacts… are left alone, so a wifi password
     * that looks like 12 hex digits is never taken for a MAC.
     */
    fun deContenido(texto: String, c: Contenido): Etiqueta =
        if (c is Contenido.Texto) buscar(texto) else Etiqueta(emptyList(), "", "")

    /** What «Copiar al portapapeles» copies: the whole text, or only the first MAC when there is one and [soloMac] is on. */
    fun queCopiar(texto: String, c: Contenido, soloMac: Boolean): String =
        if (!soloMac) texto else deContenido(texto, c).macs.firstOrNull() ?: texto

    fun buscar(texto: String): Etiqueta {
        val macs = LinkedHashSet<String>()
        var modelo = ""
        var serie = ""

        // 1) JSON keys (any key with "mac"; short keys "dma" = MAC, "dm" = model, "ds" = serial).
        for (m in PAR_JSON.findAll(texto)) {
            val clave = m.groupValues[1].trim().lowercase()
            val valor = (m.groups[2]?.value ?: m.groups[3]?.value ?: "").trim()
            if (valor.isEmpty()) continue
            when {
                clave in CLAVES_MAC || clave.contains("mac") -> comoMac(valor)?.let { macs.add(it) }
                clave in CLAVES_MODELO && modelo.isEmpty() -> modelo = valor
                clave in CLAVES_SERIE && serie.isEmpty() -> serie = valor
            }
        }

        // 2) Anywhere in the text, with separators.
        for (m in CON_SEPARADOR.findAll(texto)) macs.add(normalizar(m.value.filter { it != ':' && it != '-' }))
        for (m in CON_PUNTOS.findAll(texto)) macs.add(normalizar(m.value.replace(".", "")))

        // 3) 12 in a row: with "MAC" before, or mixing letters and digits.
        for (m in SEGUIDOS.findAll(texto)) {
            val v = m.value
            val antes = texto.substring(maxOf(0, m.range.first - 24), m.range.first)
            val conEtiqueta = ETIQUETA_MAC.containsMatchIn(antes)
            val mezcla = v.any { it.isDigit() } && v.any { it.isLetter() }
            if (conEtiqueta || mezcla) macs.add(normalizar(v))
        }

        if (modelo.isEmpty()) ETIQ_MODELO.find(texto)?.let { modelo = it.groupValues[1] }
        if (serie.isEmpty()) ETIQ_SERIE.find(texto)?.let { serie = it.groupValues[1] }
        return Etiqueta(macs.toList(), modelo, serie)
    }
}
