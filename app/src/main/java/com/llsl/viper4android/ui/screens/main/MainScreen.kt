package com.llsl.viper4android.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SpeakerGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.ui.screens.debug.DebugLogDialog
import com.llsl.viper4android.ui.screens.device.DeviceDialog
import com.llsl.viper4android.ui.screens.preset.PresetDialog
import com.llsl.viper4android.ui.screens.settings.SettingsDialog
import com.llsl.viper4android.ui.screens.status.DriverStatusDialog
import com.llsl.viper4android.ui.theme.master_on_container_dark
import com.llsl.viper4android.ui.theme.master_on_container_light
import com.llsl.viper4android.ui.theme.master_on_onContainer_dark
import com.llsl.viper4android.ui.theme.master_on_onContainer_light
import com.llsl.viper4android.ui.theme.status_active_green
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveSettingsOnBackground()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presetList.collectAsStateWithLifecycle()
    val deviceSettings by viewModel.deviceSettingsList.collectAsStateWithLifecycle()
    val driverStatus by viewModel.driverStatus.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStartEnabled.collectAsStateWithLifecycle()
    val globalMode by viewModel.globalModeEnabled.collectAsStateWithLifecycle()
    val aidlMode by viewModel.aidlModeEnabled.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugModeEnabled.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showDriverStatusDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appVersionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
        }

    val clearAllProgressStr = stringResource(R.string.preset_clear_all_progress)
    val clearedStr = stringResource(R.string.preset_cleared)

    if (showPresetDialog) {
        PresetDialog(
            presets = presets,
            onSave = viewModel::savePreset,
            onLoad = { id ->
                viewModel.loadPreset(id)
                showPresetDialog = false
            },
            onDelete = viewModel::deletePreset,
            onRename = viewModel::renamePreset,
            onUpdate = viewModel::updatePreset,
            onClearAll = {
                viewModel.clearAllPresets(
                    notificationTitle = clearAllProgressStr,
                    successStr = clearedStr,
                ) { count ->
                    Toast.makeText(context, "$clearedStr: $count", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = { showPresetDialog = false },
        )
    }

    if (showDriverStatusDialog) {
        LaunchedEffect(Unit) {
            while (true) {
                viewModel.queryDriverStatus()
                delay(500)
            }
        }
        DriverStatusDialog(
            driverStatus = driverStatus,
            onDismiss = { showDriverStatusDialog = false },
        )
    }

    if (showDebugLog) {
        DebugLogDialog(
            onDisableDebug = {
                viewModel.disableDebugMode()
                showDebugLog = false
            },
            onDismiss = { showDebugLog = false },
        )
    }

    if (showDeviceDialog) {
        DeviceDialog(
            devices = deviceSettings,
            activeDeviceId = state.activeDeviceId,
            onRename = viewModel::renameDevice,
            onLoad = viewModel::loadDevicePreset,
            onUpdate = viewModel::saveDevicePreset,
            onDelete = viewModel::deleteDeviceSettings,
            onDismiss = { showDeviceDialog = false },
        )
    }

    val importSuccessStr = stringResource(R.string.import_success)
    val importFailedStr = stringResource(R.string.import_failed)
    val importPresetStr = stringResource(R.string.settings_import_preset)
    val importPresetLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importPresetFiles(uris, notificationTitle = importPresetStr, successStr = importSuccessStr) { success ->
                    val msg = if (success) importSuccessStr else importFailedStr
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importKernelStr = stringResource(R.string.settings_import_kernel)
    val importKernelLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importKernels(uris, notificationTitle = importKernelStr, successStr = importSuccessStr) { success ->
                    val msg = if (success) importSuccessStr else importFailedStr
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importVdcStr = stringResource(R.string.settings_import_vdc)
    val importVdcLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importVdcs(uris, notificationTitle = importVdcStr, successStr = importSuccessStr) { success ->
                    val msg = if (success) importSuccessStr else importFailedStr
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    if (showSettingsDialog) {
        LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
        SettingsDialog(
            autoStartEnabled = autoStart,
            globalModeEnabled = globalMode,
            aidlModeActive = aidlMode,
            onGlobalModeChanged = viewModel::toggleGlobalMode,
            driverStatus = driverStatus,
            appVersionName = appVersionName,
            onAutoStartChanged = viewModel::toggleAutoStart,
            onImportPreset = { importPresetLauncher.launch(arrayOf("application/json")) },
            onImportKernel = {
                importKernelLauncher.launch(
                    arrayOf(
                        "audio/*",
                        "application/octet-stream",
                        "*/*",
                    ),
                )
            },
            onDebugUnlocked = viewModel::enableDebugMode,
            onImportVdc = { importVdcLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showSettingsDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_name))
                        val deviceName = state.activeDeviceName
                        if (deviceName.isNotEmpty()) {
                            val dotColor =
                                if (state.masterEnable) {
                                    status_active_green
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Canvas(modifier = Modifier.size(5.dp)) {
                                    drawCircle(dotColor)
                                }
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = deviceName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                actions = {
                    if (debugMode) {
                        IconButton(onClick = { showDebugLog = true }) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.debug_log_title),
                            )
                        }
                    }
                    IconButton(onClick = { showDeviceDialog = true }) {
                        Icon(
                            Icons.Filled.SpeakerGroup,
                            contentDescription = stringResource(R.string.menu_devices),
                        )
                    }
                    IconButton(onClick = { showDriverStatusDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.menu_driver_status),
                        )
                    }
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = stringResource(R.string.menu_presets),
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            val masterOn = state.masterEnable
            val darkTheme = isSystemInDarkTheme()
            val containerColor =
                when {
                    !masterOn -> MaterialTheme.colorScheme.errorContainer
                    darkTheme -> master_on_container_dark
                    else -> master_on_container_light
                }
            val onContainerColor =
                when {
                    !masterOn -> MaterialTheme.colorScheme.onErrorContainer
                    darkTheme -> master_on_onContainer_dark
                    else -> master_on_onContainer_light
                }
            FloatingActionButton(
                onClick = { viewModel.setMasterEnabled(!masterOn) },
                shape = MaterialTheme.shapes.large,
                containerColor = containerColor,
                contentColor = onContainerColor,
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = stringResource(R.string.master_enable),
                )
            }
        },
        floatingActionButtonPosition = FabPosition.End,
    ) { paddingValues ->
        EffectList(
            state = state,
            viewModel = viewModel,
            modifier = Modifier.padding(paddingValues),
        )
    }
}

@Composable
private fun EffectList(
    state: EffectState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val targetAlpha = if (state.masterEnable) 1f else 0.38f
    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 200),
        label = "effectListAlpha",
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        contentPadding = PaddingValues(bottom = 88.dp),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { MasterLimiterRows(state, viewModel) }
        item { PlaybackGainSection(state, viewModel) }
        item { LUFSTargetingSection(state, viewModel) }
        item { MultibandCompressorSection(state, viewModel) }
        item { FetCompressorSection(state, viewModel) }
        item { DdcSection(state, viewModel) }
        item { SpectrumExtensionSection(state, viewModel) }
        item { EqualizerSection(state, viewModel) }
        item { DynamicEqSection(state, viewModel) }
        item { ConvolverSection(state, viewModel) }
        item { FieldSurroundSection(state, viewModel) }
        item { DiffSurroundSection(state, viewModel) }
        item { StereoImagerSection(state, viewModel) }
        item { HeadphoneSurroundSection(state, viewModel) }
        item { ReverberationSection(state, viewModel) }
        item { DynamicSystemSection(state, viewModel) }
        item { TubeSimulatorSection(state, viewModel) }
        item { PsychoacousticBassSection(state, viewModel) }
        item { ViperBassSection(state, viewModel) }
        item { ViperBassMonoSection(state, viewModel) }
        item { ViperClaritySection(state, viewModel) }
        item { AuditoryProtectionSection(state, viewModel) }
        item { AnalogXSection(state, viewModel) }
        item { SpeakerOptSection(state, viewModel) }
    }
}
