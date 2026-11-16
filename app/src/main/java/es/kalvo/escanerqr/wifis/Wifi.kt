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
 * This app does NOT read saved passwords (path B, see informes/S-I2.md): it only writes the network the user typed.
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

    /** A listener that ignores the answer (we check the real state afterwards). */
    private fun escucha(t: Class<*>): Any {
        val b = android.os.Binder()
        return java.lang.reflect.Proxy.newProxyInstance(t.classLoader, arrayOf(t)) { _, met, _ ->
            if (met.name == "asBinder") b else null
        }
    }
}
