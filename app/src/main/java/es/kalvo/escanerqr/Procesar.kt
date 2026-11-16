package es.kalvo.escanerqr

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import es.kalvo.escanerqr.nucleo.Contenido
import es.kalvo.escanerqr.nucleo.Historial
import es.kalvo.escanerqr.nucleo.Lectura
import es.kalvo.escanerqr.nucleo.Especial
import java.util.TimeZone

/** What happens after a code is read (camera or picture): save, copy, beep, vibrate, then result screen or browser. */
object Procesar {

    /** Saves [l] following the settings and returns it with its id (0 if history is off). */
    fun guardar(ctx: Context, leido: Leido): Lectura {
        val aj = Ajustes(ctx)
        val l = Historial.nueva(System.currentTimeMillis(), TimeZone.getDefault(), leido.formato, leido.texto, leido.metadata)
        if (!aj.guardar) return l
        val db = Db.de(ctx)
        if (!aj.repetidos) {
            val igual = db.buscarIgual(l.formato, l.texto)
            if (igual != null) {
                db.moverAhora(igual.id, l)
                return db.una(igual.id) ?: l
            }
        }
        val id = db.insertar(l)
        return if (id > 0) l.copy(id = id) else (db.buscarIgual(l.formato, l.texto) ?: l)
    }

    /**
     * S-J9: a code made in «Crear» goes to the history with origin «Creado» (only if the history is on in Ajustes).
     * Making the same code again (same text and format) moves that entry to now instead of adding another one.
     */
    fun guardarCreado(ctx: Context, texto: String, formato: String): Lectura? {
        if (!Ajustes(ctx).guardar) return null
        val l = Historial.nueva(System.currentTimeMillis(), TimeZone.getDefault(), formato, texto).copy(origen = Historial.CREADO)
        val db = Db.de(ctx)
        db.buscarIgual(formato, texto, Historial.CREADO)?.let { igual ->
            db.moverAhora(igual.id, l)
            return db.una(igual.id) ?: l
        }
        val id = db.insertar(l)
        return if (id > 0) l.copy(id = id) else db.buscarIgual(formato, texto, Historial.CREADO)
    }

    fun avisar(ctx: Context) {
        val aj = Ajustes(ctx)
        if (aj.vibrar) {
            ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(60, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        if (aj.pitido) {
            try {
                val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
                tg.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tg.release() }, 300)
            } catch (e: RuntimeException) { /* no audio right now */ }
        }
    }

    /**
     * Full flow for one code. [continua] = continuous scanning: only save (and copy), no screen.
     * Returns the saved scan.
     */
    fun nuevo(ctx: Context, leido: Leido, desdeCamara: Boolean, continua: Boolean = false): Lectura {
        val aj = Ajustes(ctx)
        val l = guardar(ctx, leido)
        if (desdeCamara) avisar(ctx)
        val c = Contenido.analizar(leido.texto, leido.formato)
        // S-J4: MAC with ':' in capitals, SIM number whole, article label's serial (see Especial.queCopiar).
        val copia = if (aj.copiar) Especial.queCopiar(leido.texto, c, aj.copiarMac).also { Acciones.copiar(ctx, it) } else null
        if (continua) return l
        if (aj.abrirEnlaces && c is Contenido.Enlace) {
            Acciones.abrirEnlace(ctx, c.url)
        } else {
            ctx.startActivity(ResultadoActivity.intent(ctx, l, copia))
        }
        return l
    }

    fun abrirResultado(ctx: Context, l: Lectura) = ctx.startActivity(ResultadoActivity.intent(ctx, l))
}
