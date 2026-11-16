package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** Made-up codes only. */
class ContenidoTest {
    private fun wifi(t: String) = Contenido.analizar(t) as Contenido.Wifi

    @Test fun wifiNormal() {
        val w = wifi("WIFI:S:Casa Ejemplo;T:WPA;P:secreto123;;")
        assertEquals("Casa Ejemplo", w.ssid); assertEquals("secreto123", w.clave); assertEquals("WPA", w.tipoMisWifis)
        assertFalse(w.oculta)
    }

    @Test fun wifiMultilineaDeQrBot() {
        val w = wifi("WIFI:S:Taller;\nT:WPA2-PSK-AES;\nP:abc;def;;")
        assertEquals("Taller", w.ssid)
        // The ';' after "abc" ends the key: this is what the code says (an unescaped ';' cannot be in the key).
        assertEquals("abc", w.clave)
        assertEquals("WPA2-PSK-AES", w.seguridad); assertEquals("WPA", w.tipoMisWifis)
    }

    @Test fun wifiMultilineaConCrlf() {
        val w = wifi("WIFI:S:Nave;\r\nT:WPA2-PSK-AES;\r\nP:clave1;\r\n;")
        assertEquals("Nave", w.ssid); assertEquals("clave1", w.clave)
    }

    @Test fun wifiEscapes() {
        val w = wifi("WIFI:T:WPA;S:Bar\\;Pepe\\:2;P:a\\;b\\,c\\\\d\\\"e;H:true;;")
        assertEquals("Bar;Pepe:2", w.ssid)
        assertEquals("a;b,c\\d\"e", w.clave)
        assertTrue(w.oculta)
    }

    @Test fun wifiTiposYOculta() {
        assertEquals("nopass", wifi("WIFI:S:Libre;T:nopass;;").tipoMisWifis)
        assertEquals("nopass", wifi("WIFI:S:Libre;;").tipoMisWifis)
        assertEquals("WEP", wifi("WIFI:S:Viejo;T:WEP;P:12345;;").tipoMisWifis)
        assertEquals("SAE", wifi("WIFI:S:Nuevo;T:SAE;P:x;;").tipoMisWifis)
        assertEquals("SAE", wifi("WIFI:S:Nuevo;T:WPA3;P:x;;").tipoMisWifis)
        assertEquals("WPA", wifi("WIFI:S:Sin tipo;P:x;;").tipoMisWifis)
        assertFalse(wifi("WIFI:S:x;H:false;;").oculta)
        assertTrue(wifi("WIFI:S:x;H:TRUE;;").oculta)
    }

    @Test fun wifiMinusculasYSinNombre() {
        assertTrue(Contenido.analizar("wifi:S:abc;P:1;;") is Contenido.Wifi)
        assertTrue(Contenido.analizar("WIFI:T:WPA;P:1;;") is Contenido.Texto)
    }

    @Test fun enlaces() {
        assertEquals("https://example.org/a?b=1", (Contenido.analizar("https://example.org/a?b=1") as Contenido.Enlace).url)
        assertEquals("http://x.es", (Contenido.analizar(" http://x.es ") as Contenido.Enlace).url)
        assertEquals("https://www.ejemplo.es", (Contenido.analizar("www.ejemplo.es") as Contenido.Enlace).url)
        assertTrue(Contenido.analizar("https://") is Contenido.Texto)
        assertTrue(Contenido.analizar("visita https://x.es ya") is Contenido.Texto)
        assertTrue(Contenido.analizar("www.") is Contenido.Texto)
    }

    @Test fun telefono() {
        assertEquals("+34600000000", (Contenido.analizar("tel:+34600000000") as Contenido.Telefono).numero)
        assertEquals("911000000", (Contenido.analizar("TEL:911000000") as Contenido.Telefono).numero)
    }

    @Test fun mailtoYMatmsg() {
        val m = Contenido.analizar("mailto:ana@example.org?subject=Hola%20Ana&body=Que+tal") as Contenido.Email
        assertEquals("ana@example.org", m.para); assertEquals("Hola Ana", m.asunto); assertEquals("Que tal", m.cuerpo)
        val mas = Contenido.analizar("mailto:ana+tienda@example.org?subject=Uno+dos") as Contenido.Email
        assertEquals("ana+tienda@example.org", mas.para); assertEquals("Uno dos", mas.asunto)
        val s = Contenido.analizar("mailto:b@example.org") as Contenido.Email
        assertEquals("b@example.org", s.para); assertEquals("", s.asunto)
        val mm = Contenido.analizar("MATMSG:TO:c@example.org;SUB:Pedido;BODY:Uno\\;dos;;") as Contenido.Email
        assertEquals("c@example.org", mm.para); assertEquals("Pedido", mm.asunto); assertEquals("Uno;dos", mm.cuerpo)
    }

