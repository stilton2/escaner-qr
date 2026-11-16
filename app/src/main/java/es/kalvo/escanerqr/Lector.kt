package es.kalvo.escanerqr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import es.kalvo.escanerqr.nucleo.Cuadro
import es.kalvo.escanerqr.nucleo.Fiable
import es.kalvo.escanerqr.nucleo.Formatos
import es.kalvo.escanerqr.nucleo.Hallado
import es.kalvo.escanerqr.nucleo.Lum
import es.kalvo.escanerqr.nucleo.Orientacion
import es.kalvo.escanerqr.nucleo.Pasada
import es.kalvo.escanerqr.nucleo.Plan
import java.nio.ByteBuffer
import zxingcpp.BarcodeReader

/** What one read gives: text, format name for the history, and metadata like QR Bot's (`{"ec":"L"}`). */
data class Leido(val texto: String, val formato: String, val metadata: String)

/** One camera frame read: the codes ([hallados]), codes seen but not readable ([errados], for the zoom), brightness, time. */
class Fotograma(val hallados: List<Hallado>, val errados: List<Cuadro>, val brillo: Int, val ms: Long, val aspecto: Float, val pasada: String)

/**
 * zxing-cpp (Apache 2.0) on the phone: every format it knows. No ML Kit, no Play Services, no internet.
 * S-J6: several passes (see `nucleo/Plan`) on grey pictures made by us (cut, enlarged, mirrored), all through the same reader.
 */
object Lector {

    /** Options of the v2.4 camera (kept for the test bench, `Banco`). */
    fun opcionesV24(maximo: Int) = BarcodeReader.Options().apply {
        formats = emptySet() // = all
        tryHarder = true; tryRotate = true; tryInvert = true; tryDownscale = true
        maxNumberOfSymbols = maximo
    }

    /** Options for one pass. [errores] = also give codes found but not readable (where they are, for the zoom). */
    fun opciones(p: Pasada, maximo: Int, errores: Boolean) = BarcodeReader.Options().apply {
        formats = emptySet()
        tryHarder = true; tryRotate = true; tryInvert = true
        tryDownscale = p.escala <= 1
        tryDenoise = p.limpiar
        binarizer = if (p.histograma) BarcodeReader.Binarizer.GLOBAL_HISTOGRAM else BarcodeReader.Binarizer.LOCAL_AVERAGE
        maxNumberOfSymbols = maximo
        returnErrors = errores
    }

    fun convertir(r: BarcodeReader.Result): Leido? {
        val t = r.text ?: return null
        if (t.isEmpty() || r.error != null) return null
        val f = r.format.name
        return Leido(t, Formatos.nombre(f), if (f.contains("QR")) Formatos.metadata(r.ecLevel) else "")
    }

    // ---------- pictures ----------

    /** The picture of [p] taken from [lum]: cut, scaled, mirrored. */
    fun preparar(lum: Lum, p: Pasada): Lum {
        var l = if (p.fraccion >= 1f) lum else lum.recortar(p.recorte(lum.ancho, lum.alto))
        l = when { p.escala > 1 -> l.ampliar(p.escala, p.suave); p.escala < -1 -> l.reducir(-p.escala); else -> l }
        if (p.espejo) l = l.espejo()
        if (p.invertir) l = l.invertir()
        return l
    }

    /** Grey picture → Android bitmap the reader takes without converting (ALPHA_8 = luminance for zxing-cpp). */
    fun aBitmap(l: Lum, reusar: Bitmap? = null): Bitmap {
        val b = reusar?.takeIf { it.width == l.ancho && it.height == l.alto && !it.isRecycled }
            ?: Bitmap.createBitmap(l.ancho, l.alto, Bitmap.Config.ALPHA_8)
        if (b.rowBytes == l.ancho) b.copyPixelsFromBuffer(ByteBuffer.wrap(l.px, 0, l.ancho * l.alto))
        else {
            val fila = b.rowBytes
            val dest = ByteArray(fila * l.alto)
            for (y in 0 until l.alto) System.arraycopy(l.px, y * l.ancho, dest, y * fila, l.ancho)
            b.copyPixelsFromBuffer(ByteBuffer.wrap(dest))
        }
        return b
    }

    /** Colour bitmap → grey picture. */
    fun aLum(bmp: Bitmap): Lum {
        val w = bmp.width; val h = bmp.height
        val out = ByteArray(w * h)
        val fila = IntArray(w)
        for (y in 0 until h) {
            bmp.getPixels(fila, 0, w, 0, y, w, 1)
            val o = y * w
            for (x in 0 until w) {
                val c = fila[x]
                // Same weights as zxing-cpp (306·R + 601·G + 117·B) / 1024.
                out[o + x] = ((306 * ((c shr 16) and 255) + 601 * ((c shr 8) and 255) + 117 * (c and 255) + 512) shr 10).toByte()
            }
        }
        return Lum(w, h, out)
    }

