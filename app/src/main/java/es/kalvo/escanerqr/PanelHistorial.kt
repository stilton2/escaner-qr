package es.kalvo.escanerqr

import android.net.Uri
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Especial
import es.kalvo.escanerqr.nucleo.Historial
import es.kalvo.escanerqr.nucleo.Importar
import es.kalvo.escanerqr.nucleo.Lectura
import es.kalvo.escanerqr.nucleo.Resumenes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Historial»: grouped by day, search, favourites, notes, delete, export CSV/JSON/text and import other apps' history (S-J5).
 * «Estilo A» (S-J4): bar with the count, Exportar, Importar and search; amber day headers; 44 dp icon squares; amber FAB.
 */
class PanelHistorial(private val act: MainActivity) {
    private val db = Db.de(act)
    private val raiz = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
    private val ui = Ui(act, raiz)
    val vista: View get() = raiz

    private var todas: List<Lectura> = emptyList()
    private var busca = ""
    private var soloFavoritos = false
    /** 0 all, 1 only scanned here, 2 only imported (S-J5), 3 only made in «Crear» (S-J9). */
    private var filtroOrigen = 0
    private var chipsOrigen: com.google.android.material.chip.ChipGroup? = null
    private val adaptador = Adaptador()
    private val vacio: TextView
    private val cabecera: LinearLayout
    private val cuenta: TextView
    private val cajaBuscar: LinearLayout
    private val campo: TextInputEditText
    private var chipFavoritos: Chip? = null

