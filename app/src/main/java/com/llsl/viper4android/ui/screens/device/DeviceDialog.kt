package com.llsl.viper4android.ui.screens.device

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.ui.theme.status_active_green
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        AlertDialog(
            onDismissRequest = { renamingDeviceId = null },
            title = { Text(stringResource(R.string.device_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text(stringResource(R.string.device_rename_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            onRename(renamingDeviceId!!, renameInput.trim())
                            renamingDeviceId = null
                        }
                    },
                    enabled = renameInput.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingDeviceId = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
        return
    }

    if (showUpdateConfirm && updateTargetDevice != null) {
        val target = updateTargetDevice!!
        AlertDialog(
            onDismissRequest = { showUpdateConfirm = false },
            title = { Text(stringResource(R.string.device_update_title)) },
            text = {
                Text(stringResource(R.string.device_update_confirm, target.deviceName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onUpdate(target.deviceId)
                        showUpdateConfirm = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.action_update))
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showLoadConfirm && loadTargetDevice != null) {
        val target = loadTargetDevice!!
        AlertDialog(
            onDismissRequest = { showLoadConfirm = false },
            title = { Text(stringResource(R.string.device_load_title)) },
            text = {
                Text(stringResource(R.string.device_load_confirm, target.deviceName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLoad(target.deviceId)
                        showLoadConfirm = false
                    },
                ) {
                    Text(stringResource(R.string.action_load))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLoadConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm && deleteTargetDevice != null) {
        val target = deleteTargetDevice!!
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.device_delete_title)) },
            text = {
                Text(stringResource(R.string.device_delete_confirm, target.deviceName))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(target.deviceId)
                        selectedDeviceId = null
                        showDeleteConfirm = false
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    AlertDialog(
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
                Text(stringResource(R.string.device_dialog_title))
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
        confirmButton = {
            if (selectedDevice == null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@Composable
private fun DeviceListView(
    devices: List<DeviceSettings>,
    activeDeviceId: String,
    onSelect: (DeviceSettings) -> Unit,
) {
    if (devices.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.device_no_devices),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val sorted =
        remember(devices, activeDeviceId) {
            devices.sortedWith(
                compareByDescending<DeviceSettings> { it.deviceId == activeDeviceId }
                    .thenByDescending { it.lastConnected },
            )
        }

    LazyColumn {
        items(sorted, key = { it.deviceId }) { device ->
            val isActive = device.deviceId == activeDeviceId
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(device) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = deviceIcon(device),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(status_active_green)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = device.deviceName,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isActive) {
                        Text(
                            text =
                                DateUtils
                                    .getRelativeTimeSpanString(
                                        device.lastConnected,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS,
                                    ).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = { onSelect(device) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
            HorizontalDivider()
        }
    }
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
    Column {
        StatusRow(
            label = stringResource(R.string.device_label_type),
            value = deviceTypeName(device),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.device_label_address),
            value = if (device.deviceId == "speaker" || device.deviceId == "wired_headphone") "-" else device.deviceId,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.label_mode),
            value =
                if (device.isHeadphone) {
                    stringResource(R.string.device_mode_headphone)
                } else {
                    stringResource(R.string.device_mode_speaker)
                },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.device_label_last_conn),
            value =
                if (isActive) {
                    "-"
                } else {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(device.lastConnected))
                },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(
                icon = Icons.Default.SettingsBackupRestore,
                label = stringResource(R.string.action_load),
                onClick = onLoad,
            )
            ActionItem(
                icon = Icons.Default.Sync,
                label = stringResource(R.string.action_update),
                onClick = onUpdate,
            )
            ActionItem(
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

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(enabled = enabled) { onClick() }
                .padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
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
