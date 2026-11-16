package es.kalvo.escanerqr.wifis

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.FileNotFoundException

/**
 * Hands the QR picture to the app chosen in "Compartir" straight from memory (a pipe), so no image with the password
 * is ever written to this app's storage. Not exported: only apps we grant a URI to can read it.
 */
class QrProvider : ContentProvider() {
    override fun onCreate() = true

    private fun bytes(uri: Uri) = uri.lastPathSegment?.removeSuffix(".png")?.let { Memoria.imagen(it) }

    override fun getType(uri: Uri) = "image/png"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor {
        val b = bytes(uri)
        val cols = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        return MatrixCursor(cols).apply {
            addRow(cols.map { c ->
                when (c) {
                    OpenableColumns.DISPLAY_NAME -> "wifi-qr.png"
                    OpenableColumns.SIZE -> b?.size?.toLong()
                    else -> null
                }
            })
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("solo lectura")
        val b = bytes(uri) ?: throw FileNotFoundException("ya no está")
        val (lee, escribe) = ParcelFileDescriptor.createPipe()
        Thread {
            try { ParcelFileDescriptor.AutoCloseOutputStream(escribe).use { it.write(b) } } catch (_: Exception) { }
        }.start()
        return lee
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
}
