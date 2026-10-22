package com.llsl.viper4android.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.llsl.viper4android.data.model.DeviceSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceSettingsDao {
    @Query("SELECT * FROM device_settings ORDER BY last_connected DESC")
    fun getAll(): Flow<List<DeviceSettings>>

    @Query("SELECT * FROM device_settings WHERE device_id = :deviceId")
    suspend fun getByDeviceId(deviceId: String): DeviceSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: DeviceSettings)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(settings: DeviceSettings)

    @Transaction
    suspend fun getOrInsert(settings: DeviceSettings): DeviceSettings {
        insertIfAbsent(settings)
        return checkNotNull(getByDeviceId(settings.deviceId))
    }

    @Query("UPDATE device_settings SET device_name = :name WHERE device_id = :deviceId")
    suspend fun rename(
        deviceId: String,
        name: String,
    )

    @Query("DELETE FROM device_settings WHERE device_id = :deviceId")
    suspend fun deleteByDeviceId(deviceId: String)

    @Query(
        "UPDATE device_settings SET device_type = :deviceType, is_headphone = :isHeadphone, " +
            "last_connected = :time WHERE device_id = :deviceId",
    )
    suspend fun updateConnection(
        deviceId: String,
        deviceType: Int,
        isHeadphone: Boolean,
        time: Long,
    )
}
