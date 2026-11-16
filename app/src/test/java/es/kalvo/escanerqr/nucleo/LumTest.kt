package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Grey pictures: cut, shrink, enlarge, mirror (S-J6). */
class LumTest {
    private fun lum(w: Int, h: Int, vararg v: Int) = Lum(w, h, ByteArray(v.size) { v[it].toByte() })
    private fun valores(l: Lum) = IntArray(l.ancho * l.alto) { l.px[it].toInt() and 0xFF }

    @Test fun recortarCoge_laParteJusta() {
        val l = lum(4, 3, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12)
        val r = l.recortar(Recorte(1, 1, 2, 2))
        assertEquals(2, r.ancho); assertEquals(2, r.alto)
        assertArrayEquals(intArrayOf(6, 7, 10, 11), valores(r))
        // Out of the picture: clamped, never fails.
        assertEquals(1, l.recortar(Recorte(3, 2, 10, 10)).ancho)
    }

    @Test fun reducirPromedia() {
        val l = lum(4, 2, 0, 100, 200, 200, 100, 200, 200, 200)
        val r = l.reducir(2)
        assertEquals(2, r.ancho); assertEquals(1, r.alto)
        assertArrayEquals(intArrayOf(100, 200), valores(r))
    }

    @Test fun ampliarCopiandoYSuave() {
        val l = lum(2, 1, 0, 200)
        assertArrayEquals(intArrayOf(0, 0, 200, 200, 0, 0, 200, 200), valores(l.ampliar(2)))
        val s = l.ampliar(2, suave = true)
        assertEquals(4, s.ancho); assertEquals(2, s.alto)
        val fila = valores(s).take(4)
        assertEquals(0, fila[0]); assertEquals(200, fila[3])
        assert(fila[1] in 1..199 && fila[2] in 1..199 && fila[1] < fila[2])   // smooth ramp in between
    }

    @Test fun ampliarSuaveDejaIgualUnaZonaLisa() {
        val l = lum(3, 3, *IntArray(9) { 77 })
        assertEquals(setOf(77), valores(l.ampliar(3, suave = true)).toSet())
    }

    @Test fun espejoDaLaVuelta() {
        val l = lum(3, 2, 1, 2, 3, 4, 5, 6)
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), valores(l.espejo()))
        assertArrayEquals(valores(l), valores(l.espejo().espejo()))
    }

    @Test fun invertirClaroOscuro() {
        assertArrayEquals(intArrayOf(255, 155, 0), valores(lum(3, 1, 0, 100, 255).invertir()))
    }

    @Test fun brilloMedio() {
        assertEquals(50, lum(2, 2, 0, 100, 0, 100).brillo(1))
    }

    @Test fun recorteCentral() {
        val r = Recorte.central(1000, 800, 0.5f)
        assertEquals(Recorte(250, 200, 500, 400), r)
        val r2 = Recorte.central(1001, 801, 0.3f)
        assertEquals(0, r2.x % 2); assertEquals(0, r2.ancho % 2)
        assert(r2.x + r2.ancho <= 1001 && r2.y + r2.alto <= 801)
    }
}
