package es.kalvo.escanerqr.nucleo

/** What a scanned text means, so the result screen can offer the right buttons. Pure Kotlin (tested on the laptop). */
sealed class Contenido {
    abstract val tipo: String  // label shown to the user

    data class Enlace(val url: String) : Contenido() { override val tipo = "Sitio web" }

    /** [seguridad] as written in the code (WPA, WPA2-PSK-AES, WEP, nopass…); [tipoMisWifis] is the normalised type the Wifis part saves (WPA, SAE, WEP, nopass). */
    data class Wifi(val ssid: String, val clave: String, val seguridad: String, val oculta: Boolean) : Contenido() {
        override val tipo = "Wifi"
        val tipoMisWifis: String get() = tipoParaMisWifis(seguridad, clave)
    }

    data class Telefono(val numero: String) : Contenido() { override val tipo = "Teléfono" }
    data class Email(val para: String, val asunto: String, val cuerpo: String) : Contenido() { override val tipo = "Correo electrónico" }
    data class Sms(val numero: String, val mensaje: String) : Contenido() { override val tipo = "SMS" }
    data class Geo(val lat: Double, val lon: Double, val consulta: String) : Contenido() { override val tipo = "Ubicación" }

    data class Contacto(
        val nombre: String,
        val telefonos: List<String>,
        val emails: List<String>,
        val empresa: String,
        val cargo: String,
        val direccion: String,
        val web: String,
        val nota: String,
    ) : Contenido() { override val tipo = "Contacto" }

    /** [inicio]/[fin] as written (yyyyMMdd or yyyyMMdd'T'HHmmss[Z]); [todoElDia] when there is no time. */
    data class Evento(val titulo: String, val inicio: String, val fin: String, val lugar: String, val descripcion: String) : Contenido() {
        override val tipo = "Evento"
    }

    data class Producto(val codigo: String) : Contenido() { override val tipo = "Producto" }
    data class Texto(val texto: String) : Contenido() { override val tipo = "Texto" }

    companion object {
        /** Formats that are shop product codes: «Buscar producto» instead of «Buscar en la web». */
        private val FORMATOS_PRODUCTO = setOf("EAN-13", "EAN-13/JAN", "EAN-8", "UPC-A", "UPC-E", "ISBN")

        fun analizar(texto: String, formato: String = "QR Code"): Contenido {
            val t = texto.trim()
            if (formato in FORMATOS_PRODUCTO && t.all { it.isDigit() }) return Producto(t)
            val mayus = t.uppercase()
            return when {
                mayus.startsWith("WIFI:") -> leerWifi(t) ?: Texto(texto)
                mayus.startsWith("BEGIN:VCARD") -> leerVcard(t)
                mayus.startsWith("MECARD:") -> leerMecard(t)
                mayus.startsWith("BEGIN:VCALENDAR") || mayus.startsWith("BEGIN:VEVENT") -> leerEvento(t)
                mayus.startsWith("TEL:") -> Telefono(t.substring(4).trim())
                mayus.startsWith("MAILTO:") -> leerMailto(t)
                mayus.startsWith("MATMSG:") -> leerMatmsg(t)
                mayus.startsWith("SMSTO:") || mayus.startsWith("MMSTO:") -> leerSmsto(t)
                mayus.startsWith("SMS:") -> leerSmsUri(t)
                mayus.startsWith("GEO:") -> leerGeo(t) ?: Texto(texto)
                esEnlace(t) -> Enlace(normalizarEnlace(t))
                else -> Texto(texto)
            }
        }

        // ---------- enlaces ----------

        private val ESQUEMAS = listOf("http://", "https://", "ftp://", "market://", "rtsp://", "intent://")

        fun esEnlace(t: String): Boolean {
            if (t.isEmpty() || t.any { it.isWhitespace() }) return false
            val l = t.lowercase()
            if (ESQUEMAS.any { l.startsWith(it) }) return l.length > l.indexOf("://") + 3
            // Bare "www.example.com/…" also counts.
            return l.startsWith("www.") && l.length > 6 && l.substring(4).contains('.')
        }

        fun normalizarEnlace(t: String): String = if (t.lowercase().startsWith("www.")) "https://$t" else t

        // ---------- wifi ----------

        /**
         * `WIFI:S:<ssid>;T:<WPA|WEP|nopass|…>;P:<clave>;H:<true|false>;;` in any order. `\;` `\,` `\:` `\\` `\"` are escapes.
         * QR Bot also saved multi-line ones (`WIFI:S:x;\nT:WPA2-PSK-AES;\nP:y;;`): leading line breaks before a key are ignored.
         */
        fun leerWifi(t: String): Wifi? {
            val cuerpo = t.substring(5)
            val partes = partirEscapado(cuerpo, ';')
            var ssid: String? = null
            var clave = ""
            var seg = ""
            var oculta = false
            for (p in partes) {
                val parte = p.trimStart()
                val dos = parte.indexOf(':')
                if (dos <= 0) continue
                val clav = parte.substring(0, dos).trim().uppercase()
                val valor = desescapar(parte.substring(dos + 1))
                when (clav) {
                    "S" -> ssid = valor
                    "P" -> clave = valor
                    "T" -> seg = valor.trim()
                    "H" -> oculta = valor.trim().equals("true", ignoreCase = true)
                }
            }
            val nombre = ssid ?: return null
            if (nombre.isEmpty()) return null
            return Wifi(nombre, clave, seg, oculta)
        }

        /** The four types the Wifis part saves: WPA, SAE, WEP and nopass (was the contract with «Mis wifis» until v2.0). */
        fun tipoParaMisWifis(seguridad: String, clave: String): String {
            val s = seguridad.uppercase()
            return when {
                s == "NOPASS" || (s.isEmpty() && clave.isEmpty()) -> "nopass"
                s.contains("SAE") || s.contains("WPA3") -> "SAE"
                s == "WEP" -> "WEP"
                else -> "WPA" // WPA, WPA2, WPA2-PSK-AES, WPA/WPA2, empty with a key…
            }
        }

        /** Split at [sep] that is not escaped with a backslash; the escapes stay for [desescapar]. */
        fun partirEscapado(s: String, sep: Char): List<String> {
            val out = ArrayList<String>()
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { sb.append(c).append(s[i + 1]); i += 2; continue }
                if (c == sep) { out.add(sb.toString()); sb.setLength(0) } else sb.append(c)
                i++
            }
            if (sb.isNotEmpty()) out.add(sb.toString())
            return out
        }

