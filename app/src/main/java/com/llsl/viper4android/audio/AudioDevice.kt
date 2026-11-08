package com.llsl.viper4android.audio

import android.media.AudioDeviceInfo

data class AudioDevice(
    val id: String,
    val name: String,
    val type: Int,
    val isHeadphone: Boolean,
) {
    companion object {
        const val ID_SPEAKER = "speaker"
        const val ID_WIRED = "wired_headphone"
        const val ID_ANDROID_AUTO = "android_auto"

        val DEFAULT_SPEAKER = AudioDevice(ID_SPEAKER, "Speaker", AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false)
    }
}
