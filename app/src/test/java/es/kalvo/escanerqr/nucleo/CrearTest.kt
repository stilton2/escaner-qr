package es.kalvo.escanerqr.nucleo

import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import es.kalvo.escanerqr.wifis.nucleo.DatosRed
import es.kalvo.escanerqr.wifis.nucleo.Seguridad
import es.kalvo.escanerqr.wifis.nucleo.TextoQr
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** «Crear» (S-J9): the text of each kind and, read back by the app's own parser, the same data. Made-up data only. */
class CrearTest {

    // ---------- enlace, teléfono, sms, correo ----------

    @Test fun enlaceSinEsquemaLlevaHttps() {
        assertEquals("https://ejemplo.es/a?b=1", Crear.enlace("  ejemplo.es/a?b=1 "))
        assertEquals("http://ejemplo.es", Crear.enlace("http://ejemplo.es"))
        assertNotNull(Crear.problemaEnlace("eje mplo.es"))
        assertNotNull(Crear.problemaEnlace(""))
        assertNull(Crear.problemaEnlace("ejemplo.es"))
        assertTrue(Contenido.analizar(Crear.enlace("ejemplo.es")) is Contenido.Enlace)
    }

    @Test fun telefonoSoloCifrasYMasDelante() {
        assertEquals("+34600112233", Crear.limpiarNumero("+34 600 11-22-33"))
        assertEquals("600112233", Crear.limpiarNumero("(600) 11.22.33+"))
        val t = Contenido.analizar(Crear.telefono("+34 600 11 22 33")) as Contenido.Telefono
        assertEquals("+34600112233", t.numero)
        assertNotNull(Crear.problemaNumero("12"))
    }

    @Test fun smsConDosPuntosEnElMensaje() {
        val s = Contenido.analizar(Crear.sms("600 11 22 33", "Llego a las 10:30, ¿vale?")) as Contenido.Sms
        assertEquals("600112233", s.numero)
        assertEquals("Llego a las 10:30, ¿vale?", s.mensaje)
    }

    @Test fun correoMailtoIdaYVuelta() {
        val t = Crear.correo("ana+tienda@ejemplo.es", "Hola & adiós", "Línea 1\nLínea 2 = 100% +1")
        assertTrue(t.startsWith("mailto:ana+tienda@ejemplo.es?subject="))
        assertFalse(t.contains(' '))
        val e = Contenido.analizar(t) as Contenido.Email
        assertEquals("ana+tienda@ejemplo.es", e.para)
        assertEquals("Hola & adiós", e.asunto)
        assertEquals("Línea 1\nLínea 2 = 100% +1", e.cuerpo)
        assertEquals("mailto:a@b.es", Crear.correo("a@b.es", "", ""))
        assertNotNull(Crear.problemaCorreo("ana@ejemplo"))
        assertNull(Crear.problemaCorreo("ana@ejemplo.es"))
    }

    // ---------- wifi ----------

    @Test fun wifiConCaracteresEspecialesSeEscapaYSeLeeIgual() {
        val d = DatosRed("Casa;de,Ana:\\1\"", Seguridad.WPA, "cl;a,v:e\\\"x", oculta = true)
        val t = TextoQr.crear(d)
        assertEquals("WIFI:T:WPA;S:Casa\\;de\\,Ana\\:\\\\1\\\";P:cl\\;a\\,v\\:e\\\\\\\"x;H:true;;", t)
        val w = Contenido.analizar(t) as Contenido.Wifi
        assertEquals(d.ssid, w.ssid); assertEquals(d.clave, w.clave); assertTrue(w.oculta); assertEquals("WPA", w.tipoMisWifis)
    }

    @Test fun wifiAbiertaYWpa3() {
        val a = Contenido.analizar(TextoQr.crear(DatosRed("Bar", Seguridad.ABIERTA, null, false))) as Contenido.Wifi
        assertEquals("nopass", a.tipoMisWifis); assertEquals("", a.clave)
        val s = Contenido.analizar(TextoQr.crear(DatosRed("Nueva", Seguridad.WPA3, "secreto123", false))) as Contenido.Wifi
        assertEquals("SAE", s.tipoMisWifis)
    }

