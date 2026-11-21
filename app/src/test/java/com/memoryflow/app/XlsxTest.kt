package com.memoryflow.app

import com.memoryflow.app.io.XlsxReader
import com.memoryflow.app.io.XlsxWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class XlsxTest {

    @Test
    fun `written workbook can be read back`() {
        val rows = listOf(
            listOf("front", "back", "hint1"),
            listOf("das Haus", "البيت", "مبنى للسكن"),
            listOf("der Baum", "الشجرة", ""),
            listOf("die Straße", "الشارع", "طريق")
        )
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(it, rows) }.toByteArray()
        val read = XlsxReader.read(bytes.inputStream())
        assertEquals(4, read.size)
        assertEquals("das Haus", read[1][0])
        assertEquals("البيت", read[1][1])
        assertEquals("مبنى للسكن", read[1][2])
        assertEquals("die Straße", read[3][0])
        assertEquals("", read[2][2])
    }

    @Test
    fun `sheets with many columns keep their order`() {
        val rows = listOf((0 until 30).map { "c$it" })
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(it, rows) }.toByteArray()
        val read = XlsxReader.read(bytes.inputStream())
        assertEquals(1, read.size)
        assertEquals(30, read[0].size)
        assertEquals("c0", read[0][0])
        assertEquals("c26", read[0][26])
        assertEquals("c29", read[0][29])
    }

    @Test
    fun `special characters are escaped and preserved`() {
        val rows = listOf(listOf("a < b & c > d", "\"quoted\"", "line1\nline2"))
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(it, rows) }.toByteArray()
        val read = XlsxReader.read(bytes.inputStream())
        assertEquals("a < b & c > d", read[0][0])
        assertEquals("\"quoted\"", read[0][1])
        assertEquals("line1\nline2", read[0][2])
    }

    @Test
    fun `empty workbook returns no rows`() {
        val bytes = ByteArrayOutputStream().also { XlsxWriter.write(it, emptyList()) }.toByteArray()
        assertEquals(0, XlsxReader.read(bytes.inputStream()).size)
    }
}
