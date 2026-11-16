package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Passes of the camera and the pictures, coordinates, automatic zoom and torch (S-J6). */
class CamaraTest {

    @Test fun planDeCamaraGrande() {
        val p = Plan.camara(1920, 1440, 0)
        assertEquals(3, p.size)
        assertEquals(-2, p[0].escala)                         // whole, halved (quick)
        assertEquals(1f, p[0].fraccion)
        assertEquals(0.5f, p[1].fraccion); assertEquals(1, p[1].escala)   // middle at full detail
    }

    @Test fun planDeCamaraPequena() {
        val p = Plan.camara(1280, 960, 0)
        assertEquals(1, p[0].escala)
        assertEquals(2, p[1].escala)                          // middle enlarged ×2
    }

    @Test fun losExtrasSeTurnan() {
        val extras = (0L until 8L).map { Plan.camara(1920, 1440, it)[2].nombre }
        assertEquals(listOf("histograma", "lejos", "espejo", "invertido", "histograma", "lejos", "espejo", "invertido"), extras)
        assertTrue(Plan.camara(1920, 1440, 3)[2].invertir)
        assertTrue(Plan.camara(1920, 1440, 1)[2].escala >= 3)
        assertTrue(Plan.camara(1920, 1440, 2)[2].espejo)
    }

    @Test fun planDeImagenSegunTamano() {
        val pequena = Plan.imagen(800, 600).map { it.nombre }
        assertTrue("x2" in pequena && "x3" in pequena)
        val grande = Plan.imagen(4000, 3000).map { it.nombre }
        assertFalse("x2" in grande); assertTrue("centro x2" in grande)
        assertTrue("espejo" in grande && "histograma" in grande && "invertido" in grande)
    }

    @Test fun deVueltaAlFotograma() {
        val r = Recorte(100, 50, 400, 300)
        // Enlarged ×2: half, plus the corner of the cut.
        assertEquals(Pair(150f, 100f), Pasada("x", fraccion = 0.5f, escala = 2).aMarco(r, 100f, 100f))
        // Halved: double.
        assertEquals(Pair(300f, 250f), Pasada("x", escala = -2).aMarco(r, 100f, 100f))
        // Mirrored: from the other side.
        assertEquals(Pair(450f, 60f), Pasada("x", espejo = true).aMarco(r, 50f, 10f))
    }

    @Test fun orientacion() {
        // Sensor frame 400×300 turned 90°: the top left corner of the sensor ends at the top right of the screen.
        assertEquals(Pair(1f, 0f), Orientacion.normalizar(0f, 0f, 400, 300, 90))
        assertEquals(Pair(0f, 0f), Orientacion.normalizar(0f, 0f, 400, 300, 0))
        assertEquals(Pair(1f, 1f), Orientacion.normalizar(0f, 0f, 400, 300, 180))
        assertEquals(Pair(0f, 1f), Orientacion.normalizar(0f, 0f, 400, 300, 270))
        assertEquals(0.75f, Orientacion.aspecto(400, 300, 90))
    }

    @Test fun ladoDelCuadro() {
        val c = Cuadro.de(0.4f, 0.45f, 0.6f, 0.45f, 0.6f, 0.55f, 0.4f, 0.55f)
        // Portrait 3:4: 0.2 of the width; 0.1 of the height = 0.133 of the width.
        assertEquals(0.2f, c.lado(0.75f), 1e-4f)
        assertTrue(c.contiene(0.5f, 0.5f))
        assertFalse(c.contiene(0.3f, 0.5f))
        assertTrue(c.contiene(0.38f, 0.5f, 0.03f))
    }

    @Test fun zoomAutoAcercaCodigosPequenosDelCentro() {
        val z = ZoomAuto.siguiente(0.1f, 0.5f, 0.5f, 1f, 10f, 2000)
        assertNotNull(z); assertEquals(1.6f, z!!, 1e-4f)      // step by step, not all at once
        assertNull(ZoomAuto.siguiente(0.3f, 0.5f, 0.5f, 1f, 10f, 2000))          // already big enough
        assertNull(ZoomAuto.siguiente(0.1f, 0.5f, 0.5f, 1f, 10f, 100))           // too soon after the last step
        assertNull(ZoomAuto.siguiente(0.1f, 0.5f, 0.5f, 5f, 10f, 2000))          // at the top
        assertEquals(3f, ZoomAuto.siguiente(0.05f, 0.5f, 0.5f, 2f, 3f, 2000)!!, 1e-4f) // never past the phone's maximum
    }

    @Test fun zoomAutoNoSacaElCodigoDePantalla() {
        // Code at 0.3 from the middle: ×1.33 at most (0.4 / 0.3).
        assertEquals(1.3333f, ZoomAuto.siguiente(0.1f, 0.8f, 0.5f, 1f, 10f, 2000)!!, 1e-3f)
        // Almost at the edge: no zoom.
        assertNull(ZoomAuto.siguiente(0.1f, 0.9f, 0.5f, 1f, 10f, 2000))
    }

    @Test fun linternaAutoSoloConOscuridadSeguida() {
        val l = LuzAuto(umbral = 45, ms = 1500)
        assertFalse(l.ver(20, 0)); assertFalse(l.ver(20, 1000))
        assertFalse(l.ver(120, 1200))                          // light again: start over
        assertFalse(l.ver(20, 1300)); assertTrue(l.ver(20, 2900))
        assertFalse(l.ver(20, 5000))                           // only once
        assertTrue(l.encendida)
    }

    @Test fun demasiadoCerca() {
        assertTrue(Cerca.demasiado(0.9f)); assertFalse(Cerca.demasiado(0.5f))
    }
}

class FiableTest {
    @Test fun itfCortoSoloSiVieneDeLaPasadaNormal() {
        assertTrue(Fiable.aceptar("ITF", "123456", false))
        assertFalse(Fiable.aceptar("ITF", "123456", true))
        assertTrue(Fiable.aceptar("ITF", "12345678901231", true))
        assertTrue(Fiable.aceptar("QR Code", "1", true))
        assertTrue(Fiable.esExtra(Pasada("x", escala = 2))); assertFalse(Fiable.esExtra(Pasada("x", escala = -2)))
    }
}
