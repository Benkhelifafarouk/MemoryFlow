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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.memoryflow.app.R
import com.memoryflow.app.data.SettingsStore
import com.memoryflow.app.ui.AppUiState
import com.memoryflow.app.ui.AppViewModel
import com.memoryflow.app.ui.components.GlowCard
import com.memoryflow.app.ui.components.ScreenHeader
import com.memoryflow.app.ui.components.SectionTitle
import com.memoryflow.app.ui.theme.Mint

@Composable
fun SettingsScreen(
    state: AppUiState,
    vm: AppViewModel,
    onLanguageChanged: () -> Unit,
    onOpenSelfCheck: () -> Unit
) {
    val settings = state.settings
    val csvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) vm.exportCsv(uri) }
    val xlsxLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    ) { uri -> if (uri != null) vm.exportXlsx(uri) }
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) vm.backupJson(uri) }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) vm.restoreJson(uri) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { ScreenHeader(title = stringResource(R.string.settings_title), subtitle = stringResource(R.string.settings_subtitle)) }

        // ---- Learning ----
        item { SectionTitle(stringResource(R.string.settings_section_learning)) }
        item {
            GlowCard(active = settings.learningMode, accent = Mint) {
                SwitchRow(
                    title = stringResource(R.string.settings_learning_mode),
                    subtitle = stringResource(R.string.settings_learning_mode_desc),
                    checked = settings.learningMode
                ) { vm.setLearningMode(it) }
                SettingsSlider(
                    title = stringResource(R.string.settings_retention),
                    subtitle = stringResource(R.string.settings_retention_desc),
                    value = settings.desiredRetention,
                    range = 0.70f..0.97f,
                    steps = 25,
                    format = { "${(it * 100).toInt()}%" }
                ) { vm.updateSettings { s -> s.desiredRetention = it } }
                ValueStepper(
                    title = stringResource(R.string.settings_new_per_day),
                    value = settings.newPerDayGlobal,
                    step = 5,
                    range = 0..100
                ) { vm.updateSettings { s -> s.newPerDayGlobal = it } }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_frequency),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        R.string.settings_freq_calm,
                        R.string.settings_freq_normal,
                        R.string.settings_freq_intense
                    ).forEachIndexed { index, label ->
                        FilterChip(
                            selected = settings.frequency == index,
                            onClick = { vm.updateSettings { s -> s.frequency = index } },
                            label = { Text(stringResource(label)) }
                        )
                    }
                }
            }
        }

        // ---- Notifications ----
        item { SectionTitle(stringResource(R.string.settings_section_notifications)) }
        item {
            GlowCard(active = settings.notificationsEnabled, accent = MaterialTheme.colorScheme.primary) {
                SwitchRow(
                    title = stringResource(R.string.settings_notifications),
                    subtitle = stringResource(R.string.settings_notifications_desc),
                    checked = settings.notificationsEnabled
                ) { vm.updateSettings { s -> s.notificationsEnabled = it } }
                SwitchRow(
                    title = stringResource(R.string.settings_scheduled),
                    subtitle = stringResource(R.string.settings_scheduled_desc),
                    checked = settings.scheduledEnabled
                ) { vm.updateSettings { s -> s.scheduledEnabled = it } }
                SwitchRow(
                    title = stringResource(R.string.settings_random),
                    subtitle = stringResource(R.string.settings_random_desc),
                    checked = settings.randomEnabled
                ) { vm.updateSettings { s -> s.randomEnabled = it } }
                SwitchRow(
                    title = stringResource(R.string.settings_sound),
                    subtitle = stringResource(R.string.settings_sound_desc),
                    checked = settings.soundEnabled
                ) { vm.updateSettings { s -> s.soundEnabled = it } }
                SettingsSlider(
                    title = stringResource(R.string.settings_daily_notifications),
                    subtitle = stringResource(R.string.settings_daily_notifications_desc),
                    value = settings.dailyNotificationLimit.toFloat(),
                    range = 1f..40f,
                    steps = 38,
                    format = { it.toInt().toString() }
                ) { vm.updateSettings { s -> s.dailyNotificationLimit = it.toInt() } }
                SettingsSlider(
                    title = stringResource(R.string.settings_max_questions),
                    subtitle = stringResource(R.string.settings_max_questions_desc),
                    value = settings.maxQuestionsPerDay.toFloat(),
                    range = 5f..100f,
                    steps = 18,
                    format = { it.toInt().toString() }
                ) { vm.updateSettings { s -> s.maxQuestionsPerDay = it.toInt() } }
            }
        }

        // ---- Times ----
        item { SectionTitle(stringResource(R.string.settings_section_times)) }
        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.secondary) {
                Text(
                    text = stringResource(R.string.settings_window_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                HourSlider(
                    label = stringResource(R.string.settings_window_start),
                    hour = settings.windowStartHour
                ) { vm.updateSettings { s -> s.windowStartHour = it } }
                HourSlider(
                    label = stringResource(R.string.settings_window_end),
                    hour = settings.windowEndHour
                ) { vm.updateSettings { s -> s.windowEndHour = it } }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.settings_quiet_title),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                HourSlider(
                    label = stringResource(R.string.settings_quiet_start),
                    hour = settings.quietStartHour
                ) { vm.updateSettings { s -> s.quietStartHour = it } }
                HourSlider(
                    label = stringResource(R.string.settings_quiet_end),
                    hour = settings.quietEndHour
                ) { vm.updateSettings { s -> s.quietEndHour = it } }
            }
        }

        // ---- Language & theme ----
        item { SectionTitle(stringResource(R.string.settings_section_language)) }
        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.tertiary) {
                Text(
                    text = stringResource(R.string.settings_language),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("ar" to "العربية", "en" to "English", "de" to "Deutsch").forEach { pair ->
                        FilterChip(
                            selected = settings.language == pair.first,
                            onClick = {
                                vm.updateSettings { s -> s.language = pair.first }
                                onLanguageChanged()
                            },
                            label = { Text(pair.second) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_theme),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        SettingsStore.THEME_SYSTEM to R.string.theme_system,
                        SettingsStore.THEME_LIGHT to R.string.theme_light,
                        SettingsStore.THEME_DARK to R.string.theme_dark
                    ).forEach { pair ->
                        FilterChip(
                            selected = settings.themeMode == pair.first,
                            onClick = { vm.updateSettings { s -> s.themeMode = pair.first } },
                            label = { Text(stringResource(pair.second)) }
                        )
                    }
                }
            }
        }

        // ---- Data ----
        item { SectionTitle(stringResource(R.string.settings_section_data)) }
        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.primary) {
                Text(
                    text = stringResource(R.string.settings_data_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { csvLauncher.launch("memory-flow-cards.csv") }) {
                        Text(stringResource(R.string.settings_export_csv))
                    }
                    OutlinedButton(onClick = { xlsxLauncher.launch("memory-flow-cards.xlsx") }) {
                        Text(stringResource(R.string.settings_export_xlsx))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { backupLauncher.launch("memory-flow-backup.json") }) {
                        Text(stringResource(R.string.settings_backup))
                    }
                    TextButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*")) }) {
                        Text(stringResource(R.string.settings_restore))
                    }
                }
            }
        }

        // ---- Diagnostics ----
        item { SectionTitle(stringResource(R.string.settings_section_diagnostics)) }
        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.secondary) {
                Text(
                    text = stringResource(R.string.settings_test_notification_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.testNotification() }) {
                        Text(stringResource(R.string.settings_test_notification))
                    }
                    OutlinedButton(onClick = { vm.restartScheduler() }) {
                        Text(stringResource(R.string.settings_rearm))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.settings_self_check_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSelfCheck) {
                    Text(stringResource(R.string.settings_self_check))
                }
            }
        }

        item {
            GlowCard(active = false, accent = MaterialTheme.colorScheme.outline) {
                Text(
                    text = stringResource(R.string.settings_about),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_about_text),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    format: (Float) -> String,
    onCommit: (Float) -> Unit
) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = format(local),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local) },
            valueRange = range,
            steps = steps
        )
    }
}

@Composable
private fun HourSlider(label: String, hour: Int, onCommit: (Int) -> Unit) {
    var local by remember(hour) { mutableFloatStateOf(hour.toFloat()) }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                text = "%02d:00".format(local.toInt()),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onCommit(local.toInt()) },
            valueRange = 0f..23f,
            steps = 22
        )
    }
}

@Composable
private fun ValueStepper(title: String, value: Int, step: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(text = title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceIn(range)) }) { Text("−") }
        Text(text = value.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        TextButton(onClick = { onChange((value + step).coerceIn(range)) }) { Text("+") }
    }
}
