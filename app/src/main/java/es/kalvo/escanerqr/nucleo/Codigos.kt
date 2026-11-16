package es.kalvo.escanerqr.nucleo

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.datamatrix.encoder.SymbolShapeHint
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * «Crear» (S-J9): which formats can be made, the checks of what is typed for each one, and drawing the code
 * (ZXing core 3.5.3, Apache 2.0, pure Java: also runs in the unit tests). [nombre] is the format name the history uses
 * ([Formatos]), so a code made here shows like a scanned one.
 */
enum class Formato(val nombre: String, val zxing: BarcodeFormat, val ayuda: String, val soloCifras: Boolean, val matriz: Boolean) {
    QR("QR Code", BarcodeFormat.QR_CODE, "El de siempre: vale cualquier texto.", false, true),
    EAN13("EAN-13", BarcodeFormat.EAN_13, "13 cifras (productos de tienda). Si pones 12, la app calcula la última.", true, false),
    EAN8("EAN-8", BarcodeFormat.EAN_8, "8 cifras (productos pequeños). Si pones 7, la app calcula la última.", true, false),
    UPCA("UPC-A", BarcodeFormat.UPC_A, "12 cifras (productos de EE. UU.). Si pones 11, la app calcula la última.", true, false),
    CODE128("Code 128", BarcodeFormat.CODE_128, "Letras sin tildes, cifras y signos (hasta 80). El más usado en etiquetas.", false, false),
    CODE39("Code 39", BarcodeFormat.CODE_39, "Mayúsculas, cifras, espacio y - . $ / + % (hasta 80).", false, false),
    ITF("ITF", BarcodeFormat.ITF, "Solo cifras, un número par de ellas (cajas de envío, ITF-14).", true, false),
    DATA_MATRIX("Data Matrix", BarcodeFormat.DATA_MATRIX, "Cuadrado pequeño (piezas, medicinas). Vale cualquier texto.", false, true),
    AZTEC("Aztec", BarcodeFormat.AZTEC, "Cuadrado con una diana en el centro (billetes). Vale cualquier texto.", false, true),
    PDF417("PDF 417", BarcodeFormat.PDF_417, "Rectángulo de filas (carnés, tarjetas de embarque). Vale cualquier texto.", false, true);

    companion object {
        /** The ones offered under «Código de barras» (QR has its own types). */
        val barras = entries.filter { it != QR }
    }
}

/** Result of checking what was typed for a format: the text to encode (maybe completed) or why it is not valid. */
sealed class Revision {
    data class Bien(val texto: String, val nota: String? = null) : Revision()
    data class Mal(val motivo: String) : Revision()
}

object Codigos {
    private const val CODE39_VALEN = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ -.$/+%"

    /** GTIN check digit (EAN-13, EAN-8, UPC-A): weights 3,1,3… from the right, then up to the next ten. */
    fun digitoControl(cifras: String): Int {
        var suma = 0
        for ((i, c) in cifras.reversed().withIndex()) suma += (c - '0') * (if (i % 2 == 0) 3 else 1)
        return (10 - suma % 10) % 10
    }

    /** Checks [texto] for [f] in plain words. Numbers: spaces and dashes are removed first. */
    fun revisar(f: Formato, texto: String): Revision {
        val t = if (f.soloCifras) texto.filterNot { it.isWhitespace() || it == '-' } else texto
        if (t.isEmpty() || (!f.matriz && t.isBlank())) return Revision.Mal("Escribe lo que va dentro del código")
        return when (f) {
            Formato.EAN13 -> gtin(t, 13, "EAN-13")
            Formato.EAN8 -> gtin(t, 8, "EAN-8")
            Formato.UPCA -> gtin(t, 12, "UPC-A")
            Formato.ITF -> when {
                !t.all { it in '0'..'9' } -> Revision.Mal("ITF solo admite cifras (0 a 9)")
                t.length % 2 != 0 -> Revision.Mal("ITF necesita un número par de cifras: añade un 0 al principio (0${t})")
                t.length < 6 -> Revision.Mal("ITF necesita al menos 6 cifras")
                t.length > 80 -> Revision.Mal("Demasiado largo: como mucho 80 cifras")
                else -> Revision.Bien(t)
            }
            Formato.CODE128 -> {
                val mala = t.firstOrNull { it.code < 32 || it.code > 126 }
                when {
                    mala != null -> Revision.Mal("Code 128 no admite «${visible(mala)}»: solo letras sin tildes ni ñ, cifras y signos normales")
                    t.length > 80 -> Revision.Mal("Demasiado largo: como mucho 80 caracteres")
                    else -> Revision.Bien(t)
                }
            }
            Formato.CODE39 -> {
                val may = t.uppercase(java.util.Locale.ROOT)
                val mala = may.firstOrNull { it !in CODE39_VALEN }
                when {
                    mala != null -> Revision.Mal("Code 39 no admite «${visible(mala)}»: solo mayúsculas sin tildes, cifras, espacio y - . $ / + %")
                    may.length > 80 -> Revision.Mal("Demasiado largo: como mucho 80 caracteres")
                    may != t -> Revision.Bien(may, "Code 39 solo lleva mayúsculas: lo he puesto en mayúsculas")
                    else -> Revision.Bien(t)
                }
            }
            Formato.QR, Formato.DATA_MATRIX, Formato.AZTEC, Formato.PDF417 -> Revision.Bien(t)
        }
    }

