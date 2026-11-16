package es.kalvo.escanerqr.nucleo

/** A code found in a camera frame: what it says and where it is ([cuadro], 0–1 in the picture as you see it). */
data class Hallado(val texto: String, val formato: String, val metadata: String, val cuadro: Cuadro)

/** Setting «Preferir tipo» (S-J6): with several codes in view, this kind is read straight away. */
enum class Preferido(val nombre: String) { NINGUNO("Ninguno"), MAC("MAC"), SIM("SIM"), QR("QR"), BARRAS("Código de barras") }

/** What to do with the codes of a frame. */
sealed class Decision {
    object Nada : Decision()
    data class Uno(val h: Hallado) : Decision()
    /** Several: freeze the picture and let you touch one (top to bottom, left to right). */
    data class Varios(val lista: List<Hallado>) : Decision()
}

/**
 * Which code to read when the camera sees more than one (S-J6: no tapar con la mano los que no quiere).
 * Pure Kotlin (tested).
 */
object Eleccion {
    /** How far around the middle still counts as «touching the crosshair» (fraction of the picture). */
    const val MARGEN_MIRA = 0.03f

    fun decidir(hallados: List<Hallado>, soloMira: Boolean, preferido: Preferido): Decision {
        val unicos = sinRepetir(hallados)
        if (unicos.isEmpty()) return Decision.Nada
        if (soloMira) {
            val enMira = unicos.filter { it.cuadro.contiene(0.5f, 0.5f, MARGEN_MIRA) }
            return enMira.minByOrNull { it.cuadro.alCentro() }?.let { Decision.Uno(it) } ?: Decision.Nada
        }
        if (unicos.size == 1) return Decision.Uno(unicos[0])
        if (preferido != Preferido.NINGUNO) {
            unicos.filter { tipo(it.texto, it.formato) == preferido }.minByOrNull { it.cuadro.alCentro() }?.let { return Decision.Uno(it) }
        }
        return Decision.Varios(unicos.sortedWith(compareBy({ (it.cuadro.cy * 10).toInt() }, { it.cuadro.cx })))
    }

    /** The same code found twice (two passes, two frames) counts once. */
    fun sinRepetir(l: List<Hallado>): List<Hallado> {
        val vistos = HashSet<String>()
        return l.filter { vistos.add(it.formato + "\u0000" + it.texto) }
    }

    /** Which «Preferir tipo» kind a code is (NINGUNO = none of them, e.g. a Data Matrix with a plain text). */
    fun tipo(texto: String, formato: String): Preferido = when (Especial.de(texto, formato)) {
        is Especial.Equipo -> Preferido.MAC
        is Especial.Sim -> Preferido.SIM
        else -> when {
            formato in QR -> Preferido.QR
            !Formatos.esMatriz(formato) && formato != Formatos.DESCONOCIDO -> Preferido.BARRAS
            else -> Preferido.NINGUNO
        }
    }

    private val QR = setOf("QR Code", "Micro QR", "rMQR")

    /** Short label drawn over each code: «MAC 00:1D:94:…», «SIM 8934 5678…», «QR enlace qrw.es/Eh5…», «EAN-13 8412345678905». */
    fun etiqueta(texto: String, formato: String): String {
        val corto = if (formato == "QR Code") "QR" else formato
        return when (val e = Especial.de(texto, formato)) {
            is Especial.Equipo -> "MAC " + recortar(e.macs.firstOrNull() ?: "", 9)
            is Especial.Sim -> "SIM " + recortar(Especial.agrupar4(e.iccid), 9)
            else -> {
                val r = Resumenes.de(texto, formato)
                val tipo = r.tipo.lowercase()
                val cabeza = if (tipo == formato.lowercase() || tipo == corto.lowercase()) corto else "$corto $tipo"
                "$cabeza " + recortar(r.principal, 14)
            }
        }.trim()
    }

    private fun recortar(s: String, n: Int) = if (s.length <= n) s else s.take(n) + "…"
}
