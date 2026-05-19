package com.llsl.viper4android.ui.screens.main

import android.app.Application
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.ByteArrayParam
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import javax.inject.Inject

data class DriverStatus(
    val installed: Boolean = false,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
    val streaming: Boolean = false,
    val samplingRate: Int = 0,
)

data class MainUiState(
    val fxType: Int = ViperParams.FX_TYPE_HEADPHONE,
    val masterEnabled: Boolean = false,
    val spkMasterEnabled: Boolean = false,
    val out: OutputState = OutputState(),
    val agc: AgcState = AgcState(),
    val lufs: LufsState = LufsState(),
    val fet: FetState = FetState(),
    val mbc: MbcState = MbcState(),
    val ddc: DdcState = DdcState(),
    val vse: VseState = VseState(),
    val eq: EqState = EqState(),
    val dynamicEq: DynamicEqState = DynamicEqState(),
    val convolver: ConvolverState = ConvolverState(),
    val fieldSurround: FieldSurroundState = FieldSurroundState(),
    val diffSurround: DiffSurroundState = DiffSurroundState(),
    val stereoImg: StereoImagerState = StereoImagerState(),
    val vhe: VheState = VheState(),
    val reverb: ReverbState = ReverbState(),
    val dynamicSystem: DynamicSystemState = DynamicSystemState(),
    val psychoBass: PsychoBassState = PsychoBassState(),
    val bass: BassState = BassState(),
    val bassMono: BassMonoState = BassMonoState(),
    val clarity: ClarityState = ClarityState(),
    val cure: CureState = CureState(),
    val analog: AnalogXState = AnalogXState(),
    val tube: TubeSimulatorState = TubeSimulatorState(),
    val speakerCorrection: SpeakerCorrectionState = SpeakerCorrectionState(),
    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)

