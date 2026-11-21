package com.memoryflow.app

import com.memoryflow.app.data.Deck
import com.memoryflow.app.notify.SelectionPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionPolicyTest {

    private val deck = Deck(id = 1, name = "German A2", isActive = true, dailyLimit = 5, newPerDay = 10)
    private val now = 1_700_000_000_000L

    private fun post(
        learningMode: Boolean = true,
        notificationsEnabled: Boolean = true,
        quietNow: Boolean = false,
        inWindow: Boolean = true,
        notificationsToday: Int = 0,
        dailyLimit: Int = 10,
        answersToday: Int = 0,
        maxQuestions: Int = 30,
        lastNotifiedAt: Long = 0L,
        activeDecks: Int = 1,
        at: Long = now
    ) = SelectionPolicy.shouldPost(
        lastNotificationAt = lastNotifiedAt,
        now = at,
        notificationsToday = notificationsToday,
        dailyNotificationLimit = dailyLimit,
        answersToday = answersToday,
        maxQuestionsPerDay = maxQuestions,
        frequency = 1,
        quietNow = quietNow,
        inWindow = inWindow,
        notificationsEnabled = notificationsEnabled,
        learningMode = learningMode,
        activeDeckCount = activeDecks
    )

    @Test
    fun `a deck is eligible only when active, not archived and notifications on`() {
        assertTrue(SelectionPolicy.isDeckEligible(deck))
        assertFalse(SelectionPolicy.isDeckEligible(deck.copy(isActive = false)))
        assertFalse(SelectionPolicy.isDeckEligible(deck.copy(archived = true)))
        assertFalse(SelectionPolicy.isDeckEligible(deck.copy(notificationsEnabled = false)))
    }

    @Test
    fun `daily limit is exclusive`() {
        assertTrue(SelectionPolicy.canReviewMore(deck, 4))
        assertFalse(SelectionPolicy.canReviewMore(deck, 5))
        assertFalse(SelectionPolicy.canReviewMore(deck, 9))
    }

    @Test
    fun `new cards respect the deck cap and the global cap`() {
        assertTrue(SelectionPolicy.canIntroduceNew(deck, newToday = 9, newTodayAllDecks = 0, globalCap = 10))
        assertFalse(SelectionPolicy.canIntroduceNew(deck, newToday = 10, newTodayAllDecks = 0, globalCap = 10))
        assertFalse(SelectionPolicy.canIntroduceNew(deck, newToday = 0, newTodayAllDecks = 10, globalCap = 10))
    }

    @Test
    fun `quiet hours crossing midnight work`() {
        assertTrue(SelectionPolicy.withinWindow(23, 22, 7))
        assertTrue(SelectionPolicy.withinWindow(2, 22, 7))
        assertFalse(SelectionPolicy.withinWindow(12, 22, 7))
        assertFalse(SelectionPolicy.withinWindow(22, 8, 22))
        assertTrue(SelectionPolicy.withinWindow(8, 8, 22))
        assertTrue(SelectionPolicy.withinWindow(21, 8, 22))
    }

    @Test
    fun `nothing is posted while learning mode is off`() {
        assertFalse(post(learningMode = false))
        assertFalse(post(notificationsEnabled = false))
        assertFalse(post(activeDecks = 0))
    }

    @Test
    fun `quiet hours and the allowed window block notifications`() {
        assertFalse(post(quietNow = true))
        assertFalse(post(inWindow = false))
    }

    @Test
    fun `daily notification and question caps block notifications`() {
        assertFalse(post(notificationsToday = 10, dailyLimit = 10))
        assertFalse(post(answersToday = 30, maxQuestions = 30))
        assertTrue(post(notificationsToday = 9, dailyLimit = 10, answersToday = 29, maxQuestions = 30))
    }

    @Test
    fun `a minimum gap is enforced between notifications`() {
        val justNow = now - 60_000L
        assertFalse(post(lastNotifiedAt = justNow))
        assertTrue(post(lastNotifiedAt = now - 31 * 60_000L))
    }
}
