package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Crossing the history with the networks, and the dates in words. Invented data. */
class EscaneosYCuandoTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    // 2026-11-16 10:00:00 CET
    private val lunes = 1794819600000L

    private val filas = listOf(
        FilaHistorial(1, 1794675780000L, "WIFI:S:Casa Ejemplo;T:WPA;P:12345678;;"),           // 14/11 18:03
        FilaHistorial(2, 1794556800000L, "wifi:T:WPA;S:Casa Ejemplo;P:otra;;"),                 // 13/11, lower case
        FilaHistorial(3, 1758010976000L, "WIFI:S:Bar\\; Uno;T:nopass;;"),                        // escaped ;
        FilaHistorial(4, 1794556800000L, "  WIFI:S:Multi;\nT:WPA2-PSK-AES;\nP:abcdefgh;;"),     // QR Bot multi-line, spaces before
        FilaHistorial(5, 1794556800000L, "https://ejemplo.org/WIFI:S:NoEsWifi;;"),             // not a wifi code
        FilaHistorial(6, 1794556800000L, "WIFI:T:WPA;P:sinnombre;;"),                           // no name
    )

    @Test fun lecturasPorNombre() {
        val e = Escaneos.porNombre(filas)
        assertEquals(setOf("Casa Ejemplo", "Bar; Uno", "Multi"), e.keys)
        assertEquals(listOf(1L, 2L), e.getValue("Casa Ejemplo").map { it.id }) // newest first
        assertEquals(1, e.getValue("Bar; Uno").size)
    }

    @Test fun asignarANombreExacto() {
        val redes = listOf(
            Red(0, "Casa Ejemplo", Seguridad.WPA, null, false, Origen.desde(null), false),
            Red(1, "casa ejemplo", Seguridad.WPA, null, false, Origen.desde(null), false),
        )
        val r = Escaneos.asignar(redes, Escaneos.porNombre(filas))
        assertEquals(2, r[0].escaneos.size)
        assertTrue(r[1].escaneos.isEmpty()) // Android names are case sensitive
    }

    @Test fun haceEnPalabras() {
        val h = 3_600_000L
        assertEquals("hoy", Cuando.hace(lunes - 2 * h, lunes, madrid))
        assertEquals("ayer", Cuando.hace(lunes - 12 * h, lunes, madrid))            // Sunday 22:00
        assertEquals("hace 3 días", Cuando.hace(lunes - 3 * 24 * h, lunes, madrid))
        assertEquals("hace 2 semanas", Cuando.hace(lunes - 15 * 24 * h, lunes, madrid))
        assertEquals("hace 5 meses", Cuando.hace(lunes - 150 * 24 * h, lunes, madrid))
        assertEquals("hace 1 año", Cuando.hace(lunes - 400 * 24 * h, lunes, madrid))
        assertEquals("hace 3 años", Cuando.hace(lunes - 1100 * 24 * h, lunes, madrid))
    }

    @Test fun fechasCortasYLargas() {
        assertEquals("14/11", Cuando.corta(1794675780000L, lunes, madrid))
        assertEquals("16/09/2025", Cuando.corta(1758010976000L, lunes, madrid))
        assertEquals("14/11/2026 18:03", Cuando.larga(1794675780000L, madrid))
        assertEquals("16/09/2025 10:22", Cuando.larga(1758010976000L, madrid))
    }

    @Test fun duraciones() {
        assertEquals("45 s", Cuando.duracion(45))
        assertEquals("12 min", Cuando.duracion(12 * 60 + 5))
        assertEquals("1 h 20 min", Cuando.duracion(80 * 60))
        assertEquals("1 día", Cuando.duracion(86_400))
        assertEquals("3 días 4 h", Cuando.duracion(3 * 86_400 + 4 * 3600 + 59))
    }

    @Test fun lineaDeLaLista() {
        val base = Red(0, "Casa Ejemplo", Seguridad.WPA, null, false, Origen.desde(null), false)
        val tres = lunes - 3 * 86_400_000L
        val conTodo = base.copy(uso = Uso(ultimaVez = tres, conexiones = 4), escaneos = listOf(Escaneo(1, 1794675780000L)))
        assertEquals("Última vez: hace 3 días · Escaneada el 14/11", Cuando.lineaLista(conTodo, lunes, madrid))
        assertEquals("Nunca conectada", Cuando.lineaLista(base.copy(uso = Uso(conexiones = 0)), lunes, madrid))
        assertEquals("Conectada ahora", Cuando.lineaLista(base.copy(detalle = Detalle(0, conectadaAhora = true), uso = Uso(ultimaVez = tres)), lunes, madrid))
        assertEquals("", Cuando.lineaLista(base, lunes, madrid))
        assertEquals("Escaneada el 16/09/2025", Cuando.lineaLista(base.copy(escaneos = listOf(Escaneo(3, 1758010976000L))), lunes, madrid))
    }
}
