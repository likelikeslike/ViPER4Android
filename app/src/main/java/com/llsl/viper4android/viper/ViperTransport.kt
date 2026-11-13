package com.llsl.viper4android.viper

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.utils.FileLogger

sealed interface ParamValue {
    val index: Int

    data class Bool(
        val v: Boolean,
        override val index: Int = -1,
    ) : ParamValue

    data class IntV(
        val v: Int,
        override val index: Int = -1,
    ) : ParamValue

    data class FloatV(
        val v: Float,
        override val index: Int = -1,
    ) : ParamValue

    data class Ints(
        val v: IntArray,
        override val index: Int = -1,
    ) : ParamValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Ints

            if (index != other.index) return false
            if (!v.contentEquals(other.v)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + v.contentHashCode()
            return result
        }
    }

    data class Floats(
        val v: FloatArray,
        override val index: Int = -1,
    ) : ParamValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Floats

            if (index != other.index) return false
            if (!v.contentEquals(other.v)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + v.contentHashCode()
            return result
        }
    }

    data class Bytes(
        val v: ByteArray,
        override val index: Int = -1,
    ) : ParamValue {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Bytes

            if (index != other.index) return false
            if (!v.contentEquals(other.v)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = index
            result = 31 * result + v.contentHashCode()
            return result
        }
    }
}

enum class BulkKind {
    CONVOLVER,
    DDC,
}

data class DriverStatus(
    val enabled: Boolean,
    val sampleRate: Int,
    val processedFrames: Long,
    val kernelId: Int,
    val versionCode: Int,
    val versionName: String,
    val arch: String,
)

interface ViperTransport {
    var bulkSideChannels: ((ddcDevice: String, convolverKernel: String, force: Boolean) -> Unit)?

    fun set(
        param: Int,
        value: ParamValue,
    )

    fun pushFullState(
        state: EffectState,
        forceBulk: Boolean = false,
    )

    fun probeStatus(): DriverStatus?

    fun shouldStream(
        kind: BulkKind,
        key: String,
        force: Boolean,
    ): Boolean

    fun markStreamed(
        kind: BulkKind,
        key: String,
    )
}

class AidlTransport : ViperTransport {
    override var bulkSideChannels: ((ddcDevice: String, convolverKernel: String, force: Boolean) -> Unit)? = null

    private val bulkKeys = mutableMapOf<BulkKind, String>()

    override fun set(
        param: Int,
        value: ParamValue,
    ) {
        when (value) {
            is ParamValue.Bool -> {
                if (value.index != -1) {
                    ViperControlClient.dispatchParam(param, value.index, value.v)
                } else {
                    ViperControlClient.dispatchParam(param, value.v)
                }
            }

            is ParamValue.IntV -> {
                if (value.index != -1) {
                    ViperControlClient.dispatchParam(param, value.index, value.v)
                } else {
                    ViperControlClient.dispatchParam(param, value.v)
                }
            }

            is ParamValue.FloatV -> {
                if (value.index != -1) {
                    ViperControlClient.dispatchParam(param, value.index, value.v)
                } else {
                    ViperControlClient.dispatchParam(param, value.v)
                }
            }

            is ParamValue.Ints -> {
                if (value.v.size == 3) {
                    ViperControlClient.dispatchParam(param, value.v[0], value.v[1], value.v[2])
                } else {
                    FileLogger.w("AidlTransport", "Dropping Ints param $param: size ${value.v.size} != 3")
                }
            }

            is ParamValue.Floats -> {
                ViperControlClient.dispatchParam(param, value.v)
            }

            is ParamValue.Bytes -> {
                ViperControlClient.dispatchParam(param, value.v)
            }
        }
    }

    override fun pushFullState(
        state: EffectState,
        forceBulk: Boolean,
    ) {
        val snapshotApplied =
            ViperControlClient.dispatchParam(
                ViperParams.PARAM_SET_FULL_PARAMS,
                ViperParamsSerializer.toByteArray(state),
            )
        if (!snapshotApplied) {
            FileLogger.w("AidlTransport", "Full snapshot dispatch failed")
        }
        val hasDdc = state.ddc.enable && state.ddc.device.isNotEmpty()
        val hasKernel = state.convolver.enable && state.convolver.kernelFile.isNotEmpty()
        if (hasDdc || hasKernel) {
            bulkSideChannels?.invoke(
                if (hasDdc) state.ddc.device else "",
                if (hasKernel) state.convolver.kernelFile else "",
                forceBulk,
            )
        }
    }

    override fun probeStatus(): DriverStatus? = ViperControlClient.getStatus()

    override fun shouldStream(
        kind: BulkKind,
        key: String,
        force: Boolean,
    ): Boolean = force || bulkKeys[kind] != key

    override fun markStreamed(
        kind: BulkKind,
        key: String,
    ) {
        bulkKeys[kind] = key
    }
}

class HidlTransport(
    private val effects: () -> List<ViperEffect>,
    private val activeEffect: () -> ViperEffect? = { null },
) : ViperTransport {
    override var bulkSideChannels: ((ddcDevice: String, convolverKernel: String, force: Boolean) -> Unit)? = null

    private val bulkKeys = mutableMapOf<BulkKind, String>()

    override fun set(
        param: Int,
        value: ParamValue,
    ) {
        effects().forEach { it.setParameter(param, value) }
    }

    override fun pushFullState(
        state: EffectState,
        forceBulk: Boolean,
    ) {
        val snapshot = ParamValue.Bytes(ViperParamsSerializer.toByteArray(state))
        effects().forEach {
            val snapshotApplied = it.setParameter(ViperParams.PARAM_SET_FULL_PARAMS, snapshot)
            if (!snapshotApplied) {
                FileLogger.w("HidlTransport", "Full snapshot dispatch failed")
            }
        }
        val hasDdc = state.ddc.enable && state.ddc.device.isNotEmpty()
        val hasKernel = state.convolver.enable && state.convolver.kernelFile.isNotEmpty()
        if (hasDdc || hasKernel) {
            bulkSideChannels?.invoke(
                if (hasDdc) state.ddc.device else "",
                if (hasKernel) state.convolver.kernelFile else "",
                true,
            )
        }
    }

    override fun probeStatus(): DriverStatus? {
        val active = activeEffect()
        if (active != null && active.isCreated) return active.getStatus()
        val probe = ViperEffect(0, ViperEffect.EFFECT_TYPE_UUID)
        if (!probe.create()) {
            probe.release()
            return null
        }
        val status = probe.getStatus()
        probe.release()
        return status
    }

    override fun shouldStream(
        kind: BulkKind,
        key: String,
        force: Boolean,
    ): Boolean = force || bulkKeys[kind] != key

    override fun markStreamed(
        kind: BulkKind,
        key: String,
    ) {
        bulkKeys[kind] = key
    }
}
