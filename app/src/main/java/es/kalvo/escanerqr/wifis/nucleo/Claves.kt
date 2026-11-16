package es.kalvo.escanerqr.wifis.nucleo

/** Small helpers for names and passwords as Android stores them. */
object Claves {
    private fun esHex(s: String) = s.isNotEmpty() && s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /**
     * Android keeps a text name/password between quotes (`"Casa"`) and a raw one in hex without quotes.
     * Returns the readable text. A hex name that is not valid UTF-8 stays as hex.
     */
    fun quitarComillas(s: String?): String? {
        if (s == null) return null
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) return s.substring(1, s.length - 1)
        return s
    }

    fun ssidLegible(s: String?): String? {
        if (s == null) return null
        if (s.length >= 2 && s.startsWith('"') && s.endsWith('"')) return s.substring(1, s.length - 1)
        if (esHex(s) && s.length % 2 == 0) {
            val bytes = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
            val dec = java.nio.charset.StandardCharsets.UTF_8.newDecoder()
            return try { dec.decode(java.nio.ByteBuffer.wrap(bytes)).toString() } catch (_: Exception) { s }
        }
        return s
    }

    /** The PSK as the user typed it: text without quotes, or the 64-hex raw key as it is. "*" or empty = unknown. */
    fun claveLegible(s: String?): String? {
        if (s.isNullOrEmpty() || s == "*") return null
        return quitarComillas(s)?.takeIf { it.isNotEmpty() }
    }

    fun esClaveHexWpa(c: String) = c.length == 64 && esHex(c)
    fun esClaveHexWep(c: String) = (c.length == 10 || c.length == 26) && esHex(c)

    /** Problem with the name, in plain words, or null if it is fine. */
    fun problemaNombre(ssid: String): String? = when {
        ssid.isEmpty() -> "Escribe el nombre de la red"
        ssid.toByteArray(Charsets.UTF_8).size > 32 -> "El nombre es demasiado largo (máximo 32 letras sin tildes)"
        else -> null
    }

    /** Problem with the password for that type, in plain words, or null if it is fine. */
    fun problemaClave(seg: Seguridad, clave: String?): String? {
        val c = clave.orEmpty()
        return when (seg) {
            Seguridad.ABIERTA, Seguridad.OWE -> null
            Seguridad.EMPRESA -> "Las redes de empresa hay que añadirlas en Ajustes"
            Seguridad.WPA -> when {
                c.isEmpty() -> "Escribe la contraseña"
                esClaveHexWpa(c) -> null
                c.any { it.code < 32 || it.code > 126 } -> "La contraseña solo puede llevar letras sin tildes, números y signos normales"
                c.length < 8 -> "La contraseña tiene que tener al menos 8 caracteres"
                c.length > 63 -> "La contraseña puede tener como mucho 63 caracteres"
                else -> null
            }
            Seguridad.WPA3 -> when {
                c.isEmpty() -> "Escribe la contraseña"
                c.length > 128 -> "La contraseña es demasiado larga"
                else -> null
            }
            Seguridad.WEP -> when {
                c.isEmpty() -> "Escribe la contraseña"
                esClaveHexWep(c) || c.length == 5 || c.length == 13 -> null
                else -> "Una clave WEP tiene 5 o 13 caracteres (o 10 o 26 en hexadecimal)"
            }
        }
    }

    /** Lowercase without accents, for the search box. */
    fun paraBuscar(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
}
