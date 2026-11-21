package com.memoryflow.app.io

import java.util.Locale

/** A card parsed from pasted text, CSV or XLSX, before it is written to the database. */
data class ParsedCard(
    val front: String,
    val back: String,
    val hint1: String = "",
    val hint2: String = "",
    val explanation: String = "",
    val altAnswers: String = ""
)

/**
 * Parses bulk text and CSV into [ParsedCard]s. Pure Kotlin so it can be unit tested.
 *
 * Supported TXT line formats:
 *   der Tisch                                  -> one term, no answer yet (a draft card)
 *   die Herausforderung = التحدي                -> front + back
 *   die Herausforderung = التحدي | clue 1 | clue 2
 *   laufen = يمشي / يجري                        -> back + accepted alternative answer
 *   die Erfahrung = الخبرة | الجمع: die Erfahrungen
 *   Frage<TAB>Antwort<TAB>Hinweis 1
 *
 * A term may be followed by labelled lines that carry extra information:
 *
 *   die Erfahrung
 *   Plural: die Erfahrungen
 *   العربية: الخبرة
 *
 * Recognised labels end up in the fields the app already has: Arabic/translation labels fill the
 * answer, everything else (Plural, Präteritum, Partizip II, …) is kept in the explanation.
 */
object CardTextParser {

    private val HEADER_FRONT = setOf("front", "question", "term", "word", "السؤال", "الكلمة", "الوجه", "المصطلح")
    private val HEADER_BACK = setOf("back", "answer", "definition", "الجواب", "الإجابة", "المعنى", "الظهر", "التعريف")

    /** Labels whose value is the translation (the back of the card). */
    private val BACK_LABELS = setOf(
        "arabic", "العربية", "عربي", "الترجمة", "ترجمة", "المعنى", "معنى", "translation", "meaning", "answer", "back"
    )

    /** Labels whose value is extra information, kept verbatim in the explanation field. */
    private val INFO_LABELS = setOf(
        "plural", "الجمع", "جمع", "pluralform",
        "präteritum", "praeteritum", "الماضي",
        "partizip ii", "partizip 2", "partizip", "التصريف الثالث",
        "genitiv", "dativ", "akkusativ",
        "example", "مثال", "note", "ملاحظة", "synonym", "مرادف"
    )

    private val LABEL_LINE = Regex("^([^:]{1,24}):\\s*(.*)$")

