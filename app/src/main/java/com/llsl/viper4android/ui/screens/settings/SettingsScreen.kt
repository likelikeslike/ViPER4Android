package com.llsl.viper4android.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.screens.main.DriverStatus

@Composable
fun SettingsDialog(
    autoStartEnabled: Boolean,
    aidlModeEnabled: Boolean,
    globalModeEnabled: Boolean,
    driverStatus: DriverStatus,
    appVersionName: String,
    onAutoStartChanged: (Boolean) -> Unit,
    onAidlModeChanged: (Boolean) -> Unit,
    onGlobalModeChanged: (Boolean) -> Unit,
    onImportPreset: () -> Unit,
    onImportKernel: () -> Unit,
    onDebugUnlocked: () -> Unit,
    onImportVdc: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tapCount = remember { mutableIntStateOf(0) }
    val debugModeEnabledStr = stringResource(R.string.debug_mode_enabled)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.menu_settings)) },
        text = {
            Column {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_auto_start),
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartChanged
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsToggleRow(
                    label = stringResource(R.string.settings_aidl_mode),
                    checked = aidlModeEnabled,
                    onCheckedChange = onAidlModeChanged
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsToggleRow(
                    label = stringResource(R.string.settings_global_mode),
                    checked = globalModeEnabled,
                    onCheckedChange = onGlobalModeChanged
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.settings_files_section),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedButton(
                    onClick = onImportPreset,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.settings_import_preset))
                }
                OutlinedButton(
                    onClick = onImportKernel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.settings_import_kernel))
                }
                OutlinedButton(
                    onClick = onImportVdc,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                ) {
                    Text(stringResource(R.string.settings_import_vdc))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            tapCount.intValue++
                            if (tapCount.intValue >= 7) {
                                tapCount.intValue = 0
                                onDebugUnlocked()
                                Toast.makeText(
                                    context,
                                    debugModeEnabledStr,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_driver_version),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (driverStatus.installed) driverStatus.versionName else "-",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_driver_arch),
                    value = if (driverStatus.installed) driverStatus.architecture else "-"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_app_version),
                    value = appVersionName
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        }
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
