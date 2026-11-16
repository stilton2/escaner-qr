package es.kalvo.escanerqr.wifis.nucleo

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * What Android keeps about one saved normal network, read from the "Configured networks" part of `dumpsys wifi`
 * (the same thing Ajustes shows when you tap the network). Read-only; it never has the password (`PSK/SAE: *`).
 * Fields that the dump does not have stay null.
 */
data class Detalle(
    val id: Int,
    /** `macRandomizationSetting`: 0 = phone's MAC, 1 = random fixed, 2 = random that changes, 3 = random (Android decides). */
    val macAjuste: Int? = null,
    /** The random MAC this network uses (null if none or the "no MAC" placeholder). */
    val macAleatoria: String? = null,
    /** `IP assignment`: DHCP, STATIC or UNASSIGNED. */
    val ip: String? = null,
    /** With a fixed IP: "IP address …, Gateway …, DNS …", as the dump gives it. */
    val ipFija: String? = null,
    /** `Proxy settings`: NONE, STATIC, PAC or UNASSIGNED. */
    val proxy: String? = null,
    /** With a proxy: host:port or the PAC address. */
    val proxyDetalle: String? = null,
    /** `allowAutojoin`: connects by itself when it is in range. */
    val autoConexion: Boolean? = null,
    /** `meteredOverride`: 0 = detect automatically, 1 = treat as metered, 2 = treat as not metered. */
    val medida: Int = 0,
    /** `meteredHint`: the network itself says it is metered (e.g. a phone's hotspot). */
    val pistaMedida: Boolean = false,
    val oculta: Boolean? = null,
    /** `cname`: who created it. */
    val creador: String? = null,
    /** `lname`: who changed it last. */
    val cambiadaPor: String? = null,
    val conectadaAlgunaVez: Boolean? = null,
    val conectadaAhora: Boolean = false,
    val internetAlgunaVez: Boolean? = null,
    /** `lastConnected` (only for networks used since the phone started; Android does not keep it after a restart). */
    val ultimaConexion: Long? = null,
    /** `numRebootsSinceLastUse`. */
    val reiniciosSinUsar: Int? = null,
    /** Why Android stopped using it on its own (`mNetworkSelectionDisableReason`, e.g. `…_BY_WRONG_PASSWORD`); null if in use. */
    val desactivada: String? = null,
) {
    /** The reason in plain words, as Ajustes says it. */
    val motivoDesactivada: String? get() = desactivada?.let {
        when {
            it.contains("WRONG_PASSWORD") -> "contraseña incorrecta"
            it.contains("NO_INTERNET") -> "sin internet"
            it.contains("AUTHENTICATION") -> "fallo al autenticarse"
            it.contains("DHCP") -> "no le dio dirección IP"
            it.contains("ASSOCIATION") -> "el router la rechazó"
            it.contains("BY_WIFI_MANAGER") || it.contains("BY_USER") -> "desactivada a mano o por una app"
            else -> it.removePrefix("NETWORK_SELECTION_DISABLED_").lowercase().replace('_', ' ')
        }
    }
}

