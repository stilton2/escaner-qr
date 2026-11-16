package es.kalvo.escanerqr.nucleo

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Calendar
import java.util.TimeZone

/**
 * «Importar historial» (S-J5): reads the history exported by other scanner apps. Pure Kotlin (tested on the laptop).
 *
 * Known formats, recognised by themselves:
 * - **QR Bot** CSV (`date,time,time_zone,format,text,notes,favorite,date_utc,time_utc,metadata`), read by [Historial.leerCsv]
 *   exactly as before.
 * - **Binary Eye** CSV and JSON (columns `pinned,_datetime,format,name,content,error_correction_level,…`; CSV separator
 *   chosen by the user there; dates `yyyy-MM-dd HH:mm:ss[:SSS]` in local time). Source: its code, `database/CsvExport.kt`,
 *   `JsonExport.kt`, `Scan.kt` (github.com/markusfisch/BinaryEye, MIT).
 * - **QR & Barcode Scanner** (open source, github.com/wewewe718/QrAndBarcodeScanner): CSV `Date,Format,Text` without
 *   quotes (the text may carry commas and line breaks) and JSON `[{"date","format","text"}]` (`usecase/BarcodeSaver.kt`).
 * - **Barcode Scanner** of the ZXing team: CSV without a header: text, display text, format, timestamp in ms, date as
 *   text, details (`android/…/history/HistoryManager.java`, github.com/zxing/zxing, Apache 2.0).
 * - This app's own JSON export ([Historial.exportarJson]), which keeps where each read came from.
 *
 * Anything else: columns found by their name in Spanish or English ([mapa]); separator `,` `;` or tab and encoding
 * (UTF-8 with or without BOM, UTF-16 with BOM, else Latin-1) detected by themselves; a TXT = one read per line. If the
 * columns cannot be recognised, [Resultado.Elegir] asks which one is the text and which one the date.
 */
object Importar {

    /** What a file gives. [origen] = what goes after «importado de». [sinFecha] = no dates: all at the import time. */
    data class Listo(
        val lecturas: List<Lectura>,
        val fallos: List<Historial.Fallo>,
        val origen: String,
        val tipo: String,
        val sinFecha: Boolean = false,
        val codificacion: String = "UTF-8",
    )

    sealed class Resultado {
        data class Hecho(val listo: Listo) : Resultado()

        /**
         * The columns were not recognised. [filas] = all the rows as read (the first may be a header: [primeraEsCabecera]
         * is a guess); [columnas] = how many columns the widest row has.
         */
        data class Elegir(
            val filas: List<Csv.Fila>,
            val columnas: Int,
            val primeraEsCabecera: Boolean,
            val nombre: String,
            val tipo: String,
            val codificacion: String,
        ) : Resultado() {
            /** The first 5 rows, for the screen. */
            val muestra: List<List<String>> get() = filas.take(5).map { it.campos }
        }

        data class NoVale(val motivo: String) : Resultado()
    }

    // ---------------- codificación y separador ----------------

