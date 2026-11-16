package es.kalvo.escanerqr

import android.app.Application
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Ajustes(this).aplicarTema()
        // Wifis part only (was «Mis wifis»): the wifi service interface, WifiConfiguration's hidden fields, the list wrapper
        // it returns, and Samsung's wifi service for getConfiguredNetworkLocations() (read-only).
        try {
            val ok = HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/net/wifi/", "Lcom/android/wifi/", "Lcom/android/modules/utils/", "Landroid/content/pm/ParceledListSlice",
                "Lcom/samsung/android/wifi/", "Landroid/os/IServiceManager", "Lcom/android/internal/os/BinderInternal"
            )
            if (!ok) Log.w("EscanerQR", "HiddenApiBypass: addHiddenApiExemptions dio false")
        } catch (e: Throwable) {
            Log.w("EscanerQR", "HiddenApiBypass: ${e.javaClass.name}: ${e.message?.take(120)}")
        }
    }
}
