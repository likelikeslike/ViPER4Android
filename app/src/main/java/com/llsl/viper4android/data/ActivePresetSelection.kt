package com.llsl.viper4android.data

import android.content.Context

/** Tracks the active preset independently for each audio output device. */
object ActivePresetSelection {
    private const val STORAGE = "viper_device_preset_selection"
    private const val CURRENT_DEVICE = "current_audio_device"
    private const val DEVICE_PREFIX = "device:"

    private fun prefs(context: Context) =
        context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)

    fun selectDevice(context: Context, deviceId: String) {
        if (deviceId.isBlank()) return
        val prefs = prefs(context)
        if (prefs.getString(CURRENT_DEVICE, null) != deviceId) {
            prefs.edit().putString(CURRENT_DEVICE, deviceId).apply()
        }
    }

    fun activePresetId(context: Context): Long? {
        val prefs = prefs(context)
        val deviceId = prefs.getString(CURRENT_DEVICE, null) ?: return null
        return prefs.getLong(DEVICE_PREFIX + deviceId, -1L).takeIf { it > 0 }
    }

    /** Returns true only if the visible active-device selection changed. */
    fun setPreset(context: Context, deviceId: String, presetId: Long?): Boolean {
        if (deviceId.isBlank()) return false
        val prefs = prefs(context)
        val key = DEVICE_PREFIX + deviceId
        val next = presetId?.takeIf { it > 0 } ?: -1L
        if (prefs.getLong(key, -1L) == next) return false
        prefs.edit().putLong(key, next).apply()
        return prefs.getString(CURRENT_DEVICE, null) == deviceId
    }

    fun forgetPreset(context: Context, presetId: Long) {
        val prefs = prefs(context)
        val keys = prefs.all.filter { (key, value) ->
            key.startsWith(DEVICE_PREFIX) && value == presetId
        }.keys
        if (keys.isEmpty()) return
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    fun forgetAllPresets(context: Context) {
        val prefs = prefs(context)
        val keys = prefs.all.keys.filter { it.startsWith(DEVICE_PREFIX) }
        prefs.edit().apply { keys.forEach { remove(it) } }.apply()
    }

    fun forgetDevice(context: Context, deviceId: String): Boolean {
        val prefs = prefs(context)
        val key = DEVICE_PREFIX + deviceId
        val wasActive = prefs.getString(CURRENT_DEVICE, null) == deviceId
        prefs.edit().remove(key).apply()
        return wasActive
    }
}
