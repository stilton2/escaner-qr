package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Made-up labels only (same shapes as real ones). */
class MacTest {
    private fun macs(t: String) = Mac.buscar(t).macs

    @Test fun jsonDeEtiquetaConClavesCortas() {
        val e = Mac.buscar("""{"dm": "XY-ABCD-002e","dma": "24:52:6A:0B:1C:2D"}""")
        assertEquals(listOf("24:52:6A:0B:1C:2D"), e.macs)
        assertEquals("XY-ABCD-002e", e.modelo)
    }

    @Test fun jsonOtrasClavesYSinSeparadores() {
        assertEquals(listOf("AA:BB:CC:00:11:22"), macs("""{"MAC":"aabbcc001122","SN":"123456789012"}"""))
        assertEquals("123456789012", Mac.buscar("""{"MAC":"aabbcc001122","SN":"123456789012"}""").serie)
        // Only digits, but the key says MAC: it counts.
        assertEquals(listOf("00:11:22:33:44:55"), macs("""{"mac": "001122334455"}"""))
        assertEquals(listOf("00:11:22:33:44:55"), macs("""{"lanMac":"00-11-22-33-44-55"}"""))
    }

    @Test fun conEtiquetaMac() {
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), macs("MAC:AA:BB:CC:DD:EE:FF"))
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), macs("mac: aa-bb-cc-dd-ee-ff"))
        assertEquals(listOf("00:11:22:33:44:55"), macs("SN:9F01234567 MAC:001122334455 Model:IPC-X1"))
        assertEquals(listOf("00:11:22:33:44:55"), macs("MAC Address = 001122334455"))
    }

    @Test fun formasSueltas() {
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), macs("AA-BB-CC-DD-EE-FF"))
        assertEquals(listOf("AA:BB:CC:DD:EE:FF"), macs("AABB.CCDD.EEFF"))
        assertEquals(listOf("12:34:56:78:90:12"), macs("12:34:56:78:90:12"))  // separators: counts even if all digits
        assertEquals(listOf("3C:EF:8C:01:A2:B3"), macs("S/N: 7K0ABCD12345\nM/N: XVR1B04\n3CEF8C01A2B3"))
    }

    @Test fun textoLargoConSerieYModelo() {
        val e = Mac.buscar("Model: DH-IPC-HFW1230S SN: 6G0ABC1DEF234 MAC: 3c:ef:8c:aa:bb:cc")
        assertEquals(listOf("3C:EF:8C:AA:BB:CC"), e.macs)
        assertEquals("DH-IPC-HFW1230S", e.modelo)
        assertEquals("6G0ABC1DEF234", e.serie)
    }

    @Test fun variasSinRepetir() {
        assertEquals(listOf("AA:BB:CC:DD:EE:01", "AA:BB:CC:DD:EE:02"),
            macs("LAN MAC: AA:BB:CC:DD:EE:01\nWIFI MAC: AA:BB:CC:DD:EE:02\nmac AA-BB-CC-DD-EE-01"))
    }

    @Test fun falsosPositivos() {
        assertTrue(macs("841000000000").isEmpty())           // 12 digits alone (EAN-like)
        assertTrue(macs("SN 123456789012 lote 4").isEmpty())  // serial only digits
        assertTrue(macs("1234567890123").isEmpty())          // 13 digits
        assertTrue(macs("AABBCCDDEEFF00").isEmpty())         // 14 hex
        assertTrue(macs("AA:BB:CC:DD:EE:FF:00").isEmpty())   // 7 groups (EUI-64 or other)
        assertTrue(macs("AA:BB:CC:DD:EE").isEmpty())         // 5 groups
        assertTrue(macs("AA:BB-CC:DD-EE:FF").isEmpty())      // mixed separators
        assertTrue(macs("2023-11-05 10:02:30").isEmpty())
        assertTrue(macs("Hola, qué tal").isEmpty())
        assertTrue(macs("DEADBEEFCAFE").isEmpty())           // only letters, no MAC word
    }

    @Test fun soloEnTextoNoEnWifisNiEnlaces() {
        val wifi = "WIFI:S:Casa;T:WPA;P:a1b2c3d4e5f6;;"
        assertTrue(Mac.deContenido(wifi, Contenido.analizar(wifi)).vacia)
        val url = "https://example.org/a1b2c3d4e5f6"
        assertTrue(Mac.deContenido(url, Contenido.analizar(url)).vacia)
        val json = """{"dm": "XY-ABCD-002e","dma": "24:52:6A:0B:1C:2D"}"""
        assertEquals(listOf("24:52:6A:0B:1C:2D"), Mac.deContenido(json, Contenido.analizar(json)).macs)
    }

    @Test fun queSeCopia() {
        val json = """{"dm": "XY-ABCD-002e","dma": "24:52:6A:0B:1C:2D"}"""
        val c = Contenido.analizar(json)
        assertEquals("24:52:6A:0B:1C:2D", Mac.queCopiar(json, c, soloMac = true))
        assertEquals(json, Mac.queCopiar(json, c, soloMac = false))
        assertEquals("Hola", Mac.queCopiar("Hola", Contenido.analizar("Hola"), soloMac = true))
        val wifi = "WIFI:S:x;P:a1b2c3d4e5f6;;"
        assertEquals(wifi, Mac.queCopiar(wifi, Contenido.analizar(wifi), soloMac = true))
    }

    @Test fun comoMac() {
        assertEquals("AA:BB:CC:DD:EE:FF", Mac.comoMac(" aabb.ccdd.eeff "))
        assertEquals(null, Mac.comoMac("nada"))
    }
}
