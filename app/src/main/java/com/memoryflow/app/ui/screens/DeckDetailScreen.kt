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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.Deck
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.AppViewModel
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.components.RetentionBar
import com.memoryflow.app.ui.components.StatTile
import com.memoryflow.app.ui.components.deckAccent
import com.memoryflow.app.ui.theme.Amber
import com.memoryflow.app.ui.theme.Mint
import com.memoryflow.app.ui.theme.Rose

@Composable
fun DeckDetailScreen(
    deckId: Long,
    state: AppUiState,
    vm: AppViewModel,
    onBack: () -> Unit,
    onAddCard: (Long) -> Unit,
    onReview: () -> Unit,
    onDeleted: () -> Unit
) {
    val deck = state.decks.firstOrNull { it.id == deckId }
    if (deck == null) {
        LaunchedEffect(deckId) { onBack() }
        return
    }
    val accent = deckAccent(deck.colorIndex)
    val stats = state.deckStats[deckId]
    var name by remember(deck.id) { mutableStateOf(deck.name) }
    var description by remember(deck.id) { mutableStateOf(deck.description) }
    var category by remember(deck.id) { mutableStateOf(deck.category) }
    var dailyLimit by remember(deck.id) { mutableStateOf(deck.dailyLimit) }
    var newPerDay by remember(deck.id) { mutableStateOf(deck.newPerDay) }
    var notifications by remember(deck.id) { mutableStateOf(deck.notificationsEnabled) }
    var cards by remember { mutableStateOf<List<Card>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Card?>(null) }

    LaunchedEffect(deckId, reload, state.overall.totalCards) {
        cards = vm.cardsOf(deckId)
    }

    val save: () -> Unit = {
        vm.updateDeck(
            deck.copy(
                name = name.trim(),
                description = description.trim(),
                category = category.trim(),
                dailyLimit = dailyLimit,
                newPerDay = newPerDay,
                notificationsEnabled = notifications
            )
        )
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
                        text = deck.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.deck_detail_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = stringResource(R.string.home_stat_due),
                    value = (stats?.due ?: 0).toString(),
                    accent = accent,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.deck_stats_total),
                    value = (stats?.total ?: 0).toString(),
                    accent = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.deck_stats_accuracy),
                    value = if ((stats?.reviews ?: 0) == 0) "—" else "${(((stats?.accuracy ?: 0.0)) * 100).toInt()}%",
                    accent = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            GlowCard(active = deck.isActive, accent = accent) {
                Text(
                    text = stringResource(R.string.deck_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.label_category_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                LimitStepper(
                    label = stringResource(R.string.deck_daily_limit),
                    value = dailyLimit,
                    range = 1..200,
                    onChange = { dailyLimit = it }
                )
                LimitStepper(
                    label = stringResource(R.string.deck_new_per_day),
                    value = newPerDay,
                    range = 0..100,
                    onChange = { newPerDay = it }
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.deck_notifications),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = notifications, onCheckedChange = { notifications = it })
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = save) { Text(stringResource(R.string.action_save)) }
                    OutlinedButton(onClick = onReview) { Text(stringResource(R.string.home_review_now)) }
                }
            }
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.deck_cards_title, cards.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onAddCard(deckId) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.deck_add_card))
                }
            }
        }

        if (cards.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.deck_no_cards),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(cards, key = { it.id }) { card ->
                GlowCard(active = false, accent = accent) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = card.front,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = card.back,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                when {
                                    card.back.isBlank() -> Pill(
                                        text = stringResource(R.string.card_draft),
                                        color = Amber
                                    )
                                    card.weakCount > 0 -> Pill(
                                        text = stringResource(R.string.card_weak),
                                        color = Rose
                                    )
                                    card.state == 0 -> Pill(
                                        text = stringResource(R.string.card_state_new),
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    card.state == 2 && card.stability >= 21.0 -> Pill(
                                        text = stringResource(R.string.card_state_mastered),
                                        color = Mint
                                    )
                                    else -> Pill(
                                        text = stringResource(R.string.card_state_review),
                                        color = accent
                                    )
                                }
                                if (card.hintCount > 0) {
                                    Pill(
                                        text = stringResource(R.string.card_hints, card.hintCount),
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { editing = card }) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = stringResource(R.string.action_edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            vm.deleteCard(card) { reload += 1 }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.action_delete),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.error) {
                Text(
                    text = stringResource(R.string.deck_danger_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.deck_danger_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmReset = true }) {
                        Text(stringResource(R.string.deck_reset_progress))
                    }
                    OutlinedButton(onClick = { confirmDelete = true }) {
                        Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.deck_delete_title)) },
            text = { Text(stringResource(R.string.deck_delete_text, deck.name)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteDeck(deck)
                    onDeleted()
                }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.deck_reset_title)) },
            text = { Text(stringResource(R.string.deck_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    vm.resetDeck(deck)
                    reload += 1
                }) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }

    val cardBeingEdited = editing
    if (cardBeingEdited != null) {
        CardEditDialog(
            card = cardBeingEdited,
            onDismiss = { editing = null },
            onSave = { front, back, h1, h2, explanation ->
                vm.updateCard(cardBeingEdited, front, back, h1, h2, explanation) { reload += 1 }
                editing = null
            }
        )
    }
}

@Composable
private fun CardEditDialog(
    card: Card,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var front by remember(card.id) { mutableStateOf(card.front) }
    var back by remember(card.id) { mutableStateOf(card.back) }
    var hint1 by remember(card.id) { mutableStateOf(card.hint1) }
    var hint2 by remember(card.id) { mutableStateOf(card.hint2) }
    var explanation by remember(card.id) { mutableStateOf(card.explanation) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.card_edit_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = front,
                    onValueChange = { front = it },
                    label = { Text(stringResource(R.string.add_front)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = back,
                    onValueChange = { back = it },
                    label = { Text(stringResource(R.string.add_back)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (back.isBlank()) {
                    Text(
                        text = stringResource(R.string.card_needs_answer),
                        style = MaterialTheme.typography.labelSmall,
                        color = Amber
                    )
                }
                OutlinedTextField(
                    value = hint1,
                    onValueChange = { hint1 = it },
                    label = { Text(stringResource(R.string.add_hint1)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = hint2,
                    onValueChange = { hint2 = it },
                    label = { Text(stringResource(R.string.add_hint2)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text(stringResource(R.string.add_explanation)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(front, back, hint1, hint2, explanation) },
                enabled = front.isNotBlank()
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun LimitStepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 5).coerceIn(range)) }) { Text("−5") }
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        TextButton(onClick = { onChange((value + 5).coerceIn(range)) }) { Text("+5") }
    }
}
