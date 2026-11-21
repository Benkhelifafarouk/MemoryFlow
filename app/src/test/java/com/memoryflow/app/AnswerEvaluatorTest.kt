package com.memoryflow.app

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardType
import com.memoryflow.app.review.AnswerEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerEvaluatorTest {

    private fun card(back: String, alt: String = "", type: CardType = CardType.VOCABULARY) = Card(
        deckId = 1,
        front = "question",
        back = back,
        altAnswers = alt,
        type = type
    )

    @Test
    fun `arabic diacritics and the definite article are ignored`() {
        val result = AnswerEvaluator.evaluate(card("التّحدّي"), "تحدي")
        assertEquals(1.0, result.score, 0.01)
        assertTrue(result.isCorrect)
    }

    @Test
    fun `alternative answers are accepted`() {
        val c = card("التحدي", alt = "العائق|المصاعب")
        assertTrue(AnswerEvaluator.evaluate(c, "العائق").score >= 0.99)
        assertTrue(AnswerEvaluator.evaluate(c, "المصاعب").score >= 0.99)
    }

    @Test
    fun `answers separated by a pipe inside the back are accepted`() {
        val c = card("the challenge | the hurdle")
        assertTrue(AnswerEvaluator.evaluate(c, "the hurdle").score >= 0.99)
    }

    @Test
    fun `multi point answers count matched points and list the missing ones`() {
        val c = card(
            "Better physical health\nImproved cardiovascular fitness\nBetter sleep\nImproved mental well-being",
            type = CardType.MULTI_POINT
        )
        val result = AnswerEvaluator.evaluate(c, "better physical health and better sleep")
        assertEquals(0.5, result.score, 0.01)
        assertEquals(2, result.missingPoints.size)
        assertEquals(2, result.matchedPoints.size)
    }

    @Test
    fun `a single point answer still matches partially`() {
        val c = card("Better physical health\nBetter sleep", type = CardType.MULTI_POINT)
        val result = AnswerEvaluator.evaluate(c, "better sleep")
        assertEquals(0.5, result.score, 0.01)
    }

    @Test
    fun `typos are tolerated for single word answers`() {
        val c = card("Herausforderung")
        assertTrue(AnswerEvaluator.evaluate(c, "Herausvorderung").score > 0.70)
    }

    @Test
    fun `an unrelated answer scores zero`() {
        val c = card("التحدي")
        assertEquals(0.0, AnswerEvaluator.evaluate(c, "السيارة").score, 0.01)
    }

    @Test
    fun `an empty answer scores zero`() {
        assertEquals(0.0, AnswerEvaluator.evaluate(card("anything"), "   ").score, 0.01)
    }

    @Test
    fun `numeric answers use a tolerance`() {
        assertEquals(1.0, AnswerEvaluator.evaluate(card("3.14"), "3.1400").score, 0.01)
        assertTrue(AnswerEvaluator.evaluate(card("3.14"), "3.14159").score >= 0.80)
        assertEquals(0.0, AnswerEvaluator.evaluate(card("3.14"), "9.81").score, 0.01)
    }

    @Test
    fun `punctuation and casing do not matter`() {
        val c = card("A political system in which people participate.")
        assertTrue(AnswerEvaluator.evaluate(c, "a political system in which people participate").score >= 0.99)
    }

    @Test
    fun `grade rises when no hint was used and falls with hints`() {
        assertEquals(4, AnswerEvaluator.gradeFor(1.0, 0))
        assertEquals(3, AnswerEvaluator.gradeFor(1.0, 1))
        assertEquals(2, AnswerEvaluator.gradeFor(1.0, 2))
        assertEquals(1, AnswerEvaluator.gradeFor(0.1, 0))
    }

    @Test
    fun `partial credit maps to hard`() {
        assertEquals(2, AnswerEvaluator.gradeFor(0.5, 0))
        assertEquals(3, AnswerEvaluator.gradeFor(0.8, 0))
    }
}