@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        private val repository: ViperRepository,
    ) : AndroidViewModel(application) {
        companion object {
            const val PREF_AUTO_START = "auto_start"
            const val PREF_GLOBAL_MODE = "global_mode"
            const val PREF_DEBUG_MODE = "debug_mode"
            private const val IMPORT_NOTIFICATION_ID = 2
            private const val IMPORT_CHANNEL_ID = "viper4android_service"
        }

        private val _uiState = MutableStateFlow(MainUiState())
        val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

        val presetList: StateFlow<List<Preset>> =
            repository
                .getAllPresets()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val deviceSettingsList: StateFlow<List<DeviceSettings>> =
            repository
                .getAllDeviceSettings()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        private val _driverStatus = MutableStateFlow(DriverStatus())
        val driverStatus: StateFlow<DriverStatus> = _driverStatus.asStateFlow()

        private val _vdcFileList = MutableStateFlow<List<String>>(emptyList())
        val vdcFileList: StateFlow<List<String>> = _vdcFileList.asStateFlow()

        private val _kernelFileList = MutableStateFlow<List<String>>(emptyList())
        val kernelFileList: StateFlow<List<String>> = _kernelFileList.asStateFlow()

        private val _autoStartEnabled = MutableStateFlow(false)
        val autoStartEnabled: StateFlow<Boolean> = _autoStartEnabled.asStateFlow()

        private val _aidlModeEnabled = MutableStateFlow(false)

        private val _globalModeEnabled = MutableStateFlow(false)
        val globalModeEnabled: StateFlow<Boolean> = _globalModeEnabled.asStateFlow()

        private val _debugModeEnabled = MutableStateFlow(false)
        val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

        private var viperService: ViperService? = null
        private var serviceBound = false
        private val audioOutputDetector = AudioOutputDetector(application)
        private var activeDeviceType: Int = ViperParams.FX_TYPE_HEADPHONE
        private val editingFxType: Int get() = _uiState.value.fxType
        private var currentDeviceId: String = AudioDevice.ID_SPEAKER
        private var eqPresetsJob: Job? = null
        private var spkEqPresetsJob: Job? = null
        private var dsPresetsJob: Job? = null

        private val serviceConnection =
            object : ServiceConnection {
                override fun onServiceConnected(
                    name: ComponentName?,
                    binder: IBinder?,
                ) {
                    val localBinder = binder as? ViperService.LocalBinder ?: return
                    viperService = localBinder.service
                    serviceBound = true
                    applyFullState()
                    queryDriverStatus()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    viperService = null
                    serviceBound = false
                }
            }

        init {
            loadSettingsPreferences()
            refreshFileLists()
            val initialDevice = audioOutputDetector.activeDevice.value
            currentDeviceId = initialDevice.id
            val initialFxType =
                if (initialDevice.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
            activeDeviceType = initialFxType
            viewModelScope.launch {
                loadInitialState()
                val initialDbName =
                    repository.getDeviceSettings(initialDevice.id)?.deviceName ?: initialDevice.name
                _uiState.update {
                    it.copy(
                        fxType = initialFxType,
                        activeDeviceName = initialDbName,
                        activeDeviceId = initialDevice.id,
                    )
                }
                loadEqPresetsForBandCount(_uiState.value.eq.hp.bandCount, isSpk = false)
                loadEqPresetsForBandCount(_uiState.value.eq.spk.bandCount, isSpk = true)
                loadDsPresets()
                loadDeviceSettings(initialDevice)
                ensureDeviceEntry(initialDevice)
                bindToService()
                audioOutputDetector.activeDevice.collect { device ->
                    val detectedType =
                        if (device.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
                    if (device.id != currentDeviceId) {
                        saveCurrentDeviceSettings()
                        activeDeviceType = detectedType
                        currentDeviceId = device.id
                        val dbName = repository.getDeviceSettings(device.id)?.deviceName ?: device.name
                        _uiState.update {
                            it.copy(
                                fxType = detectedType,
                                activeDeviceName = dbName,
                                activeDeviceId = device.id,
                            )
                        }
                        repository.setIntPreference(ViperRepository.PREF_FX_TYPE, detectedType)
                        ConfigChannel.setActiveFxType(detectedType)
                        loadDeviceSettings(device)
                    }
                    ensureDeviceEntry(device)
                }
            }
        }

        private fun loadDsPresets() {
            dsPresetsJob?.cancel()
            dsPresetsJob =
                viewModelScope.launch {
                    repository.getAllDsPresets().collect { presets ->
                        _uiState.update {
                            it.copy(
                                dynamicSystem =
                                    it.dynamicSystem.copy(
                                        hp = it.dynamicSystem.hp.copy(presets = presets),
                                        spk = it.dynamicSystem.spk.copy(presets = presets),
                                    ),
                            )
                        }
                    }
                }
        }

        private fun bindToService() {
            val intent = Intent(getApplication(), ViperService::class.java)
            getApplication<Application>().bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }

        override fun onCleared() {
            super.onCleared()
            runBlocking(Dispatchers.IO) { saveCurrentDeviceSettings() }
            audioOutputDetector.stop()
            if (serviceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                serviceBound = false
            }
            viperService = null
        }

        private suspend fun loadInitialState() {
            loadHeadphonePreferences()
            loadSpeakerPreferences()
        }

        private fun loadEqPresetsForBandCount(
            bandCount: Int,
            isSpk: Boolean,
        ) {
            if (isSpk) {
                spkEqPresetsJob?.cancel()
                spkEqPresetsJob =
                    viewModelScope.launch {
                        repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                            _uiState.update { it.copy(eq = it.eq.copy(spk = it.eq.spk.copy(presets = presets))) }
                        }
                    }
            } else {
                eqPresetsJob?.cancel()
                eqPresetsJob =
                    viewModelScope.launch {
                        repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                            _uiState.update { it.copy(eq = it.eq.copy(hp = it.eq.hp.copy(presets = presets))) }
                        }
                    }
            }
        }

        private suspend fun loadHeadphonePreferences() {
            val fxType =
                repository
                    .getIntPreference(ViperRepository.PREF_FX_TYPE, ViperParams.FX_TYPE_HEADPHONE)
                    .first()
            val state = loadEffectPrefs(repository, isSpk = false, state = _uiState.value)

            val eqBandCount = state.eq.hp.bandCount
            val rawEqBands = state.eq.hp.bands
            val parsedBandCount = rawEqBands.split(";").count { it.isNotBlank() }
            val eqBands =
                if (parsedBandCount != eqBandCount) {
                    List(eqBandCount) { 0f }.joinToString(";") {
                        String.format(Locale.US, "%.1f", it)
                    } + ";"
                } else {
                    rawEqBands
                }
            val eqBandsMap = mutableMapOf<Int, String>()
            for (bc in listOf(10, 15, 25, 31)) {
                val defaultBands =
                    List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
                eqBandsMap[bc] = repository.getStringPreference("eq_bands_$bc", defaultBands).first()
            }
            eqBandsMap[eqBandCount] = eqBands

            _uiState.update {
                state.copy(
                    fxType = fxType,
                    eq = state.eq.copy(hp = state.eq.hp.copy(bands = eqBands, bandsMap = eqBandsMap)),
                )
            }
        }

        private suspend fun loadSpeakerPreferences() {
            val state = loadEffectPrefs(repository, isSpk = true, state = _uiState.value)

            val spkEqBandCount = state.eq.spk.bandCount
            val rawSpkEqBands = state.eq.spk.bands
            val parsedSpkBandCount = rawSpkEqBands.split(";").count { it.isNotBlank() }
            val spkEqBands =
                if (parsedSpkBandCount != spkEqBandCount) {
                    List(spkEqBandCount) { 0f }.joinToString(";") {
                        String.format(Locale.US, "%.1f", it)
                    } + ";"
                } else {
                    rawSpkEqBands
                }
            val spkEqBandsMap = mutableMapOf<Int, String>()
            for (bc in listOf(10, 15, 25, 31)) {
                val defaultBands =
                    List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
                spkEqBandsMap[bc] =
                    repository.getStringPreference("spk_eq_bands_$bc", defaultBands).first()
            }
            spkEqBandsMap[spkEqBandCount] = spkEqBands

            _uiState.update {
                state.copy(
                    eq =
                        state.eq.copy(
                            spk = state.eq.spk.copy(bands = spkEqBands, bandsMap = spkEqBandsMap),
                        ),
                )
            }
        }

        private fun applyFullState() {
            val service = viperService ?: return
            val state = _uiState.value
            ConfigChannel.setActiveFxType(activeDeviceType)
            val isMasterOn =
                if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkMasterEnabled else state.masterEnabled
            val mode = if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.d(
                "ViewModel",
                "Dispatch: applyFullState mode=$mode master=${if (isMasterOn) "ON" else "OFF"}",
            )

            val byteArrayParams = mutableListOf<ByteArrayParam>()

            val ddcVals = state.ddc.forType(activeDeviceType)
            val ddcEnabled = ddcVals.enabled
            val ddcDevice = ddcVals.device
            FileLogger.i("ViewModel", "applyFullState: ddcEnabled=$ddcEnabled ddcDevice='$ddcDevice'")
            if (ddcEnabled && ddcDevice.isNotEmpty()) {
                val ba = prepareDdcByteArray(ddcDevice)
                FileLogger.i("ViewModel", "applyFullState: DDC byteArray=${ba?.data?.size ?: "null"}")
                ba?.let { byteArrayParams.add(it) }
            }

            val convolverVals = state.convolver.forType(activeDeviceType)
            val convolverEnabled = convolverVals.enabled
            val kernel = convolverVals.kernel
            FileLogger.i(
                "ViewModel",
                "applyFullState: convolverEnabled=$convolverEnabled kernel='$kernel'",
            )
            if (convolverEnabled && kernel.isNotEmpty()) {
                val ba = prepareConvolverByteArray(kernel)
                FileLogger.i(
                    "ViewModel",
                    "applyFullState: convolver byteArray=${ba?.data?.size ?: "null"}",
                )
                ba?.let { byteArrayParams.add(it) }
            }

            service.dispatchFullState(
                state.copy(fxType = activeDeviceType),
                isMasterOn,
                byteArrayParams.ifEmpty { null },
            )
        }

        private fun prepareDdcByteArray(name: String): ByteArrayParam? {
            return try {
                val file = File(getFilesDir("DDC"), "$name.vdc")
                FileLogger.i(
                    "ViewModel",
                    "prepareDdc: file=${file.absolutePath} exists=${file.exists()}",
                )
                if (!file.exists()) return null
                val lines = file.readLines()
                var coeffs44100: FloatArray? = null
                var coeffs48000: FloatArray? = null
                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("SR_44100:") -> {
                            coeffs44100 =
                                trimmed
                                    .removePrefix("SR_44100:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }

                        trimmed.startsWith("SR_48000:") -> {
                            coeffs48000 =
                                trimmed
                                    .removePrefix("SR_48000:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }
                    }
                }
                if (coeffs44100 == null || coeffs48000 == null) return null
                if (coeffs44100.size != coeffs48000.size) return null
                if (coeffs44100.size % 5 != 0) return null
                val arrSize = coeffs44100.size
                val naturalSize = 4 + arrSize * 4 * 2
                val wireSize =
                    when {
                        naturalSize <= 256 -> 256
                        naturalSize <= 1024 -> 1024
                        else -> return null
                    }
                val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(arrSize)
                for (f in coeffs44100) buffer.putFloat(f)
                for (f in coeffs48000) buffer.putFloat(f)
                ByteArrayParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array())
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to prepare DDC: $name", e)
                null
            }
        }

        private fun prepareConvolverByteArray(fileName: String): ByteArrayParam? {
            if (!_aidlModeEnabled.value) return null
            return try {
                val file = File(getFilesDir("Kernel"), fileName)
                FileLogger.i(
                    "ViewModel",
                    "prepareConvolver: file=${file.absolutePath} exists=${file.exists()}",
                )
                if (!file.exists()) return null
                val safeName = fileName.replace("'", "")
                val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
                val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
                RootShell.copyFile(file, kernelPath)
                FileLogger.i("ViewModel", "Kernel copied to $kernelPath (for full state)")
                val param =
                    if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                        ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL
                    } else {
                        ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
                    }
                val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
                val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(pathBytes.size)
                buffer.put(pathBytes)
                ByteArrayParam(param, buffer.array())
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to prepare kernel: $fileName", e)
                null
            }
        }

        private fun parseInts(
            s: String,
            default: Int,
            count: Int = 5,
        ): List<Int> =
            s.split(";").map { it.toIntOrNull() ?: default }.let {
                if (it.size >= count) it.take(count) else it + List(count - it.size) { default }
            }

        private fun parseInts(
            s: String,
            default: Int,
            defaults: List<Int>,
        ): List<Int> =
            s
                .split(";")
                .mapIndexed { i, v -> v.toIntOrNull() ?: defaults.getOrElse(i) { default } }
                .let {
                    if (it.size >= defaults.size) it.take(defaults.size) else it + defaults.drop(it.size)
                }

        private fun parseBools(
            s: String,
            default: Boolean = true,
            count: Int = 5,
        ): List<Boolean> =
            s.split(";").map { (it.toIntOrNull() ?: if (default) 1 else 0) != 0 }.let {
                if (it.size >= count) it.take(count) else it + List(count - it.size) { default }
            }

        private fun updateInt(
            current: String,
            index: Int,
            value: Int,
            default: Int,
            count: Int = 5,
        ): String {
            val list = parseInts(current, default, count).toMutableList()
            list[index] = value
            return list.joinToString(";")
        }

        private fun updateInt(
            current: String,
            index: Int,
            value: Int,
            default: Int,
            defaults: List<Int>,
        ): String {
            val list = parseInts(current, default, defaults).toMutableList()
            list[index] = value
            return list.joinToString(";")
        }

        private fun updateBool(
            current: String,
            index: Int,
            value: Boolean,
            default: Boolean = true,
            count: Int = 5,
        ): String {
            val list = parseBools(current, default, count).toMutableList()
            list[index] = value
            return list.joinToString(";") { if (it) "1" else "0" }
        }

        fun setMasterEnabled(enabled: Boolean) {
            FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (headphone)")
            _uiState.update { it.copy(masterEnabled = enabled) }
            viewModelScope.launch {
                repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, enabled)
            }
            if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
                viperService?.setEffectEnabled(enabled)
                if (enabled) applyFullState()
            }
        }

        fun setSpkMasterEnabled(enabled: Boolean) {
            FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (speaker)")
            _uiState.update { it.copy(spkMasterEnabled = enabled) }
            viewModelScope.launch {
                repository.setBooleanPreference("spk_${ViperRepository.PREF_MASTER_ENABLE}", enabled)
            }
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                viperService?.setEffectEnabled(enabled)
                if (enabled) applyFullState()
            }
        }

        fun setFxType(type: Int) {
            val mode = if (type == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.i("ViewModel", "Dispatch: fxType=$mode")
            _uiState.update { it.copy(fxType = type) }
            viewModelScope.launch {
                repository.setIntPreference(ViperRepository.PREF_FX_TYPE, type)
            }
            ConfigChannel.setActiveFxType(type)
            applyFullState()
        }

        fun setOutputVolume(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(out = it.out.updateType(fxType) { copy(volume = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_OUTPUT_VOLUME}" else "${ViperParams.PARAM_HP_OUTPUT_VOLUME}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_OUTPUT_VOLUME else ViperParams.PARAM_HP_OUTPUT_VOLUME
                dispatchInt(param, value)
            }
        }

        fun setChannelPan(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(out = it.out.updateType(fxType) { copy(channelPan = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}" else "${ViperParams.PARAM_HP_CHANNEL_PAN}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_CHANNEL_PAN else ViperParams.PARAM_HP_CHANNEL_PAN
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setLimiter(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(out = it.out.updateType(fxType) { copy(limiter = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_LIMITER}" else "${ViperParams.PARAM_HP_LIMITER}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param = if (isSpk) ViperParams.PARAM_SPK_LIMITER else ViperParams.PARAM_HP_LIMITER
                dispatchInt(param, value)
            }
        }

        fun setAgcEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "AGC ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(agc = it.agc.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_AGC_ENABLE}" else "${ViperParams.PARAM_HP_AGC_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.agc.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            p(ViperParams.PARAM_HP_AGC_ENABLE, ViperParams.PARAM_SPK_AGC_ENABLE),
                            intArrayOf(if (enabled) 1 else 0),
                        ),
                        ParamEntry(
                            p(ViperParams.PARAM_HP_AGC_RATIO, ViperParams.PARAM_SPK_AGC_RATIO),
                            intArrayOf(vals.strength),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_AGC_MAX_SCALER,
                                ViperParams.PARAM_SPK_AGC_MAX_SCALER,
                            ),
                            intArrayOf(vals.maxGain),
                        ),
                        ParamEntry(
                            p(ViperParams.PARAM_HP_AGC_VOLUME, ViperParams.PARAM_SPK_AGC_VOLUME),
                            intArrayOf(vals.outputThreshold),
                        ),
                    ),
                )
            }
        }

        fun setAgcStrength(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(agc = it.agc.updateType(fxType) { copy(strength = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_AGC_RATIO}" else "${ViperParams.PARAM_HP_AGC_RATIO}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_AGC_RATIO else ViperParams.PARAM_HP_AGC_RATIO
                dispatchInt(param, value)
            }
        }

        fun setAgcMaxGain(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(agc = it.agc.updateType(fxType) { copy(maxGain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_AGC_MAX_SCALER}" else "${ViperParams.PARAM_HP_AGC_MAX_SCALER}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_AGC_MAX_SCALER else ViperParams.PARAM_HP_AGC_MAX_SCALER
                dispatchInt(param, value)
            }
        }

        fun setAgcOutputThreshold(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(agc = it.agc.updateType(fxType) { copy(outputThreshold = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_AGC_VOLUME}" else "${ViperParams.PARAM_HP_AGC_VOLUME}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_AGC_VOLUME else ViperParams.PARAM_HP_AGC_VOLUME
                dispatchInt(param, value)
            }
        }

        fun setLufsEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update { it.copy(lufs = it.lufs.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_LUFS_ENABLE}" else "${ViperParams.PARAM_HP_LUFS_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.lufs.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            p(ViperParams.PARAM_HP_LUFS_ENABLE, ViperParams.PARAM_SPK_LUFS_ENABLE),
                            intArrayOf(if (enabled) 1 else 0),
                        ),
                        ParamEntry(
                            p(ViperParams.PARAM_HP_LUFS_TARGET, ViperParams.PARAM_SPK_LUFS_TARGET),
                            intArrayOf(vals.target),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_LUFS_MAX_GAIN,
                                ViperParams.PARAM_SPK_LUFS_MAX_GAIN,
                            ),
                            intArrayOf(vals.maxGain),
                        ),
                        ParamEntry(
                            p(ViperParams.PARAM_HP_LUFS_SPEED, ViperParams.PARAM_SPK_LUFS_SPEED),
                            intArrayOf(vals.speed),
                        ),
                    ),
                )
            }
        }

        fun setLufsTarget(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(lufs = it.lufs.updateType(fxType) { copy(target = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_LUFS_TARGET}" else "${ViperParams.PARAM_HP_LUFS_TARGET}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_LUFS_TARGET else ViperParams.PARAM_HP_LUFS_TARGET
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setLufsMaxGain(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(lufs = it.lufs.updateType(fxType) { copy(maxGain = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_LUFS_MAX_GAIN}" else "${ViperParams.PARAM_HP_LUFS_MAX_GAIN}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_LUFS_MAX_GAIN else ViperParams.PARAM_HP_LUFS_MAX_GAIN
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setLufsSpeed(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(lufs = it.lufs.updateType(fxType) { copy(speed = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_LUFS_SPEED}" else "${ViperParams.PARAM_HP_LUFS_SPEED}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_LUFS_SPEED else ViperParams.PARAM_HP_LUFS_SPEED
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setFetEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "FET Compressor ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.fet.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
                            ),
                            intArrayOf(if (enabled) 100 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
                            ),
                            intArrayOf(EffectDispatcher.fetThresholdToRaw(vals.threshold)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
                            ),
                            intArrayOf(vals.ratio),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
                            ),
                            intArrayOf(if (vals.autoKnee) 100 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
                            ),
                            intArrayOf(EffectDispatcher.fetKneeToRaw(vals.knee)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
                            ),
                            intArrayOf(vals.kneeMulti),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
                            ),
                            intArrayOf(if (vals.autoGain) 100 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
                            ),
                            intArrayOf(EffectDispatcher.fetGainToRaw(vals.gain)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
                            ),
                            intArrayOf(if (vals.autoAttack) 100 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
                            ),
                            intArrayOf(EffectDispatcher.fetAttackMsToRaw(vals.attack)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
                            ),
                            intArrayOf(EffectDispatcher.fetAttackMsToRaw(vals.maxAttack)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
                            ),
                            intArrayOf(if (vals.autoRelease) 100 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
                            ),
                            intArrayOf(EffectDispatcher.fetReleaseMsToRaw(vals.release)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
                            ),
                            intArrayOf(EffectDispatcher.fetReleaseMsToRaw(vals.maxRelease)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
                            ),
                            intArrayOf(EffectDispatcher.fetReleaseMsToRaw(vals.crest)),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
                            ),
                            intArrayOf(vals.adapt),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
                                ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
                            ),
                            intArrayOf(if (vals.noClip) 100 else 0),
                        ),
                    ),
                )
            }
        }

        fun setFetThreshold(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(threshold = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD else ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD
                dispatchInt(param, EffectDispatcher.fetThresholdToRaw(value))
            }
        }

        fun setFetRatio(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(ratio = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO else ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setFetAutoKnee(value: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(autoKnee = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE else ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE
            viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, if (value) 100 else 0)
        }

        fun setFetKnee(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(knee = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE else ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE
                dispatchInt(param, EffectDispatcher.fetKneeToRaw(value))
            }
        }

        fun setFetKneeMulti(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(kneeMulti = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI else ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setFetAutoGain(value: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(autoGain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN else ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN
            viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, if (value) 100 else 0)
        }

        fun setFetGain(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(gain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN else ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN
                dispatchInt(param, EffectDispatcher.fetGainToRaw(value))
            }
        }

        fun setFetAutoAttack(value: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(autoAttack = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK else ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK
            viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, if (value) 100 else 0)
        }

        fun setFetAttack(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(attack = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK else ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK
                dispatchInt(param, EffectDispatcher.fetAttackMsToRaw(value))
            }
        }

        fun setFetMaxAttack(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(maxAttack = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK else ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK
                dispatchInt(param, EffectDispatcher.fetAttackMsToRaw(value))
            }
        }

        fun setFetAutoRelease(value: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(autoRelease = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE else ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE
            viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, if (value) 100 else 0)
        }

        fun setFetRelease(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(release = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE else ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE
                dispatchInt(param, EffectDispatcher.fetReleaseMsToRaw(value))
            }
        }

        fun setFetMaxRelease(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(maxRelease = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE else ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE
                dispatchInt(param, EffectDispatcher.fetReleaseMsToRaw(value))
            }
        }

        fun setFetCrest(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(crest = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST else ViperParams.PARAM_HP_FET_COMPRESSOR_CREST
                dispatchInt(param, EffectDispatcher.fetReleaseMsToRaw(value))
            }
        }

        fun setFetAdapt(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(adapt = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT else ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setFetNoClip(value: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(fet = it.fet.updateType(fxType) { copy(noClip = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}" else "${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP else ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP
            viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, if (value) 100 else 0)
        }

        private fun removeFromString(
            s: String,
            index: Int,
            default: Int,
            count: Int,
        ): String {
            val list = parseInts(s, default, count).toMutableList()
            if (index in list.indices) list.removeAt(index)
            return list.joinToString(";")
        }

        private fun isMbcBandEnabled(band: Int): Boolean =
            parseBools(
                _uiState.value.mbc
                    .forType(editingFxType)
                    .bandEnables,
            ).getOrElse(band) { true }

        fun setMbcEnabled(enabled: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(enabled = enabled) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_MULTIBAND_COMP_ENABLE}" else "${ViperParams.PARAM_HP_MULTIBAND_COMP_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.mbc.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                val entries = mutableListOf<ParamEntry>()
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_COUNT,
                            ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_COUNT,
                        ),
                        intArrayOf(5),
                    ),
                )
                val crossovers = parseInts(vals.crossovers, 0, listOf(120, 500, 4000, 8000))
                for (i in crossovers.indices) {
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_CROSSOVER_FREQ,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_CROSSOVER_FREQ,
                            ),
                            intArrayOf(i, crossovers[i]),
                        ),
                    )
                }
                val bandEnables = parseBools(vals.bandEnables, count = 5)
                val thresholds = parseInts(vals.thresholds, -18, 5)
                val ratios = parseInts(vals.ratios, 50, 5)
                val gains = parseInts(vals.gains, 24, 5)
                val attacks = parseInts(vals.attacks, 1, 5)
                val releases = parseInts(vals.releases, 100, 5)
                val knees = parseInts(vals.knees, 0, 5)
                val autoGains = parseBools(vals.autoGains, count = 5)
                val autoAttacks = parseBools(vals.autoAttacks, count = 5)
                val autoReleases = parseBools(vals.autoReleases, count = 5)
                for (b in 0..4) {
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_ENABLE,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_ENABLE,
                            ),
                            intArrayOf(b, if (bandEnables[b]) 100 else 0),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_THRESHOLD,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_THRESHOLD,
                            ),
                            intArrayOf(b, EffectDispatcher.fetThresholdToRaw(thresholds[b])),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_RATIO,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_RATIO,
                            ),
                            intArrayOf(b, ratios[b]),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_GAIN,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_GAIN,
                            ),
                            intArrayOf(b, EffectDispatcher.fetGainToRaw(gains[b])),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_ATTACK,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_ATTACK,
                            ),
                            intArrayOf(b, EffectDispatcher.fetAttackMsToRaw(attacks[b])),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_RELEASE,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_RELEASE,
                            ),
                            intArrayOf(b, EffectDispatcher.fetReleaseMsToRaw(releases[b])),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_KNEE,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_KNEE,
                            ),
                            intArrayOf(b, EffectDispatcher.fetKneeToRaw(knees[b])),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_GAIN,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_GAIN,
                            ),
                            intArrayOf(b, if (autoGains[b]) 100 else 0),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_ATTACK,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_ATTACK,
                            ),
                            intArrayOf(b, if (autoAttacks[b]) 100 else 0),
                        ),
                    )
                    entries.add(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_RELEASE,
                                ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_RELEASE,
                            ),
                            intArrayOf(b, if (autoReleases[b]) 100 else 0),
                        ),
                    )
                }
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_MULTIBAND_COMP_ENABLE,
                            ViperParams.PARAM_SPK_MULTIBAND_COMP_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                )
                viperService?.dispatchParamsBatch(entries)
            }
        }

        fun setMbcBandEnable(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .bandEnables,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(bandEnables = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_band_enables" else "mbc_band_enables",
                    updated,
                )
            }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_ENABLE else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_ENABLE
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, if (value) 100 else 0),
                        ),
                    ),
                )
            }
        }

        fun setMbcCrossover(
            index: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .crossovers,
                    index,
                    value,
                    0,
                    listOf(120, 500, 4000, 8000),
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(crossovers = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_crossovers" else "mbc_crossovers",
                    updated,
                )
            }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_CROSSOVER_FREQ else ViperParams.PARAM_HP_MULTIBAND_COMP_CROSSOVER_FREQ
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(index, value))))
            }
        }

        fun setMbcBandThreshold(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .thresholds,
                    band,
                    value,
                    -18,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(thresholds = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_thresholds" else "mbc_thresholds",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_THRESHOLD else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_THRESHOLD
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, EffectDispatcher.fetThresholdToRaw(value)),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandRatio(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .ratios,
                    band,
                    value,
                    50,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(ratios = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_ratios" else "mbc_ratios",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_RATIO else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_RATIO
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setMbcBandGain(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .gains,
                    band,
                    value,
                    24,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(gains = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_gains" else "mbc_gains",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_GAIN else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_GAIN
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, EffectDispatcher.fetGainToRaw(value)),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandKnee(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .knees,
                    band,
                    value,
                    0,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(knees = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_knees" else "mbc_knees",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_KNEE else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_KNEE
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, EffectDispatcher.fetKneeToRaw(value)),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandKneeMulti(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .kneeMultis,
                    band,
                    value,
                    0,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(kneeMultis = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_knee_multis" else "mbc_knee_multis",
                    updated,
                )
            }
        }

        fun setMbcBandAttack(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .attacks,
                    band,
                    value,
                    1,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(attacks = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_attacks" else "mbc_attacks",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_ATTACK else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_ATTACK
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, EffectDispatcher.fetAttackMsToRaw(value)),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandMaxAttack(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .maxAttacks,
                    band,
                    value,
                    44,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(maxAttacks = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_max_attacks" else "mbc_max_attacks",
                    updated,
                )
            }
        }

        fun setMbcBandRelease(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .releases,
                    band,
                    value,
                    100,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(releases = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_releases" else "mbc_releases",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_RELEASE else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_RELEASE
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, EffectDispatcher.fetReleaseMsToRaw(value)),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandMaxRelease(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .maxReleases,
                    band,
                    value,
                    200,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(maxReleases = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_max_releases" else "mbc_max_releases",
                    updated,
                )
            }
        }

        fun setMbcBandCrest(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .crests,
                    band,
                    value,
                    100,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(crests = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_crests" else "mbc_crests",
                    updated,
                )
            }
        }

        fun setMbcBandAdapt(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.mbc
                        .forType(fxType)
                        .adapts,
                    band,
                    value,
                    50,
                    5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(adapts = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_adapts" else "mbc_adapts",
                    updated,
                )
            }
        }

        fun setMbcBandAutoKnee(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .autoKnees,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(autoKnees = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_auto_knees" else "mbc_auto_knees",
                    updated,
                )
            }
        }

        fun setMbcBandAutoGain(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .autoGains,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(autoGains = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_auto_gains" else "mbc_auto_gains",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_GAIN else ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_GAIN
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, if (value) 100 else 0),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandAutoAttack(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .autoAttacks,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(autoAttacks = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_auto_attacks" else "mbc_auto_attacks",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) {
                        ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_ATTACK
                    } else {
                        ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_ATTACK
                    }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, if (value) 100 else 0),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandAutoRelease(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .autoReleases,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(autoReleases = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_auto_releases" else "mbc_auto_releases",
                    updated,
                )
            }
            if (fxType == activeDeviceType && isMbcBandEnabled(band)) {
                val param =
                    if (isSpk) {
                        ViperParams.PARAM_SPK_MULTIBAND_COMP_BAND_AUTO_RELEASE
                    } else {
                        ViperParams.PARAM_HP_MULTIBAND_COMP_BAND_AUTO_RELEASE
                    }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            param,
                            intArrayOf(band, if (value) 100 else 0),
                        ),
                    ),
                )
            }
        }

        fun setMbcBandNoClip(
            band: Int,
            value: Boolean,
        ) {
            val fxType = editingFxType
            val updated =
                updateBool(
                    _uiState.value.mbc
                        .forType(fxType)
                        .noClips,
                    band,
                    value,
                    count = 5,
                )
            _uiState.update { it.copy(mbc = it.mbc.updateType(fxType) { copy(noClips = updated) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.setStringPreference(
                    if (isSpk) "spk_mbc_no_clips" else "mbc_no_clips",
                    updated,
                )
            }
        }

        fun setDdcEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "DDC ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(ddc = it.ddc.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DDC_ENABLE}" else "${ViperParams.PARAM_HP_DDC_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.ddc.forType(fxType)
                val enableParam =
                    if (isSpk) ViperParams.PARAM_SPK_DDC_ENABLE else ViperParams.PARAM_HP_DDC_ENABLE
                val effectiveEnabled = enabled && vals.device.isNotEmpty()
                if (effectiveEnabled) {
                    loadVdcByName(vals.device, enableParam)
                } else {
                    dispatchInt(enableParam, 0)
                }
            }
        }

        fun setDdcDevice(device: String) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "DDC ($mode) selected: $device")
            _uiState.update { it.copy(ddc = it.ddc.updateType(fxType) { copy(device = device) }) }
            val prefKey =
                if (isSpk) "spk_${ViperRepository.PREF_DDC_DEVICE}" else ViperRepository.PREF_DDC_DEVICE
            viewModelScope.launch { repository.setStringPreference(prefKey, device) }
            if (fxType == activeDeviceType) {
                val enableParam =
                    if (isSpk) ViperParams.PARAM_SPK_DDC_ENABLE else ViperParams.PARAM_HP_DDC_ENABLE
                if (device.isEmpty()) {
                    dispatchInt(enableParam, 0)
                } else {
                    val vals = _uiState.value.ddc.forType(fxType)
                    val ep = if (vals.enabled) enableParam else null
                    loadVdcByName(device, ep)
                }
            }
        }

        fun setVseEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "VSE ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(vse = it.vse.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}" else "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.vse.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
                                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
                            ),
                            intArrayOf(if (enabled) 1 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
                                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
                            ),
                            intArrayOf(vals.strength),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                            ),
                            intArrayOf(EffectDispatcher.vseExciterToRaw(vals.exciter)),
                        ),
                    ),
                )
            }
        }

        fun setVseStrength(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(vse = it.vse.updateType(fxType) { copy(strength = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}" else "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK else ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK
                dispatchInt(param, value)
            }
        }

        fun setVseExciter(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(vse = it.vse.updateType(fxType) { copy(exciter = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) {
                    "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}"
                } else {
                    "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}"
                }
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) {
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT
                    } else {
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT
                    }
                dispatchInt(param, EffectDispatcher.vseExciterToRaw(value))
            }
        }

        fun setEqEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "EQ ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(eq = it.eq.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_EQ_ENABLE}" else "${ViperParams.PARAM_HP_EQ_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_EQ_ENABLE else ViperParams.PARAM_HP_EQ_ENABLE
                viperService?.dispatchParamsBatch(
                    listOf(ParamEntry(param, intArrayOf(if (enabled) 1 else 0))),
                )
            }
        }

        fun setEqPreset(presetId: Long) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val eqVals = _uiState.value.eq.forType(fxType)
            val preset = eqVals.presets.find { it.id == presetId } ?: return
            val bands = preset.bands
            val bandCount = eqVals.bandCount
            _uiState.update { state ->
                val curVals = state.eq.forType(fxType)
                val updatedMap = curVals.bandsMap.toMutableMap().apply { put(bandCount, bands) }
                state.copy(
                    eq =
                        state.eq.updateType(fxType) {
                            copy(presetId = presetId, bands = bands, bandsMap = updatedMap)
                        },
                )
            }
            val presetPrefKey =
                if (isSpk) "spk_${ViperRepository.PREF_EQ_PRESET_ID}" else ViperRepository.PREF_EQ_PRESET_ID
            val bandLevelKey =
                if (isSpk) "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}" else "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}"
            val bandsMapKey = if (isSpk) "spk_eq_bands_$bandCount" else "eq_bands_$bandCount"
            viewModelScope.launch {
                repository.setIntPreference(presetPrefKey, presetId.toInt())
                repository.setStringPreference(bandLevelKey, bands)
                repository.setStringPreference(bandsMapKey, bands)
            }
            if (fxType == activeDeviceType) {
                val dispatchParam =
                    if (isSpk) ViperParams.PARAM_SPK_EQ_BAND_LEVEL else ViperParams.PARAM_HP_EQ_BAND_LEVEL
                dispatchEqBands(dispatchParam, bands)
            }
        }

        fun setEqBands(bands: String) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val bandCount =
                _uiState.value.eq
                    .forType(fxType)
                    .bandCount
            _uiState.update { state ->
                val curVals = state.eq.forType(fxType)
                val updatedMap = curVals.bandsMap.toMutableMap().apply { put(bandCount, bands) }
                state.copy(
                    eq =
                        state.eq.updateType(fxType) {
                            copy(bands = bands, bandsMap = updatedMap)
                        },
                )
            }
            val bandLevelKey =
                if (isSpk) "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}" else "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}"
            val bandsMapKey = if (isSpk) "spk_eq_bands_$bandCount" else "eq_bands_$bandCount"
            viewModelScope.launch {
                repository.setStringPreference(bandLevelKey, bands)
                repository.setStringPreference(bandsMapKey, bands)
            }
            if (fxType == activeDeviceType) {
                val dispatchParam =
                    if (isSpk) ViperParams.PARAM_SPK_EQ_BAND_LEVEL else ViperParams.PARAM_HP_EQ_BAND_LEVEL
                dispatchEqBands(dispatchParam, bands)
            }
        }

        fun setEqBandCount(count: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val eqVals = _uiState.value.eq.forType(fxType)
            val oldCount = eqVals.bandCount
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.d("ViewModel", "EQ ($mode) band count: $oldCount -> $count")
            val updatedMap = eqVals.bandsMap.toMutableMap().apply { put(oldCount, eqVals.bands) }
            val defaultBands =
                List(count) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            val bands = updatedMap[count] ?: defaultBands
            _uiState.update {
                it.copy(
                    eq =
                        it.eq.updateType(fxType) {
                            copy(
                                bandCount = count,
                                bands = bands,
                                presetId = null,
                                bandsMap = updatedMap,
                            )
                        },
                )
            }
            val bandCountKey =
                if (isSpk) "spk_${ViperParams.PARAM_HP_EQ_BAND_COUNT}" else "${ViperParams.PARAM_HP_EQ_BAND_COUNT}"
            val bandLevelKey =
                if (isSpk) "${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}" else "${ViperParams.PARAM_HP_EQ_BAND_LEVEL}"
            val oldBandsMapKey = if (isSpk) "spk_eq_bands_$oldCount" else "eq_bands_$oldCount"
            val newBandsMapKey = if (isSpk) "spk_eq_bands_$count" else "eq_bands_$count"
            viewModelScope.launch {
                repository.setIntPreference(bandCountKey, count)
                repository.setStringPreference(bandLevelKey, bands)
                repository.setStringPreference(oldBandsMapKey, eqVals.bands)
                repository.setStringPreference(newBandsMapKey, bands)
            }
            if (fxType == activeDeviceType) {
                val dispatchBandLevel =
                    if (isSpk) ViperParams.PARAM_SPK_EQ_BAND_LEVEL else ViperParams.PARAM_HP_EQ_BAND_LEVEL
                val dispatchBandCount =
                    if (isSpk) ViperParams.PARAM_SPK_EQ_BAND_COUNT else ViperParams.PARAM_HP_EQ_BAND_COUNT
                dispatchEqBands(dispatchBandLevel, bands, dispatchBandCount, count)
            }
            loadEqPresetsForBandCount(count, isSpk = isSpk)
        }

        fun addEqPreset(name: String) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val eqVals = _uiState.value.eq.forType(fxType)
            val preset = EqPreset(name = name, bandCount = eqVals.bandCount, bands = eqVals.bands)
            viewModelScope.launch {
                val id = repository.saveEqPreset(preset)
                _uiState.update { it.copy(eq = it.eq.updateType(fxType) { copy(presetId = id) }) }
                val prefKey =
                    if (isSpk) "spk_${ViperRepository.PREF_EQ_PRESET_ID}" else ViperRepository.PREF_EQ_PRESET_ID
                repository.setIntPreference(prefKey, id.toInt())
            }
        }

        fun deleteEqPreset(presetId: Long) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            viewModelScope.launch {
                repository.deleteEqPresetById(presetId)
                val curPresetId =
                    _uiState.value.eq
                        .forType(fxType)
                        .presetId
                if (curPresetId == presetId) {
                    _uiState.update { it.copy(eq = it.eq.updateType(fxType) { copy(presetId = null) }) }
                    val prefKey =
                        if (isSpk) "spk_${ViperRepository.PREF_EQ_PRESET_ID}" else ViperRepository.PREF_EQ_PRESET_ID
                    repository.setIntPreference(prefKey, -1)
                }
            }
        }

        fun resetEqBands() {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val bandCount =
                _uiState.value.eq
                    .forType(fxType)
                    .bandCount
            val flatBands =
                List(bandCount) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            setEqBands(flatBands)
            _uiState.update { it.copy(eq = it.eq.updateType(fxType) { copy(presetId = null) }) }
            val prefKey =
                if (isSpk) "spk_${ViperRepository.PREF_EQ_PRESET_ID}" else ViperRepository.PREF_EQ_PRESET_ID
            viewModelScope.launch { repository.setIntPreference(prefKey, -1) }
        }

        fun setDynamicEqEnabled(enabled: Boolean) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            viewModelScope.launch {
                repository.setBooleanPreference(
                    "${ViperParams.PARAM_HP_DYNAMIC_EQ_ENABLE}",
                    enabled,
                )
            }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.dynamicEq.forType(fxType)
            val count = vals.bandCount
            val entries = mutableListOf<ParamEntry>()
            val freqs = parseInts(vals.freqs, 1000, 8)
            if (count > 0 && freqs[count - 1] >= 20000) return
            val qs = parseInts(vals.qs, 150, 8)
            val gains = parseInts(vals.gains, 0, 8)
            val thresholds = parseInts(vals.thresholds, -300, 8)
            val attacks = parseInts(vals.attacks, 10, 8)
            val releases = parseInts(vals.releases, 100, 8)
            val filterTypes = parseInts(vals.filterTypes, 0, 8)
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            for (b in 0 until count) {
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FREQ,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FREQ,
                        ),
                        intArrayOf(b, freqs[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_Q,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_Q,
                        ),
                        intArrayOf(b, qs[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_GAIN,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_GAIN,
                        ),
                        intArrayOf(b, gains[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_THRESHOLD,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_THRESHOLD,
                        ),
                        intArrayOf(b, thresholds[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_ATTACK,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_ATTACK,
                        ),
                        intArrayOf(b, attacks[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_RELEASE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_RELEASE,
                        ),
                        intArrayOf(b, releases[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FILTER_TYPE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FILTER_TYPE,
                        ),
                        intArrayOf(b, filterTypes[b]),
                    ),
                )
            }
            entries.add(
                ParamEntry(
                    p(
                        ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_COUNT,
                        ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_COUNT,
                    ),
                    intArrayOf(count),
                ),
            )
            entries.add(
                ParamEntry(
                    p(
                        ViperParams.PARAM_HP_DYNAMIC_EQ_ENABLE,
                        ViperParams.PARAM_SPK_DYNAMIC_EQ_ENABLE,
                    ),
                    intArrayOf(if (enabled) 1 else 0),
                ),
            )
            viperService?.dispatchParamsBatch(entries)
        }

        fun setDynamicEqBandFreq(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .freqs,
                    band,
                    value,
                    1000,
                    8,
                )
            _uiState.update { it.copy(dynamicEq = it.dynamicEq.updateType(fxType) { copy(freqs = updated) }) }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_freqs", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FREQ else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FREQ
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandQ(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .qs,
                    band,
                    value,
                    150,
                    8,
                )
            _uiState.update { it.copy(dynamicEq = it.dynamicEq.updateType(fxType) { copy(qs = updated) }) }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_qs", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_Q else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_Q
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandGain(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .gains,
                    band,
                    value,
                    0,
                    8,
                )
            _uiState.update { it.copy(dynamicEq = it.dynamicEq.updateType(fxType) { copy(gains = updated) }) }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_gains", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_GAIN else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_GAIN
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandThreshold(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .thresholds,
                    band,
                    value,
                    -300,
                    8,
                )
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(thresholds = updated)
                        },
                )
            }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_thresholds", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_THRESHOLD else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_THRESHOLD
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandAttack(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .attacks,
                    band,
                    value,
                    10,
                    8,
                )
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(attacks = updated)
                        },
                )
            }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_attacks", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_ATTACK else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_ATTACK
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandRelease(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .releases,
                    band,
                    value,
                    100,
                    8,
                )
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(releases = updated)
                        },
                )
            }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_releases", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_RELEASE else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_RELEASE
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun setDynamicEqBandFilterType(
            band: Int,
            value: Int,
        ) {
            val fxType = editingFxType
            val updated =
                updateInt(
                    _uiState.value.dynamicEq
                        .forType(fxType)
                        .filterTypes,
                    band,
                    value,
                    0,
                    8,
                )
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(filterTypes = updated)
                        },
                )
            }
            viewModelScope.launch { repository.setStringPreference("dynamic_eq_filter_types", updated) }
            if (fxType == activeDeviceType) {
                val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FILTER_TYPE else ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FILTER_TYPE
                viperService?.dispatchParamsBatch(listOf(ParamEntry(param, intArrayOf(band, value))))
            }
        }

        fun addDynamicEqBand() {
            val fxType = editingFxType
            val vals = _uiState.value.dynamicEq.forType(fxType)
            val count = vals.bandCount
            if (count >= 8) return
            val freqs = parseInts(vals.freqs, 1000, 8)
            val newFreq = if (count == 0) 1000 else (freqs[count - 1] * 2).coerceAtMost(20000)
            val updatedFreqs = updateInt(vals.freqs, count, newFreq, 1000, 8)
            val updatedQs = updateInt(vals.qs, count, 150, 150, 8)
            val updatedGains = updateInt(vals.gains, count, 0, 0, 8)
            val updatedThresholds = updateInt(vals.thresholds, count, -300, -300, 8)
            val updatedAttacks = updateInt(vals.attacks, count, 10, 10, 8)
            val updatedReleases = updateInt(vals.releases, count, 100, 100, 8)
            val updatedFilterTypes = updateInt(vals.filterTypes, count, 0, 0, 8)
            val newCount = count + 1
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(
                                bandCount = newCount,
                                freqs = updatedFreqs,
                                qs = updatedQs,
                                gains = updatedGains,
                                thresholds = updatedThresholds,
                                attacks = updatedAttacks,
                                releases = updatedReleases,
                                filterTypes = updatedFilterTypes,
                            )
                        },
                )
            }
            viewModelScope.launch {
                repository.setIntPreference("dynamic_eq_band_count", newCount)
                repository.setStringPreference("dynamic_eq_freqs", updatedFreqs)
                repository.setStringPreference("dynamic_eq_qs", updatedQs)
                repository.setStringPreference("dynamic_eq_gains", updatedGains)
                repository.setStringPreference("dynamic_eq_thresholds", updatedThresholds)
                repository.setStringPreference("dynamic_eq_attacks", updatedAttacks)
                repository.setStringPreference("dynamic_eq_releases", updatedReleases)
                repository.setStringPreference("dynamic_eq_filter_types", updatedFilterTypes)
            }
            if (fxType != activeDeviceType) return
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            val entries = mutableListOf<ParamEntry>()
            val fList = parseInts(updatedFreqs, 1000, 8)
            val qList = parseInts(updatedQs, 150, 8)
            val gList = parseInts(updatedGains, 0, 8)
            val tList = parseInts(updatedThresholds, -300, 8)
            val aList = parseInts(updatedAttacks, 10, 8)
            val rList = parseInts(updatedReleases, 100, 8)
            val ftList = parseInts(updatedFilterTypes, 0, 8)
            for (b in 0 until newCount) {
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FREQ,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FREQ,
                        ),
                        intArrayOf(b, fList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_Q,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_Q,
                        ),
                        intArrayOf(b, qList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_GAIN,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_GAIN,
                        ),
                        intArrayOf(b, gList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_THRESHOLD,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_THRESHOLD,
                        ),
                        intArrayOf(b, tList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_ATTACK,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_ATTACK,
                        ),
                        intArrayOf(b, aList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_RELEASE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_RELEASE,
                        ),
                        intArrayOf(b, rList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FILTER_TYPE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FILTER_TYPE,
                        ),
                        intArrayOf(b, ftList[b]),
                    ),
                )
            }
            entries.add(
                ParamEntry(
                    p(
                        ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_COUNT,
                        ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_COUNT,
                    ),
                    intArrayOf(newCount),
                ),
            )
            viperService?.dispatchParamsBatch(entries)
        }

        fun removeDynamicEqBand(index: Int) {
            val fxType = editingFxType
            val vals = _uiState.value.dynamicEq.forType(fxType)
            val count = vals.bandCount
            if (count <= 0 || index !in 0 until count) return
            val updatedFreqs = removeFromString(vals.freqs, index, 1000, count)
            val updatedQs = removeFromString(vals.qs, index, 150, count)
            val updatedGains = removeFromString(vals.gains, index, 0, count)
            val updatedThresholds = removeFromString(vals.thresholds, index, -300, count)
            val updatedAttacks = removeFromString(vals.attacks, index, 10, count)
            val updatedReleases = removeFromString(vals.releases, index, 100, count)
            val updatedFilterTypes = removeFromString(vals.filterTypes, index, 0, count)
            val newCount = count - 1
            _uiState.update {
                it.copy(
                    dynamicEq =
                        it.dynamicEq.updateType(fxType) {
                            copy(
                                bandCount = newCount,
                                freqs = updatedFreqs,
                                qs = updatedQs,
                                gains = updatedGains,
                                thresholds = updatedThresholds,
                                attacks = updatedAttacks,
                                releases = updatedReleases,
                                filterTypes = updatedFilterTypes,
                            )
                        },
                )
            }
            viewModelScope.launch {
                repository.setIntPreference("dynamic_eq_band_count", newCount)
                repository.setStringPreference("dynamic_eq_freqs", updatedFreqs)
                repository.setStringPreference("dynamic_eq_qs", updatedQs)
                repository.setStringPreference("dynamic_eq_gains", updatedGains)
                repository.setStringPreference("dynamic_eq_thresholds", updatedThresholds)
                repository.setStringPreference("dynamic_eq_attacks", updatedAttacks)
                repository.setStringPreference("dynamic_eq_releases", updatedReleases)
                repository.setStringPreference("dynamic_eq_filter_types", updatedFilterTypes)
            }
            if (fxType != activeDeviceType) return
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            val entries = mutableListOf<ParamEntry>()
            val fList = parseInts(updatedFreqs, 1000, 8)
            val qList = parseInts(updatedQs, 150, 8)
            val gList = parseInts(updatedGains, 0, 8)
            val tList = parseInts(updatedThresholds, -300, 8)
            val aList = parseInts(updatedAttacks, 10, 8)
            val rList = parseInts(updatedReleases, 100, 8)
            val ftList = parseInts(updatedFilterTypes, 0, 8)
            for (b in 0 until newCount) {
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FREQ,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FREQ,
                        ),
                        intArrayOf(b, fList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_Q,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_Q,
                        ),
                        intArrayOf(b, qList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_GAIN,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_GAIN,
                        ),
                        intArrayOf(b, gList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_THRESHOLD,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_THRESHOLD,
                        ),
                        intArrayOf(b, tList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_ATTACK,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_ATTACK,
                        ),
                        intArrayOf(b, aList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_RELEASE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_RELEASE,
                        ),
                        intArrayOf(b, rList[b]),
                    ),
                )
                entries.add(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_FILTER_TYPE,
                            ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_FILTER_TYPE,
                        ),
                        intArrayOf(b, ftList[b]),
                    ),
                )
            }
            entries.add(
                ParamEntry(
                    p(
                        ViperParams.PARAM_HP_DYNAMIC_EQ_BAND_COUNT,
                        ViperParams.PARAM_SPK_DYNAMIC_EQ_BAND_COUNT,
                    ),
                    intArrayOf(newCount),
                ),
            )
            viperService?.dispatchParamsBatch(entries)
        }

        fun setConvolverEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Convolver ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update {
                it.copy(
                    convolver =
                        it.convolver.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}" else "${ViperParams.PARAM_HP_CONVOLVER_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.convolver.forType(fxType)
                val enableParam =
                    if (isSpk) ViperParams.PARAM_SPK_CONVOLVER_ENABLE else ViperParams.PARAM_HP_CONVOLVER_ENABLE
                val effectiveEnabled = enabled && vals.kernel.isNotEmpty()
                if (effectiveEnabled) {
                    loadKernelByName(vals.kernel, enableParam)
                } else {
                    dispatchInt(enableParam, 0)
                }
            }
        }

        fun setConvolverKernel(kernel: String) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Convolver ($mode) kernel selected: $kernel")
            _uiState.update {
                it.copy(
                    convolver =
                        it.convolver.updateType(fxType) {
                            copy(kernel = kernel)
                        },
                )
            }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}" else "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}"
            viewModelScope.launch { repository.setStringPreference(prefKey, kernel) }
            if (fxType == activeDeviceType) {
                val enableParam =
                    if (isSpk) ViperParams.PARAM_SPK_CONVOLVER_ENABLE else ViperParams.PARAM_HP_CONVOLVER_ENABLE
                if (kernel.isEmpty()) {
                    dispatchInt(enableParam, 0)
                } else {
                    val vals = _uiState.value.convolver.forType(fxType)
                    val ep = if (vals.enabled) enableParam else null
                    loadKernelByName(kernel, ep)
                }
            }
        }

        fun setConvolverCrossChannel(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    convolver =
                        it.convolver.updateType(fxType) {
                            copy(crossChannel = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}" else "${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL else ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setFieldSurroundEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Field Surround ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update {
                it.copy(
                    fieldSurround =
                        it.fieldSurround.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}" else "${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.fieldSurround.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
                            ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
                            ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
                        ),
                        intArrayOf(EffectDispatcher.fieldSurroundWideningToRaw(vals.widening)),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
                            ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
                        ),
                        intArrayOf(EffectDispatcher.fieldSurroundMidImageToRaw(vals.midImage)),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
                            ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
                        ),
                        intArrayOf(EffectDispatcher.fieldSurroundDepthToRaw(vals.depth)),
                    ),
                ),
            )
        }

        fun setFieldSurroundWidening(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    fieldSurround =
                        it.fieldSurround.updateType(fxType) {
                            copy(widening = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}" else "${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING else ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING
                dispatchInt(param, EffectDispatcher.fieldSurroundWideningToRaw(value))
            }
        }

        fun setFieldSurroundMidImage(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    fieldSurround =
                        it.fieldSurround.updateType(fxType) {
                            copy(midImage = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}" else "${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE else ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE
                dispatchInt(param, EffectDispatcher.fieldSurroundMidImageToRaw(value))
            }
        }

        fun setFieldSurroundDepth(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    fieldSurround =
                        it.fieldSurround.updateType(fxType) {
                            copy(depth = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}" else "${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH else ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH
                dispatchInt(param, EffectDispatcher.fieldSurroundDepthToRaw(value))
            }
        }

        fun setDiffSurroundEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Diff Surround ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update {
                it.copy(
                    diffSurround =
                        it.diffSurround.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}" else "${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.diffSurround.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
                            ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
                            ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
                        ),
                        intArrayOf(EffectDispatcher.diffSurroundDelayToRaw(vals.delay)),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE,
                            ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE,
                        ),
                        intArrayOf(if (vals.reverse) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DIFF_SURROUND_WET_DRY_MIX,
                            ViperParams.PARAM_SPK_DIFF_SURROUND_WET_DRY_MIX,
                        ),
                        intArrayOf(vals.wetDryMix),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DIFF_SURROUND_LP_CUTOFF,
                            ViperParams.PARAM_SPK_DIFF_SURROUND_LP_CUTOFF,
                        ),
                        intArrayOf(vals.lpCutoff),
                    ),
                ),
            )
        }

        fun setDiffSurroundDelay(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    diffSurround =
                        it.diffSurround.updateType(fxType) {
                            copy(delay = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}" else "${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY else ViperParams.PARAM_HP_DIFF_SURROUND_DELAY
                dispatchInt(param, EffectDispatcher.diffSurroundDelayToRaw(value))
            }
        }

        fun setDiffSurroundReverse(reverse: Boolean) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    diffSurround =
                        it.diffSurround.updateType(fxType) {
                            copy(reverse = reverse)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE}" else "${ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, reverse) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE else ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE
                dispatchInt(param, if (reverse) 1 else 0)
            }
        }

        fun setDiffSurroundWetDryMix(v: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    diffSurround =
                        it.diffSurround.updateType(fxType) {
                            copy(wetDryMix = v)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_DIFF_SURROUND_WET_DRY_MIX}" else "${ViperParams.PARAM_HP_DIFF_SURROUND_WET_DRY_MIX}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_DIFF_SURROUND_WET_DRY_MIX else ViperParams.PARAM_HP_DIFF_SURROUND_WET_DRY_MIX
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setDiffSurroundLpCutoff(v: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    diffSurround =
                        it.diffSurround.updateType(fxType) {
                            copy(lpCutoff = v)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_DIFF_SURROUND_LP_CUTOFF}" else "${ViperParams.PARAM_HP_DIFF_SURROUND_LP_CUTOFF}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_DIFF_SURROUND_LP_CUTOFF else ViperParams.PARAM_HP_DIFF_SURROUND_LP_CUTOFF
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setStereoImgEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(
                    stereoImg =
                        it.stereoImg.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_STEREO_IMAGER_ENABLE}" else "${ViperParams.PARAM_HP_STEREO_IMAGER_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.stereoImg.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_ENABLE,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_LOW_WIDTH,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_WIDTH,
                        ),
                        intArrayOf(vals.lowWidth),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_MID_WIDTH,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_MID_WIDTH,
                        ),
                        intArrayOf(vals.midWidth),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_WIDTH,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_WIDTH,
                        ),
                        intArrayOf(vals.highWidth),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_LOW_CROSSOVER,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_CROSSOVER,
                        ),
                        intArrayOf(vals.lowCrossover),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_CROSSOVER,
                            ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_CROSSOVER,
                        ),
                        intArrayOf(vals.highCrossover),
                    ),
                ),
            )
        }

        fun setStereoImgLowWidth(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(stereoImg = it.stereoImg.updateType(fxType) { copy(lowWidth = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_WIDTH}" else "${ViperParams.PARAM_HP_STEREO_IMAGER_LOW_WIDTH}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_WIDTH else ViperParams.PARAM_HP_STEREO_IMAGER_LOW_WIDTH
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setStereoImgMidWidth(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(stereoImg = it.stereoImg.updateType(fxType) { copy(midWidth = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_STEREO_IMAGER_MID_WIDTH}" else "${ViperParams.PARAM_HP_STEREO_IMAGER_MID_WIDTH}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_STEREO_IMAGER_MID_WIDTH else ViperParams.PARAM_HP_STEREO_IMAGER_MID_WIDTH
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setStereoImgHighWidth(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(stereoImg = it.stereoImg.updateType(fxType) { copy(highWidth = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_WIDTH}" else "${ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_WIDTH}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_WIDTH else ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_WIDTH
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setStereoImgLowCrossover(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(stereoImg = it.stereoImg.updateType(fxType) { copy(lowCrossover = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_CROSSOVER}" else "${ViperParams.PARAM_HP_STEREO_IMAGER_LOW_CROSSOVER}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_STEREO_IMAGER_LOW_CROSSOVER else ViperParams.PARAM_HP_STEREO_IMAGER_LOW_CROSSOVER
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setStereoImgHighCrossover(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(stereoImg = it.stereoImg.updateType(fxType) { copy(highCrossover = v) })
            }
            val prefKey =
                if (isSpk) {
                    "${ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_CROSSOVER}"
                } else {
                    "${ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_CROSSOVER}"
                }
            val param =
                if (isSpk) ViperParams.PARAM_SPK_STEREO_IMAGER_HIGH_CROSSOVER else ViperParams.PARAM_HP_STEREO_IMAGER_HIGH_CROSSOVER
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setVheEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "VHE ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(vhe = it.vhe.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}" else "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val vals = _uiState.value.vhe.forType(fxType)
                val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
                                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
                            ),
                            intArrayOf(if (enabled) 1 else 0),
                        ),
                        ParamEntry(
                            p(
                                ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
                                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
                            ),
                            intArrayOf(vals.quality),
                        ),
                    ),
                )
            }
        }

        fun setVheQuality(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(vhe = it.vhe.updateType(fxType) { copy(quality = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) {
                    "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}"
                } else {
                    "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}"
                }
            val param =
                if (isSpk) ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH else ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setReverbEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Reverb ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ENABLE}" else "${ViperParams.PARAM_HP_REVERB_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.reverb.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ENABLE,
                            ViperParams.PARAM_SPK_REVERB_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ROOM_SIZE,
                            ViperParams.PARAM_SPK_REVERB_ROOM_SIZE,
                        ),
                        intArrayOf(vals.roomSize * 10),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ROOM_WIDTH,
                            ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH,
                        ),
                        intArrayOf(vals.width * 10),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING,
                            ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING,
                        ),
                        intArrayOf(vals.dampening),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL,
                            ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL,
                        ),
                        intArrayOf(vals.wet),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
                            ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
                        ),
                        intArrayOf(vals.dry),
                    ),
                ),
            )
        }

        fun setReverbRoomSize(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(roomSize = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}" else "${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_REVERB_ROOM_SIZE else ViperParams.PARAM_HP_REVERB_ROOM_SIZE
                dispatchInt(param, value * 10)
            }
        }

        fun setReverbWidth(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(width = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}" else "${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH else ViperParams.PARAM_HP_REVERB_ROOM_WIDTH
                dispatchInt(param, value * 10)
            }
        }

        fun setReverbDampening(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(dampening = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}" else "${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING else ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING
                dispatchInt(param, value)
            }
        }

        fun setReverbWet(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(wet = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}" else "${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL else ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL
                dispatchInt(param, value)
            }
        }

        fun setReverbDry(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(reverb = it.reverb.updateType(fxType) { copy(dry = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}" else "${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL else ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setDynamicSystemEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Dynamic System ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update {
                it.copy(
                    dynamicSystem =
                        it.dynamicSystem.updateType(fxType) {
                            copy(enabled = enabled)
                        },
                )
            }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}" else "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) dispatchDynamicSystem()
        }

        fun setDynamicSystemStrength(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    dynamicSystem =
                        it.dynamicSystem.updateType(fxType) {
                            copy(strength = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}" else "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchDynamicSystem()
        }

        private fun dispatchDynamicSystem() {
            val isSpk = activeDeviceType == ViperParams.FX_TYPE_SPEAKER
            val vals = _uiState.value.dynamicSystem.forType(activeDeviceType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
                            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
                        ),
                        intArrayOf(if (vals.enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
                            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
                        ),
                        intArrayOf(EffectDispatcher.dynamicSystemStrengthToRaw(vals.strength)),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                        ),
                        intArrayOf(vals.xLow, vals.xHigh),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                        ),
                        intArrayOf(vals.yLow, vals.yHigh),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN,
                            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN,
                        ),
                        intArrayOf(vals.sideGainLow, vals.sideGainHigh),
                    ),
                ),
            )
        }

        private fun dsPrefPrefix(): String = if (editingFxType == ViperParams.FX_TYPE_SPEAKER) "spk_" else ""

        fun setDynamicSystemPreset(presetId: Long) {
            val fxType = editingFxType
            val vals = _uiState.value.dynamicSystem.forType(fxType)
            val preset = vals.presets.find { it.id == presetId } ?: return
            _uiState.update {
                it.copy(
                    dynamicSystem =
                        it.dynamicSystem.updateType(fxType) {
                            copy(
                                presetId = presetId,
                                xLow = preset.xLow,
                                xHigh = preset.xHigh,
                                yLow = preset.yLow,
                                yHigh = preset.yHigh,
                                sideGainLow = preset.sideGainLow,
                                sideGainHigh = preset.sideGainHigh,
                            )
                        },
                )
            }
            val pfx = dsPrefPrefix()
            viewModelScope.launch {
                repository.setIntPreference(
                    "${pfx}${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                    presetId.toInt(),
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                    preset.xLow,
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                    preset.xHigh,
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                    preset.yLow,
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                    preset.yHigh,
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                    preset.sideGainLow,
                )
                repository.setIntPreference(
                    "${pfx}${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                    preset.sideGainHigh,
                )
            }
            if (fxType == activeDeviceType) dispatchDynamicSystem()
        }

        private fun setDynamicSystemCoefficient(
            transform: DynamicSystemValues.() -> DynamicSystemValues,
            prefKeySuffix: String,
            value: Int,
        ) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    dynamicSystem =
                        it.dynamicSystem.updateType(fxType) {
                            transform().copy(presetId = null)
                        },
                )
            }
            val pfx = dsPrefPrefix()
            viewModelScope.launch {
                repository.setIntPreference("${pfx}$prefKeySuffix", value)
                repository.setIntPreference("${pfx}${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
            }
            if (fxType == activeDeviceType) dispatchDynamicSystem()
        }

        fun setDynamicSystemXLow(value: Int) =
            setDynamicSystemCoefficient(
                { copy(xLow = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                value,
            )

        fun setDynamicSystemXHigh(value: Int) =
            setDynamicSystemCoefficient(
                { copy(xHigh = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                value,
            )

        fun setDynamicSystemYLow(value: Int) =
            setDynamicSystemCoefficient(
                { copy(yLow = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                value,
            )

        fun setDynamicSystemYHigh(value: Int) =
            setDynamicSystemCoefficient(
                { copy(yHigh = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                value,
            )

        fun setDynamicSystemSideGainLow(value: Int) =
            setDynamicSystemCoefficient(
                { copy(sideGainLow = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                value,
            )

        fun setDynamicSystemSideGainHigh(value: Int) =
            setDynamicSystemCoefficient(
                { copy(sideGainHigh = value) },
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                value,
            )

        fun addDynamicSystemPreset(name: String) {
            val fxType = editingFxType
            val vals = _uiState.value.dynamicSystem.forType(fxType)
            val preset =
                DsPreset(
                    name = name,
                    xLow = vals.xLow,
                    xHigh = vals.xHigh,
                    yLow = vals.yLow,
                    yHigh = vals.yHigh,
                    sideGainLow = vals.sideGainLow,
                    sideGainHigh = vals.sideGainHigh,
                )
            val pfx = dsPrefPrefix()
            viewModelScope.launch {
                val id = repository.saveDsPreset(preset)
                _uiState.update {
                    it.copy(
                        dynamicSystem =
                            it.dynamicSystem.updateType(fxType) {
                                copy(presetId = id)
                            },
                    )
                }
                repository.setIntPreference(
                    "${pfx}${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                    id.toInt(),
                )
            }
        }

        fun deleteDynamicSystemPreset(presetId: Long) {
            val fxType = editingFxType
            val pfx = dsPrefPrefix()
            viewModelScope.launch {
                repository.deleteDsPresetById(presetId)
                val curPresetId =
                    _uiState.value.dynamicSystem
                        .forType(fxType)
                        .presetId
                if (curPresetId == presetId) {
                    _uiState.update {
                        it.copy(
                            dynamicSystem =
                                it.dynamicSystem.updateType(fxType) {
                                    copy(presetId = null)
                                },
                        )
                    }
                    repository.setIntPreference(
                        "${pfx}${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                        -1,
                    )
                }
            }
        }

        fun resetDynamicSystemCoefficients() {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    dynamicSystem =
                        it.dynamicSystem.updateType(fxType) {
                            copy(
                                xLow = 100,
                                xHigh = 5600,
                                yLow = 40,
                                yHigh = 80,
                                sideGainLow = 50,
                                sideGainHigh = 50,
                                presetId = null,
                            )
                        },
                )
            }
            val pfx = dsPrefPrefix()
            viewModelScope.launch {
                repository.setIntPreference(
                    "${pfx}${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                    -1,
                )
            }
            if (fxType == activeDeviceType) dispatchDynamicSystem()
        }

        fun setTubeSimulatorEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Tube Simulator ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(tube = it.tube.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}" else "${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE else ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE
                viperService?.dispatchParamsBatch(
                    listOf(
                        ParamEntry(param, intArrayOf(if (enabled) 1 else 0)),
                    ),
                )
            }
        }

        fun setPsychoBassEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(psychoBass = it.psychoBass.updateType(fxType) { copy(enabled = enabled) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_PSYCHO_BASS_ENABLE}" else "${ViperParams.PARAM_HP_PSYCHO_BASS_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.psychoBass.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_PSYCHO_BASS_ENABLE,
                            ViperParams.PARAM_SPK_PSYCHO_BASS_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_PSYCHO_BASS_CUTOFF,
                            ViperParams.PARAM_SPK_PSYCHO_BASS_CUTOFF,
                        ),
                        intArrayOf(vals.cutoff),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_PSYCHO_BASS_INTENSITY,
                            ViperParams.PARAM_SPK_PSYCHO_BASS_INTENSITY,
                        ),
                        intArrayOf(vals.intensity),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_PSYCHO_BASS_HARMONIC_ORDER,
                            ViperParams.PARAM_SPK_PSYCHO_BASS_HARMONIC_ORDER,
                        ),
                        intArrayOf(vals.harmonicOrder),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_PSYCHO_BASS_ORIGINAL_LEVEL,
                            ViperParams.PARAM_SPK_PSYCHO_BASS_ORIGINAL_LEVEL,
                        ),
                        intArrayOf(vals.originalLevel),
                    ),
                ),
            )
        }

        fun setPsychoBassCutoff(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(psychoBass = it.psychoBass.updateType(fxType) { copy(cutoff = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_PSYCHO_BASS_CUTOFF}" else "${ViperParams.PARAM_HP_PSYCHO_BASS_CUTOFF}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_PSYCHO_BASS_CUTOFF else ViperParams.PARAM_HP_PSYCHO_BASS_CUTOFF
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setPsychoBassIntensity(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(psychoBass = it.psychoBass.updateType(fxType) { copy(intensity = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_PSYCHO_BASS_INTENSITY}" else "${ViperParams.PARAM_HP_PSYCHO_BASS_INTENSITY}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_PSYCHO_BASS_INTENSITY else ViperParams.PARAM_HP_PSYCHO_BASS_INTENSITY
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setPsychoBassHarmonicOrder(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(psychoBass = it.psychoBass.updateType(fxType) { copy(harmonicOrder = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_PSYCHO_BASS_HARMONIC_ORDER}" else "${ViperParams.PARAM_HP_PSYCHO_BASS_HARMONIC_ORDER}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_PSYCHO_BASS_HARMONIC_ORDER else ViperParams.PARAM_HP_PSYCHO_BASS_HARMONIC_ORDER
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setPsychoBassOriginalLevel(v: Int) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            _uiState.update {
                it.copy(psychoBass = it.psychoBass.updateType(fxType) { copy(originalLevel = v) })
            }
            val prefKey =
                if (isSpk) "${ViperParams.PARAM_SPK_PSYCHO_BASS_ORIGINAL_LEVEL}" else "${ViperParams.PARAM_HP_PSYCHO_BASS_ORIGINAL_LEVEL}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_PSYCHO_BASS_ORIGINAL_LEVEL else ViperParams.PARAM_HP_PSYCHO_BASS_ORIGINAL_LEVEL
            viewModelScope.launch { repository.setIntPreference(prefKey, v) }
            if (fxType == activeDeviceType) dispatchInt(param, v)
        }

        fun setBassEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Bass ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(bass = it.bass.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_ENABLE}" else "${ViperParams.PARAM_HP_BASS_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.bass.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(ViperParams.PARAM_HP_BASS_ENABLE, ViperParams.PARAM_SPK_BASS_ENABLE),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(ViperParams.PARAM_HP_BASS_MODE, ViperParams.PARAM_SPK_BASS_MODE),
                        intArrayOf(vals.mode),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_FREQUENCY,
                            ViperParams.PARAM_SPK_BASS_FREQUENCY,
                        ),
                        intArrayOf(EffectDispatcher.bassFrequencyToRaw(vals.frequency)),
                    ),
                    ParamEntry(
                        p(ViperParams.PARAM_HP_BASS_GAIN, ViperParams.PARAM_SPK_BASS_GAIN),
                        intArrayOf(vals.gain),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_ANTI_POP,
                            ViperParams.PARAM_SPK_BASS_ANTI_POP,
                        ),
                        intArrayOf(if (vals.antiPop) 1 else 0),
                    ),
                ),
            )
        }

        fun setBassMode(mode: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(bass = it.bass.updateType(fxType) { copy(mode = mode) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MODE}" else "${ViperParams.PARAM_HP_BASS_MODE}"
            val param = if (isSpk) ViperParams.PARAM_SPK_BASS_MODE else ViperParams.PARAM_HP_BASS_MODE
            viewModelScope.launch { repository.setIntPreference(prefKey, mode) }
            if (fxType == activeDeviceType) dispatchInt(param, mode)
        }

        fun setBassFrequency(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(bass = it.bass.updateType(fxType) { copy(frequency = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}" else "${ViperParams.PARAM_HP_BASS_FREQUENCY}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_FREQUENCY else ViperParams.PARAM_HP_BASS_FREQUENCY
                dispatchInt(param, EffectDispatcher.bassFrequencyToRaw(value))
            }
        }

        fun setBassGain(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(bass = it.bass.updateType(fxType) { copy(gain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_GAIN}" else "${ViperParams.PARAM_HP_BASS_GAIN}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_GAIN else ViperParams.PARAM_HP_BASS_GAIN
                dispatchInt(param, value)
            }
        }

        fun setBassAntiPop(enabled: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(bass = it.bass.updateType(fxType) { copy(antiPop = enabled) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_ANTI_POP}" else "${ViperParams.PARAM_HP_BASS_ANTI_POP}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_ANTI_POP else ViperParams.PARAM_HP_BASS_ANTI_POP
                dispatchInt(param, if (enabled) 1 else 0)
            }
        }

        fun setBassMonoEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Bass Mono ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(bassMono = it.bassMono.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MONO_ENABLE}" else "${ViperParams.PARAM_HP_BASS_MONO_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.bassMono.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_MONO_ENABLE,
                            ViperParams.PARAM_SPK_BASS_MONO_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_MONO_MODE,
                            ViperParams.PARAM_SPK_BASS_MONO_MODE,
                        ),
                        intArrayOf(vals.mode),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_MONO_FREQUENCY,
                            ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY,
                        ),
                        intArrayOf(EffectDispatcher.bassFrequencyToRaw(vals.frequency)),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_MONO_GAIN,
                            ViperParams.PARAM_SPK_BASS_MONO_GAIN,
                        ),
                        intArrayOf(vals.gain),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_BASS_MONO_ANTI_POP,
                            ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP,
                        ),
                        intArrayOf(if (vals.antiPop) 1 else 0),
                    ),
                ),
            )
        }

        fun setBassMonoMode(mode: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(bassMono = it.bassMono.updateType(fxType) { copy(mode = mode) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MONO_MODE}" else "${ViperParams.PARAM_HP_BASS_MONO_MODE}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_BASS_MONO_MODE else ViperParams.PARAM_HP_BASS_MONO_MODE
            viewModelScope.launch { repository.setIntPreference(prefKey, mode) }
            if (fxType == activeDeviceType) dispatchInt(param, mode)
        }

        fun setBassMonoFrequency(value: Int) {
            val fxType = editingFxType
            _uiState.update {
                it.copy(
                    bassMono =
                        it.bassMono.updateType(fxType) {
                            copy(frequency = value)
                        },
                )
            }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY}" else "${ViperParams.PARAM_HP_BASS_MONO_FREQUENCY}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY else ViperParams.PARAM_HP_BASS_MONO_FREQUENCY
                dispatchInt(param, EffectDispatcher.bassFrequencyToRaw(value))
            }
        }

        fun setBassMonoGain(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(bassMono = it.bassMono.updateType(fxType) { copy(gain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MONO_GAIN}" else "${ViperParams.PARAM_HP_BASS_MONO_GAIN}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_MONO_GAIN else ViperParams.PARAM_HP_BASS_MONO_GAIN
                dispatchInt(param, value)
            }
        }

        fun setBassMonoAntiPop(enabled: Boolean) {
            val fxType = editingFxType
            _uiState.update { it.copy(bassMono = it.bassMono.updateType(fxType) { copy(antiPop = enabled) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP}" else "${ViperParams.PARAM_HP_BASS_MONO_ANTI_POP}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP else ViperParams.PARAM_HP_BASS_MONO_ANTI_POP
                dispatchInt(param, if (enabled) 1 else 0)
            }
        }

        fun setClarityEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Clarity ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(clarity = it.clarity.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}" else "${ViperParams.PARAM_HP_CLARITY_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.clarity.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_CLARITY_ENABLE,
                            ViperParams.PARAM_SPK_CLARITY_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(ViperParams.PARAM_HP_CLARITY_MODE, ViperParams.PARAM_SPK_CLARITY_MODE),
                        intArrayOf(vals.mode),
                    ),
                    ParamEntry(
                        p(ViperParams.PARAM_HP_CLARITY_GAIN, ViperParams.PARAM_SPK_CLARITY_GAIN),
                        intArrayOf(vals.gain),
                    ),
                ),
            )
        }

        fun setClarityMode(mode: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(clarity = it.clarity.updateType(fxType) { copy(mode = mode) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CLARITY_MODE}" else "${ViperParams.PARAM_HP_CLARITY_MODE}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_CLARITY_MODE else ViperParams.PARAM_HP_CLARITY_MODE
            viewModelScope.launch { repository.setIntPreference(prefKey, mode) }
            if (fxType == activeDeviceType) dispatchInt(param, mode)
        }

        fun setClarityGain(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(clarity = it.clarity.updateType(fxType) { copy(gain = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}" else "${ViperParams.PARAM_HP_CLARITY_GAIN}"
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) {
                val param =
                    if (isSpk) ViperParams.PARAM_SPK_CLARITY_GAIN else ViperParams.PARAM_HP_CLARITY_GAIN
                dispatchInt(param, value)
            }
        }

        fun setCureEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "Cure ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(cure = it.cure.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CURE_ENABLE}" else "${ViperParams.PARAM_HP_CURE_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.cure.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(ViperParams.PARAM_HP_CURE_ENABLE, ViperParams.PARAM_SPK_CURE_ENABLE),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_CURE_STRENGTH,
                            ViperParams.PARAM_SPK_CURE_STRENGTH,
                        ),
                        intArrayOf(vals.strength),
                    ),
                ),
            )
        }

        fun setCureStrength(value: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(cure = it.cure.updateType(fxType) { copy(strength = value) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}" else "${ViperParams.PARAM_HP_CURE_STRENGTH}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_CURE_STRENGTH else ViperParams.PARAM_HP_CURE_STRENGTH
            viewModelScope.launch { repository.setIntPreference(prefKey, value) }
            if (fxType == activeDeviceType) dispatchInt(param, value)
        }

        fun setAnalogXEnabled(enabled: Boolean) {
            val fxType = editingFxType
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val mode = if (isSpk) "Speaker" else "Headphone"
            FileLogger.i("ViewModel", "AnalogX ($mode): ${if (enabled) "ON" else "OFF"}")
            _uiState.update { it.copy(analog = it.analog.updateType(fxType) { copy(enabled = enabled) }) }
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}" else "${ViperParams.PARAM_HP_ANALOGX_ENABLE}"
            viewModelScope.launch { repository.setBooleanPreference(prefKey, enabled) }
            if (fxType != activeDeviceType) return
            val vals = _uiState.value.analog.forType(fxType)
            val p = { hp: Int, spk: Int -> if (isSpk) spk else hp }
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        p(
                            ViperParams.PARAM_HP_ANALOGX_ENABLE,
                            ViperParams.PARAM_SPK_ANALOGX_ENABLE,
                        ),
                        intArrayOf(if (enabled) 1 else 0),
                    ),
                    ParamEntry(
                        p(ViperParams.PARAM_HP_ANALOGX_MODE, ViperParams.PARAM_SPK_ANALOGX_MODE),
                        intArrayOf(vals.mode),
                    ),
                ),
            )
        }

        fun setAnalogXMode(mode: Int) {
            val fxType = editingFxType
            _uiState.update { it.copy(analog = it.analog.updateType(fxType) { copy(mode = mode) }) }
            val isSpk = fxType == ViperParams.FX_TYPE_SPEAKER
            val prefKey =
                if (isSpk) "spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}" else "${ViperParams.PARAM_HP_ANALOGX_MODE}"
            val param =
                if (isSpk) ViperParams.PARAM_SPK_ANALOGX_MODE else ViperParams.PARAM_HP_ANALOGX_MODE
            viewModelScope.launch { repository.setIntPreference(prefKey, mode) }
            if (fxType == activeDeviceType) dispatchInt(param, mode)
        }

        fun setSpeakerOptEnabled(enabled: Boolean) {
            FileLogger.i("ViewModel", "Speaker Optimization: ${if (enabled) "ON" else "OFF"}")
            _uiState.update {
                it.copy(
                    speakerCorrection =
                        it.speakerCorrection.updateType(ViperParams.FX_TYPE_SPEAKER) {
                            copy(enabled = enabled)
                        },
                )
            }
            viewModelScope.launch {
                repository.setBooleanPreference(
                    "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
                    enabled,
                )
            }
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                dispatchInt(ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE, if (enabled) 1 else 0)
            }
        }

        private suspend fun ensureDeviceEntry(device: AudioDevice) {
            val existing = repository.getDeviceSettings(device.id)
            if (existing == null) {
                val state = _uiState.value
                val isSpk = !device.isHeadphone
                repository.saveDeviceSettings(
                    DeviceSettings(
                        deviceId = device.id,
                        deviceName = device.name,
                        isHeadphone = device.isHeadphone,
                        settingsJson = serializeEffectPrefs(state, isSpk).toString(),
                    ),
                )
            } else {
                repository.updateDeviceLastConnected(device.id)
            }
        }

        private suspend fun saveCurrentDeviceSettings() {
            val state = _uiState.value
            val isSpk = activeDeviceType == ViperParams.FX_TYPE_SPEAKER
            val json = serializeEffectPrefs(state, isSpk).toString()
            val existing = repository.getDeviceSettings(currentDeviceId)
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = currentDeviceId,
                    deviceName = existing?.deviceName ?: state.activeDeviceName,
                    isHeadphone = existing?.isHeadphone ?: !isSpk,
                    settingsJson = json,
                ),
            )
        }

        private suspend fun loadDeviceSettings(device: AudioDevice) {
            val saved = repository.getDeviceSettings(device.id) ?: return
            val isSpk = !device.isHeadphone
            val json = JSONObject(saved.settingsJson)
            _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
            saveEffectPrefs(repository, _uiState.value, isSpk)
            applyFullState()
        }

        fun saveSettingsOnBackground() {
            viewModelScope.launch { saveCurrentDeviceSettings() }
        }

        fun renameDevice(
            deviceId: String,
            name: String,
        ) {
            viewModelScope.launch {
                repository.renameDevice(deviceId, name)
                if (deviceId == currentDeviceId) {
                    _uiState.update { it.copy(activeDeviceName = name) }
                }
            }
        }

        fun deleteDeviceSettings(deviceId: String) {
            viewModelScope.launch { repository.deleteDeviceSettings(deviceId) }
        }

        fun saveDevicePreset(deviceId: String) {
            viewModelScope.launch {
                val existing = repository.getDeviceSettings(deviceId) ?: return@launch
                val state = _uiState.value
                val isSpk = !existing.isHeadphone
                val json = serializeEffectPrefs(state, isSpk).toString()
                repository.saveDeviceSettings(existing.copy(settingsJson = json))
            }
        }

        fun loadDevicePreset(deviceId: String) {
            viewModelScope.launch {
                val saved = repository.getDeviceSettings(deviceId) ?: return@launch
                val isSpk = !saved.isHeadphone
                val json = JSONObject(saved.settingsJson)
                _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
                saveEffectPrefs(repository, _uiState.value, isSpk)
                applyFullState()
            }
        }

        private fun getFilesDir(subDir: String): File {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), subDir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private fun updateImportProgress(
            title: String,
            current: Int,
            total: Int,
        ) {
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, IMPORT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText("$current / $total")
                    .setProgress(total, current, false)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            nm.notify(IMPORT_NOTIFICATION_ID, notification)
        }

        private fun completeImportProgress(
            title: String,
            content: String,
        ) {
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, IMPORT_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build()
            nm.notify(IMPORT_NOTIFICATION_ID, notification)
        }

        private fun copyUriToFile(
            uri: Uri,
            destDir: File,
            fallbackName: String,
        ): File? {
            val context = getApplication<Application>()
            val fileName =
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                } ?: fallbackName
            val destFile = File(destDir, fileName)
            return try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                        output.fd.sync()
                    }
                }
                destFile
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to copy file", e)
                null
            }
        }

        fun importPresetFile(uri: Uri): Boolean {
            return try {
                val destDir = getFilesDir("Preset")
                val destFile = copyUriToFile(uri, destDir, "preset.json") ?: return false
                val json = destFile.readText()
                val obj = JSONObject(json)
                val isSpk = obj.has("spkMasterEnabled") && !obj.has("masterEnabled")
                val fxType = if (isSpk) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
                deserializeAndApplyStateForMode(json, fxType)
                viewModelScope.launch { persistStateForMode(fxType) }
                if (fxType == activeDeviceType) {
                    applyFullState()
                }
                val presetName = destFile.nameWithoutExtension
                viewModelScope.launch {
                    val existing = repository.getPresetByNameAndFxType(presetName, fxType)
                    if (existing != null) {
                        repository.updatePreset(
                            existing.copy(settingsJson = json, updatedAt = System.currentTimeMillis()),
                        )
                    } else {
                        repository.savePreset(
                            Preset(
                                name = presetName,
                                fxType = fxType,
                                settingsJson = json,
                            ),
                        )
                    }
                }
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to import preset", e)
                false
            }
        }

        fun importKernels(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 50
                val destDir = getFilesDir("Kernel")
                var count = 0
                for ((index, uri) in uris.withIndex()) {
                    try {
                        if (copyUriToFile(uri, destDir, "kernel_$count.wav") != null) count++
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import kernel from $uri", e)
                    }
                    if (showProgress && ((index + 1) % 10 == 0 || index + 1 == total)) {
                        updateImportProgress(notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) completeImportProgress(notificationTitle, "$successStr: $count / $total")
                if (count > 0) refreshFileLists()
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun importVdcs(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 50
                val destDir = getFilesDir("DDC")
                var count = 0
                for ((index, uri) in uris.withIndex()) {
                    try {
                        if (copyUriToFile(uri, destDir, "imported_$count.vdc") != null) count++
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import VDC from $uri", e)
                    }
                    if (showProgress && ((index + 1) % 10 == 0 || index + 1 == total)) {
                        updateImportProgress(notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) completeImportProgress(notificationTitle, "$successStr: $count / $total")
                if (count > 0) refreshFileLists()
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun refreshFileLists() {
            val ddcDir = getFilesDir("DDC")
            _vdcFileList.value = ddcDir
                .listFiles()
                ?.filter { it.extension == "vdc" }
                ?.map { it.nameWithoutExtension }
                ?.sorted() ?: emptyList()

            val kernelDir = getFilesDir("Kernel")
            _kernelFileList.value = kernelDir
                .listFiles()
                ?.map { it.name }
                ?.sorted() ?: emptyList()
        }

        fun deleteVdcFile(name: String): Boolean {
            return try {
                val file = File(getFilesDir("DDC"), "$name.vdc")
                if (!file.exists()) return false
                file.delete()
                val state = _uiState.value
                if (state.ddc.hp.device == name) {
                    _uiState.update { it.copy(ddc = it.ddc.copy(hp = it.ddc.hp.copy(device = ""))) }
                    viewModelScope.launch {
                        repository.setStringPreference(ViperRepository.PREF_DDC_DEVICE, "")
                    }
                }
                if (state.ddc.spk.device == name) {
                    _uiState.update { it.copy(ddc = it.ddc.copy(spk = it.ddc.spk.copy(device = ""))) }
                    viewModelScope.launch {
                        repository.setStringPreference("spk_${ViperRepository.PREF_DDC_DEVICE}", "")
                    }
                }
                refreshFileLists()
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to delete VDC: $name", e)
                false
            }
        }

        fun deleteKernelFile(fileName: String): Boolean {
            return try {
                val file = File(getFilesDir("Kernel"), fileName)
                if (!file.exists()) return false
                file.delete()
                val state = _uiState.value
                if (state.convolver.hp.kernel == fileName) {
                    _uiState.update {
                        it.copy(convolver = it.convolver.copy(hp = it.convolver.hp.copy(kernel = "")))
                    }
                    viewModelScope.launch {
                        repository.setStringPreference(
                            "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                            "",
                        )
                    }
                }
                if (state.convolver.spk.kernel == fileName) {
                    _uiState.update {
                        it.copy(convolver = it.convolver.copy(spk = it.convolver.spk.copy(kernel = "")))
                    }
                    viewModelScope.launch {
                        repository.setStringPreference(
                            "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                            "",
                        )
                    }
                }
                refreshFileLists()
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to delete kernel: $fileName", e)
                false
            }
        }

        fun loadVdcByName(
            name: String,
            enableParam: Int? = null,
        ): Boolean {
            FileLogger.i("ViewModel", "Loading DDC: $name")
            return try {
                val file = File(getFilesDir("DDC"), "$name.vdc")
                if (!file.exists()) return false
                val lines = file.readLines()

                var coeffs44100: FloatArray? = null
                var coeffs48000: FloatArray? = null

                for (line in lines) {
                    val trimmed = line.trim()
                    when {
                        trimmed.startsWith("SR_44100:") -> {
                            coeffs44100 =
                                trimmed
                                    .removePrefix("SR_44100:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }

                        trimmed.startsWith("SR_48000:") -> {
                            coeffs48000 =
                                trimmed
                                    .removePrefix("SR_48000:")
                                    .split(",")
                                    .map { it.trim().toFloat() }
                                    .toFloatArray()
                        }
                    }
                }

                if (coeffs44100 == null || coeffs48000 == null) return false
                if (coeffs44100.size != coeffs48000.size) return false
                if (coeffs44100.size % 5 != 0) return false

                val arrSize = coeffs44100.size
                val naturalSize = 4 + arrSize * 4 * 2
                val wireSize =
                    when {
                        naturalSize <= 256 -> 256
                        naturalSize <= 1024 -> 1024
                        else -> return false
                    }
                val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(arrSize)
                for (f in coeffs44100) buffer.putFloat(f)
                for (f in coeffs48000) buffer.putFloat(f)

                val service = viperService ?: return false
                val extras =
                    if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
                service.dispatchParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array(), extras)
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to load VDC: $name", e)
                false
            }
        }

        fun loadKernelByName(
            fileName: String,
            enableParam: Int? = null,
        ): Boolean {
            FileLogger.i("ViewModel", "Loading convolver kernel: $fileName")
            return try {
                val file = File(getFilesDir("Kernel"), fileName)
                if (!file.exists()) return false

                if (_aidlModeEnabled.value) {
                    return loadKernelViaFile(file, fileName, enableParam)
                }

                val wavBytes = file.readBytes()
                val floatSamples = decodeWavToFloat(wavBytes) ?: return false
                val channelCount = getWavChannelCount(wavBytes)
                val totalFloats = floatSamples.size
                FileLogger.i(
                    "ViewModel",
                    "Kernel loaded: $fileName samples=$totalFloats ch=$channelCount",
                )

                val service = viperService ?: return false

                val prepareParam =
                    if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                        ViperParams.PARAM_SPK_CONVOLVER_PREPARE_BUFFER
                    } else {
                        ViperParams.PARAM_HP_CONVOLVER_PREPARE_BUFFER
                    }
                val setParam =
                    if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                        ViperParams.PARAM_SPK_CONVOLVER_SET_BUFFER
                    } else {
                        ViperParams.PARAM_HP_CONVOLVER_SET_BUFFER
                    }
                val commitParam =
                    if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                        ViperParams.PARAM_SPK_CONVOLVER_COMMIT_BUFFER
                    } else {
                        ViperParams.PARAM_HP_CONVOLVER_COMMIT_BUFFER
                    }

                service.dispatchParam(prepareParam, totalFloats, channelCount, 0)

                val floatBytes = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN)
                for (f in floatSamples) floatBytes.putFloat(f)
                val rawBytes = floatBytes.array()

                val crc = CRC32()
                crc.update(rawBytes)
                val crcValue = crc.value.toInt()

                val maxFloatsPerChunk = 2046
                var offset = 0
                var chunkIndex = 0
                while (offset < totalFloats) {
                    val remaining = totalFloats - offset
                    val floatsInChunk = minOf(remaining, maxFloatsPerChunk)
                    val chunkByteCount = floatsInChunk * 4

                    val chunkBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                    chunkBuffer.putInt(chunkIndex)
                    chunkBuffer.putInt(floatsInChunk)
                    chunkBuffer.put(rawBytes, offset * 4, chunkByteCount)

                    service.dispatchParam(setParam, chunkBuffer.array())
                    offset += floatsInChunk
                    chunkIndex++
                }

                val kernelId = fileName.hashCode()
                service.dispatchParam(commitParam, totalFloats, crcValue, kernelId)
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to load kernel: $fileName", e)
                false
            }
        }

        private fun loadKernelViaFile(
            file: File,
            fileName: String,
            enableParam: Int? = null,
        ): Boolean {
            return try {
                val safeName = fileName.replace("'", "")
                val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
                val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
                RootShell.copyFile(file, kernelPath)
                FileLogger.i("ViewModel", "Kernel copied to $kernelPath")

                val param =
                    if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
                        ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL
                    } else {
                        ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
                    }
                val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
                val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
                buffer.putInt(pathBytes.size)
                buffer.put(pathBytes)
                val service = viperService ?: return false
                val extras =
                    if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
                service.dispatchParam(param, buffer.array(), extras)
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to load kernel via file: $fileName", e)
                false
            }
        }

        private fun getWavChannelCount(wavBytes: ByteArray): Int {
            if (wavBytes.size < 44) return 1
            val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(22)
            return buf.short.toInt()
        }

        private fun decodeWavToFloat(wavBytes: ByteArray): FloatArray? {
            if (wavBytes.size < 44) return null
            val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

            val riff = ByteArray(4)
            buf.get(riff)
            if (String(riff) != "RIFF") return null
            buf.int
            val wave = ByteArray(4)
            buf.get(wave)
            if (String(wave) != "WAVE") return null

            var audioFormat = 0
            var numChannels = 0
            var bitsPerSample = 0
            var dataBytes: ByteArray? = null

            while (buf.remaining() >= 8) {
                val chunkId = ByteArray(4)
                buf.get(chunkId)
                val chunkSize = buf.int
                val chunkIdStr = String(chunkId)

                when (chunkIdStr) {
                    "fmt " -> {
                        val fmtStart = buf.position()
                        audioFormat = buf.short.toInt() and 0xFFFF
                        numChannels = buf.short.toInt() and 0xFFFF
                        buf.int
                        buf.int
                        buf.short
                        bitsPerSample = buf.short.toInt() and 0xFFFF
                        buf.position(fmtStart + chunkSize)
                    }

                    "data" -> {
                        val safeSize = minOf(chunkSize, buf.remaining())
                        dataBytes = ByteArray(safeSize)
                        buf.get(dataBytes)
                    }

                    else -> {
                        val skip = minOf(chunkSize, buf.remaining())
                        buf.position(buf.position() + skip)
                    }
                }
            }

            val data = dataBytes ?: return null
            if (numChannels !in 1..2) return null

            val dataBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
            val bytesPerSample = bitsPerSample / 8
            if (bytesPerSample == 0) return null
            val totalSamples = data.size / bytesPerSample
            val result = FloatArray(totalSamples)

            when {
                audioFormat == 1 && bitsPerSample == 16 -> {
                    for (i in 0 until totalSamples) result[i] = dataBuf.short.toFloat() / (1 shl 15)
                }

                audioFormat == 1 && bitsPerSample == 24 -> {
                    for (i in 0 until totalSamples) {
                        val b0 = dataBuf.get().toInt() and 0xFF
                        val b1 = dataBuf.get().toInt() and 0xFF
                        val b2 = dataBuf.get().toInt()
                        result[i] = ((b2 shl 16) or (b1 shl 8) or b0).toFloat() / (1 shl 23)
                    }
                }

                audioFormat == 1 && bitsPerSample == 32 -> {
                    for (i in 0 until totalSamples) result[i] = dataBuf.int.toFloat() / (1L shl 31)
                }

                audioFormat == 3 && bitsPerSample == 32 -> {
                    for (i in 0 until totalSamples) result[i] = dataBuf.float
                }

                else -> {
                    return null
                }
            }

            return result
        }

        private suspend fun persistStateForMode(fxType: Int) {
            val state = _uiState.value
            val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
            saveEffectPrefs(repository, state, isSpk)
            if (isSpk) {
                for ((bc, bands) in state.eq.spk.bandsMap) {
                    repository.setStringPreference("spk_eq_bands_$bc", bands)
                }
            } else {
                for ((bc, bands) in state.eq.hp.bandsMap) {
                    repository.setStringPreference("eq_bands_$bc", bands)
                }
            }
        }

        private fun loadSettingsPreferences() {
            viewModelScope.launch {
                repository.getBooleanPreference(PREF_AUTO_START).collect { v ->
                    _autoStartEnabled.value = v
                }
            }
            _aidlModeEnabled.value = repository.aidlMode
            viewModelScope.launch {
                repository.getBooleanPreference(PREF_GLOBAL_MODE).collect { v ->
                    _globalModeEnabled.value = v
                }
            }
            viewModelScope.launch {
                repository.getBooleanPreference(PREF_DEBUG_MODE).collect { v ->
                    _debugModeEnabled.value = v
                }
            }
        }

        fun enableDebugMode() {
            _debugModeEnabled.value = true
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, true) }
        }

        fun disableDebugMode() {
            _debugModeEnabled.value = false
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, false) }
        }

        fun savePreset(name: String) {
            viewModelScope.launch {
                try {
                    val state = _uiState.value
                    val fxType = state.fxType
                    val mode = if (fxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
                    FileLogger.i("ViewModel", "Dispatch: savePreset name=$name mode=$mode")
                    val json = serializeStateForMode(state, fxType)
                    val preset =
                        Preset(
                            name = name,
                            fxType = fxType,
                            settingsJson = json,
                        )
                    repository.savePreset(preset)
                    val presetDir = getFilesDir("Preset")
                    val file = File(presetDir, "$name.json")
                    FileOutputStream(file).use { fos ->
                        fos.write(json.toByteArray(Charsets.UTF_8))
                        fos.fd.sync()
                    }
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "savePreset: failed for name=$name", e)
                }
            }
        }

        fun loadPreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                val targetFxType = preset.fxType
                val mode = if (targetFxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
                FileLogger.i("ViewModel", "Dispatch: loadPreset name=${preset.name} mode=$mode")
                deserializeAndApplyStateForMode(preset.settingsJson, targetFxType)
                persistStateForMode(targetFxType)
                if (targetFxType == activeDeviceType) {
                    applyFullState()
                }
            }
        }

        fun deletePreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.deletePresetById(id)
                try {
                    val presetDir = getFilesDir("Preset")
                    File(presetDir, "${preset.name}.json").delete()
                } catch (_: Exception) {
                }
            }
        }

        fun renamePreset(
            id: Long,
            newName: String,
        ) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.updatePreset(
                    preset.copy(name = newName, updatedAt = System.currentTimeMillis()),
                )
                try {
                    val presetDir = getFilesDir("Preset")
                    val oldFile = File(presetDir, "${preset.name}.json")
                    val newFile = File(presetDir, "$newName.json")
                    oldFile.renameTo(newFile)
                } catch (_: Exception) {
                }
            }
        }

        fun queryDriverStatus() {
            if (_aidlModeEnabled.value) {
                queryDriverStatusFromFile()
                return
            }
            val effect = viperService?.getActiveEffect()
            if (effect != null && effect.isCreated) {
                queryDriverStatusFrom(effect)
                return
            }
            val typeUuid = ViperEffect.EFFECT_TYPE_UUID
            val probe = ViperEffect(0, typeUuid)
            if (!probe.create()) {
                _driverStatus.value = DriverStatus(installed = false)
                probe.release()
                return
            }
            queryDriverStatusFrom(probe)
            probe.release()
        }

        private fun queryDriverStatusFromFile() {
            val status = ConfigChannel.readStatus()
            if (status == null || status.versionCode <= 0) {
                if (_driverStatus.value.installed) return
                _driverStatus.value = DriverStatus(installed = false)
                return
            }
            _driverStatus.value =
                DriverStatus(
                    installed = true,
                    versionCode = status.versionCode,
                    versionName = status.versionName,
                    architecture = status.architecture,
                    streaming = status.streaming,
                    samplingRate = status.sampleRate,
                )
        }

        private fun queryDriverStatusFrom(effect: ViperEffect) {
            val versionCode = effect.getDriverVersionCode()
            val archName = effect.getArchitectureString()
            val streaming = effect.isStreaming()
            val samplingRate = effect.getParameter(ViperParams.PARAM_GET_SAMPLING_RATE)

            val versionBytes = effect.getParameter(ViperParams.PARAM_GET_DRIVER_VERSION_NAME, 256)
            val versionName =
                if (versionBytes.isNotEmpty()) {
                    val nullIdx = versionBytes.indexOf(0.toByte())
                    if (nullIdx >= 0) String(versionBytes, 0, nullIdx) else String(versionBytes)
                } else {
                    versionCode.toString()
                }

            _driverStatus.value =
                DriverStatus(
                    installed = true,
                    versionCode = versionCode,
                    versionName = versionName,
                    architecture = archName,
                    streaming = streaming,
                    samplingRate = samplingRate,
                )
        }

        fun toggleAutoStart(enabled: Boolean) {
            _autoStartEnabled.value = enabled
            viewModelScope.launch {
                repository.setBooleanPreference(PREF_AUTO_START, enabled)
            }
        }

        fun toggleGlobalMode(enabled: Boolean) {
            _globalModeEnabled.value = enabled
            viewModelScope.launch {
                repository.setBooleanPreference(PREF_GLOBAL_MODE, enabled)
            }
            viperService?.setGlobalMode(enabled)
        }

        private fun serializeStateForMode(
            state: MainUiState,
            fxType: Int,
        ): String {
            val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
            val obj = serializeEffectPrefs(state, isSpk)
            return obj.toString()
        }

        private fun deserializeAndApplyStateForMode(
            json: String,
            fxType: Int,
        ) {
            val obj = JSONObject(json)
            val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
            _uiState.update { state ->
                deserializeEffectPrefs(obj, state, isSpk)
            }
        }

        private fun dispatchInt(
            param: Int,
            value: Int,
        ) {
            viperService?.dispatchParam(param, value)
        }

        private fun dispatchEqBands(
            param: Int,
            bandsString: String,
            bandCountParam: Int = 0,
            bandCount: Int = 0,
        ) {
            viperService?.dispatchEqBands(param, bandsString, bandCountParam, bandCount)
        }
    }
