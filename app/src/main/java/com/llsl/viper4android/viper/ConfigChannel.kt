package com.llsl.viper4android.viper

import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import java.io.File
import java.io.RandomAccessFile
import java.lang.invoke.VarHandle
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

data class FileDriverStatus(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val streaming: Boolean = false,
    val sampleRate: Int = 0,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
)

object ConfigChannel {
    private const val SHM_STATUS_PATH = "/data/local/tmp/v4a/shm_status.bin"
    private const val SHM_PARAMS_PATH = "/data/local/tmp/v4a/shm_params.bin"
    private const val SHM_BULK_PATH = "/data/local/tmp/v4a/shm_bulk.bin"

    private const val SHM_MAGIC = 0x534D3456 // 'V4MS' little-endian
    private const val FORMAT_VERSION = 5

    private const val STATUS_SHM_SIZE = 256
    private const val PARAM_SHM_SIZE = 4096
    private const val BULK_SHM_SIZE = 4 * 1024

    // Bulk region is split into two independent sub-channels so a DDC write and a
    // convolver write cannot clobber each other before the driver polls the slot.
    private const val BULK_DDC_BASE = 0
    private const val BULK_DDC_REGION_SIZE = 2 * 1024
    private const val BULK_CONVOLVER_BASE = BULK_DDC_REGION_SIZE
    private const val BULK_CONVOLVER_REGION_SIZE = 2 * 1024

    private const val STATUS_DATA_OFFSET = 20

    private const val PARAMS_HEADER_SIZE = 16
    private const val PARAMS_ACTIVE_OFFSET = 8
    private const val PARAMS_UPDATE_COUNT_OFFSET = 12
    private const val PARAMS_SLOT_A_OFFSET = PARAMS_HEADER_SIZE
    private const val PARAMS_SLOT_B_OFFSET = PARAMS_HEADER_SIZE + ViperParamsLayout.SIZE

    private const val BULK_HEADER_SIZE = 32
    private const val BULK_SEQ_OFFSET = 8
    private const val BULK_COMMAND_OFFSET = 12
    private const val BULK_DATA_SIZE_OFFSET = 16
    private const val BULK_CMD_DDC = 1
    private const val BULK_CMD_CONVOLVER_PATH = 2
    private const val BULK_CMD_DDC_RESET = 3
    private const val BULK_CMD_CONVOLVER_RESET = 4

    private var statusBuffer: MappedByteBuffer? = null
    private var paramsBuffer: MappedByteBuffer? = null
    private var bulkBuffer: MappedByteBuffer? = null

    private val initDone = AtomicBoolean(false)
    private val writeLock = Any()

    private var producerActiveSlot: Int = 1
    private var currentUpdateCount: Int = 0
    private var currentDdcSeq: Int = 0
    private var currentConvolverSeq: Int = 0

    private var lastStatusSeq = 0
    private var lastProcessedFrames = 0L
    private var cachedStatus: FileDriverStatus? = null

    fun ensureInitialized() {
        if (initDone.get() && statusBuffer != null) return
        synchronized(writeLock) {
            if (initDone.get() && statusBuffer != null) return
            try {
                if (mapShm()) {
                    FileLogger.i("ConfigChannel", "Direct mmap succeeded")
                    initDone.set(true)
                    return
                }
                FileLogger.w("ConfigChannel", "Direct mmap failed, trying su fallback")
                createShmViaSu()
                mapShm()
                initDone.set(true)
            } catch (e: Exception) {
                FileLogger.e("ConfigChannel", "Init failed", e)
            }
        }
    }

    private fun createShmViaSu() {
        RootShell.exec(
            "mkdir -p /data/local/tmp/v4a && " +
                "[ ! -f $SHM_STATUS_PATH ] && dd if=/dev/zero of=$SHM_STATUS_PATH bs=$STATUS_SHM_SIZE count=1 2>/dev/null; " +
                "[ ! -f $SHM_PARAMS_PATH ] && dd if=/dev/zero of=$SHM_PARAMS_PATH bs=$PARAM_SHM_SIZE count=1 2>/dev/null; " +
                "[ ! -f $SHM_BULK_PATH ]   && dd if=/dev/zero of=$SHM_BULK_PATH   bs=$BULK_SHM_SIZE   count=1 2>/dev/null; " +
                "chmod 666 $SHM_STATUS_PATH $SHM_PARAMS_PATH $SHM_BULK_PATH; " +
                "chcon u:object_r:shell_data_file:s0 $SHM_STATUS_PATH $SHM_PARAMS_PATH $SHM_BULK_PATH 2>/dev/null; " +
                "echo ok",
        )
    }

