package com.memoryflow.app.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardType
import com.memoryflow.app.data.CardState
import com.memoryflow.app.data.Deck
import com.memoryflow.app.data.Repository
import com.memoryflow.app.review.AnswerEvaluator
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.Charset

data class ImportResult(val imported: Int, val duplicates: Int, val invalid: Int)

/** File based import/export. Everything happens locally, nothing is uploaded. */
class ImportExportManager(private val context: Context) {

    private val repo = Repository.get(context)

    fun displayName(uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            }
        }.getOrNull()
    }

    /** Reads a CSV, XLSX, TSV or TXT file and returns parsed cards without touching the database. */
    fun readPreview(uri: Uri, fileName: String?): List<ParsedCard> {
        val name = (fileName ?: displayName(uri) ?: "").lowercase()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("Cannot open file")
        return when {
            name.endsWith(".xlsx") -> {
                val rows = XlsxReader.read(bytes.inputStream())
                CardTextParser.parseRows(rows)
            }
            name.endsWith(".csv") || name.endsWith(".tsv") -> {
                CardTextParser.parseCsv(decode(bytes))
            }
            else -> CardTextParser.parseBulk(decode(bytes))
        }
    }

    private fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0]
            val b1 = bytes[1]
            if (b0 == 0xFE.toByte() && b1 == 0xFF.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            }
            if (b0 == 0xFF.toByte() && b1 == 0xFE.toByte()) {
                return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            }
        }
        var text = String(bytes, Charsets.UTF_8)
        if (text.startsWith("\uFEFF")) text = text.substring(1)
        // A legacy Windows-1252 file (common for German TXT exports) is not valid UTF-8.
        if (text.contains('\uFFFD')) {
            val fallback = String(bytes, Charset.forName("windows-1252"))
            if (!fallback.contains('\uFFFD')) return fallback
        }
        return text
    }

    /** Counts new / duplicate terms for a deck without writing anything. */
    fun previewPlan(deckId: Long, cards: List<ParsedCard>): ImportPlan {
        val existing = repo.cardFronts(deckId).mapTo(HashSet()) { ImportPlanner.key(it) }
        return ImportPlanner.plan(existing, cards)
    }

    fun importInto(deckId: Long, cards: List<ParsedCard>): ImportResult {
        val plan = previewPlan(deckId, cards)
        val now = System.currentTimeMillis()
        val toInsert = plan.toImport.map { parsed ->
            val type = if (AnswerEvaluator.splitPoints(parsed.back).size >= 2) CardType.MULTI_POINT else CardType.VOCABULARY
            Card(
                deckId = deckId,
                front = parsed.front,
                back = parsed.back,
                hint1 = parsed.hint1,
                hint2 = parsed.hint2,
                explanation = parsed.explanation,
                altAnswers = parsed.altAnswers,
                type = type,
                state = CardState.NEW,
                due = now,
                createdAt = now,
                // A term imported without a meaning is stored as a draft: it shows up in the
                // deck, but stays out of reviews until the user adds the answer.
                suspended = parsed.back.isBlank()
            )
        }
        val inserted = repo.insertCardsBatch(toInsert)
        return ImportResult(inserted, plan.duplicates, plan.invalid)
    }

    fun exportCsv(uri: Uri, deckId: Long?): Int {
        val rows = exportRows(deckId)
        val text = CardTextParser.toCsv(rows)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())) // UTF-8 BOM for Excel
            out.write(text.toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot write file")
        return rows.size - 1
    }

    fun exportXlsx(uri: Uri, deckId: Long?): Int {
        val rows = exportRows(deckId)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            XlsxWriter.write(out, rows, "Cards")
        } ?: throw IllegalStateException("Cannot write file")
        return rows.size - 1
    }

    private fun exportRows(deckId: Long?): List<List<String>> {
        val rows = ArrayList<List<String>>()
        rows.add(listOf("front", "back", "hint1", "hint2", "explanation", "alternatives", "deck", "type", "due", "stability", "difficulty", "reps", "lapses"))
        val decks = repo.decks(includeArchived = true).associateBy { it.id }
        val targetDecks = if (deckId == null) decks.values.toList() else listOfNotNull(decks[deckId])
        for (deck in targetDecks) {
            for (card in repo.cards(deck.id)) {
                rows.add(
                    listOf(
                        card.front,
                        card.back,
                        card.hint1,
                        card.hint2,
                        card.explanation,
                        card.altAnswers,
                        deck.name,
                        card.type.name,
                        card.due.toString(),
                        card.stability.toString(),
                        card.difficulty.toString(),
                        card.reps.toString(),
                        card.lapses.toString()
                    )
                )
            }
        }
        return rows
    }

    // ---- Full backup ------------------------------------------------------

    fun backupJson(uri: Uri): Int {
        val root = JSONObject()
        root.put("app", "Memory Flow")
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        val decksArray = JSONArray()
        val cardsArray = JSONArray()
        for (deck in repo.decks(includeArchived = true)) {
            decksArray.put(
                JSONObject().apply {
                    put("id", deck.id)
                    put("name", deck.name)
                    put("description", deck.description)
                    put("category", deck.category)
                    put("isActive", deck.isActive)
                    put("colorIndex", deck.colorIndex)
                    put("dailyLimit", deck.dailyLimit)
                    put("newPerDay", deck.newPerDay)
                    put("notificationsEnabled", deck.notificationsEnabled)
                    put("archived", deck.archived)
                }
            )
            for (card in repo.cards(deck.id)) {
                cardsArray.put(
                    JSONObject().apply {
                        put("deckId", deck.id)
                        put("front", card.front)
                        put("back", card.back)
                        put("altAnswers", card.altAnswers)
                        put("hint1", card.hint1)
                        put("hint2", card.hint2)
                        put("explanation", card.explanation)
                        put("type", card.type.name)
                        put("tags", card.tags)
                        put("state", card.state)
                        put("due", card.due)
                        put("stability", card.stability)
                        put("difficulty", card.difficulty)
                        put("reps", card.reps)
                        put("lapses", card.lapses)
                        put("lastReview", card.lastReview)
                        put("createdAt", card.createdAt)
                    }
                )
            }
        }
        root.put("decks", decksArray)
        root.put("cards", cardsArray)
        context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
            out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw IllegalStateException("Cannot write file")
        return cardsArray.length()
    }

    /** Restores a backup into new decks, so existing data is never overwritten. */
    fun restoreJson(uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }?.toString(Charsets.UTF_8)
            ?: throw IllegalStateException("Cannot read file")
        val root = JSONObject(text)
        val decksJson = root.optJSONArray("decks") ?: JSONArray()
        val cardsJson = root.optJSONArray("cards") ?: JSONArray()
        val now = System.currentTimeMillis()
        val idMap = HashMap<Long, Long>()
        for (i in 0 until decksJson.length()) {
            val d = decksJson.getJSONObject(i)
            val newId = repo.createDeck(
                name = d.optString("name", "Imported deck"),
                description = d.optString("description", ""),
                category = d.optString("category", ""),
                active = false
            )
            idMap[d.optLong("id")] = newId
            val deck = repo.deck(newId)
            if (deck != null) {
                repo.saveDeck(
                    deck.copy(
                        dailyLimit = d.optInt("dailyLimit", 20),
                        newPerDay = d.optInt("newPerDay", 10),
                        colorIndex = d.optInt("colorIndex", 0)
                    )
                )
            }
        }
        var restored = 0
        for (i in 0 until cardsJson.length()) {
            val c = cardsJson.getJSONObject(i)
            val deckId = idMap[c.optLong("deckId")] ?: continue
            repo.insertCard(
                Card(
                    deckId = deckId,
                    front = c.optString("front"),
                    back = c.optString("back"),
                    altAnswers = c.optString("altAnswers"),
                    hint1 = c.optString("hint1"),
                    hint2 = c.optString("hint2"),
                    explanation = c.optString("explanation"),
                    type = CardType.from(c.optString("type")),
                    tags = c.optString("tags"),
                    state = c.optInt("state", 0),
                    due = c.optLong("due", now),
                    stability = c.optDouble("stability", 0.0),
                    difficulty = c.optDouble("difficulty", 0.0),
                    reps = c.optInt("reps", 0),
                    lapses = c.optInt("lapses", 0),
                    lastReview = c.optLong("lastReview", 0L),
                    createdAt = c.optLong("createdAt", now)
                )
            )
            restored++
        }
        return restored
    }

    fun suggestCsvName(): String = "memory-flow-cards.csv"
    fun suggestXlsxName(): String = "memory-flow-cards.xlsx"
    fun suggestBackupName(): String = "memory-flow-backup.json"
}
