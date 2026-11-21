package com.memoryflow.app.notify

import android.content.Context
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.Deck
import com.memoryflow.app.data.Repository
import com.memoryflow.app.data.ReviewSession
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.fsrs.ReviewOutcome
import com.memoryflow.app.review.AnswerEvaluator

data class SelectedCard(val card: Card, val deck: Deck, val kind: String)

data class Feedback(
    val correct: Boolean,
    val score: Double,
    val rating: Int,
    val expectedAnswer: String,
    val missingPoints: List<String>,
    val intervalDays: Int,
    val explanation: String = "",
    val thirdHintFailure: Boolean = false,
    val practice: Boolean = false
)

/**
 * Shared learning logic for the notification flow and the in-app review screen.
 * Both paths call [submit] so a grade always produces exactly one database transition.
 */
class ReviewEngine(private val context: Context) {

    private val repo = Repository.get(context)
    private val settings = repo.settings

    fun settingsStore() = settings

    fun activeDecks(): List<Deck> = repo.decks().filter { SelectionPolicy.isDeckEligible(it) }

    /**
     * Picks the next card following the documented priority:
     * due reviews -> new cards (within per-deck and global caps) -> optional random practice.
     */
    fun pickNext(now: Long = System.currentTimeMillis(), exclude: Set<Long> = emptySet()): SelectedCard? {
        if (!settings.learningMode) return null
        val active = activeDecks()
        if (active.isEmpty()) return null

        val dayStart = Repository.startOfDay(now)
        val deckById = active.associateBy { it.id }
        val reviewedToday = active.associate { it.id to repo.studiedToday(it.id, dayStart) }

        val due = repo.dueCards(active.map { it.id }, now, 60)
            .filter { it.id !in exclude }
            .firstOrNull { card ->
                val deck = deckById[card.deckId]
                deck != null && SelectionPolicy.canReviewMore(deck, reviewedToday[card.deckId] ?: 0)
            }
        if (due != null) {
            return SelectedCard(due, deckById.getValue(due.deckId), SelectionPolicy.KIND_SCHEDULED)
        }

        var newTodayAll = active.sumOf { repo.newStudiedToday(it.id, dayStart) }
        for (deck in active.sortedBy { it.sortOrder }) {
            if (!SelectionPolicy.canIntroduceNew(deck, repo.newStudiedToday(deck.id, dayStart), newTodayAll, settings.newPerDayGlobal)) {
                continue
            }
            val candidate = repo.newCards(deck.id, 5).firstOrNull { it.id !in exclude }
            if (candidate != null) {
                newTodayAll += 1
                return SelectedCard(candidate, deck, SelectionPolicy.KIND_NEW)
            }
        }

        if (settings.randomEnabled) {
            val random = repo.randomCards(active.map { it.id }, 8).firstOrNull { it.id !in exclude }
            if (random != null) {
                val deck = deckById[random.deckId] ?: return null
                return SelectedCard(random, deck, SelectionPolicy.KIND_RANDOM)
            }
        }
        return null
    }

    /** Grades one attempt. Random practice is logged but never touches the FSRS schedule. */
    fun submit(session: ReviewSession, answer: String, now: Long = System.currentTimeMillis()): Feedback? {
        val card = repo.card(session.cardId) ?: return null
        val practice = session.kind == SelectionPolicy.KIND_RANDOM
        val evaluation = AnswerEvaluator.evaluate(card, answer)
        val rating = if (evaluation.score < 0.40) Grades.AGAIN
        else AnswerEvaluator.gradeFor(evaluation.score, session.hintCount)

        val outcome = repo.grade(card.id, rating, answer, session.hintCount, practice, now) ?: return null
        markAnswered(session, outcome.correct, now)

        return Feedback(
            correct = outcome.correct,
            score = evaluation.score,
            rating = rating,
            expectedAnswer = card.back,
            missingPoints = evaluation.missingPoints,
            intervalDays = outcome.intervalDays,
            explanation = card.explanation,
            practice = practice
        )
    }

    /**
     * "I don't know" was pressed. The card fails (Again) and [Repository.grade] raises its
     * weak counter, so the word returns with higher priority in later sessions.
     */
    fun markDontKnow(session: ReviewSession, now: Long = System.currentTimeMillis()): Feedback? {
        val card = repo.card(session.cardId) ?: return null
        val practice = session.kind == SelectionPolicy.KIND_RANDOM
        val outcome = repo.grade(card.id, Grades.AGAIN, "", session.hintCount, practice, now) ?: return null
        repo.updateSession(session.copy(answered = true, correct = false, answeredAt = now))
        return Feedback(
            correct = false,
            score = 0.0,
            rating = Grades.AGAIN,
            expectedAnswer = card.back,
            missingPoints = AnswerEvaluator.splitPoints(card.back),
            intervalDays = outcome.intervalDays,
            explanation = card.explanation,
            practice = practice
        )
    }

    /** Third hint request is an automatic failure, as required by the learning rules. */
    fun thirdHintFailure(session: ReviewSession, now: Long = System.currentTimeMillis()): Feedback? {
        val card = repo.card(session.cardId) ?: return null
        val practice = session.kind == SelectionPolicy.KIND_RANDOM
        val outcome = repo.grade(card.id, Grades.AGAIN, "", 3, practice, now) ?: return null
        markAnswered(session, false, now, hintCount = 3)
        return Feedback(
            correct = false,
            score = 0.0,
            rating = Grades.AGAIN,
            expectedAnswer = card.back,
            missingPoints = AnswerEvaluator.splitPoints(card.back),
            intervalDays = outcome.intervalDays,
            explanation = card.explanation,
            thirdHintFailure = true,
            practice = practice
        )
    }

    fun hintText(card: Card, usedBefore: Int): String = when (usedBefore + 1) {
        1 -> card.hint1
        2 -> card.hint2
        else -> ""
    }

    private fun markAnswered(session: ReviewSession, correct: Boolean, now: Long, hintCount: Int = session.hintCount) {
        repo.updateSession(
            session.copy(answered = true, correct = correct, answeredAt = now, hintCount = hintCount)
        )
    }

    /** Used by the in-app review screen, which keeps its hints in memory. */
    fun rate(cardId: Long, rating: Int, answer: String, hintCount: Int, now: Long = System.currentTimeMillis()): ReviewOutcome? =
        repo.grade(cardId, rating, answer, hintCount, practice = false, now = now)

    fun cardsForReview(deckId: Long, now: Long = System.currentTimeMillis()): List<Card> {
        val deck = repo.deck(deckId) ?: return emptyList()
        return repo.cards(deckId, includeSuspended = false)
            .sortedWith(compareBy<Card> { it.due }.thenBy { it.state })
            .filter { it.state == 0 || it.due <= now || deck.dailyLimit > 0 }
    }
}
