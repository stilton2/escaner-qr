package es.kalvo.escanerqr

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.nucleo.Codigos
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Crear
import es.kalvo.escanerqr.nucleo.Formato
import es.kalvo.escanerqr.nucleo.Revision
import es.kalvo.escanerqr.wifis.Formulario
import es.kalvo.escanerqr.wifis.nucleo.TextoQr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * «Crear» (S-J9): the form of one kind of code. «Crear código» checks the fields, builds the text ([Crear], [Codigos]) and
 * opens [CodigoActivity] with the picture. Going back from there returns here with the fields as they were.
 * No permissions: «Elegir de mis contactos» uses Android's own picker (only the chosen contact is lent to the app) and
 * «Ubicación» takes coordinates typed or pasted (no location permission).
 */
class CrearActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private lateinit var tipo: Crear.Tipo
    private lateinit var form: LinearLayout
    private val capas = HashMap<TextInputEditText, TextInputLayout>()
    private val es = Locale.forLanguageTag("es-ES")

    // Fields (only the ones of [tipo] exist).
    private var c1: TextInputEditText? = null
    private var c2: TextInputEditText? = null
    private var c3: TextInputEditText? = null
    private var c4: TextInputEditText? = null
    private var c5: TextInputEditText? = null
    private var c6: TextInputEditText? = null
    private var formWifi: Formulario? = null
    private var avisoPegar: TextView? = null
    private var pegado = false

    // Event
    private val inicio = Calendar.getInstance()
    private val fin = Calendar.getInstance()
    private var todoElDia: MaterialCheckBox? = null
    private var bInicioFecha: TextView? = null
    private var bInicioHora: TextView? = null
    private var bFinFecha: TextView? = null
    private var bFinHora: TextView? = null

    // Barcode
    private var formato = Formato.EAN13
    private var ayudaFormato: TextView? = null

    /** Android's contact picker, phone numbers only: the app gets read access to that one row, nothing else. */
    private val elegirContacto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@registerForActivityResult
        try {
            contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { c1?.setText(it) }
                    c.getString(1)?.let { c2?.setText(it) }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No he podido leer ese contacto", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tipo = Crear.Tipo.entries.getOrNull(intent.getIntExtra("tipo", -1)) ?: run { finish(); return }
        Ui.ventana(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ui = Ui(this, raiz)
        raiz.setBackgroundColor(ui.cFondo)
        val barra = barra(this, ui, "Crear: ${tipo.titulo.lowercase(es)}", tipo.ayuda)
        raiz.addView(barra, MATCH_PARENT, WRAP_CONTENT)
        form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(18), ui.px(18), ui.px(18), ui.px(28)) }
        val scroll = ScrollView(this).apply { addView(form, MATCH_PARENT, WRAP_CONTENT); clipToPadding = false }
        raiz.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val teclado = ins.getInsets(WindowInsetsCompat.Type.ime()).bottom
            barra.setPadding(ui.px(4) + b.left, b.top + ui.px(10), ui.px(4) + b.right, ui.px(10))
            scroll.setPadding(b.left, 0, b.right, maxOf(b.bottom, teclado))
            ins
        }

        val (icono, clase) = PanelCrear.icono(tipo)
        ui.cabecera(form, icono, clase, tipo.titulo, tipo.ayuda)
        when (tipo) {
            Crear.Tipo.TEXTO -> c1 = campo("Texto", TEXTO_LARGO, 4)
            Crear.Tipo.ENLACE -> c1 = campo("Dirección (por ejemplo ejemplo.es)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            Crear.Tipo.WIFI -> {
                val f = Formulario(this, null)
                f.vista.setPadding(0, 0, 0, 0)
                form.addView(f.vista, MATCH_PARENT, WRAP_CONTENT)
                formWifi = f
                nota("Quien lea el código se conecta sin escribir nada. La contraseña va dentro del código: compártelo solo con quien quieras.")
            }
            Crear.Tipo.TELEFONO -> c1 = campo("Número de teléfono", InputType.TYPE_CLASS_PHONE)
            Crear.Tipo.SMS -> {
                c1 = campo("Número de teléfono", InputType.TYPE_CLASS_PHONE)
                c2 = campo("Mensaje", TEXTO_LARGO, 3)
                nota("Al leerlo, se abre la app de mensajes con todo escrito; el mensaje solo se manda si la otra persona lo envía.")
            }
            Crear.Tipo.CORREO -> {
                c1 = campo("Para (correo)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                c2 = campo("Asunto", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                c3 = campo("Mensaje", TEXTO_LARGO, 3)
            }
            Crear.Tipo.CONTACTO -> {
                form.addView(ui.accion("Elegir de mis contactos", R.drawable.ic_contacto) { elegirDeContactos() })
                nota("Solo se lee el contacto que elijas (nombre y teléfono). La app no tiene permiso para ver tu agenda.")
                c1 = campo("Nombre", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                c2 = campo("Teléfono", InputType.TYPE_CLASS_PHONE)
                c3 = campo("Correo", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                c4 = campo("Empresa", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                c5 = campo("Dirección", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_POSTAL_ADDRESS or InputType.TYPE_TEXT_FLAG_CAP_WORDS)
                c6 = campo("Web", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
            }
            Crear.Tipo.UBICACION -> {
                form.addView(ui.accion("Pegar coordenadas copiadas", R.drawable.ic_portapapeles) { pegarCoordenadas() })
                nota("En Google Maps, mantén pulsado el sitio: arriba salen sus coordenadas (40.41, -3.70). Tócalas para copiarlas y " +
                    "vuelve aquí a «Pegar». También vale pegar un enlace de Maps que lleve los números. La app no usa tu ubicación.")
                val numero = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                c1 = campo("Latitud (por ejemplo 40.4168)", numero)
                c2 = campo("Longitud (por ejemplo -3.7038)", numero)
                form.addView(ui.accion("Comprobar en el mapa", R.drawable.ic_mapa) { comprobarMapa() })
            }
            Crear.Tipo.EVENTO -> evento()
            Crear.Tipo.PORTAPAPELES -> {
                c1 = campo("Texto copiado", TEXTO_LARGO, 4)
                avisoPegar = ui.texto("", 14f, ui.cMuted).apply { visibility = View.GONE; setPadding(ui.px(2), 0, ui.px(2), ui.px(8)) }
                form.addView(avisoPegar)
                form.addView(ui.accion("Pegar otra vez", R.drawable.ic_portapapeles) { pegar(siempre = true) })
            }
            Crear.Tipo.BARRAS -> barras()
        }
        form.addView(ui.botonPrincipal("Crear código") { crear() }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { topMargin = ui.px(12) }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Android 10+ only lets the app read the clipboard while its window has the focus.
        if (hasFocus && tipo == Crear.Tipo.PORTAPAPELES && !pegado) { pegado = true; pegar(siempre = false) }
    }

    // ---------- building blocks ----------

    private fun campo(hint: String, tipoEntrada: Int, lineas: Int = 1, ayuda: String? = null): TextInputEditText {
        val capa = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            this.hint = hint
            if (ayuda != null) helperText = ayuda
        }
        val e = TextInputEditText(capa.context).apply {
            inputType = tipoEntrada
            if (lineas > 1) { isSingleLine = false; minLines = lineas; maxLines = 10; gravity = Gravity.TOP or Gravity.START }
            else isSingleLine = true
        }
        capa.addView(e)
        form.addView(capa, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(10) })
        capas[e] = capa
        return e
    }

    private fun nota(s: String) = form.addView(ui.texto(s, 13f, ui.cMuted).apply { setPadding(ui.px(2), ui.px(2), ui.px(2), ui.px(12)) })

    private fun v(e: TextInputEditText?) = e?.text?.toString().orEmpty()

    /** Shows [error] under the field (or clears it). Returns true when there is an error. */
    private fun marcar(e: TextInputEditText?, error: String?): Boolean {
        val capa = e?.let { capas[it] } ?: return error != null
        capa.error = error
        if (error != null) e.requestFocus()
        return error != null
    }

    // ---------- contacto, ubicación, portapapeles ----------

    private fun elegirDeContactos() {
        try {
            elegirContacto.launch(Intent(Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay app de contactos para elegir", Toast.LENGTH_LONG).show()
        }
    }

    private fun portapapeles(): String? = try {
        getSystemService(ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
    } catch (e: Exception) { null }

    private fun pegarCoordenadas() {
        val t = portapapeles()
        val par = t?.let { Crear.coordenadas(it) }
        if (par == null) {
            Toast.makeText(this, if (t.isNullOrBlank()) "No hay nada copiado" else "Lo copiado no lleva coordenadas (dos números con punto, como 40.41, -3.70)",
                Toast.LENGTH_LONG).show()
            return
        }
        c1?.setText(par.first.toString()); c2?.setText(par.second.toString())
    }

    private fun comprobarMapa() {
        val lat = Crear.leerNumero(v(c1)); val lon = Crear.leerNumero(v(c2))
        if (marcar(c1, Crear.problemaLatitud(v(c1))) or marcar(c2, Crear.problemaLongitud(v(c2))) || lat == null || lon == null) return
        Acciones.mapa(this, Contenido.Geo(lat, lon, ""))
    }

    private fun pegar(siempre: Boolean) {
        val t = portapapeles()
        val aviso = avisoPegar ?: return
        if (t.isNullOrEmpty()) {
            aviso.text = "No hay nada copiado (o Android no deja leerlo ahora). Copia algo en otra app y toca «Pegar otra vez», o escríbelo."
            aviso.visibility = View.VISIBLE
            return
        }
        aviso.visibility = View.GONE
        if (siempre || v(c1).isEmpty()) c1?.setText(t)
    }

    // ---------- evento ----------

    private fun evento() {
        c1 = campo("Título", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        c2 = campo("Lugar (si quieres)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        // Starts at the next full hour, one hour long.
        inicio.apply { add(Calendar.HOUR_OF_DAY, 1); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        fin.timeInMillis = inicio.timeInMillis + 3_600_000
        todoElDia = MaterialCheckBox(this).apply {
            text = "Todo el día"
            setOnCheckedChangeListener { _, _ -> pintarFechas() }
        }
        form.addView(todoElDia)
        val (f1, h1) = filaFecha("Empieza", inicio)
        val (f2, h2) = filaFecha("Termina", fin)
        bInicioFecha = f1; bInicioHora = h1; bFinFecha = f2; bFinHora = h2
        pintarFechas()
    }

    private fun filaFecha(etiqueta: String, cal: Calendar): Pair<TextView, TextView> {
        form.addView(ui.etiqueta(etiqueta).apply { setPadding(ui.px(2), ui.px(8), 0, ui.px(4)) })
        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
        val fecha = ui.botonSecundario("", R.drawable.ic_calendario) {
            DatePickerDialog(this, { _, y, m, d ->
                cal.set(Calendar.YEAR, y); cal.set(Calendar.MONTH, m); cal.set(Calendar.DAY_OF_MONTH, d)
                cuadrar(cal); pintarFechas()
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
        val hora = ui.botonSecundario("") {
            TimePickerDialog(this, { _, h, min ->
                cal.set(Calendar.HOUR_OF_DAY, h); cal.set(Calendar.MINUTE, min)
                cuadrar(cal); pintarFechas()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
        fila.addView(fecha, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1.6f).apply { marginEnd = ui.px(10) })
        fila.addView(hora, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        form.addView(fila, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(6) })
        return fecha to hora
    }

    /** Moving the start past the end pushes the end (same length); moving the end before the start is checked on «Crear». */
    private fun cuadrar(cambiado: Calendar) {
        if (cambiado === inicio && fin.before(inicio)) fin.timeInMillis = inicio.timeInMillis + 3_600_000
    }

    private fun pintarFechas() {
        val dia = SimpleDateFormat("EEE d MMM yyyy", es)
        val hora = SimpleDateFormat("HH:mm", es)
        val todo = todoElDia?.isChecked == true
        bInicioFecha?.text = dia.format(inicio.time); bFinFecha?.text = dia.format(fin.time)
        bInicioHora?.text = hora.format(inicio.time); bFinHora?.text = hora.format(fin.time)
        bInicioHora?.visibility = if (todo) View.INVISIBLE else View.VISIBLE
        bFinHora?.visibility = if (todo) View.INVISIBLE else View.VISIBLE
    }

    // ---------- código de barras ----------

    private fun barras() {
        form.addView(ui.etiqueta("Formato").apply { setPadding(ui.px(2), 0, 0, ui.px(4)) })
        val grupo = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        for ((i, f) in Formato.barras.withIndex()) grupo.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            id = 600 + i; text = f.nombre; isCheckable = true; isChecked = f == formato
        })
        grupo.setOnCheckedStateChangeListener { _, ids -> Formato.barras.getOrNull((ids.firstOrNull() ?: 600) - 600)?.let { formato = it; pintarFormato() } }
        form.addView(grupo, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(6) })
        ayudaFormato = ui.texto("", 14f, ui.cMuted).apply { setPadding(ui.px(2), 0, ui.px(2), ui.px(12)) }
        form.addView(ayudaFormato)
        c1 = campo("Contenido", InputType.TYPE_CLASS_NUMBER)
        pintarFormato()
    }

    private fun pintarFormato() {
        ayudaFormato?.text = formato.ayuda
        val e = c1 ?: return
        val nuevo = if (formato.soloCifras) InputType.TYPE_CLASS_NUMBER
            else if (formato.matriz) TEXTO_LARGO else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        if (e.inputType != nuevo) {
            e.inputType = nuevo
            if (formato.matriz) { e.isSingleLine = false; e.minLines = 3; e.maxLines = 10 } else { e.isSingleLine = true; e.minLines = 1 }
        }
        capas[e]?.error = null
    }

    // ---------- «Crear código» ----------

    private fun crear() {
        capas.values.forEach { it.error = null }
        var f = Formato.QR
        var nota: String? = null
        val texto: String = when (tipo) {
            Crear.Tipo.TEXTO, Crear.Tipo.PORTAPAPELES -> {
                if (marcar(c1, if (v(c1).isEmpty()) "Escribe el texto" else null)) return
                v(c1)
            }
            Crear.Tipo.ENLACE -> {
                if (marcar(c1, Crear.problemaEnlace(v(c1)))) return
                Crear.enlace(v(c1))
            }
            Crear.Tipo.WIFI -> {
                val d = formWifi?.leer() ?: return
                TextoQr.crear(d)
            }
            Crear.Tipo.TELEFONO -> {
                if (marcar(c1, Crear.problemaNumero(v(c1)))) return
                Crear.telefono(v(c1))
            }
            Crear.Tipo.SMS -> {
                if (marcar(c1, Crear.problemaNumero(v(c1)))) return
                Crear.sms(v(c1), v(c2))
            }
            Crear.Tipo.CORREO -> {
                if (marcar(c1, Crear.problemaCorreo(v(c1)))) return
                Crear.correo(v(c1), v(c2), v(c3))
            }
            Crear.Tipo.CONTACTO -> {
                val ficha = Crear.Ficha(v(c1), v(c2), v(c3), v(c4), v(c5), v(c6))
                if (marcar(c1, Crear.problemaContacto(ficha))) return
                if (ficha.correo.isNotBlank() && marcar(c3, Crear.problemaCorreo(ficha.correo))) return
                Crear.contacto(ficha)
            }
            Crear.Tipo.UBICACION -> {
                if (marcar(c1, Crear.problemaLatitud(v(c1))) or marcar(c2, Crear.problemaLongitud(v(c2)))) return
                Crear.ubicacion(Crear.leerNumero(v(c1))!!, Crear.leerNumero(v(c2))!!)
            }
            Crear.Tipo.EVENTO -> {
                val todo = todoElDia?.isChecked == true
                val problema = Crear.problemaEvento(v(c1), inicio.timeInMillis, fin.timeInMillis, todo)
                if (problema != null) {
                    if (v(c1).isBlank()) marcar(c1, problema) else Toast.makeText(this, problema, Toast.LENGTH_LONG).show()
                    return
                }
                Crear.evento(v(c1), inicio.timeInMillis, fin.timeInMillis, v(c2), todo)
            }
            Crear.Tipo.BARRAS -> {
                f = formato
                when (val r = Codigos.revisar(f, v(c1))) {
                    is Revision.Mal -> { marcar(c1, r.motivo); return }
                    is Revision.Bien -> { nota = r.nota; r.texto }
                }
            }
        }
        // Too long for this kind of code? Better to say it here, next to the fields.
        try { Codigos.matriz(f, texto) } catch (e: Exception) {
            val msg = "Es demasiado largo (o no vale) para un código ${f.nombre}. Acórtalo."
            if (c1 != null && tipo != Crear.Tipo.CONTACTO && tipo != Crear.Tipo.EVENTO) marcar(c1, msg) else Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            return
        }
        CodigoActivity.abrir(this, texto, f.nombre, guardar = true, tipo = tipo.titulo, nota = nota)
    }

    companion object {
        private const val TEXTO_LARGO = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

        fun abrir(ctx: Context, t: Crear.Tipo) = ctx.startActivity(Intent(ctx, CrearActivity::class.java).putExtra("tipo", t.ordinal))

        /** Teal bar with «back», a title and a subtitle (same look as the result screen). */
        fun barra(act: AppCompatActivity, ui: Ui, titulo: String, sub: String): LinearLayout {
            val blanco = ui.c(R.color.barra_activa)
            val barra = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setBackgroundColor(ui.cBarra)
            }
            barra.addView(ui.botonIcono(R.drawable.ic_atras, "Atrás", blanco) { act.finish() })
            val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(4), 0, ui.px(8), 0) }
            col.addView(ui.texto(titulo, 20f, blanco, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (sub.isNotEmpty()) col.addView(ui.texto(sub, 13f, ui.c(R.color.barra_texto)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
            barra.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            return barra
        }
    }
}
