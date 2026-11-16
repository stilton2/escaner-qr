package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.TimeZone

/** Invented pieces with the same shape as `dumpsys sem_wifi` on the S24 (no real names). */
class UsoSamsungTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    private val CLAVE = Ubicaciones.Familia.CLAVE

    private val volcado = """
SemWifiConfigManager:
SemWifiConfigManager - Log Begin ----
2026-11-15T19:25:44.1 - addOrUpdateNetwork "Casa Ejemplo"WPA_PSK (changed){creationTime : 1742155743580 }
SemWifiConfigManager - Log End ----
"Casa Ejemplo"SAE
 disableReason: DISABLED_NONE networkScore: 4
 cTime: 1742155743580
 personalizedConnectionOption: 0


"Solo Config"WPA_PSK
 disableReason: DISABLED_NONE networkScore: 0
 cTime: 1752225472408
 lastInvalidatedRssi: -127

"Cero"WPA_PSK
 cTime: 0

 isSimCardReady = true
 networkType = 18

ScanPool:
 - last measured country code: ES
Dump of SemWifiUsageMonitor
[[[Daily Usage]]] - 60 days
2026-09-18 - EnabledTime: 86400 sec, #ofConnections: 5, ConnectedTime/RX/TX -    total [82131 sec / 4,1 GB / 252 MB ]
[[[Network Usage]]] - 4 networks
"Casa Ejemplo"WPA_PSK - cTime 2025/09/16 10:22:56 - uTime 2026/11/14 18:03:00, #ofConnections: 1234, ConnectedTime/RX/TX -    total [29507801 sec / 1,7 TB / 443 GB ]    |    5GHz [ 25393418 sec / 1,5 TB / 421 GB ]
"Bar, Uno"WPA_PSK-0 - cTime 2026/08/04 16:50:47 - uTime 2026/08/04 17:35:37, #ofConnections: 1, ConnectedTime/RX/TX -    total [2689 sec / 71 MB / 34 MB ]    |    5GHz [ 2689 sec / 71 MB / 34 MB ]
"Nunca"WPA_PSK - cTime 2026/07/16 17:02:03 - uTime 1970/01/01 00:59:59, #ofConnections: 0, ConnectedTime/RX/TX -    total [0 sec / 0 B / 0 B ]    |    |
"Libre"NONE - cTime 2026/02/12 12:07:11 - uTime 2026/02/12 13:26:54, #ofConnections: 2, ConnectedTime/RX/TX -    total [4754 sec / 36 MB / 10 MB ]
****************************************************************

SemWifiUsageMonitor - Log Begin ----
"Despues"WPA_PSK - cTime 2026/01/01 00:00:00 - uTime 2026/01/01 00:00:00, #ofConnections: 1, ConnectedTime/RX/TX -    total [1 sec / 1 B / 1 B ]
""".trimIndent()

    private val u = UsoSamsung.leer(volcado, madrid)

    @Test fun lineasDeUso() {
        val casa = u.getValue("Casa Ejemplo" to CLAVE)
        assertEquals(1758010976000L, casa.creada)   // 2025-09-16 10:22:56 CEST
        assertEquals(1794675780000L, casa.ultimaVez) // 2026-11-14 18:03:00 CET
        assertEquals(1234, casa.conexiones)
        assertEquals(29507801L, casa.segundos)
        assertEquals("1,7 TB", casa.recibido); assertEquals("443 GB", casa.enviado)
        assertEquals(1, u.getValue("Bar, Uno" to CLAVE).conexiones)
        assertEquals(2, u.getValue("Libre" to Ubicaciones.Familia.ABIERTA).conexiones)
    }

    @Test fun nuncaConectadaNoTieneUltimaVez() {
        val n = u.getValue("Nunca" to CLAVE)
        assertEquals(0, n.conexiones)
        assertNull(n.ultimaVez)
        assertEquals(null, UsoSamsung.fecha("1970/01/01 00:59:59", madrid))
    }

    @Test fun fechaGuardadaDeSemWifiConfigManagerSiFalta() {
        assertEquals(Uso(creada = 1752225472408L), u["Solo Config" to CLAVE])
        assertNull(u["Cero" to CLAVE]) // cTime 0 = unknown
    }

    @Test fun nadaFueraDeLasSecciones() {
        assertNull(u["Despues" to CLAVE])
        assertEquals(5, u.size) // Casa, Bar, Nunca, Libre + Solo Config
    }

    @Test fun asignarUsoARedes() {
        val redes = listOf(
            Red(2, "Casa Ejemplo", Seguridad.WPA, null, false, Origen.desde("android.uid.system:1000"), false),
            Red(-1, "Casa Ejemplo", Seguridad.WPA, null, false, Origen(Origen.Tipo.APP, Origen.QR_BOT, Origen.QR_BOT), true),
            Red(-1, "Bar, Uno", Seguridad.WPA3, null, false, Origen(Origen.Tipo.APP, Origen.QR_BOT, Origen.QR_BOT), true),
            Red(5, "Desconocida", Seguridad.WPA, null, false, Origen.desde(null), false),
        )
        val r = Ubicaciones.asignarUso(redes, u)
        assertEquals(1234, r[0].uso?.conexiones)
        assertNull(r[1].uso)                          // suggestion with the same name as a normal network: not guessed
        assertEquals(1, r[2].uso?.conexiones)          // only suggestion with that name (WPA3 is the same family)
        assertNull(r[3].uso)
        assertEquals(1794675780000L, r[0].ultimaVez)
    }

    @Test fun ultimaVezLaMasReciente() {
        val red = Red(2, "Casa Ejemplo", Seguridad.WPA, null, false, Origen.desde(null), false,
            uso = Uso(ultimaVez = 1000L), detalle = Detalle(2, ultimaConexion = 5000L))
        assertEquals(5000L, red.ultimaVez)
        assertNull(red.copy(uso = null, detalle = null).ultimaVez)
    }
}
