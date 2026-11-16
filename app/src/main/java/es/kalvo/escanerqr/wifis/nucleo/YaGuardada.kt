package es.kalvo.escanerqr.wifis.nucleo

/**
 * What the phone already has with the same name as a wifi just read from a QR: a normal saved network,
 * and/or "suggestions" of other apps (QR Bot saved its wifis like that).
 * [mismaClave]: whether the saved normal network has the same password as the code (null = unknown or no normal network).
 */
data class YaGuardada(val normal: Boolean, val sugeridaPor: List<String>, val mismaClave: Boolean? = null) {
    val nada get() = !normal && sugeridaPor.isEmpty()

    companion object {
        /** [claves] = saved passwords by networkId (may be empty if they could not be read); [clave] = the code's one. */
        fun buscar(
            ssid: String, normales: List<RedListada>, sugerencias: List<Sugerencia>,
            claves: Map<Int, String> = emptyMap(), clave: String? = null,
        ): YaGuardada {
            val mias = normales.filter { it.ssid == ssid }
            val guardadas = mias.mapNotNull { claves[it.id] }
            val misma = when {
                mias.isEmpty() || clave == null || guardadas.isEmpty() -> null
                else -> guardadas.any { it == clave }
            }
            return YaGuardada(
                normal = mias.isNotEmpty(),
                sugeridaPor = sugerencias.filter { it.ssid == ssid }.map { it.paquete }.distinct(),
                mismaClave = misma,
            )
        }
    }
}

object DesdeEscaner {
    /**
     * The scanner's wifi fields to the data to save. [tipo] is the scanner's normalised type
     * (`WPA`, `SAE`, `WEP` or `nopass`, see Contenido.tipoParaMisWifis); an open network never carries a password.
     */
    fun datos(ssid: String, clave: String, tipo: String, oculta: Boolean): DatosRed {
        val seg = Seguridad.desdeTexto(tipo, clave.isNotEmpty())
        return DatosRed(ssid, seg, if (seg.llevaClave) clave.takeIf { it.isNotEmpty() } else null, oculta)
    }

    /** Why it cannot be saved as it came (bad name or password for its type), in plain words; null if it is fine. */
    fun problema(d: DatosRed): String? = Claves.problemaNombre(d.ssid) ?: Claves.problemaClave(d.seguridad, d.clave)
}
