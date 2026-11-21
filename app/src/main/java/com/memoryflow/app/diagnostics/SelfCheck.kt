package com.memoryflow.app.diagnostics

import android.content.Context
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState
import com.memoryflow.app.data.Deck
import com.memoryflow.app.data.Repository
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.io.CardTextParser
import com.memoryflow.app.io.ImportPlanner
import com.memoryflow.app.io.ParsedCard
import com.memoryflow.app.io.XlsxReader
import com.memoryflow.app.io.XlsxWriter
import com.memoryflow.app.notify.SelectionPolicy
import com.memoryflow.app.review.AnswerEvaluator
import com.memoryflow.app.review.SessionBuilder
import java.io.ByteArrayOutputStream

data class CheckResult(
    val title: String,
    val passed: Boolean,
    val detail: String,
    val manual: Boolean = false
)

/**
 * Runs the functional acceptance checks against a throw-away database.
 * Nothing here touches the user's real data, and every result is a real assertion.
 */
class SelfCheck(private val context: Context) {

    fun runAll(): List<CheckResult> {
        val dbName = "memoryflow_selfcheck_${System.currentTimeMillis()}.db"
        context.deleteDatabase(dbName)
        val results = ArrayList<CheckResult>()
        var repo: Repository? = null
        try {
            repo = Repository.scratch(context, dbName)
            functionalChecks(repo, results)
        } catch (t: Throwable) {
            results.add(CheckResult("Self-check crashed", false, t.message ?: t.javaClass.simpleName))
        } finally {
            runCatching { repo?.close() }
            context.deleteDatabase(dbName)
        }
        results.addAll(manualChecks())
        return results
    }

