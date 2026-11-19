package com.llsl.viper4android.data

import android.content.Context
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.deserializeEffectPrefs
import com.llsl.viper4android.effect.serializeEffectPrefs
import org.json.JSONObject

/** Matches the current audio-device settings against saved presets. */
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

    fun activePresetId(context: Context, presets: List<Preset>): Long? {
        val prefs = prefs(context)
        val deviceId = prefs.getString(CURRENT_DEVICE, null) ?: return null
        val current = prefs.all[DEVICE_PREFIX + deviceId] as? String ?: return null
        return presets.firstOrNull { preset ->
            try {
                val saved = deserializeEffectPrefs(JSONObject(preset.settingsJson), EffectState())
                serializeEffectPrefs(saved).toString() == current
            } catch (_: Exception) {
                false
            }
        }?.id
    }

    /** Returns true if the visible device's settings changed. */
    fun setState(context: Context, deviceId: String, state: EffectState): Boolean {
        if (deviceId.isBlank()) return false
        val prefs = prefs(context)
        val key = DEVICE_PREFIX + deviceId
        val current = serializeEffectPrefs(state).toString()
        if (prefs.all[key] == current) return false
        prefs.edit().putString(key, current).apply()
        return prefs.getString(CURRENT_DEVICE, null) == deviceId
    }

    fun forgetDevice(context: Context, deviceId: String): Boolean {
        val prefs = prefs(context)
        val key = DEVICE_PREFIX + deviceId
        val wasActive = prefs.getString(CURRENT_DEVICE, null) == deviceId
        prefs.edit().remove(key).apply()
        return wasActive
    }
}
