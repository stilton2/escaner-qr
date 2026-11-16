package es.kalvo.escanerqr.wifis

import android.content.Context
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.wifis.nucleo.Claves
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.Seguridad

/** Name, password, type and "hidden": used by "Añadir red" and by "Guardar esta red" (from another app). */
class Formulario(ctx: Context, inicial: DatosRed?, nombreFijo: Boolean = false) {
    private val ui = Ui(ctx)
    val vista = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ui.px(20), ui.px(8), ui.px(20), 0) }

    private val capaNombre = TextInputLayout(ctx).apply { hint = "Nombre de la red" }
    private val nombre = TextInputEditText(capaNombre.context).apply {
        setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setText(inicial?.ssid.orEmpty())
        isEnabled = !nombreFijo
    }
    private val capaClave = TextInputLayout(ctx).apply {
        hint = "Contraseña"; endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
    }
    private val clave = TextInputEditText(capaClave.context).apply {
        setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setText(inicial?.clave.orEmpty())
    }
    private val tipos = RadioGroup(ctx)
    private val oculta = MaterialCheckBox(ctx).apply { text = "Red oculta (no anuncia su nombre)"; isChecked = inicial?.oculta == true }

    init {
        capaNombre.addView(nombre)
        capaClave.addView(clave)
        vista.addView(capaNombre, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        vista.addView(capaClave, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply { topMargin = ui.px(8) })
        vista.addView(ui.text("Tipo", 13f).apply { setPadding(ui.px(4), ui.px(12), 0, 0) })
        val elegida = inicial?.seguridad?.takeIf { it in Seguridad.elegibles } ?: Seguridad.WPA
        Seguridad.elegibles.forEachIndexed { i, s ->
            tipos.addView(RadioButton(ctx).apply {
                id = 100 + i
                text = when (s) {
                    Seguridad.WPA -> "WPA2/WPA3 (lo normal)"
                    else -> s.nombre
                }
                isChecked = s == elegida
            })
        }
        tipos.setOnCheckedChangeListener { _, _ -> capaClave.isEnabled = seguridad().llevaClave }
        capaClave.isEnabled = elegida.llevaClave
        vista.addView(tipos)
        vista.addView(oculta)
    }

    fun seguridad(): Seguridad = Seguridad.elegibles.getOrElse(tipos.checkedRadioButtonId - 100) { Seguridad.WPA }

    /** The data if it is valid; otherwise marks the wrong field and returns null. */
    fun leer(): DatosRed? {
        val s = seguridad()
        val n = nombre.text?.toString().orEmpty()
        val c = clave.text?.toString().orEmpty()
        capaNombre.error = Claves.problemaNombre(n)
        capaClave.error = Claves.problemaClave(s, c)
        if (capaNombre.error != null || capaClave.error != null) return null
        return DatosRed(n, s, if (s.llevaClave) c else null, oculta.isChecked)
    }
}
