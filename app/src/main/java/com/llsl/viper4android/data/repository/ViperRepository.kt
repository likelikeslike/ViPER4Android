package com.llsl.viper4android.data.repository

import android.annotation.SuppressLint
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.llsl.viper4android.data.dao.DeviceSettingsDao
import com.llsl.viper4android.data.dao.DsPresetDao
import com.llsl.viper4android.data.dao.EqPresetDao
import com.llsl.viper4android.data.dao.PresetDao
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@SuppressLint("DiscouragedPrivateApi", "PrivateApi")
@Singleton
class ViperRepository
    @Inject
    constructor(
        private val presetDao: PresetDao,
        private val eqPresetDao: EqPresetDao,
        private val dsPresetDao: DsPresetDao,
        private val deviceSettingsDao: DeviceSettingsDao,
        private val dataStore: DataStore<Preferences>,
    ) {
        fun getAllPresets(): Flow<List<Preset>> = presetDao.getAll()

        suspend fun getPresetById(id: Long): Preset? = presetDao.getById(id)

        suspend fun getPresetByName(name: String): Preset? = presetDao.getByName(name)

        suspend fun savePreset(preset: Preset): Long = presetDao.insert(preset)

        suspend fun updatePreset(preset: Preset) = presetDao.update(preset)

        suspend fun deletePreset(preset: Preset) = presetDao.delete(preset)

        suspend fun deletePresetById(id: Long) = presetDao.deleteById(id)

        suspend fun deleteAllPresets() = presetDao.deleteAll()

        fun getEqPresetsByBandCount(bandCount: Int): Flow<List<EqPreset>> = eqPresetDao.getByBandCount(bandCount)

        suspend fun getEqPresetById(id: Long): EqPreset? = eqPresetDao.getById(id)

        suspend fun saveEqPreset(preset: EqPreset): Long = eqPresetDao.insert(preset)

        suspend fun renameEqPreset(
            id: Long,
            name: String,
        ) = eqPresetDao.rename(id, name)

        suspend fun deleteEqPresetById(id: Long) = eqPresetDao.deleteById(id)

        fun getAllDsPresets(): Flow<List<DsPreset>> = dsPresetDao.getAll()

        suspend fun getDsPresetById(id: Long): DsPreset? = dsPresetDao.getById(id)

        suspend fun saveDsPreset(preset: DsPreset): Long = dsPresetDao.insert(preset)

        suspend fun renameDsPreset(
            id: Long,
            name: String,
        ) = dsPresetDao.rename(id, name)

        suspend fun deleteDsPresetById(id: Long) = dsPresetDao.deleteById(id)

        fun getAllDeviceSettings(): Flow<List<DeviceSettings>> = deviceSettingsDao.getAll()

        suspend fun getDeviceSettings(deviceId: String): DeviceSettings? = deviceSettingsDao.getByDeviceId(deviceId)

        suspend fun saveDeviceSettings(settings: DeviceSettings) = deviceSettingsDao.upsert(settings)

        suspend fun renameDevice(
            deviceId: String,
            name: String,
        ) = deviceSettingsDao.rename(deviceId, name)

        suspend fun deleteDeviceSettings(deviceId: String) = deviceSettingsDao.deleteByDeviceId(deviceId)

        suspend fun updateDeviceLastConnected(deviceId: String) =
            deviceSettingsDao.updateLastConnected(deviceId, System.currentTimeMillis())

        fun getBooleanPreference(
            key: String,
            default: Boolean = false,
        ): Flow<Boolean> =
            flow {
                ensureV2Initialized()
                emitAll(dataStore.data.map { it[booleanPreferencesKey(key)] ?: default })
            }

        val aidlMode: Boolean by lazy {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return@lazy false
            runCatching {
                val services =
                    Class
                        .forName("android.os.ServiceManager")
                        .getDeclaredMethod("listServices")
                        .invoke(null) as? Array<*>
                "android.hardware.audio.effect.IFactory/default" in services.orEmpty()
            }.getOrDefault(false)
        }

        suspend fun setBooleanPreference(
            key: String,
            value: Boolean,
        ) {
            ensureV2Initialized()
            dataStore.edit { it[booleanPreferencesKey(key)] = value }
        }

        fun getIntPreference(
            key: String,
            default: Int = 0,
        ): Flow<Int> =
            flow {
                ensureV2Initialized()
                emitAll(dataStore.data.map { it[intPreferencesKey(key)] ?: default })
            }

        suspend fun setIntPreference(
            key: String,
            value: Int,
        ) {
            ensureV2Initialized()
            dataStore.edit { it[intPreferencesKey(key)] = value }
        }

        fun getStringPreference(
            key: String,
            default: String = "",
        ): Flow<String> =
            flow {
                ensureV2Initialized()
                emitAll(dataStore.data.map { it[stringPreferencesKey(key)] ?: default })
            }

        suspend fun setStringPreference(
            key: String,
            value: String,
        ) {
            ensureV2Initialized()
            dataStore.edit { it[stringPreferencesKey(key)] = value }
        }

        fun getStringSetPreference(key: String): Flow<Set<String>> =
            flow {
                ensureV2Initialized()
                emitAll(
                    dataStore.data.map { prefs ->
                        prefs[stringPreferencesKey(key)]
                            ?.split('\n')
                            ?.filter { it.isNotEmpty() }
                            ?.toSet()
                            .orEmpty()
                    },
                )
            }

        suspend fun setStringSetPreference(
            key: String,
            value: Set<String>,
        ) {
            ensureV2Initialized()
            dataStore.edit { it[stringPreferencesKey(key)] = value.joinToString("\n") }
        }

        @Volatile private var initDone = false
        private val initMutex = Mutex()

        suspend fun ensureV2Initialized() {
            if (initDone) return
            initMutex.withLock {
                if (initDone) return
                val flag =
                    dataStore.data.map {
                        it[booleanPreferencesKey(PREF_V2_INITIALIZED)] ?: false
                    }
                if (flag.first()) {
                    initDone = true
                    return
                }
                dataStore.edit { prefs ->
                    prefs.clear()
                    prefs[booleanPreferencesKey(PREF_V2_INITIALIZED)] = true
                }
                initDone = true
            }
        }

        companion object {
            const val PREF_MASTER_ENABLE = "master_enable"
            const val PREF_AUTO_START = "auto_start"
            const val PREF_GLOBAL_MODE = "global_mode"
            const val PREF_DEBUG_MODE = "debug_mode"
            const val PREF_V2_INITIALIZED = "v2_initialized"
            const val PREF_EXCLUDED_APPS = "excluded_apps"
        }
    }
