package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import es.kalvo.escanerqr.Db

/**
 * Self-check of the Wifis tab without touching the screen: `adb shell am instrument -w es.kalvo.escanerqr/.wifis.Diagnostico`.
 * Runs inside the app (same hidden-API rules and Shizuku permission as the app), loads the networks like the tab does and
 * answers ONLY counts (also of the history, S-J5) and the warnings' reasons: never network names, places, MACs or passwords. Changes nothing.
 */
class Diagnostico : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val out = Bundle()
        try {
            // History (S-J5): only how many reads and how many were scanned here / imported. Never texts or file names.
            try {
                val cuenta = Db.de(targetContext).cuentaPorOrigen()
                out.putInt("historial_total", cuenta.values.sum())
                out.putInt("historial_escaneadas", cuenta[""] ?: 0)
                out.putInt("historial_importadas", cuenta.filterKeys { it.isNotEmpty() && it != es.kalvo.escanerqr.nucleo.Historial.CREADO }.values.sum())
                out.putInt("historial_creadas", cuenta[es.kalvo.escanerqr.nucleo.Historial.CREADO] ?: 0)
                out.putInt("historial_importadas_qr_bot", cuenta["QR Bot"] ?: 0)
            } catch (e: Throwable) { out.putString("historial_fallo", e.javaClass.simpleName) }
            // Shizuku's binder arrives a moment after the app starts.
            var i = 0
            while (!Shell.arrancado() && i++ < 50) Thread.sleep(200)
            out.putString("shizuku", EstadoShizuku.ahora().name)
            if (EstadoShizuku.ahora() == EstadoShizuku.E.LISTO) {
                val t0 = System.currentTimeMillis()
                val historial = try { Db.de(targetContext).wifis() } catch (e: Throwable) { emptyList() }
                val c = Redes.cargar(historial)
                val r = c.redes
                out.putInt("redes", r.size)
                out.putInt("normales", r.count { !it.sugerencia })
                out.putInt("sugerencias", r.count { it.sugerencia })
                out.putInt("con_ubicacion", r.count { it.ubicacion != null })
                out.putInt("con_detalle", r.count { it.detalle != null })
                out.putInt("con_fecha_guardada", r.count { it.uso?.creada != null })
                out.putInt("con_ultima_vez", r.count { it.ultimaVez != null })
                out.putInt("nunca_conectadas", r.count { it.uso?.conexiones == 0 })
                out.putInt("conectada_ahora", r.count { it.detalle?.conectadaAhora == true })
                out.putInt("desactivadas", r.count { it.detalle?.desactivada != null })
                out.putInt("mac_telefono", r.count { it.detalle?.macAjuste == 0 })
                out.putInt("ip_fija", r.count { it.detalle?.ip == "STATIC" })
                out.putInt("con_proxy", r.count { it.detalle?.proxy.let { p -> p != null && p != "NONE" && p != "UNASSIGNED" } })
                out.putInt("sin_autoconexion", r.count { it.detalle?.autoConexion == false })
                out.putInt("medida_forzada", r.count { (it.detalle?.medida ?: 0) != 0 })
                out.putInt("lecturas_wifi_historial", historial.size)
                out.putInt("redes_escaneadas", r.count { it.escaneos.isNotEmpty() })
                out.putLong("ms", System.currentTimeMillis() - t0)
                out.putString("avisos", c.avisos.joinToString(" | ").ifEmpty { "ninguno" })
            }
        } catch (e: Throwable) {
            out.putString("fallo", e.javaClass.name + ": " + (e.message?.take(120) ?: ""))
        }
        finish(Activity.RESULT_OK, out)
    }
}
