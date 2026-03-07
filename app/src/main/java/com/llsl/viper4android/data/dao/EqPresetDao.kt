package com.llsl.viper4android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.llsl.viper4android.data.model.EqPreset
import kotlinx.coroutines.flow.Flow

@Dao
interface EqPresetDao {

    @Query("SELECT * FROM eq_presets WHERE band_count = :bandCount ORDER BY id ASC")
    fun getByBandCount(bandCount: Int): Flow<List<EqPreset>>

    @Query("SELECT * FROM eq_presets WHERE id = :id")
    suspend fun getById(id: Long): EqPreset?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: EqPreset): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(presets: List<EqPreset>)

    @Query("UPDATE eq_presets SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM eq_presets")
    suspend fun count(): Int
}
