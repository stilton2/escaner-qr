package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Sort and filters of the Wifis tab (S-J4, v2.3). Made-up networks and dates. */
class OrdenRedesTest {
    private val tz = TimeZone.getTimeZone("Europe/Madrid")
    private val ahora = 1_763_294_400_000L // 16/11/2025 13:00 Madrid
    private val dia = 86_400_000L
    private val ajustes = Origen(Origen.Tipo.AJUSTES, null, "android.uid.system:1000")
    private val qrBot = Origen(Origen.Tipo.APP, Origen.QR_BOT, Origen.QR_BOT)

    private fun red(ssid: String, sug: Boolean = false, ultima: Long? = null, creada: Long? = null, escaneo: Long? = null,
                    ubic: Boolean = false, ahoraConectada: Boolean = false) = Red(
        id = if (sug) -1 else ssid.hashCode() and 0xffff, ssid = ssid, seguridad = Seguridad.WPA, clave = null, oculta = false,
        origen = if (sug) qrBot else ajustes, sugerencia = sug,
        ubicacion = if (ubic) Ubicacion(40.0, -3.0) else null,
        detalle = if (sug) null else Detalle(id = 1, conectadaAhora = ahoraConectada),
        uso = Uso(creada = creada, ultimaVez = ultima),
        escaneos = listOfNotNull(escaneo?.let { Escaneo(1, it) }),
    )

    private val redes = listOf(
        red("bravo", ultima = ahora - 2 * dia, creada = ahora - 400 * dia, escaneo = ahora - 3 * dia, ubic = true),
        red("Álamo", ultima = ahora - 20 * dia, creada = ahora - 5 * dia),
        red("charlie", ultima = null, creada = ahora - 100 * dia, escaneo = ahora - 40 * dia),
        red("delta", sug = true, escaneo = ahora - 1 * dia),
        red("eco", ultima = ahora - 300 * dia, ahoraConectada = true, creada = null),
    )

    private fun nombres(l: List<Red>) = l.map { it.ssid }
    private fun ap(f: FiltroRedes = FiltroRedes(), o: Orden) = nombres(OrdenRedes.aplicar(redes, f, o, ahora, tz))

    @Test fun ordenPorNombreSinAcentos() {
        assertEquals(listOf("Álamo", "bravo", "charlie", "delta", "eco"), ap(o = Orden.NOMBRE))
    }

    @Test fun ordenPorFechasMasRecientePrimeroYSinFechaAlFinal() {
        // «eco» is connected now: first. «charlie» and «delta» never connected: at the end, by name.
        assertEquals(listOf("eco", "bravo", "Álamo", "charlie", "delta"), ap(o = Orden.ULTIMA_CONEXION))
        assertEquals(listOf("Álamo", "charlie", "bravo", "delta", "eco"), ap(o = Orden.GUARDADA))
        assertEquals(listOf("delta", "bravo", "charlie", "Álamo", "eco"), ap(o = Orden.ESCANEADA))
    }

    @Test fun filtrosDeOrigenUbicacionYEscaneadas() {
        assertEquals(listOf("Álamo", "bravo", "charlie", "eco"), ap(FiltroRedes(origen = 1), Orden.NOMBRE))
        assertEquals(listOf("delta"), ap(FiltroRedes(origen = 2), Orden.NOMBRE))
        assertEquals(listOf("bravo"), ap(FiltroRedes(conUbicacion = true), Orden.NOMBRE))
        assertEquals(listOf("bravo", "charlie", "delta"), ap(FiltroRedes(escaneadas = true), Orden.NOMBRE))
        assertEquals(listOf("charlie"), ap(FiltroRedes(texto = "CHAR", escaneadas = true), Orden.NOMBRE))
    }

    @Test fun rangoSobreLaFechaDeLaOrdenacion() {
        assertEquals(listOf("eco", "bravo"), ap(FiltroRedes(rango = Rango.SEMANA), Orden.ULTIMA_CONEXION))
        assertEquals(listOf("eco", "bravo", "Álamo"), ap(FiltroRedes(rango = Rango.MES), Orden.ULTIMA_CONEXION))
        assertEquals(listOf("Álamo"), ap(FiltroRedes(rango = Rango.SEMANA), Orden.GUARDADA))
        assertEquals(listOf("delta", "bravo"), ap(FiltroRedes(rango = Rango.SEMANA), Orden.ESCANEADA))
        // With «Nombre A-Z» there is no date: the range does not remove anything.
        assertEquals(5, ap(FiltroRedes(rango = Rango.SEMANA), Orden.NOMBRE).size)
    }

    @Test fun esteAno() {
        assertTrue(OrdenRedes.dentro(ahora - 300 * dia, Rango.AÑO, ahora, tz))   // 21/01/2025
        assertFalse(OrdenRedes.dentro(ahora - 330 * dia, Rango.AÑO, ahora, tz))  // 22/12/2024
        assertFalse(OrdenRedes.dentro(null, Rango.MES, ahora, tz))
        assertTrue(OrdenRedes.dentro(null, Rango.SIEMPRE, ahora, tz))
    }
}
