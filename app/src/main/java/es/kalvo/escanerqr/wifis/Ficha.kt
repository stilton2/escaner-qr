package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import es.kalvo.escanerqr.wifis.nucleo.Cuando
import es.kalvo.escanerqr.wifis.nucleo.Red

/**
 * Everything about one network: password (hidden, eye, copy), who saved it, when (saved / last connected), where it is used,
 * its reads in the history, its settings as Ajustes shows them, and what you can do.
 */
object Ficha {
    fun puedeQr(r: Red) = r.seguridad.sencilla

    /** Open networks, or ones whose password was read, go straight to the QR; otherwise it is asked for first. */
    fun verQr(act: Activity, r: Red) {
        if (!r.seguridad.llevaClave || r.clave != null) Dialogos.abrirQr(act, r.datos()) else Dialogos.soloQr(act, r.datos())
    }

    /**
     * The network's card («estilo A», S-J4): big icon and name, password (if known), «Conectar ahora» in amber,
     * secondary buttons, then «DATOS DE LA RED», «AJUSTES DE LA RED» and its reads in the history as label · value rows.
     */
    fun abrir(act: Activity, r: Red, redes: List<Red>, alCambiar: () -> Unit) {
        val ui = es.kalvo.escanerqr.Ui(act)
        val hoja = BottomSheetDialog(act)
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(18), ui.px(12), ui.px(18), ui.px(28)) }
        hoja.setContentView(ScrollView(act).apply { addView(col, MATCH_PARENT, WRAP_CONTENT) })

        fun nota(t: String) = col.addView(ui.texto(t, 14f, ui.cMuted).apply { setPadding(ui.px(2), ui.px(2), ui.px(2), ui.px(10)) })

        ui.cabecera(col, es.kalvo.escanerqr.R.drawable.ic_wifi,
            if (r.sugerencia) es.kalvo.escanerqr.nucleo.Clase.OTRO else es.kalvo.escanerqr.nucleo.Clase.WIFI,
            r.ssid, r.seguridad.nombre + if (r.oculta) " · red oculta" else "")

        // Password
        if (r.seguridad.llevaClave) {
            val clave = r.clave
            if (clave != null) ui.tarjeta(col) {
                setPadding(ui.px(16), ui.px(12), ui.px(4), ui.px(12))
                var ver = false
                val fila = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
                val c2 = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
                c2.addView(ui.etiqueta("Contraseña"))
                val valor = ui.mono(ui.texto("••••••••", 20f).apply { setPadding(0, ui.px(4), 0, 0) })
                c2.addView(valor)
                fila.addView(c2, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                lateinit var ojo: MaterialButton
                ojo = ui.botonIcono(es.kalvo.escanerqr.R.drawable.ic_ver, "Ver contraseña", ui.cText) {
                    ver = !ver
                    valor.text = if (ver) clave else "••••••••"
                    ojo.setIconResource(if (ver) es.kalvo.escanerqr.R.drawable.ic_ver_no else es.kalvo.escanerqr.R.drawable.ic_ver)
                    ojo.contentDescription = if (ver) "Ocultar contraseña" else "Ver contraseña"
                }
                fila.addView(ojo)
                fila.addView(ui.botonIcono(es.kalvo.escanerqr.R.drawable.ic_copiar, "Copiar contraseña") { Ui.copiar(act, "Contraseña wifi", clave) })
                addView(fila)
            } else nota((if (r.sugerencia) "Es de otra app: Android no deja ver su contraseña. " else "No pude leer su contraseña (¿Shizuku listo?). ") + "Para el QR o para cambiarla, la escribes tú. " +
                "(Samsung la enseña en Ajustes → Wi-Fi → ⋮ → Administrar redes.)")
        } else nota("Sin contraseña (red abierta)")

        if (r.sugerencia) ui.aviso(col, "Es una «sugerencia» de esa app, no una red normal: ni Ajustes ni esta app pueden cambiarle la " +
            "contraseña ni olvidarla, y desaparece si desinstalas la app. Para no perderla, pásala a red normal.")

        // Actions
        if (r.olvidable && r.seguridad.sencilla) col.addView(ui.botonPrincipal("Conectar ahora") {
            hoja.dismiss()
            if (EstadoShizuku.ahora() != EstadoShizuku.E.LISTO) { Toast.makeText(act, "Shizuku no está listo", Toast.LENGTH_SHORT).show(); return@botonPrincipal }
            act.fondo({ Redes.conectarGuardada(r.id) }) { res ->
                val fallo = res.getOrElse { e -> e.javaClass.simpleName }
                if (fallo == null) act.avisarConexion(r.ssid)
                else Toast.makeText(act, "No se pudo conectar a «${r.ssid}»: $fallo", Toast.LENGTH_LONG).show()
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { bottomMargin = ui.px(10) } })
        val botones = ArrayList<MaterialButton>()
        if (puedeQr(r)) botones += ui.botonSecundario("QR para compartir") { hoja.dismiss(); verQr(act, r) }
        if (r.olvidable && r.seguridad.sencilla) {
            if (r.seguridad.llevaClave) botones += ui.botonSecundario("Cambiar contraseña") { hoja.dismiss(); Dialogos.cambiarClave(act, r, alCambiar) }
            botones += ui.botonSecundario("Olvidar esta red") { hoja.dismiss(); Dialogos.olvidar(act, r, alCambiar) }
        }
        if (r.sugerencia && r.seguridad.sencilla && redes.none { !it.sugerencia && it.ssid == r.ssid }) {
            botones += ui.botonSecundario("Pasarla a red normal") { hoja.dismiss(); Dialogos.anadir(act, r.datos(), redes, "Pasar a red normal", alCambiar) }
        }
        if (botones.isNotEmpty()) ui.rejilla(col, botones, columnas = minOf(3, botones.size))
        if (r.sugerencia && redes.any { !it.sugerencia && it.ssid == r.ssid }) nota("Ya tienes también una red normal con este nombre: esa es la que se queda.")
        if (!r.seguridad.sencilla) nota("Las redes de empresa (usuario y certificado) se cambian en Ajustes.")

        // DATOS DE LA RED (when, who, where, scans) and its settings
        col.addView(ui.titulo("Datos de la red"))
        FilasRed.datos(act, ui, col, r)
        r.uso?.let { u ->
            if ((u.conexiones ?: 0) > 0 && u.recibido != null && u.enviado != null)
                nota("Datos: ${u.recibido} recibidos, ${u.enviado} enviados.")
        }
        col.addView(ui.titulo("Ajustes de la red"))
        FilasRed.ajustes(ui, col, r)

        // Reads in the history (scanned with this app or imported from another one)
        col.addView(ui.titulo("En tu historial de lecturas"))
        if (r.escaneos.isEmpty()) {
            nota("No hay ninguna lectura de un QR con este nombre de red (ni escaneada ni importada).")
        } else {
            val lista = r.escaneos.take(10)
            lista.forEachIndexed { i, e ->
                ui.filaValor(col, "Lectura del " + Cuando.larga(e.instante), "Abrir", ultima = i == lista.size - 1) {
                    val l = try { es.kalvo.escanerqr.Db.de(act).una(e.id) } catch (_: Throwable) { null }
                    if (l == null) Toast.makeText(act, "Esa lectura ya no está en el historial", Toast.LENGTH_SHORT).show()
                    else { hoja.dismiss(); es.kalvo.escanerqr.Procesar.abrirResultado(act, l) }
                }
            }
            if (r.escaneos.size > 10) nota("…y ${r.escaneos.size - 10} más en el Historial.")
        }

        hoja.behavior.skipCollapsed = true
        hoja.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        hoja.show()
    }
}
