package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.content.Intent
import android.net.wifi.WifiNetworkSuggestion
import android.provider.Settings
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.Seguridad

/**
 * «Conectar» without Shizuku (S-J4, for phones without Shizuku): Android's own window «¿Guardar esta red?»
 * (`Settings.ACTION_WIFI_ADD_NETWORKS`, Android 11+) with the network in `EXTRA_WIFI_NETWORK_LIST`.
 * If you accept, Android keeps it as a normal saved network (as if you had typed it in Ajustes) and connects on its own
 * when it is in range. Nothing needs Shizuku nor any wifi permission. With Shizuku, «Conectar» stays as before (direct).
 */
object SinShizuku {
    /** The network for Android's window, or the reason why it cannot go that way (WEP, enterprise, bad password). */
    fun sugerencia(d: DatosRed): Result<WifiNetworkSuggestion> = runCatching {
        val b = WifiNetworkSuggestion.Builder().setSsid(d.ssid)
        when (d.seguridad) {
            Seguridad.WPA -> b.setWpa2Passphrase(d.clave ?: error("falta la contraseña"))
            Seguridad.WPA3 -> b.setWpa3Passphrase(d.clave ?: error("falta la contraseña"))
            Seguridad.ABIERTA -> {}
            Seguridad.OWE -> b.setIsEnhancedOpen(true)
            Seguridad.WEP -> error("Android no deja guardar redes WEP (antiguas) desde otra app sin Shizuku")
            Seguridad.EMPRESA -> error("las redes de empresa se guardan en Ajustes")
        }
        if (d.oculta) b.setIsHiddenSsid(true)
        b.build()
    }

    fun intent(s: WifiNetworkSuggestion): Intent =
        Intent(Settings.ACTION_WIFI_ADD_NETWORKS).putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(s))

    /** What Android answered, in plain words. */
    fun resultado(codigo: Int, datos: Intent?, ssid: String): String {
        if (codigo != Activity.RESULT_OK) return "No se ha guardado «$ssid»."
        val r = datos?.getIntegerArrayListExtra(Settings.EXTRA_WIFI_NETWORK_RESULT_LIST)?.firstOrNull()
        return when (r) {
            Settings.ADD_WIFI_RESULT_SUCCESS, null -> "Guardada «$ssid». El móvil se conecta solo cuando la tenga cerca."
            Settings.ADD_WIFI_RESULT_ALREADY_EXISTS -> "«$ssid» ya estaba guardada así en el móvil."
            else -> "Android no ha podido guardar «$ssid»."
        }
    }
}
