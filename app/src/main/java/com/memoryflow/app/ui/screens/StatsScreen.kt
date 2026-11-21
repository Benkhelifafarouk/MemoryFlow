package com.memoryflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.components.EmptyState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.components.RetentionBar
import com.memoryflow.app.ui.components.ScreenHeader
import com.memoryflow.app.ui.components.SectionTitle
import com.memoryflow.app.ui.components.StatTile
import com.memoryflow.app.ui.components.WeekChart
import com.memoryflow.app.ui.components.deckAccent
import com.memoryflow.app.ui.theme.Cyan
import com.memoryflow.app.ui.theme.Indigo
import com.memoryflow.app.ui.theme.Mint
import com.memoryflow.app.ui.theme.Violet
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StatsScreen(state: AppUiState) {
    val overall = state.overall
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.stats_title),
                subtitle = stringResource(R.string.stats_subtitle)
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_total_cards),
                    value = overall.totalCards.toString(),
                    accent = Indigo,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.stats_due),
                    value = overall.dueToday.toString(),
                    accent = Cyan,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.stats_accuracy),
                    value = if (overall.reviews == 0) "—" else "${(overall.accuracy * 100).toInt()}%",
                    accent = Mint,
                    modifier = Modifier.weight(1f),
                    caption = stringResource(R.string.stats_reviews_count, overall.reviews)
                )
                StatTile(
                    label = stringResource(R.string.stats_hints),
                    value = overall.hintAssisted.toString(),
                    accent = Violet,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.primary) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.stats_retention_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (overall.retention > 0) {
                        Pill(
                            text = "${(overall.retention * 100).toInt()}%",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (overall.retention > 0) {
                    RetentionBar(retention = overall.retention, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.stats_retention_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = stringResource(R.string.stats_retention_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            GlowCard(active = false, accent = Cyan) {
                SectionTitle(stringResource(R.string.stats_week))
                WeekChart(
                    values = state.activity.map { it.count },
                    labels = state.activity.map { SimpleDateFormat("EEE", Locale.getDefault()).format(Date(it.dayStart)) },
                    accent = Cyan
                )
            }
        }

        item { SectionTitle(stringResource(R.string.stats_per_deck)) }

        if (state.decks.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.stats_no_decks_title),
                    subtitle = stringResource(R.string.stats_no_decks_text)
                )
            }
        } else {
            items(state.decks, key = { it.id }) { deck ->
                val stats = state.deckStats[deck.id]
                val accent = deckAccent(deck.colorIndex)
                GlowCard(active = deck.isActive, accent = accent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = deck.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        if (deck.isActive) {
                            Pill(text = stringResource(R.string.status_active), color = accent)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = stringResource(R.string.stats_deck_due, stats?.due ?: 0),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.stats_deck_cards, stats?.total ?: 0),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(R.string.stats_deck_weak, stats?.weak ?: 0),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    RetentionBar(retention = stats?.retention ?: 0.0, color = accent)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text(
                            text = if ((stats?.retention ?: 0.0) > 0)
                                stringResource(R.string.decks_retention, retentionPercent(stats?.retention ?: 0.0))
                            else stringResource(R.string.decks_retention_unknown),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = stringResource(
                                R.string.stats_deck_accuracy,
                                if ((stats?.reviews ?: 0) == 0) "—" else "${((stats?.accuracy ?: 0.0) * 100).toInt()}%"
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if ((stats?.studiedToday ?: 0) > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.stats_deck_today, stats?.studiedToday ?: 0),
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                        )
                    }
                }
            }
        }

        item {
            GlowCard(active = false, accent = Mint) {
                Text(
                    text = stringResource(R.string.stats_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
