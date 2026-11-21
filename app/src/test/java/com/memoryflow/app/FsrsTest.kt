package com.memoryflow.app

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState
import com.memoryflow.app.fsrs.Fsrs
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.fsrs.ReviewScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numeric assertions below are the official FSRS-5 hand-computed examples
 * (S=5, D=5, R=0.9, Grade=3 -> 17.46568510), so a formula regression cannot pass silently.
 */
class FsrsTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun newCard() = Card(deckId = 1, front = "die Herausforderung", back = "التحدي", due = now, createdAt = now)

    @Test
    fun `default weights are the FSRS-5 set`() {
        assertEquals(19, Fsrs.DEFAULT_WEIGHTS.size)
        assertEquals(0.40255, Fsrs.DEFAULT_WEIGHTS[0], 1e-9)
        assertEquals(15.69105, Fsrs.DEFAULT_WEIGHTS[3], 1e-9)
        assertEquals(0.6621, Fsrs.DEFAULT_WEIGHTS[18], 1e-9)
    }

    @Test
    fun `example 1 - first review rated Good`() {
        assertEquals(3.173, Fsrs.initStability(Grades.GOOD), 1e-9)
        assertEquals(5.28243442, Fsrs.initDifficulty(Grades.GOOD), 1e-6)
        assertEquals(3, Fsrs.nextIntervalDays(3.173, 0.9))
    }

    @Test
    fun `example 2 - recall with S=5 D=5 R=0_9 Grade=3`() {
        assertEquals(17.46568510, Fsrs.nextRecallStability(5.0, 5.0, 0.9, Grades.GOOD), 1e-5)
        assertEquals(4.99183271, Fsrs.nextDifficulty(5.0, Grades.GOOD), 1e-5)
        assertEquals(17, Fsrs.nextIntervalDays(17.46568510, 0.9))
    }

    @Test
    fun `example 3 - hard recall applies the w15 penalty`() {
        assertEquals(30.30998530, Fsrs.nextRecallStability(8.0, 20.0, 0.8, Grades.HARD), 1e-5)
        assertEquals(8.30107319, Fsrs.nextDifficulty(8.0, Grades.HARD), 1e-5)
        assertEquals(30, Fsrs.nextIntervalDays(30.30998530, 0.9))
    }

    @Test
    fun `example 4 - forgetting with S=5 D=5 R=0_9`() {
        assertEquals(1.42655877, Fsrs.nextForgetStability(5.0, 5.0, 0.9), 1e-5)
        assertEquals(6.60703511, Fsrs.nextDifficulty(5.0, Grades.AGAIN), 1e-5)
        assertEquals(1, Fsrs.nextIntervalDays(1.42655877, 0.9))
    }

    @Test
    fun `example 5 - same day review uses the short term formula`() {
        assertEquals(4.71950979, Fsrs.shortTermStability(2.0, Grades.EASY), 1e-5)
    }

    @Test
    fun `initial stability grows with the grade`() {
        assertTrue(Fsrs.initStability(Grades.AGAIN) < Fsrs.initStability(Grades.HARD))
        assertTrue(Fsrs.initStability(Grades.HARD) < Fsrs.initStability(Grades.GOOD))
        assertTrue(Fsrs.initStability(Grades.GOOD) < Fsrs.initStability(Grades.EASY))
    }

    @Test
    fun `initial difficulty falls as the grade rises`() {
        assertTrue(Fsrs.initDifficulty(Grades.AGAIN) > Fsrs.initDifficulty(Grades.GOOD))
        assertTrue(Fsrs.initDifficulty(Grades.GOOD) > Fsrs.initDifficulty(Grades.EASY))
        assertEquals(7.1949, Fsrs.initDifficulty(Grades.AGAIN), 1e-9)
        assertEquals(3.22450159, Fsrs.initDifficulty(Grades.EASY), 1e-5)
    }

    @Test
    fun `retrievability is one at zero elapsed days and decays with time`() {
        assertEquals(1.0, Fsrs.retrievability(0.0, 10.0), 0.0001)
        val day1 = Fsrs.retrievability(1.0, 10.0)
        val day30 = Fsrs.retrievability(30.0, 10.0)
        assertTrue(day1 < 1.0)
        assertTrue(day30 < day1)
        assertTrue(day30 > 0.0)
    }

    @Test
    fun `retrievability at the stability is about ninety percent`() {
        assertEquals(0.9, Fsrs.retrievability(10.0, 10.0), 0.001)
    }

    @Test
    fun `interval inverts the forgetting curve`() {
        assertEquals(10, Fsrs.nextIntervalDays(10.0, 0.9))
        assertTrue(Fsrs.nextIntervalDays(10.0, 0.8) > Fsrs.nextIntervalDays(10.0, 0.95))
        assertTrue(Fsrs.nextIntervalDays(0.001, 0.9) >= 1)
    }

    @Test
    fun `difficulty increases after a lapse and decreases after easy recall`() {
        val base = 5.0
        assertTrue(Fsrs.nextDifficulty(base, Grades.AGAIN) > base)
        assertTrue(Fsrs.nextDifficulty(base, Grades.EASY) < base)
    }

    @Test
    fun `easy recall grows stability more than hard`() {
        val s = 10.0
        val d = 5.0
        val r = 0.9
        val hard = Fsrs.nextRecallStability(d, s, r, Grades.HARD)
        val good = Fsrs.nextRecallStability(d, s, r, Grades.GOOD)
        val easy = Fsrs.nextRecallStability(d, s, r, Grades.EASY)
        assertTrue(good > s)
        assertTrue(easy > good)
        assertTrue(good > hard)
    }

    @Test
    fun `forgetting is capped at 71 percent of the previous stability`() {
        val s = 100.0
        val forgotten = Fsrs.nextForgetStability(2.0, s, 0.9)
        assertTrue(forgotten < s)
        assertTrue(forgotten <= s / Math.exp(Fsrs.DEFAULT_WEIGHTS[17] * Fsrs.DEFAULT_WEIGHTS[18]) + 1e-9)
        assertTrue(forgotten > 0.0)
    }

    @Test
    fun `first review schedules the card in the future`() {
        val outcome = ReviewScheduler.review(newCard(), Grades.GOOD, now, 0.9)
        assertEquals(CardState.REVIEW, outcome.state)
        assertEquals(1, outcome.reps)
        assertEquals(3, outcome.intervalDays)
        assertTrue(outcome.due > now)
        assertTrue(outcome.correct)
    }

    @Test
    fun `a lapse schedules the card for tomorrow and increases lapses`() {
        val reviewed = newCard().copy(
            state = CardState.REVIEW,
            stability = 30.0,
            difficulty = 5.0,
            reps = 4,
            lastReview = now - 20 * day
        )
        val outcome = ReviewScheduler.review(reviewed, Grades.AGAIN, now, 0.9)
        assertEquals(1, outcome.intervalDays)
        assertEquals(CardState.RELEARNING, outcome.state)
        assertEquals(5, outcome.reps)
        assertEquals(1, outcome.lapses)
        assertTrue(outcome.stability <= 30.0)
    }

    @Test
    fun `good recall on a due card pushes the due date further`() {
        val reviewed = newCard().copy(
            state = CardState.REVIEW,
            stability = 5.0,
            difficulty = 5.0,
            reps = 2,
            lastReview = now - 5 * day
        )
        val outcome = ReviewScheduler.review(reviewed, Grades.GOOD, now, 0.9)
        assertTrue(outcome.stability > 5.0)
        assertTrue(outcome.due > now + day)
    }

    @Test
    fun `a same day review never uses the long interval formula`() {
        val reviewed = newCard().copy(
            state = CardState.REVIEW,
            stability = 10.0,
            difficulty = 5.0,
            reps = 2,
            lastReview = now - 60_000L
        )
        val outcome = ReviewScheduler.review(reviewed, Grades.EASY, now, 0.9)
        // Short-term branch: 10 * exp(0.51655 * (4 - 3 + 0.6621)) = 23.5975
        assertEquals(23.59755, outcome.stability, 1e-3)
        assertEquals(0.0, outcome.elapsedDays, 1e-9)
    }

    @Test
    fun `grades decrease when hints are used`() {
        val noHint = com.memoryflow.app.review.AnswerEvaluator.gradeFor(1.0, 0)
        val oneHint = com.memoryflow.app.review.AnswerEvaluator.gradeFor(1.0, 1)
        val twoHints = com.memoryflow.app.review.AnswerEvaluator.gradeFor(1.0, 2)
        assertEquals(Grades.EASY, noHint)
        assertEquals(Grades.GOOD, oneHint)
        assertEquals(Grades.HARD, twoHints)
    }
}
