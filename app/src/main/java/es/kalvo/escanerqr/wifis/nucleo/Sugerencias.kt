package es.kalvo.escanerqr.wifis.nucleo

/**
 * A network that another app "suggested" to Android (WifiNetworkSuggestion API). This is how QR Bot saved its wifis:
 * they are not normal saved networks, Ajustes cannot edit or forget them, and only that app (or uninstalling it) removes them.
 */
data class Sugerencia(val paquete: String, val ssid: String, val seguridad: Seguridad, val oculta: Boolean, val aprobada: Boolean)

object Sugerencias {
    private const val INICIO = "WifiNetworkSuggestionsManager - Networks Begin"
    private const val FIN = "WifiNetworkSuggestionsManager - Networks End"

    /** Reads the suggestions section of `dumpsys wifi` (never contains passwords). */
    fun leer(volcado: String): List<Sugerencia> {
        val out = ArrayList<Sugerencia>()
        var dentro = false
        var paquete: String? = null
        var aprobada = false
        for (cruda in volcado.lineSequence()) {
            val linea = cruda.trim()
            if (!dentro) { if (linea.startsWith(INICIO)) dentro = true; continue }
            if (linea.startsWith(FIN)) break
            when {
                linea.startsWith("Package Name:") -> { paquete = linea.substringAfter(':').trim(); aprobada = false }
                linea.startsWith("Has user approved:") -> aprobada = linea.substringAfter(':').trim() == "true"
                linea.startsWith("WifiNetworkSuggestion[") -> {
                    val pkg = paquete ?: continue
                    val s = leerLinea(linea) ?: continue
                    out += Sugerencia(pkg, s.first, s.second, s.third, aprobada)
                }
            }
        }
        return out
    }

    /** Name, type and hidden flag of one `WifiNetworkSuggestion[ SSID="…", BSSID=…, …]` line. Null for Passpoint ones. */
    fun leerLinea(l: String): Triple<String, Seguridad, Boolean>? {
        val i = l.indexOf("SSID=")
        if (i < 0) return null
        val resto = l.substring(i + 5)
        val ssidCrudo = if (resto.startsWith('"')) {
            val fin = resto.indexOf("\", BSSID=")
            if (fin < 0) return null
            resto.substring(0, fin + 1)
        } else resto.substringBefore(',')
        if (ssidCrudo == "null" || ssidCrudo.isEmpty()) return null
        val ssid = Claves.ssidLegible(ssidCrudo) ?: return null
        val tras = resto.substring(ssidCrudo.length)
        val tipos = tras.substringAfter("SecurityParams=", "").substringBefore(',').trim().split(Regex("\\s+"))
        val oculta = tras.contains("isHiddenSsid=true")
        return Triple(ssid, Seguridad.desdeTipos(tipos), oculta)
    }
}
