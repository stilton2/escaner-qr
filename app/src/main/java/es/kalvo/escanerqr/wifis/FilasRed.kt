package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.LinearLayout
import android.widget.Toast
import es.kalvo.escanerqr.Ui
import es.kalvo.escanerqr.wifis.nucleo.Cuando
import es.kalvo.escanerqr.wifis.nucleo.Escaneo
import es.kalvo.escanerqr.wifis.nucleo.Origen
import es.kalvo.escanerqr.wifis.nucleo.Red

/**
 * «DATOS DE LA RED» and «AJUSTES DE LA RED» as label · value rows (S-J4, «estilo A»), shared by the result of a wifi code
 * and the network's card in the Wifis tab. The data are the ones S-J3 added (all read-only, through Shizuku).
 */
object FilasRed {
    /** When, who, where, scans. [escaneos] = reads of a code with this name (the network's own list if null). */
    fun datos(act: Activity, ui: Ui, padre: LinearLayout, r: Red, escaneos: List<Escaneo>? = null) {
        val ahora = System.currentTimeMillis()
        val filas = ArrayList<Triple<String, String, (() -> Unit)?>>()
        r.uso?.creada?.let { filas += Triple("Guardada por primera vez", Cuando.fechaCorta(it), null) }
        val ultima = r.ultimaVez
        filas += Triple("Última conexión", when {
            r.detalle?.conectadaAhora == true -> "Ahora (estás conectado)"
            ultima != null -> Cuando.haceSolo(ultima, ahora)
            r.uso?.conexiones == 0 || r.detalle?.conectadaAlgunaVez == false -> "Nunca"
            else -> "No se sabe"
        }, null)
        r.uso?.conexiones?.takeIf { it > 0 }?.let { n ->
            val t = r.uso?.segundos?.takeIf { it > 0 }?.let { " · " + Cuando.duracion(it) } ?: ""
            filas += Triple("Veces conectada", (if (n == 1) "1 vez" else "$n veces") + t, null)
        }
        val e = Cuando.escaneada(escaneos ?: r.escaneos, ahora)
        filas += Triple("Escaneada", e.ifEmpty { "Nunca" }, null)
        filas += Triple("Quién la guardó", Quien.corto(act, r.origen, r.sugerencia).replaceFirstChar { it.uppercase() }, null)
        r.detalle?.cambiadaPor?.let { lname ->
            if (lname != r.detalle?.creador) filas += Triple("Cambiada por", Quien.corto(act, Origen.desde(lname), false).replaceFirstChar { it.uppercase() }, null)
        }
        when (r.detalle?.macAjuste) {
            null -> {}
            0 -> filas += Triple("MAC del móvil", "La del teléfono", null)
            else -> filas += Triple("MAC del móvil", "Aleatoria", null)
        }
        val u = r.ubicacion
        if (u != null) filas += Triple("Ubicación", "Ver en el mapa") {
            try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.geo(r.ssid)))) }
            catch (_: ActivityNotFoundException) { Toast.makeText(act, "No hay app de mapas", Toast.LENGTH_SHORT).show() }
        } else filas += Triple("Ubicación", "Samsung no la tiene", null)
        filas.forEachIndexed { i, (k, v, a) -> ui.filaValor(padre, k, v, ultima = i == filas.size - 1, accion = a) }
    }

    /** The network's settings as Ajustes shows them. Nothing if Android did not give them (suggestions). */
    fun ajustes(ui: Ui, padre: LinearLayout, r: Red) {
        val d = r.detalle ?: run {
            padre.addView(ui.texto(if (r.sugerencia) "Android no da estos datos de las «sugerencias» de otras apps."
                else "Android no dio estos datos.", 14f, ui.cMuted))
            return
        }
        val filas = ArrayList<Pair<String, String>>()
        filas += "Seguridad" to r.seguridad.nombre
        d.motivoDesactivada?.let { filas += "Estado" to "Android ha dejado de usarla ($it)" }
        d.autoConexion?.let { filas += "Conexión automática" to if (it) "Sí" else "No" }
        if (d.macAjuste != null && d.macAjuste != 0) d.macAleatoria?.let { filas += "MAC aleatoria" to it }
        when (d.ip) {
            null -> {}
            "DHCP" -> filas += "Ajustes de IP" to "DHCP (automática)"
            "STATIC" -> filas += "Ajustes de IP" to ("Estática" + (d.ipFija?.let { " · $it" } ?: ""))
            else -> filas += "Ajustes de IP" to (d.ip ?: "")
        }
        when (d.proxy) {
            null -> {}
            "NONE", "UNASSIGNED" -> filas += "Proxy" to "Ninguno"
            "STATIC" -> filas += "Proxy" to ("Manual" + (d.proxyDetalle?.let { " · $it" } ?: ""))
            "PAC" -> filas += "Proxy" to ("Automático" + (d.proxyDetalle?.let { " · $it" } ?: ""))
            else -> filas += "Proxy" to (d.proxy ?: "")
        }
        filas += "Red medida" to when (d.medida) {
            1 -> "Tratar como medida"
            2 -> "Tratar como no medida"
            else -> "Automático" + if (d.pistaMedida) " (dice que es medida)" else ""
        }
        d.oculta?.let { filas += "Red oculta" to if (it) "Sí" else "No" }
        d.internetAlgunaVez?.let { filas += "Ha tenido internet" to if (it) "Sí" else "Nunca" }
        filas.forEachIndexed { i, (k, v) -> ui.filaValor(padre, k, v, ultima = i == filas.size - 1) }
    }
}
