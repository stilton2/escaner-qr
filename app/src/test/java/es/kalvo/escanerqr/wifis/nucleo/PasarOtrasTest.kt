package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Test

class PasarOtrasTest {
    private val qrbot = Origen(Origen.Tipo.APP, "net.qrbot", "net.qrbot")
    private fun sug(ssid: String, seg: Seguridad = Seguridad.WPA) = Red(-1, ssid, seg, null, false, qrbot, true)
    private fun normal(ssid: String) = Red(3, ssid, Seguridad.WPA, "x1234567", false, Origen.desde(null), false)

    @Test fun usaLaClaveDelHistorialYDejaLasQueNoTienen() {
        val leidas = Leidas.agrupar(listOf(
            FilaHistorial(1, 100, "WIFI:S:Bar;T:WPA;P:vieja123;;", "QR Bot"),
            FilaHistorial(2, 200, "WIFI:S:Bar;T:WPA;P:nueva123;;", "QR Bot"),
            FilaHistorial(3, 300, "WIFI:S:Corta;T:WPA;P:abc;;", "QR Bot"),
        ))
        val p = PasarOtras.plan(
            listOf(sug("Bar"), sug("Bar"), sug("SinNada"), sug("Corta"), sug("Abierta", Seguridad.ABIERTA), sug("Casa"), normal("Casa")),
            leidas,
        )
        assertEquals(listOf("Bar" to "nueva123", "Abierta" to null), p.listas.map { it.ssid to it.clave })
        assertEquals(listOf("SinNada", "Corta"), p.sinClave)
    }
}
