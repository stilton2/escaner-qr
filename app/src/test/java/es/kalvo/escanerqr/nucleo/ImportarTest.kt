package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** «Importar historial» (S-J5). Every file here is made up, with the shapes each app writes. */
class ImportarTest {
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")
    private val ahora = Historial.instanteUtc("2026-11-16", "09:30:00")!!

    private fun hecho(texto: String, nombre: String = "fichero.csv"): Importar.Listo {
        val r = Importar.analizarTexto(texto, nombre, madrid, ahora)
        assertTrue("esperaba Hecho y es $r", r is Importar.Resultado.Hecho)
        return (r as Importar.Resultado.Hecho).listo
    }

    private fun utc(f: String, h: String) = Historial.instanteUtc(f, h)!!

    // ---------------- QR Bot (no cambia) ----------------

    @Test fun qrBotSigueIgual() {
        val csv = "\"date\",\"time\",\"time_zone\",\"format\",\"text\",\"notes\",\"favorite\",\"date_utc\",\"time_utc\",\"metadata\"\n" +
            "\"2023-11-05\",\"10:02:30\",\"hora estándar de Europa central\",\"QR Code\",\"Hola\",\"n\",\"1\",\"2023-11-05\",\"09:02:30\",\"{\"\"ec\"\":\"\"M\"\"}\"\n"
        val l = hecho(csv)
        assertEquals("QR Bot", l.origen)
        assertEquals(1, l.lecturas.size)
        val a = l.lecturas[0]
        assertEquals(Historial.leerCsv(csv, madrid).lecturas[0].copy(origen = "QR Bot"), a)
        assertEquals("importado de QR Bot", a.textoOrigen)
        assertTrue(a.importada); assertTrue(a.favorito)
        assertFalse(l.sinFecha)
    }

    // ---------------- Binary Eye ----------------

    @Test fun binaryEyeCsvConComaYPuntoYComa() {
        val cab = "pinned,_datetime,format,name,content,error_correction_level,version,sequence_size,sequence_index,sequence_id,gtin_country,gtin_add_on,gtin_price,gtin_issue_number\n"
        val f1 = "\"0\",\"2025-03-01 18:04:05:123\",\"QR_CODE\",\"\",\"https://example.org/a\",\"M\",\"2\",\"-1\",\"-1\",\"\",\"\",\"\",\"\",\"\",\n"
        val f2 = "\"1\",\"2025-03-02 08:00:00\",\"EAN_13\",\"caja\",\"8410000000000\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\"\",\n"
        val l = hecho(cab + f1 + f2)
        assertEquals("Binary Eye", l.origen)
        assertEquals(2, l.lecturas.size); assertEquals(0, l.fallos.size)
        val a = l.lecturas[0]
        assertEquals("QR Code", a.formato); assertEquals("https://example.org/a", a.texto)
        assertEquals("2025-03-01", a.fecha); assertEquals("18:04:05", a.hora); assertEquals("17:04:05", a.horaUtc)
        assertEquals("{\"ec\":\"M\"}", a.metadata)
        val b = l.lecturas[1]
        assertEquals("EAN-13", b.formato); assertEquals("caja", b.notas); assertTrue(b.favorito)

        // Same file with «;» (Binary Eye lets you choose the separator).
        val pyc = (cab + f1 + f2).replace(",", ";")
        val l2 = hecho(pyc)
        assertEquals("Binary Eye", l2.origen)
        assertEquals(l.lecturas.map { it.texto }, l2.lecturas.map { it.texto })
    }

    @Test fun binaryEyeJson() {
        val json = """[{"pinned":"0","_datetime":"2025-03-01 18:04:05","format":"DATA_MATRIX","name":"","content":"línea 1\nlínea 2","error_correction_level":"","version":""},
            {"pinned":"1","_datetime":"2025-03-02 08:00:00","format":"CODE_128","name":"nota","content":"ABC-123"}]"""
        val l = hecho(json, "BinaryEye.json")
        assertEquals("Binary Eye", l.origen)
        assertEquals(listOf("línea 1\nlínea 2", "ABC-123"), l.lecturas.map { it.texto })
        assertEquals(listOf("Data Matrix", "Code 128"), l.lecturas.map { it.formato })
        assertEquals("nota", l.lecturas[1].notas); assertTrue(l.lecturas[1].favorito)
    }

    // ---------------- QR & Barcode Scanner (wewewe718) ----------------

