package es.kalvo.escanerqr.wifis

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Runs commands as the shell user (uid 2000) through Shizuku, as an argument list (no shell in between),
 * so names and passwords with spaces or quotes go through untouched. Same idea as bateria-noche/Shell.kt.
 */
object Shell {
    data class Res(val codigo: Int, val salida: String, val error: String)

    fun arrancado(): Boolean = try { Shizuku.pingBinder() } catch (_: Throwable) { false }

    fun listo(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    /** Null if Shizuku is not usable or the command could not start. Never log the arguments: one may be a password. */
    fun run(argv: List<String>): Res? = try {
        // Shizuku.newProcess is private in API 13 but still the simplest way to run a command.
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java
        )
        m.isAccessible = true
        val p = m.invoke(null, argv.toTypedArray(), null, null) as Process
        p.outputStream.close()
        var err = ""
        val t = Thread { err = p.errorStream.bufferedReader().readText() }.also { it.start() }
        val out = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        t.join(3000)
        Res(code, out, err)
    } catch (_: Throwable) {
        null
    }
}
