package com.memoryflow.app.io

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal, dependency-free XLSX reader/writer.
 *
 * An .xlsx file is a ZIP with XML parts; we only need sharedStrings.xml and the first worksheet,
 * so a full Apache POI dependency (which is heavy on Android) is unnecessary.
 */
object XlsxReader {

    fun read(input: InputStream, maxUncompressedBytes: Int = 30 * 1024 * 1024): List<List<String>> {
        val entries = HashMap<String, ByteArray>()
        var total = 0
        ZipInputStream(input).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val bytes = zip.readBytes()
                    total += bytes.size
                    if (total > maxUncompressedBytes) throw IllegalArgumentException("XLSX file is too large")
                    entries[entry.name] = bytes
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val shared = parseSharedStrings(entries["xl/sharedStrings.xml"])
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.entries.firstOrNull { it.key.startsWith("xl/worksheets/") && it.key.endsWith(".xml") }?.value
            ?: return emptyList()
        return parseSheet(sheetBytes, shared)
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val doc = parseXml(bytes)
        val nodes = doc.getElementsByTagName("si")
        val result = ArrayList<String>(nodes.length)
        for (i in 0 until nodes.length) {
            result.add(textOf(nodes.item(i)))
        }
        return result
    }

    private fun parseSheet(bytes: ByteArray, shared: List<String>): List<List<String>> {
        val doc = parseXml(bytes)
        val rowNodes = doc.getElementsByTagName("row")
        val rows = ArrayList<List<String>>()
        for (r in 0 until rowNodes.length) {
            val rowNode = rowNodes.item(r) as? Element ?: continue
            val cells = rowNode.getElementsByTagName("c")
            val values = HashMap<Int, String>()
            var maxIndex = -1
            for (c in 0 until cells.length) {
                val cell = cells.item(c) as? Element ?: continue
                val ref = cell.getAttribute("r")
                val index = if (ref.isNotEmpty()) columnIndex(ref) else maxIndex + 1
                if (index < 0) continue
                val type = cell.getAttribute("t")
                val value = when (type) {
                    "s" -> childText(cell, "v")?.toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
                    "inlineStr" -> textOf(cell)
                    "b" -> if (childText(cell, "v") == "1") "TRUE" else "FALSE"
                    else -> childText(cell, "v").orEmpty()
                }
                values[index] = value
                if (index > maxIndex) maxIndex = index
            }
            if (maxIndex >= 0) {
                val row = ArrayList<String>(maxIndex + 1)
                for (i in 0..maxIndex) row.add(values[i] ?: "")
                rows.add(row)
            }
        }
        return rows
    }

    private fun columnIndex(ref: String): Int {
        var index = -1
        for (ch in ref) {
            if (ch.isLetter()) {
                index = if (index < 0) 0 else index
                index = index * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else {
                break
            }
        }
        return index - 1
    }

    private fun childText(parent: Element, tag: String): String? {
        val nodes = parent.getElementsByTagName(tag)
        if (nodes.length == 0) return null
        return nodes.item(0)?.textContent
    }

    /** Concatenates every <t> below a node (handles rich-text runs). */
    private fun textOf(node: Node): String {
        if (node.nodeType == Node.TEXT_NODE) return node.nodeValue ?: ""
        val sb = StringBuilder()
        if (node is Element) {
            val tNodes = node.getElementsByTagName("t")
            for (i in 0 until tNodes.length) {
                sb.append(tNodes.item(i)?.textContent ?: "")
            }
            if (sb.isNotEmpty()) return sb.toString()
        }
        val children = node.childNodes
        for (i in 0 until children.length) sb.append(textOf(children.item(i)))
        return sb.toString()
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}

object XlsxWriter {

    fun write(output: OutputStream, rows: List<List<String>>, sheetName: String = "Cards") {
        ZipOutputStream(output).use { zip ->
            put(zip, "[Content_Types].xml", contentTypes())
            put(zip, "_rels/.rels", rootRels())
            put(zip, "xl/workbook.xml", workbook(sheetName))
            put(zip, "xl/_rels/workbook.xml.rels", workbookRels())
            put(zip, "xl/worksheets/sheet1.xml", sheet(rows))
        }
    }

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun contentTypes() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
          <Default Extension="xml" ContentType="application/xml"/>
          <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
          <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
        </Types>
    """.trimIndent()

    private fun rootRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
        </Relationships>
    """.trimIndent()

    private fun workbook(sheetName: String) = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
                  xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
          <sheets><sheet name="${escape(sheetName.take(31))}" sheetId="1" r:id="rId1"/></sheets>
        </workbook>
    """.trimIndent()

    private fun workbookRels() = """
        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
        </Relationships>
    """.trimIndent()

    private fun sheet(rows: List<List<String>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>")
        rows.forEachIndexed { rowIndex, row ->
            sb.append("<row r=\"").append(rowIndex + 1).append("\">")
            row.forEachIndexed { colIndex, value ->
                val ref = columnName(colIndex) + (rowIndex + 1)
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                sb.append(escape(value))
                sb.append("</t></is></c>")
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    private fun columnName(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.insert(0, ('A' + rem))
            n = (n - 1) / 26
        }
        return sb.toString()
    }

    private fun escape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("\r", "")
        .replace("\n", "&#10;")
}
