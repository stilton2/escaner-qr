package es.kalvo.escanerqr

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.File

/** Draws a text as a QR picture (ZXing core, Apache 2.0) for «Ver como QR» and for sharing it; also saves pictures (S-J9). */
object Qr {
    fun dibujar(texto: String, lado: Int = 900): Bitmap? = try {
        val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, lado, lado,
            mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.MARGIN to 2))
        val px = IntArray(m.width * m.height)
        for (y in 0 until m.height) for (x in 0 until m.width) px[y * m.width + x] = if (m.get(x, y)) Color.BLACK else Color.WHITE
        Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
    } catch (e: Exception) { null }  // too long for a QR

    /** Writes the picture to the app cache (only that file, replaced each time) and opens the share sheet. */
    fun compartir(ctx: Context, bmp: Bitmap, titulo: String = "Compartir QR") {
        val dir = File(ctx.cacheDir, "qr").apply { mkdirs() }
        val f = File(dir, "codigo.png")
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".ficheros", f)
        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, titulo))
    }

    /**
     * «Guardar en la galería» (S-J9): a PNG in Pictures/Escáner QR through MediaStore (no permission needed since Android 10).
     * Returns false if it could not be written (the half-written entry is removed).
     */
    fun guardarEnGaleria(ctx: Context, bmp: Bitmap, nombre: String): Boolean {
        val cr = ctx.contentResolver
        val datos = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, nombre)
            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Escáner QR")
            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            cr.insert(android.provider.MediaStore.Images.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY), datos)
        } catch (e: Exception) { null } ?: return false
        return try {
            cr.openOutputStream(uri)!!.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            cr.update(uri, android.content.ContentValues().apply { put(android.provider.MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        } catch (e: Exception) {
            try { cr.delete(uri, null, null) } catch (x: Exception) {}
            false
        }
    }
}
