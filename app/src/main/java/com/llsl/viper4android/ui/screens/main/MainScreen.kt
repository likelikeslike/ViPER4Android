package com.llsl.viper4android.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.ui.screens.debug.DebugLogDialog
import com.llsl.viper4android.ui.screens.preset.PresetDialog
import com.llsl.viper4android.ui.screens.settings.SettingsDialog
import com.llsl.viper4android.ui.screens.status.DriverStatusDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presetList.collectAsStateWithLifecycle()
    val driverStatus by viewModel.driverStatus.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStartEnabled.collectAsStateWithLifecycle()
    val aidlMode by viewModel.aidlModeEnabled.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugModeEnabled.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showDriverStatusDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }
    var debugLogClearTime by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val appVersionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

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
            onDismiss = { showPresetDialog = false }
        )
    }

    if (showDriverStatusDialog) {
        LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
        DriverStatusDialog(
            driverStatus = driverStatus,
            onDismiss = { showDriverStatusDialog = false }
        )
    }

    if (showDebugLog) {
        DebugLogDialog(
            clearTimestamp = debugLogClearTime,
            onClear = { debugLogClearTime = System.currentTimeMillis() },
            onDisableDebug = {
                viewModel.disableDebugMode()
                showDebugLog = false
            },
            onDismiss = { showDebugLog = false }
        )
    }

    val importSuccessStr = stringResource(R.string.import_success)
    val importFailedStr = stringResource(R.string.import_failed)
    val importPresetLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val success = viewModel.importPresetFile(uri)
            val msg = if (success) importSuccessStr else importFailedStr
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val importKernelLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val success = viewModel.importKernel(uri)
            val msg = if (success) importSuccessStr else importFailedStr
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val importVdcLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val success = viewModel.importVdc(uri)
            val msg = if (success) importSuccessStr else importFailedStr
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    if (showSettingsDialog) {
        LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
        SettingsDialog(
            autoStartEnabled = autoStart,
            aidlModeEnabled = aidlMode,
            driverStatus = driverStatus,
            appVersionName = appVersionName,
            onAutoStartChanged = viewModel::toggleAutoStart,
            onAidlModeChanged = viewModel::toggleAidlMode,
            onImportPreset = { importPresetLauncher.launch(arrayOf("application/json", "*/*")) },
            onImportKernel = {
                importKernelLauncher.launch(
                    arrayOf(
                        "audio/*",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            onDebugUnlocked = viewModel::enableDebugMode,
            onImportVdc = { importVdcLauncher.launch(arrayOf("*/*")) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    val selectedTab = if (state.fxType == ViperParams.FX_TYPE_SPEAKER) 1 else 0
    val isSpkMode = selectedTab == 1

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    if (debugMode) {
                        IconButton(onClick = { showDebugLog = true }) {
                            Icon(
                                Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.debug_log_title)
                            )
                        }
                    }
                    IconButton(onClick = { showDriverStatusDialog = true }) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(R.string.menu_driver_status)
                        )
                    }
                    IconButton(onClick = { showPresetDialog = true }) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = stringResource(R.string.menu_presets)
                        )
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.menu_settings)
                        )
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { viewModel.setFxType(ViperParams.FX_TYPE_HEADPHONE) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 0) Icons.Filled.Headphones else Icons.Outlined.Headphones,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_headphone)) }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { viewModel.setFxType(ViperParams.FX_TYPE_SPEAKER) },
                    icon = {
                        Icon(
                            imageVector = if (selectedTab == 1) Icons.Filled.Speaker else Icons.Outlined.Speaker,
                            contentDescription = null
                        )
                    },
                    label = { Text(stringResource(R.string.tab_speaker)) }
                )
            }
        }
    ) { paddingValues ->
        EffectList(
            state = state,
            viewModel = viewModel,
            isSpkMode = isSpkMode,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
private fun EffectList(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { MasterLimiterSection(state, viewModel, isSpkMode) }
        item { PlaybackGainSection(state, viewModel, isSpkMode) }
        item { FetCompressorSection(state, viewModel, isSpkMode) }
        item { DdcSection(state, viewModel, isSpkMode) }
        item { SpectrumExtensionSection(state, viewModel, isSpkMode) }
        item { EqualizerSection(state, viewModel, isSpkMode) }
        item { ConvolverSection(state, viewModel, isSpkMode) }
        item { FieldSurroundSection(state, viewModel, isSpkMode) }
        item { DiffSurroundSection(state, viewModel, isSpkMode) }
        item { HeadphoneSurroundSection(state, viewModel, isSpkMode) }
        item { ReverberationSection(state, viewModel, isSpkMode) }
        item { DynamicSystemSection(state, viewModel, isSpkMode) }
        item { TubeSimulatorSection(state, viewModel, isSpkMode) }
        item { ViperBassSection(state, viewModel, isSpkMode) }
        item { ViperBassMonoSection(state, viewModel, isSpkMode) }
        item { ViperClaritySection(state, viewModel, isSpkMode) }
        item { AuditoryProtectionSection(state, viewModel, isSpkMode) }
        item { AnalogXSection(state, viewModel, isSpkMode) }
        if (isSpkMode) {
            item { SpeakerOptSection(state, viewModel) }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