    @Test fun ocultarClaveWifiSoloTapaLaContrasena() {
        assertEquals("WIFI:T:WPA;S:Casa;P:••••••;;", Crear.ocultarClaveWifi("WIFI:T:WPA;S:Casa;P:secreto123;;"))
        // A name with an escaped ";P:" inside is not mistaken for the password.
        assertEquals("WIFI:T:WPA;S:a\\;P:b;P:••••••;;", Crear.ocultarClaveWifi("WIFI:T:WPA;S:a\\;P:b;P:real;;"))
        assertEquals("WIFI:T:nopass;S:Bar;;", Crear.ocultarClaveWifi("WIFI:T:nopass;S:Bar;;"))
        assertEquals("hola", Crear.ocultarClaveWifi("hola"))
    }

    // ---------- contacto ----------

    @Test fun contactoVcardIdaYVuelta() {
        val f = Crear.Ficha("Ana García López", "+34 600 11 22 33", "ana@ejemplo.es", "Talleres, S.L.", "C/ Mayor 3; 2º", "ejemplo.es")
        val t = Crear.contacto(f)
        assertTrue(t.startsWith("BEGIN:VCARD\nVERSION:3.0\nN:García López;Ana;;;\nFN:Ana García López\n"))
        assertTrue(t.contains("ORG:Talleres\\, S.L.\n"))
        val c = Contenido.analizar(t) as Contenido.Contacto
        assertEquals("Ana García López", c.nombre)
        assertEquals(listOf("+34600112233"), c.telefonos)
        assertEquals(listOf("ana@ejemplo.es"), c.emails)
        assertEquals("Talleres, S.L.", c.empresa)
        assertEquals("https://ejemplo.es", c.web)
        assertEquals("C/ Mayor 3; 2º", c.direccion)
    }

    @Test fun contactoSoloConTelefono() {
        val t = Crear.contacto(Crear.Ficha("", "600112233"))
        val c = Contenido.analizar(t) as Contenido.Contacto
        assertEquals(listOf("600112233"), c.telefonos)
        assertFalse(t.contains("EMAIL")); assertFalse(t.contains("ADR"))
        assertNotNull(Crear.problemaContacto(Crear.Ficha("")))
    }

    @Test fun escaparVcardEscapaLoQueManda() {
        assertEquals("a\\\\b\\;c\\,d\\ne", Crear.escaparVcard("a\\b;c,d\r\ne"))
    }

    // ---------- ubicación ----------

    @Test fun ubicacionConPuntoYSeisDecimales() {
        assertEquals("geo:40.416775,-3.70379", Crear.ubicacion(40.4167754, -3.70379))
        assertEquals("geo:0,10", Crear.ubicacion(-0.0000001, 10.0))
        val g = Contenido.analizar(Crear.ubicacion(40.4168, -3.7038)) as Contenido.Geo
        assertEquals(40.4168, g.lat, 1e-9); assertEquals(-3.7038, g.lon, 1e-9)
    }

    @Test fun coordenadasDeTextosPegados() {
        assertEquals(40.4168 to -3.7038, Crear.coordenadas("40.4168, -3.7038"))
        assertEquals(40.4168 to -3.7038, Crear.coordenadas("40.4168 -3.7038"))
        assertEquals(40.4168 to -3.7038, Crear.coordenadas("https://www.google.com/maps/@40.4168,-3.7038,15z"))
        assertEquals(40.4168 to -3.7038, Crear.coordenadas("https://maps.google.com/?q=40.4168,-3.7038"))
        assertNull(Crear.coordenadas("hola"))
        assertNull(Crear.coordenadas("95.1, 10.2"))
        assertEquals(40.5, Crear.leerNumero("40,5")!!, 0.0)
        assertNotNull(Crear.problemaLatitud("91")); assertNull(Crear.problemaLongitud("-179,5"))
    }

