package es.kalvo.escanerqr.wifis.nucleo

/** One saved network as `cmd wifi list-networks` shows it (no password: that command never gives it). */
data class RedListada(val id: Int, val ssid: String, val tipos: List<String>)

object ListaRedes {
    /**
     * Lines are `<id>  <name>  <type>` with spaces as separators (the name itself can have spaces).
     * A WPA2 network comes twice (`wpa2-psk` and `wpa3-sae^`): both lines are joined by id.
     */
    fun leer(salida: String): List<RedListada> {
        val porId = LinkedHashMap<Int, Pair<String, MutableList<String>>>()
        for (raw in salida.lines()) {
            val linea = raw.trimEnd()
            val m = Regex("^\\s*(\\d+)\\s+(.*\\S)\\s+(\\S+)$").find(linea) ?: continue
            val id = m.groupValues[1].toInt()
            val ssid = m.groupValues[2].trim()
            val tipo = m.groupValues[3]
            porId.getOrPut(id) { ssid to mutableListOf() }.second += tipo
        }
        return porId.map { (id, v) -> RedListada(id, v.first, v.second.toList()) }
    }
}