    private fun mapShm(): Boolean =
        try {
            statusBuffer = mapSingleShm(SHM_STATUS_PATH, STATUS_SHM_SIZE)
            paramsBuffer = mapSingleShm(SHM_PARAMS_PATH, PARAM_SHM_SIZE)
            bulkBuffer = mapSingleShm(SHM_BULK_PATH, BULK_SHM_SIZE)
            statusBuffer != null && paramsBuffer != null && bulkBuffer != null
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "mmap failed, will use su fallback", e)
            false
        }

    private fun mapSingleShm(
        path: String,
        size: Int,
    ): MappedByteBuffer? {
        val file = File(path)
        if (!file.exists() || file.length() < size) {
            FileLogger.w(
                "ConfigChannel",
                "SHM file not ready: $path exists=${file.exists()} size=${file.length()}",
            )
            return null
        }
        val raf = RandomAccessFile(file, "rw")
        val buf = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size.toLong())
        buf.order(ByteOrder.LITTLE_ENDIAN)
        raf.close()

        val magic = buf.getInt(0)
        val version = buf.getInt(4)
        if (magic != SHM_MAGIC || version != FORMAT_VERSION) {
            buf.putInt(0, SHM_MAGIC)
            buf.putInt(4, FORMAT_VERSION)
            buf.putInt(8, 0)
            buf.putInt(12, 0)
        }
        FileLogger.i("ConfigChannel", "mmap OK: $path size=$size")
        return buf
    }

    /**
     * Write a complete viper::ViPERParams snapshot to shm. Single entry point
     * for parameter dispatch on the AIDL path.
     *
     * Note: VarHandle.releaseFence() is a `public static void` method, NOT
     * signature-polymorphic, so R8 minification cannot mangle it.
     */
    @Suppress("NewApi")
    fun writeFullState(state: EffectState) {
        ensureInitialized()
        synchronized(writeLock) {
            val buf =
                paramsBuffer ?: run {
                    FileLogger.w("ConfigChannel", "writeFullState: params buf NULL")
                    return
                }
            val nextSlot = 1 - producerActiveSlot
            val slotOffset =
                if (nextSlot == 0) PARAMS_SLOT_A_OFFSET else PARAMS_SLOT_B_OFFSET
            ViperParamsSerializer.write(buf, slotOffset, state)

            VarHandle.releaseFence()

            buf.putInt(PARAMS_ACTIVE_OFFSET, nextSlot)
            currentUpdateCount++
            buf.putInt(PARAMS_UPDATE_COUNT_OFFSET, currentUpdateCount)

            producerActiveSlot = nextSlot
        }
    }

    fun writeBulkDdc(
        perRateFloats: Int,
        coeffs: FloatArray,
    ) {
        ensureInitialized()
        synchronized(writeLock) {
            val buf =
                bulkBuffer ?: run {
                    FileLogger.w("ConfigChannel", "writeBulkDdc: bulk buf NULL")
                    return
                }
            require(coeffs.size == perRateFloats * 2) {
                "writeBulkDdc: coeffs.size=${coeffs.size} but expected " +
                    "${perRateFloats * 2} (perRateFloats * 2 rates)"
            }
            val payloadSize = 8 + coeffs.size * 4
            if (BULK_HEADER_SIZE + payloadSize > BULK_DDC_REGION_SIZE) {
                FileLogger.w(
                    "ConfigChannel",
                    "writeBulkDdc: payload $payloadSize exceeds DDC region",
                )
                return
            }
            buf.putInt(BULK_DDC_BASE + BULK_COMMAND_OFFSET, BULK_CMD_DDC)
            buf.putInt(BULK_DDC_BASE + BULK_DATA_SIZE_OFFSET, payloadSize)
            buf.position(BULK_DDC_BASE + BULK_HEADER_SIZE)
            buf.putInt(perRateFloats)
            buf.putInt(coeffs.size)
            for (f in coeffs) buf.putFloat(f)

            currentDdcSeq++
            buf.putInt(BULK_DDC_BASE + BULK_SEQ_OFFSET, currentDdcSeq)
        }
    }

    fun writeBulkConvolverPath(path: String) {
        ensureInitialized()
        synchronized(writeLock) {
            val buf =
                bulkBuffer ?: run {
                    FileLogger.w(
                        "ConfigChannel",
                        "writeBulkConvolverPath: bulk buf NULL",
                    )
                    return
                }
            val pathBytes = path.toByteArray(Charsets.UTF_8)
            if (BULK_HEADER_SIZE + pathBytes.size > BULK_CONVOLVER_REGION_SIZE) {
                FileLogger.w(
                    "ConfigChannel",
                    "writeBulkConvolverPath: path too long ${pathBytes.size}",
                )
                return
            }
            buf.putInt(BULK_CONVOLVER_BASE + BULK_COMMAND_OFFSET, BULK_CMD_CONVOLVER_PATH)
            buf.putInt(BULK_CONVOLVER_BASE + BULK_DATA_SIZE_OFFSET, pathBytes.size)
            buf.position(BULK_CONVOLVER_BASE + BULK_HEADER_SIZE)
            buf.put(pathBytes)

            currentConvolverSeq++
            buf.putInt(BULK_CONVOLVER_BASE + BULK_SEQ_OFFSET, currentConvolverSeq)
        }
    }

    fun writeBulkDdcReset() {
        ensureInitialized()
        synchronized(writeLock) {
            val buf =
                bulkBuffer ?: run {
                    FileLogger.w("ConfigChannel", "writeBulkDdcReset: bulk buf NULL")
                    return
                }
            buf.putInt(BULK_DDC_BASE + BULK_COMMAND_OFFSET, BULK_CMD_DDC_RESET)
            buf.putInt(BULK_DDC_BASE + BULK_DATA_SIZE_OFFSET, 0)

            currentDdcSeq++
            buf.putInt(BULK_DDC_BASE + BULK_SEQ_OFFSET, currentDdcSeq)
        }
    }

    fun writeBulkConvolverReset() {
        ensureInitialized()
        synchronized(writeLock) {
            val buf =
                bulkBuffer ?: run {
                    FileLogger.w("ConfigChannel", "writeBulkConvolverReset: bulk buf NULL")
                    return
                }
            buf.putInt(BULK_CONVOLVER_BASE + BULK_COMMAND_OFFSET, BULK_CMD_CONVOLVER_RESET)
            buf.putInt(BULK_CONVOLVER_BASE + BULK_DATA_SIZE_OFFSET, 0)

            currentConvolverSeq++
            buf.putInt(BULK_CONVOLVER_BASE + BULK_SEQ_OFFSET, currentConvolverSeq)
        }
    }

    fun readStatus(): FileDriverStatus? {
        ensureInitialized()
        try {
            val buf = statusBuffer
            if (buf != null) {
                val statusSeq = buf.getInt(8)
                if (statusSeq == 0) return cachedStatus
                cachedStatus = parseStatusFromShm(buf)
                lastStatusSeq = statusSeq
                return cachedStatus
            }
            return readStatusViaSu()
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "Failed to read status", e)
            return cachedStatus
        }
    }

    private fun parseStatusFromShm(buf: MappedByteBuffer): FileDriverStatus {
        val base = STATUS_DATA_OFFSET
        val enabled = buf.getInt(base) != 0
        val configured = buf.getInt(base + 4) != 0
        val processedFrames = buf.getLong(base + 8)
        val streaming =
            processedFrames > 0 && processedFrames != lastProcessedFrames
        lastProcessedFrames = processedFrames
        val sampleRate = buf.getInt(base + 16)
        val versionCode = buf.getInt(base + 20)

        val nameBytes = ByteArray(64)
        buf.position(base + 24)
        buf.get(nameBytes)
        val nameEnd = nameBytes.indexOf(0.toByte())
        val versionName =
            if (nameEnd >= 0) String(nameBytes, 0, nameEnd) else String(nameBytes)

        val archBytes = ByteArray(32)
        buf.position(base + 88)
        buf.get(archBytes)
        val archEnd = archBytes.indexOf(0.toByte())
        val architecture =
            if (archEnd >= 0) String(archBytes, 0, archEnd) else String(archBytes)

        return FileDriverStatus(
            enabled = enabled,
            configured = configured,
            streaming = streaming,
            sampleRate = sampleRate,
            versionCode = versionCode,
            versionName = versionName,
            architecture = architecture,
        )
    }

    private fun readStatusViaSu(): FileDriverStatus? {
        try {
            val statusRegionSize = 128
            val process =
                RootShell.exec(
                    "xxd -p -l $statusRegionSize -s $STATUS_DATA_OFFSET $SHM_STATUS_PATH",
                )
            val hex =
                process.inputStream
                    .bufferedReader()
                    .readText()
                    .trim()
                    .replace("\n", "")
            if (hex.length < 256) return null

            val bytes = ByteArray(128)
            for (i in 0 until 128) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val enabled = buf.getInt(0) != 0
            val configured = buf.getInt(4) != 0
            val processedFrames = buf.getLong(8)
            val streaming =
                processedFrames > 0 && processedFrames != lastProcessedFrames
            lastProcessedFrames = processedFrames
            val sampleRate = buf.getInt(16)
            val versionCode = buf.getInt(20)

            val nameBytes = ByteArray(64)
            buf.position(24)
            buf.get(nameBytes)
            val nameEnd = nameBytes.indexOf(0.toByte())
            val versionName =
                if (nameEnd >= 0) String(nameBytes, 0, nameEnd) else String(nameBytes)

            val archBytes = ByteArray(32)
            buf.position(88)
            buf.get(archBytes)
            val archEnd = archBytes.indexOf(0.toByte())
            val architecture =
                if (archEnd >= 0) String(archBytes, 0, archEnd) else String(archBytes)

            val status =
                FileDriverStatus(
                    enabled = enabled,
                    configured = configured,
                    streaming = streaming,
                    sampleRate = sampleRate,
                    versionCode = versionCode,
                    versionName = versionName,
                    architecture = architecture,
                )
            cachedStatus = status
            return status
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "readStatusViaSu failed", e)
            return null
        }
    }
}
