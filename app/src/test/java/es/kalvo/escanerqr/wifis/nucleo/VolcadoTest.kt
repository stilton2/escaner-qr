package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Invented pieces with the same shape as `dumpsys wifi` and `cmd wifi list-networks` on the S24 (no real names). */
class VolcadoTest {
    private val volcado = """
WifiConfigManager - Log Begin ----
WifiConfigManager - Log End ----
WifiConfigManager - Configured networks Begin ----
ID: 0 SSID: "Casa Ejemplo" PROVIDER-NAME: null BSSID: null FQDN: null PRIO: 0 HIDDEN: false
 KeyMgmt: WPA_PSK Protocols: WPA RSN
 PSK: *
IP config:
 cuid=1000 cname=android.uid.system:1000 luid=1000 lname=android.uid.system:1000 lcuid=1000 allowAutojoin=true

ID: 3 SSID: "Bar Uno" PROVIDER-NAME: null BSSID: null FQDN: null PRIO: 0 HIDDEN: false
 cuid=10123 cname=com.android.systemui luid=10123 lname=com.android.systemui
WifiConfigManager - Configured networks End ----
Dump of WifiNetworkSuggestionsManager
WifiNetworkSuggestionsManager - Networks Begin ----
Package Name: com.teacapps.barcodescanner
Has user approved: true
Has carrier privileges: false
WifiNetworkSuggestion[ SSID="Cliente, Uno", BSSID=null, SecurityParams= wpa2-psk wpa3-sae^, isHiddenSsid=false, isCredentialSharedWithUser=true ]
WifiNetworkSuggestion[ SSID="Oculta Dos", BSSID=null, SecurityParams= wpa3-sae, isHiddenSsid=true ]
WifiNetworkSuggestion[ SSID=null, BSSID=null, FQDN=passpoint ]
Package Name: com.otra.app
Has user approved: false
WifiNetworkSuggestion[ SSID="Libre", BSSID=null, SecurityParams= open, isHiddenSsid=false ]
WifiNetworkSuggestionsManager - Networks End ----
""".trimIndent()

    @Test fun sugerencias() {
        val s = Sugerencias.leer(volcado)
        assertEquals(3, s.size)
        assertEquals(Sugerencia(Origen.QR_BOT, "Cliente, Uno", Seguridad.WPA, false, true), s[0])
        assertEquals(Seguridad.WPA3, s[1].seguridad); assertTrue(s[1].oculta)
        assertEquals("com.otra.app", s[2].paquete); assertFalse(s[2].aprobada); assertEquals(Seguridad.ABIERTA, s[2].seguridad)
    }

    @Test fun creadores() {
        val c = Creadores.leer(volcado)
        assertEquals(mapOf(0 to "android.uid.system:1000", 3 to "com.android.systemui"), c)
        assertEquals(Origen.Tipo.AJUSTES, Origen.desde(c[0]).tipo)
        assertEquals(Origen.Tipo.PANEL, Origen.desde(c[3]).tipo)
    }

    @Test fun listaRedes() {
        val salida = """
Network Id      SSID                         Security type
0            Casa Ejemplo                 wpa2-psk
0            Casa Ejemplo                 wpa3-sae^
3            Bar Uno                      open
5            Solo Tres                    wpa3-sae
""".trimIndent()
        val l = ListaRedes.leer(salida)
        assertEquals(3, l.size)
        assertEquals(RedListada(0, "Casa Ejemplo", listOf("wpa2-psk", "wpa3-sae^")), l[0])
        assertEquals(Seguridad.WPA, Seguridad.desdeTipos(l[0].tipos))
        assertEquals(Seguridad.ABIERTA, Seguridad.desdeTipos(l[1].tipos))
        assertEquals(Seguridad.WPA3, Seguridad.desdeTipos(l[2].tipos))
    }

    @Test fun combinarSinClaves() {
        val l = Combinar.sinClaves(
            listOf(RedListada(0, "Casa Ejemplo", listOf("wpa2-psk")), RedListada(3, "Bar Uno", listOf("open"))),
            Sugerencias.leer(volcado), Creadores.leer(volcado),
        )
        assertEquals(listOf("Bar Uno", "Casa Ejemplo", "Cliente, Uno", "Libre", "Oculta Dos"), l.map { it.ssid })
        val qr = l.first { it.ssid == "Cliente, Uno" }
        assertTrue(qr.sugerencia); assertTrue(qr.origen.esQrBot); assertFalse(qr.olvidable)
        assertTrue(l.first { it.ssid == "Casa Ejemplo" }.olvidable)
        assertEquals(1, Combinar.filtrar(l, "cliente", soloQrBot = true).size)
    }
}
