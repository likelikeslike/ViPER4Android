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
import com.llsl.viper4android.BULK_OP_CHANNEL_ID
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.data.repository.ViperRepository.Companion.PREF_AUTO_START
import com.llsl.viper4android.data.repository.ViperRepository.Companion.PREF_DEBUG_MODE
import com.llsl.viper4android.data.repository.ViperRepository.Companion.PREF_GLOBAL_MODE
import com.llsl.viper4android.data.repository.ViperRepository.Companion.PREF_MASTER_ENABLE
import com.llsl.viper4android.effect.BoolListPref
import com.llsl.viper4android.effect.BoolPref
import com.llsl.viper4android.effect.DoubleListPref
import com.llsl.viper4android.effect.ENABLE_PREF_BY_EFFECT_KEY
import com.llsl.viper4android.effect.EffectPref
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.Effects
import com.llsl.viper4android.effect.IntListPref
import com.llsl.viper4android.effect.IntPref
import com.llsl.viper4android.effect.ListPref
import com.llsl.viper4android.effect.NullableLongPref
import com.llsl.viper4android.effect.StringPref
import com.llsl.viper4android.effect.deserializeEffectPrefs
import com.llsl.viper4android.effect.loadEffectPrefs
import com.llsl.viper4android.effect.saveEffectPrefs
import com.llsl.viper4android.effect.serializeEffectPrefs
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.viper.ConfigChannel
import com.llsl.viper4android.viper.ViperEffect
import com.llsl.viper4android.viper.ViperParams
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

data class DriverStatus(
    val installed: Boolean = false,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
    val streaming: Boolean = false,
    val samplingRate: Int = 0,
)