    /** Text of the file and the name of its encoding: UTF-8 (BOM or not), UTF-16 with BOM, else Latin-1 (Windows-1252). */
    fun decodificar(b: ByteArray): Pair<String, String> {
        if (b.size >= 3 && b[0] == 0xEF.toByte() && b[1] == 0xBB.toByte() && b[2] == 0xBF.toByte())
            return String(b, 3, b.size - 3, Charsets.UTF_8) to "UTF-8"
        if (b.size >= 2 && b[0] == 0xFF.toByte() && b[1] == 0xFE.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16LE) to "UTF-16"
        if (b.size >= 2 && b[0] == 0xFE.toByte() && b[1] == 0xFF.toByte()) return String(b, 2, b.size - 2, Charsets.UTF_16BE) to "UTF-16"
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b)).toString() to "UTF-8"
        } catch (e: CharacterCodingException) {
            val cs = try { Charset.forName("windows-1252") } catch (_: Exception) { Charsets.ISO_8859_1 }
            String(b, cs) to "Latin-1"
        }
    }

    /** `,` `;` or tab: the one that appears most (outside quotes) in the first line with something. Null = only one column. */
    fun separador(texto: String): Char? {
        val linea = primeraLinea(texto)
        val cuenta = HashMap<Char, Int>()
        var dentro = false
        for (c in linea) {
            if (c == '"') dentro = !dentro
            else if (!dentro && (c == ',' || c == ';' || c == '\t')) cuenta[c] = (cuenta[c] ?: 0) + 1
        }
        // On a tie: tab, then «;», then «,» (a text with commas is more likely than one with tabs).
        return listOf('\t', ';', ',').filter { (cuenta[it] ?: 0) > 0 }.maxByOrNull { cuenta[it] ?: 0 }
    }

    private fun primeraLinea(texto: String) = texto.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()

    // ---------------- nombres de columnas ----------------

    /** Which column (by its name in the file) is each thing. Only [texto] is required. */
    data class Mapa(
        val texto: String,
        val fechaHora: String? = null,
        val fecha: String? = null,
        val hora: String? = null,
        val formato: String? = null,
        val notas: String? = null,
        val favorito: String? = null,
        val ec: String? = null,
    ) {
        val conFecha get() = fechaHora != null || fecha != null || hora != null
    }

    /** "Código leído" → "codigoleido": lower case, no accents, only letters and digits. */
    fun normal(s: String) = Historial.sinAcentos(s.trim()).filter { it.isLetterOrDigit() }

    // In order of preference: if a file has «text» and «raw», «text» wins.
    private val TEXTO = listOf("text", "texto", "content", "contenido", "contents", "value", "valor", "rawvalue", "rawtext",
        "displayvalue", "decodedtext", "decoded", "barcodevalue", "codevalue", "qrtext", "qrcontent", "data", "datos", "dato",
        "result", "resultado", "codigoleido", "textoleido", "valorleido", "contenidoleido", "code", "codigo", "barcode", "codigodebarras", "qr", "qrcode", "scan", "lectura", "raw")
    private val FECHA_HORA = listOf("timestamp", "datetime", "fechahora", "fechayhora", "dateandtime", "datetimescanned",
        "scannedat", "scannedon", "createdat", "created", "creado", "creadoel", "scantimestamp", "epoch", "ts", "momento", "when")
    private val FECHA = listOf("date", "fecha", "scandate", "datescanned", "fechadelectura", "fechadeescaneo", "day", "dia")
    private val HORA = listOf("time", "hora", "scantime", "timescanned", "horadelectura", "horadeescaneo")
    private val FORMATO = listOf("format", "formato", "barcodeformat", "barcodetype", "codetype", "symbology", "simbologia",
        "formatname", "tipodecodigo", "formatodecodigo", "tipodecodigodebarras", "type", "tipo", "kind")
    private val NOTAS = listOf("notes", "note", "notas", "nota", "comment", "comments", "comentario", "comentarios",
        "name", "nombre", "description", "descripcion", "label", "etiqueta", "title", "titulo")
    private val FAVORITO = listOf("favorite", "favourite", "favorito", "favoritos", "fav", "starred", "star", "pinned", "destacado")

    /** The column names of a file → [Mapa], or null when there is no column that can be the text. */
    fun mapa(cabecera: List<String>): Mapa? {
        val porNormal = LinkedHashMap<String, String>()
        for (c in cabecera) if (c.isNotBlank()) porNormal.putIfAbsent(normal(c), c)
        fun buscar(l: List<String>): String? = l.firstNotNullOfOrNull { porNormal[it] }
        val texto = buscar(TEXTO) ?: return null
        val usados = mutableSetOf(texto)
        fun otro(l: List<String>): String? = buscar(l)?.takeIf { usados.add(it) }
        return Mapa(texto, fechaHora = otro(FECHA_HORA), fecha = otro(FECHA), hora = otro(HORA), formato = otro(FORMATO),
            notas = otro(NOTAS), favorito = otro(FAVORITO))
    }

    // ---------------- entrada ----------------

    fun analizar(bytes: ByteArray, nombreFichero: String, tz: TimeZone = TimeZone.getDefault(), ahora: Long = System.currentTimeMillis()): Resultado {
        val (texto, cod) = decodificar(bytes)
        return when (val r = analizarTexto(texto, nombreFichero, tz, ahora)) {
            is Resultado.Hecho -> Resultado.Hecho(r.listo.copy(codificacion = cod))
            is Resultado.Elegir -> r.copy(codificacion = cod)
            else -> r
        }
    }

    fun analizarTexto(texto0: String, nombreFichero: String, tz: TimeZone = TimeZone.getDefault(), ahora: Long = System.currentTimeMillis()): Resultado {
        val texto = texto0.removePrefix("﻿")
        val nombre = nombreFichero.substringAfterLast('/').ifBlank { "un fichero" }
        if (texto.isBlank()) return Resultado.NoVale("El fichero está vacío.")
        val esTxt = nombre.endsWith(".txt", ignoreCase = true)
        val inicio = texto.trimStart()

        if (inicio.startsWith("[") || inicio.startsWith("{")) {
            val raiz = try { Json.leer(texto) } catch (e: Json.Error) { null }
            json(raiz, nombre, tz, ahora)?.let { return it }
            // Not a list of reads (e.g. a TXT with one JSON label per line): read it as lines below.
            if (raiz == null && !esTxt && !inicio.startsWith("{")) return Resultado.NoVale("No es un JSON válido.")
            return Resultado.Hecho(lineas(texto, nombre, ahora, tz))
        }

        val sep = separador(texto)
        if (sep != null) {
            val primera = primeraLinea(texto).trim()
            // QR Bot: exactly as before (its own reader).
            val filas = Csv.leer(texto, sep)
            val cab = filas.firstOrNull()?.campos.orEmpty().map { it.trim().lowercase() }
            if (sep == ',' && "text" in cab && "format" in cab && ("date_utc" in cab || "time_zone" in cab)) {
                val r = Historial.leerCsv(texto, tz)
                return Resultado.Hecho(Listo(r.lecturas.map { it.copy(origen = QR_BOT) }, r.fallos, QR_BOT, "QR Bot (CSV)"))
            }
            if ("_datetime" in cab && "content" in cab) {
                return Resultado.Hecho(convertir(registros(filas), BINARY_EYE_MAPA, BINARY_EYE, "Binary Eye (CSV)", tz, ahora))
            }
            if (sep == ',' && primera == "Date,Format,Text") return Resultado.Hecho(qrBarcodeScannerCsv(texto, tz))
            val f0 = filas.firstOrNull()?.campos.orEmpty()
            if (f0.size >= 5 && Regex("""\d{12,14}""").matches(f0[3].trim()) && Regex("""[A-Z0-9_]+""").matches(f0[2].trim())) {
                val regs = filas.map { f -> Registro(f.linea, f.campos.withIndex().associate { (i, v) -> "#$i" to v }) }
                return Resultado.Hecho(convertir(regs, Mapa("#0", fechaHora = "#3", formato = "#2"), ZXING, "Barcode Scanner de ZXing (CSV)", tz, ahora))
            }
            mapa(f0)?.let { m ->
                val tipo = if (esTxt) "Texto con columnas" else "CSV (columnas por su nombre)"
                return Resultado.Hecho(convertir(registros(filas), m, nombre, tipo, tz, ahora))
            }
            if (!esTxt) {
                return Resultado.Elegir(filas, filas.maxOf { it.campos.size }, pareceCabecera(f0, tz), nombre,
                    "CSV (columnas elegidas a mano)", "UTF-8")
            }
        }
        return Resultado.Hecho(lineas(texto, nombre, ahora, tz))
    }

    /** With the columns the user chose in [e]: [colTexto] (0-based), [colFecha] or null (then all at the import time). */
    fun conColumnas(e: Resultado.Elegir, colTexto: Int, colFecha: Int?, saltarPrimera: Boolean,
                    tz: TimeZone = TimeZone.getDefault(), ahora: Long = System.currentTimeMillis()): Listo {
        val filas = if (saltarPrimera) e.filas.drop(1) else e.filas
        val regs = filas.map { f -> Registro(f.linea, f.campos.withIndex().associate { (i, v) -> "#$i" to v }) }
        return convertir(regs, Mapa("#$colTexto", fechaHora = colFecha?.let { "#$it" }), e.nombre, e.tipo, tz, ahora)
            .copy(codificacion = e.codificacion)
    }

    const val QR_BOT = "QR Bot"
    const val BINARY_EYE = "Binary Eye"
    const val QR_BARCODE_SCANNER = "QR & Barcode Scanner"
    const val ZXING = "Barcode Scanner (ZXing)"

    private val BINARY_EYE_MAPA = Mapa("content", fechaHora = "_datetime", formato = "format", notas = "name", favorito = "pinned",
        ec = "error_correction_level")

    /** First row of a file without recognised columns: a header if nothing in it is a date or a number. */
    private fun pareceCabecera(f0: List<String>, tz: TimeZone) =
        f0.isNotEmpty() && f0.none { Fechas.leer(it, tz) != null || it.trim().toDoubleOrNull() != null }

    // ---------------- registros ----------------

    /** One read of the file: its line and its values by column name. */
    data class Registro(val linea: Int, val valores: Map<String, String>)

    /** CSV rows (first = header) → records by column name (the first column with a name wins if repeated). */
    private fun registros(filas: List<Csv.Fila>): List<Registro> {
        if (filas.isEmpty()) return emptyList()
        val cab = filas[0].campos.map { it.trim() }
        return filas.drop(1).map { f ->
            val m = LinkedHashMap<String, String>()
            for ((i, v) in f.campos.withIndex()) if (i < cab.size) m.putIfAbsent(cab[i], v)
            Registro(f.linea, m)
        }
    }

    private fun sinComillasRaras(v: String) = v.trim()

    private fun esSi(v: String) = normal(v) in setOf("1", "true", "yes", "si", "y", "s", "x", "verdadero")

    private fun instante(r: Registro, m: Mapa, tz: TimeZone): Long? {
        m.fechaHora?.let { return Fechas.leer(r.valores[it].orEmpty(), tz) }
        val f = m.fecha?.let { r.valores[it] }?.trim().orEmpty()
        val h = m.hora?.let { r.valores[it] }?.trim().orEmpty()
        if (f.isNotEmpty() && h.isNotEmpty()) Fechas.leer("$f $h", tz)?.let { return it }
        if (f.isNotEmpty()) return Fechas.leer(f, tz)
        if (h.isNotEmpty()) return Fechas.leer(h, tz)
        return null
    }

    /** Records → reads. Rows without text or with a date that cannot be understood go to the failures. */
    fun convertir(regs: List<Registro>, m: Mapa, origen: String, tipo: String, tz: TimeZone, ahora: Long): Listo {
        val out = ArrayList<Lectura>()
        val fallos = ArrayList<Historial.Fallo>()
        for (r in regs) {
            if (r.valores.values.all { it.isBlank() }) continue
            val muestra = r.valores.values.joinToString(",").replace('\n', ' ').take(60)
            val txt = r.valores[m.texto].orEmpty()
            if (txt.isBlank()) { fallos.add(Historial.Fallo(r.linea, "no tiene texto", muestra)); continue }
            val ms = if (!m.conFecha) ahora else instante(r, m, tz)
            if (ms == null) { fallos.add(Historial.Fallo(r.linea, "fecha u hora que no se entiende", muestra)); continue }
            val formato = Formatos.normalizar(m.formato?.let { r.valores[it] }.orEmpty())
            val meta = m.ec?.let { r.valores[it] }?.let { Formatos.metadata(it) }.orEmpty()
            out.add(Historial.nueva(ms, tz, formato, txt, meta).copy(
                notas = m.notas?.let { r.valores[it] }?.let(::sinComillasRaras).orEmpty(),
                favorito = m.favorito?.let { r.valores[it] }?.let(::esSi) ?: false,
                origen = origen,
            ))
        }
        return Listo(out, fallos, origen, tipo, sinFecha = !m.conFecha)
    }

    /** A TXT: one read per line (blank lines skipped), all at the import time. */
    fun lineas(texto: String, nombre: String, ahora: Long, tz: TimeZone): Listo {
        val out = ArrayList<Lectura>()
        for (l in texto.lineSequence()) {
            val t = l.trim()
            if (t.isEmpty()) continue
            out.add(Historial.nueva(ahora, tz, Formatos.DESCONOCIDO, t).copy(origen = nombre))
        }
        return Listo(out, emptyList(), nombre, "Texto, una lectura por línea", sinFecha = true)
    }

    /** «QR & Barcode Scanner» CSV: `Date,Format,Text` with no quotes; a line that does not start with a date continues the text. */
    private fun qrBarcodeScannerCsv(texto: String, tz: TimeZone): Listo {
        val re = Regex("""^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}|null),([A-Za-z0-9_]+),(.*)$""")
        data class Trozo(val linea: Int, val fecha: String, val formato: String, val texto: StringBuilder)
        val trozos = ArrayList<Trozo>()
        val fallos = ArrayList<Historial.Fallo>()
        val lineas = texto.trimEnd().split('\n').map { it.removeSuffix("\r") }
        for ((i, l) in lineas.withIndex()) {
            if (i == 0) continue
            val m = re.matchEntire(l)
            when {
                m != null -> trozos.add(Trozo(i + 1, m.groupValues[1], m.groupValues[2], StringBuilder(m.groupValues[3])))
                trozos.isNotEmpty() -> trozos.last().texto.append('\n').append(l)
                l.isNotBlank() -> fallos.add(Historial.Fallo(i + 1, "no empieza por fecha y formato", l.take(60)))
            }
        }
        val out = ArrayList<Lectura>()
        for (t in trozos) {
            val ms = Fechas.leer(t.fecha, tz)
            val muestra = "${t.fecha},${t.formato},${t.texto}".replace('\n', ' ').take(60)
            if (t.texto.isBlank()) { fallos.add(Historial.Fallo(t.linea, "no tiene texto", muestra)); continue }
            if (ms == null) { fallos.add(Historial.Fallo(t.linea, "fecha u hora que no se entiende", muestra)); continue }
            out.add(Historial.nueva(ms, tz, Formatos.normalizar(t.formato), t.texto.toString()).copy(origen = QR_BARCODE_SCANNER))
        }
        return Listo(out, fallos.sortedBy { it.linea }, QR_BARCODE_SCANNER, "QR & Barcode Scanner (CSV)")
    }

    // ---------------- JSON ----------------

    /** Null when [raiz] is not a list of reads (the caller then reads the file as lines). */
    private fun json(raiz: Any?, nombre: String, tz: TimeZone, ahora: Long): Resultado? {
        // Our own export: keeps the origin of each read and its exact local date, time and zone.
        if (raiz is Map<*, *> && raiz["app"] == Historial.APP_JSON && raiz["scans"] is List<*>) {
            return Resultado.Hecho(propio(raiz["scans"] as List<*>))
        }
        val lista: List<*> = when (raiz) {
            is List<*> -> raiz
            is Map<*, *> -> raiz.values.firstOrNull { v -> v is List<*> && v.any { it is Map<*, *> } } as List<*>? ?: return null
            else -> return null
        }
        val objs = lista.filterIsInstance<Map<*, *>>()
        if (objs.isEmpty()) return null
        val regs = objs.mapIndexed { i, o -> Registro(i + 1, o.entries.associate { (k, v) -> k.toString() to Json.comoTexto(v) }) }
        val claves = LinkedHashSet<String>().apply { objs.forEach { o -> o.keys.forEach { add(it.toString()) } } }.toList()
        if ("_datetime" in claves && "content" in claves) return Resultado.Hecho(convertir(regs, BINARY_EYE_MAPA, BINARY_EYE, "Binary Eye (JSON)", tz, ahora))
        if (claves == listOf("date", "format", "text")) {
            return Resultado.Hecho(convertir(regs, Mapa("text", fechaHora = "date", formato = "format"), QR_BARCODE_SCANNER, "QR & Barcode Scanner (JSON)", tz, ahora))
        }
        mapa(claves)?.let { return Resultado.Hecho(convertir(regs, it, nombre, "JSON (campos por su nombre)", tz, ahora)) }
        // Ask: header = the keys, then the values in that order.
        val filas = ArrayList<Csv.Fila>()
        filas.add(Csv.Fila(0, claves))
        regs.forEach { r -> filas.add(Csv.Fila(r.linea, claves.map { r.valores[it].orEmpty() })) }
        return Resultado.Elegir(filas, claves.size, true, nombre, "JSON (campos elegidos a mano)", "UTF-8")
    }

    private fun propio(scans: List<*>): Listo {
        val out = ArrayList<Lectura>()
        val fallos = ArrayList<Historial.Fallo>()
        for ((i, o) in scans.withIndex()) {
            val m = o as? Map<*, *> ?: continue
            fun v(k: String) = Json.comoTexto(m[k])
            val muestra = v("text").replace('\n', ' ').take(60)
            val l = Lectura(fecha = v("date"), hora = v("time"), zona = v("time_zone"), formato = v("format").ifEmpty { Formatos.DESCONOCIDO },
                texto = v("text"), notas = v("notes"), favorito = m["favorite"] == true || esSi(v("favorite")),
                fechaUtc = v("date_utc"), horaUtc = v("time_utc"), metadata = v("metadata"), origen = v("source"))
            when {
                l.texto.isEmpty() -> fallos.add(Historial.Fallo(i + 1, "no tiene texto", muestra))
                Historial.instanteUtc(l.fechaUtc, l.horaUtc) == null -> fallos.add(Historial.Fallo(i + 1, "fecha u hora que no se entiende", muestra))
                else -> out.add(l)
            }
        }
        return Listo(out, fallos, "", "Copia de Escáner QR (JSON)")
    }
}

