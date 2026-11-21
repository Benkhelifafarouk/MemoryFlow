package com.memoryflow.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.memoryflow.app.R
import com.memoryflow.app.ui.screens.AddScreen
import com.memoryflow.app.ui.screens.DeckDetailScreen
import com.memoryflow.app.ui.screens.DecksScreen
import com.memoryflow.app.ui.screens.HomeScreen
import com.memoryflow.app.ui.screens.ReviewScreen
import com.memoryflow.app.ui.screens.SelfCheckScreen
import com.memoryflow.app.ui.screens.SettingsScreen
import com.memoryflow.app.ui.screens.StatsScreen

sealed interface Screen {
    data object Home : Screen
    data object Decks : Screen
    data object Add : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object SelfCheck : Screen
    data class DeckDetail(val deckId: Long) : Screen
    data class Review(val deckId: Long?, val weakOnly: Boolean = false) : Screen
}

private data class Tab(val screen: Screen, val icon: ImageVector, val labelRes: Int)

@Composable
fun AppRoot(
    vm: AppViewModel,
    state: AppUiState,
    openReviewOnStart: Boolean,
    reviewSignal: Int,
    onLanguageChanged: () -> Unit
) {
    var stack by remember { mutableStateOf<List<Screen>>(listOf(Screen.Home)) }
    var addScreenDeckId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbar.showSnackbar(message)
            vm.consumeMessage()
        }
    }
    LaunchedEffect(reviewSignal) {
        if (reviewSignal > 0) stack = stack + Screen.Review(null)
    }
    LaunchedEffect(openReviewOnStart) {
        if (openReviewOnStart) stack = listOf(Screen.Home, Screen.Review(null))
    }

    val tabs = listOf(
        Tab(Screen.Home, Icons.Filled.Home, R.string.nav_home),
        Tab(Screen.Decks, Icons.AutoMirrored.Filled.List, R.string.nav_decks),
        Tab(Screen.Add, Icons.Filled.Add, R.string.nav_add),
        Tab(Screen.Stats, Icons.Filled.DateRange, R.string.nav_stats),
        Tab(Screen.Settings, Icons.Filled.Settings, R.string.nav_settings)
    )

    val current = stack.last()
    val showBottomBar = tabs.any { it.screen == current }

    BackHandler(enabled = stack.size > 1) {
        stack = stack.dropLast(1)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.screen,
                            onClick = {
                                stack = when (tab.screen) {
                                    Screen.Home -> listOf(Screen.Home)
                                    else -> listOf(Screen.Home, tab.screen)
                                }
                                if (tab.screen == Screen.Add) addScreenDeckId = null
                            },
                            icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val screen = current) {
                Screen.Home -> HomeScreen(
                    state = state,
                    onReviewNow = { stack = stack + Screen.Review(null) },
                    onReviewWeak = { stack = stack + Screen.Review(null, weakOnly = true) },
                    onOpenDecks = { stack = listOf(Screen.Home, Screen.Decks) },
                    onOpenAdd = { addScreenDeckId = null; stack = listOf(Screen.Home, Screen.Add) },
                    onToggleLearning = { vm.setLearningMode(!state.settings.learningMode) },
                    onToggleDeck = { deck, active -> vm.toggleDeck(deck, active) }
                )

                Screen.Decks -> DecksScreen(
                    state = state,
                    onToggleDeck = { deck, active -> vm.toggleDeck(deck, active) },
                    onToggleAll = { vm.toggleAll(it) },
                    onOpenDeck = { stack = stack + Screen.DeckDetail(it) },
                    onCreateDeck = { name, description, category ->
                        vm.createDeck(name, description, category, false) { id ->
                            stack = stack.dropLast(1) + Screen.DeckDetail(id)
                        }
                    }
                )

                Screen.Add -> AddScreen(
                    state = state,
                    initialDeckId = addScreenDeckId,
                    onSave = { deckId, front, back, h1, h2, explanation, alt, type, again ->
                        vm.addCard(deckId, front, back, h1, h2, explanation, alt, type) {
                            if (!again) stack = listOf(Screen.Home, Screen.Decks)
                        }
                    },
                    onBulkImport = { deckId, text -> vm.addBulk(deckId, text) { } },
                    onPickFile = { uri, deckId -> vm.previewFile(uri, deckId) },
                    onRecountImport = { deckId -> vm.recountImport(deckId) },
                    onConfirmImport = { deckId -> vm.confirmImport(deckId) },
                    onCancelImport = { vm.cancelImport() },
                    onCreateDeck = { stack = listOf(Screen.Home, Screen.Decks) }
                )

                Screen.Stats -> StatsScreen(state = state)
                Screen.Settings -> SettingsScreen(
                    state = state,
                    vm = vm,
                    onLanguageChanged = onLanguageChanged,
                    onOpenSelfCheck = { stack = stack + Screen.SelfCheck }
                )

                Screen.SelfCheck -> SelfCheckScreen(
                    state = state,
                    onRun = { vm.runSelfCheck() },
                    onBack = { stack = stack.dropLast(1) }
                )

                is Screen.DeckDetail -> DeckDetailScreen(
                    deckId = screen.deckId,
                    state = state,
                    vm = vm,
                    onBack = { stack = stack.dropLast(1) },
                    onAddCard = { deckId -> addScreenDeckId = deckId; stack = stack + Screen.Add },
                    onReview = { stack = stack + Screen.Review(screen.deckId) },
                    onDeleted = { stack = listOf(Screen.Home, Screen.Decks) }
                )

                is Screen.Review -> ReviewScreen(
                    deckId = screen.deckId,
                    weakOnly = screen.weakOnly,
                    state = state,
                    vm = vm,
                    onBack = { stack = stack.dropLast(1) }
                )
            }
        }
    }
}
