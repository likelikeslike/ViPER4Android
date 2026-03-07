package com.llsl.viper4android.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputDetector(context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _isHeadphoneConnected = MutableStateFlow(checkHeadphoneConnected(audioManager))
    val isHeadphoneConnected: StateFlow<Boolean> = _isHeadphoneConnected.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val connected = checkHeadphoneConnected(audioManager)
            FileLogger.i(
                "AudioOutput",
                "Output device added: headphone=${if (connected) "connected" else "disconnected"}"
            )
            _isHeadphoneConnected.value = connected
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            val connected = checkHeadphoneConnected(audioManager)
            FileLogger.i(
                "AudioOutput",
                "Output device removed: headphone=${if (connected) "connected" else "disconnected"}"
            )
            _isHeadphoneConnected.value = connected
        }
    }

    init {
        val initial = _isHeadphoneConnected.value
        FileLogger.i(
            "AudioOutput",
            "Output init: headphone=${if (initial) "connected" else "disconnected"}"
        )
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    companion object {
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        )

        fun isHeadphoneConnected(audioManager: AudioManager): Boolean =
            checkHeadphoneConnected(audioManager)

        private fun checkHeadphoneConnected(audioManager: AudioManager): Boolean =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type in HEADPHONE_TYPES }
    }
}
