package es.kalvo.escanerqr

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import es.kalvo.escanerqr.nucleo.Cerca
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Decision
import es.kalvo.escanerqr.nucleo.Eleccion
import es.kalvo.escanerqr.nucleo.Hallado
import es.kalvo.escanerqr.nucleo.LuzAuto
import es.kalvo.escanerqr.nucleo.ZoomAuto
import es.kalvo.escanerqr.nucleo.Resumenes
import es.kalvo.escanerqr.wifis.PanelWifis
import android.util.Log
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tabs: Escanear (camera), Historial, Crear (S-J9) and Ajustes like QR Bot, plus Wifis (what was the «Mis wifis» app until v2.0).
 * Since v2.2 (S-J4) the tab bar is at the top, «estilo A».
 */
class MainActivity : AppCompatActivity() {
    private lateinit var ui: Ui
    private lateinit var contenedor: FrameLayout
    private lateinit var nav: Pestanas
    private lateinit var vistaEscaner: FrameLayout
    private lateinit var historial: PanelHistorial
    private lateinit var ajustesPanel: PanelAjustes
    private lateinit var wifis: PanelWifis
    private lateinit var crear: PanelCrear
    private val aj by lazy { Ajustes(this) }

    // Camera
    private lateinit var preview: PreviewView
    private lateinit var sinPermiso: LinearLayout
    private lateinit var aviso: TextView
    private lateinit var tarjetaManual: MaterialCardView
    private lateinit var textoManual: TextView
    private lateinit var btnLinterna: MaterialButton
    private lateinit var zoom: Slider
    private var proveedor: ProcessCameraProvider? = null
    private var camara: Camera? = null
    private var linterna = false
    private val ejecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val lectorCam = LectorCamara()
    @Volatile private var pausado = false
    private var pendiente: Leido? = null
    /** Codes just read that are still in front of the camera: not again until [ignorarHasta]. */
    private var ignorados: Set<String> = emptySet()
    private var ignorarHasta = 0L
    // S-J6: automatic zoom, torch, «too close» hint, several codes.
    private lateinit var marcas: Marcas
    private lateinit var visor: Visor
    private var zoomBase: Float? = null
    private var zoomDelUsuario = false
    private var ultimoZoomAuto = 0L
    private var ultimoEnfoque = 0L
    private var ultimoVisto = 0L
    private var avisoHasta = 0L
    private val luz = LuzAuto()
    private var estadistica = Estadistica()
    @Volatile private var haciendoFoto = false

