package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** History rows and day headers of the new look (S-J4). Made-up values only. */
class ResumenTest {
    @Test fun equipoYMac() {
        val r = Resumenes.de("""{"dm":"AB-CD123-X","dma":"102030abcdef"}""", "QR Code")
        assertEquals("10:20:30:AB:CD:EF", r.principal)
        assertEquals("Equipo · AB-CD123-X · 08:31", Resumenes.lineaDebajo(r, "08:31", ""))
        assertEquals(Clase.EQUIPO, r.clase); assertTrue(r.mono)
        val m = Resumenes.de("0A1B2C3D4E5F", "Code 128")
        assertEquals("MAC · 08:31", Resumenes.lineaDebajo(m, "08:31", ""))
    }

    @Test fun simAgrupada() {
        val r = Resumenes.de("8901234567890123456", "Code 128")
        assertEquals("8901 2345 6789 0123 456", r.principal)
        assertEquals("SIM · Code 128 · 08:31", Resumenes.lineaDebajo(r, "08:31", ""))
    }

    @Test fun articulo() {
        val r = Resumenes.de("1234-000056 IPC-Z99 5K01B2C 03/02/2024 5K01B2CQRS9X001", "Code 128")
        assertEquals("IPC-Z99", r.principal)
        assertEquals("Artículo · 1234-000056 · 5K01B2CQRS9X001 · 10:00", Resumenes.lineaDebajo(r, "10:00", ""))
        assertEquals("5678-000012", Resumenes.de("5678-000012", "Code 128").principal)
    }

    @Test fun wifiEnlaceYBarras() {
        val w = Resumenes.de("WIFI:S:Casa_Prueba;T:WPA;P:12345678;;", "QR Code")
        assertEquals("Casa_Prueba", w.principal); assertEquals(Clase.WIFI, w.clase)
        assertEquals("Wifi · 20:45", Resumenes.lineaDebajo(w, "20:45", ""))
        val e = Resumenes.de("https://www.example.org/abc/", "QR Code")
        assertEquals("example.org/abc", e.principal); assertEquals(Clase.ENLACE, e.clase)
        val b = Resumenes.de("ABC-123", "Code 39")
        assertEquals(Clase.BARRAS, b.clase); assertEquals("Code 39 · 09:00", Resumenes.lineaDebajo(b, "09:00", ""))
        val p = Resumenes.de("8412345678901", "EAN-13")
        assertEquals(Clase.BARRAS, p.clase); assertFalse(p.mono)
        val t = Resumenes.de("Hola\n\nqué tal", "QR Code")
        assertEquals("Hola · qué tal", t.principal); assertEquals("QR · 09:00 · nota", Resumenes.lineaDebajo(t, "09:00", "nota"))
    }

    @Test fun cabecerasDeDia() {
        assertEquals("HOY", Historial.cabeceraDia("2026-11-16", "2026-11-16", "2026-11-15"))
        assertEquals("AYER", Historial.cabeceraDia("2026-11-15", "2026-11-16", "2026-11-15"))
        assertEquals("SÁBADO, 14 DE NOVIEMBRE", Historial.cabeceraDia("2026-11-14", "2026-11-16", "2026-11-15"))
        assertEquals("MIÉRCOLES, 5 DE NOVIEMBRE DE 2025", Historial.cabeceraDia("2025-11-05", "2026-11-16", "2026-11-15"))
    }
}
