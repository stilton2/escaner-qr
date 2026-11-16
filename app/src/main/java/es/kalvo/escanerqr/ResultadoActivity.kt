package es.kalvo.escanerqr

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.nucleo.Clase
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Especial
import es.kalvo.escanerqr.nucleo.Formatos
import es.kalvo.escanerqr.nucleo.Historial
import es.kalvo.escanerqr.nucleo.Lectura
import es.kalvo.escanerqr.nucleo.Resumenes
import es.kalvo.escanerqr.wifis.Dialogos
import es.kalvo.escanerqr.wifis.EstadoShizuku
import es.kalvo.escanerqr.wifis.FilasRed
import es.kalvo.escanerqr.wifis.Quien
import es.kalvo.escanerqr.wifis.Redes
import es.kalvo.escanerqr.wifis.SinShizuku
import es.kalvo.escanerqr.wifis.avisarConexion
import es.kalvo.escanerqr.wifis.fondo
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.DesdeEscaner
import es.kalvo.escanerqr.wifis.nucleo.Escaneos
import es.kalvo.escanerqr.wifis.nucleo.Red
import es.kalvo.escanerqr.wifis.nucleo.YaGuardada
import rikka.shizuku.Shizuku

/**
 * Result of a scan (new or from the history). «Estilo A» (S-J4): teal header with back, type and time; big icon and title;
 * rounded cards with the key datum in monospace; amber main button; outlined secondary buttons. Laid out per type:
 * device label (model, MAC, serial), MAC, SIM (ICCID), article label, wifi (password, Conectar, DATOS DE LA RED) and the rest.
 */
class ResultadoActivity : AppCompatActivity() {
    private lateinit var l: Lectura
    private lateinit var ui: Ui
    private lateinit var cuerpo: LinearLayout
    private lateinit var tituloBarra: TextView
    private lateinit var subBarra: TextView
    private lateinit var btnEstrella: MaterialButton
    private lateinit var btnMas: MaterialButton
    private val db by lazy { Db.de(this) }
    private val aj by lazy { Ajustes(this) }
    /** What «Copiar al portapapeles» copied when it was read (only for a fresh read, not from the history). */
    private var copiado: String? = null

    // Wifi results: buttons that need Shizuku, whether a save/connect is running, and the network's data (read once).
    private var esWifi = false
    private var estadoPintado: EstadoShizuku.E? = null
    private var btnConectar: MaterialButton? = null
    private var btnGuardar: MaterialButton? = null
    private var trabajando = false
    private var verClave = false
    private var redesLeidas: List<Red>? = null
    private var leyendoRedes = false
    private var ssidSinShizuku = ""
    private val alLlegarShizuku = Shizuku.OnBinderReceivedListener { runOnUiThread { if (esWifi && !isFinishing) pintar() } }

    /** Android's «¿Guardar esta red?» window (no Shizuku). */
    private val anadirRed = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        Toast.makeText(this, SinShizuku.resultado(r.resultCode, r.data, ssidSinShizuku), Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        l = desdeIntent(intent) ?: run { finish(); return }
        if (l.id > 0) db.una(l.id)?.let { l = it }
        copiado = intent.getStringExtra("copiado")

        Ui.ventana(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ui = Ui(this, raiz)
        raiz.setBackgroundColor(ui.cFondo)
        val barra = construirBarra()
        raiz.addView(barra, MATCH_PARENT, WRAP_CONTENT)
        cuerpo = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cuerpo.setPadding(ui.px(18), ui.px(20), ui.px(18), ui.px(28))
        val scroll = ScrollView(this).apply { addView(cuerpo, MATCH_PARENT, WRAP_CONTENT); clipToPadding = false }
        raiz.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            barra.setPadding(ui.px(4) + b.left, b.top + ui.px(10), ui.px(4) + b.right, ui.px(10))
            scroll.setPadding(b.left, 0, b.right, b.bottom)
            ins
        }
        pintar()
        Shizuku.addBinderReceivedListener(alLlegarShizuku)
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(alLlegarShizuku)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        // Back from Shizuku (started it or gave permission): show the wifi buttons.
        if (esWifi && EstadoShizuku.ahora() != estadoPintado) pintar()
    }

    // ---------- cabecera: atrás, tipo, hora; favorito y menú ----------

