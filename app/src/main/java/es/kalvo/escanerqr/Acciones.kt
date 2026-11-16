package es.kalvo.escanerqr

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PersistableBundle
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.widget.Toast
import es.kalvo.escanerqr.nucleo.Contenido

/** The buttons of the result screen: every one hands the job to another app of the phone (browser, phone, contacts…). */
object Acciones {
    fun copiar(ctx: Context, texto: String, sensible: Boolean = false, avisar: Boolean = true) {
        val cm = ctx.getSystemService(ClipboardManager::class.java)
        val clip = ClipData.newPlainText("Escáner QR", texto)
        // Passwords: Android hides them in the "copied" preview and in keyboard suggestions.
        if (sensible) clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
        cm.setPrimaryClip(clip)
        // Android 13+ shows its own "copied" bubble; a toast on top would be repeated.
        if (avisar && android.os.Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "Copiado", Toast.LENGTH_SHORT).show()
    }

    private fun lanzar(ctx: Context, i: Intent, siNoHay: String = "No hay ninguna app en el móvil que pueda hacer esto.") {
        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(ctx, siNoHay, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Toast.makeText(ctx, siNoHay, Toast.LENGTH_LONG).show()
        }
    }

    fun abrirEnlace(ctx: Context, url: String) =
        lanzar(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
            "No hay navegador para abrir este enlace.")

    fun compartir(ctx: Context, texto: String) =
        lanzar(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), "Compartir"))

    fun buscar(ctx: Context, texto: String, plantilla: String) =
        abrirEnlace(ctx, plantilla.replace("{code}", Uri.encode(texto)))

    fun llamar(ctx: Context, numero: String) = lanzar(ctx, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(numero))))

    fun correo(ctx: Context, para: String, asunto: String = "", cuerpo: String = "") {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(para))
        if (asunto.isNotEmpty()) i.putExtra(Intent.EXTRA_SUBJECT, asunto)
        if (cuerpo.isNotEmpty()) i.putExtra(Intent.EXTRA_TEXT, cuerpo)
        lanzar(ctx, i, "No hay app de correo.")
    }

    fun sms(ctx: Context, numero: String, mensaje: String) =
        lanzar(ctx, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(numero))).putExtra("sms_body", mensaje))

    fun mapa(ctx: Context, g: Contenido.Geo) {
        val q = if (g.consulta.isNotEmpty()) "${g.lat},${g.lon}(${g.consulta})" else "${g.lat},${g.lon}"
        lanzar(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:${g.lat},${g.lon}?q=" + Uri.encode(q))), "No hay app de mapas.")
    }

    /** Opens the phone's Contacts with the form filled in; nothing is saved until you tap Save there. */
    fun contacto(ctx: Context, nombre: String, telefonos: List<String>, emails: List<String>, empresa: String = "",
                 cargo: String = "", direccion: String = "", web: String = "", nota: String = "") {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        if (nombre.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.NAME, nombre)
        if (empresa.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.COMPANY, empresa)
        if (cargo.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, cargo)
        if (direccion.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.POSTAL, direccion)
        if (nota.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.NOTES, nota)
        val datos = ArrayList<ContentValues>()
        for (t in telefonos) datos.add(ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Phone.NUMBER, t)
        })
        for (e in emails) datos.add(ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Email.ADDRESS, e)
        })
        if (web.isNotEmpty()) datos.add(ContentValues().apply {
            put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
            put(ContactsContract.CommonDataKinds.Website.URL, web)
        })
        if (datos.isNotEmpty()) i.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, datos)
        lanzar(ctx, i, "No hay app de contactos.")
    }

    /** Opens the Calendar with the event filled in; it is saved only if you tap Save there. */
    fun evento(ctx: Context, e: Contenido.Evento) {
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, e.titulo)
        if (e.lugar.isNotEmpty()) i.putExtra(CalendarContract.Events.EVENT_LOCATION, e.lugar)
        if (e.descripcion.isNotEmpty()) i.putExtra(CalendarContract.Events.DESCRIPTION, e.descripcion)
        Contenido.fechaEvento(e.inicio)?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        Contenido.fechaEvento(e.fin)?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (Contenido.esTodoElDia(e.inicio)) i.putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        lanzar(ctx, i, "No hay app de calendario.")
    }
}
