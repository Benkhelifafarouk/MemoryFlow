package com.memoryflow.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.fsrs.ReviewOutcome
import com.memoryflow.app.fsrs.ReviewScheduler
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Single entry point for all persistence. Every screen and the notification worker go through here,
 * which is what keeps the UI, the database and the notification scheduler in sync.
 */
class Repository private constructor(context: Context, dbName: String = Db.DB_NAME) {

    private val db = Db(context, dbName)
    val settings = SettingsStore(context)

    // ------------------------------------------------------------------
    // Decks
    // ------------------------------------------------------------------

    @Synchronized
    fun decks(includeArchived: Boolean = false): List<Deck> {
        val where = if (includeArchived) "" else "WHERE archived = 0"
        val list = ArrayList<Deck>()
        db.readableDatabase.rawQuery("SELECT * FROM decks $where ORDER BY sort_order ASC, id ASC", null).use { c ->
            while (c.moveToNext()) list.add(mapDeck(c))
        }
        return list
    }

    @Synchronized
    fun deck(id: Long): Deck? = db.readableDatabase.rawQuery("SELECT * FROM decks WHERE id = ?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) mapDeck(c) else null
    }

    @Synchronized
    fun createDeck(
        name: String,
        description: String = "",
        category: String = "",
        active: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        val values = ContentValues().apply {
            put("name", name.trim())
            put("description", description.trim())
            put("category", category.trim())
            put("is_active", if (active) 1 else 0)
            put("color_index", (name.hashCode() and 0x7FFFFFFF) % 6)
            put("daily_limit", 20)
            put("new_per_day", 10)
            put("notifications_enabled", 1)
            put("archived", 0)
            put("sort_order", nextDeckOrder())
            put("created_at", now)
        }
        return db.writableDatabase.insert("decks", null, values)
    }

    private fun nextDeckOrder(): Int = db.readableDatabase
        .rawQuery("SELECT COALESCE(MAX(sort_order), 0) + 1 FROM decks", null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 1
        }

    @Synchronized
    fun saveDeck(deck: Deck) {
        val values = ContentValues().apply {
            put("name", deck.name.trim())
            put("description", deck.description)
            put("category", deck.category)
            put("is_active", if (deck.isActive) 1 else 0)
            put("color_index", deck.colorIndex)
            put("daily_limit", deck.dailyLimit)
            put("new_per_day", deck.newPerDay)
            put("notifications_enabled", if (deck.notificationsEnabled) 1 else 0)
            put("archived", if (deck.archived) 1 else 0)
            put("sort_order", deck.sortOrder)
        }
        db.writableDatabase.update("decks", values, "id = ?", arrayOf(deck.id.toString()))
    }