    @Test fun sms() {
        val a = Contenido.analizar("SMSTO:600111222:Hola: llego tarde") as Contenido.Sms
        assertEquals("600111222", a.numero); assertEquals("Hola: llego tarde", a.mensaje)
        val b = Contenido.analizar("smsto:600111222") as Contenido.Sms
        assertEquals("600111222", b.numero); assertEquals("", b.mensaje)
        val c = Contenido.analizar("sms:+34600111222?body=Vale%21") as Contenido.Sms
        assertEquals("+34600111222", c.numero); assertEquals("Vale!", c.mensaje)
    }

    @Test fun geo() {
        val g = Contenido.analizar("geo:40.4168,-3.7038?q=Sol") as Contenido.Geo
        assertEquals(40.4168, g.lat, 1e-9); assertEquals(-3.7038, g.lon, 1e-9); assertEquals("Sol", g.consulta)
        val h = Contenido.analizar("GEO:43.1,-2.5,100;u=10") as Contenido.Geo
        assertEquals(43.1, h.lat, 1e-9)
        assertTrue(Contenido.analizar("geo:abc") is Contenido.Texto)
        assertTrue(Contenido.analizar("geo:95,0") is Contenido.Texto)
    }

    @Test fun vcard() {
        val v = "BEGIN:VCARD\r\nVERSION:3.0\r\nN:Pérez;Ana;;;\r\nFN:Ana Pérez\r\nORG:Talleres Ejemplo\r\nTITLE:Jefa\r\n" +
            "TEL;TYPE=CELL:+34600000001\r\nTEL;TYPE=WORK:910000002\r\nEMAIL:ana@example.org\r\n" +
            "ADR;TYPE=WORK:;;Calle Falsa 1;Pueblo;;28000;España\r\nURL:https://example.org\r\nNOTE:Línea 1\\nLínea 2\r\nEND:VCARD"
        val c = Contenido.analizar(v) as Contenido.Contacto
        assertEquals("Ana Pérez", c.nombre)
        assertEquals(listOf("+34600000001", "910000002"), c.telefonos)
        assertEquals(listOf("ana@example.org"), c.emails)
        assertEquals("Talleres Ejemplo", c.empresa); assertEquals("Jefa", c.cargo)
        assertEquals("Calle Falsa 1, Pueblo, 28000, España", c.direccion)
        assertEquals("https://example.org", c.web)
        assertEquals("Línea 1\nLínea 2", c.nota)
    }

    @Test fun vcardSinFnYPlegada() {
        val v = "BEGIN:VCARD\nVERSION:2.1\nN:Gómez;Luis\nTEL:600\n 123\nEND:VCARD"
        val c = Contenido.analizar(v) as Contenido.Contacto
        assertEquals("Luis Gómez", c.nombre)
        assertEquals(listOf("600123"), c.telefonos)
    }

    @Test fun mecard() {
        val c = Contenido.analizar("MECARD:N:Pérez,Ana;TEL:600000001;TEL:910000002;EMAIL:ana@example.org;ORG:Ejemplo SL;NOTE:Hola\\, qué tal;;") as Contenido.Contacto
        assertEquals("Ana Pérez", c.nombre)
        assertEquals(listOf("600000001", "910000002"), c.telefonos)
        assertEquals("Ejemplo SL", c.empresa)
        assertEquals("Hola, qué tal", c.nota)
    }

    @Test fun evento() {
        val e = Contenido.analizar("BEGIN:VEVENT\nSUMMARY:Revisión\nDTSTART:20261120T090000Z\nDTEND:20261120T100000Z\nLOCATION:Nave 3\nEND:VEVENT") as Contenido.Evento
        assertEquals("Revisión", e.titulo); assertEquals("Nave 3", e.lugar)
        assertEquals(Historial.instanteUtc("2026-11-20", "09:00:00"), Contenido.fechaEvento(e.inicio))
        val madrid = TimeZone.getTimeZone("Europe/Madrid")
        assertEquals(Historial.instanteUtc("2026-11-19", "23:00:00"), Contenido.fechaEvento("20261120", madrid))
        assertTrue(Contenido.esTodoElDia("20261120"))
        assertNull(Contenido.fechaEvento("mañana"))
    }

    @Test fun productoYTexto() {
        assertEquals("8410000000000", (Contenido.analizar("8410000000000", "EAN-13") as Contenido.Producto).codigo)
        // Same digits in a Code 128 are just text (it can be a serial number).
        assertTrue(Contenido.analizar("8410000000000", "Code 128") is Contenido.Texto)
        val t = Contenido.analizar("Hola\nqué tal") as Contenido.Texto
        assertEquals("Hola\nqué tal", t.texto)
    }

    @Test fun decodificarUrlRaro() {
        assertEquals("100%", Contenido.decodificarUrl("100%"))
        assertEquals("a%zzb", Contenido.decodificarUrl("a%zzb"))
        assertEquals("ñ", Contenido.decodificarUrl("%C3%B1"))
        assertNotNull(Contenido.decodificarUrl("%-1"))
    }
}