    private val pedirCamara = registerForActivityResult(ActivityResultContracts.RequestPermission()) { si ->
        if (si) { sinPermiso.visibility = View.GONE; enlazarCamara() } else mostrarSinPermiso()
    }
    private val elegirImagen = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) leerImagen(uri)
    }
    val crearCsv = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { historial.exportarA(it, 1) } }
    val crearJson = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let { historial.exportarA(it, 2) } }
    val crearTxt = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> uri?.let { historial.exportarA(it, 3) } }
    val abrirCsv = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { historial.importarDe(it) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.ventana(this)
        val raiz = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        ui = Ui(this, raiz)
        raiz.setBackgroundColor(ui.cFondo)
        // Tabs at the top (S-J4), teal under the status bar too.
        nav = Pestanas(ui) { mostrar(it) }
        nav.anadir(1, "Escanear", R.drawable.ic_escanear)
        nav.anadir(2, "Historial", R.drawable.ic_historial)
        nav.anadir(5, "Crear", R.drawable.ic_crear)  // S-J9
        nav.anadir(4, "Wifis", R.drawable.ic_wifi)
        nav.anadir(3, "Ajustes", R.drawable.ic_ajustes)
        raiz.addView(nav.vista, MATCH_PARENT, WRAP_CONTENT)
        contenedor = FrameLayout(this)
        raiz.addView(contenedor, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        setContentView(raiz)

        vistaEscaner = construirEscaner()
        historial = PanelHistorial(this)
        ajustesPanel = PanelAjustes(this)
        wifis = PanelWifis(this)
        crear = PanelCrear(this)
        contenedor.addView(vistaEscaner, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(historial.vista, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(ajustesPanel.vista, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(wifis.vista, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(crear.vista, MATCH_PARENT, MATCH_PARENT)
        // Edge to edge: the tab bar goes under the status bar; the content stops above the gesture bar.
        ViewCompat.setOnApplyWindowInsetsListener(raiz) { _, ins ->
            val b = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            nav.margenArriba(b.top)
            contenedor.setPadding(b.left, 0, b.right, b.bottom)
            ins
        }
        historial.margenArriba(0); ajustesPanel.margenArriba(0); wifis.margenArriba(0)
        nav.elegir(1)
        recibir(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        recibir(intent)
    }

    /** A picture shared from another app ("Compartir" → Escáner QR). */
    private fun recibir(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        @Suppress("DEPRECATION")
        val uri = (if (android.os.Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else i.getParcelableExtra(Intent.EXTRA_STREAM)) ?: return
        i.action = null
        leerImagen(uri)
    }

    private var actual = 0
    private fun mostrar(id: Int) {
        actual = id
        vistaEscaner.visibility = if (id == 1) View.VISIBLE else View.GONE
        historial.vista.visibility = if (id == 2) View.VISIBLE else View.GONE
        ajustesPanel.vista.visibility = if (id == 3) View.VISIBLE else View.GONE
        wifis.vista.visibility = if (id == 4) View.VISIBLE else View.GONE
        crear.vista.visibility = if (id == 5) View.VISIBLE else View.GONE
        if (id == 1) { visor.mira = aj.soloMira; arrancarCamara() } else pararCamara()
        if (id == 2) historial.refrescar()
        if (id == 3) ajustesPanel.refrescar()
        if (id == 4) wifis.mostrar() else wifis.ocultar()
    }

    fun irAHistorial() { nav.elegir(2) }
    fun irAEscanear() { nav.elegir(1) }

    override fun onResume() {
        super.onResume()
        // Coming back from the result: do not read again at once the same code that is still in front of the camera.
        pendiente?.let { ignorados = ignorados + it.texto; ignorarHasta = SystemClock.elapsedRealtime() + 2500 }
        pendiente = null
        pausado = false
        tarjetaManual.visibility = View.GONE
        if (::marcas.isInitialized) marcas.ocultar()
        volverZoom()
        if (::visor.isInitialized) visor.mira = aj.soloMira
        if (actual == 1) arrancarCamara()
        if (actual == 2) historial.refrescar()
        if (actual == 4) wifis.mostrar()
    }

    override fun onDestroy() {
        super.onDestroy()
        ejecutor.execute { lectorCam.cerrar() }
        ejecutor.shutdown()
        if (::wifis.isInitialized) wifis.cerrar()
    }

    // ---------------- cámara ----------------

    private var botonesArriba: LinearLayout? = null

    @SuppressLint("ClickableViewAccessibility")
    private fun construirEscaner(): FrameLayout {
        val f = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        preview = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
        f.addView(preview, MATCH_PARENT, MATCH_PARENT)
        visor = Visor(this).apply { mira = aj.soloMira }
        f.addView(visor, MATCH_PARENT, MATCH_PARENT)

        // Pinch to zoom, tap to focus.
        val pellizco = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val c = camara ?: return true
                val z = c.cameraInfo.zoomState.value ?: return true
                c.cameraControl.setZoomRatio((z.zoomRatio * d.scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                zoomDelUsuario = true; zoomBase = null
                return true
            }
        })
        preview.setOnTouchListener { _, e ->
            pellizco.onTouchEvent(e)
            if (e.action == MotionEvent.ACTION_UP && !pellizco.isInProgress) {
                camara?.let { c ->
                    val p = preview.meteringPointFactory.createPoint(e.x, e.y)
                    val fut = c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                    // S-J6: if it cannot focus there, it is usually too close.
                    fut.addListener({
                        val ok = try { fut.get().isFocusSuccessful } catch (x: Exception) { true }
                        if (!ok) avisoTemporal("No enfoca: si está muy cerca, aléjate un poco y usa el zoom")
                    }, ContextCompat.getMainExecutor(this))
                }
            }
            true
        }

        // Top: picture from the gallery, torch, switch camera.
        botonesArriba = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(ui.px(12), ui.px(12), ui.px(12), ui.px(12))
            addView(botonRedondo(R.drawable.ic_foto, "Foto: leer con una foto a máxima resolución") { tomarFoto() })
            addView(botonRedondo(R.drawable.ic_galeria, "Leer desde una imagen") { elegirImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
            btnLinterna = botonRedondo(R.drawable.ic_linterna, "Linterna") { ponerLinterna(!linterna) }
            addView(btnLinterna)
            addView(botonRedondo(R.drawable.ic_camara_giro, "Cambiar de cámara") { aj.frontal = !aj.frontal; enlazarCamara() })
        }
        f.addView(botonesArriba, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.TOP))

        // Bottom: hint, zoom.
        val abajo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.px(24), ui.px(8), ui.px(24), ui.px(16))
        }
        aviso = TextView(this).apply {
            text = TEXTO_AVISO
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        abajo.addView(aviso, MATCH_PARENT, WRAP_CONTENT)
        zoom = Slider(this).apply {
            valueFrom = 0f; valueTo = 1f; value = 0f
            contentDescription = "Zoom"
            setLabelFormatter { "Zoom" }
            addOnChangeListener { _, v, desdeUsuario -> if (desdeUsuario) { camara?.cameraControl?.setLinearZoom(v); zoomDelUsuario = true; zoomBase = null } }
        }
        abajo.addView(zoom, MATCH_PARENT, WRAP_CONTENT)
        f.addView(abajo, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM))

        // «Confirmar a mano»: what was found, and you decide.
        tarjetaManual = MaterialCardView(this).apply {
            radius = ui.px(16).toFloat(); visibility = View.GONE; cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(ui.cSuperficie)
            val dentro = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(16), ui.px(14), ui.px(16), ui.px(10)) }
            dentro.addView(ui.texto("Código encontrado", 13f, ui.cMuted))
            textoManual = ui.texto("", 18f).apply { maxLines = 3 }
            dentro.addView(textoManual)
            val fila = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, ui.px(10), 0, 0) }
            fila.addView(ui.boton("Seguir buscando", contorno = true) { tarjetaManual.visibility = View.GONE; pausado = false; pendiente = null },
                LinearLayout.LayoutParams(0, ui.px(48), 1f).apply { marginEnd = ui.px(8) })
            fila.addView(ui.boton("Abrir") {
                tarjetaManual.visibility = View.GONE
                pendiente?.let { Procesar.nuevo(this@MainActivity, it, desdeCamara = true) }
            }, LinearLayout.LayoutParams(0, ui.px(48), 1f))
            dentro.addView(fila)
            addView(dentro)
        }
        f.addView(tarjetaManual, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(ui.px(16), 0, ui.px(16), ui.px(96))
        })

        // No permission yet.
        sinPermiso = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; visibility = View.GONE
            setPadding(ui.px(32), 0, ui.px(32), 0)
            addView(TextView(context).apply {
                text = "Para leer con la cámara, la app necesita permiso de cámara. Las fotos no se guardan ni salen del móvil."
                setTextColor(Color.WHITE); textSize = 16f; gravity = Gravity.CENTER
            })
            addView(ui.botonPrincipal("Dar permiso de cámara") { pedirCamara.launch(Manifest.permission.CAMERA) },
                LinearLayout.LayoutParams(MATCH_PARENT, ui.px(56)).apply { topMargin = ui.px(20) })
            addView(ui.botonSecundario("Leer desde una imagen", R.drawable.ic_galeria, 56) {
                elegirImagen.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.apply { setTextColor(Color.WHITE); iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE) },
                LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.px(10) })
        }
        // S-J6: several codes at once: frozen picture with a box on each.
        marcas = Marcas(this, ui,
            elegido = { h -> marcas.ocultar(); val l = Leido(h.texto, h.formato, h.metadata); pendiente = l; Procesar.nuevo(this, l, desdeCamara = true) },
            todos = { l -> guardarTodos(l) },
            seguir = { l ->
                // «Seguir buscando»: none of these; leave them alone 3 s so you can aim somewhere else.
                marcas.ocultar(); ignorados = l.map { it.texto }.toSet(); ignorarHasta = SystemClock.elapsedRealtime() + 3000; pausado = false
            })
        f.addView(marcas.vista, MATCH_PARENT, MATCH_PARENT)

        f.addView(sinPermiso, MATCH_PARENT, MATCH_PARENT)
        return f
    }

    private fun botonRedondo(icono: Int, descripcion: String, f: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonFilledStyle).apply {
            setIconResource(icono); contentDescription = descripcion
            // Dark round button over the camera picture, white icon (readable on any background).
            backgroundTintList = android.content.res.ColorStateList.valueOf(0x99000000.toInt())
            iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE)
            minWidth = ui.px(48); minHeight = ui.px(48)
            setOnClickListener { f() }
            layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = ui.px(8) }
        }

    private fun tienePermiso() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun mostrarSinPermiso() { sinPermiso.visibility = View.VISIBLE }

    private var pedido = false
    private fun arrancarCamara() {
        if (!tienePermiso()) {
            if (!pedido) { pedido = true; pedirCamara.launch(Manifest.permission.CAMERA) } else mostrarSinPermiso()
            return
        }
        sinPermiso.visibility = View.GONE
        enlazarCamara()
    }

    private fun pararCamara() {
        proveedor?.unbindAll()
        camara = null
        linterna = false
        zoomBase = null
        luz.reiniciar()
    }

    private var intentosVista = 0

    private fun enlazarCamara() {
        if (!tienePermiso() || actual != 1) return
        val p = proveedor
        if (p == null) {
            val futuro = ProcessCameraProvider.getInstance(this)
            futuro.addListener({
                proveedor = futuro.get()
                enlazarCamara()
            }, ContextCompat.getMainExecutor(this))
            return
        }
        // The viewport (what the screen shows) exists once the view is laid out; wait for it a moment.
        val puerto = preview.viewPort
        if (puerto == null && intentosVista++ < 5) { preview.post { enlazarCamara() }; return }
        intentosVista = 0
        p.unbindAll()
        zoomDelUsuario = false; zoomBase = null; luz.reiniciar()
        // S-J6: 4:3 like the sensor (more picture than 16:9) and more detail for small and far codes.
        val cuatroTercios = AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY
        val vista = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(cuatroTercios).build())
            .build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        val analisis = ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(cuatroTercios).setResolutionStrategy(
                ResolutionStrategy(ANALISIS, ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analisis.setAnalyzer(ejecutor) { img -> analizar(img) }
        val sel = if (aj.frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            // Same viewport for both: the frame read is exactly what you see, so the boxes fall on the codes.
            val grupo = UseCaseGroup.Builder().addUseCase(vista).addUseCase(analisis).apply { puerto?.let { setViewPort(it) } }.build()
            camara = p.bindToLifecycle(this, sel, grupo)
        } catch (e: Exception) {
            if (aj.frontal) { aj.frontal = false; enlazarCamara(); return }
            Toast.makeText(this, "No puedo abrir la cámara: ${e.message}", Toast.LENGTH_LONG).show()
            return
        }
        camara?.cameraInfo?.zoomState?.observe(this) { z -> zoom.value = z.linearZoom.coerceIn(0f, 1f) }
        val conFlash = camara?.cameraInfo?.hasFlashUnit() == true
        btnLinterna.visibility = if (conFlash) View.VISIBLE else View.GONE
        if (conFlash && aj.linternaAlAbrir) ponerLinterna(true) else ponerLinterna(false)
    }

    private fun ponerLinterna(on: Boolean) {
        val c = camara ?: return
        if (!c.cameraInfo.hasFlashUnit()) return
        linterna = on
        c.cameraControl.enableTorch(on)
        btnLinterna.alpha = if (on) 1f else 0.75f
        btnLinterna.contentDescription = if (on) "Apagar linterna" else "Encender linterna"
    }

    /** Runs on the camera thread. */
    private fun analizar(img: ImageProxy) {
        try {
            if (pausado || haciendoFoto) return
            val f = lectorCam.leer(img)
            estadistica.apuntar(img.width, img.height, img.cropRect.width(), img.cropRect.height(), f)
            runOnUiThread { fotograma(f) }
        } catch (e: Exception) {
            // A bad frame: try the next one.
        } finally {
            img.close()
        }
    }

    /** What to do with one frame read (main thread). */
    private fun fotograma(f: Fotograma) {
        if (pausado || actual != 1 || isFinishing) return
        val ahora = SystemClock.elapsedRealtime()
        if (aj.linternaAuto && !linterna && luz.ver(f.brillo, ahora)) {
            ponerLinterna(true); avisoTemporal("Poca luz: linterna encendida")
        }
        val hallados = if (ahora < ignorarHasta) f.hallados.filter { it.texto !in ignorados } else f.hallados
        if (hallados.isEmpty()) {
            if (f.errados.isNotEmpty()) { ultimoVisto = ahora; codigoSinLeer(f, ahora) }
            // Nothing seen for a while: back to the zoom you had.
            if (zoomBase != null && ahora - ultimoVisto > 5000) volverZoom()
            if (ahora > avisoHasta && aviso.text != TEXTO_AVISO && !aj.continua) aviso.text = TEXTO_AVISO
            return
        }
        when (val d = Eleccion.decidir(hallados, aj.soloMira, aj.preferido)) {
            is Decision.Nada -> {}
            is Decision.Uno -> detectado(aLeido(d.h))
            is Decision.Varios -> if (aj.continua) d.lista.forEach { detectado(aLeido(it)) } else congelar(d.lista)
        }
    }

    private fun aLeido(h: Hallado) = Leido(h.texto, h.formato, h.metadata)

    /** A code that was seen but not read: zoom in if it is small, warn if it is too close, focus on it. */
    private fun codigoSinLeer(f: Fotograma, ahora: Long) {
        val c = camara ?: return
        val grande = f.errados.maxByOrNull { it.lado(f.aspecto) } ?: return
        val lado = grande.lado(f.aspecto)
        if (Cerca.demasiado(lado)) { avisoTemporal("Aléjate un poco y usa el zoom"); return }
        if (ahora - ultimoEnfoque > 1500 && preview.width > 0) {
            ultimoEnfoque = ahora
            val p = preview.meteringPointFactory.createPoint(grande.cx * preview.width, grande.cy * preview.height)
            c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(p).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build())
        }
        if (!aj.zoomAuto || zoomDelUsuario || aj.frontal) return
        val z = c.cameraInfo.zoomState.value ?: return
        val nuevo = ZoomAuto.siguiente(lado, grande.cx, grande.cy, z.zoomRatio, z.maxZoomRatio, ahora - ultimoZoomAuto, aj.zoomAuto) ?: return
        if (zoomBase == null) zoomBase = z.zoomRatio
        ultimoZoomAuto = ahora
        c.cameraControl.setZoomRatio(nuevo)
        avisoTemporal("Acercando…")
    }

    private fun volverZoom() {
        val b = zoomBase ?: return
        zoomBase = null
        camara?.cameraControl?.setZoomRatio(b)
    }

    private fun avisoTemporal(s: String) {
        aviso.text = s
        avisoHasta = SystemClock.elapsedRealtime() + 2500
    }

    /** Several codes: stop the picture and draw a box on each. */
    private fun congelar(l: List<Hallado>) {
        pausado = true
        val foto = try { preview.bitmap } catch (e: Exception) { null }
        marcas.mostrar(foto, l)
        Procesar.avisar(this)
    }

    /** «Guardar todos»: all of them to the history, without opening any; then keep scanning. */
    private fun guardarTodos(l: List<Hallado>) {
        marcas.ocultar()
        var n = 0
        for (h in l) { Procesar.guardar(this, aLeido(h)); n++ }
        ignorados = l.map { it.texto }.toSet(); ignorarHasta = SystemClock.elapsedRealtime() + 4000
        Toast.makeText(this, "Guardados $n códigos en el historial", Toast.LENGTH_SHORT).show()
        pausado = false
    }

    private fun detectado(r: Leido) {
        if (pausado || actual != 1 || isFinishing) return
        val ahora = SystemClock.elapsedRealtime()
        if (r.texto in ignorados && ahora < ignorarHasta) return
        when {
            aj.manual -> {
                pausado = true; pendiente = r
                textoManual.text = r.texto
                tarjetaManual.visibility = View.VISIBLE
            }
            aj.continua -> {
                // Same code again within 3 s: it is still in front of the camera, not a new one.
                ignorados = (if (ahora < ignorarHasta) ignorados else emptySet()) + r.texto; ignorarHasta = ahora + 3000
                Procesar.nuevo(this, r, desdeCamara = true, continua = true)
                val res = Resumenes.de(r.texto, r.formato)
                aviso.text = "Guardado (${res.tipo}): ${res.principal.take(60)}"
                avisoHasta = ahora + 2500
            }
            else -> {
                pausado = true; pendiente = r
                Procesar.nuevo(this, r, desdeCamara = true)
            }
        }
    }

    // ---------------- foto a máxima resolución (S-J6) ----------------

    /** «Foto»: one photo at the camera's highest resolution, read with every pass (hard cases: tiny, far, worn). */
    private fun tomarFoto() {
        val p = proveedor ?: return
        if (camara == null || haciendoFoto) return
        haciendoFoto = true
        aviso.text = "Haciendo la foto… no muevas el móvil"
        avisoHasta = SystemClock.elapsedRealtime() + 60000
        val captura = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY).build())
            .build()
        val sel = if (aj.frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            // Analysis off for a moment (three streams at full size do not fit in every phone).
            p.unbindAll()
            val vista = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            camara = p.bindToLifecycle(this, sel, vista, captura)
            if (linterna) camara?.cameraControl?.enableTorch(true)
        } catch (e: Exception) {
            haciendoFoto = false; aviso.text = TEXTO_AVISO
            Toast.makeText(this, "No puedo hacer la foto: ${e.message}", Toast.LENGTH_LONG).show()
            enlazarCamara(); return
        }
        captura.takePicture(ejecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(img: ImageProxy) {
                val lista = try {
                    val buf = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
                    img.close()
                    runOnUiThread { aviso.text = "Buscando códigos en la foto…"; enlazarCamaraTrasFoto() }
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    Lector.deBitmap(bmp).also { bmp.recycle() }
                } catch (e: Throwable) { try { img.close() } catch (x: Exception) {}; null }
                runOnUiThread { haciendoFoto = false; avisoHasta = 0; aviso.text = TEXTO_AVISO; mostrarLista(lista, "la foto") }
            }

            override fun onError(e: ImageCaptureException) {
                runOnUiThread {
                    haciendoFoto = false; avisoHasta = 0; aviso.text = TEXTO_AVISO
                    Toast.makeText(this@MainActivity, "No se pudo hacer la foto", Toast.LENGTH_LONG).show()
                    enlazarCamaraTrasFoto()
                }
            }
        })
    }

    private fun enlazarCamaraTrasFoto() {
        val l = linterna
        enlazarCamara()
        if (l) ponerLinterna(true)
    }

    // ---------------- imagen ----------------

    private fun leerImagen(uri: Uri) {
        Toast.makeText(this, "Buscando códigos en la imagen…", Toast.LENGTH_SHORT).show()
        Thread {
            val lista = try { Lector.deImagen(this, uri) } catch (e: Exception) { null }
            runOnUiThread { mostrarLista(lista, "la imagen") }
        }.start()
    }

    /** What a picture or photo gave: nothing, one (open it) or several (choose). */
    private fun mostrarLista(lista: List<Leido>?, donde: String) {
        if (isFinishing) return
        when {
            lista == null -> Toast.makeText(this, "No he podido abrir $donde.", Toast.LENGTH_LONG).show()
            lista.isEmpty() -> MaterialAlertDialogBuilder(this)
                .setTitle("Ningún código")
                .setMessage("No he encontrado ningún código en $donde. Prueba con una foto más nítida o más cerca.")
                .setPositiveButton("Vale", null).show()
            lista.size == 1 -> { pausado = true; pendiente = lista[0]; Procesar.nuevo(this, lista[0], desdeCamara = false) }
            else -> MaterialAlertDialogBuilder(this)
                .setTitle("Hay ${lista.size} códigos: elige uno")
                .setItems(lista.map { "${it.formato}: ${it.texto.lineSequence().first().take(50)}" }.toTypedArray()) { _, i ->
                    pausado = true; pendiente = lista[i]
                    Procesar.nuevo(this, lista[i], desdeCamara = false)
                }
                .setNegativeButton("Cancelar", null).show()
        }
    }

    /** Frame size and time per frame in the log, every 60 frames (numbers only: `adb logcat -s EscanerQR`). */
    private class Estadistica {
        private var n = 0; private var suma = 0L; private var max = 0L
        private val porPasada = HashMap<String, Int>()
        fun apuntar(w: Int, h: Int, cw: Int, ch: Int, f: Fotograma) {
            n++; suma += f.ms; if (f.ms > max) max = f.ms
            if (f.pasada.isNotEmpty()) porPasada[f.pasada] = (porPasada[f.pasada] ?: 0) + 1
            if (n == 60) {
                Log.i("EscanerQR", "camara ${w}x$h vista ${cw}x$ch ms media ${suma / n} max $max leidos_por_pasada $porPasada")
                n = 0; suma = 0; max = 0; porPasada.clear()
            }
        }
    }

    companion object {
        /** Analysis size asked for (4:3, CameraX takes the closest the phone gives); see docs § v2.5 for the measures. */
        val ANALISIS = Size(2560, 1920)
        const val TEXTO_AVISO = "Apunta a un código QR o de barras"
    }

    /** Corners of the viewfinder (only a guide: the whole picture is read). */
    private class Visor(ctx: Context) : View(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4 * d; strokeCap = Paint.Cap.ROUND
        }
        private val pm = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(ctx, R.color.ambar); style = Paint.Style.STROKE; strokeWidth = 3 * d; strokeCap = Paint.Cap.ROUND
            setShadowLayer(4 * d, 0f, 0f, Color.BLACK)
        }
        /** S-J6 «Leer solo lo que está en el centro»: crosshair in the exact middle (what is read). */
        var mira = false
            set(v) { field = v; invalidate() }

        override fun onDraw(c: Canvas) {
            if (mira) {
                val cx = width / 2f; val cy = height / 2f; val r = 14 * d; val l = 26 * d
                c.drawCircle(cx, cy, r, pm)
                c.drawLine(cx - l, cy, cx - r / 2, cy, pm); c.drawLine(cx + r / 2, cy, cx + l, cy, pm)
                c.drawLine(cx, cy - l, cx, cy - r / 2, pm); c.drawLine(cx, cy + r / 2, cx, cy + l, pm)
            }
            val lado = minOf(width, height) * 0.62f
            val r = RectF((width - lado) / 2, (height - lado) / 2.4f, (width + lado) / 2, (height - lado) / 2.4f + lado)
            val t = lado * 0.16f
            c.drawLine(r.left, r.top, r.left + t, r.top, p); c.drawLine(r.left, r.top, r.left, r.top + t, p)
            c.drawLine(r.right, r.top, r.right - t, r.top, p); c.drawLine(r.right, r.top, r.right, r.top + t, p)
            c.drawLine(r.left, r.bottom, r.left + t, r.bottom, p); c.drawLine(r.left, r.bottom, r.left, r.bottom - t, p)
            c.drawLine(r.right, r.bottom, r.right - t, r.bottom, p); c.drawLine(r.right, r.bottom, r.right, r.bottom - t, p)
        }
    }
}
