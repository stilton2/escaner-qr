package es.kalvo.escanerqr

import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import es.kalvo.escanerqr.nucleo.Clase
import es.kalvo.escanerqr.nucleo.Crear

/**
 * «Crear» tab (S-J9, like QR Bot's): the list of what can be put in a code. Each row opens [CrearActivity] with its form;
 * the code is made on the phone (ZXing), without internet.
 */
class PanelCrear(private val act: MainActivity) {
    private val raiz = ScrollView(act)
    private val ui = Ui(act, raiz)
    val vista: View get() = raiz

    init {
        raiz.setBackgroundColor(ui.cFondo)
        val col = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.px(16), ui.px(4), ui.px(16), ui.px(24))
        }
        col.addView(ui.titulo("Crear un código"))
        col.addView(ui.texto("Elige qué quieres guardar dentro. El código se hace en el móvil, sin internet, y queda en el Historial.",
            14f, ui.cMuted).apply { setPadding(ui.px(2), 0, ui.px(2), ui.px(12)) })
        for (t in Crear.Tipo.entries) fila(col, t)
        raiz.addView(col, MATCH_PARENT, WRAP_CONTENT)
    }

    private fun fila(padre: LinearLayout, t: Crear.Tipo) {
        val (icono, clase) = icono(t)
        ui.tarjeta(padre) {
            setPadding(ui.px(14), ui.px(10), ui.px(14), ui.px(10))
            val f = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.px(52) }
            f.addView(ui.cuadro(icono, clase, 44))
            val c = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(14), 0, 0, 0) }
            c.addView(ui.texto(t.titulo, 17f, bold = true))
            c.addView(ui.texto(t.ayuda, 13f, ui.cMuted).apply { setPadding(0, ui.px(2), 0, 0) })
            f.addView(c, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(f)
        }.apply {
            isClickable = true; isFocusable = true
            contentDescription = "Crear ${t.titulo.lowercase()}: ${t.ayuda}"
            setOnClickListener { CrearActivity.abrir(act, t) }
        }
    }

    fun margenArriba(@Suppress("UNUSED_PARAMETER") top: Int) {}

    companion object {
        /** Icon and colour family of each kind (same icons as the results of a scan). */
        fun icono(t: Crear.Tipo): Pair<Int, Clase> = when (t) {
            Crear.Tipo.TEXTO -> R.drawable.ic_texto to Clase.OTRO
            Crear.Tipo.ENLACE -> R.drawable.ic_enlace to Clase.ENLACE
            Crear.Tipo.WIFI -> R.drawable.ic_wifi to Clase.WIFI
            Crear.Tipo.TELEFONO -> R.drawable.ic_llamar to Clase.OTRO
            Crear.Tipo.SMS -> R.drawable.ic_sms to Clase.OTRO
            Crear.Tipo.CORREO -> R.drawable.ic_correo to Clase.OTRO
            Crear.Tipo.CONTACTO -> R.drawable.ic_contacto to Clase.OTRO
            Crear.Tipo.UBICACION -> R.drawable.ic_mapa to Clase.OTRO
            Crear.Tipo.EVENTO -> R.drawable.ic_calendario to Clase.OTRO
            Crear.Tipo.PORTAPAPELES -> R.drawable.ic_portapapeles to Clase.OTRO
            Crear.Tipo.BARRAS -> R.drawable.ic_barras to Clase.BARRAS
        }
    }
}
