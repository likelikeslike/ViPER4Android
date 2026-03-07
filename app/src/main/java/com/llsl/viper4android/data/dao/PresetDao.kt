package com.llsl.viper4android.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.llsl.viper4android.data.model.Preset
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {

    @Query("SELECT * FROM presets ORDER BY name ASC")
    fun getAll(): Flow<List<Preset>>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getById(id: Long): Preset?

    @Query("SELECT * FROM presets WHERE fx_type = :fxType ORDER BY name ASC")
    fun getByFxType(fxType: Int): Flow<List<Preset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: Preset): Long

    @Update
    suspend fun update(preset: Preset)

    @Delete
    suspend fun delete(preset: Preset)

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