    private fun visible(c: Char) = when (c) { '\n' -> "salto de línea"; '\t' -> "tabulador"; else -> c.toString() }

    private fun gtin(t: String, largo: Int, nombre: String): Revision {
        if (!t.all { it in '0'..'9' }) return Revision.Mal("$nombre solo admite cifras (0 a 9)")
        return when (t.length) {
            largo - 1 -> {
                val d = digitoControl(t)
                Revision.Bien(t + d, "He añadido la última cifra (dígito de control): $d")
            }
            largo -> {
                val d = digitoControl(t.dropLast(1))
                if (t.last() - '0' == d) Revision.Bien(t)
                else Revision.Mal("La última cifra (dígito de control) no cuadra: con las otras tendría que ser $d. " +
                    "Revisa el número, o escribe solo las ${largo - 1} primeras y la app pone la última.")
            }
            else -> Revision.Mal("$nombre lleva $largo cifras (o ${largo - 1} y la app pone la última); has puesto ${t.length}")
        }
    }

    /** Encoding options: no margin (added when drawing, see [pixeles]); UTF-8 (with its ECI mark) only if the text needs it. */
    fun pistas(f: Formato, texto: String): Map<EncodeHintType, Any> {
        val m = HashMap<EncodeHintType, Any>()
        m[EncodeHintType.MARGIN] = 0
        val ascii = texto.all { it.code < 128 }
        when (f) {
            Formato.QR -> {
                m[EncodeHintType.ERROR_CORRECTION] = ErrorCorrectionLevel.M
                if (!ascii) m[EncodeHintType.CHARACTER_SET] = "UTF-8"
            }
            Formato.DATA_MATRIX -> {
                m[EncodeHintType.DATA_MATRIX_SHAPE] = SymbolShapeHint.FORCE_SQUARE
                // The plain encoder only takes Latin-1; the compact one can mark UTF-8.
                if (!ascii) { m[EncodeHintType.CHARACTER_SET] = "UTF-8"; m[EncodeHintType.DATA_MATRIX_COMPACT] = true }
            }
            Formato.AZTEC, Formato.PDF417 -> if (!ascii) m[EncodeHintType.CHARACTER_SET] = "UTF-8"
            else -> {}
        }
        return m
    }

    /** The code as modules (1 = black), smallest size. Throws IllegalArgumentException / WriterException if it does not fit. */
    fun matriz(f: Formato, texto: String): BitMatrix = MultiFormatWriter().encode(texto, f.zxing, 0, 0, pistas(f, texto))

    /** ARGB pixels of a drawn code, white around it. */
    class Pixeles(val ancho: Int, val alto: Int, val px: IntArray)

    private const val NEGRO = -0x1000000   // 0xFF000000
    private const val BLANCO = -0x1        // 0xFFFFFFFF

    /**
     * Draws [m] about [objetivo] px wide with whole pixels per module (sharp edges), with the white quiet zone readers need:
     * 4 modules round 2D codes, 10 at both sides of a barcode (which gets a height of 40 % of its width).
     */
    fun pixeles(m: BitMatrix, f: Formato, objetivo: Int = 1000): Pixeles {
        val margen = if (f.matriz) 4 else 10
        val modAncho = m.width + 2 * margen
        val escala = maxOf(1, objetivo / modAncho)
        val ancho = modAncho * escala
        if (!f.matriz) {
            val alto = maxOf(escala * 30, ancho * 2 / 5)
            val vertical = escala * 4
            val total = alto + 2 * vertical
            val px = IntArray(ancho * total) { BLANCO }
            for (x in 0 until m.width) if (m.get(x, 0)) {
                val x0 = (x + margen) * escala
                for (y in vertical until vertical + alto) for (dx in 0 until escala) px[y * ancho + x0 + dx] = NEGRO
            }
            return Pixeles(ancho, total, px)
        }
        val alto = (m.height + 2 * margen) * escala
        val px = IntArray(ancho * alto) { BLANCO }
        for (y in 0 until m.height) for (x in 0 until m.width) if (m.get(x, y)) {
            val x0 = (x + margen) * escala; val y0 = (y + margen) * escala
            for (dy in 0 until escala) {
                val fila = (y0 + dy) * ancho + x0
                for (dx in 0 until escala) px[fila + dx] = NEGRO
            }
        }
        return Pixeles(ancho, alto, px)
    }
}
