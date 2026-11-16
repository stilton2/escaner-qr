package es.kalvo.escanerqr.nucleo

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How a picture is read (S-J6): one or more «pasadas», each on a part of the picture, shrunk or enlarged, mirrored or not,
 * with one way or another of telling black from white. Pure Kotlin (tested); `Lector` runs them with zxing-cpp.
 *
 * - [fraccion]: 1 = whole picture, 0.5 = the middle half of each side.
 * - [escala]: 1 = as it is, 2/3 = enlarged (small codes far away), -2 = halved (big pictures, quicker).
 * - [suave]: enlarge with smoothing (bilinear) instead of copying pixels.
 * - [histograma]: the other black/white method of zxing-cpp (GlobalHistogram), for reflections and uneven light.
 * - [espejo]: mirrored (Micro QR, rMQR, PDF417… seen backwards; QR, Data Matrix and Aztec the engine already reads mirrored).
 * - [limpiar]: zxing-cpp «tryDenoise» (fills small holes in worn QR, Data Matrix and Aztec).
 * - [invertir]: light ↔ dark, for white-on-dark barcodes, PDF417 and MaxiCode (the engine only inverts QR, Data Matrix and Aztec).
 */
data class Pasada(
    val nombre: String,
    val fraccion: Float = 1f,
    val escala: Int = 1,
    val suave: Boolean = false,
    val histograma: Boolean = false,
    val espejo: Boolean = false,
    val limpiar: Boolean = false,
    val invertir: Boolean = false,
) {
    /** Where the part read sits in the frame. */
    fun recorte(w: Int, h: Int): Recorte = if (fraccion >= 1f) Recorte(0, 0, w, h) else Recorte.central(w, h, fraccion)

    /** A point of the picture actually read (cut, scaled, maybe mirrored) back to frame pixels. */
    fun aMarco(r: Recorte, x: Float, y: Float): Pair<Float, Float> {
        val f = when { escala > 1 -> 1f / escala; escala < -1 -> (-escala).toFloat(); else -> 1f }
        var px = x * f; val py = y * f
        if (espejo) px = r.ancho - px
        return Pair(r.x + px, r.y + py)
    }
}

object Plan {
    /** Frames with the short side above this are read halved in the first pass (the second one reads the middle at full size). */
    const val LADO_REDUCIR = 1300

    /**
     * Camera, frame number [n] of [w]×[h] (what you see on the screen). The passes run in order and stop at the first that reads:
     * 1. the whole frame (halved if big): big and medium codes anywhere;
     * 2. the middle at full detail (enlarged ×2 if the frame is small): small and far codes where you aim;
     * 3. one of four extras, taking turns frame by frame, so a frame never costs more than three passes.
     */
    fun camara(w: Int, h: Int, n: Long): List<Pasada> {
        val grande = min(w, h) > LADO_REDUCIR
        val entera = Pasada("entera", escala = if (grande) -2 else 1)
        val centro = if (grande) Pasada("centro", fraccion = 0.5f) else Pasada("centro", fraccion = 0.5f, escala = 2)
        val extra = when ((n % 4).toInt()) {
            0 -> Pasada("histograma", escala = if (grande) -2 else 1, histograma = true)
            1 -> Pasada("lejos", fraccion = if (grande) 0.3f else 0.35f, escala = 3, suave = true)
            2 -> Pasada("espejo", escala = if (grande) -2 else 1, espejo = true, limpiar = true)
            else -> Pasada("invertido", escala = if (grande) -2 else 1, invertir = true)
        }
        return listOf(entera, centro, extra)
    }

    /**
     * A picture from the gallery, shared, or the «Foto» button: everything, one after the other, and all that is found counts.
     * Enlarging only for small pictures (a big one is already detailed and would be slow).
     */
    fun imagen(w: Int, h: Int): List<Pasada> {
        val lado = max(w, h)
        val l = mutableListOf(
            Pasada("histograma", histograma = true),
            Pasada("espejo", espejo = true),
            Pasada("limpiar", limpiar = true),
            Pasada("invertido", invertir = true),
        )
        if (lado <= 1600) l += Pasada("x2", escala = 2)
        if (lado <= 1100) l += Pasada("x3", escala = 3, suave = true)
        if (lado > 1600) { l += Pasada("centro x2", fraccion = 0.5f, escala = 2); l += Pasada("centro x3", fraccion = 0.34f, escala = 3, suave = true) }
        return l
    }
}

/** Turning a point of the camera frame (as the sensor gives it) into 0–1 coordinates of the picture as you see it. */
object Orientacion {
    /** [grados] = what CameraX says the frame must turn clockwise to stand upright (0, 90, 180, 270). */
    fun normalizar(x: Float, y: Float, w: Int, h: Int, grados: Int): Pair<Float, Float> = when (((grados % 360) + 360) % 360) {
        90 -> Pair((h - y) / h, x / w)
        180 -> Pair((w - x) / w, (h - y) / h)
        270 -> Pair(y / h, (w - x) / w)
        else -> Pair(x / w, y / h)
    }

