package com.memoryflow.app.ui.components

import androidx.compose.ui.graphics.Color
import com.memoryflow.app.ui.theme.Amber
import com.memoryflow.app.ui.theme.Cyan
import com.memoryflow.app.ui.theme.Indigo
import com.memoryflow.app.ui.theme.Mint
import com.memoryflow.app.ui.theme.Rose
import com.memoryflow.app.ui.theme.Violet

val DeckAccents: List<Color> = listOf(Indigo, Cyan, Violet, Mint, Amber, Rose)

fun deckAccent(index: Int): Color {
    val size = DeckAccents.size
    return DeckAccents[((index % size) + size) % size]
}