    @Test fun qrBarcodeScannerCsvSinComillas() {
        val csv = "Date,Format,Text\n" +
            "2024-06-01 10:00:00,QR_CODE,Hola, con coma\n" +
            "2024-06-01 11:00:00,QR_CODE,WIFI:S:Casa Ejemplo;\nT:WPA;P:clave1234;;\n" +
            "2024-06-02 12:30:00,CODE_39,ABC123\n"
        val l = hecho(csv)
        assertEquals("QR & Barcode Scanner", l.origen)
        assertEquals(listOf("Hola, con coma", "WIFI:S:Casa Ejemplo;\nT:WPA;P:clave1234;;", "ABC123"), l.lecturas.map { it.texto })
        assertEquals("Code 39", l.lecturas[2].formato)
        assertEquals("10:00:00", l.lecturas[0].hora)
    }

    @Test fun qrBarcodeScannerJson() {
        val l = hecho("""[{"date":"2024-06-01 10:00:00","format":"QR_CODE","text":"https://example.org"}]""", "x.json")
        assertEquals("QR & Barcode Scanner", l.origen)
        assertEquals("QR Code", l.lecturas[0].formato)
    }

    // ---------------- Barcode Scanner de ZXing ----------------

    @Test fun zxingSinCabecera() {
        val ms = utc("2020-01-02", "03:04:05")
        val csv = "\"https://example.org\",\"example.org\",\"QR_CODE\",\"$ms\",\"2 ene. 2020 4:04:05\",\"\"\r\n" +
            "\"8410000000000\",\"8410000000000\",\"EAN_13\",\"${ms + 1000}\",\"…\",\"\"\r\n"
        val l = hecho(csv, "History.csv")
        assertEquals("Barcode Scanner (ZXing)", l.origen)
        assertEquals(2, l.lecturas.size)
        assertEquals(ms, l.lecturas[0].instante)
        assertEquals("EAN-13", l.lecturas[1].formato)
    }

    // ---------------- Genérico ----------------

    @Test fun genericoColumnasRarasPuntoYComaYEpochEnMs() {
        val ms = utc("2025-05-05", "05:05:05")
        val csv = "Nº;Código leído;Momento;Tipo de código;Comentario\n1;ABC-001;$ms;qrcode;primero\n2;\"con ; dentro\";${ms + 60_000};Code128;\n"
        val l = hecho(csv, "inventario.csv")
        assertEquals("inventario.csv", l.origen)
        assertEquals(listOf("ABC-001", "con ; dentro"), l.lecturas.map { it.texto })
        assertEquals(ms, l.lecturas[0].instante)
        assertEquals(listOf("QR Code", "Code 128"), l.lecturas.map { it.formato })
        assertEquals("primero", l.lecturas[0].notas)
        assertEquals("importado de inventario.csv", l.lecturas[0].textoOrigen)
    }

    @Test fun genericoFechaYHoraSeparadasYTabulador() {
        val l = hecho("value\tfecha\thora\n" + "uno\t16/11/2026\t10:15\n" + "dos\t2026-11-16\t10:16:30\n", "t.tsv")
        assertEquals(listOf("uno", "dos"), l.lecturas.map { it.texto })
        assertEquals("10:15:00", l.lecturas[0].hora); assertEquals("2026-11-16", l.lecturas[0].fecha)
        assertEquals("10:16:30", l.lecturas[1].hora)
        assertEquals(Formatos.DESCONOCIDO, l.lecturas[0].formato)
    }

    @Test fun genericoEpochEnSegundosEIsoConZona() {
        val l = hecho("data,timestamp\nA,1700000000\nB,2023-11-14T22:13:20Z\nC,2023-11-14T23:13:20+01:00\nD,ayer\n")
        assertEquals(3, l.lecturas.size)
        assertEquals(setOf(1_700_000_000_000L), l.lecturas.map { it.instante }.toSet())
        assertEquals(1, l.fallos.size); assertTrue(l.fallos[0].motivo.contains("fecha"))
    }

    @Test fun bomYLatin1() {
        val conBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "texto,fecha\nCamión,2024-01-02 03:04:05\n".toByteArray(Charsets.UTF_8)
        val r1 = Importar.analizar(conBom, "a.csv", madrid, ahora) as Importar.Resultado.Hecho
        assertEquals("Camión", r1.listo.lecturas[0].texto); assertEquals("UTF-8", r1.listo.codificacion)

        val latin = "contenido;fecha\nAlmacén ñ;02/01/2024 03:04\n".toByteArray(Charsets.ISO_8859_1)
        val r2 = Importar.analizar(latin, "b.csv", madrid, ahora) as Importar.Resultado.Hecho
        assertEquals("Almacén ñ", r2.listo.lecturas[0].texto); assertEquals("Latin-1", r2.listo.codificacion)
        assertEquals("2024-01-02", r2.listo.lecturas[0].fecha)
    }