object Detalles {
    private const val INICIO = "WifiConfigManager - Configured networks Begin"
    private const val FIN = "WifiConfigManager - Configured networks End"
    /** The connected network starts with `* ID: <n> SSID: …` and disabled ones with `- DSBLE ID: <n> SSID: …`. */
    private val cabecera = Regex("^(?:\\* |- DSBLE )?ID: (\\d+) SSID")
    private val mac = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    /** One [Detalle] per network id. [tz] is the phone's zone (the dump writes local times). */
    fun leer(volcado: String, tz: TimeZone = TimeZone.getDefault()): Map<Int, Detalle> {
        val out = LinkedHashMap<Int, Detalle>()
        var dentro = false
        var d: Detalle? = null
        fun cerrar() { d?.let { if (it.id !in out) out[it.id] = it }; d = null }
        for (cruda in volcado.lineSequence()) {
            if (!dentro) { if (cruda.startsWith(INICIO)) dentro = true; continue }
            if (cruda.startsWith(FIN)) break
            val m = cabecera.find(cruda)
            if (m != null) {
                cerrar()
                d = Detalle(
                    id = m.groupValues[1].toInt(),
                    // The name comes first and can contain anything: read the flags after the last occurrence.
                    oculta = valorTras(cruda, " HIDDEN: ")?.let { it == "true" },
                    conectadaAhora = valorTras(cruda, " Currently Connected: ") == "true",
                )
                continue
            }
            val actual = d ?: continue
            val l = cruda.trim()
            d = when {
                l.startsWith("mNetworkSelectionDisableReason ") -> actual.copy(desactivada = l.substringAfter(' ').trim().takeIf {
                    it.isNotEmpty() && it != "NETWORK_SELECTION_ENABLE"
                })
                l.startsWith("hasEverConnected:") -> actual.copy(conectadaAlgunaVez = l.substringAfter(':').trim() == "true")
                l.startsWith("hasEverValidatedInternetAccess:") -> actual.copy(internetAlgunaVez = l.substringAfter(':').trim() == "true")
                l.startsWith("macRandomizationSetting:") -> actual.copy(macAjuste = l.substringAfter(':').trim().toIntOrNull())
                l.startsWith("mRandomizedMacAddress:") -> {
                    val v = l.substringAfter(':').trim()
                    actual.copy(macAleatoria = v.takeIf { mac.matches(it) && it != "02:00:00:00:00:00" })
                }
                l.startsWith("mCandidateSecurityParams:") -> actual.copy(
                    pistaMedida = Regex("(^|\\s)meteredHint(\\s|$)").containsMatchIn(l),
                    medida = Regex("meteredOverride (\\d)").find(l)?.groupValues?.get(1)?.toIntOrNull() ?: actual.medida,
                )
                l.startsWith("meteredOverride ") -> actual.copy(medida = l.substringAfter(' ').trim().take(1).toIntOrNull() ?: 0)
                l.startsWith("IP assignment:") -> actual.copy(ip = l.substringAfter(':').trim())
                l.startsWith("Static configuration:") -> actual.copy(ipFija = ipFija(l.substringAfter(':').trim()))
                l.startsWith("Proxy settings:") -> actual.copy(proxy = l.substringAfter(':').trim())
                l.startsWith("HTTP proxy:") -> actual.copy(proxyDetalle = proxy(l.substringAfter(':').trim()))
                l.startsWith("cuid=") || l.contains(" cname=") -> actual.copy(
                    creador = campo(l, "cname"),
                    cambiadaPor = campo(l, "lname"),
                    autoConexion = campo(l, "allowAutojoin")?.let { it == "true" },
                )
                l.startsWith("lastConnected:") -> actual.copy(ultimaConexion = fecha(l.substringAfter(':').trim(), tz))
                l.startsWith("numRebootsSinceLastUse:") -> actual.copy(reiniciosSinUsar = l.substringAfter(':').trim().toIntOrNull())
                else -> actual
            }
        }
        cerrar()
        return out
    }

    private fun valorTras(linea: String, etiqueta: String): String? {
        val i = linea.lastIndexOf(etiqueta)
        if (i < 0) return null
        return linea.substring(i + etiqueta.length).trim().substringBefore(' ')
    }

    private fun campo(l: String, k: String): String? =
        Regex("(?:^|\\s)$k=(\\S+)").find(l)?.groupValues?.get(1)

    /** `2026-11-16T03:12:06.001` (local time) → epoch millis. */
    fun fecha(t: String, tz: TimeZone): Long? {
        val limpio = t.trim()
        for (p in listOf("yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd HH:mm:ss")) {
            try {
                return SimpleDateFormat(p, Locale.ROOT).apply { timeZone = tz; isLenient = false }.parse(limpio)?.time
            } catch (_: ParseException) {
            }
        }
        return null
    }

    /** `IP address 192.168.1.5/24 Gateway 192.168.1.1  DNS servers: [ 1.1.1.1 ] Domains ` → plain words. */
    fun ipFija(t: String): String {
        val ip = Regex("IP address (\\S+)").find(t)?.groupValues?.get(1)
        // Without a gateway Android writes "Gateway  DNS servers: …".
        val gw = Regex("Gateway (\\S+)").find(t)?.groupValues?.get(1)?.takeIf { it != "DNS" }
        val dns = Regex("DNS servers: \\[([^\\]]*)\\]").find(t)?.groupValues?.get(1)?.trim()?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }.orEmpty()
        if (ip == null && gw == null && dns.isEmpty()) return t
        return buildList {
            if (ip != null) add("IP $ip")
            if (gw != null) add("puerta de enlace $gw")
            if (dns.isNotEmpty()) add("DNS " + dns.joinToString(", "))
        }.joinToString(" · ")
    }

    /** `[proxy.casa] 8080 xl=a,b` → `proxy.casa:8080`; `PAC Script: http://…` → that address. */
    fun proxy(t: String): String {
        if (t.startsWith("PAC Script:")) return t.substringAfter(':').trim().substringBefore("[").trim()
        val m = Regex("^\\[([^\\]]*)\\]\\s*(\\d+)").find(t) ?: return t
        return m.groupValues[1] + ":" + m.groupValues[2]
    }
}
