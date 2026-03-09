package com.llsl.viper4android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.llsl.viper4android.data.model.DsPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface DsPresetDao {

    @Query("SELECT * FROM ds_presets ORDER BY id ASC")
    fun getAll(): Flow<List<DsPreset>>

    @Query("SELECT * FROM ds_presets WHERE id = :id")
    suspend fun getById(id: Long): DsPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: DsPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<DsPreset>)

    @Query("UPDATE ds_presets SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM ds_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM ds_presets")
    suspend fun count(): Int
}
