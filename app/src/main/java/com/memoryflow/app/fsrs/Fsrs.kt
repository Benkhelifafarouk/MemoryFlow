package com.memoryflow.app.fsrs

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Genuine FSRS-5 implementation (Free Spaced Repetition Scheduler, v5).
 *
 * Equations follow the reference implementation used by py-fsrs / ts-fsrs:
 *  - R(t, S)          = (1 + FACTOR * t / S) ^ DECAY
 *  - S0(G)            = max(w[G-1], 0.1)
 *  - D0(G)            = w[4] - e^(w[5] * (G - 1)) + 1        (clamped to 1..10)
 *  - I(S, retention)  = S / FACTOR * (retention ^ (1/DECAY) - 1)
 *
 * FACTOR is derived, not guessed: FACTOR = 0.9^(1/DECAY) - 1 = 19/81 for DECAY = -0.5.
 */
object Fsrs {

    const val VERSION = "FSRS-5"

    /** FSRS-5 default parameters (19 weights, w0..w18). Verified against ts-fsrs / py-fsrs v4.1.2. */
    val DEFAULT_WEIGHTS: DoubleArray = doubleArrayOf(
        0.40255,  // w0  initial stability for Again
        1.18385,  // w1  initial stability for Hard
        3.173,    // w2  initial stability for Good
        15.69105, // w3  initial stability for Easy
        7.1949,   // w4  initial difficulty base (also mean-reversion target)
        0.5345,   // w5  initial difficulty exponent
        1.4604,   // w6  difficulty change rate
        0.0046,   // w7  difficulty mean reversion strength
        1.54575,  // w8  stability growth exponent
        0.1192,   // w9  stability saturation by difficulty
        1.01925,  // w10 retrievability sensitivity
        1.9395,   // w11 forgetting stability base
        0.11,     // w12 forgetting stability decay by difficulty
        0.29605,  // w13 forgetting stability growth with S
        2.2698,   // w14 forgetting stability retrievability weight
        0.2315,   // w15 Hard penalty
        2.9898,   // w16 Easy bonus
        0.51655,  // w17 same-day (short-term) stability base
        0.6621    // w18 same-day stability rating offset
    )

    private const val DECAY = -0.5
    private const val FACTOR = 19.0 / 81.0
    private const val MIN_STABILITY = 0.01
    private const val MAX_STABILITY = 36500.0

    fun initStability(grade: Int): Double = max(DEFAULT_WEIGHTS[grade.coerceIn(1, 4) - 1], 0.1)

    fun initDifficulty(grade: Int): Double {
        val g = grade.coerceIn(1, 4)
        return clamp(DEFAULT_WEIGHTS[4] - exp(DEFAULT_WEIGHTS[5] * (g - 1)) + 1.0, 1.0, 10.0)
    }

    /** Probability that the card is still remembered after [elapsedDays]. */
    fun retrievability(elapsedDays: Double, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val t = max(elapsedDays, 0.0)
        return (1.0 + FACTOR * t / stability).pow(DECAY).coerceIn(0.0, 1.0)
    }

    /** Days until retrievability drops to [desiredRetention] (0.9 by default). */
    fun nextIntervalDays(stability: Double, desiredRetention: Double): Int {
        val r = desiredRetention.coerceIn(0.70, 0.97)
        val s = stability.coerceAtLeast(MIN_STABILITY)
        val days = s / FACTOR * (r.pow(1.0 / DECAY) - 1.0)
        if (days.isNaN() || days.isInfinite()) return 1
        return days.roundToInt().coerceIn(1, MAX_STABILITY.toInt())
    }

    fun nextDifficulty(difficulty: Double, grade: Int): Double {
        val g = grade.coerceIn(1, 4)
        val delta = -DEFAULT_WEIGHTS[6] * (g - 3)
        val next = difficulty + delta * (10.0 - difficulty) / 9.0
        val reverted = DEFAULT_WEIGHTS[7] * initDifficulty(4) + (1.0 - DEFAULT_WEIGHTS[7]) * next
        return clamp(reverted, 1.0, 10.0)
    }

