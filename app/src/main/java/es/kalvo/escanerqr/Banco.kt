package es.kalvo.escanerqr

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import es.kalvo.escanerqr.nucleo.Hallado
import es.kalvo.escanerqr.nucleo.Lum
import es.kalvo.escanerqr.nucleo.Plan
import java.io.File
import zxingcpp.BarcodeReader

/**
 * Test bench of the reader (S-J6), without the screen:
 * `adb shell am instrument -w [-e perfiles img24,img,cam24,cam] [-e variantes orig,espejo,mitad,invertido] es.kalvo.escanerqr/.Banco`.
 * Reads the sample pictures put in `Android/data/es.kalvo.escanerqr/files/banco/<carpeta>/` (zxing-cpp's `test/samples`, each picture
 * with its `.txt` or `.bin`) and answers ONLY counts per folder: how many it reads well, how many it reads wrong, time.
 * Profiles: `img24` = picture as v2.4, `img` = picture now, `cam24` = camera frame as v2.4, `cam` = camera now (4 frames' passes).
 * Folder `barrido/`: each picture shrunk step by step; answers the smallest code size (px) each profile still reads.
 * `-e camaras 1`: the phone's cameras (focus distance, zoom, lenses), nothing else. `-e borrar 1`: deletes that sample folder (only it).
 * Otherwise changes nothing; texts are never printed.
 */
