package es.kalvo.escanerqr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import es.kalvo.escanerqr.nucleo.Codigos
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Crear
import es.kalvo.escanerqr.nucleo.Formato
import es.kalvo.escanerqr.nucleo.Resumenes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Crear» (S-J9): the code made, big on white, with its text under it, and «Compartir imagen», «Guardar en la galería»,
 * «Copiar texto» and «Subir el brillo». Opened from the form ([guardar] = true: it goes to the history as «Creado») or from
 * a created entry of the history (only shows it again).
 */
class CodigoActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private lateinit var texto: String
    private lateinit var formato: Formato
    private lateinit var imagen: Bitmap
    private var brillo = false
    private var verClave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        texto = intent.getStringExtra("texto") ?: run { finish(); return }
        val nombre = intent.getStringExtra("formato") ?: Formato.QR.nombre
        formato = Formato.entries.firstOrNull { it.nombre == nombre } ?: Formato.QR
        val m = try { Codigos.matriz(formato, texto) } catch (e: Exception) {
            Toast.makeText(this, "No se puede dibujar este código (demasiado largo o no vale para ${formato.nombre}).", Toast.LENGTH_LONG).show()
            finish(); return
        }
        val p = Codigos.pixeles(m, formato, 1000)
        imagen = Bitmap.createBitmap(p.px, p.ancho, p.alto, Bitmap.Config.ARGB_8888)

        // Saved once (not again when the screen is rebuilt, e.g. after the dark/light switch).
        val guardar = intent.getBooleanExtra("guardar", false)
        if (guardar && savedInstanceState == null) Procesar.guardarCreado(this, texto, formato.nombre)

        val c = Contenido.analizar(texto, formato.nombre)
        val tipo = intent.getStringExtra("tipo") ?: Resumenes.de(texto, formato.nombre).tipo
        val esWifi = c is Contenido.Wifi

        Ui.ventana(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ui = Ui(this, raiz)
        raiz.setBackgroundColor(ui.cFondo)
        val barra = CrearActivity.barra(this, ui, "Código creado", "$tipo · ${if (formato == Formato.QR) "QR" else formato.nombre}")
        raiz.addView(barra, MATCH_PARENT, WRAP_CONTENT)
        val cuerpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(18), ui.px(18), ui.px(18), ui.px(28)) }
        val scroll = ScrollView(this).apply { addView(cuerpo, MATCH_PARENT, WRAP_CONTENT); clipToPadding = false }
        raiz.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            barra.setPadding(ui.px(4) + b.left, b.top + ui.px(10), ui.px(4) + b.right, ui.px(10))
            scroll.setPadding(b.left, 0, b.right, b.bottom)
            ins
        }

        intent.getStringExtra("nota")?.let { ui.aviso(cuerpo, it) }

        // The code on white, also in dark mode (cameras need dark on light).
        val img = ImageView(this).apply {
            // No smoothing when it is scaled to the screen: the module edges stay sharp.
            setImageDrawable(android.graphics.drawable.BitmapDrawable(resources, imagen).apply { paint.isFilterBitmap = false })
            adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Código ${formato.nombre} creado"
        }
        val tarjeta = MaterialCardView(this).apply {
            radius = ui.px(16).toFloat(); cardElevation = 0f; strokeWidth = 0; setCardBackgroundColor(Color.WHITE)
            setContentPadding(ui.px(8), ui.px(8), ui.px(8), ui.px(8))
            addView(img, MATCH_PARENT, WRAP_CONTENT)
        }
        cuerpo.addView(tarjeta, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(12) })

        // Text under it: big digits for a barcode; the text for the rest (a wifi password hidden until the eye).
        if (!formato.matriz) {
            cuerpo.addView(ui.mono(ui.texto(texto, 24f), bold = true).apply {
                gravity = Gravity.CENTER; setTextIsSelectable(true); letterSpacing = 0.08f; setPadding(0, 0, 0, ui.px(12))
            }, MATCH_PARENT, WRAP_CONTENT)
        } else ui.tarjeta(cuerpo) {
            val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val t = ui.texto(if (esWifi) Crear.ocultarClaveWifi(texto) else texto, 14f, ui.cMuted).apply { setTextIsSelectable(!esWifi) }
            fila.addView(t, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            if (esWifi && (c as Contenido.Wifi).clave.isNotEmpty()) {
                lateinit var ojo: MaterialButton
                ojo = ui.botonIcono(R.drawable.ic_ver, "Ver contraseña", ui.cText) {
                    verClave = !verClave
                    t.text = if (verClave) texto else Crear.ocultarClaveWifi(texto)
                    ojo.setIconResource(if (verClave) R.drawable.ic_ver_no else R.drawable.ic_ver)
                    ojo.contentDescription = if (verClave) "Ocultar contraseña" else "Ver contraseña"
                }
                fila.addView(ojo)
            }
            addView(fila)
        }

        cuerpo.addView(ui.botonPrincipal("Compartir imagen") { Qr.compartir(this, paraGuardar(), "Compartir código") }.apply {
            setIconResource(R.drawable.ic_compartir); iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { bottomMargin = ui.px(10) }
        })
        cuerpo.addView(ui.accion("Guardar en la galería", R.drawable.ic_guardar) { guardarGaleria() })
        cuerpo.addView(ui.accion("Copiar texto", R.drawable.ic_copiar) { Acciones.copiar(this, texto, sensible = esWifi) })
        lateinit var bBrillo: MaterialButton
        bBrillo = ui.accion("Subir el brillo (para que otro móvil lo lea)", R.drawable.ic_brillo) {
            brillo = !brillo
            window.attributes = window.attributes.apply {
                screenBrightness = if (brillo) 1f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            if (brillo) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            bBrillo.text = if (brillo) "Volver al brillo normal" else "Subir el brillo (para que otro móvil lo lea)"
        }
        cuerpo.addView(bBrillo)

        val pie = when {
            !guardar -> "Creado con esta app."
            Ajustes(this).guardar -> "Guardado en el Historial (sale como «creado»)."
            else -> "No se ha guardado en el Historial (lo tienes apagado en Ajustes)."
        }
        cuerpo.addView(ui.texto(pie, 13f, ui.cMuted).apply { setPadding(0, ui.px(8), 0, 0) })
    }

    /** Picture to share or save: the code; a barcode gets its digits printed under it, like on a product. */
    private fun paraGuardar(): Bitmap {
        if (formato.matriz) return imagen
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK; typeface = Typeface.MONOSPACE; textAlign = Paint.Align.CENTER
            textSize = (imagen.width / 14f).coerceAtMost(72f)
        }
        while (p.measureText(texto) > imagen.width * 0.92f && p.textSize > 16f) p.textSize -= 2f
        val extra = (p.textSize * 1.6f).toInt()
        val out = Bitmap.createBitmap(imagen.width, imagen.height + extra, Bitmap.Config.ARGB_8888)
        val cv = Canvas(out)
        cv.drawColor(Color.WHITE)
        cv.drawBitmap(imagen, 0f, 0f, null)
        cv.drawText(texto, imagen.width / 2f, imagen.height + p.textSize * 0.9f, p)
        return out
    }

    private fun guardarGaleria() {
        val nombre = "codigo_" + formato.nombre.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]+"), "") + "_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".png"
        val bmp = paraGuardar()
        Thread {
            val ok = Qr.guardarEnGaleria(this, bmp, nombre)
            runOnUiThread {
                Toast.makeText(this, if (ok) "Guardada en Galería, álbum «Escáner QR»" else "No he podido guardar la imagen", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    companion object {
        /** [formato] = name as in the history («QR Code», «EAN-13»…). [nota] = a remark shown on top (e.g. check digit added). */
        fun abrir(ctx: Context, texto: String, formato: String, guardar: Boolean, tipo: String? = null, nota: String? = null) {
            ctx.startActivity(Intent(ctx, CodigoActivity::class.java)
                .putExtra("texto", texto).putExtra("formato", formato).putExtra("guardar", guardar)
                .apply { if (tipo != null) putExtra("tipo", tipo); if (nota != null) putExtra("nota", nota) })
        }
    }
}
