package es.kalvo.escanerqr.nucleo

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BinarioTest {
    @Test fun bytesSinTextoSeEnsenanEnHexadecimal() {
        val b = byteArrayOf(0xE1.toByte(), 0xA1.toByte(), 0x00, 0x40, 0x5F)
        assertEquals("Datos binarios (hex): E1 A1 00 40 5F", Binario.texto(b))
    }

    @Test fun sinBytesNoHayTexto() {
        assertNull(Binario.texto(null))
        assertNull(Binario.texto(ByteArray(0)))
    }

    @Test fun idaYVuelta() {
        val b = ByteArray(256) { it.toByte() }
        assertArrayEquals(b, Binario.bytes(Binario.texto(b)!!))
    }

    @Test fun unTextoNormalNoEsBinario() {
        assertNull(Binario.bytes("E1 A1 00"))
        assertNull(Binario.bytes("Datos binarios (hex): "))
        assertNull(Binario.bytes("Datos binarios (hex): E1 XZ"))
        assertNull(Binario.bytes("Datos binarios (hex): E1A1"))
    }

    @Test fun elHexadecimalNoPareceUnaMac() {
        // Six bytes in a row must not be offered as a MAC address.
        val t = Binario.texto(byteArrayOf(0x00, 0x1D, 0x2E, 0x3F, 0x4A, 0x5B))!!
        assertTrue(Mac.buscar(t).vacia)
    }
}