    init {
        raiz.setBackgroundColor(ui.cFondo)
        cabecera = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ui.cSuperficie)
            setPadding(ui.px(18), ui.px(4), ui.px(4), ui.px(4))
        }
        cuenta = ui.texto("", 15f, ui.c(R.color.texto_cuenta)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        cabecera.addView(cuenta, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        cabecera.addView(botonBarra("Exportar") { menuExportar(it) })
        cabecera.addView(botonBarra("Importar") { importar() })
        cabecera.addView(ui.botonIcono(R.drawable.ic_buscar, "Buscar", ui.cText) { alternarBuscar() })
        cabecera.addView(ui.botonIcono(R.drawable.ic_mas, "Más: solo favoritos, exportar a texto, borrar", ui.cText) { menuGeneral(cabecera.getChildAt(cabecera.childCount - 1)) })
        raiz.addView(cabecera, MATCH_PARENT, WRAP_CONTENT)

        campo = TextInputEditText(act).apply {
            isSingleLine = true
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { busca = s?.toString() ?: ""; filtrar() }
            })
        }
        val caja = TextInputLayout(act, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Buscar en textos y notas"
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
            addView(campo)
        }
        // The search box and the favourites filter only show after tapping the magnifier (or with a filter on).
        cajaBuscar = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE; setPadding(ui.px(16), ui.px(8), ui.px(16), 0) }
        cajaBuscar.addView(caja, MATCH_PARENT, WRAP_CONTENT)
        val chip = Chip(act, null, com.google.android.material.R.attr.chipStyle).apply {
            text = "Solo favoritos"; isCheckable = true
            setOnCheckedChangeListener { _, c -> soloFavoritos = c; filtrar() }
        }
        chipFavoritos = chip
        // «Escaneadas» / «Importadas» (S-J5) / «Creados» (S-J9): only shown when something was imported or made here.
        val grupo = com.google.android.material.chip.ChipGroup(act)
        grupo.addView(chip)
        listOf("Escaneadas", "Importadas", "Creados").forEachIndexed { i, t ->
            grupo.addView(Chip(act, null, com.google.android.material.R.attr.chipStyle).apply {
                id = 400 + i; text = t; isCheckable = true
                setOnCheckedChangeListener { _, c ->
                    if (c) { filtroOrigen = i + 1; for (j in 0 until 3) if (j != i) grupo.findViewById<Chip>(400 + j)?.isChecked = false }
                    else if (filtroOrigen == i + 1) filtroOrigen = 0
                    filtrar()
                }
            })
        }
        chipsOrigen = grupo
        cajaBuscar.addView(grupo, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(4) })
        raiz.addView(cajaBuscar, MATCH_PARENT, WRAP_CONTENT)

        val marco = FrameLayout(act)
        val lista = RecyclerView(act).apply {
            layoutManager = LinearLayoutManager(act)
            adapter = adaptador
            clipToPadding = false
            setPadding(0, 0, 0, ui.px(104))  // room for the Escanear button
        }
        marco.addView(lista, MATCH_PARENT, MATCH_PARENT)
        vacio = ui.texto("Lo que leas saldrá aquí, agrupado por días.", 15f, ui.cMuted).apply {
            gravity = Gravity.CENTER; setPadding(ui.px(32), ui.px(48), ui.px(32), 0)
        }
        marco.addView(vacio, MATCH_PARENT, WRAP_CONTENT)
        // Amber «Escanear» button (64 dp, 20 dp corners), bottom right.
        val fab = MaterialButton(act).apply {
            setIconResource(R.drawable.ic_escanear); iconSize = ui.px(30)
            iconTint = android.content.res.ColorStateList.valueOf(ui.cSobreAmbar)
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START; iconPadding = 0
            backgroundTintList = android.content.res.ColorStateList.valueOf(ui.cAmbar)
            cornerRadius = ui.px(20); insetTop = 0; insetBottom = 0
            setPadding(0, 0, 0, 0)
            contentDescription = "Escanear"
            elevation = ui.px(4).toFloat()
            setOnClickListener { act.irAEscanear() }
        }
        marco.addView(fab, FrameLayout.LayoutParams(ui.px(64), ui.px(64), Gravity.BOTTOM or Gravity.END).apply {
            setMargins(0, 0, ui.px(20), ui.px(24))
        })
        raiz.addView(marco, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
    }

    private fun botonBarra(texto: String, accion: (View) -> Unit) =
        MaterialButton(act, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = texto; isAllCaps = false; textSize = 15f
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ui.cText)
            minWidth = 0; minimumWidth = 0; minHeight = ui.px(48)
            setPadding(ui.px(8), 0, ui.px(8), 0)
            setOnClickListener { accion(this) }
        }

    private fun alternarBuscar() {
        val abrir = cajaBuscar.visibility != View.VISIBLE
        if (!abrir && (busca.isNotEmpty() || soloFavoritos || filtroOrigen != 0)) {
            campo.setText(""); chipFavoritos?.isChecked = false
            chipsOrigen?.let { g -> for (i in 0 until g.childCount) (g.getChildAt(i) as? Chip)?.isChecked = false }
        }
        cajaBuscar.visibility = if (abrir) View.VISIBLE else View.GONE
        if (abrir) {
            campo.requestFocus()
            act.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.showSoftInput(campo, 0)
        } else act.getSystemService(android.view.inputmethod.InputMethodManager::class.java)?.hideSoftInputFromWindow(campo.windowToken, 0)
    }

    /** CSV, JSON or TXT from any app (S-J5). Many file managers call a CSV «vnd.ms-excel» or «octet-stream». */
    private fun importar() =
        act.abrirCsv.launch(arrayOf("text/*", "application/json", "application/csv", "text/comma-separated-values",
            "application/vnd.ms-excel", "application/octet-stream"))

    private fun menuExportar(ancla: View) {
        val m = PopupMenu(act, ancla)
        m.menu.add(0, 1, 0, "CSV (el mismo formato que QR Bot)")
        m.menu.add(0, 2, 1, "JSON (copia completa, con el origen)")
        m.menu.add(0, 3, 2, "Texto")
        m.setOnMenuItemClickListener { exportarComo(it.itemId); true }
        m.show()
    }

    private fun exportarComo(tipo: Int) {
        val nombre = "codes_" + SimpleDateFormat("yyyyMMddHHmmss", Locale.ROOT).format(Date())
        when (tipo) {
            1 -> act.crearCsv.launch("$nombre.csv")
            2 -> act.crearJson.launch("$nombre.json")
            else -> act.crearTxt.launch("$nombre.txt")
        }
    }

    fun margenArriba(@Suppress("UNUSED_PARAMETER") top: Int) {}

    fun refrescar() {
        Thread {
            val l = db.todas()
            act.runOnUiThread { todas = l; filtrar() }
        }.start()
    }

    private fun filtrar() {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date())
        val ayer = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(System.currentTimeMillis() - 86_400_000))
        val hayImportadas = todas.any { it.importada }
        val hayCreadas = todas.any { it.creada }
        chipsOrigen?.let { g ->
            g.findViewById<Chip>(400)?.visibility = if (hayImportadas || hayCreadas) View.VISIBLE else View.GONE
            g.findViewById<Chip>(401)?.visibility = if (hayImportadas) View.VISIBLE else View.GONE
            g.findViewById<Chip>(402)?.visibility = if (hayCreadas) View.VISIBLE else View.GONE
        }
        val vistas = todas.filter {
            (!soloFavoritos || it.favorito) && Historial.coincide(it, busca) &&
                when (filtroOrigen) { 1 -> it.origen.isEmpty(); 2 -> it.importada; 3 -> it.creada; else -> true }
        }
        val nf = java.text.NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-ES"))
        val n = nf.format(todas.size)
        cuenta.text = when {
            todas.size == 1 -> "1 lectura"
            vistas.size != todas.size -> "${nf.format(vistas.size)} de $n"
            else -> "$n lecturas"
        }
        val filas = ArrayList<Fila>()
        var dia: String? = null
        for (l in vistas) {
            if (l.fecha != dia) { dia = l.fecha; filas.add(Fila.Dia(Historial.cabeceraDia(l.fecha, hoy, ayer))) }
            filas.add(Fila.Una(l))
        }
        adaptador.filas = filas
        adaptador.notifyDataSetChanged()
        vacio.visibility = if (filas.isEmpty()) View.VISIBLE else View.GONE
        vacio.text = when {
            todas.isEmpty() -> "Lo que leas saldrá aquí, agrupado por días.\n\n¿Vienes de otra app de escanear? Exporta allí el historial " +
                "y toca «Importar» arriba (vale CSV, JSON o TXT)."
            else -> "Nada coincide con lo que buscas."
        }
    }

    // ---------------- menú general ----------------

    private fun menuGeneral(ancla: View) {
        val m = PopupMenu(act, ancla)
        m.menu.add(0, 1, 0, if (soloFavoritos) "Ver todas (no solo favoritos)" else "Solo favoritos")
        m.menu.add(0, 2, 1, "Exportar a CSV")
        m.menu.add(0, 6, 2, "Exportar a JSON")
        m.menu.add(0, 3, 3, "Exportar a texto")
        m.menu.add(0, 4, 4, "Importar historial (CSV, JSON o TXT)")
        m.menu.add(0, 5, 5, "Borrar todo el historial")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { cajaBuscar.visibility = View.VISIBLE; chipFavoritos?.isChecked = !soloFavoritos }
                2 -> exportarComo(1)
                6 -> exportarComo(2)
                3 -> exportarComo(3)
                4 -> importar()
                5 -> borrarTodo()
            }
            true
        }
        m.show()
    }

    /** [tipo]: 1 CSV, 2 JSON, 3 text. */
    fun exportarA(uri: Uri, tipo: Int) {
        Thread {
            val r = runCatching {
                val l = db.todas()
                val texto = when (tipo) { 1 -> Historial.exportar(l); 2 -> Historial.exportarJson(l); else -> Historial.exportarTexto(l) }
                act.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(texto.toByteArray(Charsets.UTF_8)) }
                l.size
            }
            act.runOnUiThread {
                r.onSuccess { Toast.makeText(act, "Exportadas $it lecturas.", Toast.LENGTH_LONG).show() }
                    .onFailure { Toast.makeText(act, "No he podido guardar el fichero: ${it.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }

    fun importarDe(uri: Uri) {
        Toast.makeText(act, "Leyendo el fichero…", Toast.LENGTH_SHORT).show()
        Thread {
            val r = runCatching {
                val nombre = nombreDe(uri)
                val bytes = act.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                Importar.analizar(bytes, nombre)
            }
            act.runOnUiThread {
                r.onFailure { Toast.makeText(act, "No he podido leer el fichero: ${it.message}", Toast.LENGTH_LONG).show() }
                    .onSuccess { res ->
                        when (res) {
                            is Importar.Resultado.Hecho -> guardar(res.listo)
                            is Importar.Resultado.Elegir -> ImportarColumnas.preguntar(act, res) { guardar(it) }
                            is Importar.Resultado.NoVale -> MaterialAlertDialogBuilder(act).setTitle("No he podido importar")
                                .setMessage(res.motivo + "\n\nVale el historial exportado por otra app de escanear: CSV, JSON o un TXT con una lectura por línea.")
                                .setPositiveButton("Vale", null).show()
                        }
                    }
            }
        }.start()
    }

    /** Name of the picked file ("historial.csv"), for «importado de …» when the app that made it is not known. */
    private fun nombreDe(uri: Uri): String = try {
        act.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
    } catch (e: Exception) { uri.lastPathSegment.orEmpty() }

    /** Adds what was read, without repeating what was already there, and says how it went. */
    private fun guardar(listo: Importar.Listo) {
        Thread {
            val r = runCatching {
                val c = Historial.sinRepetir(listo.lecturas, db.claves(), listo.sinFecha, if (listo.sinFecha) db.clavesSinHora() else emptySet())
                val entraron = db.insertarVarias(c.nuevas)
                entraron to c.yaEstaban + (c.nuevas.size - entraron)
            }
            act.runOnUiThread {
                r.onFailure { Toast.makeText(act, "No he podido guardar lo importado: ${it.message}", Toast.LENGTH_LONG).show() }
                    .onSuccess { (n, m) ->
                        val fallos = listo.fallos
                        val sb = StringBuilder()
                        sb.append("Leído como: ${listo.tipo}")
                        if (listo.codificacion != "UTF-8") sb.append(" (letras en ${listo.codificacion})")
                        sb.append("\n\nImportadas $n, ya estaban $m, con error ${fallos.size}.")
                        if (listo.sinFecha && n > 0) {
                            val ahora = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.ROOT).format(Date())
                            sb.append("\n\nEl fichero no dice cuándo se leyó cada código: las he guardado con la hora de ahora ($ahora).")
                        }
                        if (fallos.isNotEmpty()) {
                            sb.append("\n\nLas que fallaron:")
                            for (f in fallos.take(40)) sb.append("\n· línea ${f.linea}: ${f.motivo} — ${f.muestra}")
                            if (fallos.size > 40) sb.append("\n… y ${fallos.size - 40} más.")
                        }
                        MaterialAlertDialogBuilder(act).setTitle("Importar historial").setMessage(sb)
                            .setPositiveButton("Vale", null).show()
                        refrescar()
                    }
            }
        }.start()
    }

    private fun borrarTodo() {
        val caja = CheckBox(act).apply { text = "Borrar también los favoritos" }
        MaterialAlertDialogBuilder(act)
            .setTitle("¿Borrar todo el historial?")
            .setMessage("No se puede deshacer. Si quieres, exporta antes a CSV.")
            .setView(FrameLayout(act).apply { setPadding(ui.px(20), 0, ui.px(20), 0); addView(caja) })
            .setPositiveButton("Borrar") { _, _ ->
                val n = db.borrarTodo(caja.isChecked)
                Toast.makeText(act, "Borradas $n lecturas.", Toast.LENGTH_SHORT).show()
                refrescar()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // ---------------- una lectura ----------------

    private fun menuLectura(ancla: View, l: Lectura) {
        val m = PopupMenu(act, ancla)
        m.menu.add(0, 1, 0, if (l.favorito) "Quitar de favoritos" else "Añadir a favoritos")
        m.menu.add(0, 2, 1, "Notas")
        m.menu.add(0, 3, 2, "Copiar")
        m.menu.add(0, 4, 3, "Compartir")
        m.menu.add(0, 5, 4, "Borrar")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> { db.ponerFavorito(l.id, !l.favorito); refrescar() }
                2 -> ResultadoActivity.editarNotas(act, l.notas) { n -> db.ponerNotas(l.id, n); refrescar() }
                3 -> Acciones.copiar(act, l.texto, sensible = Contenido.analizar(l.texto, l.formato) is Contenido.Wifi)
                4 -> Acciones.compartir(act, l.texto)
                5 -> MaterialAlertDialogBuilder(act).setTitle("¿Borrar esta lectura?").setMessage(l.texto.take(120))
                    .setPositiveButton("Borrar") { _, _ -> db.borrar(listOf(l.id)); refrescar() }
                    .setNegativeButton("Cancelar", null).show()
            }
            true
        }
        m.show()
    }

    private sealed class Fila {
        data class Dia(val titulo: String) : Fila()
        data class Una(val l: Lectura) : Fila()
    }

    private inner class Adaptador : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var filas: List<Fila> = emptyList()
        override fun getItemCount() = filas.size
        override fun getItemViewType(position: Int) = if (filas[position] is Fila.Dia) 0 else 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            if (viewType == 0) object : RecyclerView.ViewHolder(ui.texto("", 13f, ui.cPrimary, bold = true).apply {
                letterSpacing = 0.04f
                layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT); setPadding(ui.px(18), ui.px(14), ui.px(18), ui.px(6))
                isFocusable = true
                accessibilityDelegate = object : View.AccessibilityDelegate() {
                    override fun onInitializeAccessibilityNodeInfo(host: View, info: android.view.accessibility.AccessibilityNodeInfo) {
                        super.onInitializeAccessibilityNodeInfo(host, info); info.isHeading = true
                    }
                }
            }) {}
            else ItemHolder(ItemVista())

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            when (val f = filas[position]) {
                is Fila.Dia -> (h.itemView as TextView).text = f.titulo
                is Fila.Una -> (h as ItemHolder).poner(f.l)
            }
        }
    }

    private inner class ItemVista : LinearLayout(act) {
        val marco = FrameLayout(act)
        val texto = ui.texto("", 18f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val detalle = ui.texto("", 13f, ui.cMuted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        val estrella = ImageView(act).apply { setImageResource(R.drawable.ic_estrella_llena); imageTintList = android.content.res.ColorStateList.valueOf(ui.cAmbar) }

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.px(18), ui.px(12), ui.px(18), ui.px(12))
            minimumHeight = ui.px(68)
            layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
            val fondo = android.util.TypedValue()
            act.theme.resolveAttribute(android.R.attr.selectableItemBackground, fondo, true)
            setBackgroundResource(fondo.resourceId)
            addView(marco, ui.px(44), ui.px(44))
            val col = LinearLayout(act).apply { orientation = VERTICAL; setPadding(ui.px(16), 0, ui.px(8), 0) }
            col.addView(texto)
            col.addView(detalle, LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(3) })
            addView(col, LayoutParams(0, WRAP_CONTENT, 1f))
            addView(estrella, ui.px(18), ui.px(18))
        }
    }

    private inner class ItemHolder(val v: ItemVista) : RecyclerView.ViewHolder(v) {
        fun poner(l: Lectura) {
            val c = Contenido.analizar(l.texto, l.formato)
            val r = Resumenes.de(l.texto, l.formato)
            v.marco.removeAllViews()
            v.marco.addView(ui.cuadro(ResultadoActivity.icono(c, Especial.de(l.texto, c), l.formato), r.clase, 44), ui.px(44), ui.px(44))
            v.texto.text = r.principal
            v.texto.typeface = if (r.mono) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
            v.texto.textSize = if (r.mono) 17f else 18f
            v.detalle.text = Resumenes.lineaDebajo(r, l.hora.take(5), l.notas, if (l.importada || l.creada) l.textoOrigen else "")
            v.estrella.visibility = if (l.favorito) View.VISIBLE else View.GONE
            v.setOnClickListener { Procesar.abrirResultado(act, l) }
            v.setOnLongClickListener { menuLectura(v, l); true }
        }
    }
}