    /** Stability after a successful review at [retrievability]. */
    fun nextRecallStability(difficulty: Double, stability: Double, retrievability: Double, grade: Int): Double {
        val hardPenalty = if (grade == 2) DEFAULT_WEIGHTS[15] else 1.0
        val easyBonus = if (grade == 4) DEFAULT_WEIGHTS[16] else 1.0
        val s = stability.coerceAtLeast(MIN_STABILITY)
        val grow = 1.0 + exp(DEFAULT_WEIGHTS[8]) *
            (11.0 - difficulty) *
            s.pow(-DEFAULT_WEIGHTS[9]) *
            (exp((1.0 - retrievability) * DEFAULT_WEIGHTS[10]) - 1.0) *
            hardPenalty * easyBonus
        return (s * grow).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    /**
     * Stability after a lapse (forgetting). The result is capped so that a lapse can never leave
     * more stability than [stability] / e^(w17*w18): with FSRS-5 defaults that is ~71% of the old
     * value. This cap is what keeps "Again" from inflating a strong card.
     */
    fun nextForgetStability(difficulty: Double, stability: Double, retrievability: Double): Double {
        val s = stability.coerceAtLeast(MIN_STABILITY)
        val value = DEFAULT_WEIGHTS[11] *
            difficulty.pow(-DEFAULT_WEIGHTS[12]) *
            ((s + 1.0).pow(DEFAULT_WEIGHTS[13]) - 1.0) *
            exp((1.0 - retrievability) * DEFAULT_WEIGHTS[14])
        val cap = s / exp(DEFAULT_WEIGHTS[17] * DEFAULT_WEIGHTS[18])
        return min(value, cap).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    /** Stability for a second review on the same day (elapsed < 0.5 day). */
    fun shortTermStability(stability: Double, grade: Int): Double {
        val s = stability.coerceAtLeast(MIN_STABILITY)
        val factor = exp(DEFAULT_WEIGHTS[17] * (grade.coerceIn(1, 4) - 3 + DEFAULT_WEIGHTS[18]))
        return (s * factor).coerceIn(MIN_STABILITY, MAX_STABILITY)
    }

    fun clamp(value: Double, low: Double, high: Double): Double = when {
        value.isNaN() -> low
        value < low -> low
        value > high -> high
        else -> value
    }
}

object Grades {
    const val AGAIN = 1
    const val HARD = 2
    const val GOOD = 3
    const val EASY = 4
}

data class ReviewOutcome(
    val state: Int,
    val stability: Double,
    val difficulty: Double,
    val intervalDays: Int,
    val due: Long,
    val elapsedDays: Double,
    val reps: Int,
    val lapses: Int,
    val correct: Boolean
)

/**
 * Applies one FSRS review to a card. Pure function: no database, no clock except [nowMillis].
 */
object ReviewScheduler {

    const val DAY_MS = 86_400_000L

    fun review(card: Card, grade: Int, nowMillis: Long, desiredRetention: Double): ReviewOutcome {
        val g = grade.coerceIn(1, 4)
        val elapsedWholeDays = if (card.lastReview <= 0L || nowMillis <= card.lastReview) {
            0L
        } else {
            (nowMillis - card.lastReview) / DAY_MS
        }
        val elapsedDays = elapsedWholeDays.toDouble()
        val isSameDay = elapsedWholeDays == 0L

        val isFirstReview = card.state == CardState.NEW || card.stability <= 0.0
        var stability: Double
        var difficulty: Double
        var state: Int
        var lapses = card.lapses

        if (isFirstReview) {
            stability = Fsrs.initStability(g)
            difficulty = Fsrs.initDifficulty(g)
            state = if (g == Grades.AGAIN) CardState.RELEARNING else CardState.REVIEW
        } else {
            val r = Fsrs.retrievability(elapsedDays, card.stability)
            difficulty = Fsrs.nextDifficulty(card.difficulty, g)
            stability = when {
                // Same-day reviews use the short-term formula for every grade, including Again.
                isSameDay -> Fsrs.shortTermStability(card.stability, g)
                g == Grades.AGAIN -> Fsrs.nextForgetStability(card.difficulty, card.stability, r)
                else -> Fsrs.nextRecallStability(card.difficulty, card.stability, r, g)
            }
            state = if (g == Grades.AGAIN) CardState.RELEARNING else CardState.REVIEW
            if (g == Grades.AGAIN) lapses += 1
        }

        var interval = Fsrs.nextIntervalDays(stability, desiredRetention)
        // A failed card should come back tomorrow rather than later.
        if (g == Grades.AGAIN) interval = 1

        return ReviewOutcome(
            state = state,
            stability = stability,
            difficulty = difficulty,
            intervalDays = interval,
            due = nowMillis + interval * DAY_MS,
            elapsedDays = elapsedDays,
            reps = card.reps + 1,
            lapses = lapses,
            correct = g != Grades.AGAIN
        )
    }

    /** Current estimated probability of recall for a card, used for the retention estimate. */
    fun currentRetention(card: Card, nowMillis: Long): Double {
        if (card.stability <= 0.0) return 0.0
        val elapsed = if (card.lastReview <= 0L) 0.0 else (nowMillis - card.lastReview).toDouble() / DAY_MS
        return Fsrs.retrievability(max(elapsed, 0.0), card.stability)
    }
}
