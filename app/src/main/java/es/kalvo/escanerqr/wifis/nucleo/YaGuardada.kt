package es.kalvo.escanerqr.wifis.nucleo

/**
 * What the phone already has with the same name as a wifi just read from a QR: a normal saved network,
 * and/or "suggestions" of other apps (QR Bot saved its wifis like that). We never know the old password,
 * so the screen only says "ya está guardada", never "es distinta".
 */
data class YaGuardada(val normal: Boolean, val sugeridaPor: List<String>) {
    val nada get() = !normal && sugeridaPor.isEmpty()

    companion object {
        fun buscar(ssid: String, normales: List<RedListada>, sugerencias: List<Sugerencia>) = YaGuardada(
            normal = normales.any { it.ssid == ssid },
            sugeridaPor = sugerencias.filter { it.ssid == ssid }.map { it.paquete }.distinct(),
        )
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