    /** Width/height of the upright picture. */
    fun aspecto(w: Int, h: Int, grados: Int): Float = if (grados % 180 != 0) h.toFloat() / w else w.toFloat() / h
}

/** Four corners of a code, 0–1 in the upright picture (x across, y down). */
class Cuadro(val p: FloatArray) {
    init { require(p.size == 8) }
    val izq get() = min(min(p[0], p[2]), min(p[4], p[6]))
    val der get() = max(max(p[0], p[2]), max(p[4], p[6]))
    val arriba get() = min(min(p[1], p[3]), min(p[5], p[7]))
    val abajo get() = max(max(p[1], p[3]), max(p[5], p[7]))
    val cx get() = (izq + der) / 2
    val cy get() = (arriba + abajo) / 2

    /** Longest side of the box, as a fraction of the short side of the picture ([aspecto] = width/height). */
    fun lado(aspecto: Float): Float {
        val w = der - izq; val h = abajo - arriba
        return if (aspecto <= 1f) max(w, h / aspecto) else max(w * aspecto, h)
    }

    /** Is the point inside the box (with [margen] around it)? */
    fun contiene(x: Float, y: Float, margen: Float = 0f) = x >= izq - margen && x <= der + margen && y >= arriba - margen && y <= abajo + margen

    /** Distance from its centre to the middle of the picture. */
    fun alCentro(): Float = abs(cx - 0.5f) + abs(cy - 0.5f)

    companion object {
        fun de(vararg v: Float) = Cuadro(v)
    }
}

/**
 * Zoom by itself (S-J6): a code was found but could not be read because it is too small (zxing-cpp gives where it is even when it
 * cannot read it). Zoom in a little, step by step, but never so much that the code leaves the screen.
 */
object ZoomAuto {
    /** Below this (fraction of the short side) a code is «too small». */
    const val PEQUENO = 0.22f
    /** What we aim for after zooming. */
    const val OBJETIVO = 0.33f
    /** Highest automatic zoom (more is shaky and dark by hand). */
    const val TOPE = 5f
    /** Time between two steps, so the focus can settle. */
    const val ESPERA_MS = 700L

    /** The new zoom, or null to leave it. [lado] from [Cuadro.lado]; [cx], [cy] its centre (0–1). */
    fun siguiente(lado: Float, cx: Float, cy: Float, zoom: Float, zoomMax: Float, msDesdeCambio: Long, activo: Boolean): Float? {
        if (!activo || msDesdeCambio < ESPERA_MS || lado <= 0f || lado >= PEQUENO) return null
        val tope = min(zoomMax, TOPE)
        if (zoom >= tope * 0.98f) return null
        var k = min(1.6f, OBJETIVO / lado)
        // After zooming by k, the code's distance from the middle grows k times: keep it inside 40 % (on screen).
        val fuera = max(abs(cx - 0.5f), abs(cy - 0.5f))
        if (fuera > 0f) k = min(k, 0.4f / fuera)
        if (k < 1.1f) return null
        return min(zoom * k, tope)
    }
}

/** Torch by itself in the dark (setting, off by default): on after [ms] of a dark picture; never off by itself (it would blink). */
class LuzAuto(private val umbral: Int = 45, private val ms: Long = 1500) {
    private var desde = -1L
    var encendida = false
        private set

    /** True only at the moment the torch should go on. */
    fun ver(brillo: Int, ahora: Long): Boolean {
        if (encendida) return false
        if (brillo < umbral) {
            if (desde < 0) desde = ahora
            if (ahora - desde >= ms) { encendida = true; return true }
        } else desde = -1
        return false
    }

    fun reiniciar() { desde = -1; encendida = false }
}

/** «Aléjate un poco y usa el zoom»: a code that fills almost the whole picture and is not read is too close to focus. */
object Cerca {
    const val LADO = 0.8f
    fun demasiado(lado: Float) = lado >= LADO
}

/**
 * Results of the extra passes (enlarged, other black/white, mirrored) that are not trusted: a short ITF (fewer than 10 digits)
 * comes out of noise when a picture is enlarged (measured in zxing-cpp's false-positive samples, S-J6). The first pass keeps
 * everything, as before.
 */
object Fiable {
    fun aceptar(formato: String, texto: String, pasadaExtra: Boolean): Boolean =
        !(pasadaExtra && formato == "ITF" && texto.length < 10)

    fun esExtra(p: Pasada) = p.escala > 1 || p.histograma || p.espejo || p.limpiar || p.invertir
}