class Banco : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        args = arguments ?: Bundle()
        start()
    }

    private var args = Bundle()

    override fun onStart() {
        val out = Bundle()
        try {
            if (args.getString("camaras") == "1") camaras(out)
            else if (args.getString("borrar") == "1") {
                // Clean up after a run: the app deletes its own sample folder (nothing else).
                val raiz = File(targetContext.getExternalFilesDir(null), "banco")
                out.putBoolean("borrado", !raiz.exists() || raiz.deleteRecursively())
            } else {
                val raiz = File(targetContext.getExternalFilesDir(null), "banco")
                val perfiles = (args.getString("perfiles") ?: "img24,img,cam24,cam").split(',')
                val variantes = (args.getString("variantes") ?: "orig").split(',')
                val solo = args.getString("carpetas")?.split(',')?.toSet()
                val carpetas = raiz.listFiles()?.filter { it.isDirectory && (solo == null || it.name in solo) }?.sortedBy { it.name } ?: emptyList()
                out.putInt("carpetas", carpetas.size)
                val totales = HashMap<String, IntArray>()
                for (c in carpetas) {
                    if (c.name == "barrido") { barrido(c, perfiles, out); continue }
                    for (v in variantes) {
                        val linea = carpeta(c, v, perfiles, totales)
                        out.putString("${c.name}@$v", linea)
                        Log.i("EscanerQR", "banco ${c.name}@$v $linea")
                    }
                }
                for ((k, t) in totales) out.putString("TOTAL@$k", "bien=${t[0]} mal=${t[1]} de=${t[2]} ms=${t[3]}")
            }
        } catch (e: Throwable) {
            out.putString("fallo", e.javaClass.name + ": " + (e.message?.take(160) ?: ""))
        }
        finish(Activity.RESULT_OK, out)
    }

    private val imagenes = setOf("png", "jpg", "jpeg", "webp")

    /** One folder in one variant: «img24 50/56 mal 1 · img 53/56 mal 1 …». */
    private fun carpeta(c: File, variante: String, perfiles: List<String>, totales: HashMap<String, IntArray>): String {
        val fotos = c.listFiles()?.filter { it.extension.lowercase() in imagenes }?.sortedBy { it.name } ?: return "vacía"
        val falsos = c.name.startsWith("falsepositives")
        val sinTexto = c.name.startsWith("multi") || c.name == "qrcode-7" || c.name == "pdf417-4"
        val bien = IntArray(perfiles.size); val mal = IntArray(perfiles.size); val ms = LongArray(perfiles.size)
        var n = 0
        for (f in fotos) {
            val esperado = esperado(f)
            if (!falsos && !sinTexto && esperado == null) continue
            val bmp0 = try { BitmapFactory.decodeFile(f.path) } catch (e: Throwable) { null } ?: continue
            val bmp = variante(bmp0, variante)
            n++
            for ((i, p) in perfiles.withIndex()) {
                val t0 = SystemClock.elapsedRealtime()
                val res = try { leer(p, bmp) } catch (e: Throwable) { emptyList() }
                ms[i] += SystemClock.elapsedRealtime() - t0
                when {
                    falsos -> if (res.isNotEmpty()) mal[i]++
                    sinTexto -> if (res.isNotEmpty()) bien[i]++
                    else -> {
                        val ok = res.any { coincide(it, esperado!!, c.name) }
                        if (ok) bien[i]++ else if (res.isNotEmpty()) mal[i]++
                    }
                }
            }
            if (bmp !== bmp0) bmp.recycle()
            bmp0.recycle()
        }
        for ((i, p) in perfiles.withIndex()) {
            val t = totales.getOrPut("$p@$variante") { IntArray(4) }
            if (!falsos) { t[0] += bien[i]; t[2] += n }
            t[1] += mal[i]; t[3] += ms[i].toInt()
        }
        return perfiles.indices.joinToString(" · ") { i -> "${perfiles[i]} ${bien[i]}/$n mal ${mal[i]} ${ms[i]}ms" }
    }

    /** Text and bytes of each code read by profile [p]. */
    private fun leer(p: String, bmp: Bitmap): List<Pair<String, ByteArray?>> = when (p) {
        "img24" -> BarcodeReader(Lector.opcionesV24(20)).read(bmp, Rect(0, 0, bmp.width, bmp.height), 0)
            .filter { it.error == null && it.text != null }.map { Pair(it.text!!, it.bytes) }
        "img" -> Lector.deBitmap(bmp).map { Pair(it.texto, null) }
        "cam24" -> {
            val lum = Lector.aLum(bmp)
            val b = Lector.aBitmap(lum)
            BarcodeReader(Lector.opcionesV24(1)).read(b, Rect(0, 0, b.width, b.height), 0)
                .also { b.recycle() }.filter { it.error == null && it.text != null }.map { Pair(it.text!!, it.bytes) }
        }
        "cam" -> camara(Lector.aLum(bmp)).map { Pair(it.texto, null) }
        else -> emptyList()
    }

    /** Four camera frames in a row with the same picture (the extras take turns), like holding the phone still half a second. */
    private fun camara(lum: Lum): List<Hallado> {
        val lector = BarcodeReader()
        for (n in 0L until 4L) {
            val l = ArrayList<Hallado>()
            for (p in Plan.camara(lum.ancho, lum.alto, n)) {
                for ((r, _) in Lector.pasada(lector, lum, p, 8, true)) {
                    val x = Lector.convertir(r) ?: continue
                    l += Hallado(x.texto, x.formato, x.metadata, es.kalvo.escanerqr.nucleo.Cuadro(FloatArray(8)))
                }
                if (l.isNotEmpty()) return l
            }
        }
        return emptyList()
    }

    private fun variante(b: Bitmap, v: String): Bitmap = when (v) {
        "espejo" -> Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { preScale(-1f, 1f) }, false)
        "mitad" -> Bitmap.createScaledBitmap(b, maxOf(1, b.width / 2), maxOf(1, b.height / 2), true)
        "invertido" -> {
            val o = Bitmap.createBitmap(b.width, b.height, Bitmap.Config.ARGB_8888)
            val cm = ColorMatrix(floatArrayOf(-1f, 0f, 0f, 0f, 255f, 0f, -1f, 0f, 0f, 255f, 0f, 0f, -1f, 0f, 255f, 0f, 0f, 0f, 1f, 0f))
            Canvas(o).drawBitmap(b, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(cm) })
            o
        }
        else -> if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false)
    }

    private class Esperado(val texto: String?, val bytes: ByteArray?)

    private fun esperado(f: File): Esperado? {
        val base = f.path.substringBeforeLast('.')
        File("$base.txt").takeIf { it.exists() }?.let { return Esperado(it.readText(Charsets.UTF_8), null) }
        File("$base.bin").takeIf { it.exists() }?.let { return Esperado(null, it.readBytes()) }
        return null
    }

    /** Same content? Lenient with what only changes how it is written (GS1 brackets, control characters, EAN add-on, UPC-A as EAN-13). */
    private fun coincide(r: Pair<String, ByteArray?>, e: Esperado, carpeta: String): Boolean {
        if (e.bytes != null) {
            if (r.second != null && r.second!!.contentEquals(e.bytes)) return true
            return limpio(r.first) == limpio(String(e.bytes, Charsets.ISO_8859_1)) || limpio(r.first) == limpio(String(e.bytes, Charsets.UTF_8))
        }
        val a = limpio(r.first); val b = limpio(e.texto ?: return false)
        if (a == b || "0$a" == b || a == "0$b") return true
        if (carpeta.contains("extension") && a.isNotEmpty() && b.startsWith(a)) return true
        return false
    }

    private val escape = Regex("<(NUL|SOH|STX|ETX|EOT|ENQ|ACK|BEL|BS|HT|LF|VT|FF|CR|SO|SI|DLE|DC1|DC2|DC3|DC4|NAK|SYN|ETB|CAN|EM|SUB|ESC|FS|GS|RS|US|DEL|U\\+[0-9A-F]+)>")
    private fun limpio(s: String) = escape.replace(s, "").filter { it.code >= 32 && it.code != 127 && it != '(' && it != ')' && !it.isWhitespace() && it != '�' }

    // ---------- barrido: how small can a code get ----------

    private fun barrido(c: File, perfiles: List<String>, out: Bundle) {
        val fotos = c.listFiles()?.filter { it.extension.lowercase() in imagenes }?.sortedBy { it.name } ?: return
        for ((k, f) in fotos.withIndex()) {
            val b0 = BitmapFactory.decodeFile(f.path) ?: continue
            val b = variante(b0, "orig")
            // Code size at full size: from the first read of the v2.4 picture profile.
            val r0 = BarcodeReader(Lector.opcionesV24(20)).read(b, Rect(0, 0, b.width, b.height), 0).firstOrNull { it.error == null }
            if (r0 == null) { out.putString("barrido$k", "no se lee ni entera"); continue }
            val lado0 = maxOf(r0.position.topRight.x - r0.position.topLeft.x, r0.position.bottomLeft.y - r0.position.topLeft.y).toFloat()
            val tipo = es.kalvo.escanerqr.nucleo.Eleccion.tipo(r0.text ?: "", es.kalvo.escanerqr.nucleo.Formatos.nombre(r0.format.name)).name
            val lineas = ArrayList<String>()
            for (p in perfiles) {
                var minimo = -1
                var lado = lado0.toInt()
                while (lado >= 12) {
                    val f2 = lado / lado0
                    val bs = Bitmap.createScaledBitmap(b, maxOf(1, (b.width * f2).toInt()), maxOf(1, (b.height * f2).toInt()), true)
                    val ok = try { leer(p, bs).any { limpio(it.first) == limpio(r0.text ?: "") } } catch (e: Throwable) { false }
                    bs.recycle()
                    if (ok) minimo = lado
                    lado = (lado * 0.9f).toInt().coerceAtMost(lado - 1)
                }
                lineas += "$p min ${minimo}px"
            }
            out.putString("barrido$k", "tipo $tipo, código ${lado0.toInt()}px · " + lineas.joinToString(" · "))
            if (b !== b0) b.recycle(); b0.recycle()
        }
    }


    // ---------- cámaras ----------

    private fun camaras(out: Bundle) {
        val cm = targetContext.getSystemService(CameraManager::class.java)
        for (id in cm.cameraIdList) {
            val ch = cm.getCameraCharacteristics(id)
            val cara = when (ch.get(CameraCharacteristics.LENS_FACING)) { CameraCharacteristics.LENS_FACING_BACK -> "atrás"; CameraCharacteristics.LENS_FACING_FRONT -> "delante"; else -> "otra" }
            val focales = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.joinToString("/") ?: "?"
            val enfoque = ch.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) // dioptres: 1/m
            val cerca = if (enfoque == null || enfoque <= 0f) "fijo" else "%.1f cm".format(100f / enfoque)
            val zoom = if (android.os.Build.VERSION.SDK_INT >= 30) ch.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.let { "${it.lower}-${it.upper}" } ?: "?" else "?"
            val fisicas = ch.physicalCameraIds.joinToString(",")
            val nivel = ch.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            val yuv = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)
                ?.sortedByDescending { it.width * it.height }?.take(3)?.joinToString(",") ?: "?"
            out.putString("camara$id", "$cara focal $focales mm, enfoca desde $cerca, zoom $zoom, físicas [$fisicas], nivel $nivel, yuv $yuv")
        }
    }
}