/** Dates and times as other apps write them (S-J5). Pure Kotlin. */
object Fechas {
    private val EPOCH = Regex("""(\d{9,14})(?:\.(\d+))?""")
    private val ISO = Regex("""(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:(?:T|\s+|,\s*)(\d{1,2}):(\d{2})(?::(\d{2}))?(?:[.:,](\d{1,9}))?)?\s*(Z|[+-]\d{2}(?::?\d{2})?)?""", RegexOption.IGNORE_CASE)
    private val DMY = Regex("""(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?:(?:T|\s+|,\s*)(\d{1,2}):(\d{2})(?::(\d{2}))?(?:[.:,](\d{1,3}))?\s*([ap])?\.?\s?(?:m\.?)?)?""", RegexOption.IGNORE_CASE)

    /**
     * Epoch in seconds (9–11 digits, may have decimals) or milliseconds (12–14); ISO `2026-11-16T10:02:30(.123)(Z|+01:00)`
     * or with a space (`2026-11-16 10:02:30:123` as Binary Eye writes it); `16/11/2026 10:02(:30)` (day first unless the
     * second number cannot be a month), with `-` or `.` too, 2-digit years and AM/PM. Without a zone: [tz]. Null if not a date.
     */
    fun leer(v0: String, tz: TimeZone): Long? {
        val v = v0.trim().removeSurrounding("\"")
        if (v.isEmpty()) return null
        EPOCH.matchEntire(v)?.let { m ->
            val n = m.groupValues[1]
            val ms = if (n.length <= 11) n.toLong() * 1000 + (m.groupValues[2].padEnd(3, '0').take(3).toLongOrNull() ?: 0) else n.toLong()
            return ms.takeIf { razonable(it) }
        }
        ISO.matchEntire(v)?.let { m ->
            val g = m.groupValues
            return construir(g[1].toInt(), g[2].toInt(), g[3].toInt(), g[4], g[5], g[6], g[7], g[8], null, tz)
        }
        DMY.matchEntire(v)?.let { m ->
            val g = m.groupValues
            val a = g[1].toInt(); val b = g[2].toInt()
            var y = g[3].toInt(); if (g[3].length == 2) y += 2000
            val (d, mes) = if (a <= 12 && b > 12) b to a else a to b
            return construir(y, mes, d, g[4], g[5], g[6], g[7], "", g[8].ifEmpty { null }, tz)
        }
        return null
    }

