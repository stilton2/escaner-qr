package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import es.kalvo.escanerqr.wifis.nucleo.TextoQr

/** The QR of one network, big and bright, for another phone to read. Data comes from [Memoria], not from the intent. */
class QrActivity : Activity() {
    private val ui by lazy { Ui(this) }
    private var verClave = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = Memoria.qr ?: run { finish(); return }
        // Full brightness and screen on while it is shown, so the other camera reads it at once.
        window.attributes = window.attributes.apply { screenBrightness = 1f }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.px(16), ui.px(36), ui.px(16), ui.px(24))
        }
        setContentView(ScrollView(this).apply { addView(root, MATCH_PARENT, WRAP_CONTENT) })

        root.addView(ui.text(d.ssid, 26f, bold = true).apply { gravity = Gravity.CENTER })
        root.addView(ui.text(d.seguridad.nombre + if (d.oculta) " · red oculta" else "", 14f).apply {
            gravity = Gravity.CENTER; setTextColor(ui.muted(root)); setPadding(0, ui.px(4), 0, ui.px(16))
        })

        val lado = (resources.displayMetrics.widthPixels - ui.px(48)).coerceAtMost(ui.px(420))
        val img = ImageView(this).apply {
            setImageBitmap(Qr.imagen(TextoQr.crear(d), lado))
            setBackgroundColor(Color.WHITE)
            contentDescription = "Código QR de la wifi ${d.ssid}"
        }
        // White card around the QR, also in dark mode (cameras need dark on light).
        val tarjeta = MaterialCardView(this).apply {
            radius = ui.px(20).toFloat(); cardElevation = 0f; setCardBackgroundColor(Color.WHITE)
            setContentPadding(ui.px(12), ui.px(12), ui.px(12), ui.px(12))
            addView(img, lado, lado)
        }
        root.addView(tarjeta, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))

        root.addView(ui.text("Que el otro móvil abra la cámara y apunte aquí.", 14f).apply {
            gravity = Gravity.CENTER; setPadding(0, ui.px(16), 0, ui.px(8))
        })

        val clave = d.clave
        if (!clave.isNullOrEmpty()) {
            val linea = ui.text("Contraseña: ••••••••", 18f).apply { gravity = Gravity.CENTER; setPadding(0, ui.px(8), 0, 0) }
            root.addView(linea)
            val fila = LinearLayout(this).apply { gravity = Gravity.CENTER }
            fila.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Ver"
                setOnClickListener {
                    verClave = !verClave
                    linea.text = if (verClave) "Contraseña: $clave" else "Contraseña: ••••••••"
                    text = if (verClave) "Ocultar" else "Ver"
                }
            })
            fila.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = "Copiar"
                setOnClickListener { Ui.copiar(this@QrActivity, "Contraseña wifi", clave) }
            })
            root.addView(fila)
        }

        root.addView(MaterialButton(this).apply {
            text = "Compartir como imagen"
            setOnClickListener { compartir() }
        }, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(16) })
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Cerrar"
            setOnClickListener { finish() }
        })
    }

    private fun compartir() {
        val d = Memoria.qr ?: return
        val id = Memoria.guardarImagen(Qr.png(Qr.paraCompartir(d)))
        val uri = Uri.parse("content://$packageName.qrwifi/$id.png")
        val envio = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("QR wifi", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(envio, "Compartir el QR de ${d.ssid}").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
