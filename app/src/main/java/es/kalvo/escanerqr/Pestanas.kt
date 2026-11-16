package es.kalvo.escanerqr

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Tab bar at the top («estilo A», S-J4): teal, icon + text; the active tab in white with an amber line under it.
 * It goes under the status bar (its top padding is the status bar height, see [margenArriba]).
 */
class Pestanas(private val ui: Ui, private val alElegir: (Int) -> Unit) {
    private data class Una(val id: Int, val vista: LinearLayout, val icono: ImageView, val texto: TextView, val raya: View)

    private val lista = ArrayList<Una>()
    val vista = LinearLayout(ui.ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        setBackgroundColor(ui.cBarra)
    }
    var actual = 0
        private set

    fun anadir(id: Int, texto: String, icono: Int) {
        val ctx = ui.ctx
        val img = ImageView(ctx).apply { setImageResource(icono) }
        val txt = TextView(ctx).apply { text = texto; textSize = 13f; gravity = Gravity.CENTER; maxLines = 1 }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, ui.px(10), 0, ui.px(9))
            addView(img, LinearLayout.LayoutParams(ui.px(26), ui.px(26)))
            addView(txt, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(4) })
        }
        val raya = View(ctx).apply { setBackgroundColor(ui.cAmbar) }
        val marco = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = ui.px(48)
            val fondo = android.util.TypedValue()
            ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, fondo, true)
            foreground = ctx.getDrawable(fondo.resourceId)
            isClickable = true; isFocusable = true
            contentDescription = texto
            accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.TabWidget"
                    info.isSelected = actual == id
                }
            }
            addView(FrameLayout(ctx).apply { addView(col, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)) })
            addView(raya, LinearLayout.LayoutParams(MATCH_PARENT, ui.px(3)))
            setOnClickListener { elegir(id) }
        }
        vista.addView(marco, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        lista += Una(id, marco, img, txt, raya)
        pintar()
    }

    /** Selects [id] and tells the activity (like tapping it). */
    fun elegir(id: Int) {
        actual = id
        pintar()
        alElegir(id)
    }

    private fun pintar() {
        val apagado = ui.c(R.color.barra_texto)
        val activo = ui.c(R.color.barra_activa)
        for (p in lista) {
            val si = p.id == actual
            val col = if (si) activo else apagado
            p.icono.imageTintList = ColorStateList.valueOf(col)
            p.texto.setTextColor(col)
            p.texto.typeface = Typeface.create(Typeface.DEFAULT, if (si) Typeface.BOLD else Typeface.NORMAL)
            p.raya.visibility = if (si) View.VISIBLE else View.INVISIBLE
            p.vista.isSelected = si
        }
    }

    fun margenArriba(top: Int) { vista.setPadding(0, top, 0, 0) }
}