    // ---------- evento ----------

    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    private fun ms(s: String) = Contenido.fechaEvento(s, madrid)!!

    @Test fun eventoConHoraIdaYVuelta() {
        val t = Crear.evento("Cena; con amigos, 2", ms("20261120T213000"), ms("20261120T233000"), "Bar Sol", false, madrid)
        assertEquals("BEGIN:VEVENT\nSUMMARY:Cena\\; con amigos\\, 2\nDTSTART:20261120T213000\nDTEND:20261120T233000\nLOCATION:Bar Sol\nEND:VEVENT", t)
        val e = Contenido.analizar(t) as Contenido.Evento
        assertEquals("Cena; con amigos, 2", e.titulo); assertEquals("Bar Sol", e.lugar)
        assertEquals(ms("20261120T213000"), Contenido.fechaEvento(e.inicio, madrid))
    }

    @Test fun eventoTodoElDiaTerminaElDiaSiguiente() {
        val t = Crear.evento("Fiesta", ms("20261231T100000"), ms("20261231T110000"), "", true, madrid)
        assertTrue(t.contains("DTSTART;VALUE=DATE:20261231\n")); assertTrue(t.contains("DTEND;VALUE=DATE:20270101\n"))
        assertFalse(t.contains("LOCATION"))
        val e = Contenido.analizar(t) as Contenido.Evento
        assertTrue(Contenido.esTodoElDia(e.inicio))
    }

    @Test fun eventoProblemas() {
        assertNotNull(Crear.problemaEvento("", 0, 1, false, madrid))
        assertNotNull(Crear.problemaEvento("x", ms("20261120T213000"), ms("20261120T203000"), false, madrid))
        assertNull(Crear.problemaEvento("x", ms("20261120T213000"), ms("20261120T203000"), true, madrid))
        assertNotNull(Crear.problemaEvento("x", ms("20261121T100000"), ms("20261120T100000"), true, madrid))
    }

    // ---------- códigos de barras: comprobar lo escrito ----------

    @Test fun digitoDeControl() {
        assertEquals(1, Codigos.digitoControl("400638133393"))   // EAN-13 4006381333931
        assertEquals(4, Codigos.digitoControl("9638507"))        // EAN-8 96385074
        assertEquals(2, Codigos.digitoControl("03600029145"))    // UPC-A 036000291452
    }

    @Test fun eanConDoceCifrasAnadeLaUltima() {
        val r = Codigos.revisar(Formato.EAN13, "4006381 33393") as Revision.Bien
        assertEquals("4006381333931", r.texto); assertNotNull(r.nota)
        assertEquals(Revision.Bien("4006381333931"), Codigos.revisar(Formato.EAN13, "4006381333931"))
        val mal = Codigos.revisar(Formato.EAN13, "4006381333932") as Revision.Mal
        assertTrue(mal.motivo.contains("tendría que ser 1"))
        assertTrue(Codigos.revisar(Formato.EAN13, "40063813339A") is Revision.Mal)
        assertTrue(Codigos.revisar(Formato.EAN13, "123") is Revision.Mal)
        assertEquals("96385074", (Codigos.revisar(Formato.EAN8, "9638507") as Revision.Bien).texto)
        assertEquals("036000291452", (Codigos.revisar(Formato.UPCA, "03600029145") as Revision.Bien).texto)
        assertTrue(Codigos.revisar(Formato.UPCA, "036000291453") is Revision.Mal)
    }