    @Synchronized
    fun setDeckActive(id: Long, active: Boolean) {
        val values = ContentValues().apply { put("is_active", if (active) 1 else 0) }
        db.writableDatabase.update("decks", values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun setAllActive(active: Boolean) {
        val values = ContentValues().apply { put("is_active", if (active) 1 else 0) }
        db.writableDatabase.update("decks", values, "archived = 0", null)
    }

    @Synchronized
    fun deleteDeck(id: Long) {
        db.writableDatabase.delete("decks", "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun activeDeckIds(): List<Long> {
        val list = ArrayList<Long>()
        db.readableDatabase.rawQuery(
            "SELECT id FROM decks WHERE is_active = 1 AND archived = 0 AND notifications_enabled = 1", null
        ).use { c -> while (c.moveToNext()) list.add(c.getLong(0)) }
        return list
    }

    @Synchronized
    fun resetDeckProgress(id: Long) {
        val values = ContentValues().apply {
            put("state", CardState.NEW)
            put("due", System.currentTimeMillis())
            put("stability", 0.0)
            put("difficulty", 0.0)
            put("reps", 0)
            put("lapses", 0)
            put("last_review", 0L)
            put("weak_count", 0)
        }
        db.writableDatabase.update("cards", values, "deck_id = ?", arrayOf(id.toString()))
        db.writableDatabase.delete("reviews", "deck_id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    @Synchronized
    fun cards(deckId: Long, includeSuspended: Boolean = true): List<Card> {
        val where = if (includeSuspended) "" else "AND suspended = 0"
        val list = ArrayList<Card>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM cards WHERE deck_id = ? $where ORDER BY id ASC", arrayOf(deckId.toString())
        ).use { c -> while (c.moveToNext()) list.add(mapCard(c)) }
        return list
    }

    @Synchronized
    fun card(id: Long): Card? = db.readableDatabase.rawQuery("SELECT * FROM cards WHERE id = ?", arrayOf(id.toString())).use { c ->
        if (c.moveToFirst()) mapCard(c) else null
    }

    @Synchronized
    fun cardFronts(deckId: Long): List<String> {
        val list = ArrayList<String>()
        db.readableDatabase.rawQuery(
            "SELECT front FROM cards WHERE deck_id = ?", arrayOf(deckId.toString())
        ).use { c -> while (c.moveToNext()) list.add(c.getString(0) ?: "") }
        return list
    }

    /** Inserts many cards inside one transaction: 1,000+ terms stay fast and atomic. */
    @Synchronized
    fun insertCardsBatch(cards: List<Card>): Int {
        if (cards.isEmpty()) return 0
        val database = db.writableDatabase
        database.beginTransaction()
        try {
            var inserted = 0
            for (card in cards) {
                if (database.insert("cards", null, cardValues(card)) != -1L) inserted++
            }
            database.setTransactionSuccessful()
            return inserted
        } finally {
            database.endTransaction()
        }
    }

    @Synchronized
    fun insertCard(card: Card): Long {
        val values = cardValues(card)
        return db.writableDatabase.insert("cards", null, values)
    }

    @Synchronized
    fun updateCard(card: Card) {
        db.writableDatabase.update("cards", cardValues(card), "id = ?", arrayOf(card.id.toString()))
    }

    @Synchronized
    fun deleteCard(id: Long) {
        db.writableDatabase.delete("cards", "id = ?", arrayOf(id.toString()))
    }

    private fun cardValues(card: Card): ContentValues = ContentValues().apply {
        put("deck_id", card.deckId)
        put("front", card.front.trim())
        put("back", card.back.trim())
        put("alt_answers", card.altAnswers)
        put("hint1", card.hint1)
        put("hint2", card.hint2)
        put("explanation", card.explanation)
        put("type", card.type.name)
        put("tags", card.tags)
        put("state", card.state)
        put("due", if (card.due <= 0L) System.currentTimeMillis() else card.due)
        put("stability", card.stability)
        put("difficulty", card.difficulty)
        put("reps", card.reps)
        put("lapses", card.lapses)
        put("last_review", card.lastReview)
        put("created_at", if (card.createdAt <= 0L) System.currentTimeMillis() else card.createdAt)
        put("suspended", if (card.suspended) 1 else 0)
        put("weak_count", card.weakCount)
    }

    // ------------------------------------------------------------------
    // Selection (the queue that notifications and the review screen use)
    // ------------------------------------------------------------------

    @Synchronized
    fun setWeakCount(cardId: Long, count: Int) {
        val values = ContentValues().apply { put("weak_count", count.coerceAtLeast(0)) }
        db.writableDatabase.update("cards", values, "id = ?", arrayOf(cardId.toString()))
    }

    /** The words the user explicitly does not know, strongest first. */
    @Synchronized
    fun weakCards(deckIds: List<Long>, limit: Int): List<Card> {
        if (deckIds.isEmpty() || limit <= 0) return emptyList()
        val placeholders = deckIds.joinToString(",") { "?" }
        val list = ArrayList<Card>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM cards WHERE suspended = 0 AND back != '' AND weak_count > 0 " +
                "AND deck_id IN ($placeholders) ORDER BY weak_count DESC, due ASC LIMIT ?",
            (deckIds.map { it.toString() } + limit.toString()).toTypedArray()
        ).use { c -> while (c.moveToNext()) list.add(mapCard(c)) }
        return list
    }

    @Synchronized
    fun dueCards(deckIds: List<Long>, now: Long, limit: Int): List<Card> {
        if (deckIds.isEmpty() || limit <= 0) return emptyList()
        val placeholders = deckIds.joinToString(",") { "?" }
        val list = ArrayList<Card>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM cards WHERE suspended = 0 AND state != 0 AND due <= ? " +
                "AND deck_id IN ($placeholders) ORDER BY weak_count DESC, due ASC, difficulty DESC LIMIT ?",
            (listOf(now.toString()) + deckIds.map { it.toString() } + listOf(limit.toString())).toTypedArray()
        ).use { c -> while (c.moveToNext()) list.add(mapCard(c)) }
        return list
    }

    @Synchronized
    fun newCards(deckId: Long, limit: Int): List<Card> {
        if (limit <= 0) return emptyList()
        val list = ArrayList<Card>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM cards WHERE suspended = 0 AND state = 0 AND deck_id = ? ORDER BY created_at ASC, id ASC LIMIT ?",
            arrayOf(deckId.toString(), limit.toString())
        ).use { c -> while (c.moveToNext()) list.add(mapCard(c)) }
        return list
    }

    @Synchronized
    fun randomCards(deckIds: List<Long>, limit: Int): List<Card> {
        if (deckIds.isEmpty() || limit <= 0) return emptyList()
        val placeholders = deckIds.joinToString(",") { "?" }
        val list = ArrayList<Card>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM cards WHERE suspended = 0 AND deck_id IN ($placeholders) ORDER BY RANDOM() LIMIT ?",
            (deckIds.map { it.toString() } + limit.toString()).toTypedArray()
        ).use { c -> while (c.moveToNext()) list.add(mapCard(c)) }
        return list
    }

