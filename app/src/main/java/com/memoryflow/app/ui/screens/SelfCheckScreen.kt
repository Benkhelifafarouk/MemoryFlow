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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.diagnostics.CheckResult
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.Pill
import com.memoryflow.app.ui.theme.Amber
import com.memoryflow.app.ui.theme.Mint

@Composable
fun SelfCheckScreen(state: AppUiState, onRun: () -> Unit, onBack: () -> Unit) {
    val passed = state.checks.count { it.passed && !it.manual }
    val failed = state.checks.count { !it.passed && !it.manual }
    val manual = state.checks.count { it.manual }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Text(
                    text = stringResource(R.string.selfcheck_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.selfcheck_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onRun, enabled = !state.checking) {
                    Text(
                        if (state.checking) stringResource(R.string.selfcheck_running)
                        else stringResource(R.string.selfcheck_run)
                    )
                }
                if (state.checks.isNotEmpty()) {
                    Pill(text = stringResource(R.string.selfcheck_passed, passed), color = Mint)
                    Pill(text = stringResource(R.string.selfcheck_failed, failed), color = MaterialTheme.colorScheme.error)
                    Pill(text = stringResource(R.string.selfcheck_manual, manual), color = Amber)
                }
            }
        }

        itemsIndexed(state.checks) { index, result ->
            CheckRow(index = index + 1, result = result)
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CheckRow(index: Int, result: CheckResult) {
    val color = when {
        result.manual -> Amber
        result.passed -> Mint
        else -> MaterialTheme.colorScheme.error
    }
    GlowCard(active = result.passed && !result.manual, accent = color) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$index.",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.padding(4.dp))
            Text(
                text = result.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            Pill(
                text = when {
                    result.manual -> stringResource(R.string.selfcheck_manual_label)
                    result.passed -> stringResource(R.string.selfcheck_pass)
                    else -> stringResource(R.string.selfcheck_fail)
                },
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = result.detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
