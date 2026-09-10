package com.llsl.viper4android.ui.screens.debug

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.ConfirmDialog
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.IconActionItem
import com.llsl.viper4android.ui.components.UiDimens
import com.llsl.viper4android.ui.theme.log_level_debug
import com.llsl.viper4android.ui.theme.log_level_error
import com.llsl.viper4android.ui.theme.log_level_info
import com.llsl.viper4android.ui.theme.log_level_unspecified
import com.llsl.viper4android.ui.theme.log_level_warn
import com.llsl.viper4android.ui.theme.log_source_app
import com.llsl.viper4android.ui.theme.log_source_driver
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class SourceFilter { ALL, APP, DRIVER }

private enum class LevelFilter { ALL, INFO, DEBUG, WARN, ERROR }

@Composable
fun DebugLogDialog(
    onDisableDebug: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state = remember { DebugLogState() }
    val listState = rememberLazyListState()
    var sourceFilter by remember { mutableStateOf(SourceFilter.ALL) }
    var levelFilter by remember { mutableStateOf(LevelFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var showExportConfirm by remember { mutableStateOf(false) }

    DisposableEffect(state) {
        state.start(includeFileHistory = true)
        onDispose { state.shutdown() }
    }

    val filteredEntries by remember(state) {
        derivedStateOf {
            val query = searchQuery
            state.visibleEntries.filter { entry ->
                matchesSource(entry, sourceFilter) &&
                    matchesLevel(entry, levelFilter) &&
                    (query.isEmpty() || entry.raw.contains(query, ignoreCase = true))
            }
        }
    }

    LaunchedEffect(filteredEntries.size) {
        if (filteredEntries.isNotEmpty()) {
            listState.animateScrollToItem(filteredEntries.size - 1)
        }
    }

    if (showExportConfirm) {
        val ts = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) }
        ConfirmDialog(
            title = stringResource(R.string.debug_export_title),
            body = stringResource(R.string.debug_export_confirm, "$ts.app.log", "$ts.driver.log"),
            confirmLabel = stringResource(R.string.debug_export_action),
            onConfirm = {
                exportLogs(context, state, ts)
                showExportConfirm = false
            },
            onDismiss = { showExportConfirm = false },
        )
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.debug_log_title))
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val listHeight = maxHeight * 0.6f
                Column(modifier = Modifier.fillMaxWidth()) {
                    SearchField(searchQuery) { searchQuery = it }
                    SourceFilterRow(sourceFilter) { sourceFilter = it }
                    LevelFilterRow(levelFilter) { levelFilter = it }

                    StreamErrorBanner(state.appStreamError, state.driverStreamError)

                    Text(
                        text = "${filteredEntries.size} / ${state.totalCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = UiDimens.XSmall),
                    )

                    DialogCard {
                        LazyColumn(
                            state = listState,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(listHeight)
                                    .padding(UiDimens.Large),
                        ) {
                            items(filteredEntries) { entry ->
                                Text(
                                    text = entry.raw,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = colorForEntry(entry),
                                    modifier = Modifier.padding(vertical = UiDimens.Hairline),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth()) {
                IconActionItem(
                    icon = Icons.Default.Clear,
                    label = stringResource(R.string.action_clear),
                    onClick = { state.clear() },
                    modifier = Modifier.weight(1f),
                )
                IconActionItem(
                    icon = Icons.Default.Download,
                    label = stringResource(R.string.debug_export_action),
                    onClick = { showExportConfirm = true },
                    modifier = Modifier.weight(1f),
                )
                IconActionItem(
                    icon = Icons.Default.DoNotDisturb,
                    label = stringResource(R.string.debug_disable_debug),
                    onClick = onDisableDebug,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
            }
        },
    )
}

@Composable
private fun SearchField(
    value: String,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = UiDimens.Medium),
        placeholder = { Text(stringResource(R.string.debug_search_hint)) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(UiDimens.IconSmall),
            )
        },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall,
        colors =
            OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
            ),
    )
}

@Composable
private fun SourceFilterRow(
    selected: SourceFilter,
    onSelect: (SourceFilter) -> Unit,
) {
    FilterRow {
        SourceFilter.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(sourceLabel(option), style = MaterialTheme.typography.labelSmall) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorForSource(option).copy(alpha = 0.2f),
                        selectedLabelColor = colorForSource(option),
                    ),
                modifier = Modifier.height(UiDimens.ChipHeight),
            )
        }
    }
}

