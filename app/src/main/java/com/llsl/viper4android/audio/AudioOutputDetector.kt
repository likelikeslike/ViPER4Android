package com.llsl.viper4android.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.llsl.viper4android.utils.FileLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioOutputDetector(
    context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _activeDevice = MutableStateFlow(detectActiveDevice(audioManager))
    val activeDevice: StateFlow<AudioDevice> = _activeDevice.asStateFlow()

    private val callback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                val connected = checkHeadphoneConnected(audioManager)
                val device = detectActiveDevice(audioManager)
                FileLogger.i(
                    "AudioOutput",
                    "Output device added: headphone=${if (connected) "connected" else "disconnected"} device=${device.name}",
                )
                _activeDevice.value = device
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                val connected = checkHeadphoneConnected(audioManager)
                val device = detectActiveDevice(audioManager)
                FileLogger.i(
                    "AudioOutput",
                    "Output device removed: headphone=${if (connected) "connected" else "disconnected"} device=${device.name}",
                )
                _activeDevice.value = device
            }
        }

    init {
        val initialDevice = _activeDevice.value
        FileLogger.i(
            "AudioOutput",
            "Output init: headphone=${if (initialDevice.isHeadphone) "connected" else "disconnected"} device=${initialDevice.name}",
        )
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    @Suppress("NewApi")
    companion object {
        private val HEADPHONE_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )

        private val BT_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_BROADCAST,
                AudioDeviceInfo.TYPE_HEARING_AID,
            )

        private val USB_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE,
            )

        private val AUTO_TYPES =
            setOf(
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX,
                AudioDeviceInfo.TYPE_BUS,
            )

        private fun checkHeadphoneConnected(audioManager: AudioManager): Boolean =
            audioManager
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .any { it.type in HEADPHONE_TYPES }

        private fun detectActiveDevice(audioManager: AudioManager): AudioDevice {
            val routedOutputs =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val mediaAttrs =
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    audioManager.getAudioDevicesForAttributes(mediaAttrs).filter { it.isSink }
                } else {
                    emptyList()
                }

            val outputs =
                routedOutputs.ifEmpty {
                    audioManager
                        .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                        .filter { it.isSink }
                }

            for (dev in outputs) {
                FileLogger.d(
                    "AudioOutput",
                    "  output: type=${dev.type} name=${dev.productName} addr=${dev.address} id=${dev.id}",
                )
            }

            val androidAuto = outputs.firstOrNull { it.type in AUTO_TYPES }
            if (androidAuto != null) {
                return AudioDevice(
                    id = AudioDevice.ID_ANDROID_AUTO,
                    name = "Android Auto",
                    type = androidAuto.type,
                    isHeadphone = false,
                )
            }

            val headphone = outputs.firstOrNull { it.type in HEADPHONE_TYPES }
            if (headphone != null) {
                val isBt = headphone.type in BT_TYPES
                val productName = headphone.productName?.toString()?.takeIf { it.isNotBlank() }
                return if (isBt) {
                    val address =
                        headphone.address.takeIf { it.isNotBlank() } ?: "bt_${headphone.id}"
                    AudioDevice(
                        id = address,
                        name = productName ?: getTypeName(headphone.type),
                        type = headphone.type,
                        isHeadphone = true,
                    )
                } else if (headphone.type in USB_TYPES) {
                    val address =
                        headphone.address.takeIf { it.isNotBlank() } ?: "usb_${headphone.id}"
                    AudioDevice(
                        id = address,
                        name = productName ?: "USB Audio",
                        type = headphone.type,
                        isHeadphone = true,
                    )
                } else {
                    AudioDevice(
                        id = AudioDevice.ID_WIRED,
                        name = productName ?: "Wired Headphone",
                        type = headphone.type,
                        isHeadphone = true,
                    )
                }
            }

            return AudioDevice.DEFAULT_SPEAKER
        }

        private fun getTypeName(type: Int): String =
            when (type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth A2DP"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE Headset"
                AudioDeviceInfo.TYPE_BLE_BROADCAST -> "BLE Broadcast"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
                else -> "Bluetooth"
            }
    }
}
