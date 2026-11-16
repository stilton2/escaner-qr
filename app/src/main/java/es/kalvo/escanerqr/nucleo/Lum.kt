package es.kalvo.escanerqr.nucleo

/**
 * A grey picture (one byte of brightness per pixel, row after row), the way the reader wants it. Pure Kotlin (tested).
 * S-J6: the camera and the photo cut, shrink, enlarge and mirror it before reading, to catch small, far and mirrored codes.
 */
class Lum(val ancho: Int, val alto: Int, val px: ByteArray = ByteArray(ancho * alto)) {
    init { require(ancho > 0 && alto > 0 && px.size >= ancho * alto) { "Tamaño no válido" } }

    fun en(x: Int, y: Int): Int = px[y * ancho + x].toInt() and 0xFF

    /** The part [r] (clamped to the picture). */
    fun recortar(r: Recorte): Lum {
        val c = r.dentroDe(ancho, alto)
        val out = ByteArray(c.ancho * c.alto)
        for (y in 0 until c.alto) System.arraycopy(px, (c.y + y) * ancho + c.x, out, y * c.ancho, c.ancho)
        return Lum(c.ancho, c.alto, out)
    }

    /** [f] times smaller, each new pixel the average of an f×f block (less noise than skipping pixels). */
    fun reducir(f: Int): Lum {
        if (f <= 1) return this
        val w = ancho / f; val h = alto / f
        if (w < 1 || h < 1) return this
        val out = ByteArray(w * h)
        val n = f * f
        for (y in 0 until h) {
            val y0 = y * f
            for (x in 0 until w) {
                val x0 = x * f
                var s = 0
                for (dy in 0 until f) {
                    val fila = (y0 + dy) * ancho + x0
                    for (dx in 0 until f) s += px[fila + dx].toInt() and 0xFF
                }
                out[y * w + x] = (s / n).toByte()
            }
        }
        return Lum(w, h, out)
    }

    /**
     * [f] times bigger. The reader needs about 2 pixels per module: a tiny Data Matrix far away (about 25 px wide) reads once
     * enlarged (measured with a real label, S-J6). [suave] = bilinear (better at ×3); otherwise each pixel is copied (fast).
     */
    fun ampliar(f: Int, suave: Boolean = false): Lum {
        if (f <= 1) return this
        val w = ancho * f; val h = alto * f
        val out = ByteArray(w * h)
        if (!suave) {
            for (y in 0 until alto) {
                val o = y * ancho
                val fila = ByteArray(w)
                for (x in 0 until ancho) { val v = px[o + x]; val b = x * f; for (k in 0 until f) fila[b + k] = v }
                for (k in 0 until f) System.arraycopy(fila, 0, out, (y * f + k) * w, w)
            }
        } else {
            for (y in 0 until h) {
                // Centre of the new pixel in old coordinates, in 1/256ths.
                val sy = ((y * 2 + 1) * 256 / (2 * f) - 128).coerceIn(0, (alto - 1) * 256)
                val y0 = sy shr 8; val y1 = minOf(y0 + 1, alto - 1); val fy = sy and 255
                for (x in 0 until w) {
                    val sx = ((x * 2 + 1) * 256 / (2 * f) - 128).coerceIn(0, (ancho - 1) * 256)
                    val x0 = sx shr 8; val x1 = minOf(x0 + 1, ancho - 1); val fx = sx and 255
                    val a = en(x0, y0) * (256 - fx) + en(x1, y0) * fx
                    val b = en(x0, y1) * (256 - fx) + en(x1, y1) * fx
                    out[y * w + x] = ((a * (256 - fy) + b * fy) shr 16).toByte()
                }
            }
        }
        return Lum(w, h, out)
    }

    /** Left-right mirror (a code seen from behind a glass or printed backwards). */
    fun espejo(): Lum {
        val out = ByteArray(ancho * alto)
        for (y in 0 until alto) {
            val o = y * ancho
            for (x in 0 until ancho) out[o + x] = px[o + ancho - 1 - x]
        }
        return Lum(ancho, alto, out)
    }

    /**
     * Light ↔ dark. zxing-cpp's «tryInvert» only covers QR, Data Matrix and Aztec: white bars on a dark background (barcodes,
     * PDF417, MaxiCode) need this (S-J6, measured).
     */
    fun invertir(): Lum {
        val out = ByteArray(ancho * alto)
        for (i in 0 until ancho * alto) out[i] = (255 - (px[i].toInt() and 0xFF)).toByte()
        return Lum(ancho, alto, out)
    }

    /** Average brightness 0–255 looking at one pixel in [paso]×[paso] (cheap, for the automatic torch). */
    fun brillo(paso: Int = 8): Int {
        var s = 0L; var n = 0
        var y = 0
        while (y < alto) { var x = 0; while (x < ancho) { s += en(x, y); n++; x += paso }; y += paso }
        return if (n == 0) 0 else (s / n).toInt()
    }
}

/** A rectangle in pixels. */
data class Recorte(val x: Int, val y: Int, val ancho: Int, val alto: Int) {
    fun dentroDe(w: Int, h: Int): Recorte {
        val x0 = x.coerceIn(0, w - 1); val y0 = y.coerceIn(0, h - 1)
        return Recorte(x0, y0, ancho.coerceIn(1, w - x0), alto.coerceIn(1, h - y0))
    }

    companion object {
        /** The middle part: [fraccion] of each side, centred (even numbers, like the camera likes). */
        fun central(w: Int, h: Int, fraccion: Float): Recorte {
            fun par(v: Int) = if (v >= 2) v - v % 2 else v.coerceAtLeast(1)
            val cw = par((w * fraccion).toInt().coerceIn(1, w))
            val ch = par((h * fraccion).toInt().coerceIn(1, h))
            val x = (w - cw) / 2; val y = (h - ch) / 2
            return Recorte(x - x % 2, y - y % 2, cw, ch)
        }
    }
}
