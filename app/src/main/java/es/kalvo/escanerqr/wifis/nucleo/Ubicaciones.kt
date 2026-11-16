package es.kalvo.escanerqr.wifis.nucleo

import java.net.URLEncoder
import java.util.Locale

/**
 * Where the phone uses a network. Samsung keeps one point per network for "Activar Wi-Fi automáticamente"
 * (its WifiGeofenceManager) and gives it through `sem_wifi` → `getConfiguredNetworkLocations()` to callers
 * with NETWORK_SETTINGS (the shell user has it). The answer is a map
 * `configKey → {"latitude": Double, "longitude": Double}`, with configKey like `"Casa"WPA_PSK` or `"Bar"SAE-0`.
 */
data class Ubicacion(val lat: Double, val lon: Double) {
    fun texto(): String = String.format(Locale.ROOT, "%.5f, %.5f", lat, lon)

    /** Link that opens the map app on that point (with the network name as label). Opening it is the user's choice. */
    fun geo(nombre: String): String {
        val la = String.format(Locale.ROOT, "%.6f", lat)
        val lo = String.format(Locale.ROOT, "%.6f", lon)
        val etiqueta = URLEncoder.encode(nombre, "UTF-8").replace("+", "%20")
        return "geo:$la,$lo?q=$la,$lo($etiqueta)"
    }
}

object Ubicaciones {
    /** Family of security types, so that a WPA2 network (key `WPA_PSK`) also matches when it was keyed as `SAE`. */
    enum class Familia { CLAVE, ABIERTA, WEP, EMPRESA }

    fun familia(s: Seguridad) = when (s) {
        Seguridad.WPA, Seguridad.WPA3 -> Familia.CLAVE
        Seguridad.ABIERTA, Seguridad.OWE -> Familia.ABIERTA
        Seguridad.WEP -> Familia.WEP
        Seguridad.EMPRESA -> Familia.EMPRESA
    }

    private fun familiaClave(t: String): Familia? = when {
        t.startsWith("WPA_PSK") || t.startsWith("WPA2_PSK") || t == "SAE" || t == "WAPI_PSK" -> Familia.CLAVE
        t == "NONE" || t == "OWE" -> Familia.ABIERTA
        t == "WEP" -> Familia.WEP
        t.contains("EAP") || t.contains("SUITE_B") || t == "IEEE8021X" || t.startsWith("WAPI") -> Familia.EMPRESA
        else -> null
    }

    private val conComillas = Regex("^\"(.*)\"([A-Z][A-Z0-9_]*)(?:-\\d+)?$", RegexOption.DOT_MATCHES_ALL)
    private val enHex = Regex("^([0-9a-f]+)([A-Z][A-Z0-9_]*)(?:-\\d+)?$")

    /** Name and type family of a configKey (`"Casa"WPA_PSK`, `"Casa"SAE-0`, `"Libre"NONE`). Null if it has another shape. */
    fun leerClave(k: String): Pair<String, Familia?>? {
        conComillas.find(k)?.let { return it.groupValues[1] to familiaClave(it.groupValues[2]) }
        enHex.find(k)?.let { m -> Claves.ssidLegible(m.groupValues[1])?.let { return it to familiaClave(m.groupValues[2]) } }
        return null
    }

    /** From the raw map the service returns. Entries with another shape or without both numbers are skipped. */
    fun desdeMapa(mapa: Map<*, *>): Map<Pair<String, Familia?>, Ubicacion> {
        val out = LinkedHashMap<Pair<String, Familia?>, Ubicacion>()
        for ((k, v) in mapa) {
            val clave = (k as? String)?.let { leerClave(it) } ?: continue
            val m = v as? Map<*, *> ?: continue
            val la = (m["latitude"] as? Number)?.toDouble() ?: continue
            val lo = (m["longitude"] as? Number)?.toDouble() ?: continue
            if (la.isNaN() || lo.isNaN() || la !in -90.0..90.0 || lo !in -180.0..180.0) continue
            if (la == 0.0 && lo == 0.0) continue
            out.putIfAbsent(clave, Ubicacion(la, lo))
        }
        return out
    }

    /** The entry for [r]: same name and family first; otherwise the only distinct entry with that name. */
    fun <T> buscar(r: Red, m: Map<Pair<String, Familia?>, T>, porNombre: Map<String, List<T>>): T? =
        m[r.ssid to familia(r.seguridad)] ?: porNombre[r.ssid]?.distinct()?.singleOrNull()

    /**
     * Puts Samsung's dates and use on each network. A suggestion only gets them when there is no normal network with
     * the same name (Samsung keys them by name and type, so it could not tell them apart).
     */
    fun asignarUso(redes: List<Red>, usos: Map<Pair<String, Familia?>, Uso>): List<Red> {
        if (usos.isEmpty()) return redes
        val porNombre = usos.entries.groupBy({ it.key.first }, { it.value })
        val normales = redes.filter { !it.sugerencia }.mapTo(HashSet()) { it.ssid }
        return redes.map { r ->
            if (r.sugerencia && r.ssid in normales) r
            else buscar(r, usos, porNombre)?.let { r.copy(uso = it) } ?: r
        }
    }

    /** Puts each network's point on it: same name and family first; otherwise the only point with that name. */
    fun asignar(redes: List<Red>, ubic: Map<Pair<String, Familia?>, Ubicacion>): List<Red> {
        if (ubic.isEmpty()) return redes
        val porNombre = ubic.entries.groupBy({ it.key.first }, { it.value })
        return redes.map { r ->
            val u = ubic[r.ssid to familia(r.seguridad)] ?: porNombre[r.ssid]?.distinct()?.singleOrNull()
            if (u == null) r else r.copy(ubicacion = u)
        }
    }
}
