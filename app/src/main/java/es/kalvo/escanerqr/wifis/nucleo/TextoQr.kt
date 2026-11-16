package es.kalvo.escanerqr.wifis.nucleo

/** What a wifi QR (or another app) gives: enough to save the network. */
data class DatosRed(val ssid: String, val seguridad: Seguridad, val clave: String?, val oculta: Boolean)

/**
 * The standard wifi QR text (the ZXing "MECARD-like" format every phone camera reads):
 * `WIFI:T:WPA;S:<name>;P:<password>;H:true;;` with `\ ; , : "` escaped by a backslash.
 */
object TextoQr {
    private const val ESPECIALES = "\\;,:\""

    fun escapar(s: String): String = buildString {
        for (c in s) {
            if (c in ESPECIALES) append('\\')
            append(c)
        }
    }

    /** Text to put in the QR. Open networks go with `T:nopass` and no `P:`; `H:true` only when hidden. */
    fun crear(d: DatosRed): String {
        require(d.seguridad.sencilla) { "Las redes de empresa no se pueden pasar por QR" }
        return buildString {
            append("WIFI:T:").append(d.seguridad.qr).append(';')
            append("S:").append(escapar(d.ssid)).append(';')
            val clave = d.clave
            if (d.seguridad.llevaClave && !clave.isNullOrEmpty()) append("P:").append(escapar(clave)).append(';')
            if (d.oculta) append("H:true;")
            append(';')
        }
    }

    /**
     * The text from `WIFI:` on. QR Bot sometimes kept a line before it or line breaks around it,
     * so `WIFI:` may start any line (not only the first one). Null if there is no `WIFI:` at the start of a line.
     */
    fun desdeWifi(texto: String): String? {
        val t = texto.trim()
        var i = 0
        while (true) {
            val j = t.indexOf("WIFI:", i, ignoreCase = true)
            if (j < 0) return null
            if (j == 0 || t[j - 1] == '\n' || t[j - 1] == '\r' || t[j - 1] == ' ') return t.substring(j).trim()
            i = j + 5
        }
    }

    /** Reads a `WIFI:` text (from a QR or another app). Null if it is not a wifi QR or has no name. */
    fun leer(texto: String): DatosRed? {
        val t = desdeWifi(texto) ?: return null
        val campos = HashMap<String, String>()
        var i = 5
        val clave = StringBuilder()
        val valor = StringBuilder()
        var enValor = false
        fun cerrar() {
            if (clave.isNotEmpty()) campos.putIfAbsent(clave.toString().trim().uppercase(), valor.toString())
            clave.setLength(0); valor.setLength(0); enValor = false
        }
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' && i + 1 < t.length -> { (if (enValor) valor else clave).append(t[i + 1]); i++ }
                c == ';' -> cerrar()
                c == ':' && !enValor -> enValor = true
                else -> (if (enValor) valor else clave).append(c)
            }
            i++
        }
        cerrar()
        val ssid = campos["S"]
        if (ssid.isNullOrEmpty()) return null
        val pass = campos["P"]?.takeIf { it.isNotEmpty() }
        val seg = Seguridad.desdeTexto(campos["T"], pass != null)
        return DatosRed(
            ssid = ssid,
            seguridad = seg,
            clave = if (seg.llevaClave) pass else null,
            oculta = campos["H"].let { it.equals("true", ignoreCase = true) || it == "1" },
        )
    }
}
