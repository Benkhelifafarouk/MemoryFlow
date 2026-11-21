package com.memoryflow.app.io

import java.text.Normalizer
import java.util.Locale

/** Result of comparing parsed terms against what a deck already contains. */
data class ImportPlan(
    val toImport: List<ParsedCard>,
    val duplicates: Int,
    val invalid: Int,
    val total: Int
)

/**
 * Decides which parsed terms are new for a deck. Pure Kotlin: no database, no Android.
 *
 * Terms are compared after trimming, collapsing inner whitespace, Unicode-normalising and
 * lower-casing, so "  der Tisch " and "der  tisch" count as the same vocabulary.
 */
object ImportPlanner {

    private val WHITESPACE = Regex("\\s+")

    fun key(term: String): String {
        val normalized = Normalizer.normalize(term, Normalizer.Form.NFKC)
        return WHITESPACE.replace(normalized.trim(), " ").lowercase(Locale.ROOT)
    }

    fun plan(existingKeys: Set<String>, parsed: List<ParsedCard>): ImportPlan {
        val seen = HashSet<String>(parsed.size)
        val toImport = ArrayList<ParsedCard>(parsed.size)
        var duplicates = 0
        var invalid = 0

        for (card in parsed) {
            val front = card.front.trim()
            if (front.isEmpty()) {
                invalid++
                continue
            }
            val key = key(front)
            if (key in existingKeys || !seen.add(key)) {
                duplicates++
                continue
            }
            toImport.add(
                card.copy(
                    front = WHITESPACE.replace(front, " "),
                    back = card.back.trim()
                )
            )
        }

        return ImportPlan(
            toImport = toImport,
            duplicates = duplicates,
            invalid = invalid,
            total = parsed.size
        )
    }
}
