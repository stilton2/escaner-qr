package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.text.InputType
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import es.kalvo.escanerqr.wifis.nucleo.Claves
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.Red

/** Add, change password, forget: always with a confirmation, and the work on a background thread. */
object Dialogos {
    private fun envolver(v: View) = ScrollView(v.context).apply { addView(v) }

    /** Opens the big QR screen for these data (kept in memory, not in the intent). */
    fun abrirQr(act: Activity, d: DatosRed) {
        Memoria.qr = d
        act.startActivity(Intent(act, QrActivity::class.java))
    }

    /** "Añadir red" (or "Pasar a red normal" when [inicial] comes from another app's suggestion). */
    fun anadir(act: Activity, inicial: DatosRed?, redes: List<Red>, titulo: String = "Añadir red", alGuardar: () -> Unit) {
        val f = Formulario(act, inicial)
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(titulo)
            .setView(envolver(f.vista))
            .setPositiveButton("Guardar", null)
            .setNeutralButton("Solo ver QR", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val d = f.leer() ?: return@setOnClickListener
                val ya = redes.any { !it.sugerencia && it.ssid == d.ssid }
                if (ya) {
                    MaterialAlertDialogBuilder(act)
                        .setTitle("Ya tienes «${d.ssid}»")
                        .setMessage("Ya hay una red normal guardada con ese nombre. Si sigues, se guarda con estos datos " +
                            "(si es del mismo tipo, se cambia la que hay).")
                        .setPositiveButton("Seguir") { _, _ -> guardar(act, d, dlg, alGuardar) }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else guardar(act, d, dlg, alGuardar)
            }
            dlg.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener {
                val d = f.leer() ?: return@setOnClickListener
                abrirQr(act, d)
            }
        }
        dlg.show()
    }

    private fun guardar(act: Activity, d: DatosRed, dlg: AlertDialog, alGuardar: () -> Unit) {
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = false
        act.fondo({ Redes.guardar(d) }) { r ->
            val fallo = r.getOrElse { e -> e.javaClass.simpleName }
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).isEnabled = true
            if (fallo == null) {
                dlg.dismiss()
                Toast.makeText(act, "Guardada «${d.ssid}» como red normal", Toast.LENGTH_SHORT).show()
                alGuardar()
            } else {
                MaterialAlertDialogBuilder(act).setTitle("No se guardó").setMessage(fallo).setPositiveButton("Vale", null).show()
            }
        }
    }

    /** Without Shizuku: type a network and see its QR (nothing is saved). */
    fun soloQr(act: Activity, inicial: DatosRed? = null) {
        val f = Formulario(act, inicial)
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(if (inicial == null) "Hacer un QR a mano" else "QR de «${inicial.ssid}»")
            .setMessage(if (inicial == null) "No se guarda nada: solo se enseña el QR."
                else "No sé su contraseña: escríbela tú. No se guarda nada, solo se enseña el QR.")
            .setView(envolver(f.vista))
            .setPositiveButton("Ver QR", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val d = f.leer() ?: return@setOnClickListener
                dlg.dismiss()
                abrirQr(act, d)
            }
        }
        dlg.show()
    }

    /** New password for a normal network: asks for it, explains what will happen, then forget + save again. */
    fun cambiarClave(act: Activity, red: Red, alAcabar: () -> Unit) {
        val ui = Ui(act)
        val capa = TextInputLayout(act).apply { hint = "Contraseña nueva"; endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE }
        val campo = TextInputEditText(capa.context).apply {
            setSingleLine(); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        capa.addView(campo)
        val caja = LinearLayout(act).apply { setPadding(ui.px(20), ui.px(8), ui.px(20), 0); addView(capa, -1, -2) }
        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle("Cambiar la contraseña de «${red.ssid}»")
            .setView(caja)
            .setPositiveButton("Siguiente", null)
            .setNegativeButton("Cancelar", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val nueva = campo.text?.toString().orEmpty()
                capa.error = Claves.problemaClave(red.seguridad, nueva) ?: if (nueva == red.clave) "Es la misma que ya tiene" else null
                if (capa.error != null) return@setOnClickListener
                val como = if (Redes.olvidaAntes(red))
                    "Voy a olvidar «${red.ssid}» y a guardarla otra vez, igual pero con la contraseña nueva. " +
                        "Si algo falla, la dejo con la de antes."
                else "Voy a guardar «${red.ssid}» otra vez con la contraseña nueva (Android cambia la que hay)."
                MaterialAlertDialogBuilder(act)
                    .setTitle("¿Cambiar la contraseña?")
                    .setMessage(como)
                    .setPositiveButton("Cambiar") { _, _ ->
                        dlg.dismiss()
                        act.fondo({ Redes.cambiarClave(red, nueva) }) { r ->
                            val fallo = r.getOrElse { e -> e.javaClass.simpleName }
                            if (fallo == null) Toast.makeText(act, "Contraseña cambiada", Toast.LENGTH_SHORT).show()
                            else MaterialAlertDialogBuilder(act).setTitle("No se cambió").setMessage(fallo).setPositiveButton("Vale", null).show()
                            alAcabar()
                        }
                    }
                    .setNegativeButton("Cancelar", null)
                    .show()
            }
        }
        dlg.show()
    }

    /** Forget a normal network, after saying what it means (and offering to copy the password first). */
    fun olvidar(act: Activity, red: Red, alAcabar: () -> Unit) {
        val clave = red.clave
        val b = MaterialAlertDialogBuilder(act)
            .setTitle("¿Olvidar «${red.ssid}»?")
            .setMessage(
                "El móvil dejará de conectarse solo a esta wifi y desaparece de la lista. " +
                    (if (clave != null) "Si la quieres volver a poner, necesitarás la contraseña: cópiala antes si no la tienes apuntada."
                    else "No sé su contraseña: si la quieres volver a poner tendrás que saberla.")
            )
            .setPositiveButton("Olvidar") { _, _ ->
                act.fondo({ Redes.olvidar(red.id) }) { r ->
                    val fallo = r.getOrElse { e -> e.javaClass.simpleName }
                    if (fallo == null) Toast.makeText(act, "Olvidada «${red.ssid}»", Toast.LENGTH_SHORT).show()
                    else MaterialAlertDialogBuilder(act).setTitle("No se olvidó").setMessage(fallo).setPositiveButton("Vale", null).show()
                    alAcabar()
                }
            }
            .setNegativeButton("Cancelar", null)
        if (clave != null) b.setNeutralButton("Copiar contraseña") { _, _ -> Ui.copiar(act, "Contraseña wifi", clave) }
        b.show()
    }
}
