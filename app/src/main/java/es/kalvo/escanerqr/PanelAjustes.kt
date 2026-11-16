package es.kalvo.escanerqr

import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/** «Ajustes»: the QR Bot settings that make sense without internet. */
class PanelAjustes(private val act: MainActivity) {
    private val aj = Ajustes(act)
    private val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val scroll = ScrollView(act).apply { addView(col, MATCH_PARENT, WRAP_CONTENT) }
    private val ui = Ui(act, scroll)
    val vista: View get() = scroll

    init { scroll.setBackgroundColor(ui.cFondo); scroll.clipToPadding = false }
    private var top = 0

    fun margenArriba(t: Int) { top = t; col.setPadding(ui.px(16), top + ui.px(8), ui.px(16), ui.px(24)) }

    fun refrescar() {
        col.removeAllViews()
        col.setPadding(ui.px(16), top + ui.px(4), ui.px(16), ui.px(24))

        col.addView(ui.titulo("Al leer un código"))
        ui.tarjeta(col) {
            interruptor(this, "Copiar al portapapeles", "Lo leído queda copiado para pegarlo donde quieras. De una SIM, el número entero; de una etiqueta de artículo, el nº de serie", aj.copiar) { aj.copiar = it }
            interruptor(this, "Copiar directamente la MAC cuando la haya",
                "Si lo leído lleva una dirección MAC (etiquetas de cámaras, routers, grabadores, centrales), se copia solo la MAC, con «:» y en mayúsculas", aj.copiarMac) { aj.copiarMac = it }
            interruptor(this, "Vibrar", null, aj.vibrar) { aj.vibrar = it }
            interruptor(this, "Pitido", null, aj.pitido) { aj.pitido = it }
            interruptor(this, "Abrir enlaces solos", "Si es una web, va directo al navegador sin enseñar el resultado", aj.abrirEnlaces) { aj.abrirEnlaces = it }
            interruptor(this, "Lectura continua", "Solo guarda en el historial y sigue leyendo, sin abrir el resultado", aj.continua) { aj.continua = it }
            interruptor(this, "Confirmar a mano", "Te enseña lo encontrado y tú decides si abrirlo (evita lecturas sin querer)", aj.manual) { aj.manual = it }
        }

        col.addView(ui.titulo("Historial"))
        ui.tarjeta(col) {
            interruptor(this, "Guardar en el historial", null, aj.guardar) { aj.guardar = it }
            interruptor(this, "Guardar repetidos", "Apagado: si lees uno que ya estaba, se sube arriba en vez de duplicarse", aj.repetidos) { aj.repetidos = it }
        }

        col.addView(ui.titulo("Cámara"))
        ui.tarjeta(col) {
            interruptor(this, "Usar la cámara de delante", null, aj.frontal) { aj.frontal = it }
            interruptor(this, "Encender la linterna al abrir", null, aj.linternaAlAbrir) { aj.linternaAlAbrir = it }
        }

        col.addView(ui.titulo("Buscar en la web"))
        ui.tarjeta(col) {
            addView(ui.texto("Dirección del buscador. {code} se cambia por lo leído. Se abre en tu navegador; esta app no usa internet.", 13f, ui.cMuted))
            val e = TextInputEditText(context).apply { setText(aj.buscador); isSingleLine = true }
            val caja = TextInputLayout(context, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { addView(e) }
            addView(caja, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.px(8) })
            val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            fila.addView(ui.boton("Guardar") {
                val v = e.text?.toString()?.trim() ?: ""
                if (!v.contains("{code}") || !(v.startsWith("https://") || v.startsWith("http://"))) {
                    Toast.makeText(act, "Tiene que empezar por https:// y llevar {code}", Toast.LENGTH_LONG).show()
                } else { aj.buscador = v; Toast.makeText(act, "Guardado", Toast.LENGTH_SHORT).show() }
            })
            fila.addView(ui.boton("Volver a Google", contorno = true) { aj.buscador = Ajustes.BUSCADOR_DEFECTO; e.setText(aj.buscador) }
                .apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.px(8) } })
            addView(fila)
        }

        col.addView(ui.titulo("Aspecto"))
        ui.tarjeta(col) {
            val nombres = arrayOf("Como el móvil", "Claro", "Oscuro")
            addView(ui.boton("Tema: ${nombres[aj.tema]}", contorno = true) {
                MaterialAlertDialogBuilder(act).setTitle("Tema")
                    .setSingleChoiceItems(nombres, aj.tema) { d, i -> d.dismiss(); aj.tema = i }
                    .show()
            })
        }

        col.addView(ui.titulo("Acerca de"))
        ui.tarjeta(col) {
            val version = act.packageManager.getPackageInfo(act.packageName, 0).versionName
            addView(ui.texto("Escáner QR $version", 16f, bold = true))
            addView(ui.texto("App propia, sin anuncios, sin rastreadores y sin permiso de internet: lo que lees, tu historial y tus wifis no salen del móvil. " +
                "Pide la cámara y, para la pestaña Wifis y el botón «Conectar», Shizuku.", 14f, ui.cMuted))
            addView(ui.texto("\nPiezas de código abierto que usa:\n" +
                "· zxing-cpp (lector de códigos) — Apache 2.0\n" +
                "· ZXing core (dibujar el QR) — Apache 2.0\n" +
                "· AndroidX CameraX, RecyclerView y Material Components — Apache 2.0\n" +
                "· Shizuku API — MIT\n" +
                "· HiddenApiBypass — Apache 2.0", 13f, ui.cMuted))
        }
    }

    private fun interruptor(padre: LinearLayout, titulo: String, sub: String?, valor: Boolean, cambia: (Boolean) -> Unit) {
        val fila = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val textos = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        textos.addView(ui.texto(titulo, 16f))
        if (sub != null) textos.addView(ui.texto(sub, 13f, ui.cMuted))
        fila.addView(textos, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        val s = MaterialSwitch(act).apply { isChecked = valor; setOnCheckedChangeListener { _, c -> cambia(c) } }
        fila.addView(s)
        fila.setOnClickListener { s.toggle() }
        fila.setPadding(0, ui.px(8), 0, ui.px(8))
        padre.addView(fila, MATCH_PARENT, WRAP_CONTENT)
    }

}