    @Synchronized
    fun dueCount(deckIds: List<Long>, now: Long): Int {
        if (deckIds.isEmpty()) return 0
        val placeholders = deckIds.joinToString(",") { "?" }
        return db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state != 0 AND due <= ? AND deck_id IN ($placeholders)",
            (listOf(now.toString()) + deckIds.map { it.toString() }).toTypedArray()
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    @Synchronized
    fun newCount(deckIds: List<Long>): Int {
        if (deckIds.isEmpty()) return 0
        val placeholders = deckIds.joinToString(",") { "?" }
        return db.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state = 0 AND deck_id IN ($placeholders)",
            deckIds.map { it.toString() }.toTypedArray()
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ------------------------------------------------------------------
    // Review results
    // ------------------------------------------------------------------

    @Synchronized
    fun applyOutcome(card: Card, outcome: ReviewOutcome) {
        val values = ContentValues().apply {
            put("state", outcome.state)
            put("stability", outcome.stability)
            put("difficulty", outcome.difficulty)
            put("due", outcome.due)
            put("reps", outcome.reps)
            put("lapses", outcome.lapses)
            put("last_review", System.currentTimeMillis())
        }
        db.writableDatabase.update("cards", values, "id = ?", arrayOf(card.id.toString()))
    }

    @Synchronized
    fun insertReview(log: ReviewLog): Long = db.writableDatabase.insert("reviews", null, ContentValues().apply {
        put("card_id", log.cardId)
        put("deck_id", log.deckId)
        put("rating", log.rating)
        put("answer_text", log.answerText)
        put("was_correct", if (log.wasCorrect) 1 else 0)
        put("hint_count", log.hintCount)
        put("reviewed_at", if (log.reviewedAt <= 0L) System.currentTimeMillis() else log.reviewedAt)
        put("elapsed_days", log.elapsedDays)
        put("scheduled_days", log.scheduledDays)
        put("stability_after", log.stabilityAfter)
        put("difficulty_after", log.difficultyAfter)
        put("was_new", if (log.wasNew) 1 else 0)
        put("is_practice", if (log.isPractice) 1 else 0)
    })

    /**
     * One place where a grade becomes a permanent state change.
     * Used by the in-app review screen and by the notification reply path.
     */
    @Synchronized
    fun grade(
        cardId: Long,
        grade: Int,
        answerText: String,
        hintCount: Int,
        practice: Boolean = false,
        now: Long = System.currentTimeMillis()
    ): ReviewOutcome? {
        val card = card(cardId) ?: return null

        // Weak-word policy: a failure (Again / "I don't know") raises the weak counter,
        // a correct answer lowers it again, so words leave the list gradually.
        val newWeakCount = if (grade == Grades.AGAIN) card.weakCount + 1 else max(0, card.weakCount - 1)
        if (newWeakCount != card.weakCount) setWeakCount(card.id, newWeakCount)

        // Random practice must never damage the real FSRS schedule.
        if (practice) {
            insertReview(
                ReviewLog(
                    cardId = card.id,
                    deckId = card.deckId,
                    rating = grade,
                    answerText = answerText.take(500),
                    wasCorrect = grade != Grades.AGAIN,
                    hintCount = hintCount,
                    reviewedAt = now,
                    elapsedDays = 0.0,
                    scheduledDays = 0,
                    stabilityAfter = card.stability,
                    difficultyAfter = card.difficulty,
                    wasNew = false,
                    isPractice = true
                )
            )
            return ReviewOutcome(
                state = card.state,
                stability = card.stability,
                difficulty = card.difficulty,
                intervalDays = 0,
                due = card.due,
                elapsedDays = 0.0,
                reps = card.reps,
                lapses = card.lapses,
                correct = grade != Grades.AGAIN
            )
        }

        val outcome = ReviewScheduler.review(card, grade, now, settings.desiredRetention.toDouble())
        applyOutcome(card, outcome)
        insertReview(
            ReviewLog(
                cardId = card.id,
                deckId = card.deckId,
                rating = grade,
                answerText = answerText.take(500),
                wasCorrect = outcome.correct,
                hintCount = hintCount,
                reviewedAt = now,
                elapsedDays = outcome.elapsedDays,
                scheduledDays = outcome.intervalDays,
                stabilityAfter = outcome.stability,
                difficultyAfter = outcome.difficulty,
                wasNew = card.state == CardState.NEW,
                isPractice = false
            )
        )
        return outcome
    }

    @Synchronized
    fun reviews(deckId: Long?, since: Long, limit: Int = 200): List<ReviewLog> {
        val selection = StringBuilder("reviewed_at >= ?")
        val args = arrayListOf(since.toString())
        if (deckId != null) {
            selection.append(" AND deck_id = ?")
            args.add(deckId.toString())
        }
        args.add(limit.toString())
        val list = ArrayList<ReviewLog>()
        db.readableDatabase.rawQuery(
            "SELECT * FROM reviews WHERE $selection ORDER BY reviewed_at DESC LIMIT ?", args.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    ReviewLog(
                        id = c.lng("id"),
                        cardId = c.lng("card_id"),
                        deckId = c.lng("deck_id"),
                        rating = c.int("rating"),
                        answerText = c.strOr("", "answer_text"),
                        wasCorrect = c.bool("was_correct"),
                        hintCount = c.int("hint_count"),
                        reviewedAt = c.lng("reviewed_at"),
                        elapsedDays = c.dbl("elapsed_days"),
                        scheduledDays = c.int("scheduled_days"),
                        stabilityAfter = c.dbl("stability_after"),
                        difficultyAfter = c.dbl("difficulty_after")
                    )
                )
            }
        }
        return list
    }

