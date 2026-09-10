package com.llsl.viper4android.ui.screens.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.ui.components.ConfirmDialog
import com.llsl.viper4android.ui.components.DialogButtonRow
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.DialogEmptyState
import com.llsl.viper4android.ui.components.DialogIconActionRow
import com.llsl.viper4android.ui.components.DialogListCard
import com.llsl.viper4android.ui.components.IconActionItem
import com.llsl.viper4android.ui.components.InfoRow
import com.llsl.viper4android.ui.components.InputDialog
import com.llsl.viper4android.ui.components.NavRow
import com.llsl.viper4android.ui.components.RowDivider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PresetDialog(
    presets: List<Preset>,
    onSave: (String) -> Unit,
    onLoad: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onUpdate: (Long) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var showSaveInput by remember { mutableStateOf(false) }
    var renamingId by remember { mutableLongStateOf(-1L) }
    var renameInitialName by remember { mutableStateOf("") }
    var showClearAllConfirm by remember { mutableStateOf(false) }
    var updateTarget by remember { mutableStateOf<Preset?>(null) }
    var loadTarget by remember { mutableStateOf<Preset?>(null) }
    var deleteTarget by remember { mutableStateOf<Preset?>(null) }
    var selectedPresetId by remember { mutableLongStateOf(-1L) }

    val selectedPreset = presets.find { it.id == selectedPresetId }

    if (showSaveInput) {
        InputDialog(
            title = stringResource(R.string.preset_save_title),
            initialValue = "",
            confirmLabel = stringResource(R.string.action_save),
            placeholder = stringResource(R.string.preset_name_hint),
            onConfirm = { name ->
                onSave(name)
                showSaveInput = false
            },
            onDismiss = { showSaveInput = false },
        )
    }

    if (renamingId >= 0) {
        InputDialog(
            title = stringResource(R.string.preset_rename_title),
            initialValue = renameInitialName,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                onRename(renamingId, name)
                renamingId = -1L
            },
            onDismiss = { renamingId = -1L },
        )
    }

    if (showClearAllConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.preset_clear_all_title),
            body = stringResource(R.string.preset_clear_all_confirm, presets.size),
            confirmLabel = stringResource(R.string.preset_clear_all),
            destructive = true,
            onConfirm = {
                onClearAll()
                showClearAllConfirm = false
            },
            onDismiss = { showClearAllConfirm = false },
        )
    }

    updateTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.preset_update_title),
            body = stringResource(R.string.preset_update_confirm, target.name),
            confirmLabel = stringResource(R.string.action_update),
            onConfirm = {
                onUpdate(target.id)
                updateTarget = null
            },
            onDismiss = { updateTarget = null },
        )
    }

    loadTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.preset_load_title),
            body = stringResource(R.string.preset_load_confirm, target.name),
            confirmLabel = stringResource(R.string.action_load),
            onConfirm = {
                onLoad(target.id)
                loadTarget = null
            },
            onDismiss = { loadTarget = null },
        )
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.preset_delete_title),
            body = stringResource(R.string.preset_delete_confirm, target.name),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                onDelete(target.id)
                if (selectedPresetId == target.id) {
                    selectedPresetId = -1L
                }
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            if (selectedPreset != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { selectedPresetId = -1L }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = selectedPreset.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = {
                        renameInitialName = selectedPreset.name
                        renamingId = selectedPreset.id
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_rename),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.menu_presets))
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                }
            }
        },
        text = {
            if (selectedPreset != null) {
                PresetDetailView(
                    preset = selectedPreset,
                    onLoad = { loadTarget = selectedPreset },
                    onUpdate = { updateTarget = selectedPreset },
                    onDelete = { deleteTarget = selectedPreset },
                )
            } else {
                if (presets.isEmpty()) {
                    DialogEmptyState(text = stringResource(R.string.preset_empty))
                } else {
                    DialogListCard {
                        items(presets, key = { it.id }) { preset ->
                            PresetRow(
                                preset = preset,
                                onSelect = { selectedPresetId = preset.id },
                            )
                            if (preset.id != presets.last().id) {
                                RowDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (selectedPreset == null) {
                DialogButtonRow {
                    OutlinedButton(
                        onClick = { showClearAllConfirm = true },
                        modifier = Modifier.weight(1f),
                        enabled = presets.isNotEmpty(),
                        colors =
                            ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                    ) {
                        Text(stringResource(R.string.preset_clear_all))
                    }
                    Button(
                        onClick = { showSaveInput = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.preset_save_current))
                    }
                }
            }
        },
        dismissButton = {},
    )
}

@Composable
private fun PresetRow(
    preset: Preset,
    onSelect: () -> Unit,
) {
    NavRow(
        label = preset.name,
        subtitle = stringResource(R.string.preset_tap_for_details),
        onClick = onSelect,
    )
}

@Composable
private fun PresetDetailView(
    preset: Preset,
    onLoad: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    DialogCard {
        InfoRow(
            label = stringResource(R.string.preset_label_created),
            value = formatter.format(Date(preset.createdAt)),
        )
        RowDivider()
        InfoRow(
            label = stringResource(R.string.preset_label_updated),
            value = formatter.format(Date(preset.updatedAt)),
        )
        RowDivider()
        DialogIconActionRow {
            IconActionItem(
                icon = Icons.Default.SettingsBackupRestore,
                label = stringResource(R.string.action_load),
                onClick = onLoad,
            )
            IconActionItem(
                icon = Icons.Default.Sync,
                label = stringResource(R.string.action_update),
                onClick = onUpdate,
            )
            IconActionItem(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete,
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
