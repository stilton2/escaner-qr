package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up values only, with the same shapes as the real labels (S-J4). */
class EspecialTest {
    private fun de(t: String, formato: String = "QR Code") = Especial.de(t, formato)

    // ---------- 1. Etiqueta JSON de equipo ----------

    @Test fun jsonDeEquipoConSerie() {
        val e = de("""{"dm":"AB-CD123-X","dma":"10:20:30:AB:CD:EF","ds":"7F0ABCDPAZ12345"}""") as Especial.Equipo
        assertEquals("AB-CD123-X", e.modelo)
        assertEquals(listOf("10:20:30:AB:CD:EF"), e.macs)
        assertEquals("7F0ABCDPAZ12345", e.serie)
        assertEquals("Equipo", e.tipo)
    }

    @Test fun jsonDeEquipoSinSerieYMacSinSeparadores() {
        val e = de("""{"dm": "XY_DOMO_V9", "dma": "a1b2c3d4e5f6"}""") as Especial.Equipo
        assertEquals("XY_DOMO_V9", e.modelo)
        assertEquals(listOf("A1:B2:C3:D4:E5:F6"), e.macs)   // with ':' and in capitals
        assertEquals("", e.serie)
    }

    // ---------- 2. MAC ----------

    @Test fun macSiempreConDosPuntosYMayusculas() {
        val json = """{"dm":"AB-CD123-X","dma":"a1b2c3d4e5f6"}"""
        val c = Contenido.analizar(json)
        assertEquals("A1:B2:C3:D4:E5:F6", Especial.queCopiar(json, c, soloMac = true))
        assertEquals(json, Especial.queCopiar(json, c, soloMac = false))
        assertEquals("A1B2C3D4E5F6", Especial.sinDosPuntos("A1:B2:C3:D4:E5:F6"))
    }

    @Test fun doceHexadecimalesSueltos() {
        // Like the alarm panels' labels (same maker prefix, made-up end).
        val e = de("0A1B2C3D4E5F", "Code 128") as Especial.Equipo
        assertEquals(listOf("0A:1B:2C:3D:4E:5F"), e.macs)
        assertTrue(e.soloMac)
        assertEquals("MAC", e.tipo)
        assertEquals("0A:1B:2C:3D:4E:5F", Especial.queCopiar("0A1B2C3D4E5F", Contenido.analizar("0A1B2C3D4E5F", "Code 128"), true))
        val s = de("BC:32:5F:01:a2:B3") as Especial.Equipo
        assertEquals(listOf("BC:32:5F:01:A2:B3"), s.macs)
    }

    @Test fun doceCifrasSolasNoSonMac() {
        assertNull(de("123456789012", "Code 128"))
        assertNull(de("841234567890", "QR Code"))
    }

    // ---------- 3. SIM ----------

    @Test fun simDe19Y20Cifras() {
        val s19 = de("8901234567890123456", "Code 128") as Especial.Sim
        assertEquals("8901234567890123456", s19.iccid)
        assertEquals("SIM (ICCID)", s19.tipo)
        val s20 = de("89012345678901234564", "Code 128") as Especial.Sim
        assertEquals("89012345678901234564", s20.iccid)
        assertEquals("8901 2345 6789 0123 456", Especial.agrupar4("8901234567890123456"))
        // Copied whole, without spaces, even with spaces around.
        val t = " 8901234567890123456 "
        assertEquals("8901234567890123456", Especial.queCopiar(t, Contenido.analizar(t, "Code 128"), soloMac = true))
    }

    @Test fun noSonSim() {
        assertNull(de("8412345678901", "EAN-13"))            // EAN-13: a product
        assertNull(de("8412345678901", "Code 128"))          // 13 digits, not 19
        assertNull(de("890123456789012345", "Code 128"))     // 18 digits
        assertNull(de("890123456789012345645", "Code 128"))  // 21 digits
        assertNull(de("7934071234567890123", "Code 128"))    // does not start with 89
        assertNull(de("8901 2345 6789 0123 456", "Code 128")) // with spaces: someone's text, not the barcode
        assertTrue(Contenido.analizar("8412345678901", "EAN-13") is Contenido.Producto)
    }

    // ---------- 4. Artículo + modelo + serie + fecha ----------

    @Test fun articuloCompleto() {
        val t = "1234-000056 IPC-Z99 5K01B2C 03/02/2024 5K01B2CQRS9X001"
        val a = de(t, "Code 128") as Especial.Articulo
        assertEquals("1234-000056", a.codigo)
        assertEquals("IPC-Z99", a.modelo)
        assertEquals("5K01B2CQRS9X001", a.serie)
        assertEquals("03/02/2024", a.fecha)
        assertEquals(listOf("5K01B2C"), a.otros)
        assertEquals("5K01B2CQRS9X001", Especial.queCopiar(t, Contenido.analizar(t, "Code 128"), soloMac = true))
    }

    @Test fun articuloSoloCodigo() {
        val a = de("5678-000012", "Code 128") as Especial.Articulo
        assertEquals("5678-000012", a.codigo)
        assertEquals("", a.modelo); assertEquals("", a.serie); assertEquals("", a.fecha)
        assertEquals("5678-000012", Especial.queCopiar("5678-000012", Contenido.analizar("5678-000012", "Code 128"), true))
    }

    @Test fun articuloConUnaSolaPieza() {
        assertEquals("NVR-4X", (de("1111-222222 NVR-4X") as Especial.Articulo).modelo)   // with a dash: model
        val a = de("1111-222222 9Z8Y7X6W5V") as Especial.Articulo                          // without: serial
        assertEquals("9Z8Y7X6W5V", a.serie); assertEquals("", a.modelo)
        // No serial: the whole code is copied.
        val t = "1111-222222 NVR-4X 01/01/2025"
        assertEquals(t, Especial.queCopiar(t, Contenido.analizar(t), true))
    }

    @Test fun noSonArticulo() {
        assertNull(de("123-000034 XY-K22"))                  // 3 + 6 digits
        assertNull(de("2023-11-05 10:02:30"))                 // a date and time
        assertNull(de("1234-567890 esto es una frase larga con muchas palabras"))
        assertNull(de("Ref 1234-567890"))                     // the code must come first
        assertNull(de("1234-567890 ¿qué?"))
    }

    // ---------- 5. Lo demás, como estaba ----------

    @Test fun loDemasNoCambia() {
        assertNull(de("Hola, qué tal"))
        assertNull(de("WIFI:S:Casa;T:WPA;P:a1b2c3d4e5f6;;"))
        assertNull(de("https://example.org/8901234567890123456"))
        assertEquals("Hola", Especial.queCopiar("Hola", Contenido.analizar("Hola"), true))
        val w = "WIFI:S:x;P:a1b2c3d4e5f6;;"
        assertEquals(w, Especial.queCopiar(w, Contenido.analizar(w), true))
    }
}