    // ------------------------------------------------------------------
    // Statistics (all computed from real rows, nothing is fabricated)
    // ------------------------------------------------------------------

    @Synchronized
    fun deckStats(deckId: Long, now: Long = System.currentTimeMillis()): DeckStats {
        val dayStart = startOfDay(now)
        val monthAgo = now - 30L * ReviewScheduler.DAY_MS
        val total = count("SELECT COUNT(*) FROM cards WHERE deck_id = ? AND suspended = 0", deckId)
        val due = count("SELECT COUNT(*) FROM cards WHERE deck_id = ? AND suspended = 0 AND state != 0 AND due <= ?", deckId, now)
        val newCards = count("SELECT COUNT(*) FROM cards WHERE deck_id = ? AND suspended = 0 AND state = 0", deckId)
        val learned = count("SELECT COUNT(*) FROM cards WHERE deck_id = ? AND suspended = 0 AND state = 2", deckId)
        val reviews = count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND is_practice = 0", deckId, monthAgo)
        val correct = count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND was_correct = 1 AND is_practice = 0", deckId, monthAgo)
        val wrong = reviews - correct
        val hintAssisted = count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND hint_count > 0 AND is_practice = 0", deckId, monthAgo)
        val weak = count("SELECT COUNT(*) FROM cards WHERE deck_id = ? AND suspended = 0 AND state != 0 AND weak_count > 0", deckId)
        val studiedToday = count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND is_practice = 0", deckId, dayStart)

        var retentionSum = 0.0
        var retentionCount = 0
        db.readableDatabase.rawQuery(
            "SELECT stability, last_review FROM cards WHERE deck_id = ? AND suspended = 0 AND state = 2 AND stability > 0",
            arrayOf(deckId.toString())
        ).use { c ->
            while (c.moveToNext()) {
                retentionSum += ReviewScheduler.currentRetention(
                    Card(deckId = deckId, front = "", back = "", stability = c.getDouble(0), lastReview = c.getLong(1)), now
                )
                retentionCount++
            }
        }

        return DeckStats(
            deckId = deckId,
            total = total,
            due = due,
            newCards = newCards,
            learned = learned,
            reviews = reviews,
            correct = correct,
            wrong = wrong,
            hintAssisted = hintAssisted,
            weak = weak,
            retention = if (retentionCount == 0) 0.0 else retentionSum / retentionCount,
            accuracy = if (reviews == 0) 0.0 else correct.toDouble() / reviews,
            studiedToday = studiedToday
        )
    }

