package com.memoryflow.app.review

import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardType
import com.memoryflow.app.fsrs.Grades
import java.text.Normalizer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

/**
 * Offline answer evaluation. Pure Kotlin (no Android APIs) so it is unit-testable on the JVM.
 *
 * The rules are deliberately forgiving on formatting and spelling, strict on meaning:
 *  - Arabic diacritics, tatweel and hamza variants are normalised.
 *  - Punctuation, casing and extra spaces are ignored.
 *  - Alternative answers (separated by | ; / or ،) are all accepted.
 *  - Answers made of several points are scored point by point.
 */
object AnswerEvaluator {

    data class Result(
        val score: Double,
        val missingPoints: List<String> = emptyList(),
        val matchedPoints: List<String> = emptyList()
    ) {
        val isCorrect: Boolean get() = score >= 0.60
    }

    private const val PARTIAL_THRESHOLD = 0.60
    private val SEPARATORS = charArrayOf('|', ';', '/', '،')

    fun normalize(raw: String): String {
        if (raw.isBlank()) return ""
        val decomposed = Normalizer.normalize(raw, Normalizer.Form.NFKC)
        val sb = StringBuilder(decomposed.length)
        for (ch in decomposed) {
            var c = ch
            if (isArabicDiacritic(c)) continue
            c = when (c) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى', 'ئ' -> 'ي'
                'ة', 'ۀ' -> 'ه'
                'ؤ' -> 'و'
                'گ' -> 'ك'
                'پ' -> 'ب'
                'ژ' -> 'ز'
                'چ' -> 'ج'
                else -> c
            }
            if (c.isLetterOrDigit()) {
                sb.append(c.lowercaseChar())
            } else {
                sb.append(' ')
            }
        }
        val collapsed = sb.toString().replace(Regex("\\s+"), " ").trim()
        return collapsed.split(' ').joinToString(" ") { stripArabicArticle(it) }
    }

    private fun isArabicDiacritic(c: Char): Boolean =
        c == '\u0640' ||
            c in '\u0610'..'\u061A' ||
            c in '\u064B'..'\u065F' ||
            c == '\u0670' ||
            c in '\u06D6'..'\u06ED'

    /** "التحدي" and "تحدي" should be treated as the same word. */
    private fun stripArabicArticle(token: String): String {
        if (token.length >= 5 && token.startsWith("ال")) return token.substring(2)
        return token
    }

    fun alternatives(card: Card): List<String> {
        val list = ArrayList<String>()
        list.add(card.back)
        card.back.split(*SEPARATORS).forEach { part ->
            if (part.isNotBlank()) list.add(part.trim())
        }
        card.altList.forEach { list.add(it) }
        return list.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    fun evaluate(card: Card, input: String): Result {
        val normalizedInput = normalize(input)
        if (normalizedInput.isBlank()) {
            return Result(0.0, pointsOf(card), emptyList())
        }

        if (card.type == CardType.MULTI_POINT) {
            val points = splitPoints(card.back)
            if (points.size >= 2) {
                val matched = points.filter { pointMatches(it, normalizedInput, input) }
                val missing = points.filterNot { matched.contains(it) }
                return Result(
                    score = matched.size.toDouble() / points.size,
                    missingPoints = missing,
                    matchedPoints = matched
                )
            }
        }

        var best = 0.0
        for (candidate in alternatives(card)) {
            val candidateNorm = normalize(candidate)
            if (candidateNorm.isEmpty()) continue
            // Numbers are compared before punctuation is stripped ("3.14" would become "3 14").
            numericMatch(candidate.trim(), input.trim())?.let { if (it > best) best = it }
            if (best >= 0.995) break
            best = max(best, similarity(candidateNorm, normalizedInput))
        }
        return Result(best, if (best < PARTIAL_THRESHOLD) pointsOf(card) else emptyList())
    }

    /** 0..1 similarity between a reference answer and the user's input. */
    fun similarity(reference: String, input: String): Double {
        if (reference.isEmpty() || input.isEmpty()) return 0.0
        if (reference == input) return 1.0

        val refTokens = reference.split(' ').filter { it.isNotEmpty() }
        val inTokens = input.split(' ').filter { it.isNotEmpty() }
        val refSet = refTokens.toSet()
        val inSet = inTokens.toSet()

        if (reference.contains(input) || input.contains(reference)) {
            val ratio = min(reference.length, input.length).toDouble() / max(reference.length, input.length)
            if (ratio >= 0.55 || inTokens.size >= 2) return max(0.90, 0.55 + 0.40 * ratio)
        }

        val intersection = refSet.intersect(inSet).size
        if (intersection > 0) {
            val union = refSet.union(inSet).size
            val jaccard = intersection.toDouble() / union
            val coverage = intersection.toDouble() / refSet.size
            if (coverage >= 0.70) return max(0.55 + 0.40 * jaccard, 0.70)
            if (jaccard >= 0.60) return 0.60 + 0.40 * jaccard
        }

        // Typo tolerance for single-word answers ("Herausforderung" vs "Herausvorderung").
        if (refTokens.size == 1 && inTokens.size == 1) {
            val lev = levenshteinRatio(refTokens[0], inTokens[0])
            if (lev >= 0.82) return lev * 0.95
        }
        return 0.0
    }

    private fun numericMatch(reference: String, input: String): Double? {
        val ref = reference.replace(',', '.').toDoubleOrNull() ?: return null
        val inn = input.replace(',', '.').toDoubleOrNull() ?: return null
        val diff = kotlin.math.abs(ref - inn)
        return when {
            diff <= 0.0001 -> 1.0
            ref == 0.0 -> 0.0
            diff / kotlin.math.abs(ref) <= 0.02 -> 0.85
            else -> 0.0
        }
    }

    fun splitPoints(back: String): List<String> {
        val cleaned = back
            .replace(Regex("(?m)^\\s*(?:[-•*–—]|\\d+[.)])\\s*"), "\n")
            .replace("؛", "\n")
            .replace(";", "\n")
        return cleaned.split('\n', '•')
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 1 }
    }

    private fun pointMatches(point: String, normalizedInput: String, rawInput: String): Boolean {
        val pointNorm = normalize(point)
        if (pointNorm.isEmpty()) return false
        if (normalizedInput.contains(pointNorm)) return true
        if (pointNorm.contains(normalizedInput) && normalizedInput.length >= 4) return true
        val pointTokens = pointNorm.split(' ').filter { it.length >= 3 }
        if (pointTokens.isEmpty()) return false
        val inputTokens = normalizedInput.split(' ').toSet()
        val hits = pointTokens.count { token ->
            inputTokens.any { it == token || levenshteinRatio(it, token) >= 0.85 }
        }
        val coverage = hits.toDouble() / pointTokens.size
        if (coverage >= 0.70) return true
        // Very short points ("sleep") should match on a single strong token.
        return pointTokens.size <= 2 && coverage >= 0.50
    }

    /**
     * Maps an evaluation to a 1..4 FSRS grade.
     * Correct without hints is the strongest signal; hints lower the grade; a third hint is a failure.
     */
    fun gradeFor(score: Double, hintCount: Int): Int = when {
        score >= 0.995 && hintCount == 0 -> Grades.EASY
        score >= 0.995 && hintCount == 1 -> Grades.GOOD
        score >= 0.995 -> Grades.HARD
        score >= 0.75 && hintCount == 0 -> Grades.GOOD
        score >= 0.40 -> Grades.HARD
        else -> Grades.AGAIN
    }

    /** Character-level similarity (1 - distance / maxLen). */
    fun levenshteinRatio(a: String, b: String): Double {
        if (a == b) return 1.0
        val longer = max(a.length, b.length)
        if (longer == 0) return 1.0
        if (kotlin.math.abs(a.length - b.length).toDouble() / longer > 0.4) return 0.0
        val distance = levenshtein(a, b)
        return (1.0 - distance.toDouble() / longer).coerceIn(0.0, 1.0)
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val swap = prev
            prev = cur
            cur = swap
        }
        return prev[b.length]
    }

    private fun pointsOf(card: Card): List<String> =
        if (card.type == CardType.MULTI_POINT) splitPoints(card.back) else emptyList()

    fun localeOf(language: String): Locale = when (language) {
        "de" -> Locale.GERMAN
        "en" -> Locale.ENGLISH
        else -> Locale("ar")
    }
}
