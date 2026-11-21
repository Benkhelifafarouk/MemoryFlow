package com.memoryflow.app.notify

import android.content.Context
import com.memoryflow.app.R
import com.memoryflow.app.data.Repository
import com.memoryflow.app.data.ReviewSession
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.util.LocaleHelper

/**
 * Orchestrates one question at a time: pick -> notify -> grade -> next question.
 * Keeping this in one place is what prevents a second notification from contradicting the first.
 */
class NotificationFlow(context: Context) {

    private val repo = Repository.get(context)
    private val engine = ReviewEngine(context)
    private val settings = repo.settings
    private val appContext = context.applicationContext

    private fun localized(): Context = LocaleHelper.wrap(appContext, settings.language)

    private fun presenter(): NotificationPresenter = NotificationPresenter(localized())

    /** Proactive notification (called by the worker). Returns true when a question was posted. */
    fun postProactive(now: Long = System.currentTimeMillis()): Boolean {
        val active = repo.decks().filter { SelectionPolicy.isDeckEligible(it) }
        val dayStart = Repository.startOfDay(now)
        val allowed = SelectionPolicy.shouldPost(
            lastNotificationAt = settings.lastNotificationAt,
            now = now,
            notificationsToday = repo.notificationsToday(dayStart),
            dailyNotificationLimit = settings.dailyNotificationLimit,
            answersToday = repo.answersToday(dayStart),
            maxQuestionsPerDay = settings.maxQuestionsPerDay,
            frequency = settings.frequency,
            quietNow = settings.isQuietNow(),
            inWindow = settings.isInWindowNow(),
            notificationsEnabled = settings.notificationsEnabled,
            learningMode = settings.learningMode,
            activeDeckCount = active.size
        )
        if (!allowed) return false

        val selected = engine.pickNext(now) ?: return false
        val sessionId = repo.createSession(
            cardId = selected.card.id,
            deckId = selected.deck.id,
            kind = selected.kind,
            notificationId = NotificationPresenter.NOTIFICATION_ID
        )
        repo.logNotification(selected.card.id, selected.kind, now)
        settings.lastNotificationAt = now

        presenter().showQuestion(
            deckName = selected.deck.name,
            question = selected.card.front,
            hint = null,
            hintsUsed = 0,
            sessionId = sessionId,
            progressLine = progressLine(selected.deck.id)
        )
        return true
    }

    /** Reply typed directly inside the notification. */
    fun onReply(sessionId: Long, answer: String, now: Long = System.currentTimeMillis()) {
        val session = repo.session(sessionId) ?: return
        val ctx = localized()
        if (session.answered) {
            presenter().showTemporary(
                ctx.getString(R.string.notif_title, repo.deck(session.deckId)?.name.orEmpty()),
                ctx.getString(R.string.notif_already_answered)
            )
            return
        }
        val feedback = engine.submit(session, answer, now) ?: return
        continueAfter(feedback, session.cardId, now)
    }

    /** Hint button: first and second hint are free, the third request is an automatic failure. */
    fun onHint(sessionId: Long, now: Long = System.currentTimeMillis()) {
        val session = repo.session(sessionId) ?: return
        if (session.answered) return
        val ctx = localized()
        val card = repo.card(session.cardId) ?: return
        val deckName = repo.deck(session.deckId)?.name.orEmpty()

        if (session.hintCount >= 2) {
            val feedback = engine.thirdHintFailure(session, now) ?: return
            continueAfter(feedback, session.cardId, now)
            return
        }

        val next = session.hintCount + 1
        repo.updateSession(session.copy(hintCount = next))
        val hintText = engine.hintText(card, session.hintCount).ifBlank {
            ctx.getString(R.string.notif_no_more_hints)
        }
        presenter().showQuestion(
            deckName = deckName,
            question = card.front,
            hint = hintText,
            hintsUsed = next,
            sessionId = session.id,
            feedbackLine = ctx.getString(R.string.notif_hint_number, next),
            progressLine = progressLine(session.deckId)
        )
    }

