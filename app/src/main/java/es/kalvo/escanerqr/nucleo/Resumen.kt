package es.kalvo.escanerqr.nucleo

/** Colour family of a read (icon square in the history and in the result, S-J4 «estilo A»). */
enum class Clase { WIFI, ENLACE, EQUIPO, BARRAS, OTRO }

/**
 * One history row: [principal] (big text), [tipo] and [detalle] for the line under it («tipo · detalle · hora»),
 * [clase] for the icon colour, [mono] = draw [principal] in monospace (MAC, SIM, codes). Pure Kotlin (tested).
 */
data class Resumen(val principal: String, val tipo: String, val detalle: String, val clase: Clase, val mono: Boolean)

object Resumenes {
    fun de(texto: String, formato: String): Resumen {
        val c = Contenido.analizar(texto, formato)
        val matriz = Formatos.esMatriz(formato)
        val formatoCorto = if (formato == "QR Code") "QR" else formato
        return when (val e = Especial.de(texto, c)) {
            is Especial.Equipo -> Resumen(
                if (e.macs.size == 1) e.macs[0] else e.macs.joinToString(", "),
                e.tipo, e.modelo.ifEmpty { e.serie }, Clase.EQUIPO, mono = true,
            )
            is Especial.Sim -> Resumen(Especial.agrupar4(e.iccid), "SIM", formatoCorto, Clase.EQUIPO, mono = true)
            is Especial.Articulo -> Resumen(
                e.modelo.ifEmpty { e.codigo }, "Artículo",
                listOf(if (e.modelo.isEmpty()) "" else e.codigo, e.serie).filter { it.isNotEmpty() }.joinToString(" · "),
                Clase.EQUIPO, mono = e.modelo.isEmpty(),
            )
            null -> when (c) {
                is Contenido.Wifi -> Resumen(c.ssid, "Wifi", "", Clase.WIFI, mono = false)
                is Contenido.Enlace -> Resumen(sinEsquema(c.url), "Enlace", "", Clase.ENLACE, mono = false)
                is Contenido.Contacto -> Resumen(c.nombre.ifEmpty { c.telefonos.firstOrNull() ?: c.emails.firstOrNull() ?: "Contacto" },
                    "Contacto", "", Clase.OTRO, mono = false)
                is Contenido.Telefono -> Resumen(c.numero, "Teléfono", "", Clase.OTRO, mono = false)
                is Contenido.Email -> Resumen(c.para, "Correo", "", Clase.OTRO, mono = false)
                is Contenido.Sms -> Resumen(c.numero, "SMS", "", Clase.OTRO, mono = false)
                is Contenido.Geo -> Resumen(c.consulta.ifEmpty { "${c.lat}, ${c.lon}" }, "Ubicación", "", Clase.OTRO, mono = false)
                is Contenido.Evento -> Resumen(c.titulo.ifEmpty { "Evento" }, "Evento", "", Clase.OTRO, mono = false)
                is Contenido.Producto -> Resumen(c.codigo, formato, "", Clase.BARRAS, mono = false)
                is Contenido.Texto -> {
                    val linea = texto.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · ")
                    if (matriz) Resumen(linea, formatoCorto, "", Clase.OTRO, mono = false)
                    else Resumen(linea, formato, "", Clase.BARRAS, mono = false)
                }
            }
        }
    }

    /** "https://qrw.es/Eh57cP" → "qrw.es/Eh57cP" (the row is short; the result shows the whole link). */
    fun sinEsquema(url: String): String = url.replace(Regex("^(?i)(https?://)(www\\.)?"), "").removeSuffix("/")

    /** «tipo · detalle · hora · importado de … · notas» with the empty ones left out ([origen] only for imported reads, S-J5). */
    fun lineaDebajo(r: Resumen, hora: String, notas: String, origen: String = ""): String =
        listOf(r.tipo, r.detalle, hora, origen, notas.replace('\n', ' ')).filter { it.isNotBlank() }.joinToString(" · ")
}
