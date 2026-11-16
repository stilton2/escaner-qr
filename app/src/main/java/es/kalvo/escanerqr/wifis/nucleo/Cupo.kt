package es.kalvo.escanerqr.wifis.nucleo

/**
 * How many normal networks the phone can still save (v2.9).
 *
 * Android has a cap on saved networks (`config_wifiMaxNumWifiConfigurations`; 200 on the Galaxy S24). Going over it does not
 * fail: Android silently deletes the oldest saved networks to make room (`removeExcessNetworks`). On 2026-11-16 passing QR Bot's
 * networks in bulk deleted almost all the owner's own networks that way. So every save checks the room first, and the bulk
 * pass leaves [RESERVA] places free for the networks the owner saves by hand later.
 */
object Cupo {
    /** Places the bulk pass never fills, so that saving a network in Ajustes later does not push an old one out. */
    const val RESERVA = 20

    /** Used when the phone does not say its cap (reading it failed): the S24's value, the safe side. */
    const val TOPE_SI_NO_SE = 200

    /**
     * The cap from `cmd overlay lookup … integer/config_wifiMaxNumWifiConfigurations` (it prints the number alone).
     * Null = no cap (Android's default is -1 = unlimited); [TOPE_SI_NO_SE] if the output cannot be read.
     */
    fun leerTope(salida: String?, codigo: Int = 0): Int? {
        if (salida == null || codigo != 0) return TOPE_SI_NO_SE
        val n = salida.trim().lines().firstOrNull()?.trim()?.let { t ->
            if (t.startsWith("0x")) t.removePrefix("0x").toLongOrNull(16)?.toInt() else t.toIntOrNull()
        } ?: return TOPE_SI_NO_SE
        return if (n < 0) null else n
    }

    /** Places the bulk pass may use: cap minus [RESERVA] minus the networks already saved (never below 0). */
    fun huecosEnBloque(tope: Int?, normales: Int): Int =
        if (tope == null) Int.MAX_VALUE else (tope - RESERVA - normales).coerceAtLeast(0)

    /**
     * Whether saving one network (by hand: the scanner's «Ponerla», «Añadir», «Cambiar contraseña») can go ahead.
     * Updating a network that is already saved with the same name and type does not add one, so it always can.
     */
    fun cabeUna(tope: Int?, normales: List<RedListada>, d: DatosRed): Boolean {
        if (tope == null || normales.size < tope) return true
        return normales.any { it.ssid == d.ssid && Seguridad.desdeTipos(it.tipos) == d.seguridad }
    }

    fun textoLleno(tope: Int): String =
        "El móvil ya tiene $tope redes guardadas, el máximo que deja Android. Si guardo otra, Android borra sin avisar " +
            "la más antigua. Olvida antes alguna que no uses (Wifis → su ficha → Olvidar)."
}
