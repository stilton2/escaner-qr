package es.kalvo.escanerqr.wifis

import android.os.IBinder
import android.util.Log
import es.kalvo.escanerqr.wifis.nucleo.Ubicacion
import es.kalvo.escanerqr.wifis.nucleo.Ubicaciones
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/**
 * Samsung's own wifi service (`sem_wifi`, interface com.samsung.android.wifi.ISemWifiManager), read-only.
 * Only one call is used: getConfiguredNetworkLocations(), the point Samsung keeps per network for
 * "Activar Wi-Fi automáticamente". It needs NETWORK_SETTINGS, which the shell user (Shizuku) has.
 * The method is looked up by name, never by number, so a firmware update cannot make it call something else.
 * Android hides this interface from normal apps (hidden API): if plain reflection does not see the method,
 * it is asked to HiddenApiBypass, which lists hidden methods too.
 */
object SemWifi {
    const val TAG = "EscanerQR"

    fun ubicaciones(): Map<Pair<String, Ubicaciones.Familia?>, Ubicacion> {
        val binder: IBinder = ShizukuBinderWrapper(binderSemWifi())
        val stub = Class.forName("com.samsung.android.wifi.ISemWifiManager\$Stub")
        val sem = metodo(stub, "asInterface", 1).invoke(null, binder)
            ?: throw IllegalStateException("sem_wifi no contesta")
        val m = metodo(Class.forName("com.samsung.android.wifi.ISemWifiManager"), "getConfiguredNetworkLocations", 0)
        val crudo = try { m.invoke(sem) } catch (e: InvocationTargetException) { throw e.cause ?: e }
        val mapa = crudo as? Map<*, *> ?: return emptyMap()
        return Ubicaciones.desdeMapa(mapa)
    }

    /**
     * The `sem_wifi` service. Android does not hand it to normal apps (SELinux: `ServiceManager.getService` gives null inside
     * the app, checked on the S24 with the self-check, S-J3), so if that fails it asks the service manager as the shell user,
     * through Shizuku (the same as `service check sem_wifi` from adb).
     */
    private fun binderSemWifi(): IBinder {
        try { SystemServiceHelper.getSystemService("sem_wifi") } catch (_: Throwable) { null }?.let { return it }
        val contexto = metodo(Class.forName("com.android.internal.os.BinderInternal"), "getContextObject", 0).invoke(null) as IBinder
        val sm = metodo(Class.forName("android.os.IServiceManager\$Stub"), "asInterface", 1).invoke(null, ShizukuBinderWrapper(contexto))
            ?: throw IllegalStateException("no puedo hablar con el gestor de servicios")
        val ism = Class.forName("android.os.IServiceManager")
        val b = try { metodo(ism, "checkService", 1).invoke(sm, "sem_wifi") } catch (e: InvocationTargetException) { throw e.cause ?: e }
        return b as? IBinder ?: throw IllegalStateException("este móvil no tiene el servicio wifi de Samsung (sem_wifi)")
    }

    /** Public method by name and number of parameters; if Android hides it, the hidden list (HiddenApiBypass). */
    private fun metodo(cls: Class<*>, nombre: String, params: Int): Method {
        cls.methods.firstOrNull { it.name == nombre && it.parameterCount == params }?.let { return it }
        val oculto = try {
            HiddenApiBypass.getDeclaredMethods(cls).filterIsInstance<Method>().firstOrNull { it.name == nombre && it.parameterCount == params }
        } catch (_: Throwable) { null }
        if (oculto != null) {
            Log.i(TAG, "sem_wifi: $nombre solo se ve por HiddenApiBypass")
            oculto.isAccessible = true
            return oculto
        }
        throw NoSuchMethodException(nombre)
    }

    /** The reason in a few plain words, for the warning on screen. Never carries network data. */
    fun motivo(e: Throwable): String {
        val c = if (e is InvocationTargetException) e.cause ?: e else e
        return when (c) {
            is NoSuchMethodException -> "Android no deja ver «${c.message}» a las apps"
            is ClassNotFoundException -> "este móvil no tiene el servicio wifi de Samsung"
            is SecurityException -> "Samsung no da permiso (${c.message?.take(60) ?: "sin detalle"})"
            is IllegalStateException -> c.message ?: "sem_wifi no contesta"
            else -> c.javaClass.simpleName + (c.message?.let { ": " + it.take(60) } ?: "")
        }
    }

    /** Logs only the type and message of the failure (never names, places or passwords). */
    fun registrar(que: String, e: Throwable) {
        val c = if (e is InvocationTargetException) e.cause ?: e else e
        Log.w(TAG, "$que: ${c.javaClass.name}: ${c.message?.take(120)}")
    }
}
