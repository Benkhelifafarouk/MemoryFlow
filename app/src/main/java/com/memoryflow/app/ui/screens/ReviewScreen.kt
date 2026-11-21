package com.memoryflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardState
import com.memoryflow.app.data.CardType
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.review.AnswerEvaluator
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.AppViewModel
import com.memoryflow.app.ui.ReviewQueueData
import com.memoryflow.app.ui.components.EmptyState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.theme.Mint
import com.memoryflow.app.ui.theme.Rose
import kotlin.random.Random

/** The four simple question kinds, rotated per card so sessions do not feel identical. */
private enum class Kind { TYPED, REVERSE, CHOICE, CHOICE_REVERSE }

private data class PendingFeedback(
    val correct: Boolean,
    val rating: Int,
    val expected: String,
    val missing: List<String>,
    val thirdHint: Boolean = false,
    val explanation: String = "",
    val weakAdded: Boolean = false
)

@Composable
fun ReviewScreen(
    deckId: Long?,
    weakOnly: Boolean,
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit
) {
    var queueData by remember { mutableStateOf<ReviewQueueData?>(null) }
    var index by remember { mutableIntStateOf(0) }
    var answer by remember { mutableStateOf("") }
    var hintsUsed by remember { mutableIntStateOf(0) }
    var feedback by remember { mutableStateOf<PendingFeedback?>(null) }
    var selectedChoice by remember { mutableStateOf<String?>(null) }
    val seed = remember { Random.nextInt(1, 9999) }

    LaunchedEffect(deckId, weakOnly, state.decks.size) {
        val current = queueData
        if (current != null && current.cards.isNotEmpty()) return@LaunchedEffect
        queueData = vm.reviewQueue(deckId, weakOnly)
    }

    val queue = queueData?.cards ?: emptyList()
    val pool = queueData?.pool ?: emptyList()
    val card = queue.getOrNull(index)
    val deckName = card?.let { c -> state.decks.firstOrNull { it.id == c.deckId }?.name } ?: ""

    val kind: Kind = if (card != null) questionKind(card, pool, seed) else Kind.TYPED
    val options: List<String> = if (card != null && (kind == Kind.CHOICE || kind == Kind.CHOICE_REVERSE)) {
        choiceOptions(card, pool, reverse = kind == Kind.CHOICE_REVERSE, seed) ?: emptyList()
    } else emptyList()

    val question = when {
        card == null -> ""
        kind == Kind.REVERSE || kind == Kind.CHOICE_REVERSE -> card.back
        else -> card.front
    }
    val expected = when {
        card == null -> ""
        kind == Kind.REVERSE || kind == Kind.CHOICE_REVERSE -> card.front
        else -> card.back
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(if (weakOnly) R.string.home_weak_title else R.string.review_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (deckName.isNotBlank()) {
                        Text(
                            text = deckName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (queue.isNotEmpty()) {
                    Pill(
                        text = stringResource(R.string.review_progress, (index + 1).toString(), queue.size.toString()),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (card == null) {
            if (queueData == null) {
                item { Text(stringResource(R.string.review_loading)) }
            } else {
                item {
                    EmptyState(
                        title = stringResource(
                            if (weakOnly) R.string.home_weak_empty else R.string.review_empty_title
                        ),
                        subtitle = stringResource(R.string.review_empty_text)
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onBack) { Text(stringResource(R.string.action_done)) }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            val newCount = queue.count { it.state == CardState.NEW }
            val weakCount = queue.count { it.weakCount > 0 }
            Text(
                text = stringResource(
                    R.string.review_session_summary,
                    newCount.toString(),
                    (queue.size - newCount).toString(),
                    weakCount.toString()
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            GlowCard(active = true, accent = if (weakOnly) Rose else MaterialTheme.colorScheme.primary) {
                Text(
                    text = stringResource(
                        when (kind) {
                            Kind.REVERSE -> R.string.review_reverse_label
                            Kind.CHOICE, Kind.CHOICE_REVERSE -> R.string.review_choice_label
                            else -> R.string.review_question
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = question,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (hintsUsed > 0) {
                    Spacer(Modifier.height(10.dp))
                    if (card.hint1.isNotBlank()) {
                        Text(
                            text = "💡 " + card.hint1,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    if (hintsUsed > 1 && card.hint2.isNotBlank()) {
                        Text(
                            text = "💡 " + card.hint2,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        if (feedback == null) {
            if (options.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            OutlinedButton(
                                onClick = {
                                    if (selectedChoice != null) return@OutlinedButton
                                    selectedChoice = option
                                    val correctPick = option == expected
                                    val rating = if (correctPick) AnswerEvaluator.gradeFor(1.0, hintsUsed) else Grades.AGAIN
                                    vm.grade(card.id, rating, option, hintsUsed)
                                    feedback = PendingFeedback(
                                        correct = correctPick,
                                        rating = rating,
                                        expected = expected,
                                        missing = emptyList(),
                                        explanation = card.explanation,
                                        weakAdded = !correctPick
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(option) }
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = answer,
                        onValueChange = { answer = it },
                        label = { Text(stringResource(R.string.review_answer_label)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (options.isEmpty()) {
                        Button(
                            onClick = {
                                val evalCard = if (kind == Kind.REVERSE) {
                                    card.copy(front = card.back, back = card.front, altAnswers = "")
                                } else {
                                    card
                                }
                                val evaluation = AnswerEvaluator.evaluate(evalCard, answer)
                                val rating = if (evaluation.score < 0.40) Grades.AGAIN
                                else AnswerEvaluator.gradeFor(evaluation.score, hintsUsed)
                                vm.grade(card.id, rating, answer, hintsUsed)
                                feedback = PendingFeedback(
                                    correct = rating != Grades.AGAIN,
                                    rating = rating,
                                    expected = expected,
                                    missing = evaluation.missingPoints,
                                    explanation = card.explanation,
                                    weakAdded = rating == Grades.AGAIN
                                )
                            },
                            enabled = answer.isNotBlank()
                        ) { Text(stringResource(R.string.review_check)) }
                    }

                    OutlinedButton(
                        onClick = {
                            if (hintsUsed >= 2) {
                                vm.grade(card.id, Grades.AGAIN, "", 3)
                                feedback = PendingFeedback(
                                    correct = false,
                                    rating = Grades.AGAIN,
                                    expected = expected,
                                    missing = if (kind == Kind.TYPED) AnswerEvaluator.splitPoints(card.back) else emptyList(),
                                    thirdHint = true,
                                    weakAdded = true
                                )
                            } else {
                                hintsUsed += 1
                            }
                        }
                    ) {
                        Text(stringResource(R.string.review_hint, (hintsUsed + 1).toString()))
                    }

                    // "I don't know": reveal the answer, fail the card, mark it as a weak word.
                    TextButton(onClick = {
                        vm.grade(card.id, Grades.AGAIN, "", hintsUsed)
                        feedback = PendingFeedback(
                            correct = false,
                            rating = Grades.AGAIN,
                            expected = expected,
                            missing = if (kind == Kind.TYPED) AnswerEvaluator.splitPoints(card.back) else emptyList(),
                            explanation = card.explanation,
                            weakAdded = true
                        )
                    }) { Text(stringResource(R.string.review_dont_know)) }
                }
            }
        } else {
            item {
                val f = feedback!!
                val color = if (f.correct) Mint else MaterialTheme.colorScheme.error
                GlowCard(active = true, accent = color) {
                    Text(
                        text = when {
                            f.thirdHint -> stringResource(R.string.review_third_hint)
                            f.correct -> stringResource(R.string.review_correct)
                            else -> stringResource(R.string.review_wrong)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.review_expected, f.expected),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    if (f.missing.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.review_missing, f.missing.joinToString(" · ")),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (f.explanation.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = f.explanation,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (f.weakAdded) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.review_added_weak),
                            style = MaterialTheme.typography.labelLarge,
                            color = Rose
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.review_rating, ratingName(f.rating)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        index += 1
                        answer = ""
                        hintsUsed = 0
                        feedback = null
                        selectedChoice = null
                    }) {
                        Text(stringResource(R.string.review_next))
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Rotates the question kind per card; multi-point cards always stay in the forward direction. */
private fun questionKind(card: Card, pool: List<Card>, seed: Int): Kind {
    val multiPoint = card.type == CardType.MULTI_POINT || card.back.contains('\n')
    val base = (((card.id + seed) % 4).toInt() + 4) % 4
    return when {
        base == 0 || multiPoint -> Kind.TYPED
        base == 1 -> Kind.REVERSE
        else -> {
            val reverse = base == 3
            val options = choiceOptions(card, pool, reverse, seed)
            when {
                options == null -> if (reverse) Kind.REVERSE else Kind.TYPED
                reverse -> Kind.CHOICE_REVERSE
                else -> Kind.CHOICE
            }
        }
    }
}

/** Four options: the correct answer plus three distinct distractors from the same pool. */
private fun choiceOptions(card: Card, pool: List<Card>, reverse: Boolean, seed: Int): List<String>? {
    val correct = if (reverse) card.front else card.back
    val candidates = pool
        .map { if (reverse) it.front else it.back }
        .filter { it.isNotBlank() && it != correct }
        .distinct()
    if (candidates.size < 3) return null
    val distractors = candidates.shuffled(Random(seed + card.id)).take(3)
    return (listOf(correct) + distractors).shuffled(Random(card.id * 31 + seed))
}

@Composable
private fun ratingName(rating: Int): String = when (rating) {
    Grades.EASY -> stringResource(R.string.rating_easy)
    Grades.GOOD -> stringResource(R.string.rating_good)
    Grades.HARD -> stringResource(R.string.rating_hard)
    else -> stringResource(R.string.rating_again)
}