@Composable
private fun LevelFilterRow(
    selected: LevelFilter,
    onSelect: (LevelFilter) -> Unit,
) {
    FilterRow {
        LevelFilter.entries.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(levelLabel(option), style = MaterialTheme.typography.labelSmall) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = colorForLevel(option).copy(alpha = 0.2f),
                        selectedLabelColor = colorForLevel(option),
                    ),
                modifier = Modifier.height(UiDimens.ChipHeight),
            )
        }
    }
}

@Composable
private fun FilterRow(content: @Composable () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = UiDimens.XSmall),
        horizontalArrangement = Arrangement.spacedBy(UiDimens.XSmall),
    ) { content() }
}

@Composable
private fun StreamErrorBanner(
    appError: String?,
    driverError: String?,
) {
    if (appError == null && driverError == null) return
    Column(modifier = Modifier.padding(bottom = UiDimens.XSmall)) {
        appError?.let {
            Text(
                text = "App log: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        driverError?.let {
            Text(
                text = "Driver log: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun sourceLabel(option: SourceFilter): String =
    when (option) {
        SourceFilter.ALL -> stringResource(R.string.debug_filter_all)
        SourceFilter.APP -> stringResource(R.string.debug_filter_app)
        SourceFilter.DRIVER -> stringResource(R.string.debug_filter_driver)
    }

@Composable
private fun levelLabel(option: LevelFilter): String =
    when (option) {
        LevelFilter.ALL -> stringResource(R.string.debug_filter_all)
        LevelFilter.INFO -> stringResource(R.string.debug_filter_info)
        LevelFilter.DEBUG -> stringResource(R.string.debug_filter_debug)
        LevelFilter.WARN -> stringResource(R.string.debug_filter_warn)
        LevelFilter.ERROR -> stringResource(R.string.debug_filter_error)
    }

private fun matchesSource(
    entry: LogEntry,
    filter: SourceFilter,
): Boolean =
    when (filter) {
        SourceFilter.ALL -> true
        SourceFilter.APP -> entry.source == LogSource.APP
        SourceFilter.DRIVER -> entry.source == LogSource.DRIVER
    }

private fun matchesLevel(
    entry: LogEntry,
    filter: LevelFilter,
): Boolean =
    when (filter) {
        LevelFilter.ALL -> true
        LevelFilter.INFO -> entry.level == LogLevel.INFO
        LevelFilter.DEBUG -> entry.level == LogLevel.DEBUG
        LevelFilter.WARN -> entry.level == LogLevel.WARN
        LevelFilter.ERROR -> entry.level == LogLevel.ERROR
    }

private fun colorForSource(source: SourceFilter): Color =
    when (source) {
        SourceFilter.ALL -> log_level_unspecified
        SourceFilter.APP -> log_source_app
        SourceFilter.DRIVER -> log_source_driver
    }

private fun colorForLevel(level: LevelFilter): Color =
    when (level) {
        LevelFilter.ALL -> log_level_unspecified
        LevelFilter.INFO -> log_level_info
        LevelFilter.DEBUG -> log_level_debug
        LevelFilter.WARN -> log_level_warn
        LevelFilter.ERROR -> log_level_error
    }

private fun colorForEntry(entry: LogEntry): Color =
    when (entry.level) {
        LogLevel.ERROR -> log_level_error
        LogLevel.WARN -> log_level_warn
        LogLevel.INFO -> log_level_info
        LogLevel.DEBUG -> log_level_debug
        LogLevel.UNKNOWN -> log_level_unspecified
    }

private fun exportLogs(
    context: Context,
    state: DebugLogState,
    timestamp: String,
) {
    val dir = File(context.getExternalFilesDir(null), "logs")
    dir.mkdirs()
    try {
        File(dir, "$timestamp.app.log").writeText(
            state.visibleEntries.filter { it.source == LogSource.APP }.joinToString("\n") { it.raw },
        )
        File(dir, "$timestamp.driver.log").writeText(
            state.visibleEntries.filter { it.source == LogSource.DRIVER }.joinToString("\n") { it.raw },
        )
        Toast.makeText(context, context.getString(R.string.debug_export_done, dir.absolutePath), Toast.LENGTH_LONG).show()
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.debug_export_failed), Toast.LENGTH_SHORT).show()
    }
}
