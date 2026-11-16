package es.kalvo.escanerqr

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/** The app's settings (private SharedPreferences). Defaults: like QR Bot (copy to clipboard on). */
class Ajustes(ctx: Context) {
    private val p = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    private fun b(k: String, def: Boolean) = p.getBoolean(k, def)
    private fun pon(k: String, v: Boolean) = p.edit().putBoolean(k, v).apply()

    // Al leer un código
    var copiar: Boolean get() = b("copiar", true); set(v) = pon("copiar", v)
    /** With [copiar] on: if what was read carries a MAC address (device labels), copy only the MAC. */
    var copiarMac: Boolean get() = b("copiarMac", true); set(v) = pon("copiarMac", v)
    var vibrar: Boolean get() = b("vibrar", true); set(v) = pon("vibrar", v)
    var pitido: Boolean get() = b("pitido", false); set(v) = pon("pitido", v)
    var abrirEnlaces: Boolean get() = b("abrirEnlaces", false); set(v) = pon("abrirEnlaces", v)
    var continua: Boolean get() = b("continua", false); set(v) = pon("continua", v)
    var manual: Boolean get() = b("manual", false); set(v) = pon("manual", v)

    // Historial
    var guardar: Boolean get() = b("guardar", true); set(v) = pon("guardar", v)
    var repetidos: Boolean get() = b("repetidos", true); set(v) = pon("repetidos", v)

    // Cámara
    var frontal: Boolean get() = b("frontal", false); set(v) = pon("frontal", v)
    var linternaAlAbrir: Boolean get() = b("linternaAlAbrir", false); set(v) = pon("linternaAlAbrir", v)

    /** Web search address; `{code}` is replaced by what was read. */
    var buscador: String
        get() = p.getString("buscador", null)?.takeIf { it.contains("{code}") } ?: BUSCADOR_DEFECTO
        set(v) = p.edit().putString("buscador", v).apply()

    /** Wifis tab: last order chosen (ordinal of wifis.nucleo.Orden; 0 = Nombre A-Z). S-J4, v2.3. */
    var ordenWifis: Int get() = p.getInt("ordenWifis", 0); set(v) = p.edit().putInt("ordenWifis", v).apply()

    /** 0 = como el móvil, 1 = claro, 2 = oscuro. */
    var tema: Int get() = p.getInt("tema", 0); set(v) { p.edit().putInt("tema", v).apply(); aplicarTema() }

    fun aplicarTema() = AppCompatDelegate.setDefaultNightMode(
        when (tema) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO
            2 -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )

    companion object {
        const val BUSCADOR_DEFECTO = "https://www.google.com/search?q={code}"
    }
}