    @Synchronized
    fun overallStats(now: Long = System.currentTimeMillis()): OverallStats {
        val dayStart = startOfDay(now)
        val monthAgo = now - 30L * ReviewScheduler.DAY_MS
        val total = count("SELECT COUNT(*) FROM cards WHERE suspended = 0")
        val due = count("SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state != 0 AND due <= ?", now)
        val newCards = count("SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state = 0")
        val learned = count("SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state = 2")
        val reviews = count("SELECT COUNT(*) FROM reviews WHERE reviewed_at >= ? AND is_practice = 0", monthAgo)
        val correct = count("SELECT COUNT(*) FROM reviews WHERE reviewed_at >= ? AND was_correct = 1 AND is_practice = 0", monthAgo)
        val hintAssisted = count("SELECT COUNT(*) FROM reviews WHERE reviewed_at >= ? AND hint_count > 0 AND is_practice = 0", monthAgo)
        val weak = count("SELECT COUNT(*) FROM cards WHERE suspended = 0 AND state != 0 AND weak_count > 0")
        val studiedToday = count("SELECT COUNT(*) FROM reviews WHERE reviewed_at >= ? AND is_practice = 0", dayStart)

        var retentionSum = 0.0
        var retentionCount = 0
        db.readableDatabase.rawQuery(
            "SELECT stability, last_review FROM cards WHERE suspended = 0 AND state = 2 AND stability > 0", null
        ).use { c ->
            while (c.moveToNext()) {
                retentionSum += ReviewScheduler.currentRetention(
                    Card(deckId = 0, front = "", back = "", stability = c.getDouble(0), lastReview = c.getLong(1)), now
                )
                retentionCount++
            }
        }

        return OverallStats(
            totalCards = total,
            dueToday = due,
            newCards = newCards,
            learned = learned,
            reviews = reviews,
            correct = correct,
            wrong = reviews - correct,
            hintAssisted = hintAssisted,
            weak = weak,
            accuracy = if (reviews == 0) 0.0 else correct.toDouble() / reviews,
            retention = if (retentionCount == 0) 0.0 else retentionSum / retentionCount,
            streak = streakDays(now),
            studiedToday = studiedToday
        )
    }

