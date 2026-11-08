package com.llsl.viper4android.data.model

import android.media.AudioDeviceInfo
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_settings")
data class DeviceSettings(
    @PrimaryKey
    @ColumnInfo(name = "device_id")
    val deviceId: String,
    @ColumnInfo(name = "device_name")
    val deviceName: String,
    @ColumnInfo(name = "is_headphone")
    val isHeadphone: Boolean,
    @ColumnInfo(name = "settings_json")
    val settingsJson: String,
    @ColumnInfo(name = "last_connected")
    val lastConnected: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "device_type", defaultValue = "0")
    val deviceType: Int = AudioDeviceInfo.TYPE_UNKNOWN,
)
