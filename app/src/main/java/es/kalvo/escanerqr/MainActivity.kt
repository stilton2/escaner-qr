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
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Resumenes
import es.kalvo.escanerqr.wifis.PanelWifis
import zxingcpp.BarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Tabs: Escanear (camera), Historial and Ajustes like QR Bot, plus Wifis (what was the «Mis wifis» app until v2.0).
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
    private val lector: BarcodeReader by lazy { Lector.paraCamara() }
    @Volatile private var pausado = false
    private var pendiente: Leido? = null
    private var ignorarTexto: String? = null
    private var ignorarHasta = 0L

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
        contenedor.addView(vistaEscaner, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(historial.vista, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(ajustesPanel.vista, MATCH_PARENT, MATCH_PARENT)
        contenedor.addView(wifis.vista, MATCH_PARENT, MATCH_PARENT)
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
        if (id == 1) arrancarCamara() else pararCamara()
        if (id == 2) historial.refrescar()
        if (id == 3) ajustesPanel.refrescar()
        if (id == 4) wifis.mostrar() else wifis.ocultar()
    }

    fun irAHistorial() { nav.elegir(2) }
    fun irAEscanear() { nav.elegir(1) }

    override fun onResume() {
        super.onResume()
        // Coming back from the result: do not read again at once the same code that is still in front of the camera.
        pendiente?.let { ignorarTexto = it.texto; ignorarHasta = SystemClock.elapsedRealtime() + 2500 }
        pendiente = null
        pausado = false
        tarjetaManual.visibility = View.GONE
        if (actual == 1) arrancarCamara()
        if (actual == 2) historial.refrescar()
        if (actual == 4) wifis.mostrar()
    }

    override fun onDestroy() {
        super.onDestroy()
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
        f.addView(Visor(this), MATCH_PARENT, MATCH_PARENT)

        // Pinch to zoom, tap to focus.
        val pellizco = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val c = camara ?: return true
                val z = c.cameraInfo.zoomState.value ?: return true
                c.cameraControl.setZoomRatio((z.zoomRatio * d.scaleFactor).coerceIn(z.minZoomRatio, z.maxZoomRatio))
                return true
            }
        })
        preview.setOnTouchListener { _, e ->
            pellizco.onTouchEvent(e)
            if (e.action == MotionEvent.ACTION_UP && !pellizco.isInProgress) {
                camara?.let { c ->
                    val p = preview.meteringPointFactory.createPoint(e.x, e.y)
                    c.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(p).build())
                }
            }
            true
        }

        // Top: picture from the gallery, torch, switch camera.
        botonesArriba = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(ui.px(12), ui.px(12), ui.px(12), ui.px(12))
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
            text = "Apunta a un código QR o de barras"
            setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 0f, Color.BLACK)
        }
        abajo.addView(aviso, MATCH_PARENT, WRAP_CONTENT)
        zoom = Slider(this).apply {
            valueFrom = 0f; valueTo = 1f; value = 0f
            contentDescription = "Zoom"
            setLabelFormatter { "Zoom" }
            addOnChangeListener { _, v, desdeUsuario -> if (desdeUsuario) camara?.cameraControl?.setLinearZoom(v) }
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
    }

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
        p.unbindAll()
        val vista = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
        val analisis = ImageAnalysis.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(1920, 1080), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)).build())
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analisis.setAnalyzer(ejecutor) { img -> analizar(img) }
        val sel = if (aj.frontal) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            camara = p.bindToLifecycle(this, sel, vista, analisis)
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
            if (pausado) return
            val r = lector.read(img).firstNotNullOfOrNull { Lector.convertir(it) } ?: return
            runOnUiThread { detectado(r) }
        } catch (e: Exception) {
            // A bad frame: try the next one.
        } finally {
            img.close()
        }
    }

    private fun detectado(r: Leido) {
        if (pausado || actual != 1 || isFinishing) return
        val ahora = SystemClock.elapsedRealtime()
        if (r.texto == ignorarTexto && ahora < ignorarHasta) return
        when {
            aj.manual -> {
                pausado = true; pendiente = r
                textoManual.text = r.texto
                tarjetaManual.visibility = View.VISIBLE
            }
            aj.continua -> {
                // Same code again within 3 s: it is still in front of the camera, not a new one.
                ignorarTexto = r.texto; ignorarHasta = ahora + 3000
                Procesar.nuevo(this, r, desdeCamara = true, continua = true)
                val res = Resumenes.de(r.texto, r.formato)
                aviso.text = "Guardado (${res.tipo}): ${res.principal.take(60)}"
            }
            else -> {
                pausado = true; pendiente = r
                Procesar.nuevo(this, r, desdeCamara = true)
            }
        }
    }

    // ---------------- imagen ----------------

    private fun leerImagen(uri: Uri) {
        Toast.makeText(this, "Buscando códigos en la imagen…", Toast.LENGTH_SHORT).show()
        Thread {
            val res = try { Result.success(Lector.deImagen(this, uri)) } catch (e: Exception) { Result.failure(e) }
            runOnUiThread {
                val lista = res.getOrNull()
                when {
                    lista == null -> Toast.makeText(this, "No he podido abrir la imagen.", Toast.LENGTH_LONG).show()
                    lista.isEmpty() -> MaterialAlertDialogBuilder(this)
                        .setTitle("Ningún código")
                        .setMessage("No he encontrado ningún código en la imagen. Prueba con una foto más nítida o más cerca.")
                        .setPositiveButton("Vale", null).show()
                    lista.size == 1 -> Procesar.nuevo(this, lista[0], desdeCamara = false)
                    else -> MaterialAlertDialogBuilder(this)
                        .setTitle("Hay ${lista.size} códigos: elige uno")
                        .setItems(lista.map { "${it.formato}: ${it.texto.lineSequence().first().take(50)}" }.toTypedArray()) { _, i ->
                            Procesar.nuevo(this, lista[i], desdeCamara = false)
                        }
                        .setNegativeButton("Cancelar", null).show()
                }
            }
        }.start()
    }

    /** Corners of the viewfinder (only a guide: the whole picture is read). */
    private class Visor(ctx: Context) : View(ctx) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 4 * ctx.resources.displayMetrics.density; strokeCap = Paint.Cap.ROUND
        }
        override fun onDraw(c: Canvas) {
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