    @Synchronized
    fun streakDays(now: Long): Int {
        val days = HashSet<Long>()
        db.readableDatabase.rawQuery("SELECT reviewed_at FROM reviews WHERE is_practice = 0 ORDER BY reviewed_at DESC LIMIT 5000", null).use { c ->
            while (c.moveToNext()) days.add(startOfDay(c.getLong(0)))
        }
        if (days.isEmpty()) return 0
        var cursor = startOfDay(now)
        if (!days.contains(cursor)) cursor -= ReviewScheduler.DAY_MS
        var streak = 0
        while (days.contains(cursor)) {
            streak++
            cursor -= ReviewScheduler.DAY_MS
        }
        return streak
    }

    @Synchronized
    fun activity(days: Int, now: Long = System.currentTimeMillis()): List<DayCount> {
        val today = startOfDay(now)
        val counts = HashMap<Long, Int>()
        db.readableDatabase.rawQuery(
            "SELECT reviewed_at FROM reviews WHERE reviewed_at >= ? AND is_practice = 0", arrayOf((today - (days - 1) * ReviewScheduler.DAY_MS).toString())
        ).use { c ->
            while (c.moveToNext()) {
                val key = startOfDay(c.getLong(0))
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return (days - 1 downTo 0).map { offset ->
            val day = today - offset * ReviewScheduler.DAY_MS
            DayCount(day, counts[day] ?: 0)
        }
    }

    @Synchronized
    fun cardsForReview(deckId: Long): List<Card> = cards(deckId).filter { !it.suspended }

    // ------------------------------------------------------------------
    // Sessions (one question at a time, survives notification updates)
    // ------------------------------------------------------------------

    @Synchronized
    fun createSession(cardId: Long, deckId: Long, kind: String, notificationId: Int): Long =
        db.writableDatabase.insert("sessions", null, ContentValues().apply {
            put("card_id", cardId)
            put("deck_id", deckId)
            put("kind", kind)
            put("hint_count", 0)
            put("answered", 0)
            put("correct", 0)
            put("created_at", System.currentTimeMillis())
            put("answered_at", 0L)
            put("notification_id", notificationId)
        })

    @Synchronized
    fun pendingSession(): ReviewSession? {
        db.readableDatabase.rawQuery(
            "SELECT * FROM sessions WHERE answered = 0 ORDER BY created_at DESC LIMIT 1", null
        ).use { c ->
            return if (c.moveToFirst()) {
                ReviewSession(
                    id = c.lng("id"),
                    cardId = c.lng("card_id"),
                    deckId = c.lng("deck_id"),
                    kind = c.str("kind"),
                    hintCount = c.int("hint_count"),
                    answered = c.bool("answered"),
                    correct = c.bool("correct"),
                    createdAt = c.lng("created_at"),
                    answeredAt = c.lng("answered_at"),
                    notificationId = c.int("notification_id")
                )
            } else {
                null
            }
        }
    }

    @Synchronized
    fun updateSession(session: ReviewSession) {
        val values = ContentValues().apply {
            put("hint_count", session.hintCount)
            put("answered", if (session.answered) 1 else 0)
            put("correct", if (session.correct) 1 else 0)
            put("answered_at", session.answeredAt)
        }
        db.writableDatabase.update("sessions", values, "id = ?", arrayOf(session.id.toString()))
    }

    @Synchronized
    fun closeSessions() {
        db.writableDatabase.delete("sessions", "answered = 1", null)
    }

    @Synchronized
    fun notificationsToday(dayStart: Long): Int =
        count("SELECT COUNT(*) FROM notifications WHERE at >= ?", dayStart)

    /** How many *new* cards of this deck were introduced today (drives the per-deck new-card cap). */
    @Synchronized
    fun newStudiedToday(deckId: Long, dayStart: Long): Int =
        count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND was_new = 1 AND is_practice = 0", deckId, dayStart)

    @Synchronized
    fun studiedToday(deckId: Long, dayStart: Long): Int =
        count("SELECT COUNT(*) FROM reviews WHERE deck_id = ? AND reviewed_at >= ? AND is_practice = 0", deckId, dayStart)

    @Synchronized
    fun answersToday(dayStart: Long): Int =
        count("SELECT COUNT(*) FROM reviews WHERE reviewed_at >= ?", dayStart)

    @Synchronized
    fun session(id: Long): ReviewSession? {
        db.readableDatabase.rawQuery("SELECT * FROM sessions WHERE id = ?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) {
                ReviewSession(
                    id = c.lng("id"),
                    cardId = c.lng("card_id"),
                    deckId = c.lng("deck_id"),
                    kind = c.str("kind"),
                    hintCount = c.int("hint_count"),
                    answered = c.bool("answered"),
                    correct = c.bool("correct"),
                    createdAt = c.lng("created_at"),
                    answeredAt = c.lng("answered_at"),
                    notificationId = c.int("notification_id")
                )
            } else {
                null
            }
        }
    }

    @Synchronized
    fun logNotification(cardId: Long, kind: String, at: Long = System.currentTimeMillis()) {
        db.writableDatabase.insert("notifications", null, ContentValues().apply {
            put("card_id", cardId)
            put("kind", kind)
            put("at", at)
        })
    }

    // ------------------------------------------------------------------
    // Import helpers
    // ------------------------------------------------------------------

    @Synchronized
    fun findDuplicate(deckId: Long, front: String): Card? = db.readableDatabase.rawQuery(
        "SELECT * FROM cards WHERE deck_id = ? AND lower(trim(front)) = ? LIMIT 1",
        arrayOf(deckId.toString(), front.trim().lowercase(Locale.ROOT))
    ).use { if (it.moveToFirst()) mapCard(it) else null }

    @Synchronized
    fun countCards(): Int = count("SELECT COUNT(*) FROM cards")

    @Synchronized
    fun countCards(deckId: Long): Int = count("SELECT COUNT(*) FROM cards WHERE deck_id = ?", deckId)

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun count(sql: String, vararg args: Any?): Int =
        db.readableDatabase.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }

