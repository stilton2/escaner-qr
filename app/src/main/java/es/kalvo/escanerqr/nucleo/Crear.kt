package es.kalvo.escanerqr.nucleo

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * «Crear» tab (S-J9): the text that goes inside each kind of code, in the same formats the reader understands
 * ([Contenido.analizar]), so that scanning a code made here gives back what was typed. Pure Kotlin (tested on the laptop).
 * The wifi text is made by `wifis.nucleo.TextoQr.crear` (already escapes `\ ; , : "`).
 */
object Crear {

    /** What can be made, in the order of the list. [titulo] and [ayuda] are shown on screen. */
    enum class Tipo(val titulo: String, val ayuda: String) {
        TEXTO("Texto", "Cualquier texto o nota"),
        ENLACE("Enlace", "Una página web"),
        WIFI("Wifi", "Para conectarse sin escribir la contraseña"),
        TELEFONO("Teléfono", "Para llamar a un número"),
        SMS("SMS", "Un número y un mensaje ya escritos"),
        CORREO("Correo", "Dirección, asunto y texto"),
        CONTACTO("Contacto", "Tarjeta de contacto (vCard)"),
        UBICACION("Ubicación", "Un punto en el mapa"),
        EVENTO("Evento", "Una cita para el calendario"),
        PORTAPAPELES("Portapapeles", "Lo que tengas copiado"),
        BARRAS("Código de barras", "EAN, UPC, Code 128, Code 39, ITF, Data Matrix, Aztec, PDF 417"),
    }

    // ---------- texto, enlace, teléfono, sms, correo ----------

