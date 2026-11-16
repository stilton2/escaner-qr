package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented names only. */
class YaGuardadaTest {
    private val normales = listOf(RedListada(0, "Casa Ejemplo", listOf("wpa2-psk")), RedListada(4, "Bar Uno", listOf("open")))
    private val sugerencias = listOf(
        Sugerencia(Origen.QR_BOT, "Taller Ejemplo", Seguridad.WPA, false, true),
        Sugerencia(Origen.QR_BOT, "Casa Ejemplo", Seguridad.WPA, false, true),
        Sugerencia("com.otra.app", "Casa Ejemplo", Seguridad.WPA, false, true),
    )

    @Test fun nueva() {
        val y = YaGuardada.buscar("Nueva Ejemplo", normales, sugerencias)
        assertTrue(y.nada)
    }

    @Test fun normalYSugerencias() {
        val y = YaGuardada.buscar("Casa Ejemplo", normales, sugerencias)
        assertTrue(y.normal); assertFalse(y.nada)
        assertEquals(listOf(Origen.QR_BOT, "com.otra.app"), y.sugeridaPor)
    }

    @Test fun soloDeQrBot() {
        val y = YaGuardada.buscar("Taller Ejemplo", normales, sugerencias)
        assertFalse(y.normal); assertEquals(listOf(Origen.QR_BOT), y.sugeridaPor)
    }

    @Test fun mayusculasCuentan() {
        // Wifi names are case sensitive: "casa ejemplo" is another network.
        assertTrue(YaGuardada.buscar("casa ejemplo", normales, sugerencias).nada)
    }

    @Test fun datosDesdeElEscaner() {
        assertEquals(DatosRed("Casa Ejemplo", Seguridad.WPA, "clave1234", false), DesdeEscaner.datos("Casa Ejemplo", "clave1234", "WPA", false))
        assertEquals(DatosRed("N", Seguridad.WPA3, "clave1234", true), DesdeEscaner.datos("N", "clave1234", "SAE", true))
        assertEquals(DatosRed("V", Seguridad.WEP, "12345", false), DesdeEscaner.datos("V", "12345", "WEP", false))
        // Open: never carries a password, even if the QR had one.
        assertEquals(DatosRed("Libre", Seguridad.ABIERTA, null, false), DesdeEscaner.datos("Libre", "sobra", "nopass", false))
    }

    @Test fun problemas() {
        assertNull(DesdeEscaner.problema(DesdeEscaner.datos("Casa Ejemplo", "clave1234", "WPA", false)))
        assertNotNull(DesdeEscaner.problema(DesdeEscaner.datos("Casa Ejemplo", "corta", "WPA", false)))
        assertNotNull(DesdeEscaner.problema(DesdeEscaner.datos("Casa Ejemplo", "", "WPA", false)))
        assertNull(DesdeEscaner.problema(DesdeEscaner.datos("Libre", "", "nopass", false)))
    }
}
