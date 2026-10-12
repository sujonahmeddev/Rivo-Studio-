package com.rivo.studio.core.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEncodingTest {
    @Test
    fun roundTripsUtf8AndUtf16ByteOrderMarks() {
        listOf(TextEncoding.UTF8, TextEncoding.UTF16_LE, TextEncoding.UTF16_BE).forEach { encoding ->
            val bytes = TextEncoding.encode("Rivo — বাংলা", encoding, hasBom = true)
            val (text, metadata) = TextEncoding.decode(bytes)
            assertEquals("Rivo — বাংলা", text)
            assertEquals(encoding, metadata.first)
            assertTrue(metadata.second)
        }
    }

    @Test
    fun detectsWindows1252ForNonUtf8Text() {
        val bytes = TextEncoding.encode("café", TextEncoding.WINDOWS_1252, hasBom = false)
        val (text, metadata) = TextEncoding.decode(bytes)
        assertEquals("café", text)
        assertEquals(TextEncoding.WINDOWS_1252, metadata.first)
        assertFalse(metadata.second)
    }

    @Test
    fun refusesBinaryAndInvalidBomMarkedText() {
        assertThrows(IllegalArgumentException::class.java) { TextEncoding.decode(byteArrayOf(65, 0, 66)) }
        assertThrows(IllegalArgumentException::class.java) {
            TextEncoding.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte(), 0xC3.toByte(), 0x28))
        }
    }

    @Test
    fun refusesUnrepresentableLegacyEncoding() {
        assertThrows(IllegalArgumentException::class.java) {
            TextEncoding.encode("emoji 😀", TextEncoding.WINDOWS_1252, hasBom = false)
        }
    }
}