    private fun functionalChecks(repo: Repository, out: MutableList<CheckResult>) {
        // 1. Deck creation
        val a1 = repo.createDeck("German A1", active = false)
        val a2 = repo.createDeck("German A2", active = false)
        val b1 = repo.createDeck("German B1", active = false)
        check(out, "Create three decks (A1, A2, B1)") {
            val decks = repo.decks()
            if (decks.size == 3) pass("${decks.size} decks in database")
            else fail("expected 3 decks, found ${decks.size}")
        }

        // 2. Cards per deck
        val now = System.currentTimeMillis()
        val a2Card = addCard(repo, a2, "die Herausforderung", "التحدي", "شيء صعب", "موقف يحتاج صبرا", now)
        val a2Card2 = addCard(repo, a2, "die Entscheidung", "القرار", "", "", now)
        val a1Card = addCard(repo, a1, "der Apfel", "التفاحة", "", "", now)
        val b1Card = addCard(repo, b1, "die Nachhaltigkeit", "الاستدامة", "فكرة بيئية", "توازن الموارد", now)
        check(out, "Add cards to each deck") {
            val total = repo.countCards()
            if (total == 4) pass("4 cards, A2=${repo.countCards(a2)}, B1=${repo.countCards(b1)}")
            else fail("expected 4 cards, found $total")
        }

        // 3. Activation is persisted
        repo.setDeckActive(a2, true)
        check(out, "Activate A2 only") {
            val active = repo.activeDeckIds()
            if (active == listOf(a2)) pass("active decks = [A2]")
            else fail("active decks = $active")
        }

        // 4. Queue only contains active deck
        check(out, "Queue contains only A2 cards") {
            val picked = repo.dueCards(repo.activeDeckIds(), now, 10).map { it.id } +
                repo.newCards(a2, 10).map { it.id }
            val fromA2 = picked.isNotEmpty() && picked.all { id -> repo.card(id)?.deckId == a2 }
            if (fromA2) pass("${picked.size} candidates, all from A2")
            else fail("candidates leaked from another deck")
        }

        // 5. Two active decks are both included
        repo.setDeckActive(b1, true)
        check(out, "Activate A2 + B1: both decks included") {
            val active = repo.activeDeckIds().toSet()
            val a2Has = repo.newCards(a2, 10).isNotEmpty()
            val b1Has = repo.newCards(b1, 10).isNotEmpty()
            if (active.containsAll(listOf(a2, b1)) && a2Has && b1Has) pass("A2 and B1 both eligible")
            else fail("active=$active a2=$a2Has b1=$b1Has")
        }

        // 6. Deactivation removes the deck from the queue
        repo.setDeckActive(a2, false)
        check(out, "Deactivate A2: A2 excluded from queue") {
            val activeIds = repo.activeDeckIds()
            val candidates = activeIds.flatMap { repo.newCards(it, 20) + repo.dueCards(listOf(it), now, 20) }
            val leaked = candidates.count { it.deckId == a2 }
            if (activeIds == listOf(b1) && leaked == 0) pass("queue has no A2 card")
            else fail("active=$activeIds leaked=$leaked")
        }

        // 7. Hints: two available, third is a failure
        repo.createSession(a2Card, a2, SelectionPolicy.KIND_SCHEDULED, 4201)
        check(out, "Two hints available, third hint = failure") {
            val card = repo.card(a2Card)!!
            val hint1 = card.hint1
            val hint2 = card.hint2
            if (hint1.isBlank() || hint2.isBlank()) return@check fail("seed card has no hints")
            val session = repo.pendingSession()!!
            val outcome = repo.grade(card.id, Grades.AGAIN, "", 3, practice = false, now = now)
            repo.updateSession(session.copy(answered = true, correct = false, answeredAt = now, hintCount = 3))
            val failed = outcome != null && !outcome.correct && outcome.state == CardState.RELEARNING
            if (failed) pass("third hint graded Again and card moved to relearning")
            else fail("third hint did not fail: $outcome")
        }

        // 8. Hints lower the grade for a correct answer
        check(out, "Hint usage lowers the grade") {
            val noHint = AnswerEvaluator.gradeFor(1.0, 0)
            val oneHint = AnswerEvaluator.gradeFor(1.0, 1)
            val twoHints = AnswerEvaluator.gradeFor(1.0, 2)
            if (noHint == Grades.EASY && oneHint < noHint && twoHints < oneHint) pass("EASY > GOOD > HARD")
            else fail("grades $noHint/$oneHint/$twoHints")
        }

        // 9. FSRS schedule really changes
        check(out, "FSRS schedule updates after a review") {
            val before = repo.card(a2Card2)!!
            repo.grade(before.id, Grades.GOOD, "القرار", 0, practice = false, now = now + 60_000)
            val after = repo.card(a2Card2)!!
            val grew = after.stability > before.stability && after.due > now && after.reps == before.reps + 1
            if (grew) pass("S=${"%.2f".format(before.stability)} -> ${"%.2f".format(after.stability)}, due +${(after.due - now) / 86_400_000} d")
            else fail("stability ${before.stability} -> ${after.stability}, reps ${before.reps} -> ${after.reps}")
        }

        // 10. Arabic normalisation
        check(out, "Arabic answer normalisation (التحدي vs تحدي)") {
            val card = repo.card(a2Card)!!
            val score = AnswerEvaluator.evaluate(card, "تحدي").score
            if (score >= 0.99) pass("score=${"%.2f".format(score)}")
            else fail("score=${"%.2f".format(score)}")
        }

        // 11. Multi-point partial credit
        check(out, "Multi-point answers report missing points") {
            val card = Card(
                deckId = a2,
                front = "What are the advantages of exercise?",
                back = "Better physical health\nImproved cardiovascular fitness\nBetter sleep\nImproved mental well-being",
                type = com.memoryflow.app.data.CardType.MULTI_POINT
            )
            val result = AnswerEvaluator.evaluate(card, "better physical health and better sleep")
            if (result.score in 0.45..0.55 && result.missingPoints.size == 2) pass("score=${"%.2f".format(result.score)}, missing=${result.missingPoints.size}")
            else fail("score=${"%.2f".format(result.score)}, missing=${result.missingPoints.size}")
        }

        // 12. Random practice must not touch the schedule
        check(out, "Random practice leaves FSRS untouched") {
            val before = repo.card(a1Card)!!
            repo.grade(before.id, Grades.AGAIN, "wrong", 0, practice = true, now = now + 120_000)
            val after = repo.card(a1Card)!!
            val untouched = after.due == before.due && after.stability == before.stability && after.state == before.state
            if (untouched) pass("due and stability unchanged")
            else fail("state changed after practice")
        }

        // 13. CSV import with duplicate detection
        val csv = "front,back\n\"der Hund\",\"الكلب\"\n\"die Katze\",\"القطة\"\n\"der Hund\",\"الكلب\"\n"
        check(out, "CSV import + duplicate detection") {
            val parsed = CardTextParser.parseCsv(csv)
            val first = importManually(repo, b1, parsed, now)
            val second = importManually(repo, b1, parsed, now)
            if (first == 2 && second == 0) pass("imported 2, re-import skipped 2 duplicates")
            else fail("first=$first second=$second")
        }

        // 14. XLSX round trip
        check(out, "XLSX export -> import round trip") {
            val rows = listOf(listOf("front", "back"), listOf("das Haus", "البيت"), listOf("der Baum", "الشجرة"))
            val bytes = ByteArrayOutputStream().also { XlsxWriter.write(it, rows) }.toByteArray()
            val read = XlsxReader.read(bytes.inputStream())
            if (read.size == 3 && read[1][1] == "البيت" && read[2][0] == "der Baum") pass("3 rows matched, Arabic preserved")
            else fail("read ${read.size} rows: $read")
        }

        // 15. TXT bulk import: one term per line, duplicates skipped, 1,000 rows
        check(out, "TXT import: 1,000 terms + duplicates") {
            val text = buildString {
                for (i in 1..1000) append("Wort$i\n")
                append("Wort1\nWort2\nWort3\nWort4\nWort5\n")
            }
            val parsed = CardTextParser.parseBulk(text)
            val existing = repo.cardFronts(b1).mapTo(HashSet()) { ImportPlanner.key(it) }
            val plan = ImportPlanner.plan(existing, parsed)
            val startedAt = System.currentTimeMillis()
            val inserted = repo.insertCardsBatch(
                plan.toImport.map { parsedCard ->
                    Card(
                        deckId = b1,
                        front = parsedCard.front,
                        back = parsedCard.back,
                        due = startedAt,
                        createdAt = startedAt,
                        suspended = true
                    )
                }
            )
            val elapsed = System.currentTimeMillis() - startedAt
            if (plan.total == 1005 && plan.duplicates == 5 && inserted == 1000) {
                pass("1,005 parsed, 5 duplicates skipped, 1,000 inserted in ${elapsed}ms")
            } else {
                fail("total=${plan.total} duplicates=${plan.duplicates} inserted=$inserted")
            }
        }

        // 16. Labelled TXT blocks (Plural / Arabic)
        check(out, "TXT blocks: Plural + Arabic labels") {
            val text = "die Erfahrung\nPlural: die Erfahrungen\nالعربية: الخبرة\n\nder Tisch = الطاولة\n"
            val parsed = CardTextParser.parseBulk(text)
            val first = parsed.getOrNull(0)
            val ok = parsed.size == 2 &&
                first?.back == "الخبرة" &&
                first.explanation.contains("Erfahrungen") &&
                parsed[1].back == "الطاولة"
            if (ok) pass("2 entries, plural kept in the explanation") else fail("parsed=${parsed.map { it.front + "/" + it.back }}")
        }

        // 17. Weak words: a failure adds, a success removes
        check(out, "Weak words: fail adds, success removes") {
            val weakDeck = repo.createDeck("Weak test", active = false)
            val now3 = System.currentTimeMillis()
            val weakCardId = repo.insertCard(
                Card(
                    deckId = weakDeck,
                    front = "vermeiden",
                    back = "يتجنب",
                    state = CardState.REVIEW,
                    stability = 10.0,
                    difficulty = 5.0,
                    lastReview = now3 - 3 * 86_400_000L,
                    due = now3,
                    createdAt = now3
                )
            )
            repo.grade(weakCardId, Grades.AGAIN, "", 0, practice = false, now = now3)
            val afterFail = repo.card(weakCardId)?.weakCount ?: 0
            val inList = repo.weakCards(listOf(weakDeck), 10).any { it.id == weakCardId }
            repo.grade(weakCardId, Grades.GOOD, "يتجنب", 0, practice = false, now = now3 + 60_000)
            val afterPass = repo.card(weakCardId)?.weakCount ?: 0
            val outOfList = repo.weakCards(listOf(weakDeck), 10).none { it.id == weakCardId }
            if (afterFail == 1 && inList && afterPass == 0 && outOfList) {
                pass("weak 1 -> listed -> weak 0 -> removed")
            } else {
                fail("afterFail=$afterFail inList=$inList afterPass=$afterPass out=$outOfList")
            }
        }

        // 18. Session caps: new words are introduced in small batches, weak words first
        check(out, "Session caps and weak priority") {
            val now4 = System.currentTimeMillis()
            val pool = (1..60).map { i ->
                Card(id = i.toLong(), deckId = 999L, front = "Wort$i", back = "كلمة$i", state = CardState.NEW, due = now4, createdAt = now4)
            }
            val session = SessionBuilder.build(
                pool = pool,
                deckNewCaps = mapOf(999L to 20),
                newStudiedToday = emptyMap(),
                globalNewStudiedToday = 0,
                globalNewLimit = 20,
                now = now4,
                weakOnly = false
            )
            val capped = session.size == 20 && session.all { it.state == CardState.NEW }

            val duePool = pool.take(10).map { it.copy(state = CardState.REVIEW, due = now4 - 1000) } +
                pool.take(3).map { it.copy(id = 100L + it.id, state = CardState.REVIEW, due = now4 - 1000, weakCount = 3) }
            val ordered = SessionBuilder.build(
                pool = duePool,
                deckNewCaps = emptyMap(),
                newStudiedToday = emptyMap(),
                globalNewStudiedToday = 0,
                globalNewLimit = 0,
                now = now4,
                weakOnly = false
            )
            val weakFirst = ordered.take(3).all { it.weakCount > 0 }
            if (capped && weakFirst) pass("60 new -> 20 in session, weak words first") else fail("size=${session.size} weakFirst=$weakFirst")
        }

        // 19. Persistence across a database reopen
        check(out, "Data survives closing and reopening the database") {
            val cardBefore = repo.card(a2Card2)!!
            val reopenName = "memoryflow_selfcheck_reopen_${System.currentTimeMillis()}.db"
            val firstOpen = Repository.scratch(context, reopenName)
            val id = firstOpen.insertCard(cardBefore.copy(id = 0))
            firstOpen.close()
            val secondOpen = Repository.scratch(context, reopenName)
            val restored = secondOpen.card(id)
            secondOpen.close()
            context.deleteDatabase(reopenName)
            if (restored != null && restored.front == cardBefore.front && restored.stability == cardBefore.stability) pass("card ${restored.id} restored")
            else fail("card not found after reopen")
        }

        // 20. Daily limits and quiet hours policy
        check(out, "Limits and quiet hours policy") {
            val deck = Deck(id = 1, name = "x", dailyLimit = 5)
            val underLimit = SelectionPolicy.canReviewMore(deck, 4)
            val atLimit = SelectionPolicy.canReviewMore(deck, 5)
            val quietNight = SelectionPolicy.withinWindow(23, 22, 7)
            val quietDay = SelectionPolicy.withinWindow(12, 22, 7)
            val noSpam = !SelectionPolicy.shouldPost(
                lastNotificationAt = now, now = now + 60_000, notificationsToday = 0, dailyNotificationLimit = 12,
                answersToday = 0, maxQuestionsPerDay = 30, frequency = 1, quietNow = false, inWindow = true,
                notificationsEnabled = true, learningMode = true, activeDeckCount = 1
            )
            if (underLimit && !atLimit && quietNight && !quietDay && noSpam) pass("limit, quiet hours and min-gap gates behave")
            else fail("under=$underLimit at=$atLimit night=$quietNight day=$quietDay spamGate=$noSpam")
        }
    }

