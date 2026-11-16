package es.kalvo.escanerqr.nucleo

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * One scan, with the same columns as QR Bot's CSV. The local date/time and the zone name are kept as written, so that
 * exporting what was imported gives back the same lines. [instante] (epoch millis, from the UTC columns) is for sorting.
 */
data class Lectura(
    val id: Long = 0,
    val fecha: String,      // local yyyy-MM-dd
    val hora: String,       // local HH:mm:ss
    val zona: String,       // e.g. "hora estándar de Europa central"
    val formato: String,    // "QR Code", "Code 128"…
    val texto: String,
    val notas: String = "",
    val favorito: Boolean = false,
    val fechaUtc: String,
    val horaUtc: String,
    val metadata: String = "",
    /** Where it came from (S-J5): "" = scanned with this app; otherwise the app or file it was imported from ("QR Bot"…). */
    val origen: String = "",
) {
    val importada: Boolean get() = origen.isNotEmpty()

    /** «escaneado» or «importado de QR Bot», for the history and the result. */
    val textoOrigen: String get() = if (origen.isEmpty()) "escaneado" else "importado de $origen"

    /** For files without dates (S-J5): same origin + format + text = "ya estaba" (the time is the import time, never equal). */
    val claveSinHora: String get() = "$origen\u0000$formato\u0000$texto"

    val instante: Long get() = Historial.instanteUtc(fechaUtc, horaUtc) ?: 0L

    /** Same scan = same UTC second + same format + same text (this is what "ya estaba" means when importing). */
    val clave: String get() = "$fechaUtc $horaUtc\u0000$formato\u0000$texto"
}

object Historial {
    val CABECERA = listOf("date", "time", "time_zone", "format", "text", "notes", "favorite", "date_utc", "time_utc", "metadata")

    /** Line end of our export. QR Bot's own could be CRLF; the reader takes both. */
    const val FIN_LINEA = "\n"

    private val ES = Locale.forLanguageTag("es-ES")

    private fun fmt(p: String, tz: TimeZone) = SimpleDateFormat(p, Locale.ROOT).apply { timeZone = tz; isLenient = false }

    fun instanteUtc(fecha: String, hora: String): Long? = try {
        fmt("yyyy-MM-dd HH:mm:ss", TimeZone.getTimeZone("UTC")).parse("$fecha $hora")?.time
    } catch (e: ParseException) { null }

    /** A new scan at [instante] in the phone's zone [tz]. Zone name in Spanish, like QR Bot writes it. */
    fun nueva(instante: Long, tz: TimeZone, formato: String, texto: String, metadata: String = ""): Lectura {
        val d = Date(instante)
        val utc = TimeZone.getTimeZone("UTC")
        return Lectura(
            fecha = fmt("yyyy-MM-dd", tz).format(d),
            hora = fmt("HH:mm:ss", tz).format(d),
            zona = tz.getDisplayName(tz.inDaylightTime(d), TimeZone.LONG, ES),
            formato = formato,
            texto = texto,
            fechaUtc = fmt("yyyy-MM-dd", utc).format(d),
            horaUtc = fmt("HH:mm:ss", utc).format(d),
            metadata = metadata,
        )
    }

    // ---------------- exportar ----------------

    /** Whole CSV, oldest first (as QR Bot's file). */
    fun exportar(lecturas: List<Lectura>): String {
        val sb = StringBuilder()
        sb.append(Csv.fila(CABECERA)).append(FIN_LINEA)
        for (l in lecturas.sortedWith(compareBy<Lectura> { it.instante }.thenBy { it.id })) {
            sb.append(Csv.fila(listOf(l.fecha, l.hora, l.zona, l.formato, l.texto, l.notas, if (l.favorito) "1" else "0",
                l.fechaUtc, l.horaUtc, l.metadata))).append(FIN_LINEA)
        }
        return sb.toString()
    }

    /** Plain text export: one scan per block, newest first. */
    fun exportarTexto(lecturas: List<Lectura>): String =
        lecturas.sortedByDescending { it.instante }.joinToString("\n\n") { l ->
            buildString {
                append(l.fecha).append(' ').append(l.hora).append(" · ").append(l.formato).append('\n').append(l.texto)
                if (l.notas.isNotEmpty()) append("\nNotas: ").append(l.notas)
            }
        } + "\n"

