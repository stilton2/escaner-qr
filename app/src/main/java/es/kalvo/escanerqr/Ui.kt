package es.kalvo.escanerqr

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import es.kalvo.escanerqr.nucleo.Clase

/**
 * Builders for the screens made in code, «estilo A» (S-J4): dark grey, teal bar, amber main button,
 * cards with 16 dp corners, 44/56 dp icon squares coloured by type. Colours in res/values(-night)/colors.xml.
 */
class Ui(val ctx: Context, @Suppress("unused") private val ref: View? = null) {
    private val dp = ctx.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()
    fun c(id: Int) = ContextCompat.getColor(ctx, id)

    val cPrimary get() = c(R.color.ambar_texto)
    val cMuted get() = c(R.color.texto_suave)
    val cText get() = c(R.color.texto)
    val cFondo get() = c(R.color.fondo)
    val cSuperficie get() = c(R.color.superficie)
    val cBorde get() = c(R.color.borde)
    val cLinea get() = c(R.color.linea)
    val cAmbar get() = c(R.color.ambar)
    val cSobreAmbar get() = c(R.color.sobre_ambar)
    val cBarra get() = c(R.color.barra)

    fun texto(s: CharSequence, size: Float = 15f, col: Int = cText, bold: Boolean = false) = TextView(ctx).apply {
        text = s; textSize = size; setTextColor(col)
        if (bold) typeface = Typeface.create(typeface, Typeface.BOLD)
    }

    fun mono(t: TextView, bold: Boolean = false) = t.apply { typeface = Typeface.create(Typeface.MONOSPACE, if (bold) Typeface.BOLD else Typeface.NORMAL) }

    /** Amber section header in capitals («DATOS DE LA RED»). */
    fun titulo(s: CharSequence) = texto(s.toString().uppercase(java.util.Locale.forLanguageTag("es-ES")), 13f, cPrimary, bold = true).apply {
        letterSpacing = 0.04f
        setPadding(px(2), px(18), px(2), px(8))
    }

    /** Small caps label over a value inside a card («CONTRASEÑA», «MAC»). */
    fun etiqueta(s: String) = texto(s.uppercase(java.util.Locale.forLanguageTag("es-ES")), 12f, cMuted).apply { letterSpacing = 0.04f }

    fun redondo(color: Int, radioDp: Int, borde: Int? = null) = GradientDrawable().apply {
        setColor(color); cornerRadius = px(radioDp).toFloat()
        if (borde != null) setStroke(px(1), borde)
    }

