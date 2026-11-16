package es.kalvo.escanerqr.wifis.nucleo

/**
 * Who created each saved network, from the "Configured networks" part of `dumpsys wifi`:
 * a block starts with `ID: <n> SSID: …` and further down has ` cuid=1000 cname=android.uid.system:1000 luid=…`.
 * Used when the wifi service route fails (names only). Never reads passwords.
 */
object Creadores {
    private const val INICIO = "WifiConfigManager - Configured networks Begin"
    private const val FIN = "WifiConfigManager - Configured networks End"
    /** The connected network starts with `* ID: <n> SSID: …` and disabled ones with `- DSBLE ID: <n> SSID: …` (wrong password, no internet…). */
    private val id = Regex("^(?:\\* |- DSBLE )?ID: (\\d+) SSID")
    private val cname = Regex("(?:^|\\s)cname=(\\S+)")

    fun leer(volcado: String): Map<Int, String> {
        val out = LinkedHashMap<Int, String>()
        var dentro = false
        var actual: Int? = null
        for (linea in volcado.lineSequence()) {
            if (!dentro) { if (linea.startsWith(INICIO)) dentro = true; continue }
            if (linea.startsWith(FIN)) break
            val m = id.find(linea)
            if (m != null) { actual = m.groupValues[1].toInt(); continue }
            val n = actual ?: continue
            if (n !in out) cname.find(linea)?.let { out[n] = it.groupValues[1] }
        }
        return out
    }
}
