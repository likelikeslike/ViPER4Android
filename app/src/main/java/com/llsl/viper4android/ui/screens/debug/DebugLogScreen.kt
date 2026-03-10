package com.llsl.viper4android.ui.screens.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.FileLogger
import com.llsl.viper4android.audio.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_LOG_LINES = 500
private const val APP_PREFIX = "[App] "
private const val DRIVER_PREFIX = "[Driver] "

private enum class LogLevel(@StringRes val labelRes: Int) {
    ALL(R.string.debug_filter_all),
    INFO(R.string.debug_filter_info),
    DEBUG(R.string.debug_filter_debug),
    ERROR(R.string.debug_filter_error);

    fun matches(line: String): Boolean = when (this) {
        ALL -> true
        INFO -> line.contains("[INFO]") || line.contains(" I/")
        DEBUG -> line.contains("[DEBUG]") || line.contains(" D/")
        ERROR -> line.contains("[ERROR]") || line.contains(" E/")
    }
}

private enum class LogSource(@StringRes val labelRes: Int) {
    ALL(R.string.debug_filter_all),
    APP(R.string.debug_filter_app),
    DRIVER(R.string.debug_filter_driver);

    fun matches(line: String): Boolean = when (this) {
        ALL -> true
        APP -> line.startsWith(APP_PREFIX)
        DRIVER -> line.startsWith(DRIVER_PREFIX)
    }
}

private enum class LogCategory(@StringRes val labelRes: Int) {
    ALL(R.string.debug_filter_all),
    EFFECT(R.string.debug_filter_effect),
    DISPATCH(R.string.debug_filter_dispatch),
    CONFIG(R.string.debug_filter_config),
    COMMAND(R.string.debug_filter_command);

    fun matches(line: String): Boolean = when (this) {
        ALL -> true
        EFFECT -> line.contains(Regex("\\w+: (ON|OFF)"))
        DISPATCH -> line.contains("[Dispatch]") || line.contains("Dispatch:")
        CONFIG -> line.contains("Input ") || line.contains("Output ") ||
                line.contains("sampling") || line.contains("format") ||
                line.contains("channels") || line.contains("Config")

        COMMAND -> line.contains("handleCommand") || line.contains("EFFECT_CMD")
    }
}

@Composable
fun DebugLogDialog(
    clearTimestamp: Long,
    onClear: () -> Unit,
    onDisableDebug: () -> Unit,
    onDismiss: () -> Unit
) {
    val allLines = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var selectedLevel by remember { mutableStateOf(LogLevel.ALL) }
    var selectedSource by remember { mutableStateOf(LogSource.ALL) }
    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val filteredLines by remember {
        derivedStateOf {
            allLines.filter { line ->
                val levelMatch = selectedLevel.matches(line)
                val sourceMatch = selectedSource.matches(line)
                val categoryMatch = selectedCategory.matches(line)
                val searchMatch = searchQuery.isBlank() ||
                        line.contains(searchQuery, ignoreCase = true)
                levelMatch && sourceMatch && categoryMatch && searchMatch
            }
        }
    }

    LaunchedEffect(clearTimestamp) {
        allLines.clear()

        withContext(Dispatchers.IO) {
            val file = FileLogger.getLogFile()
            if (file != null && file.exists()) {
                try {
                    file.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            if (line.isBlank()) return@forEach
                            val tagged = APP_PREFIX + line
                            withContext(Dispatchers.Main) {
                                allLines.add(tagged)
                                while (allLines.size > MAX_LOG_LINES) {
                                    allLines.removeAt(0)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        withContext(Dispatchers.IO) {
            var proc: Process? = null
            try {
                val tsArg = if (clearTimestamp > 0L) {
                    val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                    " -T '${fmt.format(Date(clearTimestamp))}'"
                } else ""
                proc = ProcessBuilder(RootShell.getSuPath())
                    .redirectErrorStream(true)
                    .start()
                proc.outputStream.bufferedWriter().let { writer ->
                    writer.write("logcat -s ViPER4Android:* -v time$tsArg\n")
                    writer.flush()
                }
                proc.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("---------")) continue
                        if (line.startsWith("logcat ")) continue
                        val tagged = DRIVER_PREFIX + line
                        withContext(Dispatchers.Main) {
                            allLines.add(tagged)
                            while (allLines.size > MAX_LOG_LINES) {
                                allLines.removeAt(0)
                            }
                        }
                    }
                }
            } catch (_: IOException) {
            } finally {
                proc?.let {
                    try {
                        it.outputStream.close()
                    } catch (_: IOException) {
                    }
                    it.destroy()
                }
            }
        }
    }

    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.size - 1)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.debug_log_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(stringResource(R.string.debug_search_hint)) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LogSource.entries.forEach { source ->
                        FilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = {
                                Text(
                                    stringResource(source.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorForSource(source).copy(alpha = 0.2f),
                                selectedLabelColor = colorForSource(source)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LogLevel.entries.forEach { level ->
                        FilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = {
                                Text(
                                    stringResource(level.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = colorForLevel(level).copy(alpha = 0.2f),
                                selectedLabelColor = colorForLevel(level)
                            ),
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    LogCategory.entries.forEach { category ->
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = {
                                Text(
                                    stringResource(category.labelRes),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }

                Text(
                    text = "${filteredLines.size} / ${allLines.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                ) {
                    items(filteredLines) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = colorForLogLine(line),
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDisableDebug) {
                    Text(stringResource(R.string.action_disable_debug))
                }
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { FileLogger.clearLogs() }
                    }
                    onClear()
                }) {
                    Text(stringResource(R.string.action_clear))
                }
            }
        }
    )
}

private fun colorForSource(source: LogSource): Color = when (source) {
    LogSource.ALL -> Color.Unspecified
    LogSource.APP -> Color(0xFF66BB6A)
    LogSource.DRIVER -> Color(0xFFAB47BC)
}

private fun colorForLevel(level: LogLevel): Color = when (level) {
    LogLevel.ALL -> Color.Unspecified
    LogLevel.INFO -> Color(0xFF42A5F5)
    LogLevel.DEBUG -> Color.Gray
    LogLevel.ERROR -> Color(0xFFEF5350)
}

private fun colorForLogLine(line: String): Color = when {
    line.contains("[ERROR]") || line.contains(" E/") -> Color(0xFFEF5350)
    line.contains("[WARN]") || line.contains(" W/") -> Color(0xFFFFA726)
    line.contains("[INFO]") || line.contains(" I/") -> Color(0xFF42A5F5)
    line.contains("[DEBUG]") || line.contains(" D/") -> Color.Gray
    else -> Color.Unspecified
}
