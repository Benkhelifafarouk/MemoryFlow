package com.memoryflow.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.memoryflow.app.data.Deck
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.components.EmptyState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.components.PowerDial
import com.memoryflow.app.ui.components.RetentionBar
import com.memoryflow.app.ui.components.ScreenHeader
import com.memoryflow.app.ui.components.deckAccent

@Composable
fun DecksScreen(
    state: AppUiState,
    onToggleDeck: (Deck, Boolean) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onOpenDeck: (Long) -> Unit,
    onCreateDeck: (String, String, String) -> Unit
) {
    var showCreate by remember { mutableStateOf(false) }
    val activeCount = state.decks.count { it.isActive }

    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ScreenHeader(
                    title = stringResource(R.string.decks_title),
                    subtitle = stringResource(R.string.decks_active_count, activeCount, state.decks.size)
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { onToggleAll(true) }) {
                        Text(stringResource(R.string.decks_activate_all))
                    }
                    TextButton(onClick = { onToggleAll(false) }) {
                        Text(stringResource(R.string.decks_deactivate_all))
                    }
                }
            }

            if (state.decks.isEmpty()) {
                item {
                    EmptyState(
                        title = stringResource(R.string.decks_empty_title),
                        subtitle = stringResource(R.string.decks_empty_subtitle)
                    )
                }
            } else {
                items(state.decks, key = { it.id }) { deck ->
                    DeckRow(
                        deck = deck,
                        due = state.deckStats[deck.id]?.due ?: 0,
                        total = state.deckStats[deck.id]?.total ?: 0,
                        retention = state.deckStats[deck.id]?.retention ?: 0.0,
                        onToggle = { onToggleDeck(deck, it) },
                        onOpen = { onOpenDeck(deck.id) }
                    )
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text(stringResource(R.string.decks_new)) }
        )
    }

    if (showCreate) {
        CreateDeckDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, description, category ->
                showCreate = false
                onCreateDeck(name, description, category)
            }
        )
    }
}

@Composable
private fun DeckRow(
    deck: Deck,
    due: Int,
    total: Int,
    retention: Double,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit
) {
    val accent = deckAccent(deck.colorIndex)
    GlowCard(active = deck.isActive, accent = accent, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (deck.isActive) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = stringResource(R.string.status_active), color = accent)
                    }
                }
                if (deck.category.isNotBlank()) {
                    Text(
                        text = deck.category,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.decks_counts, total, due),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                RetentionBar(retention = retention, color = accent)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (retention > 0) stringResource(R.string.decks_retention, retentionPercent(retention))
                    else stringResource(R.string.decks_retention_unknown),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PowerDial(
                    active = deck.isActive,
                    accent = accent,
                    dialSize = 66.dp,
                    dialLabel = deck.name,
                    onToggle = { onToggle(!deck.isActive) }
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = if (deck.isActive) stringResource(R.string.state_on)
                    else stringResource(R.string.state_off),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (deck.isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun CreateDeckDialog(onDismiss: () -> Unit, onConfirm: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.deck_new_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.label_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text(stringResource(R.string.label_category_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.deck_new_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim(), description.trim(), category.trim()) },
                enabled = name.isNotBlank()
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

fun retentionPercent(value: Double): String = "${(value * 100).toInt()}%"
