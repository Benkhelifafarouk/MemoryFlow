package com.memoryflow.app.review

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState

/**
 * Builds one review session from a pool of cards. Pure Kotlin so the daily caps
 * and the weak-word priority are unit tested on the JVM.
 *
 * Rules:
 *  - Due cards come first, weak words before the rest.
 *  - New cards are capped per deck and globally, so importing 1,000 terms never
 *    dumps 1,000 new words into one session.
 *  - Weak-only sessions ignore due dates on purpose: drilling words you do not
 *    know is extra practice and must not wait for the schedule.
 */
object SessionBuilder {

    fun build(
        pool: List<Card>,
        deckNewCaps: Map<Long, Int>,
        newStudiedToday: Map<Long, Int>,
        globalNewStudiedToday: Int,
        globalNewLimit: Int,
        now: Long,
        weakOnly: Boolean,
        maxDue: Int = 100,
        maxWeak: Int = 40
    ): List<Card> {
        val eligible = pool.filter { !it.suspended && it.back.isNotBlank() }

        if (weakOnly) {
            return eligible
                .filter { it.weakCount > 0 }
                .sortedWith(compareByDescending<Card> { it.weakCount }.thenBy { it.due })
                .take(maxWeak)
        }

        val due = eligible
            .filter { it.state != CardState.NEW && it.due <= now }
            .sortedWith(compareByDescending<Card> { it.weakCount }.thenBy { it.due })
            .take(maxDue)

        var globalBudget = (globalNewLimit - globalNewStudiedToday).coerceAtLeast(0)
        val newCards = ArrayList<Card>()
        for ((deckId, cap) in deckNewCaps) {
            if (globalBudget <= 0) break
            val alreadyStudied = newStudiedToday[deckId] ?: 0
            val remaining = minOf(cap - alreadyStudied, globalBudget)
            if (remaining <= 0) continue
            val taken = eligible.filter { it.deckId == deckId && it.state == CardState.NEW }.take(remaining)
            newCards.addAll(taken)
            globalBudget -= taken.size
        }

        return due + newCards
    }
}
