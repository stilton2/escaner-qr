package es.kalvo.escanerqr

import android.app.Activity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import es.kalvo.escanerqr.nucleo.Fechas
import es.kalvo.escanerqr.nucleo.Importar

/**
 * «Importar historial» when the columns of the file are not recognised (S-J5): shows the first 5 rows and asks
 * «¿Qué columna es el texto leído? ¿Y la fecha?». The date is optional (without it, the import time is used and the
 * summary says so).
 */
object ImportarColumnas {

    fun preguntar(act: Activity, e: Importar.Resultado.Elegir, listo: (Importar.Listo) -> Unit) {
        val ui = Ui(act)
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(24), ui.px(8), ui.px(24), 0) }
        col.addView(ui.texto("No reconozco las columnas de «${e.nombre}». Estas son sus primeras filas:", 15f))

        // The first rows as a small table in monospace (scrolls sideways if it is wide).
        val n = e.columnas
        val muestra = e.muestra
        val ancho = (0 until n).map { i -> maxOf(("Col. " + (i + 1)).length, muestra.maxOfOrNull { corto(it.getOrElse(i) { "" }).length } ?: 0) }
        val tabla = buildString {
            append((0 until n).joinToString(" │ ") { ("Col. " + (it + 1)).padEnd(ancho[it]) }).append('\n')
            append((0 until n).joinToString("─┼─") { "─".repeat(ancho[it]) }).append('\n')
            for (f in muestra) append((0 until n).joinToString(" │ ") { corto(f.getOrElse(it) { "" }).padEnd(ancho[it]) }).append('\n')
        }.trimEnd()
        val tv = ui.texto(tabla, 12f, ui.cMuted).apply { typeface = android.graphics.Typeface.MONOSPACE; setTextIsSelectable(true) }
        val hs = HorizontalScrollView(act).apply { addView(tv); setPadding(0, ui.px(10), 0, ui.px(10)) }
        col.addView(hs, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val cabecera = CheckBox(act).apply { text = "La primera fila son los títulos (no es una lectura)"; isChecked = e.primeraEsCabecera; minHeight = ui.px(48) }
        col.addView(cabecera)

        fun nombreCol(i: Int): String {
            val ejemplo = muestra.drop(if (cabecera.isChecked) 1 else 0).firstOrNull()?.getOrNull(i).orEmpty()
            val titulo = if (cabecera.isChecked) muestra.firstOrNull()?.getOrNull(i).orEmpty() else ""
            return "Col. ${i + 1}" + (if (titulo.isNotBlank()) " · ${corto(titulo)}" else "") + (if (ejemplo.isNotBlank()) " · ej. ${corto(ejemplo)}" else "")
        }

        col.addView(ui.texto("¿Qué columna es el texto leído?", 15f, bold = true).apply { setPadding(0, ui.px(12), 0, ui.px(4)) })
        val spTexto = Spinner(act).apply { minimumHeight = ui.px(48) }
        col.addView(spTexto, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(ui.texto("¿Y la fecha? Si no tiene, se guardan con la hora de ahora.", 15f, bold = true).apply { setPadding(0, ui.px(16), 0, ui.px(4)) })
        val spFecha = Spinner(act).apply { minimumHeight = ui.px(48) }
        col.addView(spFecha, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        // Guesses: text = the column with the longest values; date = the first one whose values are dates.
        val datos = muestra.drop(if (e.primeraEsCabecera) 1 else 0)
        val tz = java.util.TimeZone.getDefault()
        val colFecha = (0 until n).firstOrNull { i -> datos.isNotEmpty() && datos.all { Fechas.leer(it.getOrElse(i) { "" }, tz) != null } }
        val colTexto = (0 until n).filter { it != colFecha }.maxByOrNull { i -> datos.sumOf { it.getOrElse(i) { "" }.length } } ?: 0

        fun rellenar() {
            val selT = spTexto.selectedItemPosition.takeIf { it >= 0 } ?: colTexto
            val selF = spFecha.selectedItemPosition.takeIf { it >= 0 } ?: (colFecha?.plus(1) ?: 0)
            spTexto.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, (0 until n).map { nombreCol(it) })
            spFecha.adapter = ArrayAdapter(act, android.R.layout.simple_spinner_dropdown_item, listOf("No tiene fecha") + (0 until n).map { nombreCol(it) })
            spTexto.setSelection(selT); spFecha.setSelection(selF)
        }
        rellenar()
        cabecera.setOnCheckedChangeListener { _, _ -> rellenar() }

        MaterialAlertDialogBuilder(act)
            .setTitle("¿Qué hay en cada columna?")
            .setView(ScrollView(act).apply { addView(col) })
            .setPositiveButton("Importar") { _, _ ->
                val t = spTexto.selectedItemPosition.coerceAtLeast(0)
                val f = spFecha.selectedItemPosition.let { if (it <= 0) null else it - 1 }
                listo(Importar.conColumnas(e, t, f, cabecera.isChecked))
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun corto(s: String): String {
        val una = s.replace('\n', ' ').replace('\r', ' ').trim()
        return if (una.length > 24) una.take(23) + "…" else una
    }
}
