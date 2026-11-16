package es.kalvo.escanerqr.nucleo

/**
 * Small JSON reader and writer for importing/exporting the history (S-J5). Pure Kotlin, so it runs in the unit tests
 * (Android's org.json is not available there). Objects keep the order of their keys.
 *
 * Values: [Map] (LinkedHashMap), [List], [String], [Double]/[Long] for numbers, [Boolean] and null.
 */
object Json {

    class Error(msg: String) : Exception(msg)

    fun leer(texto: String): Any? {
        val p = Lector(texto)
        p.espacios()
        val v = p.valor()
        p.espacios()
        if (p.i < texto.length) throw Error("sobra texto en la posición ${p.i}")
        return v
    }

    private class Lector(val s: String) {
        var i = 0

        fun espacios() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun valor(): Any? {
            espacios()
            if (i >= s.length) throw Error("se acaba antes de tiempo")
            return when (val c = s[i]) {
                '{' -> objeto()
                '[' -> lista()
                '"' -> cadena()
                't' -> palabra("true", true)
                'f' -> palabra("false", false)
                'n' -> palabra("null", null)
                else -> if (c == '-' || c.isDigit()) numero() else throw Error("carácter inesperado «$c» en la posición $i")
            }
        }

        private fun palabra(p: String, v: Any?): Any? {
            if (!s.startsWith(p, i)) throw Error("palabra rara en la posición $i")
            i += p.length
            return v
        }

        private fun numero(): Any {
            val ini = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(ini, i)
            return t.toLongOrNull() ?: t.toDoubleOrNull() ?: throw Error("número raro «$t»")
        }

        private fun cadena(): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw Error("comillas sin cerrar")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) throw Error("comillas sin cerrar")
                        when (val e = s[i++]) {
                            'n' -> sb.append('\n'); 't' -> sb.append('\t'); 'r' -> sb.append('\r')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C')
                            'u' -> {
                                if (i + 4 > s.length) throw Error("\\u incompleto")
                                sb.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4
                            }
                            else -> sb.append(e) // \" \\ \/
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun lista(): List<Any?> {
            i++
            val out = ArrayList<Any?>()
            espacios()
            if (i < s.length && s[i] == ']') { i++; return out }
            while (true) {
                out.add(valor())
                espacios()
                if (i >= s.length) throw Error("lista sin cerrar")
                when (s[i++]) {
                    ',' -> continue
                    ']' -> return out
                    else -> throw Error("falta una coma en la posición ${i - 1}")
                }
            }
        }

        private fun objeto(): Map<String, Any?> {
            i++
            val out = LinkedHashMap<String, Any?>()
            espacios()
            if (i < s.length && s[i] == '}') { i++; return out }
            while (true) {
                espacios()
                if (i >= s.length || s[i] != '"') throw Error("falta el nombre de un campo en la posición $i")
                val k = cadena()
                espacios()
                if (i >= s.length || s[i] != ':') throw Error("faltan los dos puntos en la posición $i")
                i++
                out[k] = valor()
                espacios()
                if (i >= s.length) throw Error("objeto sin cerrar")
                when (s[i++]) {
                    ',' -> continue
                    '}' -> return out
                    else -> throw Error("falta una coma en la posición ${i - 1}")
                }
            }
        }
    }

    /** A value as plain text for a column: strings as they are, numbers without ".0", nested things as JSON. */
    fun comoTexto(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is Long -> v.toString()
        is Double -> if (v % 1.0 == 0.0 && kotlin.math.abs(v) < 1e15) v.toLong().toString() else v.toString()
        is Boolean -> v.toString()
        else -> escribir(v)
    }

    // ---------------- escribir ----------------

    fun cadena(s: String): String {
        val sb = StringBuilder("\"")
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        return sb.append('"').toString()
    }

    fun escribir(v: Any?): String = when (v) {
        null -> "null"
        is String -> cadena(v)
        is Boolean, is Long, is Int -> v.toString()
        is Double -> comoTexto(v)
        is Map<*, *> -> v.entries.joinToString(",", "{", "}") { cadena(it.key.toString()) + ":" + escribir(it.value) }
        is List<*> -> v.joinToString(",", "[", "]") { escribir(it) }
        else -> cadena(v.toString())
    }
}
