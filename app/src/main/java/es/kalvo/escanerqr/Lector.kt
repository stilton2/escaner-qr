package es.kalvo.escanerqr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.net.Uri
import es.kalvo.escanerqr.nucleo.Formatos
import zxingcpp.BarcodeReader

/** What one read gives: text, format name for the history, and metadata like QR Bot's (`{"ec":"L"}`). */
data class Leido(val texto: String, val formato: String, val metadata: String)

/** zxing-cpp (Apache 2.0) on the phone: every format it knows. No ML Kit, no Play Services, no internet. */
object Lector {

    /** For camera frames: one code per frame, fast. */
    fun paraCamara() = BarcodeReader(BarcodeReader.Options().apply {
        formats = emptySet() // = all
        tryHarder = true
        tryRotate = true
        tryInvert = true
        tryDownscale = true
        maxNumberOfSymbols = 1
    })

    fun convertir(r: BarcodeReader.Result): Leido? {
        val t = r.text ?: return null
        if (t.isEmpty() || r.error != null) return null
        val f = r.format.name
        return Leido(t, Formatos.nombre(f), if (f.contains("QR")) Formatos.metadata(r.ecLevel) else "")
    }

    /** Every code in a picture from the gallery or shared from another app. Throws if the picture cannot be opened. */
    fun deImagen(ctx: Context, uri: Uri): List<Leido> {
        val src = ImageDecoder.createSource(ctx.contentResolver, uri)
        val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
            dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Very big photos: a 3000 px side is plenty and keeps memory low.
            val lado = maxOf(info.size.width, info.size.height)
            if (lado > 3000) {
                val f = 3000.0 / lado
                dec.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
        }
        val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp else bmp.copy(Bitmap.Config.ARGB_8888, false)
        val lector = BarcodeReader(BarcodeReader.Options().apply {
            formats = emptySet()
            tryHarder = true; tryRotate = true; tryInvert = true; tryDownscale = true
            maxNumberOfSymbols = 20
        })
        val vistos = LinkedHashMap<String, Leido>()
        for (r in lector.read(argb, Rect(0, 0, argb.width, argb.height), 0)) {
            val l = convertir(r) ?: continue
            vistos.putIfAbsent(l.formato + "\u0000" + l.texto, l)
        }
        return vistos.values.toList()
    }
}
