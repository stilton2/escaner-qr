package es.kalvo.escanerqr.wifis.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrdenesTest {
    @Test fun claveRaraVaEnUnSoloArgumento() {
        val clave = "a b\"c';rm -rf \$HOME|x"
        val argv = Ordenes.anadir(DatosRed("Mi red; con espacios", Seguridad.WPA, clave, false))
        assertEquals(listOf("cmd", "wifi", "add-network", "Mi red; con espacios", "wpa2", clave), argv)
    }

    @Test fun ocultaYAbierta() {
        assertEquals(listOf("cmd", "wifi", "add-network", "Libre", "open", "-h"), Ordenes.anadir(DatosRed("Libre", Seguridad.ABIERTA, null, true)))
        assertEquals(listOf("cmd", "wifi", "add-network", "N", "wpa3", "12345678"), Ordenes.anadir(DatosRed("N", Seguridad.WPA3, "12345678", false)))
    }

    @Test(expected = IllegalArgumentException::class) fun faltaClave() { Ordenes.anadir(DatosRed("X", Seguridad.WPA, null, false)) }

    @Test(expected = IllegalArgumentException::class) fun empresaNo() { Ordenes.anadir(DatosRed("X", Seguridad.EMPRESA, "u", false)) }

    @Test(expected = IllegalArgumentException::class) fun idNegativo() { Ordenes.olvidar(-1) }

    @Test fun olvidar() { assertEquals(listOf("cmd", "wifi", "forget-network", "7"), Ordenes.olvidar(7)) }

    @Test fun describirTapaLaClave() {
        val argv = Ordenes.anadir(DatosRed("Casa Ejemplo", Seguridad.WPA, "secreta123", false))
        val t = Ordenes.describir(argv, "secreta123")
        assertFalse(t.contains("secreta123")); assertTrue(t.contains("«Casa Ejemplo»"))
    }

    @Test fun conectarClaveConEspaciosYComillas() {
        val clave = "a b\"c' d;\$x"
        assertEquals(listOf("cmd", "wifi", "connect-network", "Mi wifi \"1\"", "wpa2", clave),
            Ordenes.conectar(DatosRed("Mi wifi \"1\"", Seguridad.WPA, clave, false)))
    }

    @Test fun conectarOcultaYAbierta() {
        assertEquals(listOf("cmd", "wifi", "connect-network", "Libre", "open"), Ordenes.conectar(DatosRed("Libre", Seguridad.ABIERTA, null, false)))
        assertEquals(listOf("cmd", "wifi", "connect-network", "Oc", "wpa3", "12345678", "-h"), Ordenes.conectar(DatosRed("Oc", Seguridad.WPA3, "12345678", true)))
    }

    @Test(expected = IllegalArgumentException::class) fun conectarEmpresaNo() { Ordenes.conectar(DatosRed("X", Seguridad.EMPRESA, "u", false)) }

    @Test fun conectadoA() {
        assertEquals("Mi red 5g", Ordenes.conectadoA("Wifi is enabled\nWifi is connected to \"Mi red 5g\"\nWifiInfo: x"))
        assertEquals(null, Ordenes.conectadoA("Wifi is enabled\nWifi is disconnected"))
    }
}
