package es.kalvo.escanerqr

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import es.kalvo.escanerqr.nucleo.Lectura

/**
 * History in SQLite inside the app (`databases/historial.db`); nothing leaves the phone.
 * The unique index (UTC date + UTC time + format + text) is what stops an import from duplicating.
 */
class Db private constructor(private val ctx: Context) : SQLiteOpenHelper(ctx, "historial.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE lecturas (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha TEXT NOT NULL, hora TEXT NOT NULL, zona TEXT NOT NULL,
                formato TEXT NOT NULL, texto TEXT NOT NULL, notas TEXT NOT NULL DEFAULT '',
                favorito INTEGER NOT NULL DEFAULT 0,
                fecha_utc TEXT NOT NULL, hora_utc TEXT NOT NULL, metadata TEXT NOT NULL DEFAULT '',
                instante INTEGER NOT NULL,
                origen TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL("CREATE UNIQUE INDEX lecturas_unica ON lecturas(fecha_utc, hora_utc, formato, texto)")
        db.execSQL("CREATE INDEX lecturas_instante ON lecturas(instante)")
    }

    /**
     * S-J5: column `origen` ("" = scanned with this app; else the app or file it was imported from), added in [onOpen] and
     * WITHOUT raising the database version, so going back to an older app (`adb install -r -d`) still opens the history
     * (old versions do not know the column and leave it at '').
     * Before this, only QR Bot's CSV could be imported, and that was QR Bot's history from before this app existed, so the
     * reads older than this app's install time are marked «QR Bot»; the rest were scanned here.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    /** A first test build of 2.4 had set the version to 2; going back to 1 changes nothing (the column stays). */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (db.isReadOnly) return
        val tiene = db.rawQuery("PRAGMA table_info(lecturas)", null).use { c ->
            var t = false
            while (c.moveToNext()) if (c.getString(1) == "origen") t = true
            t
        }
        if (tiene) return
        db.beginTransaction()
        try {
            db.execSQL("ALTER TABLE lecturas ADD COLUMN origen TEXT NOT NULL DEFAULT ''")
            val instalada = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).firstInstallTime } catch (e: Exception) { 0L }
            if (instalada > 0) db.execSQL("UPDATE lecturas SET origen = 'QR Bot' WHERE instante < ?", arrayOf<Any>(instalada))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    private fun valores(l: Lectura) = ContentValues().apply {
        put("fecha", l.fecha); put("hora", l.hora); put("zona", l.zona); put("formato", l.formato); put("texto", l.texto)
        put("notas", l.notas); put("favorito", if (l.favorito) 1 else 0); put("fecha_utc", l.fechaUtc); put("hora_utc", l.horaUtc)
        put("metadata", l.metadata); put("instante", l.instante); put("origen", l.origen)
    }

    private fun leer(c: Cursor) = Lectura(
        id = c.getLong(0), fecha = c.getString(1), hora = c.getString(2), zona = c.getString(3), formato = c.getString(4),
        texto = c.getString(5), notas = c.getString(6), favorito = c.getInt(7) != 0, fechaUtc = c.getString(8),
        horaUtc = c.getString(9), metadata = c.getString(10), origen = c.getString(11),
    )

    private val COLS = "id, fecha, hora, zona, formato, texto, notas, favorito, fecha_utc, hora_utc, metadata, origen"

    /** Id of the new row, or -1 if that same scan was already there. */
    fun insertar(l: Lectura): Long = writableDatabase.insertWithOnConflict("lecturas", null, valores(l), SQLiteDatabase.CONFLICT_IGNORE)

    /** Many at once in one transaction (import). Returns how many went in. */
    fun insertarVarias(ls: List<Lectura>): Int {
        val db = writableDatabase
        var n = 0
        db.beginTransaction()
        try {
            for (l in ls) if (db.insertWithOnConflict("lecturas", null, valores(l), SQLiteDatabase.CONFLICT_IGNORE) != -1L) n++
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return n
    }

    /** «No guardar repetidos»: the newest scan with the same text and format, if any. */
    fun buscarIgual(formato: String, texto: String): Lectura? =
        readableDatabase.rawQuery("SELECT $COLS FROM lecturas WHERE formato = ? AND texto = ? ORDER BY instante DESC LIMIT 1", arrayOf(formato, texto))
            .use { if (it.moveToFirst()) leer(it) else null }

    /** Moves a repeated scan to now (keeps its notes and star). */
    fun moverAhora(id: Long, ahora: Lectura) {
        val v = valores(ahora).apply { remove("notas"); remove("favorito"); remove("texto"); remove("formato") }
        writableDatabase.updateWithOnConflict("lecturas", v, "id = ?", arrayOf(id.toString()), SQLiteDatabase.CONFLICT_IGNORE)
    }

    /** Only the wifi codes (`WIFI:…`, any case), for the Wifis tab to say when each network was scanned. Read-only. */
    fun wifis(): List<es.kalvo.escanerqr.wifis.nucleo.FilaHistorial> =
        readableDatabase.rawQuery("SELECT id, instante, texto FROM lecturas WHERE texto LIKE '%WIFI:%'", null).use { c ->
            val out = ArrayList<es.kalvo.escanerqr.wifis.nucleo.FilaHistorial>(c.count)
            while (c.moveToNext()) out.add(es.kalvo.escanerqr.wifis.nucleo.FilaHistorial(c.getLong(0), c.getLong(1), c.getString(2)))
            out
        }

    fun una(id: Long): Lectura? =
        readableDatabase.rawQuery("SELECT $COLS FROM lecturas WHERE id = ?", arrayOf(id.toString())).use { if (it.moveToFirst()) leer(it) else null }

    /** Newest first. */
    fun todas(): List<Lectura> =
        readableDatabase.rawQuery("SELECT $COLS FROM lecturas ORDER BY instante DESC, id DESC", null).use { c ->
            val out = ArrayList<Lectura>(c.count)
            while (c.moveToNext()) out.add(leer(c))
            out
        }

    fun claves(): Set<String> = todas().mapTo(HashSet()) { it.clave }

    /** [Lectura.claveSinHora] of the imported reads (to not repeat a file without dates, S-J5). */
    fun clavesSinHora(): Set<String> = todas().filter { it.importada }.mapTo(HashSet()) { it.claveSinHora }

    /** How many reads came from each origin ("" = scanned here). Read-only, for the self-check (counts only). */
    fun cuentaPorOrigen(): Map<String, Int> =
        readableDatabase.rawQuery("SELECT origen, COUNT(*) FROM lecturas GROUP BY origen", null).use { c ->
            val m = LinkedHashMap<String, Int>()
            while (c.moveToNext()) m[c.getString(0)] = c.getInt(1)
            m
        }

    fun cuantas(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM lecturas", null).use { it.moveToFirst(); it.getInt(0) }

    fun ponerNotas(id: Long, notas: String) {
        writableDatabase.update("lecturas", ContentValues().apply { put("notas", notas) }, "id = ?", arrayOf(id.toString()))
    }

    fun ponerFavorito(id: Long, fav: Boolean) {
        writableDatabase.update("lecturas", ContentValues().apply { put("favorito", if (fav) 1 else 0) }, "id = ?", arrayOf(id.toString()))
    }

    fun borrar(ids: Collection<Long>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (id in ids) db.delete("lecturas", "id = ?", arrayOf(id.toString()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    /** Deletes the whole history; favourites stay unless [tambienFavoritos]. Returns how many went. */
    fun borrarTodo(tambienFavoritos: Boolean): Int =
        if (tambienFavoritos) writableDatabase.delete("lecturas", null, null)
        else writableDatabase.delete("lecturas", "favorito = 0", null)

    companion object {
        @Volatile private var instancia: Db? = null
        fun de(ctx: Context): Db = instancia ?: synchronized(this) { instancia ?: Db(ctx.applicationContext).also { instancia = it } }
    }
}
