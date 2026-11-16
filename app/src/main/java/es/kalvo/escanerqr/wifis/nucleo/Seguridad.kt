package es.kalvo.escanerqr.wifis.nucleo

/**
 * Security type of a saved network, in plain words.
 * [qr] = value of `T:` in the standard wifi QR, [cmd] = type for `cmd wifi add-network`.
 */
enum class Seguridad(val nombre: String, val qr: String, val cmd: String, val llevaClave: Boolean) {
    WPA("WPA2/WPA3", "WPA", "wpa2", true),
    WPA3("Solo WPA3", "SAE", "wpa3", true),
    WEP("WEP (antigua)", "WEP", "wep", true),
    ABIERTA("Abierta", "nopass", "open", false),
    OWE("Abierta cifrada (OWE)", "nopass", "owe", false),
    EMPRESA("Empresa (usuario y certificado)", "", "", true);

    /** Can it be written to a QR / added by hand from this app? (Enterprise needs user, certificate… not supported.) */
    val sencilla get() = this != EMPRESA

    companion object {
        /** The types the user can pick when adding a network by hand. */
        val elegibles = listOf(WPA, WPA3, WEP, ABIERTA)

        /**
         * From the type words of `cmd wifi list-networks` or of the suggestions in `dumpsys wifi`
         * (`wpa2-psk`, `wpa3-sae^`, `open`, `owe^`, `wep`, `wpa2-eap`…; `^` = added by Android itself).
         * A network that is "wpa2-psk + wpa3-sae^" is a normal WPA2 network.
         */
        fun desdeTipos(tipos: Collection<String>): Seguridad {
            val t = tipos.map { it.trim().trimEnd('^').lowercase() }.filter { it.isNotEmpty() }
            return when {
                t.any { it.contains("psk") && !it.contains("sae") } -> WPA
                t.any { it.contains("sae") } -> WPA3
                t.any { it.contains("eap") || it.contains("suite") || it.contains("8021x") } -> EMPRESA
                t.any { it.contains("wep") } -> WEP
                t.any { it == "open" || it == "none" } -> ABIERTA
                t.any { it.contains("owe") } -> OWE
                else -> ABIERTA
            }
        }

        /** From the `T:` of a wifi QR or the `tipo` extra of the intent (lenient: WPA2, WPA3, open…). */
        fun desdeTexto(t: String?, hayClave: Boolean): Seguridad = when (t?.trim()?.uppercase()) {
            "WPA", "WPA2", "WPA/WPA2", "WPA2/WPA3", "PSK" -> WPA
            "SAE", "WPA3" -> WPA3
            "WEP" -> WEP
            "NOPASS", "OPEN", "ABIERTA", "NONE" -> ABIERTA
            "OWE" -> OWE
            null, "" -> if (hayClave) WPA else ABIERTA
            else -> if (hayClave) WPA else ABIERTA
        }
    }
}