    /** "I don't know" pressed in the notification: reveal the answer, fail the card, mark it weak, continue. */
    fun onDontKnow(sessionId: Long, now: Long = System.currentTimeMillis()) {
        val session = repo.session(sessionId) ?: return
        if (session.answered) return
        val feedback = engine.markDontKnow(session, now) ?: return
        continueAfter(feedback, session.cardId, now)
    }

    /** Shows the result and immediately offers the next eligible card in the same notification. */
    fun continueAfter(feedback: Feedback, excludeCardId: Long? = null, now: Long = System.currentTimeMillis()) {
        repo.closeSessions()
        val ctx = localized()
        val dayStart = Repository.startOfDay(now)
        val active = repo.decks().filter { SelectionPolicy.isDeckEligible(it) }

        val budgetLeft = active.isNotEmpty() &&
            settings.learningMode &&
            repo.answersToday(dayStart) < settings.maxQuestionsPerDay

        val selected = if (budgetLeft) engine.pickNext(now, exclude = setOfNotNull(excludeCardId)) else null
        if (selected == null) {
            val due = repo.dueCount(active.map { it.id }, now)
            presenter().showDone(
                ctx.getString(R.string.notif_done_title),
                feedbackText(ctx, feedback) + "\n\n" + ctx.getString(R.string.notif_done_text, due, repo.countCards())
            )
            return
        }

        val sessionId = repo.createSession(
            cardId = selected.card.id,
            deckId = selected.deck.id,
            kind = selected.kind,
            notificationId = NotificationPresenter.NOTIFICATION_ID
        )
        presenter().showQuestion(
            deckName = selected.deck.name,
            question = selected.card.front,
            hint = null,
            hintsUsed = 0,
            sessionId = sessionId,
            feedbackLine = feedbackText(ctx, feedback),
            feedbackPositive = feedback.correct,
            progressLine = progressLine(selected.deck.id)
        )
    }

    private fun feedbackText(ctx: Context, feedback: Feedback): String {
        val sb = StringBuilder()
        when {
            feedback.thirdHintFailure -> sb.append(ctx.getString(R.string.notif_third_hint_title))
            feedback.correct -> sb.append(ctx.getString(R.string.notif_correct_title))
            else -> sb.append(ctx.getString(R.string.notif_wrong_title))
        }
        sb.append('\n').append(ctx.getString(R.string.notif_answer_was, feedback.expectedAnswer))
        if (!feedback.correct && feedback.missingPoints.isNotEmpty()) {
            sb.append('\n').append(
                ctx.getString(R.string.notif_missing_points, feedback.missingPoints.joinToString(" · "))
            )
        }
        if (feedback.explanation.isNotBlank()) {
            sb.append('\n').append(feedback.explanation)
        }
        val ratingName = when (feedback.rating) {
            Grades.EASY -> ctx.getString(R.string.rating_easy)
            Grades.GOOD -> ctx.getString(R.string.rating_good)
            Grades.HARD -> ctx.getString(R.string.rating_hard)
            else -> ctx.getString(R.string.rating_again)
        }
        if (feedback.practice) {
            sb.append('\n').append(ctx.getString(R.string.notif_practice_note))
        } else {
            sb.append('\n').append(ctx.getString(R.string.notif_rating, ratingName))
            sb.append(" · ").append(ctx.getString(R.string.notif_next_in_days, feedback.intervalDays))
        }
        if (feedback.rating == Grades.AGAIN) {
            sb.append('\n').append(ctx.getString(R.string.review_added_weak))
        }
        return sb.toString()
    }

    private fun progressLine(deckId: Long): String {
        val ctx = localized()
        val deck = repo.deck(deckId)
        val activeIds = repo.activeDeckIds()
        val due = repo.dueCount(activeIds, System.currentTimeMillis())
        val newCards = repo.newCount(activeIds)
        val name = deck?.name.orEmpty()
        return ctx.getString(R.string.notif_progress, name, due, newCards)
    }

    fun sessionFor(sessionId: Long): ReviewSession? = repo.session(sessionId)
}
