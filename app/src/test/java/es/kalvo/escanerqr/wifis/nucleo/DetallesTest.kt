package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Invented pieces with the same shape as the "Configured networks" part of `dumpsys wifi` on the S24 (no real names). */
class DetallesTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    private val volcado = """
WifiConfigManager - Configured networks Begin ----
* ID: 2 SSID: "Casa Ejemplo" PROVIDER-NAME: null BSSID: null FQDN: null HOME-PROVIDER-NETWORK: false PRIO: 0 HIDDEN: false PMF: false CarrierId: -1 SubscriptionId: -1 SubscriptionGroup: null Currently Connected: true User Selected: false
 NetworkSelectionStatus NETWORK_SELECTION_ENABLED
 hasEverConnected: true
 hasNeverDetectedCaptivePortal: true
 hasEverValidatedInternetAccess: true
 mCandidateSecurityParams: null mLastUsedSecurityParams: null validatedInternetAccess shared trusted
 macRandomizationSetting: 3
 mRandomizedMacAddress: 12:34:56:78:9a:bc
 randomizedMacExpirationTimeMs: <none>
 KeyMgmt: WPA_PSK Protocols: WPA RSN
 PSK/SAE: *
IP config:
IP assignment: DHCP
Proxy settings: NONE
 cuid=1000 cname=android.uid.system:1000 luid=10123 lname=com.android.systemui lcuid=1000 allowAutojoin=true noInternetAccessExpected=false mostRecentlyConnected=true

lastConnected: 2026-11-16T03:12:06.001
lastUpdated: 2026-11-16T00:23:38.084
numRebootsSinceLastUse: 0
- DSBLE ID: 9 SSID: "Bar HIDDEN: true Uno" PROVIDER-NAME: null BSSID: null FQDN: null HOME-PROVIDER-NETWORK: false PRIO: 0 HIDDEN: false PMF: false CarrierId: -1 SubscriptionId: -1 SubscriptionGroup: null Currently Connected: false User Selected: false
 NetworkSelectionStatus NETWORK_SELECTION_PERMANENTLY_DISABLED
 mNetworkSelectionDisableReason NETWORK_SELECTION_DISABLED_BY_WRONG_PASSWORD
NETWORK_SELECTION_DISABLED_BY_WRONG_PASSWORD counter:1
 hasEverConnected: false
 hasEverValidatedInternetAccess: false
 mCandidateSecurityParams: null mLastUsedSecurityParams: null shared trusted meteredHint meteredOverride 1
 macRandomizationSetting: 0
 mRandomizedMacAddress: 02:00:00:00:00:00
 PSK/SAE: *
IP config:
IP assignment: STATIC
Static configuration: IP address 192.168.50.20/24 Gateway 192.168.50.1  DNS servers: [ 192.168.50.1 9.9.9.9 ] Domains
Proxy settings: STATIC
HTTP proxy: [proxy.ejemplo] 3128 xl=
 cuid=2000 cname=com.android.shell luid=2000 lname=com.android.shell lcuid=2000 allowAutojoin=false noInternetAccessExpected=false mostRecentlyConnected=false
numRebootsSinceLastUse: 7
ID: 14 SSID: "Oculta" PROVIDER-NAME: null BSSID: null FQDN: null HOME-PROVIDER-NETWORK: false PRIO: 0 HIDDEN: true PMF: false CarrierId: -1 SubscriptionId: -1 SubscriptionGroup: null Currently Connected: false User Selected: false
IP config:
IP assignment: DHCP
Proxy settings: PAC
HTTP proxy: PAC Script: http://wpad.ejemplo/wpad.dat
 cuid=1000 cname=android.uid.system:1000 luid=1000 lname=android.uid.system:1000 lcuid=1000 allowAutojoin=true
WifiConfigManager - Configured networks End ----
ID: 99 SSID: "Fuera de la sección" HIDDEN: false
""".trimIndent()

    private val d = Detalles.leer(volcado, madrid)

    @Test fun todasLasRedesDeLaSeccionTambienLaConectadaYLasDesactivadas() {
        assertEquals(setOf(2, 9, 14), d.keys)
    }

    @Test fun redNormalConectada() {
        val a = d.getValue(2)
        assertTrue(a.conectadaAhora)
        assertEquals(false, a.oculta)
        assertEquals(true, a.conectadaAlgunaVez)
        assertEquals(true, a.internetAlgunaVez)
        assertEquals(3, a.macAjuste)
        assertEquals("12:34:56:78:9a:bc", a.macAleatoria)
        assertEquals("DHCP", a.ip); assertNull(a.ipFija)
        assertEquals("NONE", a.proxy); assertNull(a.proxyDetalle)
        assertEquals(true, a.autoConexion)
        assertEquals(0, a.medida); assertFalse(a.pistaMedida)
        assertEquals("android.uid.system:1000", a.creador)
        assertEquals("com.android.systemui", a.cambiadaPor)
        assertEquals(0, a.reiniciosSinUsar)
        assertNull(a.desactivada); assertNull(a.motivoDesactivada)
        // 2026-11-16 03:12:06.001 in Madrid (CET, UTC+1) = 02:12:06.001 UTC
        assertEquals(1794795126001L, a.ultimaConexion)
    }

    @Test fun redDesactivadaConIpFijaYProxy() {
        val b = d.getValue(9)
        // The name contains " HIDDEN: true": the flag is read after it.
        assertEquals(false, b.oculta)
        assertFalse(b.conectadaAhora)
        assertEquals("NETWORK_SELECTION_DISABLED_BY_WRONG_PASSWORD", b.desactivada)
        assertEquals("contraseña incorrecta", b.motivoDesactivada)
        assertEquals(0, b.macAjuste); assertNull(b.macAleatoria)
        assertEquals("STATIC", b.ip)
        assertEquals("IP 192.168.50.20/24 · puerta de enlace 192.168.50.1 · DNS 192.168.50.1, 9.9.9.9", b.ipFija)
        assertEquals("STATIC", b.proxy); assertEquals("proxy.ejemplo:3128", b.proxyDetalle)
        assertEquals(false, b.autoConexion)
        assertEquals(1, b.medida); assertTrue(b.pistaMedida)
        assertEquals("com.android.shell", b.creador)
        assertNull(b.ultimaConexion)
        assertEquals(7, b.reiniciosSinUsar)
    }

    @Test fun redOcultaConPac() {
        val c = d.getValue(14)
        assertEquals(true, c.oculta)
        assertEquals("PAC", c.proxy)
        assertEquals("http://wpad.ejemplo/wpad.dat", c.proxyDetalle)
        assertNull(c.conectadaAlgunaVez)
    }

    @Test fun creadoresTambienDeLasDesactivadas() {
        assertEquals(mapOf(2 to "android.uid.system:1000", 9 to "com.android.shell", 14 to "android.uid.system:1000"), Creadores.leer(volcado))
    }

    @Test fun ipFijaSinPuertaDeEnlace() {
        assertEquals("IP 10.0.0.5/8 · DNS 10.0.0.1", Detalles.ipFija("IP address 10.0.0.5/8 Gateway  DNS servers: [ 10.0.0.1 ] Domains "))
    }

    @Test fun fechasDelVolcado() {
        assertEquals(1794795126001L, Detalles.fecha("2026-11-16T03:12:06.001 ", madrid))
        assertEquals(1794795126000L, Detalles.fecha("2026-11-16T03:12:06", madrid))
        assertNull(Detalles.fecha("ayer", madrid))
    }
}
