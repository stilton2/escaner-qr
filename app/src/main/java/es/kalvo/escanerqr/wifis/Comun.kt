package es.kalvo.escanerqr.wifis

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import es.kalvo.escanerqr.wifis.nucleo.Origen
import rikka.shizuku.Shizuku

/** Runs [trabajo] on a background thread and [luego] back on the screen thread (if the screen is still open). */
fun <T> Activity.fondo(trabajo: () -> T, luego: (Result<T>) -> Unit) {
    Thread {
        val r = runCatching(trabajo)
        runOnUiThread { if (!isFinishing && !isDestroyed) luego(r) }
    }.start()
}

/** After a connect order went through: says "Conectando a …", waits for the real state, then "Conectado" / "No se pudo conectar". */
fun Activity.avisarConexion(ssid: String) {
    val app = applicationContext
    android.widget.Toast.makeText(app, "Conectando a «$ssid»…", android.widget.Toast.LENGTH_SHORT).show()
    Thread {
        val ok = Redes.esperarConexion(ssid)
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(app, if (ok) "Conectado a «$ssid»" else "No se pudo conectar a «$ssid»", android.widget.Toast.LENGTH_LONG).show()
        }
    }.start()
}

/** Keeps the content clear of the status bar, the navigation bar and the keyboard (Android 15+ draws edge to edge). */
fun View.respetarBordes(extraArriba: Int = 0, extraAbajo: Int = 0) {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    setOnApplyWindowInsetsListener { v, ins ->
        val i = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime() or WindowInsets.Type.displayCutout())
        v.setPadding(l + i.left, t + i.top + extraArriba, r + i.right, b + i.bottom + extraAbajo)
        WindowInsets.CONSUMED
    }
    requestApplyInsets()
}

/** Shizuku in plain words. */
object EstadoShizuku {
    enum class E { APAGADO, VIEJO, SIN_PERMISO, LISTO }

    fun ahora(): E = try {
        when {
            !Shizuku.pingBinder() -> E.APAGADO
            Shizuku.isPreV11() -> E.VIEJO
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> E.SIN_PERMISO
            else -> E.LISTO
        }
    } catch (_: Throwable) { E.APAGADO }

    fun texto(e: E) = when (e) {
        E.APAGADO -> "Shizuku está apagado. Sin él no puedo ver ni guardar tus wifis (leer códigos funciona igual).\n\n" +
            "Abre Shizuku y pulsa «Iniciar» (por depuración inalámbrica). Luego vuelve aquí."
        E.VIEJO -> "Tu Shizuku es muy antiguo. Actualízalo desde su web o GitHub y vuelve aquí."
        E.SIN_PERMISO -> "Falta dar permiso de Shizuku a «Escáner QR». Pulsa el botón y elige «Permitir siempre»."
        E.LISTO -> ""
    }

    fun pedirPermiso(alVolver: (Boolean) -> Unit) {
        val l = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                alVolver(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        try {
            Shizuku.addRequestPermissionResultListener(l)
            Shizuku.requestPermission(21)
        } catch (_: Throwable) {
            Shizuku.removeRequestPermissionResultListener(l)
            alVolver(false)
        }
    }

    fun abrirShizuku(ctx: Context): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api") ?: return false
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }
}

/** "Who saved it", in plain words. */
object Quien {
    private val cache = HashMap<String, String>()

    fun app(ctx: Context, paquete: String): String = cache.getOrPut(paquete) {
        Origen.NOMBRES_CONOCIDOS[paquete]?.let { return@getOrPut it }
        try {
            val pm = ctx.packageManager
            val nombre = pm.getApplicationLabel(pm.getApplicationInfo(paquete, 0)).toString()
            // Another app with the same name as this one: say which (by its package) so it is not taken for this app.
            if (nombre == pm.getApplicationLabel(ctx.applicationInfo).toString() && paquete != ctx.packageName) "$nombre ($paquete)" else nombre
        } catch (_: Throwable) { paquete }
    }

    fun texto(ctx: Context, o: Origen, sugerencia: Boolean): String = when {
        sugerencia && o.paquete != null -> "La sugirió ${app(ctx, o.paquete)} (no es una red normal)"
        else -> when (o.tipo) {
            Origen.Tipo.AJUSTES -> "Guardada desde Ajustes"
            Origen.Tipo.PANEL -> "Guardada desde el panel rápido"
            Origen.Tipo.SHELL -> "Guardada por Shizuku (esta app u órdenes ADB)"
            Origen.Tipo.SISTEMA -> "Guardada por el sistema (${o.crudo})"
            Origen.Tipo.APP -> "Guardada por ${app(ctx, o.paquete ?: "?")}"
            Origen.Tipo.DESCONOCIDO -> "No se sabe quién la guardó"
        }
    }

    /** Short label for the list row. */
    fun corto(ctx: Context, o: Origen, sugerencia: Boolean): String = when {
        sugerencia && o.paquete != null -> "de ${app(ctx, o.paquete)}"
        else -> when (o.tipo) {
            Origen.Tipo.AJUSTES -> "Ajustes"
            Origen.Tipo.PANEL -> "panel rápido"
            Origen.Tipo.SHELL -> "Shizuku"
            Origen.Tipo.SISTEMA -> "sistema"
            Origen.Tipo.APP -> app(ctx, o.paquete ?: "?")
            Origen.Tipo.DESCONOCIDO -> "¿?"
        }
    }
}

/** A plain RecyclerView adapter: each item is drawn by [pintar] into a fresh view (lists here are a few hundred rows). */
class Adaptador<T>(private val pintar: (T) -> View) : RecyclerView.Adapter<Adaptador.Hueco>() {
    class Hueco(val marco: FrameLayout) : RecyclerView.ViewHolder(marco)

    var items: List<T> = emptyList()
        set(v) { field = v; notifyDataSetChanged() }

    override fun getItemCount() = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = Hueco(FrameLayout(parent.context).apply {
        layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    })

    override fun onBindViewHolder(h: Hueco, position: Int) {
        h.marco.removeAllViews()
        h.marco.addView(pintar(items[position]))
    }
}
