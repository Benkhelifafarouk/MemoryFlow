package com.memoryflow.app

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState
import com.memoryflow.app.review.SessionBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionBuilderTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    private fun newCard(id: Long, deckId: Long = 1L) = Card(
        id = id,
        deckId = deckId,
        front = "Wort$id",
        back = "كلمة$id",
        state = CardState.NEW,
        due = now,
        createdAt = now
    )

    private fun dueCard(id: Long, weak: Int = 0, deckId: Long = 1L) = newCard(id, deckId).copy(
        state = CardState.REVIEW,
        due = now - day,
        stability = 10.0,
        difficulty = 5.0,
        lastReview = now - 5 * day,
        weakCount = weak
    )

    @Test
    fun `new cards are capped per deck`() {
        val pool = (1..60).map { newCard(it.toLong()) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = mapOf(1L to 20),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 100,
            now = now,
            weakOnly = false
        )
        assertEquals(20, session.size)
        assertTrue(session.all { it.state == CardState.NEW })
    }

    @Test
    fun `the global new-card cap wins over the deck cap`() {
        val pool = (1..60).map { newCard(it.toLong()) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = mapOf(1L to 50),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 5,
            now = now,
            weakOnly = false
        )
        assertEquals(5, session.size)
    }

    @Test
    fun `cards already studied today reduce the remaining budget`() {
        val pool = (1..60).map { newCard(it.toLong()) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = mapOf(1L to 20),
            newStudiedToday = mapOf(1L to 15),
            globalNewStudiedToday = 15,
            globalNewLimit = 20,
            now = now,
            weakOnly = false
        )
        assertEquals(5, session.size)
    }

    @Test
    fun `no new cards are added when the daily budget is exhausted`() {
        val pool = (1..10).map { newCard(it.toLong()) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = mapOf(1L to 20),
            newStudiedToday = mapOf(1L to 20),
            globalNewStudiedToday = 20,
            globalNewLimit = 20,
            now = now,
            weakOnly = false
        )
        assertEquals(0, session.size)
    }

    @Test
    fun `due cards come first and weak words before the rest`() {
        val pool = (1..5).map { dueCard(it.toLong()) } + (6..8).map { dueCard(it.toLong(), weak = 3) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = emptyMap(),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 0,
            now = now,
            weakOnly = false
        )
        assertEquals(8, session.size)
        assertTrue(session.take(3).all { it.weakCount == 3 })
    }

    @Test
    fun `weak only sessions ignore due dates`() {
        val weakNotDue = dueCard(1L, weak = 2).copy(due = now + 5 * day)
        val weakDue = dueCard(2L, weak = 1)
        val normal = dueCard(3L)
        val session = SessionBuilder.build(
            pool = listOf(weakNotDue, weakDue, normal),
            deckNewCaps = emptyMap(),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 0,
            now = now,
            weakOnly = true
        )
        assertEquals(2, session.size)
        // Sorted by weak_count descending: the card marked weak twice comes first.
        assertEquals(listOf(1L, 2L), session.map { it.id })
    }

    @Test
    fun `drafts and suspended cards never enter a session`() {
        val draft = newCard(1L).copy(back = "")
        val suspended = newCard(2L).copy(suspended = true)
        val good = newCard(3L)
        val session = SessionBuilder.build(
            pool = listOf(draft, suspended, good),
            deckNewCaps = mapOf(1L to 20),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 20,
            now = now,
            weakOnly = false
        )
        assertEquals(1, session.size)
        assertEquals(3L, session[0].id)
    }

    @Test
    fun `two decks are each capped`() {
        val pool = (1..30).map { newCard(it.toLong(), deckId = 1L) } + (31..60).map { newCard(it.toLong(), deckId = 2L) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = mapOf(1L to 10, 2L to 5),
            newStudiedToday = emptyMap(),
            globalNewStudiedToday = 0,
            globalNewLimit = 100,
            now = now,
            weakOnly = false
        )
        assertEquals(15, session.size)
        assertEquals(10, session.count { it.deckId == 1L })
        assertEquals(5, session.count { it.deckId == 2L })
    }

    @Test
    fun `an empty pool yields an empty session`() {
        assertTrue(
            SessionBuilder.build(
                pool = emptyList(),
                deckNewCaps = mapOf(1L to 20),
                newStudiedToday = emptyMap(),
                globalNewStudiedToday = 0,
                globalNewLimit = 20,
                now = now,
                weakOnly = false
            ).isEmpty()
        )
    }
}
