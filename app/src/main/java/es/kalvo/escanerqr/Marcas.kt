package es.kalvo.escanerqr

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import es.kalvo.escanerqr.nucleo.Eleccion
import es.kalvo.escanerqr.nucleo.Hallado

/**
 * Several codes in view (S-J6): the picture stops, each code gets a box with its kind and the start of its text,
 * you touch the one you want. «Todos» saves them all in the history; «Seguir buscando» goes back to the camera.
 */
class Marcas(ctx: Context, private val ui: Ui, private val elegido: (Hallado) -> Unit, private val todos: (List<Hallado>) -> Unit,
             private val seguir: (List<Hallado>) -> Unit) {
    private val lienzo = Lienzo(ctx)
    private val titulo = TextView(ctx).apply {
        setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
        setShadowLayer(6f, 0f, 0f, Color.BLACK)
        setPadding(ui.px(16), ui.px(12), ui.px(16), ui.px(12))
        setBackgroundColor(0x99000000.toInt())
    }
    private val btnTodos = ui.botonPrincipal("Todos") { todos(lista) }
    val vista: FrameLayout = FrameLayout(ctx).apply {
        visibility = View.GONE
        isClickable = true
        addView(lienzo, MATCH_PARENT, MATCH_PARENT)
        addView(titulo, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP).apply { topMargin = ui.px(72) })
        val abajo = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(ui.px(16), ui.px(8), ui.px(16), ui.px(16))
            addView(ui.boton("Seguir buscando", contorno = true) { seguir(lista) }.apply {
                setTextColor(Color.WHITE); strokeColor = android.content.res.ColorStateList.valueOf(Color.WHITE)
                setBackgroundColor(0x99000000.toInt())
            }, LinearLayout.LayoutParams(0, ui.px(56), 1f).apply { marginEnd = ui.px(8) })
            addView(btnTodos, LinearLayout.LayoutParams(0, ui.px(56), 1f))
        }
        addView(abajo, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))
    }
    private var lista: List<Hallado> = emptyList()

    val visible get() = vista.visibility == View.VISIBLE

    fun mostrar(foto: Bitmap?, l: List<Hallado>) {
        lista = l
        lienzo.foto?.recycle()
        lienzo.foto = foto
        lienzo.etiquetas = l.map { Eleccion.etiqueta(it.texto, it.formato) }
        lienzo.cuadros = l
        titulo.text = "Hay ${l.size} códigos: toca el que quieras"
        btnTodos.text = "Guardar todos (${l.size})"
        vista.visibility = View.VISIBLE
        lienzo.invalidate()
    }

    fun ocultar() {
        vista.visibility = View.GONE
        lienzo.foto?.recycle(); lienzo.foto = null
        lista = emptyList(); lienzo.cuadros = emptyList()
    }

    @SuppressLint("ViewConstructor")
    private inner class Lienzo(ctx: Context) : View(ctx) {
        var foto: Bitmap? = null
        var cuadros: List<Hallado> = emptyList()
        var etiquetas: List<String> = emptyList()
        private val d = ctx.resources.displayMetrics.density
        private val borde = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 3 * d; color = ui.cAmbar }
        private val relleno = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = (ui.cAmbar and 0x00FFFFFF) or 0x33000000 }
        private val fondoEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ui.cAmbar }
        private val textoEtiqueta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ui.cSobreAmbar; textSize = 14 * d; typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        private val oscuro = Paint().apply { color = 0x55000000 }

        private fun caja(h: Hallado): RectF {
            val c = h.cuadro
            val m = 6 * d
            return RectF(c.izq * width - m, c.arriba * height - m, c.der * width + m, c.abajo * height + m)
        }

        override fun onDraw(cv: Canvas) {
            foto?.let { cv.drawBitmap(it, null, RectF(0f, 0f, width.toFloat(), height.toFloat()), null) } ?: cv.drawColor(Color.BLACK)
            cv.drawRect(0f, 0f, width.toFloat(), height.toFloat(), oscuro)
            for ((i, h) in cuadros.withIndex()) {
                val r = caja(h)
                cv.drawRoundRect(r, 8 * d, 8 * d, relleno)
                cv.drawRoundRect(r, 8 * d, 8 * d, borde)
                val t = etiquetas.getOrElse(i) { "" }
                val tw = textoEtiqueta.measureText(t)
                val alto = 28 * d
                var x = r.centerX() - tw / 2 - 10 * d
                x = x.coerceIn(4 * d, (width - tw - 24 * d).coerceAtLeast(4 * d))
                var y = r.top - alto - 4 * d
                if (y < 4 * d) y = r.bottom + 4 * d
                cv.drawRoundRect(RectF(x, y, x + tw + 20 * d, y + alto), 14 * d, 14 * d, fondoEtiqueta)
                cv.drawText(t, x + 10 * d, y + alto / 2 - (textoEtiqueta.descent() + textoEtiqueta.ascent()) / 2, textoEtiqueta)
            }
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: MotionEvent): Boolean {
            if (e.action == MotionEvent.ACTION_UP) {
                // The box touched (with a finger's margin), or the nearest one if the touch is close to it.
                val toque = 28 * d
                val cual = cuadros.filter { caja(it).let { r -> RectF(r.left - toque, r.top - toque - 32 * d, r.right + toque, r.bottom + toque).contains(e.x, e.y) } }
                    .minByOrNull { val r = caja(it); (r.centerX() - e.x) * (r.centerX() - e.x) + (r.centerY() - e.y) * (r.centerY() - e.y) }
                if (cual != null) elegido(cual)
            }
            return true
        }
    }
}