    @Test fun sinColumnaDeFechaUsaLaHoraDeImportarYNoDuplica() {
        val l = hecho("Contenido,Formato\nuno,QR_CODE\ndos,QR_CODE\n", "lista.csv")
        assertTrue(l.sinFecha)
        assertEquals(listOf(ahora, ahora), l.lecturas.map { it.instante })
        val primera = Historial.sinRepetir(l.lecturas, emptySet(), true, emptySet())
        assertEquals(2, primera.nuevas.size)
        // Imported again later (another «now»): nothing new.
        val otra = (Importar.analizarTexto("Contenido,Formato\nuno,QR_CODE\ndos,QR_CODE\n", "lista.csv", madrid, ahora + 3_600_000) as Importar.Resultado.Hecho).listo
        val segunda = Historial.sinRepetir(otra.lecturas, primera.nuevas.map { it.clave }.toSet(), true, primera.nuevas.map { it.claveSinHora }.toSet())
        assertEquals(0, segunda.nuevas.size); assertEquals(2, segunda.yaEstaban)
    }

    // ---------------- No reconoce las columnas: elegir ----------------

    @Test fun columnasDesconocidasPreguntaYLuegoImporta() {
        val csv = "col_a,col_b,col_c\nX1,05/06/2024 10:00,ref uno\nX2,06/06/2024 11:30,ref dos\n"
        val r = Importar.analizarTexto(csv, "raro.csv", madrid, ahora)
        assertTrue(r is Importar.Resultado.Elegir)
        val e = r as Importar.Resultado.Elegir
        assertEquals(3, e.columnas); assertTrue(e.primeraEsCabecera)
        assertEquals(listOf("col_a", "col_b", "col_c"), e.muestra[0])
        val l = Importar.conColumnas(e, colTexto = 2, colFecha = 1, saltarPrimera = true, tz = madrid, ahora = ahora)
        assertEquals(listOf("ref uno", "ref dos"), l.lecturas.map { it.texto })
        assertEquals("2024-06-05", l.lecturas[0].fecha); assertEquals("10:00:00", l.lecturas[0].hora)
        assertEquals("raro.csv", l.origen)
        // Without a date: the import time, and it says so.
        val s = Importar.conColumnas(e, 0, null, true, madrid, ahora)
        assertTrue(s.sinFecha); assertEquals(ahora, s.lecturas[0].instante)
    }

    @Test fun sinCabeceraNoLaSalta() {
        val e = Importar.analizarTexto("1,2024-01-01\n2,2024-01-02\n", "n.csv", madrid, ahora) as Importar.Resultado.Elegir
        assertFalse(e.primeraEsCabecera)
        assertEquals(2, Importar.conColumnas(e, 0, 1, false, madrid, ahora).lecturas.size)
    }

    // ---------------- TXT ----------------

    @Test fun txtUnaLecturaPorLinea() {
        val l = hecho("primera\r\n\r\nsegunda, con coma\n{\"dm\":\"XY-1\",\"dma\":\"0A:1B:2C:3D:4E:5F\"}\n", "lecturas.txt")
        assertEquals(listOf("primera", "segunda, con coma", "{\"dm\":\"XY-1\",\"dma\":\"0A:1B:2C:3D:4E:5F\"}"), l.lecturas.map { it.texto })
        assertTrue(l.sinFecha); assertEquals("lecturas.txt", l.origen)
        // A file of JSON labels, one per line, is read as lines too.
        val j = hecho("{\"dm\":\"XY-1\",\"dma\":\"0A:1B:2C:3D:4E:5F\"}\n{\"dm\":\"XY-2\",\"dma\":\"0A:1B:2C:3D:4E:60\"}\n", "etiquetas.txt")
        assertEquals(2, j.lecturas.size)
    }

    @Test fun vacioNoVale() {
        assertTrue(Importar.analizarTexto("  \n", "v.csv", madrid, ahora) is Importar.Resultado.NoVale)
        assertTrue(Importar.analizarTexto("[1, 2", "v.json", madrid, ahora) is Importar.Resultado.NoVale)
    }

