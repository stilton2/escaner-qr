package es.kalvo.escanerqr.wifis.nucleo

/**
 * Shell commands, always as an argument list (Shizuku runs them without a shell in between,
 * so a name or password with spaces, quotes, `;` or `$` is passed as-is and cannot break the command).
 */
object Ordenes {
    fun listar() = listOf("cmd", "wifi", "list-networks")
    /** Android's cap on saved networks (read-only). Prints a number; -1 = no cap. */
    fun tope() = listOf("cmd", "overlay", "lookup", "com.android.wifi.resources",
        "com.android.wifi.resources:integer/config_wifiMaxNumWifiConfigurations")
    fun volcado() = listOf("dumpsys", "wifi")
    /** Samsung's wifi service dump (dates and use per network). Read-only. */
    fun volcadoSamsung() = listOf("dumpsys", "sem_wifi")
    fun olvidar(id: Int): List<String> {
        require(id >= 0) { "id de red no válido" }
        return listOf("cmd", "wifi", "forget-network", id.toString())
    }

    /** `cmd wifi add-network <ssid> <type> [<password>] [-h]` (saves it as a normal network, created by the shell user). */
    fun anadir(d: DatosRed): List<String> = armar("add-network", d)

    /** `cmd wifi connect-network <ssid> <type> [<password>] [-h]`: saves it as a normal network AND connects now. */
    fun conectar(d: DatosRed): List<String> = armar("connect-network", d)

    fun estado() = listOf("cmd", "wifi", "status")

    /** Name of the network the phone is connected to, from the output of `cmd wifi status` (null if none). */
    fun conectadoA(salida: String): String? =
        Regex("Wifi is connected to \"(.*)\"").find(salida)?.groupValues?.get(1)

    private fun armar(sub: String, d: DatosRed): List<String> {
        require(d.seguridad.sencilla) { "Las redes de empresa hay que añadirlas en Ajustes" }
        require(d.ssid.isNotEmpty()) { "Falta el nombre" }
        val out = mutableListOf("cmd", "wifi", sub, d.ssid, d.seguridad.cmd)
        if (d.seguridad.llevaClave) out += requireNotNull(d.clave?.takeIf { it.isNotEmpty() }) { "Falta la contraseña" }
        if (d.oculta) out += "-h"
        return out
    }

    /** The command in words for the screen/report, with the password hidden. */
    fun describir(argv: List<String>, clave: String?): String =
        argv.joinToString(" ") { if (clave != null && it == clave) "••••" else if (it.any { c -> c.isWhitespace() }) "«$it»" else it }
}