    private fun construirBarra(): LinearLayout {
        val blanco = ui.c(R.color.barra_activa)
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(ui.cBarra)
        }
        barra.addView(ui.botonIcono(R.drawable.ic_atras, "Atrás", blanco) { finish() })
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(4), 0, ui.px(8), 0) }
        tituloBarra = ui.texto("", 20f, blanco, bold = true).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }
        subBarra = ui.texto("", 13f, ui.c(R.color.barra_texto)).apply { maxLines = 1 }
        col.addView(tituloBarra); col.addView(subBarra)
        barra.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        btnEstrella = ui.botonIcono(R.drawable.ic_estrella, "Añadir a favoritos", blanco) {
            db.ponerFavorito(l.id, !l.favorito); l = l.copy(favorito = !l.favorito); pintarEstrella()
        }
        btnMas = ui.botonIcono(R.drawable.ic_mas, "Más: notas y borrar", blanco) { menuMas(btnMas) }
        barra.addView(btnEstrella); barra.addView(btnMas)
        return barra
    }

    private fun pintarEstrella() {
        val si = l.favorito
        btnEstrella.setIconResource(if (si) R.drawable.ic_estrella_llena else R.drawable.ic_estrella)
        btnEstrella.iconTint = android.content.res.ColorStateList.valueOf(if (si) ui.cAmbar else ui.c(R.color.barra_activa))
        btnEstrella.contentDescription = if (si) "Quitar de favoritos" else "Añadir a favoritos"
    }

    private fun menuMas(ancla: View) {
        val m = PopupMenu(this, ancla)
        m.menu.add(0, 1, 0, "Notas")
        m.menu.add(0, 2, 1, "Borrar")
        m.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> editarNotas(this, l.notas) { n -> db.ponerNotas(l.id, n); l = l.copy(notas = n); pintar() }
                2 -> MaterialAlertDialogBuilder(this)
                    .setTitle("¿Borrar esta lectura?")
                    .setMessage(l.texto.take(120))
                    .setPositiveButton("Borrar") { _, _ -> db.borrar(listOf(l.id)); finish() }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
            true
        }
        m.show()
    }

    // ---------- el cuerpo, según el tipo ----------

    private fun pintar() {
        cuerpo.removeAllViews()
        btnConectar = null; btnGuardar = null
        val c = Contenido.analizar(l.texto, l.formato)
        val e = Especial.de(l.texto, c)
        esWifi = c is Contenido.Wifi
        val res = Resumenes.de(l.texto, l.formato)
        tituloBarra.text = e?.tipo ?: c.tipo
        subBarra.text = "${fechaCorta(l)} · ${if (l.formato == "QR Code") "QR" else l.formato}"
        val guardada = l.id > 0
        btnEstrella.visibility = if (guardada) View.VISIBLE else View.GONE
        btnMas.visibility = if (guardada) View.VISIBLE else View.GONE
        pintarEstrella()

        when (e) {
            is Especial.Equipo -> equipo(e)
            is Especial.Sim -> sim(e)
            is Especial.Articulo -> articulo(e)
            null -> when (c) {
                is Contenido.Wifi -> wifi(c)
                else -> normal(c, res.clase)
            }
        }

        if (l.notas.isNotEmpty()) {
            cuerpo.addView(ui.titulo("Notas"))
            ui.tarjeta(cuerpo) { addView(ui.texto(l.notas, 15f).apply { setTextIsSelectable(true) }) }
        }
        mas(c, e)
        // Raw text under the fields, for the structured types.
        if (e != null || (c !is Contenido.Enlace && c !is Contenido.Texto && c !is Contenido.Producto && c !is Contenido.Telefono)) {
            cuerpo.addView(ui.titulo(if (l.creada) "Texto del código" else "Texto leído"))
            ui.tarjeta(cuerpo) { addView(ui.texto(if (esWifi) ocultarClave(l.texto, (c as Contenido.Wifi).clave) else l.texto, 13f, ui.cMuted).apply { setTextIsSelectable(!esWifi) }) }
        }
        if (l.id == 0L && !aj.guardar) cuerpo.addView(ui.texto("No se ha guardado en el historial (lo tienes apagado en Ajustes).", 13f, ui.cMuted))
        // Where it came from (S-J5): scanned here or imported from another app or file.
        else cuerpo.addView(ui.texto((if (l.creada) "Creado con esta app" else if (l.importada) "Importado de ${l.origen}" else "Escaneado con esta app") + " · ${fechaBonita(l)}", 13f, ui.cMuted)
            .apply { setPadding(0, ui.px(12), 0, 0) })
    }

    private fun marca(valor: String) = if (copiado != null && copiado == valor) "Copiada al leer" else null

    // 1) Device label: model in the title, MAC big with both copies, serial with its copy button.
    private fun equipo(e: Especial.Equipo) {
        ui.cabecera(cuerpo, R.drawable.ic_equipo, Clase.EQUIPO,
            e.modelo.ifEmpty { if (e.soloMac) "Dirección MAC" else "Equipo" },
            if (e.modelo.isNotEmpty()) "Modelo del equipo" else "Leída en ${l.formato}")
        for (mac in e.macs) ui.tarjeta(cuerpo) {
            val cab = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            cab.addView(ui.etiqueta(if (e.macs.size == 1) "MAC" else "MAC ${e.macs.indexOf(mac) + 1}"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            marca(mac)?.let { cab.addView(ui.pastilla(it)) }
            addView(cab)
            addView(ui.mono(ui.texto(mac, 28f).apply { setTextIsSelectable(true); setPadding(0, ui.px(8), 0, ui.px(12)); letterSpacing = 0.02f }))
            val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false; gravity = Gravity.CENTER_VERTICAL }
            fila.addView(ui.botonPrincipal("Copiar con :", alto = 56) { Acciones.copiar(this@ResultadoActivity, mac) }.apply {
                contentDescription = "Copiar la MAC con dos puntos"
            }, LinearLayout.LayoutParams(0, ui.px(56), 1f).apply { marginEnd = ui.px(8) })
            val sin = Especial.sinDosPuntos(mac)
            fila.addView(ui.botonSecundario("Copiar sin :\n$sin", alto = 56) { Acciones.copiar(this@ResultadoActivity, sin) }.apply {
                textSize = 13f; contentDescription = "Copiar la MAC sin dos puntos, $sin"
            }, LinearLayout.LayoutParams(0, ui.px(56), 1f))
            addView(fila)
        }
        if (e.serie.isNotEmpty()) ui.dato(cuerpo, "Nº de serie", e.serie, marca = marca(e.serie)) { Acciones.copiar(this, e.serie) }
        if (e.modelo.isNotEmpty()) ui.dato(cuerpo, "Modelo", e.modelo, tam = 16f) { Acciones.copiar(this, e.modelo) }
    }

    // 3) SIM: the number grouped by 4 to read it; copied whole, without spaces.
    private fun sim(s: Especial.Sim) {
        ui.cabecera(cuerpo, R.drawable.ic_sim, Clase.EQUIPO, "Tarjeta SIM", "ICCID · ${s.iccid.length} cifras · ${l.formato}")
        ui.tarjeta(cuerpo) {
            val cab = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            cab.addView(ui.etiqueta("ICCID"), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            marca(s.iccid)?.let { cab.addView(ui.pastilla(it)) }
            addView(cab)
            addView(ui.mono(ui.texto(Especial.agrupar4(s.iccid), 24f).apply {
                setPadding(0, ui.px(8), 0, ui.px(12)); contentDescription = s.iccid.toList().joinToString(" ")
            }))
            addView(ui.botonPrincipal("Copiar número") { Acciones.copiar(this@ResultadoActivity, s.iccid) })
        }
    }

    // 4) Article label: one row per field, each with its copy button.
    private fun articulo(a: Especial.Articulo) {
        ui.cabecera(cuerpo, R.drawable.ic_producto, Clase.EQUIPO, a.modelo.ifEmpty { a.codigo },
            if (a.modelo.isNotEmpty()) "Artículo ${a.codigo}" else "Código de artículo")
        val todo = l.texto.trim()
        ui.dato(cuerpo, "Artículo", a.codigo, marca = if (a.serie.isEmpty() && copiado == todo) "Copiado al leer" else null) { Acciones.copiar(this, a.codigo) }
        if (a.modelo.isNotEmpty()) ui.dato(cuerpo, "Modelo", a.modelo) { Acciones.copiar(this, a.modelo) }
        if (a.serie.isNotEmpty()) ui.dato(cuerpo, "Nº de serie", a.serie, marca = marca(a.serie)) { Acciones.copiar(this, a.serie) }
        if (a.fecha.isNotEmpty()) ui.dato(cuerpo, "Fecha", a.fecha, monoespacio = false) { Acciones.copiar(this, a.fecha) }
        a.otros.forEach { o -> ui.dato(cuerpo, "Otro código", o) { Acciones.copiar(this, o) } }
        if (a.modelo.isNotEmpty() || a.serie.isNotEmpty()) cuerpo.addView(ui.botonSecundario("Copiar la etiqueta entera", R.drawable.ic_copiar) {
            Acciones.copiar(this, todo)
        }.apply { layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { bottomMargin = ui.px(8) } })
    }

    // 5) Everything else: icon and title, the fields in a card, the main action in amber.
    private fun normal(c: Contenido, clase: Clase) {
        val ic = icono(c, null, l.formato)
        when (c) {
            is Contenido.Enlace -> {
                ui.cabecera(cuerpo, ic, clase, Resumenes.sinEsquema(c.url).substringBefore('/'), "Sitio web")
                ui.dato(cuerpo, "Enlace", c.url, monoespacio = false, tam = 16f) { Acciones.copiar(this, c.url) }
                principal("Abrir el enlace") { Acciones.abrirEnlace(this, c.url) }
            }
            is Contenido.Telefono -> {
                ui.cabecera(cuerpo, ic, clase, c.numero, "Teléfono")
                principal("Llamar") { Acciones.llamar(this, c.numero) }
                a("Añadir a contactos", R.drawable.ic_contacto) { Acciones.contacto(this, "", listOf(c.numero), emptyList()) }
                a("Copiar número", R.drawable.ic_copiar) { Acciones.copiar(this, c.numero) }
            }
            is Contenido.Email -> {
                ui.cabecera(cuerpo, ic, clase, c.para, "Correo electrónico")
                campos(listOf("Para" to c.para, "Asunto" to c.asunto, "Mensaje" to c.cuerpo))
                principal("Escribir a ${c.para}") { Acciones.correo(this, c.para, c.asunto, c.cuerpo) }
                a("Añadir a contactos", R.drawable.ic_contacto) { Acciones.contacto(this, "", emptyList(), listOf(c.para)) }
                a("Copiar dirección", R.drawable.ic_copiar) { Acciones.copiar(this, c.para) }
            }
            is Contenido.Sms -> {
                ui.cabecera(cuerpo, ic, clase, c.numero, "SMS")
                campos(listOf("Número" to c.numero, "Mensaje" to c.mensaje))
                principal("Enviar SMS") { Acciones.sms(this, c.numero, c.mensaje) }
                a("Llamar a ${c.numero}", R.drawable.ic_llamar) { Acciones.llamar(this, c.numero) }
                a("Copiar número", R.drawable.ic_copiar) { Acciones.copiar(this, c.numero) }
            }
            is Contenido.Geo -> {
                ui.cabecera(cuerpo, ic, clase, c.consulta.ifEmpty { "Ubicación" }, "Ubicación")
                ui.dato(cuerpo, "Coordenadas", "${c.lat}, ${c.lon}") { Acciones.copiar(this, "${c.lat}, ${c.lon}") }
                principal("Ver en el mapa") { Acciones.mapa(this, c) }
            }
            is Contenido.Contacto -> {
                ui.cabecera(cuerpo, ic, clase, c.nombre.ifEmpty { "Contacto" }, listOf(c.cargo, c.empresa).filter { it.isNotEmpty() }.joinToString(" · ").ifEmpty { "Contacto" })
                campos(c.telefonos.map { "Teléfono" to it } + c.emails.map { "Correo" to it } +
                    listOf("Empresa" to c.empresa, "Cargo" to c.cargo, "Dirección" to c.direccion, "Web" to c.web, "Nota" to c.nota))
                principal("Añadir a contactos") {
                    Acciones.contacto(this, c.nombre, c.telefonos, c.emails, c.empresa, c.cargo, c.direccion, c.web, c.nota)
                }
                c.telefonos.firstOrNull()?.let { t -> a("Llamar a $t", R.drawable.ic_llamar) { Acciones.llamar(this, t) } }
                c.emails.firstOrNull()?.let { m -> a("Escribir a $m", R.drawable.ic_correo) { Acciones.correo(this, m) } }
                if (c.web.isNotEmpty() && Contenido.esEnlace(c.web)) a("Abrir su web", R.drawable.ic_abrir) { Acciones.abrirEnlace(this, Contenido.normalizarEnlace(c.web)) }
                if (c.direccion.isNotEmpty()) a("Ver la dirección en el mapa", R.drawable.ic_mapa) {
                    Acciones.abrirEnlace(this, "geo:0,0?q=" + android.net.Uri.encode(c.direccion))
                }
            }
            is Contenido.Evento -> {
                ui.cabecera(cuerpo, ic, clase, c.titulo.ifEmpty { "Evento" }, "Evento")
                campos(listOf("Empieza" to fechaEvento(c.inicio), "Acaba" to (if (c.fin.isNotEmpty()) fechaEvento(c.fin) else ""),
                    "Lugar" to c.lugar, "Descripción" to c.descripcion))
                principal("Añadir al calendario") { Acciones.evento(this, c) }
            }
            is Contenido.Producto -> {
                ui.cabecera(cuerpo, ic, clase, "Producto", l.formato)
                ui.dato(cuerpo, "Número", c.codigo) { Acciones.copiar(this, c.codigo) }
                principal("Buscar el producto en la web") { Acciones.buscar(this, c.codigo, aj.buscador) }
            }
            is Contenido.Texto -> {
                ui.cabecera(cuerpo, ic, clase, "Texto", "Leído en ${l.formato}")
                ui.tarjeta(cuerpo) {
                    setPadding(ui.px(16), ui.px(12), ui.px(4), ui.px(12))
                    val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
                    fila.addView(ui.texto(l.texto, 18f).apply { setTextIsSelectable(true); setPadding(0, ui.px(6), 0, ui.px(6)) },
                        LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
                    fila.addView(ui.botonIcono(R.drawable.ic_copiar, "Copiar el texto") { Acciones.copiar(this@ResultadoActivity, l.texto) })
                    addView(fila)
                }
                principal("Buscar en la web") { Acciones.buscar(this, l.texto, aj.buscador) }
            }
            is Contenido.Wifi -> {}
        }
    }

    /** Fields of a structured type in one card (empty ones left out). */
    private fun campos(pares: List<Pair<String, String>>) {
        val llenos = pares.filter { it.second.isNotEmpty() }
        if (llenos.isEmpty()) return
        ui.tarjeta(cuerpo) {
            llenos.forEachIndexed { i, (k, v) ->
                addView(ui.etiqueta(k).apply { setPadding(0, if (i == 0) 0 else ui.px(10), 0, 0) })
                addView(ui.texto(v, 17f).apply { setTextIsSelectable(true); setPadding(0, ui.px(2), 0, 0) })
            }
        }
    }

    private fun principal(texto: String, f: () -> Unit) {
        cuerpo.addView(ui.botonPrincipal(texto) { f() }.apply {
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { topMargin = ui.px(4); bottomMargin = ui.px(10) }
        })
    }

    private fun a(texto: String, icono: Int, f: () -> Unit) = cuerpo.addView(ui.accion(texto, icono, f))

    /** «Más»: copy everything, share, see as QR, search (secondary buttons). */
    private fun mas(c: Contenido, e: Especial?) {
        cuerpo.addView(ui.titulo("Más"))
        // S-J9: a code made in «Crear»: back to its picture (same format), to share or save it again.
        if (l.creada) a("Ver el código creado", R.drawable.ic_qr) { CodigoActivity.abrir(this, l.texto, l.formato, guardar = false) }
        if (e != null) {
            val q = (e as? Especial.Equipo)?.modelo?.ifEmpty { null } ?: (e as? Especial.Articulo)?.modelo?.ifEmpty { null } ?: l.texto
            a("Buscar en la web", R.drawable.ic_buscar) { Acciones.buscar(this, q, aj.buscador) }
        }
        if (c !is Contenido.Enlace && c !is Contenido.Wifi && c !is Contenido.Texto) a("Copiar todo", R.drawable.ic_copiar) { Acciones.copiar(this, l.texto) }
        if (e != null) a("Copiar todo lo leído", R.drawable.ic_copiar) { Acciones.copiar(this, l.texto) }
        if (c is Contenido.Wifi) {
            a("Copiar nombre de red", R.drawable.ic_copiar) { Acciones.copiar(this, c.ssid) }
            a("Copiar todo (con la contraseña)", R.drawable.ic_copiar) { Acciones.copiar(this, l.texto, sensible = true) }
        } else {
            a("Compartir", R.drawable.ic_compartir) { Acciones.compartir(this, l.texto) }
            a("Ver como código QR", R.drawable.ic_qr) { verQr() }
        }
    }

    // ---------- wifi: contraseña, Conectar, Guardar sin conectar, QR, compartir, DATOS DE LA RED ----------

    private fun wifi(w: Contenido.Wifi) {
        val d = DesdeEscaner.datos(w.ssid, w.clave, w.tipoMisWifis, w.oculta)
        ui.cabecera(cuerpo, R.drawable.ic_wifi, Clase.WIFI, w.ssid, d.seguridad.nombre + if (w.oculta) " · red oculta" else "")

        // Password: hidden, eye, copy.
        if (w.clave.isNotEmpty()) ui.tarjeta(cuerpo) {
            setPadding(ui.px(16), ui.px(12), ui.px(4), ui.px(12))
            val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            col.addView(ui.etiqueta("Contraseña"))
            val valor = ui.mono(ui.texto(mostrarClave(w.clave), 20f).apply { setPadding(0, ui.px(4), 0, 0) })
            col.addView(valor)
            fila.addView(col, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            lateinit var ojo: MaterialButton
            ojo = ui.botonIcono(if (verClave) R.drawable.ic_ver_no else R.drawable.ic_ver, if (verClave) "Ocultar contraseña" else "Ver contraseña", ui.cText) {
                verClave = !verClave
                valor.text = mostrarClave(w.clave)
                ojo.setIconResource(if (verClave) R.drawable.ic_ver_no else R.drawable.ic_ver)
                ojo.contentDescription = if (verClave) "Ocultar contraseña" else "Ver contraseña"
            }
            fila.addView(ojo)
            fila.addView(ui.botonIcono(R.drawable.ic_copiar, "Copiar contraseña") { Acciones.copiar(this@ResultadoActivity, w.clave, sensible = true) })
            addView(fila)
        } else ui.tarjeta(cuerpo) { addView(ui.texto("Sin contraseña (red abierta)", 16f, ui.cMuted)) }

        // «Ya la tienes guardada» goes here when the network data arrive.
        val hueco = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cuerpo.addView(hueco)

        val e = EstadoShizuku.ahora()
        estadoPintado = e
        val listo = e == EstadoShizuku.E.LISTO
        btnConectar = ui.botonPrincipal("Conectar") { if (listo) guardarWifi(d, conectar = true) else conectarSinShizuku(d) }.apply {
            contentDescription = if (listo) "Conectar a ${w.ssid}: la guarda como red normal y se conecta ahora"
                else "Conectar a ${w.ssid}: Android te pregunta si la guardas"
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { bottomMargin = ui.px(10) }
            isEnabled = !trabajando
        }
        cuerpo.addView(btnConectar)

        val botones = ArrayList<MaterialButton>()
        if (listo) {
            btnGuardar = ui.botonSecundario("Guardar sin conectar") { guardarWifi(d, conectar = false) }.apply { isEnabled = !trabajando }
            botones += btnGuardar!!
        }
        botones += ui.botonSecundario("Ver QR para compartir") { Dialogos.abrirQr(this, d) }
        botones += ui.botonSecundario("Compartir") { Acciones.compartir(this, l.texto) }
        ui.rejilla(cuerpo, botones, columnas = botones.size)

        if (!listo) sinShizukuNota(e)

        // DATOS DE LA RED: from the phone (Shizuku, read-only) and from the history.
        cuerpo.addView(ui.titulo("Datos de la red"))
        val datos = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        cuerpo.addView(datos)
        pintarDatosRed(w, datos, hueco, listo)
    }

    private fun mostrarClave(c: String) = if (verClave) c else if (c.length <= 4) "•".repeat(c.length) else c.take(3) + "•".repeat((c.length - 5).coerceIn(3, 12)) + c.takeLast(2)

    private fun ocultarClave(t: String, clave: String) = if (clave.length >= 4) t.replace(clave, "••••") else t

    private fun sinShizukuNota(e: EstadoShizuku.E) {
        cuerpo.addView(ui.texto("Sin Shizuku, «Conectar» abre la ventana de Android para guardar la red; luego el móvil se conecta solo.",
            13f, ui.cMuted).apply { setPadding(ui.px(2), 0, ui.px(2), ui.px(6)) })
        val hayShizuku = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") != null
        if (hayShizuku && e != EstadoShizuku.E.VIEJO) {
            cuerpo.addView(ui.botonSecundario(if (e == EstadoShizuku.E.SIN_PERMISO) "Dar permiso de Shizuku" else "Abrir Shizuku", alto = 48) {
                if (e == EstadoShizuku.E.SIN_PERMISO) EstadoShizuku.pedirPermiso { runOnUiThread { pintar() } }
                else EstadoShizuku.abrirShizuku(this)
            }.apply { layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { bottomMargin = ui.px(4) } })
        }
    }

    private fun pintarDatosRed(w: Contenido.Wifi, datos: LinearLayout, hueco: LinearLayout, listo: Boolean) {
        val leidas = redesLeidas
        if (listo && leidas == null) {
            datos.addView(ui.texto("Leyendo los datos de la red…", 14f, ui.cMuted).apply { setPadding(0, ui.px(6), 0, ui.px(6)) })
            if (!leyendoRedes) {
                leyendoRedes = true
                fondo({
                    val historial = try { Db.de(this).wifis() } catch (_: Throwable) { emptyList() }
                    Redes.cargar(historial).redes
                }) { r ->
                    leyendoRedes = false
                    redesLeidas = r.getOrElse { emptyList() }
                    if (esWifi) pintar()
                }
            }
            return
        }
        val mismas = leidas.orEmpty().filter { it.ssid == w.ssid }
        val normal = mismas.firstOrNull { !it.sugerencia }
        if (normal != null) {
            ui.aviso(hueco, if (w.clave.isNotEmpty()) "Ya la tienes guardada. Si la contraseña ha cambiado, al conectar se pone la de este código."
                else "Ya la tienes guardada en el móvil.")
            FilasRed.datos(this, ui, datos, normal)
            return
        }
        // Not saved as a normal network (or no Shizuku): what we know from the history.
        val escaneos = try { Escaneos.porNombre(Db.de(this).wifis())[w.ssid].orEmpty() } catch (_: Throwable) { emptyList() }
        val filas = ArrayList<Pair<String, String>>()
        if (listo) {
            filas += "Guardada en el móvil" to "No"
            mismas.filter { it.sugerencia }.forEach { s ->
                filas += "Sugerencia de" to Quien.app(this, s.origen.paquete ?: "?")
            }
        }
        filas += "Escaneada" to es.kalvo.escanerqr.wifis.nucleo.Cuando.escaneada(escaneos, System.currentTimeMillis()).ifEmpty { "Nunca" }
        filas.forEachIndexed { i, (k, v) -> ui.filaValor(datos, k, v, ultima = i == filas.size - 1) }
        if (!listo) datos.addView(ui.texto("Con Shizuku verías también cuándo la guardaste, la última conexión y dónde la usas.", 13f, ui.cMuted)
            .apply { setPadding(0, ui.px(6), 0, 0) })
    }

    /** Android's own «¿Guardar esta red?» window (Android 11+), for phones without Shizuku. */
    private fun conectarSinShizuku(d: DatosRed) {
        DesdeEscaner.problema(d)?.let { p -> noSePuede(p); return }
        val s = SinShizuku.sugerencia(d).getOrElse { e -> noSePuede(e.message ?: "datos no válidos"); return }
        ssidSinShizuku = d.ssid
        try { anadirRed.launch(SinShizuku.intent(s)) } catch (_: ActivityNotFoundException) {
            noSePuede("este móvil no tiene la ventana de Android para guardar redes")
        }
    }

    private fun noSePuede(p: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("No puedo guardarla así")
            .setMessage("${p.replaceFirstChar { it.uppercase() }}.\n\nPuedes copiar la contraseña y ponerla tú en Ajustes → Wi-Fi.")
            .setPositiveButton("Vale", null)
            .show()
    }

    private fun ocupado(si: Boolean) {
        trabajando = si
        btnConectar?.isEnabled = !si
        btnGuardar?.isEnabled = !si
    }

    /** First looks (read-only) whether a wifi with that name is already saved; asks if so; then saves (and connects). */
    private fun guardarWifi(d: DatosRed, conectar: Boolean) {
        if (trabajando) return
        if (EstadoShizuku.ahora() != EstadoShizuku.E.LISTO) { pintar(); return }
        DesdeEscaner.problema(d)?.let { p -> noSePuede(p); return }
        ocupado(true)
        fondo({ Redes.yaGuardada(d.ssid, d.clave) }) { r ->
            ocupado(false)
            val ya = r.getOrElse { YaGuardada(false, emptyList()) }
            if (ya.nada) hacerWifi(d, conectar) else preguntarYaGuardada(d, ya, conectar)
        }
    }

    /** Says whether it is already saved and, if the saved password could be read, whether it is the same or different. */
    private fun preguntarYaGuardada(d: DatosRed, ya: YaGuardada, conectar: Boolean) {
        // Same password as the saved one: nothing to change. Connect straight away, or just say so.
        if (ya.normal && ya.mismaClave == true && ya.sugeridaPor.isEmpty()) {
            if (conectar) hacerWifi(d, true)
            else Toast.makeText(this, "«${d.ssid}» ya está guardada con esta misma contraseña", Toast.LENGTH_LONG).show()
            return
        }
        val texto = StringBuilder()
        if (ya.normal) texto.append(
            when {
                !d.seguridad.llevaClave -> "Esta wifi ya está guardada. ¿La guardo otra vez con lo que dice este código?"
                ya.mismaClave == false -> "Esta wifi ya está guardada en el móvil, pero con OTRA contraseña. ¿Pongo la de este código?"
                ya.mismaClave == true -> "Esta wifi ya está guardada con esta misma contraseña."
                else -> "Esta wifi ya está guardada. ¿Pongo la contraseña nueva?"
            }
        )
        for (p in ya.sugeridaPor) {
            val app = Quien.app(this, p)
            if (texto.isNotEmpty()) texto.append("\n\n")
            texto.append("$app también la tiene, como «sugerencia»: esa sigue atada a $app hasta que desinstales $app. " +
                "Esta se guarda aparte, como red normal del móvil.")
        }
        val si = when {
            ya.normal && d.seguridad.llevaClave -> if (conectar) "Ponerla y conectar" else "Ponerla"
            else -> if (conectar) "Guardar y conectar" else "Guardar"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (ya.normal) "«${d.ssid}» ya está guardada" else "«${d.ssid}» ya la tiene otra app")
            .setMessage(texto.toString())
            .setPositiveButton(si) { _, _ -> hacerWifi(d, conectar) }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** `cmd wifi connect-network` (saves as a normal network and connects) or just saves it, through Shizuku. */
    private fun hacerWifi(d: DatosRed, conectar: Boolean) {
        ocupado(true)
        fondo({ if (conectar) Redes.guardarYConectar(d) else Redes.guardar(d) }) { r ->
            ocupado(false)
            val fallo = r.getOrElse { e -> e.javaClass.simpleName }
            if (fallo == null) {
                if (conectar) avisarConexion(d.ssid)
                else Toast.makeText(this, "Guardada «${d.ssid}» como red normal", Toast.LENGTH_SHORT).show()
                redesLeidas = null  // read again: now it is saved
                pintar()
            } else MaterialAlertDialogBuilder(this)
                .setTitle(if (conectar) "No se pudo conectar" else "No se guardó")
                .setMessage(fallo)
                .setPositiveButton("Vale", null)
                .show()
        }
    }

    private fun verQr() {
        val bmp = Qr.dibujar(l.texto)
        if (bmp == null) { Toast.makeText(this, "Es demasiado largo para un código QR.", Toast.LENGTH_LONG).show(); return }
        val img = ImageView(this).apply { setImageBitmap(bmp); adjustViewBounds = true; setPadding(ui.px(16), ui.px(16), ui.px(16), 0) }
        MaterialAlertDialogBuilder(this)
            .setView(FrameLayout(this).apply { addView(img) })
            .setPositiveButton("Cerrar", null)
            .setNeutralButton("Compartir imagen") { _, _ -> Qr.compartir(this, bmp) }
            .show()
    }

    private fun fechaEvento(v: String): String {
        val t = Contenido.fechaEvento(v) ?: return v
        val p = if (Contenido.esTodoElDia(v)) "EEEE d 'de' MMMM 'de' yyyy" else "EEEE d 'de' MMMM 'de' yyyy, HH:mm"
        return java.text.SimpleDateFormat(p, java.util.Locale.forLanguageTag("es-ES")).format(java.util.Date(t))
    }

    companion object {
        /** [copiado] = what «Copiar al portapapeles» copied when it was read (shows «Copiada al leer» on that datum). */
        fun intent(ctx: Context, l: Lectura, copiado: String? = null): Intent = Intent(ctx, ResultadoActivity::class.java)
            .putExtra("id", l.id).putExtra("fecha", l.fecha).putExtra("hora", l.hora).putExtra("zona", l.zona)
            .putExtra("formato", l.formato).putExtra("texto", l.texto).putExtra("notas", l.notas).putExtra("favorito", l.favorito)
            .putExtra("fechaUtc", l.fechaUtc).putExtra("horaUtc", l.horaUtc).putExtra("metadata", l.metadata).putExtra("origen", l.origen)
            .apply { if (copiado != null) putExtra("copiado", copiado) }

        private fun desdeIntent(i: Intent): Lectura? {
            val texto = i.getStringExtra("texto") ?: return null
            return Lectura(id = i.getLongExtra("id", 0), fecha = i.getStringExtra("fecha") ?: "", hora = i.getStringExtra("hora") ?: "",
                zona = i.getStringExtra("zona") ?: "", formato = i.getStringExtra("formato") ?: "", texto = texto,
                notas = i.getStringExtra("notas") ?: "", favorito = i.getBooleanExtra("favorito", false),
                fechaUtc = i.getStringExtra("fechaUtc") ?: "", horaUtc = i.getStringExtra("horaUtc") ?: "",
                metadata = i.getStringExtra("metadata") ?: "", origen = i.getStringExtra("origen") ?: "")
        }

        /** "Hoy, 10:02:30" / "Ayer, …" / "5 nov 2023, 10:02:30" in the local time it was read. */
        fun fechaBonita(l: Lectura): String {
            val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
            val ayer = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(System.currentTimeMillis() - 86_400_000))
            val dia = Historial.tituloDia(l.fecha, hoy, ayer)
            return "$dia, ${l.hora}"
        }

        /** "Hoy 08:31", "Ayer 18:03", "14/11/2026 20:45" for the header. */
        fun fechaCorta(l: Lectura): String {
            val hoy = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date())
            val ayer = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(java.util.Date(System.currentTimeMillis() - 86_400_000))
            val dia = when (l.fecha) {
                hoy -> "Hoy"; ayer -> "Ayer"
                else -> l.fecha.split('-').let { if (it.size == 3) "${it[2]}/${it[1]}/${it[0]}" else l.fecha }
            }
            return "$dia ${l.hora.take(5)}"
        }

        /** Icon for a read: special labels first, then the content type; plain text from a 1D barcode gets the bars. */
        fun icono(c: Contenido, e: Especial? = null, formato: String = "QR Code") = when (e) {
            is Especial.Equipo -> R.drawable.ic_equipo
            is Especial.Sim -> R.drawable.ic_sim
            is Especial.Articulo -> R.drawable.ic_producto
            null -> when (c) {
                is Contenido.Enlace -> R.drawable.ic_enlace
                is Contenido.Wifi -> R.drawable.ic_wifi
                is Contenido.Telefono -> R.drawable.ic_llamar
                is Contenido.Email -> R.drawable.ic_correo
                is Contenido.Sms -> R.drawable.ic_sms
                is Contenido.Geo -> R.drawable.ic_mapa
                is Contenido.Contacto -> R.drawable.ic_contacto
                is Contenido.Evento -> R.drawable.ic_calendario
                is Contenido.Producto -> R.drawable.ic_barras
                is Contenido.Texto -> if (Formatos.esMatriz(formato)) R.drawable.ic_qr else R.drawable.ic_barras
            }
        }

        fun editarNotas(ctx: Context, actuales: String, listo: (String) -> Unit) {
            val e = TextInputEditText(ctx).apply {
                setText(actuales); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                minLines = 2
            }
            val caja = TextInputLayout(ctx).apply { hint = "Notas"; addView(e) }
            val d = ctx.resources.displayMetrics.density
            val marco = FrameLayout(ctx).apply { setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0); addView(caja) }
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Notas")
                .setView(marco)
                .setPositiveButton("Guardar") { _, _ -> listo(e.text?.toString()?.trim() ?: "") }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}
