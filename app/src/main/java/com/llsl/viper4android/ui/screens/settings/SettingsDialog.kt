package com.llsl.viper4android.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.DialogProperties
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.ActionRow
import com.llsl.viper4android.ui.components.DialogCard
import com.llsl.viper4android.ui.components.DialogSectionLabel
import com.llsl.viper4android.ui.components.IndicatorInfoRow
import com.llsl.viper4android.ui.components.InfoRow
import com.llsl.viper4android.ui.components.NavRow
import com.llsl.viper4android.ui.components.RowDivider
import com.llsl.viper4android.ui.components.ToggleRow
import com.llsl.viper4android.ui.screens.main.DriverStatus
import com.llsl.viper4android.ui.theme.status_active_green

@Composable
fun SettingsDialog(
    autoStartEnabled: Boolean,
    globalModeEnabled: Boolean,
    aidlModeActive: Boolean,
    debugModeEnabled: Boolean,
    driverStatus: DriverStatus,
    appVersionName: String,
    onAutoStartChanged: (Boolean) -> Unit,
    onGlobalModeChanged: (Boolean) -> Unit,
    onOpenExcludedApps: () -> Unit,
    onImportPreset: () -> Unit,
    onImportKernel: () -> Unit,
    onDebugUnlocked: () -> Unit,
    onImportVdc: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tapCount = remember { mutableIntStateOf(0) }
    val debugModeEnabledStr = stringResource(R.string.debug_mode_enabled)
    val debugAlreadyEnabledStr = stringResource(R.string.debug_already_enabled)

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
                Text(stringResource(R.string.menu_settings))
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_close),
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                DialogSectionLabel(label = stringResource(R.string.settings_general_section))
                DialogCard {
                    ToggleRow(
                        label = stringResource(R.string.settings_auto_start),
                        subtitle = stringResource(R.string.settings_auto_start_desc),
                        checked = autoStartEnabled,
                        onCheckedChange = onAutoStartChanged,
                    )
                    RowDivider()
                    ToggleRow(
                        label = stringResource(R.string.settings_global_mode),
                        subtitle = stringResource(R.string.settings_global_mode_desc),
                        checked = globalModeEnabled,
                        onCheckedChange = onGlobalModeChanged,
                    )
                    if (!globalModeEnabled) {
                        RowDivider()
                        NavRow(
                            label = stringResource(R.string.settings_excluded_apps),
                            subtitle = stringResource(R.string.settings_excluded_apps_desc),
                            onClick = onOpenExcludedApps,
                        )
                    }
                }

                DialogSectionLabel(label = stringResource(R.string.settings_files_section))
                DialogCard {
                    ActionRow(
                        label = stringResource(R.string.settings_import_preset),
                        subtitle = stringResource(R.string.settings_import_preset_desc),
                        onClick = onImportPreset,
                    )
                    RowDivider()
                    ActionRow(
                        label = stringResource(R.string.settings_import_kernel),
                        subtitle = stringResource(R.string.settings_import_kernel_desc),
                        onClick = onImportKernel,
                    )
                    RowDivider()
                    ActionRow(
                        label = stringResource(R.string.settings_import_vdc),
                        subtitle = stringResource(R.string.settings_import_vdc_desc),
                        onClick = onImportVdc,
                    )
                }

                DialogSectionLabel(label = stringResource(R.string.settings_about_section))
                DialogCard {
                    InfoRow(
                        label = stringResource(R.string.settings_driver_version),
                        value = if (driverStatus.installed) driverStatus.versionName else "—",
                        onClick = {
                            if (debugModeEnabled) {
                                Toast.makeText(context, debugAlreadyEnabledStr, Toast.LENGTH_SHORT).show()
                            } else {
                                tapCount.intValue++
                                if (tapCount.intValue >= 7) {
                                    tapCount.intValue = 0
                                    onDebugUnlocked()
                                    Toast.makeText(context, debugModeEnabledStr, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                    RowDivider()
                    InfoRow(
                        label = stringResource(R.string.settings_driver_arch),
                        value = if (driverStatus.installed) driverStatus.architecture else "—",
                    )
                    if (aidlModeActive) {
                        RowDivider()
                        IndicatorInfoRow(
                            label = stringResource(R.string.settings_aidl_mode),
                            indicatorColor = status_active_green,
                        )
                    }
                    RowDivider()
                    InfoRow(
                        label = stringResource(R.string.settings_app_version),
                        value = appVersionName,
                        onClick = onCheckUpdate,
                    )
                }
            }
        },
        confirmButton = {},
    )
}
