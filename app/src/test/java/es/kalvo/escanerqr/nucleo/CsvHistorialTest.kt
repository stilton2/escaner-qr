package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

/** All data here is made up. */
class CsvHistorialTest {
    private val cab = "\"date\",\"time\",\"time_zone\",\"format\",\"text\",\"notes\",\"favorite\",\"date_utc\",\"time_utc\",\"metadata\""
    private val madrid = TimeZone.getTimeZone("Europe/Madrid")

    private fun fichero(vararg filas: String, fin: String = "\n") = (listOf(cab) + filas).joinToString(fin) + fin

    private val f1 = "\"2023-11-05\",\"10:02:30\",\"hora estándar de Europa central\",\"QR Code\",\"Hola\",\"\",\"0\",\"2023-11-05\",\"09:02:30\",\"\""
    private val f2 = "\"2023-11-06\",\"18:00:00\",\"hora estándar de Europa central\",\"Code 128\",\"12345678\",\"caja azul\",\"1\",\"2023-11-06\",\"17:00:00\",\"{\"\"ec\"\":\"\"L\"\"}\""
    private val f3 = "\"2024-07-01\",\"12:00:00\",\"hora de verano de Europa central\",\"QR Code\",\"WIFI:S:Casa Ejemplo;\nT:WPA2-PSK-AES;\nP:clave\"\"rara;;\",\"\",\"0\",\"2024-07-01\",\"10:00:00\",\"{\"\"ec\"\":\"\"M\"\"}\""

    @Test fun csvComillasEscapadasYSaltos() {
        val filas = Csv.leer(fichero(f1, f2, f3))
        assertEquals(4, filas.size)
        assertEquals("date", filas[0].campos[0])
        assertEquals(10, filas[2].campos.size)
        assertEquals("{\"ec\":\"L\"}", filas[2].campos[9])
        assertEquals("WIFI:S:Casa Ejemplo;\nT:WPA2-PSK-AES;\nP:clave\"rara;;", filas[3].campos[4])
        assertEquals(4, filas[3].linea)
    }

    @Test fun csvCamposVaciosBomYCrlf() {
        val filas = Csv.leer("﻿" + fichero(f1, f2, fin = "\r\n"))
        assertEquals(3, filas.size)
        assertEquals("date", filas[0].campos[0])  // BOM gone
        assertEquals("", filas[1].campos[5])
        assertEquals("", filas[1].campos[9])
        assertEquals("1", filas[2].campos[6])
    }

    @Test fun csvSinComillasYUltimaLineaSinFin() {
        val filas = Csv.leer("a,b,,c\n1,\"dos, con coma\",3")
        assertEquals(listOf("a", "b", "", "c"), filas[0].campos)
        assertEquals(listOf("1", "dos, con coma", "3"), filas[1].campos)
    }

    @Test fun csvSaltoCrlfDentroDeComillasSeQueda() {
        val filas = Csv.leer("\"x\",\"una\r\ndos\"\r\n")
        assertEquals("una\r\ndos", filas[0].campos[1])
    }

    @Test fun importarLeeTodo() {
        val r = Historial.leerCsv(fichero(f1, f2, f3), madrid)
        assertFalse(r.noEsDeQrBot)
        assertEquals(0, r.fallos.size)
        assertEquals(3, r.lecturas.size)
        val b = r.lecturas[1]
        assertEquals("Code 128", b.formato)
        assertEquals("caja azul", b.notas)
        assertTrue(b.favorito)
        assertEquals("{\"ec\":\"L\"}", b.metadata)
        // Real time from the UTC columns.
        assertEquals(Historial.instanteUtc("2023-11-06", "17:00:00"), b.instante)
        assertTrue(r.lecturas[2].texto.contains("\n"))
    }

    @Test fun importarDosVecesNoDuplica() {
        val r = Historial.leerCsv(fichero(f1, f2, f3), madrid)
        val primera = Historial.sinRepetir(r.lecturas, emptySet())
        assertEquals(3, primera.nuevas.size); assertEquals(0, primera.yaEstaban)
        val segunda = Historial.sinRepetir(r.lecturas, primera.nuevas.map { it.clave }.toSet())
        assertEquals(0, segunda.nuevas.size); assertEquals(3, segunda.yaEstaban)
    }

    @Test fun repetidaDentroDelMismoFichero() {
        val r = Historial.leerCsv(fichero(f1, f1, f2), madrid)
        val c = Historial.sinRepetir(r.lecturas, emptySet())
        assertEquals(2, c.nuevas.size); assertEquals(1, c.yaEstaban)
    }

    @Test fun mismoTextoOtraHoraNoEsRepetida() {
        val otra = f1.replace("09:02:30", "09:02:31")
        val r = Historial.leerCsv(fichero(f1, otra), madrid)
        assertEquals(2, Historial.sinRepetir(r.lecturas, emptySet()).nuevas.size)
    }

    @Test fun exportarEImportarDeVueltaIgual() {
        val original = fichero(f1, f2, f3)
        val r = Historial.leerCsv(original, madrid)
        assertEquals(original, Historial.exportar(r.lecturas))
        // And again from our own export.
        val r2 = Historial.leerCsv(Historial.exportar(r.lecturas), madrid)
        assertEquals(r.lecturas, r2.lecturas)
    }