    private fun razonable(ms: Long) = ms in 0L..4_102_444_800_000L  // until 2100

    private fun construir(y: Int, mes: Int, d: Int, h: String, mi: String, s: String, frac: String, zona: String, ampm: String?, tz: TimeZone): Long? {
        if (mes !in 1..12 || d !in 1..31 || y < 1970) return null
        var hora = h.toIntOrNull() ?: 0
        if (ampm != null) {
            if (hora !in 1..12) return null
            val pm = ampm.equals("p", ignoreCase = true)
            hora = when { pm && hora < 12 -> hora + 12; !pm && hora == 12 -> 0; else -> hora }
        }
        val min = mi.toIntOrNull() ?: 0
        val seg = s.toIntOrNull() ?: 0
        if (hora !in 0..23 || min !in 0..59 || seg !in 0..59) return null
        val z = when {
            zona.isEmpty() -> tz
            zona.equals("Z", ignoreCase = true) -> TimeZone.getTimeZone("UTC")
            else -> {
                val dig = zona.substring(1).replace(":", "")
                TimeZone.getTimeZone("GMT" + zona[0] + dig.take(2) + ":" + dig.drop(2).ifEmpty { "00" })
            }
        }
        return try {
            Calendar.getInstance(z).apply {
                isLenient = false; clear()
                set(y, mes - 1, d, hora, min, seg)
                set(Calendar.MILLISECOND, frac.padEnd(3, '0').take(3).toIntOrNull() ?: 0)
            }.timeInMillis.takeIf { razonable(it) }
        } catch (e: IllegalArgumentException) { null }
    }
}
