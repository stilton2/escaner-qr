package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Invented points (the middle of the sea) with the shape Samsung's getConfiguredNetworkLocations() gives. */
class UbicacionesTest {
    private fun punto(la: Double, lo: Double) = hashMapOf("latitude" to la, "longitude" to lo)

    @Test fun claves() {
        assertEquals("Casa" to Ubicaciones.Familia.CLAVE, Ubicaciones.leerClave("\"Casa\"WPA_PSK"))
        assertEquals("Casa" to Ubicaciones.Familia.CLAVE, Ubicaciones.leerClave("\"Casa\"SAE-0"))
        assertEquals("Libre" to Ubicaciones.Familia.ABIERTA, Ubicaciones.leerClave("\"Libre\"NONE"))
        assertEquals("a\"b" to Ubicaciones.Familia.CLAVE, Ubicaciones.leerClave("\"a\"b\"WPA_PSK"))
        assertEquals("Hola" to Ubicaciones.Familia.CLAVE, Ubicaciones.leerClave("486f6c61WPA_PSK"))
        assertNull(Ubicaciones.leerClave("raro"))
    }

    @Test fun mapaYAsignar() {
        val mapa = hashMapOf<String, Any?>(
            "\"Casa\"WPA_PSK" to punto(10.5, -20.25),
            "\"Bar\"NONE" to punto(11.0, -21.0),
            "\"Malo\"WPA_PSK" to punto(95.0, 0.0),
            "\"Cero\"WPA_PSK" to punto(0.0, 0.0),
            "\"Sin\"WPA_PSK" to hashMapOf("latitude" to 1.0),
        )
        val u = Ubicaciones.desdeMapa(mapa)
        assertEquals(2, u.size)
        val origen = Origen(Origen.Tipo.AJUSTES, null, null)
        val redes = listOf(
            Red(0, "Casa", Seguridad.WPA, null, false, origen, false),
            Red(1, "Bar", Seguridad.WPA, null, false, origen, false), // other family, but the only point with that name
            Red(2, "Nada", Seguridad.WPA, null, false, origen, false),
        )
        val r = Ubicaciones.asignar(redes, u)
        assertEquals(Ubicacion(10.5, -20.25), r[0].ubicacion)
        assertEquals(Ubicacion(11.0, -21.0), r[1].ubicacion)
        assertNull(r[2].ubicacion)
    }

    @Test fun enlaceMapa() {
        assertEquals("geo:10.500000,-20.250000?q=10.500000,-20.250000(Casa%20Ejemplo)", Ubicacion(10.5, -20.25).geo("Casa Ejemplo"))
        assertEquals("10.50000, -20.25000", Ubicacion(10.5, -20.25).texto())
    }
}