        fun desescapar(s: String): String {
            val sb = StringBuilder()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) { sb.append(s[i + 1]); i += 2; continue }
                sb.append(c); i++
            }
            return sb.toString()
        }

        // ---------- correo, teléfono, sms, geo ----------

        private fun leerMailto(t: String): Email {
            val resto = t.substring(7)
            val q = resto.indexOf('?')
            val para = decodificarUrl(if (q >= 0) resto.substring(0, q) else resto, masEsEspacio = false)
            val params = if (q >= 0) parametros(resto.substring(q + 1)) else emptyMap()
            return Email(para, params["subject"] ?: "", params["body"] ?: "")
        }

        private fun leerMatmsg(t: String): Email {
            val campos = camposMecard(t.substring(7))
            return Email(campos["TO"]?.firstOrNull() ?: "", campos["SUB"]?.firstOrNull() ?: "", campos["BODY"]?.firstOrNull() ?: "")
        }

        /** `SMSTO:<número>:<mensaje>` (the message may contain ':'). */
        private fun leerSmsto(t: String): Sms {
            val resto = t.substring(6)
            val dos = resto.indexOf(':')
            return if (dos >= 0) Sms(resto.substring(0, dos).trim(), resto.substring(dos + 1)) else Sms(resto.trim(), "")
        }

        /** `sms:<número>?body=<mensaje>` (also `sms:<número>;?body=` seen in the wild). */
        private fun leerSmsUri(t: String): Sms {
            val resto = t.substring(4)
            val q = resto.indexOf('?')
            val num = (if (q >= 0) resto.substring(0, q) else resto).trimEnd(';').trim()
            val params = if (q >= 0) parametros(resto.substring(q + 1)) else emptyMap()
            return Sms(decodificarUrl(num, masEsEspacio = false), params["body"] ?: "")
        }

        /** `geo:<lat>,<lon>[,<alt>][;u=…][?q=…]`. */
        fun leerGeo(t: String): Geo? {
            val resto = t.substring(4)
            val q = resto.indexOf('?')
            val coords = (if (q >= 0) resto.substring(0, q) else resto).substringBefore(';')
            val partes = coords.split(',')
            if (partes.size < 2) return null
            val lat = partes[0].trim().toDoubleOrNull() ?: return null
            val lon = partes[1].trim().toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            val params = if (q >= 0) parametros(resto.substring(q + 1)) else emptyMap()
            return Geo(lat, lon, params["q"] ?: "")
        }

        private fun parametros(q: String): Map<String, String> =
            q.split('&').mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null else it.substring(0, i).lowercase() to decodificarUrl(it.substring(i + 1))
            }.toMap()

        /**
         * %XX (and '+' as a space in query values) decoding without android.net.Uri (so it runs in unit tests).
         * Not for the number/address part: there '+' is a real '+' (+34…, ana+tienda@…). Bad escapes are left as they are.
         */
        fun decodificarUrl(s: String, masEsEspacio: Boolean = true): String {
            val bytes = java.io.ByteArrayOutputStream()
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '%' && i + 2 < s.length) {
                    val hex = s.substring(i + 1, i + 3)
                    val h = if (hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) hex.toInt(16) else null
                    if (h != null) { bytes.write(h); i += 3; continue }
                }
                if (c == '+' && masEsEspacio) { bytes.write(' '.code); i++; continue }
                bytes.write(c.toString().toByteArray(Charsets.UTF_8)); i++
            }
            return bytes.toString(Charsets.UTF_8.name())
        }

        // ---------- contactos ----------

        /** MECARD-style `KEY:value;KEY:value;;` (also MATMSG). Repeated keys keep every value. */
        fun camposMecard(s: String): Map<String, List<String>> {
            val out = LinkedHashMap<String, MutableList<String>>()
            for (p in partirEscapado(s, ';')) {
                val parte = p.trimStart()
                val dos = parte.indexOf(':')
                if (dos <= 0) continue
                out.getOrPut(parte.substring(0, dos).trim().uppercase()) { ArrayList() }.add(desescapar(parte.substring(dos + 1)))
            }
            return out
        }

        private fun leerMecard(t: String): Contacto {
            val c = camposMecard(t.substring(7))
            // MECARD N: is "Apellido,Nombre".
            val n = c["N"]?.firstOrNull() ?: ""
            val nombre = if (n.contains(',')) n.split(',').map { it.trim() }.filter { it.isNotEmpty() }.reversed().joinToString(" ") else n
            return Contacto(
                nombre = nombre,
                telefonos = c["TEL"] ?: emptyList(),
                emails = c["EMAIL"] ?: emptyList(),
                empresa = c["ORG"]?.firstOrNull() ?: "",
                cargo = c["TITLE"]?.firstOrNull() ?: "",
                direccion = (c["ADR"]?.firstOrNull() ?: "").replace(',', ' ').trim(),
                web = c["URL"]?.firstOrNull() ?: "",
                nota = c["NOTE"]?.firstOrNull() ?: "",
            )
        }

        /** vCard lines with folding undone: `KEY;PARAMS:value` → (KEY, value). */
        fun lineasVcard(t: String): List<Pair<String, String>> {
            val unidas = t.replace("\r\n", "\n").replace("\r", "\n").replace(Regex("\n[ \t]"), "")
            return unidas.split('\n').mapNotNull { l ->
                val dos = l.indexOf(':')
                if (dos <= 0) null else {
                    val clave = l.substring(0, dos).substringBefore(';').trim().uppercase().substringAfter('.') // "item1.TEL"
                    clave to l.substring(dos + 1).trim()
                }
            }
        }

        private fun desescaparVcard(v: String) = v.replace("\\n", "\n").replace("\\N", "\n").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

        private fun leerVcard(t: String): Contacto {
            val ls = lineasVcard(t)
            fun uno(k: String) = ls.firstOrNull { it.first == k }?.second?.let { desescaparVcard(it) } ?: ""
            fun todos(k: String) = ls.filter { it.first == k }.map { desescaparVcard(it.second) }.filter { it.isNotEmpty() }
            var nombre = uno("FN")
            if (nombre.isEmpty()) {
                // N:Apellido;Nombre;Segundo;Prefijo;Sufijo
                val n = ls.firstOrNull { it.first == "N" }?.second?.split(';') ?: emptyList()
                nombre = listOfNotNull(n.getOrNull(3), n.getOrNull(1), n.getOrNull(2), n.getOrNull(0), n.getOrNull(4))
                    .map { desescaparVcard(it).trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            }
            // S-J9: an escaped "\;" inside a part (as «Crear» writes it) does not split the address.
            val adr = ls.firstOrNull { it.first == "ADR" }?.second?.let { partirEscapado(it, ';') }?.map { desescaparVcard(it).trim() }?.filter { it.isNotEmpty() }
                ?.joinToString(", ") ?: ""
            return Contacto(
                nombre = nombre,
                telefonos = todos("TEL").map { it.removePrefix("tel:") },
                emails = todos("EMAIL"),
                empresa = uno("ORG").replace(';', ' ').trim(),
                cargo = uno("TITLE"),
                direccion = adr,
                web = uno("URL"),
                nota = uno("NOTE"),
            )
        }

        // ---------- eventos ----------

        private fun leerEvento(t: String): Evento {
            val ls = lineasVcard(t)
            fun uno(k: String) = ls.firstOrNull { it.first == k }?.second?.let { desescaparVcard(it) } ?: ""
            return Evento(uno("SUMMARY"), uno("DTSTART"), uno("DTEND"), uno("LOCATION"), uno("DESCRIPTION"))
        }

        /**
         * iCalendar date to epoch millis: `yyyyMMdd` (all day, local midnight), `yyyyMMdd'T'HHmmss` (local) or with `Z` (UTC).
         * Null if it cannot be read.
         */
        fun fechaEvento(v: String, zona: java.util.TimeZone = java.util.TimeZone.getDefault()): Long? {
            val s = v.trim()
            val fmt = when {
                Regex("""\d{8}""").matches(s) -> "yyyyMMdd"
                Regex("""\d{8}T\d{6}Z""").matches(s) -> "yyyyMMdd'T'HHmmss'Z'"
                Regex("""\d{8}T\d{6}""").matches(s) -> "yyyyMMdd'T'HHmmss"
                Regex("""\d{8}T\d{4}""").matches(s) -> "yyyyMMdd'T'HHmm"
                else -> return null
            }
            val f = java.text.SimpleDateFormat(fmt, java.util.Locale.ROOT)
            f.isLenient = false
            f.timeZone = if (s.endsWith("Z")) java.util.TimeZone.getTimeZone("UTC") else zona
            return try { f.parse(s)?.time } catch (e: java.text.ParseException) { null }
        }

        fun esTodoElDia(v: String) = Regex("""\d{8}""").matches(v.trim())
    }
}
