package com.llsl.viper4android.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.data.dao.DsPresetDao
import com.llsl.viper4android.data.dao.EqPresetDao
import com.llsl.viper4android.data.dao.PresetDao
import com.llsl.viper4android.data.db.ViperDatabase
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "viper_preferences")

private val EQ_PRESET_NAMES = listOf(
    "Acoustic", "Bass Booster", "Bass Reducer", "Classical",
    "Deep", "Flat", "R&B", "Rock",
    "Small Speakers", "Treble Booster", "Treble Reducer", "Vocal Booster"
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ViperDatabase {
        lateinit var db: ViperDatabase
        db = Room.databaseBuilder(
            context,
            ViperDatabase::class.java,
            "viper4android.db"
        )
            .addMigrations(ViperDatabase.MIGRATION_1_2, ViperDatabase.MIGRATION_2_3)
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(sqDb: SupportSQLiteDatabase) {
                    super.onCreate(sqDb)
                    CoroutineScope(Dispatchers.IO).launch {
                        seedEqPresets(db.eqPresetDao())
                        seedDsPresets(db.dsPresetDao())
                    }
                }

                override fun onOpen(sqDb: SupportSQLiteDatabase) {
                    super.onOpen(sqDb)
                    CoroutineScope(Dispatchers.IO).launch {
                        val eqDao = db.eqPresetDao()
                        if (eqDao.count() == 0) {
                            seedEqPresets(eqDao)
                        }
                        val dsDao = db.dsPresetDao()
                        if (dsDao.count() == 0) {
                            seedDsPresets(dsDao)
                        }
                    }
                }
            })
            .build()
        return db
    }

    private suspend fun seedEqPresets(dao: EqPresetDao) {
        val presets = mutableListOf<EqPreset>()
        val sources = mapOf(
            10 to EffectDispatcher.EQ_PRESETS,
            15 to EffectDispatcher.EQ_PRESETS_15,
            25 to EffectDispatcher.EQ_PRESETS_25,
            31 to EffectDispatcher.EQ_PRESETS_31
        )
        for ((bandCount, bandsList) in sources) {
            for ((i, bands) in bandsList.withIndex()) {
                presets.add(
                    EqPreset(
                        name = EQ_PRESET_NAMES[i],
                        bandCount = bandCount,
                        bands = bands
                    )
                )
            }
        }
        dao.insertAll(presets)
    }

    private suspend fun seedDsPresets(dao: DsPresetDao) {
        val devices = EffectDispatcher.DYNAMIC_SYSTEM_DEVICES
        val names = EffectDispatcher.DYNAMIC_SYSTEM_DEVICE_NAMES
        val presets = devices.mapIndexed { i, coeffStr ->
            val parts = coeffStr.split(";").map { it.toIntOrNull() ?: 0 }
            DsPreset(
                name = names.getOrElse(i) { "Device $i" },
                xLow = parts.getOrElse(0) { 100 },
                xHigh = parts.getOrElse(1) { 5600 },
                yLow = parts.getOrElse(2) { 40 },
                yHigh = parts.getOrElse(3) { 80 },
                sideGainLow = parts.getOrElse(4) { 50 },
                sideGainHigh = parts.getOrElse(5) { 50 }
            )
        }
        dao.insertAll(presets)
    }

    @Provides
    @Singleton
    fun providePresetDao(database: ViperDatabase): PresetDao = database.presetDao()

    @Provides
    @Singleton
    fun provideEqPresetDao(database: ViperDatabase): EqPresetDao = database.eqPresetDao()

    @Provides
    @Singleton
    fun provideDsPresetDao(database: ViperDatabase): DsPresetDao = database.dsPresetDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.dataStore
}
