package com.llsl.viper4android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.llsl.viper4android.data.dao.EqPresetDao
import com.llsl.viper4android.data.dao.PresetDao
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset

@Database(entities = [Preset::class, EqPreset::class], version = 2, exportSchema = true)
abstract class ViperDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao
    abstract fun eqPresetDao(): EqPresetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `eq_presets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`band_count` INTEGER NOT NULL, " +
                            "`bands` TEXT NOT NULL)"
                )
            }
        }
    }
}