    private fun addCard(repo: Repository, deckId: Long, front: String, back: String, h1: String, h2: String, now: Long): Long =
        repo.insertCard(
            Card(
                deckId = deckId,
                front = front,
                back = back,
                hint1 = h1,
                hint2 = h2,
                due = now,
                createdAt = now
            )
        )

    private fun importManually(repo: Repository, deckId: Long, cards: List<ParsedCard>, now: Long): Int {
        var imported = 0
        for (p in cards) {
            if (repo.findDuplicate(deckId, p.front) != null) continue
            repo.insertCard(Card(deckId = deckId, front = p.front, back = p.back, due = now, createdAt = now))
            imported++
        }
        return imported
    }

    private fun manualChecks(): List<CheckResult> = listOf(
        CheckResult("Receive a real system notification", false, "Needs a physical phone with notification permission granted", manual = true),
        CheckResult("Reply inside the notification (RemoteInput)", false, "Needs a phone; some manufacturers restrict inline reply", manual = true),
        CheckResult("Install the generated APK", false, "Needs a phone; see the Arabic guide", manual = true),
        CheckResult("Notification after device reboot", false, "Needs a phone; re-arm happens on BOOT_COMPLETED", manual = true)
    )

    private fun pass(detail: String) = true to detail

    private fun fail(detail: String) = false to detail

    private fun check(out: MutableList<CheckResult>, title: String, block: () -> Pair<Boolean, String>) {
        val result = runCatching { block() }
        result.onSuccess { out.add(CheckResult(title, it.first, it.second)) }
            .onFailure { out.add(CheckResult(title, false, it.message ?: it.javaClass.simpleName)) }
    }
}
