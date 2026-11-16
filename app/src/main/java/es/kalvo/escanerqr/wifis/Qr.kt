package es.kalvo.escanerqr.wifis

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.TextoQr
import java.io.ByteArrayOutputStream
import java.util.UUID

/** QR pictures, made in memory with ZXing. Never written to a file. */
object Qr {
    fun imagen(texto: String, lado: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2,
        )
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado, hints)
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) px[y * m.width + x] = if (m[x, y]) Color.BLACK else Color.WHITE
        return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    }

    /** Picture to share: the QR with the name (and password, if any) underneath, on white. */
    fun paraCompartir(d: DatosRed): Bitmap {
        val ancho = 1080
        val qr = imagen(TextoQr.crear(d), ancho - 120)
        val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 52f; typeface = Typeface.DEFAULT_BOLD }
        val p2 = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 44f }
        val lineas = buildList {
            add("Wifi: ${d.ssid}" to p)
            val c = d.clave
            if (!c.isNullOrEmpty()) add("Contraseña: $c" to p2) else add("Sin contraseña" to p2)
            add("Apunta la cámara al código para conectarte" to p2)
        }
        val capas = lineas.map { (t, paint) ->
            StaticLayout.Builder.obtain(t, 0, t.length, paint, ancho - 120).setAlignment(Layout.Alignment.ALIGN_CENTER).build()
        }
        val alto = 60 + qr.height + 30 + capas.sumOf { it.height + 16 } + 60
        val out = Bitmap.createBitmap(ancho, alto, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(qr, 60f, 60f, null)
        var y = 60f + qr.height + 30f
        for (l in capas) {
            c.save(); c.translate(60f, y); l.draw(c); c.restore()
            y += l.height + 16
        }
        return out
    }

    fun png(b: Bitmap): ByteArray = ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** Things passed between our own screens in memory (so passwords never travel in intents or files). */
object Memoria {
    @Volatile var qr: DatosRed? = null
    private val imagenes = LinkedHashMap<String, ByteArray>()

    @Synchronized fun guardarImagen(bytes: ByteArray): String {
        val id = UUID.randomUUID().toString()
        imagenes[id] = bytes
        while (imagenes.size > 3) imagenes.remove(imagenes.keys.first())
        return id
    }

    @Synchronized fun imagen(id: String): ByteArray? = imagenes[id]
}
