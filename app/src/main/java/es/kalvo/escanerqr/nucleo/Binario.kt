package es.kalvo.escanerqr.nucleo

/**
 * Codes whose content zxing-cpp cannot turn into text (S-J8): binary data or mixed character sets (ECI) give an empty text
 * but the bytes are read fine. Before they were dropped; now they are shown as the bytes in hexadecimal, with a clear label,
 * so nothing is made up and nothing is lost. Pure Kotlin (tested).
 */
object Binario {
    const val PREFIJO = "Datos binarios (hex): "

    /** «Datos binarios (hex): E1 A1 …», or null if there are no bytes. */
    fun texto(bytes: ByteArray?): String? {
        if (bytes == null || bytes.isEmpty()) return null
        val sb = StringBuilder(PREFIJO.length + bytes.size * 3)
        sb.append(PREFIJO)
        for ((i, b) in bytes.withIndex()) {
            if (i > 0) sb.append(' ')
            val v = b.toInt() and 255
            sb.append(HEX[v shr 4]).append(HEX[v and 15])
        }
        return sb.toString()
    }

    /** The bytes back from a text made by [texto] (null if it is not one). */
    fun bytes(texto: String): ByteArray? {
        if (!texto.startsWith(PREFIJO)) return null
        val partes = texto.substring(PREFIJO.length).split(' ').filter { it.isNotEmpty() }
        if (partes.isEmpty()) return null
        val out = ByteArray(partes.size)
        for ((i, p) in partes.withIndex()) {
            if (p.length != 2) return null
            out[i] = (p.toIntOrNull(16) ?: return null).toByte()
        }
        return out
    }

    private const val HEX = "0123456789ABCDEF"
}