@Suppress("StaticFieldLeak")
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        application: Application,
        private val repository: ViperRepository,
    ) : AndroidViewModel(application) {
        companion object {
            private const val NOTIFY_ID_PRESET_IMPORT = 2
            private const val NOTIFY_ID_PRESET_CLEAR = 3
            private const val NOTIFY_ID_KERNEL_IMPORT = 4
            private const val NOTIFY_ID_VDC_IMPORT = 5
            private const val PROGRESS_NOTIFY_MIN_GAP_MS = 200L
            private const val PROGRESS_DRAIN_DELAY_MS = 250L
        }

        val uiState: StateFlow<EffectState>
            field: MutableStateFlow<EffectState> = MutableStateFlow(EffectState())

        val presetList: StateFlow<List<Preset>> =
            repository.getAllPresets().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val deviceSettingsList: StateFlow<List<DeviceSettings>> =
            repository.getAllDeviceSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        val driverStatus: StateFlow<DriverStatus>
            field: MutableStateFlow<DriverStatus> = MutableStateFlow(DriverStatus())

        val vdcFileList: StateFlow<List<String>>
            field: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

        val kernelFileList: StateFlow<List<String>>
            field: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())

        val autoStartEnabled: StateFlow<Boolean>
            field: MutableStateFlow<Boolean> = MutableStateFlow(false)

        val aidlModeEnabled: StateFlow<Boolean>
            field: MutableStateFlow<Boolean> = MutableStateFlow(false)

        val globalModeEnabled: StateFlow<Boolean>
            field: MutableStateFlow<Boolean> = MutableStateFlow(false)

        val debugModeEnabled: StateFlow<Boolean>
            field: MutableStateFlow<Boolean> = MutableStateFlow(false)

        private var viperService: ViperService? = null
        private var serviceBound = false
        private val audioOutputDetector = AudioOutputDetector(application)
        private var eqPresetsJob: Job? = null
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
                    viperService?.setStateProvider { uiState.value }
                    queryDriverStatus()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    viperService = null
                    serviceBound = false
                }
            }

        init {
            refreshFileLists()
            observeExternalMasterChanges()
            val initialDevice = audioOutputDetector.activeDevice.value
            viewModelScope.launch {
                loadSettingsPreferences()
                uiState.update { loadEffectPrefs(repository, it) }
                val dbName = repository.getDeviceSettings(initialDevice.id)?.deviceName ?: initialDevice.name
                uiState.update { it.copy(activeDeviceName = dbName, activeDeviceId = initialDevice.id) }
                loadEqPresetsForBandCount(uiState.value.eq.bandCount)
                loadDsPresets()
                loadDeviceSettings(initialDevice)
                ensureDeviceEntry(initialDevice)
                bindToService()
                audioOutputDetector.activeDevice.collect { device ->
                    val currentId = uiState.value.activeDeviceId
                    if (device.id != currentId) {
                        val dbName2 = repository.getDeviceSettings(device.id)?.deviceName ?: device.name
                        uiState.update { it.copy(activeDeviceName = dbName2, activeDeviceId = device.id) }
                        loadDeviceSettings(device)
                    }
                    ensureDeviceEntry(device)
                }
            }
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

        private fun observeExternalMasterChanges() {
            viewModelScope.launch {
                repository
                    .getBooleanPreference(PREF_MASTER_ENABLE, false)
                    .distinctUntilChanged()
                    .collect { enabled ->
                        uiState.update { it.copy(masterEnable = enabled) }
                    }
            }
        }

        fun <T> applyPref(
            pref: EffectPref<T>,
            value: T,
            last: Boolean = true,
        ) {
            uiState.update { pref.set(it, value) }
            viewModelScope.launch {
                persistPref(pref, value)
                if (pref.paramId == -1 || !uiState.value.masterEnable || !shouldDispatch(pref)) {
                    return@launch
                }
                if (pref is DoubleListPref) {
                    @Suppress("UNCHECKED_CAST")
                    val bytes = pref.toRawArray(value as List<Double>)
                    viperService?.dispatchParam(pref.paramId, bytes, republishAidl = last)
                } else if (pref !is IntListPref && pref !is BoolListPref) {
                    viperService?.dispatchParam(pref.paramId, pref.toRaw(value), republishAidl = last)
                }
            }
        }

        private fun <E> replaceAt(
            list: List<E>,
            index: Int,
            value: E,
            pad: E,
            count: Int = 5,
        ): List<E> {
            val mutable = list.toMutableList()
            while (mutable.size <= index) mutable.add(pad)
            mutable[index] = value
            return if (mutable.size > count) mutable.take(count) else mutable.toList()
        }

        fun <E> applyBandPref(
            pref: ListPref<E>,
            band: Int,
            value: E,
            count: Int = 5,
            last: Boolean = true,
        ) {
            val updated = replaceAt(pref.get(uiState.value), band, value, pref.padValue, count)
            applyPref(pref, updated)
            ifMasterOn {
                viperService?.dispatchParam(pref.paramId, band, pref.elementToRaw(value), 0, republishAidl = last)
            }
        }

        private fun shouldDispatch(pref: EffectPref<*>): Boolean {
            val enablePref = ENABLE_PREF_BY_EFFECT_KEY[pref.effectKey] ?: return true
            if (pref === enablePref) return true
            return enablePref.get(uiState.value)
        }

        private inline fun ifMasterOn(block: () -> Unit) {
            if (uiState.value.masterEnable) block()
        }

        @Suppress("UNCHECKED_CAST")
        private suspend fun persistPref(
            pref: EffectPref<*>,
            value: Any?,
        ) {
            when (pref) {
                is IntPref -> {
                    repository.setIntPreference(pref.prefKey, value as Int)
                }

                is BoolPref -> {
                    repository.setBooleanPreference(pref.prefKey, value as Boolean)
                }

                is StringPref -> {
                    repository.setStringPreference(pref.prefKey, value as String)
                }

                is NullableLongPref -> {
                    repository.setIntPreference(pref.prefKey, (value as Long?)?.toInt() ?: -1)
                }

                is IntListPref -> {
                    val list = value as List<Int>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";"))
                }

                is BoolListPref -> {
                    val list = value as List<Boolean>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";") { if (it) "1" else "0" })
                }

                is DoubleListPref -> {
                    val list = value as List<Double>
                    repository.setStringPreference(pref.prefKey, list.joinToString(";") { String.format(Locale.US, "%.1f", it) })
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

        private suspend fun loadSettingsPreferences() {
            autoStartEnabled.value = repository.getBooleanPreference(PREF_AUTO_START, false).first()
            globalModeEnabled.value = repository.getBooleanPreference(PREF_GLOBAL_MODE, false).first()
            debugModeEnabled.value = repository.getBooleanPreference(PREF_DEBUG_MODE, false).first()
            aidlModeEnabled.value = repository.aidlMode
        }

        private fun loadEqPresetsForBandCount(bandCount: Int) {
            eqPresetsJob?.cancel()
            eqPresetsJob =
                viewModelScope.launch {
                    repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                        uiState.update { it.copy(eq = it.eq.copy(presets = presets)) }
                    }
                }
        }

        private fun loadDsPresets() {
            dsPresetsJob?.cancel()
            dsPresetsJob =
                viewModelScope.launch {
                    repository.getAllDsPresets().collect { presets ->
                        uiState.update { it.copy(dynamicSystem = it.dynamicSystem.copy(presets = presets)) }
                    }
                }
        }

        fun dispatchFullState() {
            val service = viperService ?: return
            service.dispatchFullState(uiState.value)
        }

        fun setMasterEnabled(enabled: Boolean) {
            FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"}")
            applyPref(Effects.masterEnable, enabled)
            dispatchFullState()
        }

        fun setConvolverKernel(fileName: String) {
            FileLogger.i("ViewModel", "Convolver kernel: $fileName")
            applyPref(Effects.convolver.kernelFile, fileName)
            if (!uiState.value.convolver.enable) return
            viewModelScope.launch(Dispatchers.IO) { applyConvolverKernel(fileName) }
        }

        private fun applyConvolverKernel(fileName: String) {
            if (!uiState.value.convolver.enable) return
            try {
                ifMasterOn {
                    if (aidlModeEnabled.value) {
                        viperService?.applyConvolverKernelAidl(fileName, force = true)
                    } else {
                        viperService?.applyConvolverKernelHidl(fileName)
                    }
                }
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to apply kernel: $fileName", e)
            }
        }

        fun setDdcDevice(name: String) {
            FileLogger.i("ViewModel", "DDC device: $name")
            applyPref(Effects.ddc.device, name)
            if (!uiState.value.ddc.enable) return
            viewModelScope.launch(Dispatchers.IO) { applyDdcDevice(name) }
        }

        private fun applyDdcDevice(name: String) {
            if (!uiState.value.ddc.enable) return
            try {
                ifMasterOn {
                    if (aidlModeEnabled.value) {
                        viperService?.applyDdcDeviceAidl(name, force = true)
                    } else {
                        viperService?.applyDdcDeviceHidl(name)
                    }
                }
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to apply DDC device: $name", e)
            }
        }

        fun setEqPreset(presetId: Long) {
            viewModelScope.launch {
                val preset = repository.getEqPresetById(presetId) ?: return@launch
                val bands: List<Double> =
                    preset.bands
                        .split(";")
                        .filter { it.isNotBlank() }
                        .mapNotNull { it.toDoubleOrNull() }
                applyPref(Effects.equalizer.presetId, presetId, last = false)
                applyPref(Effects.equalizer.bands, bands)
                uiState.update { state ->
                    val updatedMap =
                        state.eq.bandsMap
                            .toMutableMap()
                            .apply { put(state.eq.bandCount, bands) }
                    state.copy(eq = state.eq.copy(bandsMap = updatedMap))
                }
            }
        }

        fun setEqBands(bands: List<Double>) {
            applyPref(Effects.equalizer.presetId, null, last = false)
            applyPref(Effects.equalizer.bands, bands)
            uiState.update { state ->
                val updatedMap =
                    state.eq.bandsMap
                        .toMutableMap()
                        .apply { put(state.eq.bandCount, bands) }
                state.copy(eq = state.eq.copy(bandsMap = updatedMap))
            }
        }

        fun setEqBandCount(count: Int) {
            val state = uiState.value
            val oldCount = state.eq.bandCount
            FileLogger.d("ViewModel", "EQ band count: $oldCount -> $count")
            val updatedMap =
                state.eq.bandsMap
                    .toMutableMap()
                    .apply { put(oldCount, state.eq.bands) }
            val defaultBands = List(count) { 0.0 }
            val bands = updatedMap[count] ?: defaultBands
            uiState.update {
                it.copy(
                    eq =
                        it.eq.copy(
                            bandCount = count,
                            bands = bands,
                            presetId = null,
                            bandsMap = updatedMap,
                        ),
                )
            }

            val joinDoubles: (List<Double>) -> String = { list ->
                list.joinToString(";") { String.format(Locale.US, "%.1f", it) }
            }
            viewModelScope.launch {
                repository.setStringPreference("eq_bands_$oldCount", joinDoubles(state.eq.bands))
                repository.setStringPreference("eq_bands_$count", joinDoubles(bands))
                repository.setIntPreference(Effects.equalizer.presetId.prefKey, -1)
            }
            applyPref(Effects.equalizer.bandCount, count, last = false)
            applyPref(Effects.equalizer.bands, bands)
            loadEqPresetsForBandCount(count)
        }

        fun addEqPreset(name: String) {
            val state = uiState.value
            val bandsStr =
                state.eq.bands.joinToString(";") {
                    String.format(Locale.US, "%.1f", it)
                }
            val preset =
                EqPreset(
                    name = name,
                    bandCount = state.eq.bandCount,
                    bands = bandsStr,
                )
            viewModelScope.launch {
                val id = repository.saveEqPreset(preset)
                applyPref(Effects.equalizer.presetId, id)
            }
        }

        fun deleteEqPreset(presetId: Long) {
            viewModelScope.launch {
                repository.deleteEqPresetById(presetId)
                if (uiState.value.eq.presetId == presetId) {
                    applyPref(Effects.equalizer.presetId, null)
                }
            }
        }

        fun resetEqBands() {
            val bandCount = uiState.value.eq.bandCount
            val flat = List(bandCount) { 0.0 }
            setEqBands(flat)
            applyPref(Effects.equalizer.presetId, null)
        }

        fun setDynamicSystemXLow(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.xLow, value)
        }

        fun setDynamicSystemXHigh(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.xHigh, value)
        }

        fun setDynamicSystemYLow(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.yLow, value)
        }

        fun setDynamicSystemYHigh(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.yHigh, value)
        }

        fun setDynamicSystemSideGainLow(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.sideGainLow, value)
        }

        fun setDynamicSystemSideGainHigh(value: Int) {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.sideGainHigh, value)
        }

        fun setDynamicSystemPreset(presetId: Long) {
            viewModelScope.launch {
                val preset = repository.getDsPresetById(presetId) ?: return@launch
                applyPref(Effects.dynamicSystem.presetId, presetId, last = false)
                applyPref(Effects.dynamicSystem.xLow, preset.xLow, last = false)
                applyPref(Effects.dynamicSystem.xHigh, preset.xHigh, last = false)
                applyPref(Effects.dynamicSystem.yLow, preset.yLow, last = false)
                applyPref(Effects.dynamicSystem.yHigh, preset.yHigh, last = false)
                applyPref(Effects.dynamicSystem.sideGainLow, preset.sideGainLow, last = false)
                applyPref(Effects.dynamicSystem.sideGainHigh, preset.sideGainHigh)
            }
        }

        fun addDynamicSystemPreset(name: String) {
            val v = uiState.value.dynamicSystem
            viewModelScope.launch {
                val id =
                    repository.saveDsPreset(
                        DsPreset(
                            name = name,
                            xLow = v.xLow,
                            xHigh = v.xHigh,
                            yLow = v.yLow,
                            yHigh = v.yHigh,
                            sideGainLow = v.sideGainLow,
                            sideGainHigh = v.sideGainHigh,
                        ),
                    )
                applyPref(Effects.dynamicSystem.presetId, id)
            }
        }

        fun deleteDynamicSystemPreset(presetId: Long) {
            viewModelScope.launch {
                repository.deleteDsPresetById(presetId)
                if (uiState.value.dynamicSystem.presetId == presetId) {
                    applyPref(Effects.dynamicSystem.presetId, null)
                }
            }
        }

        fun resetDynamicSystemCoefficients() {
            applyPref(Effects.dynamicSystem.presetId, null, last = false)
            applyPref(Effects.dynamicSystem.xLow, Effects.dynamicSystem.xLow.defaultValue, last = false)
            applyPref(Effects.dynamicSystem.xHigh, Effects.dynamicSystem.xHigh.defaultValue, last = false)
            applyPref(Effects.dynamicSystem.yLow, Effects.dynamicSystem.yLow.defaultValue, last = false)
            applyPref(Effects.dynamicSystem.yHigh, Effects.dynamicSystem.yHigh.defaultValue, last = false)
            applyPref(Effects.dynamicSystem.sideGainLow, Effects.dynamicSystem.sideGainLow.defaultValue, last = false)
            applyPref(Effects.dynamicSystem.sideGainHigh, Effects.dynamicSystem.sideGainHigh.defaultValue)
        }

        fun addDynamicEqBand() {
            val state = uiState.value
            val cur = state.dynamicEq
            if (cur.bandCount >= 10) return
            val newCount = cur.bandCount + 1

            fun <T> List<T>.resized(
                size: Int,
                fill: (Int) -> T,
            ): List<T> = List(size) { getOrElse(it, fill) }

            val newFreq =
                if (cur.bandCount == 0) {
                    1000
                } else {
                    (cur.freqs.getOrElse(cur.bandCount - 1) { 1000 } * 2).coerceAtMost(20000)
                }
            applyPref(Effects.dynamicEq.freqs, cur.freqs.resized(newCount) { newFreq })
            applyPref(Effects.dynamicEq.qs, cur.qs.resized(newCount) { 150 })
            applyPref(Effects.dynamicEq.gains, cur.gains.resized(newCount) { 0 })
            applyPref(Effects.dynamicEq.thresholds, cur.thresholds.resized(newCount) { -300 })
            applyPref(Effects.dynamicEq.attacks, cur.attacks.resized(newCount) { 10 })
            applyPref(Effects.dynamicEq.releases, cur.releases.resized(newCount) { 100 })
            applyPref(Effects.dynamicEq.filterTypes, cur.filterTypes.resized(newCount) { 0 })
            applyPref(Effects.dynamicEq.bandCount, newCount)
        }

        fun removeDynamicEqBand(index: Int) {
            val state = uiState.value
            val cur = state.dynamicEq
            if (cur.bandCount <= 1) return
            if (index !in 0 until cur.bandCount) return
            val newCount = cur.bandCount - 1

            fun List<Int>.removeBand(): List<Int> = take(cur.bandCount).filterIndexed { idx, _ -> idx != index }

            applyPref(Effects.dynamicEq.bandCount, newCount)
            applyPref(Effects.dynamicEq.freqs, cur.freqs.removeBand())
            applyPref(Effects.dynamicEq.qs, cur.qs.removeBand())
            applyPref(Effects.dynamicEq.gains, cur.gains.removeBand())
            applyPref(Effects.dynamicEq.thresholds, cur.thresholds.removeBand())
            applyPref(Effects.dynamicEq.attacks, cur.attacks.removeBand())
            applyPref(Effects.dynamicEq.releases, cur.releases.removeBand())
            applyPref(Effects.dynamicEq.filterTypes, cur.filterTypes.removeBand())
        }

        fun setPlaybackGainControlEnabled(enabled: Boolean) {
            applyPref(Effects.playbackGainControl.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.playbackGainControl
                applyPref(Effects.playbackGainControl.strength, v.strength, last = false)
                applyPref(Effects.playbackGainControl.maxGain, v.maxGain, last = false)
                applyPref(Effects.playbackGainControl.outputThreshold, v.outputThreshold)
            }
        }

        fun setLufsEnabled(enabled: Boolean) {
            applyPref(Effects.lufs.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.lufs
                applyPref(Effects.lufs.target, v.target, last = false)
                applyPref(Effects.lufs.maxGain, v.maxGain, last = false)
                applyPref(Effects.lufs.speed, v.speed)
            }
        }

        fun setFetCompressorEnabled(enabled: Boolean) {
            applyPref(Effects.fetCompressor.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.fetCompressor
                applyPref(Effects.fetCompressor.threshold, v.threshold, last = false)
                applyPref(Effects.fetCompressor.ratio, v.ratio, last = false)
                applyPref(Effects.fetCompressor.kneeAuto, v.kneeAuto, last = false)
                applyPref(Effects.fetCompressor.knee, v.knee, last = false)
                applyPref(Effects.fetCompressor.kneeMulti, v.kneeMulti, last = false)
                applyPref(Effects.fetCompressor.gainAuto, v.gainAuto, last = false)
                applyPref(Effects.fetCompressor.gain, v.gain)
            }
        }

        fun setMultibandCompressorEnabled(enabled: Boolean) {
            applyPref(Effects.multibandCompressor.enable, enabled, last = !enabled)
            if (enabled) {
                val mbc = Effects.multibandCompressor
                val intPrefs =
                    listOf(
                        mbc.crossovers,
                        mbc.thresholds,
                        mbc.ratios,
                        mbc.gains,
                        mbc.knees,
                        mbc.kneeMultis,
                        mbc.attacks,
                        mbc.maxAttacks,
                        mbc.releases,
                        mbc.maxReleases,
                        mbc.crests,
                        mbc.adapts,
                    )
                val boolPrefs =
                    listOf(
                        mbc.bandEnables,
                        mbc.kneeAutos,
                        mbc.gainAutos,
                        mbc.attackAutos,
                        mbc.releaseAutos,
                        mbc.noClips,
                    )
                val count = 5
                val total = (intPrefs.size + boolPrefs.size) * count
                var i = 0
                for (pref in intPrefs) {
                    val values = pref.get(uiState.value)
                    for (band in 0 until count) {
                        i++
                        applyBandPref(pref, band, values.getOrElse(band) { 0 }, count, last = i == total)
                    }
                }
                for (pref in boolPrefs) {
                    val values = pref.get(uiState.value)
                    for (band in 0 until count) {
                        i++
                        applyBandPref(pref, band, values.getOrElse(band) { false }, count, last = i == total)
                    }
                }
            }
        }

        fun setDdcEnabled(enabled: Boolean) {
            applyPref(Effects.ddc.enable, enabled)
            if (enabled) {
                val v = uiState.value.ddc
                applyPref(Effects.ddc.device, v.device)
                viewModelScope.launch(Dispatchers.IO) {
                    applyDdcDevice(v.device)
                }
            }
        }

        fun setSpectrumExtensionEnabled(enabled: Boolean) {
            applyPref(Effects.spectrumExtension.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.spectrumExtension
                applyPref(Effects.spectrumExtension.strength, v.strength, last = false)
                applyPref(Effects.spectrumExtension.exciter, v.exciter)
            }
        }

        fun setEqEnabled(enabled: Boolean) {
            applyPref(Effects.equalizer.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.eq
                applyPref(Effects.equalizer.bandCount, v.bandCount, last = false)
                applyPref(Effects.equalizer.bands, v.bands)
            }
        }

        fun setDynamicEqEnabled(enabled: Boolean) {
            applyPref(Effects.dynamicEq.enable, enabled, last = !enabled)
            if (enabled) {
                val count = uiState.value.dynamicEq.bandCount
                applyPref(Effects.dynamicEq.bandCount, count, last = false)
                val bandPrefs =
                    listOf(
                        Effects.dynamicEq.freqs,
                        Effects.dynamicEq.qs,
                        Effects.dynamicEq.gains,
                        Effects.dynamicEq.thresholds,
                        Effects.dynamicEq.attacks,
                        Effects.dynamicEq.releases,
                        Effects.dynamicEq.filterTypes,
                    )
                val total = bandPrefs.size * count
                var i = 0
                for (pref in bandPrefs) {
                    val values = pref.get(uiState.value)
                    for (band in 0 until count) {
                        i++
                        applyBandPref(pref, band, values[band], count, last = i == total)
                    }
                }
            }
        }

        fun setConvolverEnabled(enabled: Boolean) {
            applyPref(Effects.convolver.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.convolver
                applyPref(Effects.convolver.kernelFile, v.kernelFile, last = false)
                applyPref(Effects.convolver.crossChannel, v.crossChannel)
                viewModelScope.launch(Dispatchers.IO) {
                    applyConvolverKernel(v.kernelFile)
                }
            }
        }

        fun setFieldSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.fieldSurround.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.fieldSurround
                applyPref(Effects.fieldSurround.widening, v.widening, last = false)
                applyPref(Effects.fieldSurround.midImage, v.midImage, last = false)
                applyPref(Effects.fieldSurround.depth, v.depth)
            }
        }

        fun setDiffSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.diffSurround.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.diffSurround
                applyPref(Effects.diffSurround.delay, v.delay, last = false)
                applyPref(Effects.diffSurround.reverse, v.reverse, last = false)
                applyPref(Effects.diffSurround.wetDryMix, v.wetDryMix, last = false)
                applyPref(Effects.diffSurround.lpCutoff, v.lpCutoff)
            }
        }

        fun setStereoImagerEnabled(enabled: Boolean) {
            applyPref(Effects.stereoImager.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.stereoImager
                applyPref(Effects.stereoImager.lowWidth, v.lowWidth, last = false)
                applyPref(Effects.stereoImager.midWidth, v.midWidth, last = false)
                applyPref(Effects.stereoImager.highWidth, v.highWidth, last = false)
                applyPref(Effects.stereoImager.lowCrossover, v.lowCrossover, last = false)
                applyPref(Effects.stereoImager.highCrossover, v.highCrossover)
            }
        }

        fun setHeadphoneSurroundEnabled(enabled: Boolean) {
            applyPref(Effects.headphoneSurround.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.headphoneSurround
                applyPref(Effects.headphoneSurround.quality, v.quality)
            }
        }

        fun setReverbEnabled(enabled: Boolean) {
            applyPref(Effects.reverb.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.reverb
                applyPref(Effects.reverb.roomSize, v.roomSize, last = false)
                applyPref(Effects.reverb.width, v.width, last = false)
                applyPref(Effects.reverb.damp, v.damp, last = false)
                applyPref(Effects.reverb.wet, v.wet, last = false)
                applyPref(Effects.reverb.dry, v.dry)
            }
        }

        fun setDynamicSystemEnabled(enabled: Boolean) {
            applyPref(Effects.dynamicSystem.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.dynamicSystem
                applyPref(Effects.dynamicSystem.strength, v.strength, last = false)
                applyPref(Effects.dynamicSystem.xLow, v.xLow, last = false)
                applyPref(Effects.dynamicSystem.xHigh, v.xHigh, last = false)
                applyPref(Effects.dynamicSystem.yLow, v.yLow, last = false)
                applyPref(Effects.dynamicSystem.yHigh, v.yHigh, last = false)
                applyPref(Effects.dynamicSystem.sideGainLow, v.sideGainLow, last = false)
                applyPref(Effects.dynamicSystem.sideGainHigh, v.sideGainHigh)
            }
        }

        fun setTubeSimulatorEnabled(enabled: Boolean) {
            applyPref(Effects.tubeSimulator.enable, enabled)
        }

        fun setPsychoacousticBassEnabled(enabled: Boolean) {
            applyPref(Effects.psychoacousticBass.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.psychoacousticBass
                applyPref(Effects.psychoacousticBass.cutoff, v.cutoff, last = false)
                applyPref(Effects.psychoacousticBass.intensity, v.intensity, last = false)
                applyPref(Effects.psychoacousticBass.harmonicOrder, v.harmonicOrder, last = false)
                applyPref(Effects.psychoacousticBass.originalLevel, v.originalLevel)
            }
        }

        fun setBassEnabled(enabled: Boolean) {
            applyPref(Effects.bass.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.bass
                applyPref(Effects.bass.mode, v.mode, last = false)
                applyPref(Effects.bass.frequency, v.frequency, last = false)
                applyPref(Effects.bass.gain, v.gain, last = false)
                applyPref(Effects.bass.antiPop, v.antiPop)
            }
        }

        fun setBassMonoEnabled(enabled: Boolean) {
            applyPref(Effects.bassMono.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.bassMono
                applyPref(Effects.bassMono.mode, v.mode, last = false)
                applyPref(Effects.bassMono.frequency, v.frequency, last = false)
                applyPref(Effects.bassMono.gain, v.gain, last = false)
                applyPref(Effects.bassMono.antiPop, v.antiPop)
            }
        }

        fun setClarityEnabled(enabled: Boolean) {
            applyPref(Effects.clarity.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.clarity
                applyPref(Effects.clarity.mode, v.mode, last = false)
                applyPref(Effects.clarity.gain, v.gain)
            }
        }

        fun setCureEnabled(enabled: Boolean) {
            applyPref(Effects.cure.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.cure
                applyPref(Effects.cure.crossfeedPreset, v.crossfeedPreset)
            }
        }

        fun setAnalogXEnabled(enabled: Boolean) {
            applyPref(Effects.analogX.enable, enabled, last = !enabled)
            if (enabled) {
                val v = uiState.value.analogX
                applyPref(Effects.analogX.mode, v.mode)
            }
        }

        fun setSpeakerCorrectionEnabled(enabled: Boolean) {
            applyPref(Effects.speakerCorrection.enable, enabled)
        }

        private suspend fun ensureDeviceEntry(device: AudioDevice) {
            val existing = repository.getDeviceSettings(device.id)
            if (existing == null) {
                val state = uiState.value
                repository.saveDeviceSettings(
                    DeviceSettings(
                        deviceId = device.id,
                        deviceName = device.name,
                        isHeadphone = device.isHeadphone,
                        settingsJson = serializeEffectPrefs(state).toString(),
                    ),
                )
            } else {
                repository.updateDeviceLastConnected(device.id)
            }
        }

        private suspend fun saveCurrentDeviceSettings() {
            val state = uiState.value
            val deviceId = state.activeDeviceId.ifEmpty { return }
            val json = serializeEffectPrefs(state).toString()
            val existing = repository.getDeviceSettings(deviceId)
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = deviceId,
                    deviceName = existing?.deviceName ?: state.activeDeviceName,
                    isHeadphone = existing?.isHeadphone ?: false,
                    settingsJson = json,
                ),
            )
        }

        private suspend fun loadDeviceSettings(device: AudioDevice) {
            val saved = repository.getDeviceSettings(device.id) ?: return
            val json = JSONObject(saved.settingsJson)
            uiState.update { deserializeEffectPrefs(json, it) }
            saveEffectPrefs(repository, uiState.value)
            dispatchFullState()
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
                if (deviceId == uiState.value.activeDeviceId) {
                    uiState.update { it.copy(activeDeviceName = name) }
                }
            }
        }

        fun deleteDeviceSettings(deviceId: String) {
            viewModelScope.launch { repository.deleteDeviceSettings(deviceId) }
        }

        fun saveDevicePreset(deviceId: String) {
            viewModelScope.launch {
                val existing = repository.getDeviceSettings(deviceId) ?: return@launch
                val json = serializeEffectPrefs(uiState.value).toString()
                repository.saveDeviceSettings(existing.copy(settingsJson = json))
            }
        }

        fun loadDevicePreset(deviceId: String) {
            viewModelScope.launch {
                val saved = repository.getDeviceSettings(deviceId) ?: return@launch
                val json = JSONObject(saved.settingsJson)
                uiState.update { deserializeEffectPrefs(json, it) }
                saveEffectPrefs(repository, uiState.value)
                dispatchFullState()
            }
        }

        private fun getFilesDir(subDir: String): File {
            val dir = File(getApplication<Application>().getExternalFilesDir(null), subDir)
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

        private val lastBulkProgressNotifyMs = ConcurrentHashMap<Int, Long>()

        private fun updateBulkProgress(
            notificationId: Int,
            title: String,
            current: Int,
            total: Int,
        ) {
            val now = System.currentTimeMillis()
            val last = lastBulkProgressNotifyMs[notificationId] ?: 0L
            if (current < total && now - last < PROGRESS_NOTIFY_MIN_GAP_MS) return
            lastBulkProgressNotifyMs[notificationId] = now
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, BULK_OP_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setContentTitle(title)
                    .setContentText("$current / $total")
                    .setProgress(total, current, false)
                    .setOngoing(true)
                    .setSilent(true)
                    .build()
            nm.notify(notificationId, notification)
        }

        private suspend fun completeBulkProgress(
            notificationId: Int,
            title: String,
            content: String,
        ) {
            delay(PROGRESS_DRAIN_DELAY_MS)
            lastBulkProgressNotifyMs.remove(notificationId)
            val app = getApplication<Application>()
            val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val notification =
                NotificationCompat
                    .Builder(app, BULK_OP_CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setProgress(0, 0, false)
                    .setOngoing(false)
                    .setSilent(true)
                    .setAutoCancel(true)
                    .build()
            nm.notify(notificationId, notification)
        }

        private fun queryDisplayName(uri: Uri): String? =
            getApplication<Application>()
                .contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
                }

        // SAF pickers filter by MIME type, not extension; enforce allowed extensions here.
        private fun filterByExtension(
            uris: List<Uri>,
            allowedExtensions: Set<String>,
        ): List<Uri> =
            uris.filter { uri ->
                val name = queryDisplayName(uri) ?: return@filter false
                allowedExtensions.any { name.endsWith(".$it", ignoreCase = true) }
            }

        private fun copyUriToFile(
            uri: Uri,
            destDir: File,
            fallbackName: String,
        ): File? {
            val context = getApplication<Application>()
            val fileName = queryDisplayName(uri) ?: fallbackName
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

        fun importPresetFiles(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            val uris = filterByExtension(uris, setOf("json"))
            if (uris.isEmpty()) {
                onResult(false)
                return
            }
            viewModelScope.launch(Dispatchers.IO) {
                val total = uris.size
                val showProgress = total > 10
                val destDir = getFilesDir("Preset")
                val baseState = uiState.value
                var count = 0
                var lastParsed: EffectState? = null
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val tmpFile = copyUriToFile(uri, destDir, "import_$index.json")
                        if (tmpFile != null) {
                            val json = tmpFile.readText()
                            val obj = JSONObject(json)
                            val parsed = deserializeEffectPrefs(obj, baseState)
                            val importedName =
                                obj.optString("name", "").ifBlank {
                                    tmpFile.nameWithoutExtension
                                }
                            val importedCreatedAt =
                                obj.optLong("createdAt", System.currentTimeMillis())
                            val effectOnlyJson = serializeEffectPrefs(parsed).toString()
                            repository.savePreset(
                                Preset(
                                    name = importedName,
                                    settingsJson = effectOnlyJson,
                                    createdAt = importedCreatedAt,
                                ),
                            )
                            count++
                            lastParsed = parsed
                        }
                    } catch (e: Exception) {
                        FileLogger.e("ViewModel", "Failed to import preset from $uri", e)
                    }
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_PRESET_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_PRESET_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
                if (total == 1 && count == 1 && lastParsed != null) {
                    val applied = lastParsed
                    launch(Dispatchers.Main) {
                        uiState.update { applied }
                        saveEffectPrefs(repository, applied)
                        dispatchFullState()
                    }
                }
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun importKernels(
            uris: List<Uri>,
            notificationTitle: String,
            successStr: String,
            onResult: (Boolean) -> Unit,
        ) {
            val uris = filterByExtension(uris, setOf("wav", "irs"))
            if (uris.isEmpty()) {
                onResult(false)
                return
            }
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
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_KERNEL_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_KERNEL_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
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
            val uris = filterByExtension(uris, setOf("vdc"))
            if (uris.isEmpty()) {
                onResult(false)
                return
            }
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
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_VDC_IMPORT, notificationTitle, index + 1, total)
                    }
                }
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_VDC_IMPORT, notificationTitle, "$successStr: $count / $total")
                }
                if (count > 0) refreshFileLists()
                launch(Dispatchers.Main) { onResult(count > 0) }
            }
        }

        fun refreshFileLists() {
            val ddcDir = getFilesDir("DDC")
            vdcFileList.value = ddcDir
                .listFiles()
                ?.filter { it.extension == "vdc" }
                ?.map { it.nameWithoutExtension }
                ?.sorted() ?: emptyList()

            val kernelDir = getFilesDir("Kernel")
            kernelFileList.value = kernelDir
                .listFiles()
                ?.map { it.name }
                ?.sorted() ?: emptyList()
        }

        fun deleteVdcFile(name: String): Boolean {
            return try {
                val file = File(getFilesDir("DDC"), "$name.vdc")
                if (!file.exists()) return false
                file.delete()
                if (uiState.value.ddc.device == name) {
                    applyPref(Effects.ddc.device, "")
                    if (uiState.value.ddc.enable) {
                        viewModelScope.launch(Dispatchers.IO) { applyDdcDevice("") }
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
                if (uiState.value.convolver.kernelFile == fileName) {
                    applyPref(Effects.convolver.kernelFile, "")
                    if (uiState.value.convolver.enable) {
                        viewModelScope.launch(Dispatchers.IO) { applyConvolverKernel("") }
                    }
                }
                refreshFileLists()
                true
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to delete kernel: $fileName", e)
                false
            }
        }

        fun savePreset(name: String) {
            val createdAt = System.currentTimeMillis()
            val roomJson = serializeEffectPrefs(uiState.value).toString()
            val fileJson =
                serializeEffectPrefs(
                    uiState.value,
                    name = name,
                    createdAt = createdAt,
                ).toString()
            viewModelScope.launch {
                try {
                    repository.savePreset(
                        Preset(name = name, settingsJson = roomJson, createdAt = createdAt),
                    )
                    val file = File(getFilesDir("Preset"), "$name.json")
                    FileOutputStream(file).use { fos ->
                        fos.write(fileJson.toByteArray(Charsets.UTF_8))
                        fos.fd.sync()
                    }
                    FileLogger.i("ViewModel", "savePreset: $name -> ${file.absolutePath}")
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "savePreset: failed for name=$name", e)
                }
            }
        }

        fun loadPreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                val json = JSONObject(preset.settingsJson)
                uiState.update { deserializeEffectPrefs(json, it) }
                saveEffectPrefs(repository, uiState.value)
                dispatchFullState()
            }
        }

        fun deletePreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.deletePresetById(id)
                try {
                    File(getFilesDir("Preset"), "${preset.name}.json").delete()
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "deletePreset: file remove failed", e)
                }
            }
        }

        fun clearAllPresets(
            notificationTitle: String,
            successStr: String,
            onResult: (Int) -> Unit,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val files =
                    getFilesDir("Preset").listFiles { f -> f.isFile && f.extension == "json" }
                        ?: emptyArray()
                val total = files.size
                val showProgress = total > 10
                var deleted = 0
                files.forEachIndexed { index, file ->
                    if (file.delete()) deleted++
                    if (showProgress) {
                        updateBulkProgress(NOTIFY_ID_PRESET_CLEAR, notificationTitle, index + 1, total)
                    }
                }
                repository.deleteAllPresets()
                if (showProgress) {
                    completeBulkProgress(NOTIFY_ID_PRESET_CLEAR, notificationTitle, "$successStr: $deleted / $total")
                }
                FileLogger.i("ViewModel", "clearAllPresets: files deleted=$deleted/$total, db wiped")
                launch(Dispatchers.Main) { onResult(deleted) }
            }
        }

        fun updatePreset(id: Long) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                val roomJson = serializeEffectPrefs(uiState.value).toString()
                val updatedAt = System.currentTimeMillis()
                repository.updatePreset(
                    preset.copy(settingsJson = roomJson, updatedAt = updatedAt),
                )
                try {
                    val file = File(getFilesDir("Preset"), "${preset.name}.json")
                    val fileJson =
                        serializeEffectPrefs(
                            uiState.value,
                            name = preset.name,
                            createdAt = preset.createdAt,
                        ).toString()
                    FileOutputStream(file).use { fos ->
                        fos.write(fileJson.toByteArray(Charsets.UTF_8))
                        fos.fd.sync()
                    }
                    FileLogger.i("ViewModel", "updatePreset: ${preset.name} -> ${file.absolutePath}")
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "updatePreset: file write failed for ${preset.name}", e)
                }
            }
        }

        fun renamePreset(
            id: Long,
            newName: String,
        ) {
            viewModelScope.launch {
                val preset = repository.getPresetById(id) ?: return@launch
                repository.updatePreset(preset.copy(name = newName))
                try {
                    val dir = getFilesDir("Preset")
                    val oldFile = File(dir, "${preset.name}.json")
                    val newFile = File(dir, "$newName.json")
                    if (oldFile.exists()) {
                        oldFile.renameTo(newFile)
                    } else {
                        FileOutputStream(newFile).use { fos ->
                            fos.write(preset.settingsJson.toByteArray(Charsets.UTF_8))
                            fos.fd.sync()
                        }
                    }
                } catch (e: Exception) {
                    FileLogger.e("ViewModel", "renamePreset: file rename failed", e)
                }
            }
        }

        fun queryDriverStatus() {
            if (aidlModeEnabled.value) {
                queryDriverStatusFromFile()
                return
            }
            val active = viperService?.getActiveEffect()
            if (active != null && active.isCreated) {
                queryDriverStatusFrom(active)
                return
            }
            val probe = ViperEffect(0, ViperEffect.EFFECT_TYPE_UUID)
            if (!probe.create()) {
                driverStatus.value = DriverStatus(installed = false)
                probe.release()
                return
            }
            queryDriverStatusFrom(probe)
            probe.release()
        }

        private fun queryDriverStatusFromFile() {
            val status = ConfigChannel.readStatus()
            if (status == null || status.versionCode <= 0) {
                if (driverStatus.value.installed) return
                driverStatus.value = DriverStatus(installed = false)
                return
            }
            driverStatus.value =
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
            driverStatus.value =
                DriverStatus(
                    installed = true,
                    versionCode = versionCode,
                    versionName = versionName,
                    architecture = archName,
                    streaming = streaming,
                    samplingRate = samplingRate,
                )
        }

        fun enableDebugMode() {
            debugModeEnabled.value = true
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, true) }
        }

        fun disableDebugMode() {
            debugModeEnabled.value = false
            viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, false) }
        }

        fun toggleAutoStart(enabled: Boolean) {
            autoStartEnabled.value = enabled
            viewModelScope.launch { repository.setBooleanPreference(PREF_AUTO_START, enabled) }
        }

        fun toggleGlobalMode(enabled: Boolean) {
            globalModeEnabled.value = enabled
            viewModelScope.launch { repository.setBooleanPreference(PREF_GLOBAL_MODE, enabled) }
            viperService?.setGlobalMode(enabled)
        }
    }