    fun tarjeta(padre: LinearLayout, build: LinearLayout.() -> Unit): MaterialCardView {
        val inner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(14), px(16), px(14))
            build()
        }
        return MaterialCardView(ctx).apply {
            radius = px(16).toFloat(); cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(cSuperficie)
            addView(inner)
        }.also { padre.addView(it, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(10) }) }
    }

    private fun estados(activo: Int, apagado: Int) =
        ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(apagado, activo))

    /** The main button: amber, dark text, 56 dp, 16 dp corners. */
    fun botonPrincipal(texto: String, alto: Int = 56, accion: () -> Unit) = MaterialButton(ctx).apply {
        text = texto; isAllCaps = false; textSize = if (alto >= 56) 17f else 14f
        typeface = Typeface.create(typeface, Typeface.BOLD)
        backgroundTintList = estados(cAmbar, cBorde)
        setTextColor(estados(cSobreAmbar, cMuted))
        iconTint = estados(cSobreAmbar, cMuted)
        cornerRadius = px(if (alto >= 56) 16 else 12)
        insetTop = 0; insetBottom = 0
        minHeight = px(alto); minimumHeight = px(alto)
        setOnClickListener { accion() }
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, px(alto))
    }

    /** Secondary button: no fill, thin border, at least 48 dp. */
    fun botonSecundario(texto: String, icono: Int? = null, alto: Int = 52, accion: () -> Unit) =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = texto; isAllCaps = false; textSize = 14f
            setTextColor(estados(cText, cMuted))
            strokeColor = ColorStateList.valueOf(cBorde); strokeWidth = px(1)
            cornerRadius = px(14)
            insetTop = 0; insetBottom = 0
            minHeight = px(alto); minimumHeight = px(alto)
            if (icono != null) {
                setIconResource(icono); iconTint = ColorStateList.valueOf(cText)
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            }
            setOnClickListener { accion() }
        }

    /** Compatibility with the older screens: filled = main (48 dp), outlined = secondary. */
    fun boton(texto: String, contorno: Boolean = false, accion: () -> Unit): MaterialButton =
        if (contorno) botonSecundario(texto, alto = 48, accion = accion)
        else botonPrincipal(texto, alto = 48, accion = accion).apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, px(48)) }

    /** Full-width secondary button with an icon, like the actions of a result. */
    fun accion(texto: String, icono: Int, accion: () -> Unit) = botonSecundario(texto, icono, 52, accion).apply {
        iconGravity = MaterialButton.ICON_GRAVITY_START
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
        iconPadding = px(16)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(8) }
    }

    /** Round 48 dp icon button (copy, eye…). */
    fun botonIcono(icono: Int, descripcion: String, color: Int = cPrimary, accion: () -> Unit) =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            setIconResource(icono); iconTint = ColorStateList.valueOf(color); iconSize = px(22)
            contentDescription = descripcion
            minWidth = px(48); minHeight = px(48); minimumWidth = px(48); minimumHeight = px(48)
            insetTop = 0; insetBottom = 0
            setOnClickListener { accion() }
        }

    /** (background, foreground) of the icon square for each kind: wifi green, link blue, device amber, barcode grey. */
    fun colores(clase: Clase, enLista: Boolean): Pair<Int, Int> = when (clase) {
        Clase.WIFI -> c(R.color.wifi_fondo) to c(R.color.wifi)
        Clase.ENLACE -> c(R.color.enlace_fondo) to c(R.color.enlace)
        Clase.EQUIPO -> c(if (enLista) R.color.equipo_fondo_lista else R.color.equipo_fondo) to c(R.color.equipo)
        Clase.BARRAS, Clase.OTRO -> c(R.color.gris_fondo) to c(R.color.gris)
    }

    /** Icon in a rounded square: 44 dp in lists (12 dp corners), 56 dp in the result (16 dp corners). */
    fun cuadro(icono: Int, clase: Clase, lado: Int = 44): FrameLayout {
        val (fondo, frente) = colores(clase, enLista = lado < 50)
        return FrameLayout(ctx).apply {
            background = redondo(fondo, if (lado < 50) 12 else 16)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            addView(ImageView(ctx).apply { setImageResource(icono); imageTintList = ColorStateList.valueOf(frente) },
                FrameLayout.LayoutParams(px(if (lado < 50) 26 else 32), px(if (lado < 50) 26 else 32), Gravity.CENTER))
            layoutParams = LinearLayout.LayoutParams(px(lado), px(lado))
        }
    }

    /** Small pill («Copiada al leer», «Conectada»). */
    fun pastilla(cual: String, fondo: Int = c(R.color.ficha_fondo), frente: Int = c(R.color.ficha_texto)) = texto(cual, 12f, frente, bold = true).apply {
        background = redondo(fondo, 8); setPadding(px(10), px(4), px(10), px(4))
    }

    /** Big icon + title + subtitle at the top of a result («XY-K22 / Modelo del equipo»). */
    fun cabecera(padre: LinearLayout, icono: Int, clase: Clase, titulo: String, sub: String) {
        val fila = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fila.addView(cuadro(icono, clase, 56))
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(px(16), 0, 0, 0) }
        col.addView(texto(titulo, 22f, bold = true).apply { setTextIsSelectable(true); maxLines = 3; ellipsize = TextUtils.TruncateAt.END })
        if (sub.isNotEmpty()) col.addView(texto(sub, 14f, cMuted).apply { setPadding(0, px(2), 0, 0) })
        fila.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        padre.addView(fila, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(16) })
    }

    /**
     * A card with one datum: caps label (+ optional pill), the value (monospace), and a 48 dp amber copy button.
     * [mostrado] is what is shown (e.g. grouped); [copiar] what the button does.
     */
    fun dato(padre: LinearLayout, nombre: String, mostrado: String, monoespacio: Boolean = true, tam: Float = 18f,
             marca: String? = null, copiar: (() -> Unit)?) {
        tarjeta(padre) {
            setPadding(px(16), px(12), px(4), px(12))
            val fila = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            val cab = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            cab.addView(etiqueta(nombre))
            if (marca != null) cab.addView(pastilla(marca), LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = px(8) })
            col.addView(cab)
            col.addView(texto(mostrado, tam).apply {
                if (monoespacio) mono(this)
                setTextIsSelectable(true); setPadding(0, px(4), 0, 0)
            })
            fila.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (copiar != null) fila.addView(botonIcono(R.drawable.ic_copiar, "Copiar ${nombre.lowercase()}") { copiar() })
            addView(fila)
        }
    }

    /** «Etiqueta ........ valor» row with a thin line under it (DATOS DE LA RED). [accion] makes the value a link. */
    fun filaValor(padre: LinearLayout, etiqueta: String, valor: String, ultima: Boolean = false, accion: (() -> Unit)? = null): LinearLayout {
        val fila = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = px(48)
            setPadding(0, px(10), 0, px(10))
        }
        fila.addView(texto(etiqueta, 15f, cMuted), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { marginEnd = px(12) })
        val v = texto(valor, 15f, if (accion != null) cPrimary else cText, bold = accion != null).apply {
            gravity = Gravity.END
            if (accion == null) setTextIsSelectable(true)
        }
        fila.addView(v, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.3f))
        if (accion != null) {
            fila.setOnClickListener { accion() }
            fila.isClickable = true; fila.isFocusable = true
            fila.contentDescription = "$etiqueta: $valor"
        }
        padre.addView(fila, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        if (!ultima) padre.addView(View(ctx).apply { setBackgroundColor(cLinea) }, LinearLayout.LayoutParams(MATCH_PARENT, px(1)))
        return fila
    }

    /** Info strip («Ya la tienes guardada…»). */
    fun aviso(padre: LinearLayout, s: String) = texto(s, 14f, c(R.color.aviso_texto)).apply {
        background = redondo(c(R.color.aviso_fondo), 12); setPadding(px(14), px(10), px(14), px(10))
        padre.addView(this, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(12) })
    }

    /** Buttons side by side in equal columns (10 dp apart), several rows if needed (the 3 secondary buttons of a wifi). */
    fun rejilla(padre: LinearLayout, botones: List<MaterialButton>, columnas: Int = 3, alto: Int = 64) {
        botones.chunked(columnas).forEach { trozo ->
            // Not aligned by text baseline: buttons with 1 and 2 lines would be shifted and cut at the bottom.
            val fila = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            trozo.forEachIndexed { i, b ->
                b.minHeight = px(alto); b.minimumHeight = px(alto); b.maxLines = 3
                b.setPadding(px(6), px(4), px(6), px(4))
                fila.addView(b, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = px(10) })
            }
            // Keep the column width when the last row is shorter.
            repeat(columnas - trozo.size) { fila.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = px(10) }) }
            padre.addView(fila, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = px(10) })
        }
    }

    companion object {
        /** Edge to edge: white status bar icons over the teal bar; navigation bar icons as the background needs. */
        fun ventana(act: Activity) {
            WindowCompat.setDecorFitsSystemWindows(act.window, false)
            val noche = (act.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !noche
            }
        }
    }
}
