package com.memoryflow.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.memoryflow.app.R
import com.memoryflow.app.data.Card
import com.memoryflow.app.data.CardType
import com.memoryflow.app.data.DayCount
import com.memoryflow.app.data.Deck
import com.memoryflow.app.data.DeckStats
import com.memoryflow.app.data.OverallStats
import com.memoryflow.app.data.Repository
import com.memoryflow.app.data.ReviewLog
import com.memoryflow.app.data.ReviewSession
import com.memoryflow.app.data.SettingsSnapshot
import com.memoryflow.app.data.SettingsStore
import com.memoryflow.app.diagnostics.CheckResult
import com.memoryflow.app.diagnostics.SelfCheck
import com.memoryflow.app.io.ImportExportManager
import com.memoryflow.app.io.ParsedCard
import com.memoryflow.app.notify.SchedulerSetup
import com.memoryflow.app.review.SessionBuilder
import com.memoryflow.app.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportPreview(
    val cards: List<ParsedCard>,
    val fileName: String,
    val deckId: Long,
    val duplicates: Int,
    val newCount: Int
) {
    val total: Int get() = cards.size
}

data class ReviewQueueData(val cards: List<Card>, val pool: List<Card>)

data class AppUiState(
    val loading: Boolean = true,
    val decks: List<Deck> = emptyList(),
    val overall: OverallStats = OverallStats(),
    val deckStats: Map<Long, DeckStats> = emptyMap(),
    val activity: List<DayCount> = emptyList(),
    val settings: SettingsSnapshot = SettingsSnapshot(),
    val pending: ReviewSession? = null,
    val recent: List<ReviewLog> = emptyList(),
    val importPreview: ImportPreview? = null,
    val message: String? = null,
    val checking: Boolean = false,
    val checks: List<CheckResult> = emptyList(),
    /** Weak words inside the currently active groups (what the weak session will drill). */
    val activeWeak: Int = 0,
    val weakPreview: List<String> = emptyList()
) {
    val activeDecks: List<Deck> get() = decks.filter { it.isActive }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = Repository.get(application)
    private val io = ImportExportManager(application)
    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = buildState()
        }
    }

    private suspend fun buildState(): AppUiState = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val decks = repo.decks()
        val stats = decks.associate { it.id to repo.deckStats(it.id, now) }
        val activeIds = decks.filter { it.isActive }.map { it.id }
        val activeWeak = decks.filter { it.isActive }.sumOf { stats[it.id]?.weak ?: 0 }
        _state.value.copy(
            loading = false,
            decks = decks,
            overall = repo.overallStats(now),
            deckStats = stats,
            activity = repo.activity(7, now),
            settings = repo.settings.snapshot(),
            pending = repo.pendingSession(),
            recent = repo.reviews(null, now - 7L * 24 * 3600 * 1000, 30),
            activeWeak = activeWeak,
            weakPreview = if (activeIds.isEmpty()) emptyList()
            else repo.weakCards(activeIds, 6).map { it.front }
        )
    }

    private fun message(text: String) {
        _state.value = _state.value.copy(message = text)
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun syncScheduler() {
        if (repo.settings.learningMode && repo.settings.notificationsEnabled) {
            SchedulerSetup.ensure(getApplication())
        } else {
            SchedulerSetup.cancel(getApplication())
        }
    }

    private fun text(id: Int, vararg args: Any): String =
        LocaleHelper.wrap(getApplication(), repo.settings.language).getString(id, *args)

    // ------------------------------------------------------------------
    // Deck actions
    // ------------------------------------------------------------------

    fun toggleDeck(deck: Deck, active: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.setDeckActive(deck.id, active)
                syncScheduler()
            }
            _state.value = buildState()
            message(if (active) text(R.string.msg_deck_activated, deck.name) else text(R.string.msg_deck_deactivated, deck.name))
        }
    }

    fun toggleAll(active: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.setAllActive(active)
                syncScheduler()
            }
            _state.value = buildState()
            message(if (active) text(R.string.msg_all_activated) else text(R.string.msg_all_deactivated))
        }
    }

    fun setLearningMode(enabled: Boolean) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.settings.learningMode = enabled
                syncScheduler()
            }
            _state.value = buildState()
            message(if (enabled) text(R.string.msg_learning_on) else text(R.string.msg_learning_paused))
        }
    }

    fun createDeck(name: String, description: String, category: String, active: Boolean, onCreated: (Long) -> Unit) {
        if (name.isBlank()) {
            message(text(R.string.msg_name_required))
            return
        }
        viewModelScope.launch {
            val id = withContext(Dispatchers.IO) {
                val newId = repo.createDeck(name, description, category, active)
                if (active) syncScheduler()
                newId
            }
            _state.value = buildState()
            message(text(R.string.msg_deck_created, name))
            onCreated(id)
        }
    }

    fun updateDeck(deck: Deck) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.saveDeck(deck) }
            _state.value = buildState()
            message(text(R.string.msg_deck_saved))
        }
    }

    fun deleteDeck(deck: Deck) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteDeck(deck.id) }
            _state.value = buildState()
            message(text(R.string.msg_deck_deleted, deck.name))
        }
    }

    fun resetDeck(deck: Deck) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.resetDeckProgress(deck.id) }
            _state.value = buildState()
            message(text(R.string.msg_deck_reset))
        }
    }

    // ------------------------------------------------------------------
    // Cards
    // ------------------------------------------------------------------

    suspend fun cardsOf(deckId: Long): List<Card> = withContext(Dispatchers.IO) { repo.cards(deckId) }

    /** One review session: the question queue plus the whole pool (used for multiple-choice distractors). */
    suspend fun reviewQueue(deckId: Long?, weakOnly: Boolean): ReviewQueueData = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val deckIds = if (deckId != null) listOf(deckId) else _state.value.activeDecks.map { it.id }
        if (deckIds.isEmpty()) return@withContext ReviewQueueData(emptyList(), emptyList())
        val pool = deckIds.flatMap { repo.cards(it) }
            .filter { card -> !card.suspended && card.back.isNotBlank() }
        val dayStart = Repository.startOfDay(now)
        val studied = deckIds.associateWith { repo.newStudiedToday(it, dayStart) }
        val session = SessionBuilder.build(
            pool = pool,
            deckNewCaps = deckIds.mapNotNull { id -> repo.deck(id)?.let { id to it.newPerDay } }.toMap(),
            newStudiedToday = studied,
            globalNewStudiedToday = studied.values.sum(),
            globalNewLimit = repo.settings.newPerDayGlobal,
            now = now,
            weakOnly = weakOnly
        )
        ReviewQueueData(session, pool)
    }

    fun addCard(
        deckId: Long,
        front: String,
        back: String,
        hint1: String,
        hint2: String,
        explanation: String,
        altAnswers: String,
        type: CardType,
        onDone: () -> Unit
    ) {
        if (front.isBlank() || back.isBlank()) {
            message(text(R.string.msg_front_back_required))
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                repo.insertCard(
                    Card(
                        deckId = deckId,
                        front = front.trim(),
                        back = back.trim(),
                        hint1 = hint1.trim(),
                        hint2 = hint2.trim(),
                        explanation = explanation.trim(),
                        altAnswers = altAnswers.trim(),
                        type = type,
                        due = now,
                        createdAt = now
                    )
                )
            }
            _state.value = buildState()
            message(text(R.string.msg_card_saved))
            onDone()
        }
    }

    fun deleteCard(card: Card, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteCard(card.id) }
            _state.value = buildState()
            message(text(R.string.msg_card_deleted))
            onDone()
        }
    }

    /** Completes or edits a card, e.g. adding the meaning of a term imported from TXT. */
    fun updateCard(
        card: Card,
        front: String,
        back: String,
        hint1: String,
        hint2: String,
        explanation: String,
        onDone: () -> Unit = {}
    ) {
        if (front.isBlank()) {
            message(text(R.string.msg_front_required))
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val stillDraft = back.isBlank()
                val suspended = if (card.back.isBlank()) stillDraft else card.suspended
                repo.updateCard(
                    card.copy(
                        front = front.trim(),
                        back = back.trim(),
                        hint1 = hint1.trim(),
                        hint2 = hint2.trim(),
                        explanation = explanation.trim(),
                        suspended = suspended
                    )
                )
            }
            _state.value = buildState()
            message(text(R.string.msg_card_updated))
            onDone()
        }
    }

    fun addBulk(deckId: Long, text: String, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val cards = com.memoryflow.app.io.CardTextParser.parseBulk(text)
            val result = withContext(Dispatchers.IO) { io.importInto(deckId, cards) }
            _state.value = buildState()
            message(text(R.string.msg_import_result, result.imported, result.duplicates))
            onDone(result.imported)
        }
    }

    // ------------------------------------------------------------------
    // Import / export
    // ------------------------------------------------------------------

    fun previewFile(uri: Uri, deckId: Long) {
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) {
                runCatching {
                    val name = io.displayName(uri) ?: "file"
                    val cards = io.readPreview(uri, name)
                    val plan = io.previewPlan(deckId, cards)
                    ImportPreview(cards, name, deckId, plan.duplicates, plan.toImport.size)
                }
            }
            preview.onSuccess { p ->
                if (p.cards.isEmpty()) {
                    message(text(R.string.msg_import_empty))
                } else {
                    _state.value = _state.value.copy(importPreview = p)
                }
            }.onFailure {
                message(
                    if (it is java.io.IOException) text(R.string.msg_import_unreadable)
                    else text(R.string.msg_import_failed, it.message ?: "")
                )
            }
        }
    }

    /** Re-counts duplicates when the user switches the target group in the preview. */
    fun recountImport(deckId: Long) {
        val preview = _state.value.importPreview ?: return
        if (preview.deckId == deckId) return
        viewModelScope.launch {
            val updated = withContext(Dispatchers.IO) {
                val plan = io.previewPlan(deckId, preview.cards)
                preview.copy(deckId = deckId, duplicates = plan.duplicates, newCount = plan.toImport.size)
            }
            _state.value = _state.value.copy(importPreview = updated)
        }
    }

    fun confirmImport(deckId: Long) {
        val preview = _state.value.importPreview ?: return
        if (preview.newCount <= 0) {
            message(text(R.string.msg_import_no_new))
            return
        }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { io.importInto(deckId, preview.cards) }
            _state.value = buildState().copy(importPreview = null)
            message(text(R.string.msg_import_result, result.imported, result.duplicates))
        }
    }

    fun cancelImport() {
        _state.value = _state.value.copy(importPreview = null)
    }

    fun exportCsv(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { io.exportCsv(uri, null) } }
            count.onSuccess { message(text(R.string.msg_export_done, it)) }
                .onFailure { message(text(R.string.msg_export_failed, it.message ?: "")) }
        }
    }

    fun exportXlsx(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { io.exportXlsx(uri, null) } }
            count.onSuccess { message(text(R.string.msg_export_done, it)) }
                .onFailure { message(text(R.string.msg_export_failed, it.message ?: "")) }
        }
    }

    fun backupJson(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { io.backupJson(uri) } }
            count.onSuccess { message(text(R.string.msg_backup_done, it)) }
                .onFailure { message(text(R.string.msg_export_failed, it.message ?: "")) }
        }
    }

    fun restoreJson(uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { runCatching { io.restoreJson(uri) } }
            count.onSuccess {
                _state.value = buildState()
                message(text(R.string.msg_restore_done, it))
            }.onFailure { message(text(R.string.msg_import_failed, it.message ?: "")) }
        }
    }

    // ------------------------------------------------------------------
    // Learning
    // ------------------------------------------------------------------

    fun grade(cardId: Long, rating: Int, answer: String, hintCount: Int) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.grade(cardId, rating, answer, hintCount, practice = false) }
            _state.value = buildState()
        }
    }

    fun testNotification() {
        SchedulerSetup.runOnce(getApplication())
        message(text(R.string.msg_test_notification))
    }

    fun updateSettings(block: (SettingsStore) -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                block(repo.settings)
                syncScheduler()
            }
            _state.value = buildState()
        }
    }

    fun restartScheduler() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { SchedulerSetup.ensure(getApplication()) }
            message(text(R.string.msg_scheduler_armed))
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    fun runSelfCheck() {
        _state.value = _state.value.copy(checking = true, checks = emptyList())
        viewModelScope.launch {
            val results = withContext(Dispatchers.IO) { SelfCheck(getApplication()).runAll() }
            _state.value = _state.value.copy(checking = false, checks = results)
        }
    }

    fun clearSelfCheck() {
        _state.value = _state.value.copy(checks = emptyList())
    }
}
