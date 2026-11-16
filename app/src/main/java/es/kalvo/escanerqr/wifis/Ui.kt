package es.kalvo.escanerqr.wifis

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.PersistableBundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.color.MaterialColors

/** Small building blocks shared by the screens (plain views, Material 3 colours from the wallpaper). */
class Ui(private val ctx: Context) {
    private val dp = ctx.resources.displayMetrics.density
    fun px(v: Int) = (v * dp).toInt()

    fun color(v: View, attr: Int) = MaterialColors.getColor(v, attr)
    fun muted(v: View) = color(v, com.google.android.material.R.attr.colorOnSurfaceVariant)
    fun normal(v: View) = color(v, com.google.android.material.R.attr.colorOnSurface)
    fun error(v: View) = color(v, com.google.android.material.R.attr.colorError)

    fun text(s: CharSequence, size: Float = 15f, bold: Boolean = false, col: Int? = null) = TextView(ctx).apply {
        text = s; textSize = size
        if (col != null) setTextColor(col)
        if (bold) typeface = Typeface.create(typeface, Typeface.BOLD)
    }

    companion object {
        /** Copies a password marked as sensitive (Android hides it in the clipboard preview). */
        fun copiar(ctx: Context, etiqueta: String, valor: String) {
            val clip = ClipData.newPlainText(etiqueta, valor)
            if (Build.VERSION.SDK_INT >= 33) {
                clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
            } else {
                clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
            }
            ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
            if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copiada", Toast.LENGTH_SHORT).show()
        }
    }
}
