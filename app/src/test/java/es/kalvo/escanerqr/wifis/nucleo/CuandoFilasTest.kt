package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

/** Values of the «DATOS DE LA RED» rows (S-J4). Made-up dates. */
class CuandoFilasTest {
    private val tz = TimeZone.getTimeZone("Europe/Madrid")
    private val ahora = 1_763_294_400_000L // 16/11/2025 13:00 Madrid
    private val dia = 86_400_000L

    @Test fun fechaCorta() {
        assertEquals("16 nov 2025", Cuando.fechaCorta(ahora, tz))
        assertEquals("3 mar 2025", Cuando.fechaCorta(ahora - 258 * dia, tz))
    }

    @Test fun escaneada() {
        assertEquals("", Cuando.escaneada(emptyList(), ahora, tz))
        assertEquals("1 vez · hace 2 días", Cuando.escaneada(listOf(Escaneo(1, ahora - 2 * dia)), ahora, tz))
        assertEquals("3 veces · última hoy",
            Cuando.escaneada(listOf(Escaneo(3, ahora - 3_600_000), Escaneo(2, ahora - 5 * dia), Escaneo(1, ahora - 40 * dia)), ahora, tz))
        assertEquals("Hace 2 días", Cuando.haceSolo(ahora - 2 * dia, ahora, tz))
    }
}
