package com.memoryflow.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.data.CardType
import com.memoryflow.app.data.Deck
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.components.ScreenHeader
import com.memoryflow.app.ui.components.SectionTitle
import com.memoryflow.app.ui.components.deckAccent

private val CARD_TYPES = listOf(
    CardType.VOCABULARY to R.string.type_vocabulary,
    CardType.QA to R.string.type_qa,
    CardType.DEFINITION to R.string.type_definition,
    CardType.MULTI_POINT to R.string.type_multi,
    CardType.SHORT to R.string.type_short,
    CardType.LONG to R.string.type_long,
    CardType.TRANSLATION to R.string.type_translation,
    CardType.FORMULA to R.string.type_formula,
    CardType.CUSTOM to R.string.type_custom
)

@Composable
fun AddScreen(
    state: AppUiState,
    initialDeckId: Long?,
    onSave: (Long, String, String, String, String, String, String, CardType, Boolean) -> Unit,
    onBulkImport: (Long, String) -> Unit,
    onPickFile: (android.net.Uri, Long) -> Unit,
    onRecountImport: (Long) -> Unit,
    onConfirmImport: (Long) -> Unit,
    onCancelImport: () -> Unit,
    onCreateDeck: () -> Unit
) {
    var selectedDeckId by remember(initialDeckId, state.decks) {
        mutableStateOf(initialDeckId ?: state.decks.firstOrNull()?.id ?: -1L)
    }
    var tab by remember { mutableStateOf(0) }
    var front by remember { mutableStateOf("") }
    var back by remember { mutableStateOf("") }
    var hint1 by remember { mutableStateOf("") }
    var hint2 by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var alternatives by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CardType.VOCABULARY) }
    var bulkText by remember { mutableStateOf("") }

    val txtPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedDeckId > 0) onPickFile(uri, selectedDeckId)
    }
    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && selectedDeckId > 0) onPickFile(uri, selectedDeckId)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ScreenHeader(
                title = stringResource(R.string.add_title),
                subtitle = stringResource(R.string.add_subtitle)
            )
        }

        if (state.decks.isEmpty()) {
            item {
                GlowCard(active = false, accent = MaterialTheme.colorScheme.primary) {
                    Text(
                        text = stringResource(R.string.add_need_deck),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onCreateDeck) { Text(stringResource(R.string.add_create_deck)) }
                }
            }
            return@LazyColumn
        }

        item {
            SectionTitle(stringResource(R.string.add_deck_label))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.decks, key = { it.id }) { deck: Deck ->
                    FilterChip(
                        selected = deck.id == selectedDeckId,
                        onClick = {
                            selectedDeckId = deck.id
                            if (state.importPreview != null) onRecountImport(deck.id)
                        },
                        label = { Text(deck.name) }
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    label = { Text(stringResource(R.string.add_tab_single)) }
                )
                FilterChip(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    label = { Text(stringResource(R.string.add_tab_bulk)) }
                )
                FilterChip(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    label = { Text(stringResource(R.string.add_tab_import)) }
                )
            }
        }

        if (tab == 0) {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CARD_TYPES, key = { it.first.name }) { pair ->
                        FilterChip(
                            selected = type == pair.first,
                            onClick = { type = pair.first },
                            label = { Text(stringResource(pair.second)) }
                        )
                    }
                }
            }
            item {
                GlowCard(active = false, accent = MaterialTheme.colorScheme.primary) {
                    OutlinedTextField(
                        value = front,
                        onValueChange = { front = it },
                        label = { Text(stringResource(R.string.add_front)) },
                        placeholder = { Text(stringResource(R.string.add_front_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = back,
                        onValueChange = { back = it },
                        label = { Text(stringResource(R.string.add_back)) },
                        placeholder = { Text(stringResource(R.string.add_back_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = alternatives,
                        onValueChange = { alternatives = it },
                        label = { Text(stringResource(R.string.add_alternatives)) },
                        placeholder = { Text(stringResource(R.string.add_alternatives_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hint1,
                        onValueChange = { hint1 = it },
                        label = { Text(stringResource(R.string.add_hint1)) },
                        placeholder = { Text(stringResource(R.string.add_hint1_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hint2,
                        onValueChange = { hint2 = it },
                        label = { Text(stringResource(R.string.add_hint2)) },
                        placeholder = { Text(stringResource(R.string.add_hint2_hint)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = explanation,
                        onValueChange = { explanation = it },
                        label = { Text(stringResource(R.string.add_explanation)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (selectedDeckId > 0) {
                                onSave(
                                    selectedDeckId, front, back, hint1, hint2, explanation, alternatives, type, true
                                )
                                front = ""
                                back = ""
                                hint1 = ""
                                hint2 = ""
                                explanation = ""
                                alternatives = ""
                            }
                        },
                        enabled = front.isNotBlank() && back.isNotBlank() && selectedDeckId > 0
                    ) { Text(stringResource(R.string.add_save)) }
                }
            }
        }

        if (tab == 1) {
            item {
                GlowCard(active = false, accent = MaterialTheme.colorScheme.secondary) {
                    Text(
                        text = stringResource(R.string.add_bulk_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bulkText,
                        onValueChange = { bulkText = it },
                        placeholder = { Text(stringResource(R.string.add_bulk_placeholder)) },
                        minLines = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (selectedDeckId > 0) {
                                    onBulkImport(selectedDeckId, bulkText)
                                    bulkText = ""
                                }
                            },
                            enabled = bulkText.isNotBlank() && selectedDeckId > 0
                        ) { Text(stringResource(R.string.add_bulk_import)) }
                    }
                }
            }
        }

        if (tab == 2) {
            item {
                GlowCard(active = false, accent = MaterialTheme.colorScheme.tertiary) {
                    Text(
                        text = stringResource(R.string.add_import_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { txtPicker.launch(arrayOf("text/plain", "text/*")) },
                            enabled = selectedDeckId > 0
                        ) { Text(stringResource(R.string.import_txt_button)) }
                        OutlinedButton(
                            onClick = {
                                sheetPicker.launch(
                                    arrayOf(
                                        "text/csv",
                                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                        "application/vnd.ms-excel",
                                        "text/*",
                                        "application/octet-stream"
                                    )
                                )
                            },
                            enabled = selectedDeckId > 0
                        ) { Text(stringResource(R.string.import_sheet_button)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.add_import_formats),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val preview = state.importPreview
            if (preview != null) {
                item {
                    GlowCard(active = true, accent = MaterialTheme.colorScheme.secondary) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.add_import_preview_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Pill(
                                text = stringResource(R.string.add_import_count, preview.total.toString()),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Text(
                            text = preview.fileName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        CountRow(
                            label = stringResource(R.string.import_found),
                            value = preview.total,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        CountRow(
                            label = stringResource(R.string.import_duplicates),
                            value = preview.duplicates,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                        CountRow(
                            label = stringResource(R.string.import_new),
                            value = preview.newCount,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.height(10.dp))
                        preview.cards.take(5).forEach { card ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = card.front,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = card.back.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (preview.total > 5) {
                            Text(
                                text = stringResource(R.string.add_import_more, (preview.total - 5).toString()),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.import_note_drafts),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { if (selectedDeckId > 0) onConfirmImport(selectedDeckId) },
                                enabled = selectedDeckId > 0 && preview.newCount > 0
                            ) { Text(stringResource(R.string.import_confirm_count, preview.newCount.toString())) }
                            TextButton(onClick = onCancelImport) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                        if (preview.newCount == 0) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.import_no_new),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CountRow(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
