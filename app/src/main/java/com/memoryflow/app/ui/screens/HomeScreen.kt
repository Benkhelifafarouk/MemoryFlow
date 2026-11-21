package com.memoryflow.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.data.Deck
import com.memoryflow.app.data.ReviewLog
import com.memoryflow.app.fsrs.Grades
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.components.PowerDial
import com.memoryflow.app.ui.components.SectionTitle
import com.memoryflow.app.ui.components.StatTile
import com.memoryflow.app.ui.components.WeekChart
import com.memoryflow.app.ui.components.deckAccent
import com.memoryflow.app.ui.theme.Cyan
import com.memoryflow.app.ui.theme.Indigo
import com.memoryflow.app.ui.theme.Mint
import com.memoryflow.app.ui.theme.Rose
import androidx.compose.ui.res.stringResource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: AppUiState,
    onReviewNow: () -> Unit,
    onReviewWeak: () -> Unit,
    onOpenDecks: () -> Unit,
    onOpenAdd: () -> Unit,
    onToggleLearning: () -> Unit,
    onToggleDeck: (Deck, Boolean) -> Unit
) {
    val settings = state.settings
    val active = state.activeDecks
    val accents = listOf(Indigo, Cyan, Mint)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Pill(
                    text = if (settings.learningMode) stringResource(R.string.status_active)
                    else stringResource(R.string.status_inactive),
                    color = if (settings.learningMode) Mint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(30.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.95f),
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.home_learning_mode),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(14.dp))
                    PowerDial(
                        active = settings.learningMode,
                        accent = MaterialTheme.colorScheme.onPrimary,
                        dialSize = 132.dp,
                        dialLabel = stringResource(R.string.home_learning_mode),
                        onToggle = onToggleLearning
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (settings.learningMode) stringResource(R.string.home_mode_on)
                        else stringResource(R.string.home_mode_off),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = if (settings.learningMode) stringResource(R.string.home_mode_on_desc)
                        else stringResource(R.string.home_mode_off_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.home_active_groups, active.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.height(16.dp))
                    if (active.isEmpty()) {
                        Button(onClick = onOpenDecks) {
                            Text(stringResource(R.string.home_pick_groups))
                        }
                    } else {
                        Button(onClick = onReviewNow) {
                            Text(stringResource(R.string.home_review_now))
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.home_stat_due),
                    value = state.overall.dueToday.toString(),
                    accent = accents[0],
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.home_stat_new),
                    value = state.overall.newCards.toString(),
                    accent = accents[1],
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.home_stat_learned),
                    value = state.overall.learned.toString(),
                    accent = accents[2],
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.home_stat_streak),
                    value = stringResource(R.string.days_value, state.overall.streak),
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            val weak = state.activeWeak
            GlowCard(active = weak > 0, accent = Rose) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_weak_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (weak > 0) stringResource(R.string.home_weak_count, weak.toString())
                            else stringResource(R.string.home_weak_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.weakPreview.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.weakPreview.joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = Rose,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (weak > 0) {
                        Button(onClick = onReviewWeak) {
                            Text(stringResource(R.string.home_weak_button))
                        }
                    }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(stringResource(R.string.home_groups_section), Modifier.weight(1f))
                TextButton(onClick = onOpenDecks) { Text(stringResource(R.string.home_manage)) }
            }
        }

        if (state.decks.isEmpty()) {
            item {
                GlowCard(active = false, accent = MaterialTheme.colorScheme.primary, onClick = onOpenDecks) {
                    Text(
                        text = stringResource(R.string.home_no_groups_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.home_no_groups_text),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.decks, key = { it.id }) { deck ->
                        val accent = deckAccent(deck.colorIndex)
                        GlowCard(
                            active = deck.isActive,
                            accent = accent,
                            modifier = Modifier.width(196.dp),
                            onClick = { onToggleDeck(deck, !deck.isActive) }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = deck.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = if (deck.isActive) stringResource(R.string.status_active)
                                        else stringResource(R.string.status_inactive),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (deck.isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                PowerDial(
                                    active = deck.isActive,
                                    accent = accent,
                                    dialSize = 44.dp,
                                    dialLabel = deck.name,
                                    onToggle = { onToggleDeck(deck, !deck.isActive) }
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(
                                    R.string.home_deck_stats,
                                    state.deckStats[deck.id]?.due ?: 0,
                                    state.deckStats[deck.id]?.total ?: 0
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.secondary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.home_week_activity), Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.home_week_total, state.activity.sumOf { it.count }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                WeekChart(
                    values = state.activity.map { it.count },
                    labels = state.activity.map { dayLabel(it.dayStart) },
                    accent = MaterialTheme.colorScheme.primary
                )
            }
        }

        item { SectionTitle(stringResource(R.string.home_recent)) }
        if (state.recent.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.home_no_activity),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(state.recent.take(6), key = { it.id }) { log ->
                RecentRow(log = log, deckName = state.decks.firstOrNull { it.id == log.deckId }?.name ?: "")
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun RecentRow(log: ReviewLog, deckName: String) {
    val positive = log.rating != Grades.AGAIN
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .clip(RoundedCornerShape(50))
                .background(if (positive) Mint else MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deckName.ifBlank { stringResource(R.string.nav_decks) },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = SimpleDateFormat("d MMM · HH:mm", Locale.getDefault()).format(Date(log.reviewedAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = when (log.rating) {
                Grades.EASY -> stringResource(R.string.rating_easy)
                Grades.GOOD -> stringResource(R.string.rating_good)
                Grades.HARD -> stringResource(R.string.rating_hard)
                else -> stringResource(R.string.rating_again)
            },
            style = MaterialTheme.typography.labelMedium,
            color = if (positive) Mint else MaterialTheme.colorScheme.error
        )
    }
}

private fun dayLabel(dayStart: Long): String =
    SimpleDateFormat("EEE", Locale.getDefault()).format(Date(dayStart))
