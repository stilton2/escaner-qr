package es.kalvo.escanerqr.nucleo

/**
 * Names of the barcode formats as they go into the history and the CSV, the same ones QR Bot writes in its CSV
 * ("QR Code", "Code 128", "Data Matrix", "Code 39"…).
 * Key = name of zxing-cpp's `BarcodeReader.Format` enum (a string, so this runs in unit tests).
 */
object Formatos {
    private val NOMBRES = mapOf(
        "QR_CODE" to "QR Code", "QR_CODE_MODEL_1" to "QR Code", "QR_CODE_MODEL_2" to "QR Code",
        "MICRO_QR_CODE" to "Micro QR", "RMQR_CODE" to "rMQR",
        "DATA_MATRIX" to "Data Matrix", "AZTEC" to "Aztec", "AZTEC_CODE" to "Aztec", "AZTEC_RUNE" to "Aztec",
        "PDF_417" to "PDF 417", "COMPACT_PDF_417" to "PDF 417", "MICRO_PDF_417" to "Micro PDF417", "MAXI_CODE" to "Maxicode",
        "CODE_128" to "Code 128", "CODE_39" to "Code 39", "CODE_39_STD" to "Code 39", "CODE_39_EXT" to "Code 39",
        "CODE_32" to "Code 32", "PZN" to "PZN", "CODE_93" to "Code 93", "CODABAR" to "Codabar",
        "ITF" to "ITF", "ITF_14" to "ITF",
        "EAN_13" to "EAN-13", "EAN_8" to "EAN-8", "ISBN" to "EAN-13", "UPC_A" to "UPC-A", "UPC_E" to "UPC-E",
        "EAN_5" to "EAN-5", "EAN_2" to "EAN-2",
        "DATA_BAR" to "DataBar", "DATA_BAR_OMNI" to "DataBar", "DATA_BAR_STK" to "DataBar", "DATA_BAR_STK_OMNI" to "DataBar",
        "DATA_BAR_LTD" to "DataBar Limited", "DATA_BAR_EXP" to "DataBar Expanded", "DATA_BAR_EXP_STK" to "DataBar Expanded",
        "DX_FILM_EDGE" to "DX Film Edge", "TELEPEN" to "Telepen", "TELEPEN_ALPHA" to "Telepen", "TELEPEN_NUMERIC" to "Telepen",
        // Names of the old ZXing (Java) library, used by other scanners' exports (S-J5).
        "RSS_14" to "DataBar", "RSS_EXPANDED" to "DataBar Expanded", "UPC_EAN_EXTENSION" to "EAN-5",
    )

    /** Format of an imported read whose file does not say it (S-J5). */
    const val DESCONOCIDO = "Desconocido"

    private fun compacto(s: String) = s.uppercase().filter { it.isLetterOrDigit() }

    private val POR_COMPACTO: Map<String, String> = HashMap<String, String>().apply {
        for ((k, v) in NOMBRES) { put(compacto(k), v); put(compacto(v), v) }
        put("QR", "QR Code"); put("QRCODE", "QR Code"); put("PDF417", "PDF 417"); put("DATAMATRIX", "Data Matrix")
    }

    /**
     * A format name from another app's file → ours: `QR_CODE`, `qrcode`, `QR`, `CODE_128`, `ean13` → "QR Code", "Code 128",
     * "EAN-13". Empty → [DESCONOCIDO]. Anything else stays as written.
     */
    fun normalizar(v: String): String {
        val t = v.trim()
        if (t.isEmpty()) return DESCONOCIDO
        return POR_COMPACTO[compacto(t)] ?: if (t.contains('_') && t.all { it.isUpperCase() || it.isDigit() || it == '_' }) nombre(t) else t
    }

    fun nombre(enumZxing: String): String =
        NOMBRES[enumZxing] ?: enumZxing.lowercase().split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

    fun esMatriz(nombre: String) = nombre in setOf("QR Code", "Micro QR", "rMQR", "Data Matrix", "Aztec", "PDF 417", "Micro PDF417", "Maxicode")

    /** QR Bot keeps the QR error-correction level in metadata as `{"ec":"L"}`; we do the same. */
    fun metadata(nivelEc: String?): String {
        val ec = nivelEc?.trim().orEmpty()
        if (ec.isEmpty() || ec.any { it == '"' || it == '\\' }) return ""
        return "{\"ec\":\"$ec\"}"
    }
}