    /** A link as typed: trimmed; with no scheme, `https://` in front ("ejemplo.es" → "https://ejemplo.es"). */
    fun enlace(u: String): String {
        val t = u.trim()
        return if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(t)) t else "https://$t"
    }

    fun problemaEnlace(u: String): String? {
        val t = u.trim()
        return when {
            t.isEmpty() -> "Escribe la dirección"
            t.any { it.isWhitespace() } -> "Una dirección no lleva espacios"
            !t.contains('.') && !t.contains("://") -> "No parece una dirección (le falta el punto, como en ejemplo.es)"
            else -> null
        }
    }

    /** Only digits, and `+ * #` (a '+' only at the start): "+34 600 11-22-33" → "+34600112233". */
    fun limpiarNumero(n: String): String {
        val t = n.trim()
        val sb = StringBuilder()
        for (c in t) if (c.isDigit() || c == '*' || c == '#' || (c == '+' && sb.isEmpty())) sb.append(c)
        return sb.toString()
    }

    fun problemaNumero(n: String): String? = if (limpiarNumero(n).count { it.isDigit() } < 3) "Escribe el número de teléfono" else null

    fun telefono(n: String): String = "tel:" + limpiarNumero(n)

    /** `SMSTO:<number>:<message>` (what [Contenido] reads; the message may carry ':'). */
    fun sms(numero: String, mensaje: String): String = "SMSTO:" + limpiarNumero(numero) + ":" + mensaje

    fun problemaCorreo(c: String): String? {
        val t = c.trim()
        return when {
            t.isEmpty() -> "Escribe la dirección de correo"
            t.any { it.isWhitespace() } || !Regex("^[^@]+@[^@]+\\.[^@]+$").matches(t) -> "No parece una dirección de correo (nombre@sitio.es)"
            else -> null
        }
    }

    /** `mailto:<to>?subject=…&body=…` (RFC 6068, read by every phone camera), with spaces as %20. */
    fun correo(para: String, asunto: String, cuerpo: String): String {
        val sb = StringBuilder("mailto:").append(codificar(para.trim(), "@+.-_~!$'*"))
        val params = listOfNotNull(
            asunto.takeIf { it.isNotEmpty() }?.let { "subject=" + codificar(it) },
            cuerpo.takeIf { it.isNotEmpty() }?.let { "body=" + codificar(it) },
        )
        if (params.isNotEmpty()) sb.append('?').append(params.joinToString("&"))
        return sb.toString()
    }

    /** Percent-encoding (UTF-8) of everything but letters, digits and [libres]. */
    fun codificar(s: String, libres: String = "-_.~"): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = (b.toInt() and 0xFF).toChar()
            if (c.code < 128 && (c.isLetterOrDigit() || c in libres)) sb.append(c)
            else sb.append('%').append("%02X".format(b.toInt() and 0xFF))
        }
        return sb.toString()
    }

    // ---------- contacto (vCard 3.0) ----------

    data class Ficha(
        val nombre: String,
        val telefono: String = "",
        val correo: String = "",
        val empresa: String = "",
        val direccion: String = "",
        val web: String = "",
    )

    /** vCard text value: `\` `;` `,` escaped and line breaks as `\n` (RFC 6350 § 3.4). */
    fun escaparVcard(s: String): String = buildString {
        for (c in s.replace("\r\n", "\n").replace('\r', '\n')) when (c) {
            '\\' -> append("\\\\")
            ';' -> append("\\;")
            ',' -> append("\\,")
            '\n' -> append("\\n")
            else -> append(c)
        }
    }

    /**
     * vCard 3.0 with only the filled fields. `N:` takes the first word as the given name and the rest as surnames
     * ("Ana García López" → `N:García López;Ana;;;`), the usual split for Spanish names; `FN:` keeps it whole.
     */
    fun contacto(f: Ficha): String {
        val nombre = f.nombre.trim().replace(Regex("\\s+"), " ")
        val partes = nombre.split(' ')
        val dado = partes.firstOrNull().orEmpty()
        val apellidos = partes.drop(1).joinToString(" ")
        return buildString {
            append("BEGIN:VCARD\nVERSION:3.0\n")
            append("N:").append(escaparVcard(apellidos)).append(';').append(escaparVcard(dado)).append(";;;\n")
            append("FN:").append(escaparVcard(nombre)).append('\n')
            if (f.empresa.isNotBlank()) append("ORG:").append(escaparVcard(f.empresa.trim())).append('\n')
            if (f.telefono.isNotBlank()) append("TEL:").append(limpiarNumero(f.telefono)).append('\n')
            if (f.correo.isNotBlank()) append("EMAIL:").append(f.correo.trim()).append('\n')
            // ADR: PO box; extended; street; city; region; code; country. All of it in "street".
            if (f.direccion.isNotBlank()) append("ADR:;;").append(escaparVcard(f.direccion.trim())).append(";;;;\n")
            if (f.web.isNotBlank()) append("URL:").append(enlace(f.web)).append('\n')
            append("END:VCARD")
        }
    }

    fun problemaContacto(f: Ficha): String? =
        if (f.nombre.isBlank() && f.telefono.isBlank() && f.correo.isBlank()) "Pon al menos el nombre, un teléfono o un correo" else null

    // ---------- ubicación ----------

    /** `geo:<lat>,<lon>` with at most 6 decimals and a dot, whatever the phone language. */
    fun ubicacion(lat: Double, lon: Double): String = "geo:" + numero(lat) + "," + numero(lon)

    private fun numero(d: Double): String {
        val s = String.format(Locale.ROOT, "%.6f", d).trimEnd('0').trimEnd('.')
        return if (s == "-0") "0" else s
    }

    /** A number typed with a comma or a dot ("40,4168" or "40.4168"); null if it is not one. */
    fun leerNumero(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

    fun problemaLatitud(s: String): String? = leerNumero(s).let { if (it == null || it !in -90.0..90.0) "Latitud: un número entre -90 y 90" else null }
    fun problemaLongitud(s: String): String? = leerNumero(s).let { if (it == null || it !in -180.0..180.0) "Longitud: un número entre -180 y 180" else null }

    /**
     * Two coordinates found in a pasted text: "40.4168, -3.7038", "40.4168 -3.7038", a `geo:` text or a maps link
     * (`…/@40.4168,-3.7038,15z`, `…?q=40.4168,-3.7038`). Decimals must use a dot here (a comma separates the two).
     */
    fun coordenadas(texto: String): Pair<Double, Double>? {
        val m = Regex("""(-?\d{1,3}\.\d+)\s*[,;\s]\s*(-?\d{1,3}\.\d+)""").find(texto) ?: return null
        val lat = m.groupValues[1].toDoubleOrNull() ?: return null
        val lon = m.groupValues[2].toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return lat to lon
    }

    // ---------- evento (iCalendar VEVENT) ----------

    /** iCalendar text value: same escapes as the vCard. */
    fun escaparIcal(s: String) = escaparVcard(s)

    /**
     * `BEGIN:VEVENT … END:VEVENT` with local («floating») times `yyyyMMdd'T'HHmmss` as [Contenido.fechaEvento] reads them.
     * [todoElDia]: `DTSTART;VALUE=DATE:yyyyMMdd` and DTEND the day after the last one (the end is exclusive in iCalendar).
     */
    fun evento(titulo: String, inicio: Long, fin: Long, lugar: String, todoElDia: Boolean, zona: TimeZone = TimeZone.getDefault()): String {
        fun f(p: String, t: Long) = SimpleDateFormat(p, Locale.ROOT).apply { timeZone = zona }.format(java.util.Date(t))
        return buildString {
            append("BEGIN:VEVENT\n")
            append("SUMMARY:").append(escaparIcal(titulo.trim())).append('\n')
            if (todoElDia) {
                append("DTSTART;VALUE=DATE:").append(f("yyyyMMdd", inicio)).append('\n')
                append("DTEND;VALUE=DATE:").append(f("yyyyMMdd", diaSiguiente(fin, zona))).append('\n')
            } else {
                append("DTSTART:").append(f("yyyyMMdd'T'HHmmss", inicio)).append('\n')
                append("DTEND:").append(f("yyyyMMdd'T'HHmmss", fin)).append('\n')
            }
            if (lugar.isNotBlank()) append("LOCATION:").append(escaparIcal(lugar.trim())).append('\n')
            append("END:VEVENT")
        }
    }

    private fun diaSiguiente(t: Long, zona: TimeZone): Long =
        java.util.Calendar.getInstance(zona).apply { timeInMillis = t; add(java.util.Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    fun problemaEvento(titulo: String, inicio: Long, fin: Long, todoElDia: Boolean, zona: TimeZone = TimeZone.getDefault()): String? {
        if (titulo.isBlank()) return "Escribe el título"
        if (todoElDia) {
            val d = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { timeZone = zona }
            if (d.format(java.util.Date(fin)) < d.format(java.util.Date(inicio))) return "El último día no puede ser antes del primero"
        } else if (fin < inicio) return "El final no puede ser antes del principio"
        return null
    }

    // ---------- wifi: the shown text with the password hidden ----------

    /** The `P:` value of a wifi text replaced by dots (for the screen; the code itself keeps it). */
    fun ocultarClaveWifi(texto: String): String {
        if (!texto.trimStart().startsWith("WIFI:", ignoreCase = true)) return texto
        // Walk the fields (an escaped ';' does not end one); the field that starts with "P:" gets its value hidden.
        var inicio = texto.indexOf(':') + 1
        var i = inicio
        while (i <= texto.length) {
            if (i < texto.length && texto[i] == '\\') { i += 2; continue }
            if (i == texto.length || texto[i] == ';') {
                val campo = texto.substring(inicio, i)
                val sin = campo.trimStart()
                if (sin.startsWith("P:", ignoreCase = true) && sin.length > 2) {
                    val desde = inicio + (campo.length - sin.length) + 2
                    return texto.substring(0, desde) + "••••••" + texto.substring(i)
                }
                inicio = i + 1
            }
            i++
        }
        return texto
    }
}
