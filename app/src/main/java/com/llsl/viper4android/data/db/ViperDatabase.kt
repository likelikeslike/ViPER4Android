package com.llsl.viper4android.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.llsl.viper4android.data.dao.DeviceSettingsDao
import com.llsl.viper4android.data.dao.DsPresetDao
import com.llsl.viper4android.data.dao.EqPresetDao
import com.llsl.viper4android.data.dao.PresetDao
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.PRESET_SCHEMA_VERSION
import com.llsl.viper4android.effect.deserializeEffectPrefs
import com.llsl.viper4android.effect.serializeEffectPrefs
import org.json.JSONObject

@Database(
    entities = [Preset::class, EqPreset::class, DsPreset::class, DeviceSettings::class],
    version = 8,
    exportSchema = true,
)
abstract class ViperDatabase : RoomDatabase() {
    abstract fun presetDao(): PresetDao

    abstract fun eqPresetDao(): EqPresetDao

    abstract fun dsPresetDao(): DsPresetDao

    abstract fun deviceSettingsDao(): DeviceSettingsDao

    companion object {
        val MIGRATION_1_2 =
            object : Migration(1, 2) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `eq_presets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`band_count` INTEGER NOT NULL, " +
                            "`bands` TEXT NOT NULL)",
                    )
                }
            }

        val MIGRATION_2_3 =
            object : Migration(2, 3) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ds_presets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`x_low` INTEGER NOT NULL, " +
                            "`x_high` INTEGER NOT NULL, " +
                            "`y_low` INTEGER NOT NULL, " +
                            "`y_high` INTEGER NOT NULL, " +
                            "`side_gain_low` INTEGER NOT NULL, " +
                            "`side_gain_high` INTEGER NOT NULL)",
                    )
                }
            }

        val MIGRATION_3_4 =
            object : Migration(3, 4) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `device_settings` (" +
                            "`device_id` TEXT NOT NULL PRIMARY KEY, " +
                            "`device_name` TEXT NOT NULL, " +
                            "`is_headphone` INTEGER NOT NULL, " +
                            "`settings_json` TEXT NOT NULL, " +
                            "`last_connected` INTEGER NOT NULL)",
                    )
                }
            }

        val MIGRATION_4_5 =
            object : Migration(4, 5) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE eq_presets ADD COLUMN name_key TEXT DEFAULT NULL")
                    val builtins =
                        mapOf(
                            "Acoustic" to "eq_preset_acoustic",
                            "Bass Booster" to "eq_preset_bass_booster",
                            "Bass Reducer" to "eq_preset_bass_reducer",
                            "Classical" to "eq_preset_classical",
                            "Deep" to "eq_preset_deep",
                            "Flat" to "eq_preset_flat",
                            "R&B" to "eq_preset_rnb",
                            "Rock" to "eq_preset_rock",
                            "Small Speakers" to "eq_preset_small_speakers",
                            "Treble Booster" to "eq_preset_treble_booster",
                            "Treble Reducer" to "eq_preset_treble_reducer",
                            "Vocal Booster" to "eq_preset_vocal_booster",
                        )
                    for ((oldName, key) in builtins) {
                        db.execSQL(
                            "UPDATE eq_presets SET name_key = ? WHERE name = ?",
                            arrayOf(key, oldName),
                        )
                    }
                    db.execSQL("ALTER TABLE ds_presets ADD COLUMN name_key TEXT DEFAULT NULL")
                    val keptBuiltins =
                        mapOf(
                            "Extreme Headphone (v2)" to "ds_device_extreme_headphone_v2",
                            "High-End Headphone (v2)" to "ds_device_high_end_headphone_v2",
                            "Common Headphone (v2)" to "ds_device_common_headphone_v2",
                            "Low-End Headphone (v2)" to "ds_device_low_end_headphone_v2",
                            "Common Earphone (v2)" to "ds_device_common_earphone_v2",
                            "Extreme Headphone (v1)" to "ds_device_extreme_headphone_v1",
                            "High-End Headphone (v1)" to "ds_device_high_end_headphone_v1",
                            "Common Headphone (v1)" to "ds_device_common_headphone_v1",
                            "Common Earphone (v1)" to "ds_device_common_earphone_v1",
                        )
                    for ((oldName, key) in keptBuiltins) {
                        db.execSQL(
                            "UPDATE ds_presets SET name_key = ? WHERE name = ?",
                            arrayOf(key, oldName),
                        )
                    }
                }
            }

        val MIGRATION_5_6 =
            object : Migration(5, 6) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP TABLE IF EXISTS `presets`")
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `presets` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`settings_json` TEXT NOT NULL, " +
                            "`created_at` INTEGER NOT NULL, " +
                            "`updated_at` INTEGER NOT NULL)",
                    )
                    db.execSQL("DELETE FROM `device_settings`")

                    db.execSQL(
                        "DELETE FROM eq_presets WHERE name_key IS NOT NULL AND id NOT IN (" +
                            "SELECT MIN(id) FROM eq_presets WHERE name_key IS NOT NULL " +
                            "GROUP BY name_key, band_count)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_eq_presets_name_key_band_count` " +
                            "ON `eq_presets` (`name_key`, `band_count`)",
                    )
                    db.execSQL(
                        "DELETE FROM ds_presets WHERE name_key IS NOT NULL AND id NOT IN (" +
                            "SELECT MIN(id) FROM ds_presets WHERE name_key IS NOT NULL " +
                            "GROUP BY name_key)",
                    )
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_ds_presets_name_key` " +
                            "ON `ds_presets` (`name_key`)",
                    )
                }
            }

        val MIGRATION_6_7 =
            object : Migration(6, 7) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS `ds_presets_new` (" +
                            "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                            "`name` TEXT NOT NULL, " +
                            "`name_key` TEXT DEFAULT NULL, " +
                            "`x_low` INTEGER NOT NULL, " +
                            "`x_high` INTEGER NOT NULL, " +
                            "`y_low` INTEGER NOT NULL, " +
                            "`y_high` INTEGER NOT NULL, " +
                            "`side_gain_low` REAL NOT NULL, " +
                            "`side_gain_high` REAL NOT NULL)",
                    )
                    db.execSQL(
                        "INSERT INTO `ds_presets_new` (" +
                            "`id`, `name`, `name_key`, `x_low`, `x_high`, `y_low`, `y_high`, " +
                            "`side_gain_low`, `side_gain_high`) " +
                            "SELECT `id`, `name`, `name_key`, `x_low`, `x_high`, `y_low`, `y_high`, " +
                            "`side_gain_low` / 100.0, `side_gain_high` / 100.0 FROM `ds_presets`",
                    )
                    db.execSQL("DROP TABLE `ds_presets`")
                    db.execSQL("ALTER TABLE `ds_presets_new` RENAME TO `ds_presets`")
                    db.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS " +
                            "`index_ds_presets_name_key` " +
                            "ON `ds_presets` (`name_key`)",
                    )

                    fun convertRows(
                        table: String,
                        idColumn: String,
                    ) {
                        val cursor = db.query("SELECT $idColumn, settings_json FROM $table")
                        val converted = mutableListOf<Pair<String, String>>()
                        cursor.use {
                            while (it.moveToNext()) {
                                val json = JSONObject(it.getString(1))
                                if (json.optDouble("schemaVersion", 0.0) < PRESET_SCHEMA_VERSION) {
                                    val state = deserializeEffectPrefs(json, EffectState())
                                    converted += it.getString(0) to serializeEffectPrefs(state).toString()
                                }
                            }
                        }
                        for ((id, settingsJson) in converted) {
                            db.execSQL(
                                "UPDATE $table SET settings_json = ? WHERE $idColumn = ?",
                                arrayOf(settingsJson, id),
                            )
                        }
                    }
                    convertRows("device_settings", "device_id")
                    convertRows("presets", "id")
                }
            }

        val MIGRATION_7_8 =
            object : Migration(7, 8) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    // Old profiles learn their connection type when the device is detected again.
                    db.execSQL("ALTER TABLE device_settings ADD COLUMN device_type INTEGER NOT NULL DEFAULT 0")
                }
            }
    }
}
