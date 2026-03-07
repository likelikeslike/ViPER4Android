package com.llsl.viper4android.audio

import android.media.audiofx.AudioEffect

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

class ViperEffect(private val audioSessionId: Int, private val typeUuid: UUID = EFFECT_TYPE_UUID) {

    companion object {
        val EFFECT_TYPE_UUID: UUID = UUID.fromString("ec7178ec-e5e1-4432-a3f4-4657e6795210")
        val EFFECT_TYPE_UUID_AIDL: UUID = UUID.fromString("7261676f-6d75-7369-6364-28e2fd3ac39e")
        val EFFECT_UUID: UUID = UUID.fromString("90380da3-8536-4744-a6a3-5731970e640f")

        private val ctor: Constructor<AudioEffect>? by lazy {
            try {
                AudioEffect::class.java
                    .getConstructor(
                        UUID::class.java,
                        UUID::class.java,
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType
                    )
            } catch (e: Exception) {
                FileLogger.e("Effect", "AudioEffect constructor not found", e)
                null
            }
        }

        private val setParamMethod: Method? by lazy {
            try {
                AudioEffect::class.java
                    .getMethod("setParameter", ByteArray::class.java, ByteArray::class.java)
            } catch (e: Exception) {
                FileLogger.e("Effect", "setParameter method not found", e)
                null
            }
        }

        private val getParamMethod: Method? by lazy {
            try {
                AudioEffect::class.java
                    .getMethod("getParameter", ByteArray::class.java, ByteArray::class.java)
            } catch (e: Exception) {
                FileLogger.e("Effect", "getParameter method not found", e)
                null
            }
        }
    }

    private var effect: AudioEffect? = null

    val isCreated: Boolean
        get() = effect != null

    fun create(): Boolean {
        if (effect != null) return true
        val c = ctor
        if (c == null) {
            FileLogger.e("Effect", "AudioEffect constructor is null, reflection failed")
            return false
        }
        return try {
            FileLogger.d(
                "Effect",
                "Creating AudioEffect: type=$typeUuid, uuid=$EFFECT_UUID, session=$audioSessionId"
            )
            effect = c.newInstance(typeUuid, EFFECT_UUID, 0, audioSessionId)
            FileLogger.d("Effect", "AudioEffect created successfully for session $audioSessionId")
            true
        } catch (e: Exception) {
            FileLogger.e("Effect", "Failed to create AudioEffect for session $audioSessionId", e)
            val cause = e.cause
            if (cause != null) {
                FileLogger.e("Effect", "Root cause: ${cause.javaClass.name}: ${cause.message}")
            }
            false
        }
    }

    fun release() {
        effect?.release()
        effect = null
    }

    var enabled: Boolean
        get() = effect?.enabled ?: false
        set(value) {
            effect?.enabled = value
        }

    fun setParameter(param: Int, value: Int) {
        val fx = effect ?: return
        val m = setParamMethod ?: return
        val paramBytes = intToBytes(param)
        val valueBytes = intToBytes(value)
        try {
            val status = m.invoke(fx, paramBytes, valueBytes) as Int
            if (status != AudioEffect.SUCCESS) {
                FileLogger.w("Effect", "setParameter($param, $value) returned $status")
            }
        } catch (e: Exception) {
            FileLogger.e("Effect", "setParameter($param, $value) invoke failed", e)
        }
    }

    fun setParameter(param: Int, val1: Int, val2: Int) {
        val fx = effect ?: return
        val m = setParamMethod ?: return
        val paramBytes = intToBytes(param)
        val valueBytes =
            ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(val1).putInt(val2).array()
        try {
            val status = m.invoke(fx, paramBytes, valueBytes) as Int
            if (status != AudioEffect.SUCCESS) {
                FileLogger.w("Effect", "setParameter($param, $val1, $val2) returned $status")
            }
        } catch (e: Exception) {
            FileLogger.e("Effect", "setParameter($param, $val1, $val2) invoke failed", e)
        }
    }

    fun setParameter(param: Int, val1: Int, val2: Int, val3: Int) {
        val fx = effect ?: return
        val m = setParamMethod ?: return
        val paramBytes = intToBytes(param)
        val valueBytes = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(val1).putInt(val2).putInt(val3).array()
        try {
            val status = m.invoke(fx, paramBytes, valueBytes) as Int
            if (status != AudioEffect.SUCCESS) {
                FileLogger.w("Effect", "setParameter($param, $val1, $val2, $val3) returned $status")
            }
        } catch (e: Exception) {
            FileLogger.e("Effect", "setParameter($param, $val1, $val2, $val3) invoke failed", e)
        }
    }

    fun setParameter(param: Int, value: ByteArray) {
        val fx = effect ?: return
        val m = setParamMethod ?: return
        val paramBytes = intToBytes(param)
        try {
            val status = m.invoke(fx, paramBytes, value) as Int
            if (status != AudioEffect.SUCCESS) {
                FileLogger.w(
                    "Effect",
                    "setParameter($param, byteArray[${value.size}]) returned $status"
                )
            }
        } catch (e: Exception) {
            FileLogger.e(
                "Effect",
                "setParameter($param, byteArray[${value.size}]) invoke failed",
                e
            )
        }
    }

    fun getParameter(param: Int): Int {
        val fx = effect ?: return -1
        val m = getParamMethod ?: return -1
        val paramBytes = intToBytes(param)
        val valueBytes = ByteArray(4)
        try {
            val status = m.invoke(fx, paramBytes, valueBytes) as Int
            if (status < 0) {
                FileLogger.w("Effect", "getParameter($param) returned status $status")
                return -1
            }
            return bytesToInt(valueBytes)
        } catch (e: Exception) {
            FileLogger.e("Effect", "getParameter($param) invoke failed", e)
            return -1
        }
    }

    fun getParameter(param: Int, size: Int): ByteArray {
        val fx = effect ?: return ByteArray(0)
        val m = getParamMethod ?: return ByteArray(0)
        val paramBytes = intToBytes(param)
        val valueBytes = ByteArray(size)
        try {
            val status = m.invoke(fx, paramBytes, valueBytes) as Int
            if (status < 0) {
                FileLogger.w("Effect", "getParameter($param, size=$size) returned status $status")
                return ByteArray(0)
            }
            return valueBytes
        } catch (e: Exception) {
            FileLogger.e("Effect", "getParameter($param, size=$size) invoke failed", e)
            return ByteArray(0)
        }
    }

    fun getDriverVersionCode(): Int = getParameter(ViperParams.PARAM_GET_DRIVER_VERSION_CODE)

    fun getArchitectureString(): String {
        val bytes = getParameter(ViperParams.PARAM_GET_ARCHITECTURE, 64)
        if (bytes.isEmpty()) return "Unknown"
        val nullIdx = bytes.indexOf(0.toByte())
        return if (nullIdx >= 0) String(bytes, 0, nullIdx) else String(bytes)
    }

    fun isStreaming(): Boolean = getParameter(ViperParams.PARAM_GET_STREAMING) == 1

    private fun intToBytes(value: Int): ByteArray =
        ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()

    private fun bytesToInt(bytes: ByteArray): Int =
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
}
