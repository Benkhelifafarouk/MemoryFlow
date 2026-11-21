package com.memoryflow.app.data

/** The kind of knowledge a card holds. Stored by name, so new types can be added safely. */
enum class CardType {
    VOCABULARY,
    QA,
    DEFINITION,
    MULTI_POINT,
    SHORT,
    LONG,
    TRANSLATION,
    FORMULA,
    CUSTOM;

    companion object {
        fun from(value: String?): CardType =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VOCABULARY
    }
}

/** FSRS learning state of a card. */
object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
}

data class Deck(
    val id: Long = 0L,
    val name: String,
    val description: String = "",
    val category: String = "",
    val isActive: Boolean = false,
    val colorIndex: Int = 0,
    val dailyLimit: Int = 20,
    val newPerDay: Int = 10,
    val notificationsEnabled: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L
)

data class Card(
    val id: Long = 0L,
    val deckId: Long,
    val front: String,
    val back: String,
    val altAnswers: String = "",
    val hint1: String = "",
    val hint2: String = "",
    val explanation: String = "",
    val type: CardType = CardType.VOCABULARY,
    val tags: String = "",
    val state: Int = CardState.NEW,
    val due: Long = 0L,
    val stability: Double = 0.0,
    val difficulty: Double = 0.0,
    val reps: Int = 0,
    val lapses: Int = 0,
    val lastReview: Long = 0L,
    val createdAt: Long = 0L,
    val suspended: Boolean = false,
    /** Times the user failed or pressed "I don't know". > 0 marks a weak word. */
    val weakCount: Int = 0
) {
    val altList: List<String>
        get() = altAnswers.split('|').map { it.trim() }.filter { it.isNotEmpty() }

    val hasHints: Boolean get() = hint1.isNotBlank() || hint2.isNotBlank()
    val hintCount: Int get() = (if (hint1.isNotBlank()) 1 else 0) + (if (hint2.isNotBlank()) 1 else 0)
}

data class ReviewLog(
    val id: Long = 0L,
    val cardId: Long,
    val deckId: Long,
    val rating: Int,
    val answerText: String = "",
    val wasCorrect: Boolean = false,
    val hintCount: Int = 0,
    val reviewedAt: Long = 0L,
    val elapsedDays: Double = 0.0,
    val scheduledDays: Int = 0,
    val stabilityAfter: Double = 0.0,
    val difficultyAfter: Double = 0.0,
    val wasNew: Boolean = false,
    val isPractice: Boolean = false
)

data class ReviewSession(
    val id: Long = 0L,
    val cardId: Long,
    val deckId: Long,
    val kind: String,
    val hintCount: Int = 0,
    val answered: Boolean = false,
    val correct: Boolean = false,
    val createdAt: Long = 0L,
    val answeredAt: Long = 0L,
    val notificationId: Int = 0
)

data class DeckStats(
    val deckId: Long = 0L,
    val total: Int = 0,
    val due: Int = 0,
    val newCards: Int = 0,
    val learned: Int = 0,
    val reviews: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val hintAssisted: Int = 0,
    val weak: Int = 0,
    val retention: Double = 0.0,
    val accuracy: Double = 0.0,
    val studiedToday: Int = 0
)

data class OverallStats(
    val totalCards: Int = 0,
    val dueToday: Int = 0,
    val newCards: Int = 0,
    val learned: Int = 0,
    val reviews: Int = 0,
    val correct: Int = 0,
    val wrong: Int = 0,
    val hintAssisted: Int = 0,
    val weak: Int = 0,
    val accuracy: Double = 0.0,
    val retention: Double = 0.0,
    val streak: Int = 0,
    val studiedToday: Int = 0
)

data class DayCount(val dayStart: Long, val count: Int)