    @Test fun itfCode39Code128() {
        assertTrue((Codigos.revisar(Formato.ITF, "12345") as Revision.Mal).motivo.contains("par"))
        assertTrue(Codigos.revisar(Formato.ITF, "1234") is Revision.Mal)
        assertEquals(Revision.Bien("00012345678905"), Codigos.revisar(Formato.ITF, "00012345678905"))
        val c39 = Codigos.revisar(Formato.CODE39, "pieza-12") as Revision.Bien
        assertEquals("PIEZA-12", c39.texto); assertNotNull(c39.nota)
        assertTrue(Codigos.revisar(Formato.CODE39, "PIEZA_12") is Revision.Mal)
        assertTrue(Codigos.revisar(Formato.CODE128, "Año 2026") is Revision.Mal)
        assertEquals(Revision.Bien("Caja A-12 #3"), Codigos.revisar(Formato.CODE128, "Caja A-12 #3"))
        assertTrue(Codigos.revisar(Formato.CODE128, "x".repeat(81)) is Revision.Mal)
        assertTrue(Codigos.revisar(Formato.DATA_MATRIX, "") is Revision.Mal)
        assertEquals(Revision.Bien("ñandú €"), Codigos.revisar(Formato.AZTEC, "ñandú €"))
    }

    // ---------- dibujar y volver a leer (lector ZXing en Java, distinto del de la app) ----------

    private fun leer(f: Formato, texto: String): String {
        val p = Codigos.pixeles(Codigos.matriz(f, texto), f, 600)
        val fuente = RGBLuminanceSource(p.ancho, p.alto, p.px)
        val pistas = mapOf(DecodeHintType.TRY_HARDER to true, DecodeHintType.POSSIBLE_FORMATS to listOf(f.zxing))
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(fuente)), pistas).text
    }

    @Test fun cadaFormatoSeLeeIgualQueSeCreo() {
        val casos = listOf(
            Formato.QR to Crear.contacto(Crear.Ficha("Ana García", "600112233", "ana@ejemplo.es")),
            Formato.QR to "Texto con ñ, tildes y € ✓",
            Formato.EAN13 to "4006381333931",
            Formato.EAN8 to "96385074",
            Formato.CODE128 to "Caja A-12 #3",
            Formato.CODE39 to "PIEZA-12",
            Formato.ITF to "00012345678905",
            Formato.DATA_MATRIX to "SN:ABC123 lote 7",
            Formato.DATA_MATRIX to "Medicina ñ €",
            Formato.AZTEC to "Billete 12A ñ",
            Formato.PDF417 to "Tarjeta de embarque: MAD-BCN 12A",
        )
        for ((f, t) in casos) assertEquals("$f", t, leer(f, t))
        // UPC-A: the Java reader gives it back as UPC-A (12 digits).
        assertEquals("036000291452", leer(Formato.UPCA, "036000291452"))
    }

    @Test fun dibujoConMargenBlancoYTamanoPedido() {
        val p = Codigos.pixeles(Codigos.matriz(Formato.QR, "hola"), Formato.QR, 1000)
        assertTrue(p.ancho in 900..1000); assertEquals(p.ancho, p.alto)
        assertEquals(-1, p.px[0]); assertEquals(-1, p.px[p.px.size - 1])   // white corners
        val b = Codigos.pixeles(Codigos.matriz(Formato.EAN13, "4006381333931"), Formato.EAN13, 1000)
        assertTrue(b.ancho > b.alto)
    }

    @Test fun formatosConElNombreDelHistorial() {
        for (f in Formato.entries) assertEquals(f.nombre, Formatos.normalizar(f.nombre))
        assertEquals(10, Formato.entries.size); assertFalse(Formato.QR in Formato.barras)
        // A created EAN-13 shows as a product, like a scanned one.
        assertTrue(Contenido.analizar("4006381333931", Formato.EAN13.nombre) is Contenido.Producto)
    }

    @Test fun origenCreado() {
        val l = Historial.nueva(0, TimeZone.getTimeZone("UTC"), "QR Code", "hola").copy(origen = Historial.CREADO)
        assertTrue(l.creada); assertFalse(l.importada); assertEquals("creado", l.textoOrigen)
        val i = l.copy(origen = "QR Bot")
        assertFalse(i.creada); assertTrue(i.importada)
    }
}
