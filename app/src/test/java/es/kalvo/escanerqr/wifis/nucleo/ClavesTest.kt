package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ClavesTest {
    @Test fun nombres() {
        assertEquals("Casa", Claves.ssidLegible("\"Casa\""))
        assertEquals("Hola", Claves.ssidLegible("486f6c61"))
        assertNotNull(Claves.problemaNombre(""))
        assertNotNull(Claves.problemaNombre("x".repeat(33)))
        assertNull(Claves.problemaNombre("x".repeat(32)))
    }

    @Test fun clavesWpa() {
        assertNotNull(Claves.problemaClave(Seguridad.WPA, "1234567"))
        assertNull(Claves.problemaClave(Seguridad.WPA, "12345678"))
        assertNotNull(Claves.problemaClave(Seguridad.WPA, "x".repeat(64)))
        assertNull(Claves.problemaClave(Seguridad.WPA, "a".repeat(64)))
        assertNotNull(Claves.problemaClave(Seguridad.WPA, "contraseña1"))
        assertNull(Claves.problemaClave(Seguridad.ABIERTA, null))
        assertNull(Claves.problemaClave(Seguridad.WEP, "12345"))
        assertNotNull(Claves.problemaClave(Seguridad.WEP, "1234"))
    }

    @Test fun buscarSinTildes() { assertEquals("cafe pena", Claves.paraBuscar("Café Peña".replace("ñ", "n"))) }
}