    fun parseBulk(text: String): List<ParsedCard> {
        val result = ArrayList<ParsedCard>()
        var current: ParsedCard? = null
        var pendingLabel: String? = null

        for (rawLine in text.split('\n')) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            // Skip binary junk from a wrong encoding instead of creating garbage cards.
            if (line.contains('\uFFFD')) continue
            if (line.any { it.isISOControl() && it != '\t' }) continue

            val labelMatch = LABEL_LINE.find(line)
            if (labelMatch != null) {
                val label = labelMatch.groupValues[1].trim().lowercase(Locale.ROOT)
                if (label in BACK_LABELS || label in INFO_LABELS) {
                    val value = labelMatch.groupValues[2].trim()
                    if (value.isEmpty()) {
                        pendingLabel = label
                    } else {
                        current = current?.let { attach(it, label, value) }
                        pendingLabel = null
                    }
                    continue
                }
            }

            val pending = pendingLabel
            if (pending != null) {
                current = current?.let { attach(it, pending, line) }
                pendingLabel = null
                continue
            }

            current?.let { result.add(it) }
            current = parseLine(line)
        }
        current?.let { result.add(it) }
        return result
    }

    private fun attach(card: ParsedCard, label: String, value: String): ParsedCard {
        return if (label in BACK_LABELS) {
            card.copy(back = value)
        } else {
            val entry = label.replaceFirstChar { it.uppercase(Locale.ROOT) } + ": " + value
            card.copy(
                explanation = if (card.explanation.isBlank()) entry else card.explanation + "\n" + entry
            )
        }
    }

    fun parseLine(rawLine: String): ParsedCard? {
        val line = rawLine.trim()
        if (line.isEmpty()) return null

        if (line.contains('\t')) {
            val parts = line.split('\t')
            if (parts.size >= 2) {
                build(parts)?.let { return it }
            }
        }

        val equalsIndex = line.indexOf('=')
        if (equalsIndex >= 0) {
            val front = line.substring(0, equalsIndex).trim()
            val right = line.substring(equalsIndex + 1).trim()
            if (front.isNotEmpty()) {
                return build(listOf(front) + right.split('|').map { it.trim() })
            }
        }

        for (separator in listOf(" | ", "|", " — ", " - ", " – ")) {
            val idx = line.indexOf(separator)
            if (idx > 0 && idx < line.length - separator.length) {
                val front = line.substring(0, idx).trim()
                val right = line.substring(idx + separator.length).trim()
                if (front.isNotEmpty() && right.isNotEmpty()) {
                    return build(listOf(front) + right.split('|').map { it.trim() })
                }
            }
        }

        // A bare term: one vocabulary per line. The answer stays empty on purpose and the
        // importer stores it as a draft, so it can be completed later without breaking reviews.
        return ParsedCard(front = line, back = "")
    }

    private fun build(parts: List<String>): ParsedCard? {
        val front = parts.getOrNull(0)?.trim().orEmpty()
        if (front.isEmpty()) return null

        val hints = ArrayList<String>()
        val infos = ArrayList<String>()
        for (i in 2 until parts.size) {
            val part = parts[i].trim()
            if (part.isEmpty()) continue
            val labelled = LABEL_LINE.find(part)
            val label = labelled?.groupValues?.get(1)?.trim()?.lowercase(Locale.ROOT).orEmpty()
            if (labelled != null && label in INFO_LABELS) {
                infos.add(label.replaceFirstChar { it.uppercase(Locale.ROOT) } + ": " + labelled.groupValues[2].trim())
            } else {
                hints.add(part)
            }
        }

        // "يمشي / يجري" -> answer "يمشي" plus "يجري" as an accepted alternative.
        var back = parts.getOrNull(1)?.trim().orEmpty()
        var altAnswers = ""
        if (back.contains('/')) {
            val segments = back.split('/').map { it.trim() }.filter { it.isNotEmpty() }
            if (segments.size >= 2 && segments.all { it.length >= 2 }) {
                back = segments[0]
                altAnswers = segments.drop(1).joinToString("|")
            }
        }

        val explanation = if (infos.isEmpty()) parts.getOrNull(4)?.trim().orEmpty() else infos.joinToString("\n")
        return ParsedCard(
            front = front,
            back = back,
            hint1 = hints.getOrNull(0).orEmpty(),
            hint2 = hints.getOrNull(1).orEmpty(),
            explanation = explanation,
            altAnswers = altAnswers
        )
    }

    /** RFC 4180 style CSV/TSV parser: handles quotes, escaped quotes and embedded newlines. */
    fun parseDelimited(text: String): List<List<String>> {
        if (text.isBlank()) return emptyList()
        val delimiter = detectDelimiter(text)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                    field.append('"'); i++
                }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == delimiter -> {
                    row.add(field.toString()); field.setLength(0)
                }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(field.toString()); field.setLength(0)
                    if (row.any { it.isNotBlank() }) rows.add(row)
                    row = ArrayList()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }

    fun parseCsv(text: String): List<ParsedCard> = parseRows(parseDelimited(text))

    private fun detectDelimiter(text: String): Char {
        val firstLine = text.lineSequence().firstOrNull() ?: return ','
        val counts = mapOf(',' to firstLine.count { it == ',' }, ';' to firstLine.count { it == ';' }, '\t' to firstLine.count { it == '\t' })
        return counts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key ?: ','
    }

    fun parseRows(rows: List<List<String>>): List<ParsedCard> {
        if (rows.isEmpty()) return emptyList()
        val first = rows.first().map { it.trim().lowercase() }
        val hasHeader = first.any { it in HEADER_FRONT } && first.any { it in HEADER_BACK }

        var frontIdx = 0
        var backIdx = 1
        var hint1Idx = 2
        var hint2Idx = 3
        var explIdx = 4
        var altIdx = -1
        var dataStart = 0

        if (hasHeader) {
            dataStart = 1
            first.forEachIndexed { index, name ->
                when {
                    name in HEADER_FRONT -> frontIdx = index
                    name in HEADER_BACK -> backIdx = index
                    name == "hint1" || name == "hint 1" || name == "hint" || name == "تلميح1" || name == "تلميح" -> hint1Idx = index
                    name == "hint2" || name == "hint 2" || name == "تلميح2" -> hint2Idx = index
                    name == "explanation" || name == "note" || name == "شرح" || name == "ملاحظة" -> explIdx = index
                    name == "alternatives" || name == "alt" || name == "بدائل" -> altIdx = index
                }
            }
        }

        val result = ArrayList<ParsedCard>()
        for (index in dataStart until rows.size) {
            val row = rows[index]
            val front = row.getOrNull(frontIdx)?.trim().orEmpty()
            if (front.isEmpty()) continue
            result.add(
                ParsedCard(
                    front = front,
                    back = row.getOrNull(backIdx)?.trim().orEmpty(),
                    hint1 = row.getOrNull(hint1Idx)?.trim().orEmpty(),
                    hint2 = row.getOrNull(hint2Idx)?.trim().orEmpty(),
                    explanation = row.getOrNull(explIdx)?.trim().orEmpty(),
                    altAnswers = if (altIdx >= 0) row.getOrNull(altIdx)?.trim().orEmpty() else ""
                )
            )
        }
        return result
    }

    fun toCsv(rows: List<List<String>>): String {
        val sb = StringBuilder()
        for (row in rows) {
            sb.append(row.joinToString(",") { escapeCsv(it) })
            sb.append("\r\n")
        }
        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuotes) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }
}
