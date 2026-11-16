package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CupoTest {
    @Test fun leeElTope() {
        assertEquals(200, Cupo.leerTope("200\n"))
        assertEquals(200, Cupo.leerTope("0xc8"))
        assertNull(Cupo.leerTope("-1"))                       // Android's default: no cap
        assertEquals(Cupo.TOPE_SI_NO_SE, Cupo.leerTope(null)) // could not ask: the safe side
        assertEquals(Cupo.TOPE_SI_NO_SE, Cupo.leerTope("Error: no such resource", 1))
        assertEquals(Cupo.TOPE_SI_NO_SE, Cupo.leerTope("???"))
    }

    /** 2026-11-16: 180 own networks + 405 from QR Bot on a phone with a cap of 200. */
    @Test fun elCasoDeHoyNoPasaDelTope() {
        assertEquals(0, Cupo.huecosEnBloque(200, 180))
        assertEquals(10, Cupo.huecosEnBloque(200, 170))
        assertEquals(0, Cupo.huecosEnBloque(200, 199))
        assertEquals(Int.MAX_VALUE, Cupo.huecosEnBloque(null, 5000))
    }

    @Test fun unaSolaCabeSiHaySitioOSiSoloSeActualiza() {
        val casa = RedListada(1, "Casa", listOf("wpa2-psk", "wpa3-sae^"))
        val llenas = listOf(casa) + (2..200).map { RedListada(it, "R$it", listOf("wpa2-psk")) }
        val nueva = DatosRed("Bar", Seguridad.WPA, "clave123", false)
        assertTrue(Cupo.cabeUna(200, llenas.dropLast(1), nueva))
        assertFalse(Cupo.cabeUna(200, llenas, nueva))
        assertTrue(Cupo.cabeUna(200, llenas, DatosRed("Casa", Seguridad.WPA, "otra1234", false))) // same name and type: update
        assertFalse(Cupo.cabeUna(200, llenas, DatosRed("Casa", Seguridad.ABIERTA, null, false)))   // other type: a new one
        assertTrue(Cupo.cabeUna(null, llenas, nueva))
    }
}
