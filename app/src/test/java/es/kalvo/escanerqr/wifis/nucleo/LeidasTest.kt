package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LeidasTest {
    private fun red(id: Int, ssid: String, clave: String?) =
        Red(id, ssid, Seguridad.WPA, clave, false, Origen.desde(null), false)

    @Test fun agrupaPorNombreClaveYTipo() {
        val l = Leidas.agrupar(listOf(
            FilaHistorial(1, 100, "WIFI:S:Casa;T:WPA;P:uno12345;;"),
            FilaHistorial(2, 300, "WIFI:S:Casa;T:WPA;P:uno12345;;", "QR Bot"),
            FilaHistorial(3, 200, "WIFI:S:Casa;T:WPA;P:dos12345;;"),
            FilaHistorial(4, 50, "https://ejemplo.es"),
        ))
        assertEquals(2, l.size)
        val a = l.first()
        assertEquals("uno12345", a.clave)
        assertEquals(listOf(2L, 1L), a.ids)
        assertEquals(100L, a.primera); assertEquals(300L, a.ultima)
        assertEquals(listOf("QR Bot", ""), a.origenes)
    }

    @Test fun cruzaConElMovil() {
        val l = Leidas.agrupar(listOf(
            FilaHistorial(1, 1, "WIFI:S:Igual;T:WPA;P:clave123;;"),
            FilaHistorial(2, 2, "WIFI:S:Otra;T:WPA;P:clave123;;"),
            FilaHistorial(3, 3, "WIFI:S:NoSe;T:WPA;P:clave123;;"),
            FilaHistorial(4, 4, "WIFI:S:Nueva;T:WPA;P:clave123;;"),
        ))
        val c = Leidas.cruzar(l, listOf(red(1, "Igual", "clave123"), red(2, "Otra", "distinta1"), red(3, "NoSe", null)))
            .associateBy { it.ssid }
        assertEquals(WifiLeida.EnMovil.IGUAL, c["Igual"]!!.enMovil)
        assertEquals(WifiLeida.EnMovil.OTRA_CLAVE, c["Otra"]!!.enMovil)
        assertEquals(WifiLeida.EnMovil.SIN_SABER, c["NoSe"]!!.enMovil)
        assertEquals(WifiLeida.EnMovil.NO, c["Nueva"]!!.enMovil)
    }

    @Test fun yaGuardadaComparaLaClave() {
        val normales = listOf(RedListada(7, "Casa", listOf("wpa2-psk")))
        assertEquals(true, YaGuardada.buscar("Casa", normales, emptyList(), mapOf(7 to "abc12345"), "abc12345").mismaClave)
        assertEquals(false, YaGuardada.buscar("Casa", normales, emptyList(), mapOf(7 to "abc12345"), "otra1234").mismaClave)
        assertNull(YaGuardada.buscar("Casa", normales, emptyList(), emptyMap(), "abc12345").mismaClave)
    }
}