    // ---------------- importar ----------------

    data class Fallo(val linea: Int, val motivo: String, val muestra: String)
    data class Leido(val lecturas: List<Lectura>, val fallos: List<Fallo>, val noEsDeQrBot: Boolean = false)

    private val RE_FECHA = Regex("""\d{4}-\d{2}-\d{2}""")
    private val RE_HORA = Regex("""\d{2}:\d{2}:\d{2}""")

    /** Reads QR Bot's CSV (columns found by name, so the order does not matter). Rows that cannot be read go to [Leido.fallos]. */
    fun leerCsv(texto: String, tzSiFalta: TimeZone = TimeZone.getDefault()): Leido {
        val filas = Csv.leer(texto)
        if (filas.isEmpty()) return Leido(emptyList(), emptyList(), noEsDeQrBot = true)
        val cab = filas[0].campos.map { it.trim().lowercase() }
        val col = CABECERA.associateWith { cab.indexOf(it) }
        if (col["text"]!! < 0 || col["format"]!! < 0 || (col["date_utc"]!! < 0 && col["date"]!! < 0)) {
            return Leido(emptyList(), emptyList(), noEsDeQrBot = true)
        }
        val out = ArrayList<Lectura>()
        val fallos = ArrayList<Fallo>()
        val cortado = Csv.comillaSinCerrar(texto.removePrefix("﻿"))
        for ((n, f) in filas.withIndex()) {
            if (n == 0) continue
            val c = f.campos
            fun v(k: String): String { val i = col[k]!!; return if (i >= 0 && i < c.size) c[i] else "" }
            val muestra = c.joinToString(",").replace('\n', ' ').take(60)
            if (c.size == 1 && c[0].isBlank()) continue  // blank line
            if (n == filas.size - 1 && cortado) { fallos.add(Fallo(f.linea, "el fichero acaba a medias (comillas sin cerrar)", muestra)); continue }
            if (c.size < cab.size) { fallos.add(Fallo(f.linea, "faltan columnas (${c.size} de ${cab.size})", muestra)); continue }
            val txt = v("text")
            if (txt.isEmpty()) { fallos.add(Fallo(f.linea, "no tiene texto", muestra)); continue }
            val formato = v("format").ifEmpty { "QR Code" }
            var fUtc = v("date_utc").trim()
            var hUtc = v("time_utc").trim()
            var fLoc = v("date").trim()
            var hLoc = v("time").trim()
            val utcBien = RE_FECHA.matches(fUtc) && RE_HORA.matches(hUtc) && instanteUtc(fUtc, hUtc) != null
            val locBien = RE_FECHA.matches(fLoc) && RE_HORA.matches(hLoc)
            if (!utcBien) {
                if (!locBien) { fallos.add(Fallo(f.linea, "fecha u hora que no se entiende", muestra)); continue }
                // No UTC: take the local time in the phone's zone.
                val t = try { fmt("yyyy-MM-dd HH:mm:ss", tzSiFalta).parse("$fLoc $hLoc") } catch (e: ParseException) { null }
                if (t == null) { fallos.add(Fallo(f.linea, "fecha u hora que no se entiende", muestra)); continue }
                val utc = TimeZone.getTimeZone("UTC")
                fUtc = fmt("yyyy-MM-dd", utc).format(t); hUtc = fmt("HH:mm:ss", utc).format(t)
            } else if (!locBien) {
                val d = Date(instanteUtc(fUtc, hUtc)!!)
                fLoc = fmt("yyyy-MM-dd", tzSiFalta).format(d); hLoc = fmt("HH:mm:ss", tzSiFalta).format(d)
            }
            val fav = v("favorite").trim().let { it == "1" || it.equals("true", true) }
            out.add(Lectura(fecha = fLoc, hora = hLoc, zona = v("time_zone"), formato = formato, texto = txt, notas = v("notes"),
                favorito = fav, fechaUtc = fUtc, horaUtc = hUtc, metadata = v("metadata")))
        }
        return Leido(out, fallos)
    }

