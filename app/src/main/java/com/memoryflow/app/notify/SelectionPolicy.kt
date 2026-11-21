package com.memoryflow.app.notify

import com.memoryflow.app.data.Deck

/**
 * Pure decision logic for "should a question be posted right now?".
 * Kept free of Android APIs so every branch is unit tested on the JVM.
 */
object SelectionPolicy {

    const val KIND_SCHEDULED = "SCHEDULED"
    const val KIND_NEW = "NEW"
    const val KIND_RANDOM = "RANDOM"

    fun isDeckEligible(deck: Deck): Boolean =
        deck.isActive && deck.notificationsEnabled && !deck.archived

    fun canReviewMore(deck: Deck, reviewedToday: Int): Boolean = reviewedToday < deck.dailyLimit

    fun canIntroduceNew(deck: Deck, newToday: Int, newTodayAllDecks: Int, globalCap: Int): Boolean =
        newToday < deck.newPerDay && newTodayAllDecks < globalCap

    fun withinWindow(hour: Int, start: Int, end: Int): Boolean {
        if (start == end) return false
        return if (start < end) hour in start until end else hour >= start || hour < end
    }

    fun minGapMinutes(frequency: Int): Int = when (frequency) {
        0 -> 60
        2 -> 15
        else -> 30
    }

    /**
     * All gates in one place. Any false stops a notification, which is what protects the user
     * from notification spam and from posting outside the allowed hours.
     */
    fun shouldPost(
        lastNotificationAt: Long,
        now: Long,
        notificationsToday: Int,
        dailyNotificationLimit: Int,
        answersToday: Int,
        maxQuestionsPerDay: Int,
        frequency: Int,
        quietNow: Boolean,
        inWindow: Boolean,
        notificationsEnabled: Boolean,
        learningMode: Boolean,
        activeDeckCount: Int
    ): Boolean {
        if (!learningMode || !notificationsEnabled) return false
        if (activeDeckCount == 0) return false
        if (quietNow || !inWindow) return false
        if (notificationsToday >= dailyNotificationLimit) return false
        if (answersToday >= maxQuestionsPerDay) return false
        val gapMs = minGapMinutes(frequency) * 60_000L
        if (lastNotificationAt > 0 && now - lastNotificationAt < gapMs) return false
        return true
    }

    /** Cards that are due but belong to a deck that already hit its own daily limit are skipped. */
    fun filterDueByDeckLimit(
        dueCards: List<com.memoryflow.app.data.Card>,
        decks: Map<Long, Deck>,
        reviewedToday: Map<Long, Int>
    ): List<com.memoryflow.app.data.Card> = dueCards.filter { card ->
        val deck = decks[card.deckId] ?: return@filter false
        isDeckEligible(deck) && canReviewMore(deck, reviewedToday[card.deckId] ?: 0)
    }
}
