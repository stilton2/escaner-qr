package es.kalvo.escanerqr.wifis

import es.kalvo.escanerqr.wifis.nucleo.Combinar
import es.kalvo.escanerqr.wifis.nucleo.Creadores
import es.kalvo.escanerqr.wifis.nucleo.Detalles
import es.kalvo.escanerqr.wifis.nucleo.Escaneos
import es.kalvo.escanerqr.wifis.nucleo.FilaHistorial
import es.kalvo.escanerqr.wifis.nucleo.UsoSamsung
import es.kalvo.escanerqr.wifis.nucleo.Ubicaciones
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.ListaRedes
import es.kalvo.escanerqr.wifis.nucleo.Ordenes
import es.kalvo.escanerqr.wifis.nucleo.Red
import es.kalvo.escanerqr.wifis.nucleo.Sugerencia
import es.kalvo.escanerqr.wifis.nucleo.Sugerencias
import es.kalvo.escanerqr.wifis.nucleo.YaGuardada

/** Reads and changes the saved networks. Everything runs on a background thread; nothing is kept on disk. */
object Redes {
    data class Carga(val redes: List<Red>, val avisos: List<String>, val sugerencias: List<Sugerencia> = emptyList())

    /**
     * Reads everything for the Wifis tab. [historial] = the wifi codes of the history (to say when each one was scanned).
     * All read-only: `cmd wifi list-networks`, `dumpsys wifi`, `dumpsys sem_wifi` and Samsung's locations.
     */
    fun cargar(historial: List<FilaHistorial> = emptyList()): Carga {
        val avisos = ArrayList<String>()
        val volcado = Shell.run(Ordenes.volcado())
        val okVolcado = volcado != null && volcado.codigo == 0
        val sugerencias = if (okVolcado) Sugerencias.leer(volcado!!.salida) else {
            avisos += "No pude ver las redes que sugirieron otras apps."
            emptyList()
        }
        val creadores = if (okVolcado) Creadores.leer(volcado!!.salida) else emptyMap()
        val detalles = if (okVolcado) try { Detalles.leer(volcado!!.salida) } catch (e: Throwable) {
            SemWifi.registrar("detalles", e); emptyMap()
        } else emptyMap()
        // Names and types of the normal networks.
        val l = Shell.run(Ordenes.listar())
        val listadas = if (l == null || l.codigo != 0) {
            avisos += "No pude leer la lista de redes guardadas («cmd wifi list-networks»)."
            emptyList()
        } else ListaRedes.leer(l.salida)
        // Passwords of the normal networks (only in memory, for the card). Optional: without them the list still works.
        val claves = try { Wifi.clavesGuardadas() } catch (e: Throwable) {
            SemWifi.registrar("claves", e)
            avisos += "No pude leer las contraseñas guardadas (${SemWifi.motivo(e)})."
            emptyMap()
        }
        var redes = Combinar.sinClaves(listadas, sugerencias, creadores).map { r ->
            if (r.sugerencia) r else {
                val conDetalle = detalles[r.id]?.let { r.copy(detalle = it) } ?: r
                claves[r.id]?.let { conDetalle.copy(clave = it) } ?: conDetalle
            }
        }
        // Samsung's dates (saved, last connected) and use, and where each network is used. Optional; on other makes these
        // services do not exist, so they are not asked for and no warning is shown (S-J5: the app is for any phone).
        val esSamsung = android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)
        if (!esSamsung) return Carga(Escaneos.asignar(redes, Escaneos.porNombre(historial)), avisos, sugerencias)
        val sem = Shell.run(Ordenes.volcadoSamsung())
        if (sem != null && sem.codigo == 0) {
            try { redes = Ubicaciones.asignarUso(redes, UsoSamsung.leer(sem.salida)) } catch (e: Throwable) {
                SemWifi.registrar("uso", e)
                avisos += "No pude leer las fechas de Samsung (${SemWifi.motivo(e)})."
            }
        } else avisos += "No pude leer las fechas de Samsung («dumpsys sem_wifi»)."
        redes = Escaneos.asignar(redes, Escaneos.porNombre(historial))
        // Where each network is used (Samsung). Optional: if it fails, the list goes without it.
        return try {
            Carga(Ubicaciones.asignar(redes, SemWifi.ubicaciones()), avisos, sugerencias)
        } catch (e: Throwable) {
            SemWifi.registrar("ubicaciones", e)
            Carga(redes, avisos + "No pude leer dónde usas cada red: ${SemWifi.motivo(e)}.", sugerencias)
        }
    }

    /**
     * What the phone already has with this name (normal network and/or other apps' suggestions), read-only.
     * If Android does not answer, it says "nothing" (saving with the same name and type then just updates it).
     */
    fun yaGuardada(ssid: String, clave: String? = null): YaGuardada {
        val l = Shell.run(Ordenes.listar())
        val normales = if (l != null && l.codigo == 0) ListaRedes.leer(l.salida) else emptyList()
        val v = Shell.run(Ordenes.volcado())
        val sug = if (v != null && v.codigo == 0) Sugerencias.leer(v.salida) else emptyList()
        val claves = if (clave != null && normales.any { it.ssid == ssid }) {
            try { Wifi.clavesGuardadas() } catch (e: Throwable) { SemWifi.registrar("claves", e); emptyMap() }
        } else emptyMap()
        return YaGuardada.buscar(ssid, normales, sug, claves, clave)
    }

    /** Saves a normal network: through the wifi service; if that call is not available, with `cmd wifi add-network`. */
    fun guardar(d: DatosRed): String? {
        val primero = try { Wifi.guardar(d); return null } catch (e: Throwable) { (e.cause ?: e).message ?: e.javaClass.simpleName }
        val argv = try { Ordenes.anadir(d) } catch (e: IllegalArgumentException) { return e.message ?: "datos no válidos" }
        val r = Shell.run(argv)
        val res = when {
            r == null -> "Shizuku no responde ($primero)"
            r.codigo != 0 -> "Android no la quiso guardar: ${(r.error + r.salida).trim().take(160).ifEmpty { "código ${r.codigo}" }}"
            else -> null
        }
        // A message never carries the password, even if a command echoed it.
        val c = d.clave
        return if (res != null && !c.isNullOrEmpty()) res.replace(c, "••••") else res
    }

    /**
     * Saves it as a normal network and connects now: `cmd wifi connect-network` does both. If that fails, saves it
     * (like "Guardar") and connects to the saved one by its id. Null if the order went through, otherwise the reason.
     */
    fun guardarYConectar(d: DatosRed): String? {
        val argv = try { Ordenes.conectar(d) } catch (e: IllegalArgumentException) { return e.message ?: "datos no válidos" }
        val r = Shell.run(argv)
        if (r != null && r.codigo == 0) return null
        val motivo = if (r == null) "Shizuku no responde" else (r.error + r.salida).trim().take(120).ifEmpty { "código ${r.codigo}" }
        guardar(d)?.let { return ocultar(it, d.clave) }
        val id = Shell.run(Ordenes.listar())?.let { l -> ListaRedes.leer(l.salida).firstOrNull { it.ssid == d.ssid }?.id }
            ?: return ocultar("Se guardó, pero no pude conectar ($motivo)", d.clave)
        return conectarGuardada(id)
    }

    /** Connects to a saved normal network by id (no password needed). Null if the order went through. */
    fun conectarGuardada(id: Int): String? =
        try { Wifi.conectarGuardada(id); null } catch (e: Throwable) { "No pude conectar: ${(e.cause ?: e).message ?: e.javaClass.simpleName}" }

    private fun ocultar(t: String, clave: String?) = if (!clave.isNullOrEmpty()) t.replace(clave, "••••") else t

    /** Waits up to [segundos] for the phone to be connected to [ssid]. */
    fun esperarConexion(ssid: String, segundos: Int = 15): Boolean {
        repeat(segundos) {
            Thread.sleep(1000)
            val r = Shell.run(Ordenes.estado())
            if (r != null && Ordenes.conectadoA(r.salida) == ssid) return true
        }
        return false
    }

    /** `cmd wifi forget-network <id>`. Null if it went well, otherwise the reason. */
    fun olvidar(id: Int): String? {
        val r = Shell.run(Ordenes.olvidar(id)) ?: return "Shizuku no responde"
        val txt = (r.salida + r.error).trim()
        return if (r.codigo == 0 && txt.contains("successful", ignoreCase = true)) null
        else "Android no la olvidó: ${txt.take(160).ifEmpty { "código ${r.codigo}" }}"
    }

    /** Forget-then-save only when we can put the old password back if saving fails. */
    fun olvidaAntes(red: Red) = red.olvidable && red.clave != null

    /**
     * New password for a normal network: forget it and save it again with the same name and type.
     * If saving fails, it puts the old password back. If the old password is unknown it does not forget first
     * (saving with the same name and type then updates the saved network in place). Returns null if all went well.
     */
    fun cambiarClave(red: Red, nueva: String): String? {
        val nuevaRed = red.datos().copy(clave = nueva)
        if (olvidaAntes(red)) {
            olvidar(red.id)?.let { return it }
        }
        val fallo = guardar(nuevaRed) ?: return null
        if (olvidaAntes(red)) {
            val vuelta = guardar(red.datos())
            return if (vuelta == null) "No se pudo guardar la contraseña nueva ($fallo). La he dejado como estaba."
            else "No se pudo guardar la contraseña nueva ($fallo) y tampoco volver a la de antes ($vuelta). Añádela a mano."
        }
        return "No se pudo guardar ($fallo)."
    }

    /** Saves several networks one by one ("Pasar"). [progreso] gets (done, total). Returns the failures (name → reason). */
    fun guardarVarias(lista: List<DatosRed>, progreso: (Int, Int) -> Unit): List<Pair<String, String>> {
        val fallos = ArrayList<Pair<String, String>>()
        lista.forEachIndexed { i, d ->
            guardar(d)?.let { fallos += d.ssid to it }
            progreso(i + 1, lista.size)
        }
        return fallos
    }
}
