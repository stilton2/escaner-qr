package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextoQrTest {
    @Test fun creaTextoNormal() {
        assertEquals("WIFI:T:WPA;S:Casa Ejemplo;P:clave1234;;", TextoQr.crear(DatosRed("Casa Ejemplo", Seguridad.WPA, "clave1234", false)))
    }

    @Test fun escapaEspeciales() {
        val d = DatosRed("Bar;Uno", Seguridad.WPA, "a\\b;c,d:e\"f", false)
        val t = TextoQr.crear(d)
        assertEquals("WIFI:T:WPA;S:Bar\\;Uno;P:a\\\\b\\;c\\,d\\:e\\\"f;;", t)
        assertEquals(d, TextoQr.leer(t))
    }

    @Test fun abiertaYOculta() {
        val t = TextoQr.crear(DatosRed("Libre", Seguridad.ABIERTA, "ignorada", true))
        assertEquals("WIFI:T:nopass;S:Libre;H:true;;", t)
        val d = TextoQr.leer(t)!!
        assertEquals(Seguridad.ABIERTA, d.seguridad); assertNull(d.clave); assertTrue(d.oculta)
    }

    @Test fun leeEnOtroOrdenYMinusculas() {
        val d = TextoQr.leer("wifi:S:Taller;P:pass5678;T:WPA;;")!!
        assertEquals("Taller", d.ssid); assertEquals("pass5678", d.clave); assertEquals(Seguridad.WPA, d.seguridad)
    }

    @Test fun leeSae() {
        assertEquals(Seguridad.WPA3, TextoQr.leer("WIFI:T:SAE;S:Nueva;P:largaclave;;")!!.seguridad)
    }

    @Test fun sinTipoConClaveEsWpa() {
        assertEquals(Seguridad.WPA, TextoQr.leer("WIFI:S:X;P:12345678;;")!!.seguridad)
    }

    @Test fun conSaltosDeLinea() {
        val d = TextoQr.leer("Wifi del bar\nWIFI:S:Bar Ejemplo;\nT:WPA;\nP:cerveza99;\n;\n")!!
        assertEquals("Bar Ejemplo", d.ssid); assertEquals("cerveza99", d.clave)
    }

    @Test fun dosPuntosDentroDelValor() {
        assertEquals("a:b", TextoQr.leer("WIFI:S:a:b;T:nopass;;")!!.ssid)
    }

    @Test fun noEsWifi() {
        assertNull(TextoQr.leer("https://ejemplo.invalid"))
        assertNull(TextoQr.leer("WIFI:T:WPA;P:sin nombre;;"))
        assertNull(TextoQr.desdeWifi("noWIFI:S:x;;"))
    }

    @Test fun ocultaFalsaPorDefecto() {
        assertFalse(TextoQr.leer("WIFI:S:x;T:nopass;;")!!.oculta)
        assertTrue(TextoQr.leer("WIFI:S:x;T:nopass;H:1;;")!!.oculta)
    }
}
