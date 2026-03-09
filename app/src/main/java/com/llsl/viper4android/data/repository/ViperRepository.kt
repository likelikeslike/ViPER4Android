package com.llsl.viper4android.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.llsl.viper4android.data.dao.DsPresetDao
import com.llsl.viper4android.data.dao.EqPresetDao
import com.llsl.viper4android.data.dao.PresetDao
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ViperRepository @Inject constructor(
    private val presetDao: PresetDao,
    private val eqPresetDao: EqPresetDao,
    private val dsPresetDao: DsPresetDao,
    private val dataStore: DataStore<Preferences>
) {

    fun getAllPresets(): Flow<List<Preset>> = presetDao.getAll()

    suspend fun getPresetById(id: Long): Preset? = presetDao.getById(id)

    suspend fun savePreset(preset: Preset): Long = presetDao.insert(preset)

    suspend fun updatePreset(preset: Preset) = presetDao.update(preset)

    suspend fun deletePreset(preset: Preset) = presetDao.delete(preset)

    suspend fun deletePresetById(id: Long) = presetDao.deleteById(id)

    fun getEqPresetsByBandCount(bandCount: Int): Flow<List<EqPreset>> =
        eqPresetDao.getByBandCount(bandCount)

    suspend fun getEqPresetById(id: Long): EqPreset? = eqPresetDao.getById(id)

    suspend fun saveEqPreset(preset: EqPreset): Long = eqPresetDao.insert(preset)

    suspend fun renameEqPreset(id: Long, name: String) = eqPresetDao.rename(id, name)

    suspend fun deleteEqPresetById(id: Long) = eqPresetDao.deleteById(id)

    fun getAllDsPresets(): Flow<List<DsPreset>> = dsPresetDao.getAll()

    suspend fun getDsPresetById(id: Long): DsPreset? = dsPresetDao.getById(id)

    suspend fun saveDsPreset(preset: DsPreset): Long = dsPresetDao.insert(preset)

    suspend fun renameDsPreset(id: Long, name: String) = dsPresetDao.rename(id, name)

    suspend fun deleteDsPresetById(id: Long) = dsPresetDao.deleteById(id)
    fun getBooleanPreference(key: String, default: Boolean = false): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: default }

    suspend fun setBooleanPreference(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    fun getIntPreference(key: String, default: Int = 0): Flow<Int> =
        dataStore.data.map { it[intPreferencesKey(key)] ?: default }

    suspend fun setIntPreference(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    fun getStringPreference(key: String, default: String = ""): Flow<String> =
        dataStore.data.map { it[stringPreferencesKey(key)] ?: default }

    suspend fun setStringPreference(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    companion object {
        const val PREF_FX_TYPE = "fx_type"
        const val PREF_MASTER_ENABLE = "master_enable"
    }
}
