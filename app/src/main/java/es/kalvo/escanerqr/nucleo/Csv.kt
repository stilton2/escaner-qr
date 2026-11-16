package es.kalvo.escanerqr.nucleo

/**
 * CSV as QR Bot writes it: every field in double quotes, `""` for a quote inside, line breaks allowed inside quotes.
 * The reader also accepts unquoted fields, CRLF or LF line ends and a UTF-8 BOM at the start.
 */
object Csv {

    /** One parsed row plus the line number (1-based) where it starts in the file, for error messages. */
    data class Fila(val linea: Int, val campos: List<String>)

    /** [sep] = field separator (`,` by default; `;` and tab for other apps' files, S-J5). */
    fun leer(texto: String, sep: Char = ','): List<Fila> {
        val s = texto.removePrefix("﻿")
        val filas = ArrayList<Fila>()
        val campo = StringBuilder()
        var campos = ArrayList<String>()
        var i = 0
        var linea = 1
        var inicioFila = 1
        var entreComillas = false
        var filaEmpezada = false
        while (i < s.length) {
            val c = s[i]
            if (entreComillas) {
                if (c == '"') {
                    if (i + 1 < s.length && s[i + 1] == '"') { campo.append('"'); i += 2; continue }
                    entreComillas = false
                } else {
                    if (c == '\n') linea++
                    campo.append(c)
                }
                i++
                continue
            }
            when (c) {
                '"' -> { entreComillas = true; filaEmpezada = true }
                sep -> { campos.add(campo.toString()); campo.setLength(0); filaEmpezada = true }
                '\r' -> { /* the \n that follows ends the row */ }
                '\n' -> {
                    if (filaEmpezada || campo.isNotEmpty()) {
                        campos.add(campo.toString()); filas.add(Fila(inicioFila, campos))
                    }
                    campos = ArrayList(); campo.setLength(0); filaEmpezada = false
                    linea++; inicioFila = linea
                }
                else -> { campo.append(c); filaEmpezada = true }
            }
            i++
        }
        if (filaEmpezada || campo.isNotEmpty()) { campos.add(campo.toString()); filas.add(Fila(inicioFila, campos)) }
        return filas
    }

    /** True when the file ended inside an open quote (a cut file): the last row would be wrong. */
    fun comillaSinCerrar(texto: String): Boolean {
        var dentro = false
        for (c in texto) if (c == '"') dentro = !dentro
        return dentro
    }

    fun campo(v: String) = "\"" + v.replace("\"", "\"\"") + "\""

    fun fila(campos: List<String>) = campos.joinToString(",") { campo(it) }
}