    data class Cuenta(val nuevas: List<Lectura>, val yaEstaban: Int)

    /**
     * Which of [leidas] are not already in [existentes] (nor repeated inside the file itself).
     * [sinFecha] (a file without dates, S-J5): they all carry the import time, so a read "ya estaba" when one with the same
     * origin, format and text is already there ([existentesSinHora] = [Lectura.claveSinHora] of the imported ones).
     */
    fun sinRepetir(leidas: List<Lectura>, existentes: Set<String>, sinFecha: Boolean = false, existentesSinHora: Set<String> = emptySet()): Cuenta {
        val vistas = HashSet(existentes)
        val vistas2 = HashSet(existentesSinHora)
        val nuevas = ArrayList<Lectura>()
        var ya = 0
        for (l in leidas) {
            val nueva = if (sinFecha) vistas2.add(l.claveSinHora) && vistas.add(l.clave) else vistas.add(l.clave)
            if (nueva) nuevas.add(l) else ya++
        }
        return Cuenta(nuevas, ya)
    }

    /** Marks the export of this app as JSON (so importing it back keeps where each read came from). */
    const val APP_JSON = "es.kalvo.escanerqr"

    /**
     * JSON export (S-J5): `{"app":"es.kalvo.escanerqr","version":1,"scans":[{…}]}`, oldest first, one read per line, with the
     * same names as the CSV columns plus `source` ("" = scanned with this app; else where it was imported from).
     */
    fun exportarJson(lecturas: List<Lectura>): String {
        val sb = StringBuilder()
        sb.append("{\"app\":").append(Json.cadena(APP_JSON)).append(",\"version\":1,\"scans\":[")
        val orden = lecturas.sortedWith(compareBy<Lectura> { it.instante }.thenBy { it.id })
        for ((i, l) in orden.withIndex()) {
            sb.append(if (i == 0) "\n" else ",\n")
            sb.append(Json.escribir(linkedMapOf(
                "date" to l.fecha, "time" to l.hora, "time_zone" to l.zona, "format" to l.formato, "text" to l.texto,
                "notes" to l.notas, "favorite" to l.favorito, "date_utc" to l.fechaUtc, "time_utc" to l.horaUtc,
                "metadata" to l.metadata, "source" to l.origen,
            )))
        }
        sb.append("\n]}\n")
        return sb.toString()
    }

    // ---------------- agrupar por día ----------------

    /** "Hoy", "Ayer" or "lunes, 10 de noviembre de 2026" for a local yyyy-MM-dd. */
    fun tituloDia(fecha: String, hoy: String, ayer: String): String = when (fecha) {
        hoy -> "Hoy"
        ayer -> "Ayer"
        else -> try {
            val d = fmt("yyyy-MM-dd", TimeZone.getTimeZone("UTC")).parse(fecha)!!
            SimpleDateFormat("EEEE, d 'de' MMMM 'de' yyyy", ES).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d)
                .replaceFirstChar { it.uppercase() }
        } catch (e: Exception) { fecha }
    }

    /** Day header of the history (S-J4): "HOY", "AYER", "SÁBADO, 14 DE NOVIEMBRE" (the year only if it is not this year's). */
    fun cabeceraDia(fecha: String, hoy: String, ayer: String): String = when (fecha) {
        hoy -> "HOY"
        ayer -> "AYER"
        else -> try {
            val d = fmt("yyyy-MM-dd", TimeZone.getTimeZone("UTC")).parse(fecha)!!
            val p = if (fecha.take(4) == hoy.take(4)) "EEEE, d 'de' MMMM" else "EEEE, d 'de' MMMM 'de' yyyy"
            SimpleDateFormat(p, ES).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(d).uppercase(ES)
        } catch (e: Exception) { fecha }
    }

    /** Words in [busca] that must all appear (ignoring case and accents) in the text, notes or format. */
    fun coincide(l: Lectura, busca: String): Boolean {
        val palabras = sinAcentos(busca).split(' ').filter { it.isNotBlank() }
        if (palabras.isEmpty()) return true
        val donde = sinAcentos(l.texto + "\n" + l.notas + "\n" + l.formato)
        return palabras.all { donde.contains(it) }
    }

    fun sinAcentos(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
}
