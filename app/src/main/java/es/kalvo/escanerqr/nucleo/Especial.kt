package es.kalvo.escanerqr.nucleo

/**
 * Labels that installers read a lot, as general rules (S-J4; made generic in S-J5): device labels with a MAC (a JSON
 * label with a MAC key, or a MAC on its own), SIM cards (ICCID) and stock labels with an article code + model + serial +
 * date. Pure Kotlin (tested on the laptop).
 *
 * Only for plain text ([Contenido.Texto]): wifis, links, contacts… are never taken for one of these.
 */
sealed class Especial {
    /** Short type name for the screen ("Equipo", "MAC", "SIM (ICCID)", "Artículo"). */
    abstract val tipo: String

    /** A device label: MACs (normalised `AA:BB:CC:DD:EE:FF`), model and serial when they come (JSON keys like `mac`/`model`/`sn`, see [Mac]). */
    data class Equipo(val macs: List<String>, val modelo: String, val serie: String) : Especial() {
        /** Only a MAC (no model, no serial): "MAC"; with model or serial: "Equipo". */
        val soloMac get() = modelo.isEmpty() && serie.isEmpty()
        override val tipo get() = if (soloMac) "MAC" else "Equipo"
    }

    /** A SIM card number (ICCID, ITU-T E.118): 19 or 20 digits starting with 89 (any country, any operator). */
    data class Sim(val iccid: String) : Especial() {
        override val tipo get() = "SIM (ICCID)"
    }

    /**
     * Stock label: `1234-000056 XY-Z99 5K01B2C 03/02/2024 5K01B2CQRS9X001` (made up) or just `5678-000012`.
     * [otros] = codes that are neither model nor serial (e.g. a short code that the serial starts with).
     */
    data class Articulo(val codigo: String, val modelo: String, val serie: String, val fecha: String, val otros: List<String>) : Especial() {
        override val tipo get() = "Artículo"
    }

    companion object {
        private val SIM = Regex("""89\d{17,18}""")
        /** Article code: 4–6 digits, a dash and 4–8 digits (a common shape for stock references). */
        private val CODIGO_ARTICULO = Regex("""\d{4,6}-\d{4,8}""")
        private val FECHA = Regex("""\d{1,2}[/.-]\d{1,2}[/.-](\d{4}|\d{2})|\d{4}-\d{2}-\d{2}""")
        private val PIEZA = Regex("""[A-Za-z0-9][A-Za-z0-9./_-]*""")

        /** What [texto] is, or null if it is none of these (then the screen shows it as before). */
        fun de(texto: String, c: Contenido): Especial? {
            if (c !is Contenido.Texto) return null
            val t = texto.trim()
            sim(t)?.let { return it }
            articulo(t)?.let { return it }
            val e = Mac.buscar(t)
            if (!e.vacia) return Equipo(e.macs, e.modelo, e.serie)
            return null
        }

        fun de(texto: String, formato: String): Especial? = de(texto, Contenido.analizar(texto, formato))

        /** 19 or 20 digits starting with 89, nothing else (an EAN-13, a 12-digit number or a phone number are not). */
        fun sim(t: String): Sim? = if (SIM.matches(t)) Sim(t) else null

        /**
         * Article code `NNNN-NNNNNN` (4–6 digits, dash, 4–8 digits) first, then up to 6 short codes separated by spaces: a date, the model
         * (the first one; or the only one if it has a dash) and the serial (the longest of the others).
         */
        fun articulo(t: String): Articulo? {
            val piezas = t.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            if (piezas.isEmpty() || !CODIGO_ARTICULO.matches(piezas[0])) return null
            val resto = piezas.drop(1)
            if (resto.size > 6 || resto.any { !PIEZA.matches(it) }) return null
            val iFecha = resto.indexOfFirst { FECHA.matches(it) }
            val fecha = if (iFecha >= 0) resto[iFecha] else ""
            val codigos = resto.filterIndexed { i, _ -> i != iFecha }
            var modelo = ""
            var serie = ""
            val otros = ArrayList<String>()
            when {
                codigos.isEmpty() -> {}
                codigos.size == 1 -> if (codigos[0].contains('-')) modelo = codigos[0] else serie = codigos[0]
                else -> {
                    modelo = codigos[0]
                    val demas = codigos.drop(1)
                    // The longest; on a tie, the last one (labels print the full serial at the end).
                    serie = demas.withIndex().maxWith(compareBy({ it.value.length }, { it.index })).value
                    var quitado = false
                    for (x in demas) if (!quitado && x == serie) quitado = true else otros += x
                }
            }
            return Articulo(piezas[0], modelo, serie, fecha, otros)
        }

        /**
         * What «Copiar al portapapeles» copies when a code is read:
         * - device label / MAC: the first MAC with ':' and in capitals (when [soloMac] is on; otherwise the whole text);
         * - SIM: the whole number (no spaces);
         * - article: the serial if there is one, otherwise the whole code;
         * - anything else: the text as read.
         */
        fun queCopiar(texto: String, c: Contenido, soloMac: Boolean): String = when (val e = de(texto, c)) {
            is Equipo -> if (soloMac) e.macs.first() else texto
            is Sim -> e.iccid
            is Articulo -> e.serie.ifEmpty { texto.trim() }
            null -> texto
        }

        /** "8901 2345 6789 0123 456": in groups of 4, only to read it (it is copied without spaces). */
        fun agrupar4(s: String): String = s.chunked(4).joinToString(" ")

        /** "AA:BB:CC:DD:EE:FF" → "AABBCCDDEEFF". */
        fun sinDosPuntos(mac: String): String = mac.filter { it != ':' && it != '-' && it != '.' }.uppercase()
    }
}