    private fun mapDeck(c: Cursor) = Deck(
        id = c.lng("id"),
        name = c.str("name"),
        description = c.strOr("", "description"),
        category = c.strOr("", "category"),
        isActive = c.bool("is_active"),
        colorIndex = c.int("color_index"),
        dailyLimit = c.int("daily_limit"),
        newPerDay = c.int("new_per_day"),
        notificationsEnabled = c.bool("notifications_enabled"),
        archived = c.bool("archived"),
        sortOrder = c.int("sort_order"),
        createdAt = c.lng("created_at")
    )

    private fun mapCard(c: Cursor) = Card(
        id = c.lng("id"),
        deckId = c.lng("deck_id"),
        front = c.str("front"),
        back = c.str("back"),
        altAnswers = c.strOr("", "alt_answers"),
        hint1 = c.strOr("", "hint1"),
        hint2 = c.strOr("", "hint2"),
        explanation = c.strOr("", "explanation"),
        type = CardType.from(c.strOr("VOCABULARY", "type")),
        tags = c.strOr("", "tags"),
        state = c.int("state"),
        due = c.lng("due"),
        stability = c.dbl("stability"),
        difficulty = c.dbl("difficulty"),
        reps = c.int("reps"),
        lapses = c.int("lapses"),
        lastReview = c.lng("last_review"),
        createdAt = c.lng("created_at"),
        suspended = c.bool("suspended"),
        weakCount = c.int("weak_count")
    )

    companion object {
        @Volatile
        private var instance: Repository? = null

        fun get(context: Context): Repository {
            return instance ?: synchronized(this) {
                instance ?: Repository(context.applicationContext).also { instance = it }
            }
        }

        /** Separate instance on its own database file, used by the diagnostic self-check. */
        fun scratch(context: Context, dbName: String): Repository = Repository(context.applicationContext, dbName)

        fun startOfDay(millis: Long): Long {
            val cal = Calendar.getInstance()
            cal.timeInMillis = millis
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun daysBetween(from: Long, to: Long): Int = max(0, ((to - from) / ReviewScheduler.DAY_MS).toInt())
    }

    fun close() {
        db.close()
    }
}
