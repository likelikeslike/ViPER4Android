package com.llsl.viper4android.ui.screens.device

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.DialogProperties
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.ui.components.ConfirmDialog
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.DialogEmptyState
import com.llsl.viper4android.ui.components.DialogIconActionRow
import com.llsl.viper4android.ui.components.IconActionItem
import com.llsl.viper4android.ui.components.InfoRow
import com.llsl.viper4android.ui.components.InputDialog
import com.llsl.viper4android.ui.components.NavRow
import com.llsl.viper4android.ui.components.RowDivider
import com.llsl.viper4android.ui.theme.status_active_green
import java.text.SimpleDateFormat
import java.util.Date

@Composable
fun DeviceDialog(
    devices: List<DeviceSettings>,
    activeDeviceId: String,
    onRename: (String, String) -> Unit,
    onLoad: (String) -> Unit,
    onUpdate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var renamingDeviceId by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showUpdateConfirm by remember { mutableStateOf(false) }
    var updateTargetDevice by remember { mutableStateOf<DeviceSettings?>(null) }
    var showLoadConfirm by remember { mutableStateOf(false) }
    var loadTargetDevice by remember { mutableStateOf<DeviceSettings?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleteTargetDevice by remember { mutableStateOf<DeviceSettings?>(null) }

    val selectedDevice = selectedDeviceId?.let { id -> devices.find { it.deviceId == id } }

    if (renamingDeviceId != null) {
        InputDialog(
            title = stringResource(R.string.device_rename_title),
            initialValue = renameInput,
            confirmLabel = stringResource(R.string.action_rename),
            onConfirm = { name ->
                onRename(renamingDeviceId!!, name)
                renamingDeviceId = null
            },
            onDismiss = { renamingDeviceId = null },
        )
    }

    if (showUpdateConfirm && updateTargetDevice != null) {
        val target = updateTargetDevice!!
        ConfirmDialog(
            title = stringResource(R.string.device_update_title),
            body = stringResource(R.string.device_update_confirm, target.deviceName),
            confirmLabel = stringResource(R.string.action_update),
            onConfirm = {
                onUpdate(target.deviceId)
                showUpdateConfirm = false
            },
            onDismiss = { showUpdateConfirm = false },
        )
    }

    if (showLoadConfirm && loadTargetDevice != null) {
        val target = loadTargetDevice!!
        ConfirmDialog(
            title = stringResource(R.string.device_load_title),
            body = stringResource(R.string.device_load_confirm, target.deviceName),
            confirmLabel = stringResource(R.string.action_load),
            onConfirm = {
                onLoad(target.deviceId)
                showLoadConfirm = false
            },
            onDismiss = { showLoadConfirm = false },
        )
    }

    if (showDeleteConfirm && deleteTargetDevice != null) {
        val target = deleteTargetDevice!!
        ConfirmDialog(
            title = stringResource(R.string.device_delete_title),
            body = stringResource(R.string.device_delete_confirm, target.deviceName),
            confirmLabel = stringResource(R.string.action_delete),
            destructive = true,
            onConfirm = {
                onDelete(target.deviceId)
                selectedDeviceId = null
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }

    AlertDialog(
        modifier = Modifier.fillMaxWidth(0.9f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        onDismissRequest = onDismiss,
        title = {
            if (selectedDevice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = { selectedDeviceId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = selectedDevice.deviceName,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = {
                        renameInput = selectedDevice.deviceName
                        renamingDeviceId = selectedDevice.deviceId
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
                    Text(stringResource(R.string.device_dialog_title))
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
            if (selectedDevice != null) {
                DeviceDetailView(
                    device = selectedDevice,
                    isActive = selectedDevice.deviceId == activeDeviceId,
                    onLoad = {
                        loadTargetDevice = selectedDevice
                        showLoadConfirm = true
                    },
                    onUpdate = {
                        updateTargetDevice = selectedDevice
                        showUpdateConfirm = true
                    },
                    onDelete = {
                        deleteTargetDevice = selectedDevice
                        showDeleteConfirm = true
                    },
                )
            } else {
                DeviceListView(
                    devices = devices,
                    activeDeviceId = activeDeviceId,
                    onSelect = { selectedDeviceId = it.deviceId },
                )
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun DeviceListView(
    devices: List<DeviceSettings>,
    activeDeviceId: String,
    onSelect: (DeviceSettings) -> Unit,
) {
    if (devices.isEmpty()) {
        DialogEmptyState(text = stringResource(R.string.device_no_devices))
        return
    }

    val sorted =
        remember(devices, activeDeviceId) {
            devices.sortedWith(
                compareByDescending<DeviceSettings> { it.deviceId == activeDeviceId }
                    .thenByDescending { it.lastConnected },
            )
        }

    DialogCard {
        LazyColumn {
            itemsIndexed(sorted, key = { _, device -> device.deviceId }) { index, device ->
                DeviceNavRow(
                    device = device,
                    isActive = device.deviceId == activeDeviceId,
                    onSelect = { onSelect(device) },
                )
                if (index != sorted.lastIndex) {
                    RowDivider()
                }
            }
        }
    }
}

@Composable
private fun DeviceNavRow(
    device: DeviceSettings,
    isActive: Boolean,
    onSelect: () -> Unit,
) {
    NavRow(
        label = device.deviceName,
        subtitle =
            if (isActive) {
                stringResource(R.string.status_active)
            } else {
                DateUtils
                    .getRelativeTimeSpanString(
                        device.lastConnected,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
            },
        onClick = onSelect,
        leadingIcon = deviceIcon(device),
        statusColor = if (isActive) status_active_green else null,
    )
}

private val BUILTIN_DEVICE_IDS = setOf("speaker", "wired_headphone")

@Composable
private fun DeviceDetailView(
    device: DeviceSettings,
    isActive: Boolean,
    onLoad: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    val isBuiltIn = device.deviceId in BUILTIN_DEVICE_IDS
    val canDelete = !isActive && !isBuiltIn
    DialogCard {
        InfoRow(
            label = stringResource(R.string.device_label_type),
            value = deviceTypeName(device),
        )
        RowDivider()
        InfoRow(
            label = stringResource(R.string.device_label_address),
            value = if (device.deviceId == "speaker" || device.deviceId == "wired_headphone") "-" else device.deviceId,
        )
        RowDivider()
        InfoRow(
            label = stringResource(R.string.label_mode),
            value =
                if (device.isHeadphone) {
                    stringResource(R.string.device_mode_headphone)
                } else {
                    stringResource(R.string.device_mode_speaker)
                },
        )
        RowDivider()
        InfoRow(
            label = stringResource(R.string.device_label_last_conn),
            value =
                if (isActive) {
                    "-"
                } else {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", LocalLocale.current.platformLocale)
                        .format(Date(device.lastConnected))
                },
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
                enabled = canDelete,
                tint =
                    if (!canDelete) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.error
                    },
            )
        }
    }
}

private fun deviceIcon(device: DeviceSettings) =
    when {
        device.isHeadphone -> Icons.Default.Headphones
        else -> Icons.Default.Speaker
    }

@Composable
private fun deviceTypeName(device: DeviceSettings): String =
    when {
        device.deviceId == "speaker" -> stringResource(R.string.device_type_speaker)
        device.deviceId == "wired_headphone" -> stringResource(R.string.device_type_wired)
        device.isHeadphone -> stringResource(R.string.device_type_bluetooth)
        else -> stringResource(R.string.device_type_speaker)
    }
