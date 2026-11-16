package es.kalvo.escanerqr.wifis

import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import es.kalvo.escanerqr.wifis.nucleo.Claves
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.Seguridad
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/**
 * Saves a normal network through Android's wifi service as the shell user (Shizuku's binder), the same call Ajustes uses.
 * It also reads the phone's saved passwords for the owner to see ([clavesGuardadas]), only into memory.
 * Nothing here is written to disk or to the log.
 */
object Wifi {
    private fun servicio(): Any {
        val binder: IBinder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("wifi"))
        val stub = Class.forName("android.net.wifi.IWifiManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    private fun interfaz() = Class.forName("android.net.wifi.IWifiManager")

    private fun paquete() = if (Shizuku.getUid() == 1000) "android" else "com.android.shell"

    private fun metodo(nombre: String, params: Int): Method =
        interfaz().methods.firstOrNull { it.name == nombre && it.parameterCount == params }
            ?: throw NoSuchMethodException("$nombre/$params")

    /** WifiConfiguration for a normal network, built with public calls only. */
    @Suppress("DEPRECATION")
    fun configuracion(d: DatosRed): WifiConfiguration = WifiConfiguration().apply {
        SSID = "\"" + d.ssid + "\""
        hiddenSSID = d.oculta
        val clave = d.clave.orEmpty()
        when (d.seguridad) {
            Seguridad.WPA -> {
                setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK)
                preSharedKey = if (Claves.esClaveHexWpa(clave)) clave else "\"" + clave + "\""
            }
            Seguridad.WPA3 -> {
                setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                preSharedKey = "\"" + clave + "\""
            }
            Seguridad.WEP -> {
                setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP)
                wepKeys[0] = if (Claves.esClaveHexWep(clave)) clave else "\"" + clave + "\""
                wepTxKeyIndex = 0
            }
            Seguridad.ABIERTA -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN)
            Seguridad.OWE -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE)
            Seguridad.EMPRESA -> throw IllegalArgumentException("Las redes de empresa hay que añadirlas en Ajustes")
        }
    }

    /** Saves (or updates, same name and type) a normal network. Returns its networkId; throws on failure. */
    fun guardar(d: DatosRed): Int {
        // AddNetworkResult exists from Android 13; before that the caller falls back to `cmd wifi add-network`.
        if (Build.VERSION.SDK_INT < 33) throw UnsupportedOperationException("Android anterior a 13")
        val wm = servicio()
        val r = metodo("addOrUpdateNetworkPrivileged", 2).invoke(wm, configuracion(d), paquete())
            ?: throw IllegalStateException("sin respuesta")
        val res = r as WifiManager.AddNetworkResult
        if (res.statusCode != WifiManager.AddNetworkResult.STATUS_SUCCESS || res.networkId < 0) {
            throw IllegalStateException("Android dijo que no (código ${res.statusCode})")
        }
        return res.networkId
    }

    /**
     * Connects to a network that is already saved, by its networkId (IWifiManager.connect, as Ajustes does).
     * Needs no password: the saved one is used. The method is looked up by name and its arguments are built from
     * the parameter types, so a different signature in another Android version does not call anything else.
     */
    fun conectarGuardada(id: Int) {
        val wm = servicio()
        val m = interfaz().methods.filter { it.name == "connect" }.maxByOrNull { it.parameterCount }
            ?: throw NoSuchMethodException("connect")
        val args = m.parameterTypes.map { t ->
            when {
                t == Int::class.javaPrimitiveType -> id
                t == String::class.java -> paquete()
                t == android.os.Bundle::class.java -> android.os.Bundle()
                t.name == "android.net.wifi.IActionListener" -> escucha(t)
                t == WifiConfiguration::class.java -> null
                else -> throw IllegalStateException("parámetro ${t.simpleName} desconocido")
            }
        }
        m.invoke(wm, *args.toTypedArray())
    }

    /**
     * The passwords of the phone's own saved networks, by networkId, for the owner to see them in the card
     * (the same thing Ajustes → Wi-Fi → network → «Compartir» shows). Uses IWifiManager.getPrivilegedConfiguredNetworks,
     * allowed to the shell user (READ_WIFI_CREDENTIAL). Kept only in memory while the tab is open; never written to disk
     * or to the log. Networks without a password are left out. Throws if Android does not answer.
     */
    @Suppress("DEPRECATION")
    fun clavesGuardadas(): Map<Int, String> {
        val wm = servicio()
        val m = interfaz().methods.filter { it.name == "getPrivilegedConfiguredNetworks" }.maxByOrNull { it.parameterCount }
            ?: throw NoSuchMethodException("getPrivilegedConfiguredNetworks")
        val args = m.parameterTypes.mapIndexed { i, t ->
            when {
                t == String::class.java -> if (i == 0) paquete() else null
                // Android 13+ answers with empty passwords unless the caller says who it is (AttributionSource).
                t == android.os.Bundle::class.java -> android.os.Bundle().apply {
                    if (Build.VERSION.SDK_INT >= 31) putParcelable("EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE",
                        android.content.AttributionSource.Builder(Shizuku.getUid()).setPackageName(paquete()).build())
                }
                else -> throw IllegalStateException("parámetro ${t.simpleName} desconocido")
            }
        }
        val res = try { m.invoke(wm, *args.toTypedArray()) } catch (e: java.lang.reflect.InvocationTargetException) { throw e.cause ?: e }
            ?: return emptyMap()
        val lista = res.javaClass.getMethod("getList").invoke(res) as List<*>
        val out = HashMap<Int, String>()
        for (o in lista) {
            val c = o as? WifiConfiguration ?: continue
            val clave = Claves.claveLegible(c.preSharedKey)
                ?: c.wepKeys?.getOrNull(c.wepTxKeyIndex)?.let { Claves.claveLegible(it) }
                ?: continue
            out[c.networkId] = clave
        }
        // Only counts, never names or passwords.
        android.util.Log.i(SemWifi.TAG, "claves: redes=${lista.size} con clave=${out.size} " +
            "ocultas=${lista.count { (it as? WifiConfiguration)?.preSharedKey == "*" }}")
        return out
    }

    /** A listener that ignores the answer (we check the real state afterwards). */
    private fun escucha(t: Class<*>): Any {
        val b = android.os.Binder()
        return java.lang.reflect.Proxy.newProxyInstance(t.classLoader, arrayOf(t)) { _, met, _ ->
            if (met.name == "asBinder") b else null
        }
    }
}
