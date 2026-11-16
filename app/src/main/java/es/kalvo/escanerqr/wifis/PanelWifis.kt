package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.wifis.nucleo.Claves
import es.kalvo.escanerqr.wifis.nucleo.Cuando
import es.kalvo.escanerqr.wifis.nucleo.FiltroRedes
import es.kalvo.escanerqr.wifis.nucleo.Orden
import es.kalvo.escanerqr.wifis.nucleo.OrdenRedes
import es.kalvo.escanerqr.wifis.nucleo.Rango
import es.kalvo.escanerqr.wifis.nucleo.Red
import rikka.shizuku.Shizuku

/**
 * «Wifis» tab (what was the «Mis wifis» app until v2.0): the saved wifis with search, filters and a card per network
 * that opens its detail ([Ficha]). Nothing is kept on disk: every time it is opened it asks Android again (through Shizuku).
 * It only reads when the tab is shown, never while you are scanning.
 */
class PanelWifis(private val act: Activity) {
    private val ui = Ui(act)
    private val ua = es.kalvo.escanerqr.Ui(act)
    private val root = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(ua.cFondo)
    }
    /** Everything under the top bar, with the side margins. */
    private val dentro = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ua.px(16), ua.px(4), ua.px(16), 0) }
    val vista: View get() = root
    private val resumen: android.widget.TextView
    private val avisos: LinearLayout
    private val cargando: LinearProgressIndicator
    private val buscar: TextInputEditText
    private val filtros: LinearLayout
    private val btnOrden: MaterialButton
    private val rangos: View
    /** «De otras apps»: only shown when some app saved a network as a suggestion (S-J5). */
    private val chipOtras: Chip
    private val origenGrupo: ChipGroup
    private val lista: RecyclerView
    private val vacio: android.widget.TextView
    private val botones: LinearLayout
    private val adaptador = Adaptador<Red> { fila(it) }

    private var redes: List<Red> = emptyList()
    private var cargada = false
    private var cargandoAhora = false
    private var visible = false
    private var filtro = 0 // 0 todas, 1 normales, 2 de otras apps (S-J5)
    private var conUbicacion = false
    private var escaneadas = false
    private var rango = Rango.SIEMPRE
    private val aj = es.kalvo.escanerqr.Ajustes(act)
    /** Remembered between uses (S-J4, v2.3). */
    private var orden = Orden.entries.getOrElse(aj.ordenWifis) { Orden.NOMBRE }

    private val alLlegarShizuku = Shizuku.OnBinderReceivedListener { act.runOnUiThread { if (visible) recargar() } }
    private val alMorirShizuku = Shizuku.OnBinderDeadListener { act.runOnUiThread { cargada = false; if (visible) pintarEstado() } }

    private val capaBuscar: TextInputLayout

    init {
        // «Estilo A» (S-J4): bar with the count, «Añadir», «QR a mano» and the magnifier, like the Historial.
        botones = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ua.cSuperficie)
            setPadding(ua.px(18), ua.px(4), ua.px(4), ua.px(4))
        }
        resumen = ua.texto("", 15f, ua.c(es.kalvo.escanerqr.R.color.texto_cuenta)).apply { maxLines = 2 }
        botones.addView(resumen, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        botones.addView(botonBarra("Añadir") { Dialogos.anadir(act, null, redes) { recargar() } })
        botones.addView(botonBarra("QR a mano") { Dialogos.soloQr(act) })
        botones.addView(ua.botonIcono(es.kalvo.escanerqr.R.drawable.ic_buscar, "Buscar por nombre", ua.cText) { alternarBuscar() })
        root.addView(botones, MATCH_PARENT, WRAP_CONTENT)
        root.addView(dentro, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        avisos = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(0, ua.px(4), 0, 0) }
        dentro.addView(avisos)

        val capa = TextInputLayout(act, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply {
            hint = "Buscar por nombre"
            endIconMode = TextInputLayout.END_ICON_CLEAR_TEXT
        }
        buscar = TextInputEditText(capa.context).apply { setSingleLine() }
        capa.addView(buscar)
        capa.visibility = View.GONE
        capaBuscar = capa
        dentro.addView(capa, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.px(8) })
        buscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = aplicarFiltro()
        })

        // Filters (S-J4, v2.3): who saved it, with location, scanned; then «Ordenar» and the date range of that order.
        filtros = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val origen = ChipGroup(act).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = true }
        listOf("Todas", "Normales", "De otras apps").forEachIndexed { i, t ->
            origen.addView(Chip(act, null, com.google.android.material.R.attr.chipStyle).apply {
                id = 200 + i; text = t; isCheckable = true; isChecked = i == 0
            })
        }
        origenGrupo = origen
        chipOtras = origen.getChildAt(2) as Chip
        chipOtras.visibility = View.GONE
        origen.setOnCheckedStateChangeListener { _, ids -> filtro = (ids.firstOrNull() ?: 200) - 200; aplicarFiltro() }
        val extra = ChipGroup(act).apply { isSingleLine = true }
        extra.addView(chipFiltro("Con ubicación") { conUbicacion = it; aplicarFiltro() })
        extra.addView(chipFiltro("Escaneadas") { escaneadas = it; aplicarFiltro() })
        val fila1 = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        fila1.addView(origen)
        fila1.addView(extra, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.px(8) })
        filtros.addView(android.widget.HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false; addView(fila1) })

        btnOrden = ua.botonSecundario("", es.kalvo.escanerqr.R.drawable.ic_ordenar, alto = 48) { elegirOrden() }.apply {
            iconGravity = MaterialButton.ICON_GRAVITY_START
        }
        // Date range (S-J5): its own row under «Ordenar», aligned left like the rest; wraps to a second line
        // if it does not fit (never pressed against the edge).
        val grupoRango = ChipGroup(act).apply { isSingleSelection = true; isSelectionRequired = true; isSingleLine = false; chipSpacingVertical = 0 }
        Rango.entries.forEachIndexed { i, r ->
            grupoRango.addView(Chip(act, null, com.google.android.material.R.attr.chipStyle).apply {
                id = 300 + i; text = r.nombre; isCheckable = true; isChecked = i == 0
            })
        }
        grupoRango.setOnCheckedStateChangeListener { _, ids -> rango = Rango.entries[(ids.firstOrNull() ?: 300) - 300]; aplicarFiltro() }
        rangos = grupoRango
        filtros.addView(btnOrden, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        filtros.addView(rangos, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(4) })
        pintarOrden()
        dentro.addView(filtros)

        cargando = LinearProgressIndicator(act).apply { isIndeterminate = true; visibility = View.GONE }
        dentro.addView(cargando, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        vacio = ui.text("", 15f, col = ua.cMuted).apply { gravity = Gravity.CENTER; setPadding(0, ui.px(24), 0, 0); visibility = View.GONE }
        dentro.addView(vacio)

        lista = RecyclerView(act).apply {
            layoutManager = LinearLayoutManager(act)
            adapter = adaptador
            clipToPadding = false
            setPadding(0, ui.px(4), 0, ui.px(24))
        }
        dentro.addView(lista, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        Shizuku.addBinderReceivedListener(alLlegarShizuku)
        Shizuku.addBinderDeadListener(alMorirShizuku)
    }

    /** Since v2.2 the tab bar is at the top and takes the status bar; nothing to add here. */
    fun margenArriba(@Suppress("UNUSED_PARAMETER") top: Int) {}

    private fun botonBarra(texto: String, accion: () -> Unit) =
        MaterialButton(act, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = texto; isAllCaps = false; textSize = 15f
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ua.cText)
            minWidth = 0; minimumWidth = 0; minHeight = ua.px(48)
            setPadding(ua.px(8), 0, ua.px(8), 0)
            setOnClickListener { accion() }
        }

    private fun alternarBuscar() {
        val abrir = capaBuscar.visibility != View.VISIBLE
        if (!abrir) buscar.setText("")
        capaBuscar.visibility = if (abrir) View.VISIBLE else View.GONE
        val im = act.getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        if (abrir) { buscar.requestFocus(); im?.showSoftInput(buscar, 0) } else im?.hideSoftInputFromWindow(buscar.windowToken, 0)
    }

    /** The tab is shown: read the networks if not read yet (or if Shizuku came back). */
    fun mostrar() {
        visible = true
        if (!cargada || EstadoShizuku.ahora() != EstadoShizuku.E.LISTO) recargar()
    }

    fun ocultar() { visible = false }

    fun cerrar() {
        Shizuku.removeBinderReceivedListener(alLlegarShizuku)
        Shizuku.removeBinderDeadListener(alMorirShizuku)
    }

    /** Asks Android again for all the networks (background thread). */
    fun recargar() {
        pintarEstado()
        if (EstadoShizuku.ahora() != EstadoShizuku.E.LISTO || cargandoAhora) return
        cargandoAhora = true
        cargando.visibility = View.VISIBLE
        act.fondo({
            // The wifi codes of the history, to say when each network was scanned (read-only, stays in the app).
            val historial = try { es.kalvo.escanerqr.Db.de(act).wifis() } catch (e: Throwable) { SemWifi.registrar("historial", e); emptyList() }
            Redes.cargar(historial)
        }) { r ->
            cargandoAhora = false
            cargando.visibility = View.GONE
            val c = r.getOrElse { e ->
                Redes.Carga(emptyList(), listOf("No pude leer las redes: ${e.javaClass.simpleName}"))
            }
            redes = c.redes
            cargada = true
            pintarEstado()
            c.avisos.forEach { avisos.addView(tarjetaAviso(it, null, null)) }
            aplicarFiltro()
        }
    }

    private fun pintarEstado() {
        avisos.removeAllViews()
        val e = EstadoShizuku.ahora()
        val listo = e == EstadoShizuku.E.LISTO
        for (i in 1 until botones.childCount) botones.getChildAt(i).visibility = if (listo) View.VISIBLE else View.GONE
        filtros.visibility = if (listo) View.VISIBLE else View.GONE
        if (listo) {
            val normales = redes.count { !it.sugerencia }
            val otras = redes.count { it.sugerencia }
            resumen.text = if (!cargada) "Leyendo tus wifis…" else buildString {
                append("$normales redes")
                if (otras > 0) append(" · $otras de otras apps")
            }
            // «De otras apps» only when there is some (on a phone without them it would always be empty).
            chipOtras.visibility = if (otras > 0) View.VISIBLE else View.GONE
            if (otras == 0 && filtro == 2) origenGrupo.check(200)
            return
        }
        resumen.text = "Sin Shizuku no puedo ver tus wifis."
        redes = emptyList(); cargada = false; adaptador.items = emptyList(); vacio.visibility = View.GONE
        val par: Pair<String, () -> Unit> = when (e) {
            EstadoShizuku.E.SIN_PERMISO -> "Dar permiso" to { EstadoShizuku.pedirPermiso { act.runOnUiThread { recargar() } } }
            else -> "Abrir Shizuku" to {
                if (!EstadoShizuku.abrirShizuku(act)) Toast.makeText(act, "No encuentro Shizuku instalado", Toast.LENGTH_LONG).show()
            }
        }
        avisos.addView(tarjetaAviso(EstadoShizuku.texto(e), par.first, par.second))
        avisos.addView(tarjetaAviso(
            "Mientras, sí puedes hacer el QR de una red escribiendo tú el nombre y la contraseña.",
            "Hacer un QR a mano",
        ) { Dialogos.soloQr(act) })
        avisos.addView(ua.botonSecundario("Volver a mirar", alto = 48) { recargar() }.apply {
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(4) }
        })
    }

    private fun tarjetaAviso(texto: String, boton: String?, accion: (() -> Unit)?): View {
        val card = MaterialCardView(act).apply {
            radius = ui.px(16).toFloat(); cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(ua.c(es.kalvo.escanerqr.R.color.aviso_fondo))
        }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(16), ui.px(12), ui.px(16), ui.px(12)) }
        col.addView(ui.text(texto, 15f, col = ua.c(es.kalvo.escanerqr.R.color.aviso_texto)))
        if (boton != null && accion != null) {
            col.addView(ua.botonSecundario(boton, alto = 48) { accion() }.apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { topMargin = ui.px(8) }
            })
        }
        card.addView(col)
        return LinearLayout(act).apply { setPadding(0, ui.px(4), 0, ui.px(4)); addView(card, MATCH_PARENT, WRAP_CONTENT) }
    }

    private fun chipFiltro(texto: String, cambia: (Boolean) -> Unit) =
        Chip(act, null, com.google.android.material.R.attr.chipStyle).apply {
            text = texto; isCheckable = true
            setOnCheckedChangeListener { _, c -> cambia(c) }
        }

    /** «Ordenar»: the 4 orders; the choice is remembered. */
    private fun elegirOrden() {
        val nombres = Orden.entries.map { it.nombre }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
            .setTitle("Ordenar")
            .setSingleChoiceItems(nombres, orden.ordinal) { d, i ->
                d.dismiss()
                orden = Orden.entries[i]; aj.ordenWifis = i
                pintarOrden(); aplicarFiltro()
                lista.scrollToPosition(0)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** The button says the current order; the date range only makes sense with a date order. */
    private fun pintarOrden() {
        btnOrden.text = "Ordenar: " + when (orden) {
            Orden.NOMBRE -> "A-Z"
            Orden.ULTIMA_CONEXION -> "última conexión"
            Orden.GUARDADA -> "guardada"
            Orden.ESCANEADA -> "escaneada"
        }
        btnOrden.contentDescription = "Ordenar. Ahora: ${orden.nombre}"
        rangos.visibility = if (orden == Orden.NOMBRE) View.GONE else View.VISIBLE
    }

    private fun aplicarFiltro() {
        val q = buscar.text?.toString().orEmpty().trim()
        val f = FiltroRedes(filtro, conUbicacion, escaneadas, rango, q)
        val vista = OrdenRedes.aplicar(redes, f, orden, System.currentTimeMillis())
        adaptador.items = vista
        vacio.visibility = if (cargada && vista.isEmpty() && EstadoShizuku.ahora() == EstadoShizuku.E.LISTO) View.VISIBLE else View.GONE
        vacio.text = when {
            q.isNotEmpty() -> "Ninguna red se llama así."
            f != FiltroRedes(filtro) || filtro != 0 -> "Ninguna red cumple estos filtros."
            else -> "No hay redes en esta lista."
        }
    }

    /** One network: 44 dp icon square (green = normal, grey = another app's suggestion), name in 18 sp, details under it. */
    private fun fila(r: Red): View {
        val fila = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.px(2), ui.px(10), 0, ui.px(10))
            minimumHeight = ui.px(68)
            val fondo = android.util.TypedValue()
            act.theme.resolveAttribute(android.R.attr.selectableItemBackground, fondo, true)
            setBackgroundResource(fondo.resourceId)
            isClickable = true; isFocusable = true
            setOnClickListener { Ficha.abrir(act, r, redes) { recargar() } }
        }
        val clase = if (r.sugerencia) es.kalvo.escanerqr.nucleo.Clase.OTRO else es.kalvo.escanerqr.nucleo.Clase.WIFI
        fila.addView(ua.cuadro(es.kalvo.escanerqr.R.drawable.ic_wifi, clase, 44))
        val textos = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(16), 0, ui.px(8), 0) }
        textos.addView(ua.texto(r.ssid, 18f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        val detalle = buildList {
            add(r.seguridad.nombre)
            add(Quien.corto(act, r.origen, r.sugerencia))
            if (r.oculta) add("oculta")
            if (orden == Orden.GUARDADA) r.uso?.creada?.let { add("Guardada el " + Cuando.fechaCorta(it)) }
            if (r.detalle?.conectadaAhora != true) Cuando.lineaLista(r, System.currentTimeMillis()).takeIf { it.isNotEmpty() }?.let { add(it) }
        }.joinToString(" · ")
        textos.addView(ua.texto(detalle, 13f, ua.cMuted).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, ui.px(3), 0, 0) })
        fila.addView(textos, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        if (r.detalle?.conectadaAhora == true) fila.addView(ua.pastilla("Conectada",
            ua.c(es.kalvo.escanerqr.R.color.wifi_fondo), ua.c(es.kalvo.escanerqr.R.color.wifi)))
        if (Ficha.puedeQr(r)) {
            fila.addView(ua.botonIcono(es.kalvo.escanerqr.R.drawable.ic_qr, "Ver el QR de ${r.ssid}", ua.cText) { Ficha.verQr(act, r) })
        }
        return fila
    }
}
