package com.llsl.viper4android.ui.screens.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.DialogEmptyState
import com.llsl.viper4android.ui.components.DialogList
import com.llsl.viper4android.ui.components.DialogListItemRow
import com.llsl.viper4android.ui.components.RowDivider
import com.llsl.viper4android.ui.components.ToggleRow
import com.llsl.viper4android.ui.components.UiDimens
import com.llsl.viper4android.ui.screens.main.InstalledAppInfo

@Composable
fun ExcludedAppsDialog(
    excludedApps: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    loadInstalledApps: ((List<InstalledAppInfo>) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        loadInstalledApps { installedApps = it }
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
                Text(stringResource(R.string.excluded_apps_title))
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = UiDimens.Medium),
                    placeholder = { Text(stringResource(R.string.excluded_apps_search)) },
                    singleLine = true,
                )
                val apps = installedApps
                if (apps == null) {
                    DialogEmptyState(text = stringResource(R.string.excluded_apps_loading))
                } else {
                    val missing =
                        excludedApps
                            .filter { pkg -> apps.none { it.packageName == pkg } }
                            .map { InstalledAppInfo(packageName = it, label = it, isSystemApp = false) }
                    val all = (apps + missing).sortedBy { it.label.lowercase() }
                    val filtered =
                        all.filter { app ->
                            (showSystemApps || !app.isSystemApp) &&
                                (
                                    query.isBlank() ||
                                        app.label.contains(query, ignoreCase = true) ||
                                        app.packageName.contains(query, ignoreCase = true)
                                )
                        }
                    DialogCard {
                        ToggleRow(
                            label = stringResource(R.string.excluded_apps_show_system),
                            checked = showSystemApps,
                            onCheckedChange = { showSystemApps = it },
                        )
                        RowDivider()
                        DialogList {
                            itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
                                AppRow(
                                    app = app,
                                    checked = app.packageName in excludedApps,
                                    onToggle = { checked -> onToggle(app.packageName, checked) },
                                )
                                if (index != filtered.lastIndex) {
                                    RowDivider()
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun AppRow(
    app: InstalledAppInfo,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val icon: Drawable? =
        remember(app.packageName) {
            try {
                context.packageManager.getApplicationIcon(app.packageName)
            } catch (_: Exception) {
                null
            }
        }

    DialogListItemRow {
        if (icon != null) {
            Image(
                bitmap = icon.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(UiDimens.AppIcon),
            )
        }
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Checkbox(checked = checked, onCheckedChange = onToggle)
    }
}