    @Test fun exportarOrdenaDeMasViejaAMasNueva() {
        val r = Historial.leerCsv(fichero(f3, f1, f2), madrid)
        val lineas = Csv.leer(Historial.exportar(r.lecturas))
        assertEquals("Hola", lineas[1].campos[4])
        assertEquals("12345678", lineas[2].campos[4])
    }

    @Test fun filasMalasVanAFallos() {
        val sinTexto = f1.replace("\"Hola\"", "\"\"")
        val corta = "\"2023-11-05\",\"10:02:30\""
        val fechaMala = f1.replace("2023-11-05", "ayer")
        val r = Historial.leerCsv(fichero(f1, sinTexto, corta, fechaMala), madrid)
        assertEquals(1, r.lecturas.size)
        assertEquals(3, r.fallos.size)
        assertEquals(listOf(3, 4, 5), r.fallos.map { it.linea })
        assertTrue(r.fallos[0].motivo.contains("texto"))
        assertTrue(r.fallos[1].motivo.contains("columnas"))
        assertTrue(r.fallos[2].motivo.contains("fecha"))
    }

    @Test fun ficheroCortadoAMedias() {
        val cortado = fichero(f1) + "\"2024-01-01\",\"10:00:00\",\"x\",\"QR Code\",\"WIFI:S:a;\nP:b"
        val r = Historial.leerCsv(cortado, madrid)
        assertEquals(1, r.lecturas.size)
        assertEquals(1, r.fallos.size)
        assertTrue(r.fallos[0].motivo.contains("medias"))
    }

    @Test fun sinColumnasUtcUsaLaHoraLocal() {
        val r = Historial.leerCsv("date,time,format,text\n2023-11-05,10:02:30,QR Code,Hola\n", madrid)
        assertEquals(1, r.lecturas.size)
        assertEquals("09:02:30", r.lecturas[0].horaUtc)
    }

    @Test fun otroCsvNoEsDeQrBot() {
        assertTrue(Historial.leerCsv("nombre,telefono\nPepe,600000000\n").noEsDeQrBot)
        assertTrue(Historial.leerCsv("").noEsDeQrBot)
    }

    @Test fun nuevaLecturaConZonaEnEspanol() {
        val invierno = Historial.instanteUtc("2026-01-10", "09:00:00")!!
        val l = Historial.nueva(invierno, madrid, "QR Code", "x", Formatos.metadata("M"))
        assertEquals("2026-01-10", l.fecha); assertEquals("10:00:00", l.hora)
        assertEquals("09:00:00", l.horaUtc)
        assertEquals("hora estándar de Europa central", l.zona)
        assertEquals("{\"ec\":\"M\"}", l.metadata)
        val verano = Historial.nueva(Historial.instanteUtc("2026-07-10", "09:00:00")!!, madrid, "QR Code", "x")
        assertEquals("11:00:00", verano.hora)
        assertEquals("hora de verano de Europa central", verano.zona)
    }

    @Test fun nuevaSeExportaYSeVuelveALeer() {
        val l = Historial.nueva(1_700_000_000_000, madrid, "Data Matrix", "línea 1\nlínea \"2\"")
        val r = Historial.leerCsv(Historial.exportar(listOf(l)), madrid)
        assertEquals(listOf(l), r.lecturas)
    }

    @Test fun tituloDelDia() {
        assertEquals("Hoy", Historial.tituloDia("2026-11-16", "2026-11-16", "2026-11-15"))
        assertEquals("Ayer", Historial.tituloDia("2026-11-15", "2026-11-16", "2026-11-15"))
        assertEquals("Martes, 10 de noviembre de 2026", Historial.tituloDia("2026-11-10", "2026-11-16", "2026-11-15"))
    }

    @Test fun buscarSinAcentosNiMayusculas() {
        val l = Historial.nueva(0, madrid, "QR Code", "Camión de reparto").copy(notas = "Almacén norte")
        assertTrue(Historial.coincide(l, "camion"))
        assertTrue(Historial.coincide(l, "ALMACEN reparto"))
        assertFalse(Historial.coincide(l, "sur"))
        assertTrue(Historial.coincide(l, "  "))
    }

    @Test fun exportarTexto() {
        val l = Historial.nueva(0, madrid, "QR Code", "Hola").copy(notas = "n")
        val t = Historial.exportarTexto(listOf(l))
        assertTrue(t.contains("Hola")); assertTrue(t.contains("Notas: n"))
    }

    @Test fun nombresDeFormatos() {
        assertEquals("QR Code", Formatos.nombre("QR_CODE"))
        assertEquals("Code 128", Formatos.nombre("CODE_128"))
        assertEquals("Data Matrix", Formatos.nombre("DATA_MATRIX"))
        assertEquals("Code 39", Formatos.nombre("CODE_39"))
        assertEquals("EAN-13", Formatos.nombre("EAN_13"))
        assertEquals("Algo Nuevo", Formatos.nombre("ALGO_NUEVO"))
        assertEquals("", Formatos.metadata(null))
    }
}