    /** One pass over [lum]: the results with their corners in [lum] pixels. */
    fun pasada(lector: BarcodeReader, lum: Lum, p: Pasada, maximo: Int, errores: Boolean, reusar: Bitmap? = null): List<Pair<BarcodeReader.Result, FloatArray>> {
        val r = p.recorte(lum.ancho, lum.alto)
        val img = preparar(lum, p)
        val bmp = aBitmap(img, reusar)
        lector.options = opciones(p, maximo, errores)
        val res = lector.read(bmp, Rect(0, 0, img.ancho, img.alto), 0)
        if (bmp !== reusar) bmp.recycle()
        val extra = Fiable.esExtra(p)
        return res.filter { x -> x.error != null || Fiable.aceptar(Formatos.nombre(x.format.name), x.text ?: "", extra) }.map { x ->
            val q = x.position
            val pts = floatArrayOf(q.topLeft.x.toFloat(), q.topLeft.y.toFloat(), q.topRight.x.toFloat(), q.topRight.y.toFloat(),
                q.bottomRight.x.toFloat(), q.bottomRight.y.toFloat(), q.bottomLeft.x.toFloat(), q.bottomLeft.y.toFloat())
            for (i in 0 until 4) {
                val (mx, my) = p.aMarco(r, pts[2 * i], pts[2 * i + 1])
                pts[2 * i] = mx; pts[2 * i + 1] = my
            }
            Pair(x, pts)
        }
    }

    /**
     * Every code in a still picture: first as v2.4 did (whole colour picture), then the extra passes of [Plan.imagen]
     * (other black/white method, mirrored, cleaned, enlarged). All found count, without repeats.
     */
    fun deBitmap(argbEntrada: Bitmap): List<Leido> {
        val argb = if (argbEntrada.config == Bitmap.Config.ARGB_8888) argbEntrada else argbEntrada.copy(Bitmap.Config.ARGB_8888, false)
        val vistos = LinkedHashMap<String, Leido>()
        val lector = BarcodeReader(opcionesV24(20))
        for (r in lector.read(argb, Rect(0, 0, argb.width, argb.height), 0)) {
            val l = convertir(r) ?: continue
            vistos.putIfAbsent(l.formato + "\u0000" + l.texto, l)
        }
        // Big picture (a photo) where the normal pass already found something: done (the extras on 12 MP take seconds).
        if (vistos.isNotEmpty() && maxOf(argb.width, argb.height) > 1600) return vistos.values.toList()
        val lum = aLum(argb)
        for (p in Plan.imagen(lum.ancho, lum.alto)) {
            for ((r, _) in pasada(lector, lum, p, 20, false)) {
                val l = convertir(r) ?: continue
                vistos.putIfAbsent(l.formato + "\u0000" + l.texto, l)
            }
        }
        return vistos.values.toList()
    }

    /** Every code in a picture from the gallery or shared from another app. Throws if the picture cannot be opened. */
    fun deImagen(ctx: Context, uri: Uri): List<Leido> {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Very big photos: a 4000 px side is plenty (the S24's own photos are 4000×3000) and keeps memory low.
            val lado = maxOf(info.size.width, info.size.height)
            if (lado > 4000) {
                val f = 4000.0 / lado
                dec.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
        return deBitmap(bmp)
    }
}

/**
 * Camera reader (S-J6). One per camera; runs on the camera thread. Takes the frame as you see it on the screen (the crop of
 * CameraX's viewport), and runs [Plan.camara]: whole frame, middle at full detail, and an extra that takes turns.
 * Up to 8 codes per frame (several codes on one label).
 */
class LectorCamara {
    private val lector = BarcodeReader(Lector.opcionesV24(1))
    private var n = 0L
    private var buffer = ByteArray(0)
    private val bitmaps = HashMap<Long, Bitmap>()

    fun leer(img: ImageProxy): Fotograma {
        val t0 = SystemClock.elapsedRealtime()
        val c = img.cropRect
        val w = c.width(); val h = c.height()
        val plano = img.planes[0]
        val buf = plano.buffer
        val paso = plano.rowStride
        if (buffer.size < w * h) buffer = ByteArray(w * h)
        for (y in 0 until h) {
            buf.position((c.top + y) * paso + c.left)
            buf.get(buffer, y * w, w)
        }
        val lum = Lum(w, h, buffer)
        val giro = img.imageInfo.rotationDegrees
        val aspecto = Orientacion.aspecto(w, h, giro)
        val hallados = ArrayList<Hallado>()
        val errados = ArrayList<Cuadro>()
        var cual = ""
        for (p in Plan.camara(w, h, n++)) {
            val res = Lector.pasada(lector, lum, p, 8, true, reusable(lum, p))
            for ((r, pts) in res) {
                val norm = FloatArray(8)
                for (i in 0 until 4) {
                    val (x, y) = Orientacion.normalizar(pts[2 * i], pts[2 * i + 1], w, h, giro)
                    norm[2 * i] = x; norm[2 * i + 1] = y
                }
                val l = Lector.convertir(r)
                if (l != null) hallados += Hallado(l.texto, l.formato, l.metadata, Cuadro(norm)) else errados += Cuadro(norm)
            }
            if (hallados.isNotEmpty()) { cual = p.nombre; break }
        }
        return Fotograma(hallados, errados, lum.brillo(16), SystemClock.elapsedRealtime() - t0, aspecto, cual)
    }

    /** A bitmap of the right size kept between frames (no new 4 MB bitmap 10 times a second). */
    private fun reusable(lum: Lum, p: Pasada): Bitmap {
        val r = p.recorte(lum.ancho, lum.alto)
        var w = r.ancho; var h = r.alto
        if (p.escala > 1) { w *= p.escala; h *= p.escala } else if (p.escala < -1) { w /= -p.escala; h /= -p.escala }
        val clave = (w.toLong() shl 32) or h.toLong()
        if (bitmaps.size > 6 && clave !in bitmaps) { bitmaps.values.forEach { it.recycle() }; bitmaps.clear() }
        return bitmaps.getOrPut(clave) { Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8) }
    }

    fun cerrar() { bitmaps.values.forEach { it.recycle() }; bitmaps.clear() }
}
