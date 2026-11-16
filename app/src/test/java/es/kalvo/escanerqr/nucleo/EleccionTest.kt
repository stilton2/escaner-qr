package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Several codes in view: which one, the crosshair, the preferred kind, the labels (S-J6). Made-up values only. */
class EleccionTest {
    private fun en(cx: Float, cy: Float, lado: Float = 0.1f): Cuadro {
        val m = lado / 2
        return Cuadro.de(cx - m, cy - m, cx + m, cy - m, cx + m, cy + m, cx - m, cy + m)
    }
    private val mac = Hallado("00A1B2C3D4E5", "Data Matrix", "", en(0.7f, 0.7f))        // like an alarm panel's label (made up)
    private val sim = Hallado("8934071234567890123", "Code 128", "", en(0.3f, 0.3f))
    private val qr = Hallado("https://example.com/abc", "QR Code", "", en(0.52f, 0.5f))
    private val ean = Hallado("8412345678905", "EAN-13", "", en(0.5f, 0.85f))

    @Test fun unoSoloSeLeeDirecto() {
        assertEquals(Decision.Uno(qr), Eleccion.decidir(listOf(qr), false, Preferido.NINGUNO))
        assertEquals(Decision.Nada, Eleccion.decidir(emptyList(), false, Preferido.NINGUNO))
    }

    @Test fun elMismoDosVecesCuentaUno() {
        assertEquals(Decision.Uno(qr), Eleccion.decidir(listOf(qr, qr.copy(cuadro = en(0.1f, 0.1f))), false, Preferido.NINGUNO))
    }

    @Test fun variosSePreguntaEnOrdenDeLectura() {
        val d = Eleccion.decidir(listOf(ean, mac, qr, sim), false, Preferido.NINGUNO) as Decision.Varios
        assertEquals(listOf(sim, qr, mac, ean), d.lista)          // top to bottom
    }

    @Test fun tipoPreferidoSeLeeSinPreguntar() {
        assertEquals(Decision.Uno(mac), Eleccion.decidir(listOf(qr, mac, sim), false, Preferido.MAC))
        assertEquals(Decision.Uno(sim), Eleccion.decidir(listOf(qr, mac, sim), false, Preferido.SIM))
        assertEquals(Decision.Uno(qr), Eleccion.decidir(listOf(qr, mac, sim), false, Preferido.QR))
        // Two barcodes: the one nearer the middle.
        val otroEan = ean.copy(texto = "4006381333931", cuadro = en(0.5f, 0.55f))
        assertEquals(Decision.Uno(otroEan), Eleccion.decidir(listOf(ean, otroEan, mac), false, Preferido.BARRAS))
        // The preferred kind is not there: ask.
        assertTrue(Eleccion.decidir(listOf(qr, ean), false, Preferido.SIM) is Decision.Varios)
    }

    @Test fun conLaMiraSoloElDelCentro() {
        assertEquals(Decision.Uno(qr), Eleccion.decidir(listOf(mac, qr, sim), true, Preferido.NINGUNO))
        assertEquals(Decision.Nada, Eleccion.decidir(listOf(mac, sim), true, Preferido.NINGUNO))
        // The crosshair wins over the preferred kind.
        assertEquals(Decision.Uno(qr), Eleccion.decidir(listOf(mac, qr), true, Preferido.MAC))
        // A big code that covers the middle counts even if its centre is elsewhere.
        val grande = Hallado("Hola", "Aztec", "", en(0.4f, 0.4f, 0.4f))
        assertEquals(Decision.Uno(grande), Eleccion.decidir(listOf(grande, ean), true, Preferido.NINGUNO))
    }

    @Test fun tipos() {
        assertEquals(Preferido.MAC, Eleccion.tipo(mac.texto, mac.formato))
        assertEquals(Preferido.SIM, Eleccion.tipo(sim.texto, sim.formato))
        assertEquals(Preferido.QR, Eleccion.tipo(qr.texto, qr.formato))
        assertEquals(Preferido.QR, Eleccion.tipo("hola", "Micro QR"))
        assertEquals(Preferido.BARRAS, Eleccion.tipo(ean.texto, ean.formato))
        assertEquals(Preferido.NINGUNO, Eleccion.tipo("hola", "Data Matrix"))
    }

    @Test fun etiquetas() {
        assertEquals("MAC 00:A1:B2:…", Eleccion.etiqueta(mac.texto, mac.formato))
        assertEquals("SIM 8934 0712…", Eleccion.etiqueta(sim.texto, sim.formato))
        assertEquals("QR enlace example.com/ab…", Eleccion.etiqueta(qr.texto, qr.formato))
        assertEquals("EAN-13 8412345678905", Eleccion.etiqueta(ean.texto, ean.formato))
    }

    @Test fun etiquetaDeUnaCentralDeAlarma() {
        // Same shape as the real label (Data Matrix with only the MAC, 12 hexadecimals), made-up value:
        // it is an «Equipo» and the MAC is copied with ':'.
        val e = Especial.de("001D94A1B2C3", "Data Matrix") as Especial.Equipo
        assertEquals(listOf("00:1D:94:A1:B2:C3"), e.macs)
        assertEquals("00:1D:94:A1:B2:C3", Especial.queCopiar("001D94A1B2C3", Contenido.analizar("001D94A1B2C3", "Data Matrix"), soloMac = true))
    }
}