    // ---------------- Nuestro JSON ----------------

    @Test fun exportarJsonYVolverAImportarConservaElOrigen() {
        val a = Historial.nueva(1_700_000_000_000, madrid, "QR Code", "línea \"1\"\nlínea 2").copy(notas = "n", favorito = true)
        val b = Historial.nueva(1_700_000_100_000, madrid, "Code 128", "ABC").copy(origen = "QR Bot")
        val json = Historial.exportarJson(listOf(b, a))
        assertTrue(json.startsWith("{\"app\":\"es.kalvo.escanerqr\""))
        val l = hecho(json, "codes.json")
        assertEquals(listOf(a, b), l.lecturas)
        assertEquals("escaneado", l.lecturas[0].textoOrigen)
        assertEquals("importado de QR Bot", l.lecturas[1].textoOrigen)
    }

    // ---------------- piezas ----------------

    @Test fun jsonLeeYEscribe() {
        val v = Json.leer("""{"a":[1,2.5,true,null,"x\"\\\u00e9\n"],"b":{}}""") as Map<*, *>
        assertEquals(listOf(1L, 2.5, true, null, "x\"\\é\n"), v["a"])
        assertEquals("{\"k\":\"a\\nb\",\"n\":[1,true]}", Json.escribir(linkedMapOf("k" to "a\nb", "n" to listOf(1L, true))))
        assertEquals("3", Json.comoTexto(3.0))
    }

    @Test fun fechas() {
        val utc = TimeZone.getTimeZone("UTC")
        assertEquals(1_700_000_000_000L, Fechas.leer("1700000000000", utc))
        assertEquals(1_700_000_000_500L, Fechas.leer("1700000000.5", utc))
        assertEquals(utc("2026-11-16", "10:02:30") + 123, Fechas.leer("2026-11-16 10:02:30:123", utc))
        assertEquals(utc("2026-11-16", "10:02:30"), Fechas.leer("2026-11-16T11:02:30+01:00", utc))
        assertEquals(utc("2026-11-03", "00:00:00"), Fechas.leer("03/11/2026", utc))        // day first
        assertEquals(utc("2026-11-23", "00:00:00"), Fechas.leer("11/23/2026", utc))        // 23 cannot be a month
        assertEquals(utc("2026-11-16", "22:05:00"), Fechas.leer("16-11-26 10:05 PM", utc))
        assertEquals(utc("2026-11-16", "09:00:00"), Fechas.leer("16.11.2026 10:00", madrid))
        assertNull(Fechas.leer("31/02/2026", utc))
        assertNull(Fechas.leer("hola", utc))
        assertNull(Fechas.leer("12345", utc))
    }

    @Test fun separadorYCodificacion() {
        assertEquals(',', Importar.separador("a,b,c\n1;2"))
        assertEquals(';', Importar.separador("\n\"a,1\";b;c"))
        assertEquals('\t', Importar.separador("a\tb"))
        assertNull(Importar.separador("solo una columna"))
        assertEquals("UTF-16", Importar.decodificar(byteArrayOf(0xFF.toByte(), 0xFE.toByte(), 'a'.code.toByte(), 0)).second)
    }

    @Test fun nombresDeFormatoDeOtrasApps() {
        assertEquals("QR Code", Formatos.normalizar("QR_CODE"))
        assertEquals("QR Code", Formatos.normalizar("qrcode"))
        assertEquals("QR Code", Formatos.normalizar("QR"))
        assertEquals("EAN-13", Formatos.normalizar("ean13"))
        assertEquals("DataBar", Formatos.normalizar("RSS_14"))
        assertEquals("Code 128", Formatos.normalizar("Code 128"))
        assertEquals(Formatos.DESCONOCIDO, Formatos.normalizar(" "))
        assertEquals("URL", Formatos.normalizar("URL"))
    }

    @Test fun mapaDeColumnas() {
        val m = Importar.mapa(listOf("ID", "Raw", "Texto", "Fecha", "Hora", "Tipo", "Notas", "Favorito"))!!
        assertEquals("Texto", m.texto)  // «Texto» wins over «Raw»
        assertEquals("Fecha", m.fecha); assertEquals("Hora", m.hora); assertEquals("Tipo", m.formato)
        assertEquals("Notas", m.notas); assertEquals("Favorito", m.favorito)
        assertNull(Importar.mapa(listOf("nombre", "telefono")))
    }
}
