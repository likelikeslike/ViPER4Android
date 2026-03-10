package com.llsl.viper4android.audio

import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.atomic.AtomicBoolean

data class ParamEntry(val paramId: Int, val values: IntArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParamEntry) return false
        return paramId == other.paramId && values.contentEquals(other.values)
    }

    override fun hashCode(): Int = 31 * paramId + values.contentHashCode()
}

data class ByteArrayParam(val paramId: Int, val data: ByteArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ByteArrayParam) return false
        return paramId == other.paramId && data.contentEquals(other.data)
    }

    override fun hashCode(): Int = 31 * paramId + data.contentHashCode()
}

data class FileDriverStatus(
    val enabled: Boolean = false,
    val configured: Boolean = false,
    val streaming: Boolean = false,
    val sampleRate: Int = 0,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = ""
)

object ConfigChannel {

    private const val SHM_STATUS_PATH = "/data/local/tmp/v4a/shm_status.bin"
    private const val SHM_HP_PATH = "/data/local/tmp/v4a/shm_hp.bin"
    private const val SHM_SPK_PATH = "/data/local/tmp/v4a/shm_spk.bin"
    private const val SHM_MAGIC = 0x56344D53
    private const val FORMAT_VERSION = 3
    private const val STATUS_SHM_SIZE = 256
    private const val PARAM_SHM_SIZE = 32784
    private const val PARAMS_OFFSET = 16
    private const val PARAMS_REGION_SIZE = 32 * 1024
    private const val STATUS_DATA_OFFSET = 20

    private var statusBuffer: MappedByteBuffer? = null
    private var hpBuffer: MappedByteBuffer? = null
    private var spkBuffer: MappedByteBuffer? = null
    private val initDone = AtomicBoolean(false)
    private var lastStatusSeq = 0
    private var cachedStatus: FileDriverStatus? = null
    private val writeLock = Any()
    private val hpCurrentParams = linkedMapOf<Long, ParamEntry>()
    private val spkCurrentParams = linkedMapOf<Long, ParamEntry>()
    private var activeFxType: Int = 0
    private fun shmFxType(viperFxType: Int): Int = if (viperFxType == 2) 1 else 0

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
            "mkdir -p /data/local/tmp/v4a/hp /data/local/tmp/v4a/spk && " +
                    "[ ! -f $SHM_STATUS_PATH ] && dd if=/dev/zero of=$SHM_STATUS_PATH bs=$STATUS_SHM_SIZE count=1 2>/dev/null; " +
                    "[ ! -f $SHM_HP_PATH ] && dd if=/dev/zero of=$SHM_HP_PATH bs=$PARAM_SHM_SIZE count=1 2>/dev/null; " +
                    "[ ! -f $SHM_SPK_PATH ] && dd if=/dev/zero of=$SHM_SPK_PATH bs=$PARAM_SHM_SIZE count=1 2>/dev/null; " +
                    "chmod 666 $SHM_STATUS_PATH $SHM_HP_PATH $SHM_SPK_PATH; " +
                    "chcon u:object_r:shell_data_file:s0 $SHM_STATUS_PATH $SHM_HP_PATH $SHM_SPK_PATH 2>/dev/null; " +
                    "echo ok"
        )
    }

    private fun mapShm(): Boolean {
        return try {
            statusBuffer = mapSingleShm(SHM_STATUS_PATH, STATUS_SHM_SIZE)
            hpBuffer = mapSingleShm(SHM_HP_PATH, PARAM_SHM_SIZE)
            spkBuffer = mapSingleShm(SHM_SPK_PATH, PARAM_SHM_SIZE)
            statusBuffer != null && hpBuffer != null && spkBuffer != null
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "mmap failed, will use su fallback", e)
            false
        }
    }

    private fun mapSingleShm(path: String, size: Int): MappedByteBuffer? {
        val file = File(path)
        if (!file.exists() || file.length() < size) {
            FileLogger.w(
                "ConfigChannel",
                "SHM file not ready: $path exists=${file.exists()} size=${file.length()}"
            )
            return null
        }
        val raf = RandomAccessFile(file, "rw")
        val buf = raf.channel.map(FileChannel.MapMode.READ_WRITE, 0, size.toLong())
        buf.order(ByteOrder.LITTLE_ENDIAN)
        raf.close()

        val magic = buf.getInt(0)
        if (magic != SHM_MAGIC) {
            buf.putInt(0, SHM_MAGIC)
            buf.putInt(4, FORMAT_VERSION)
            buf.putInt(8, 0)
            buf.putInt(12, 0)
        }
        FileLogger.i("ConfigChannel", "mmap OK: $path size=$size")
        return buf
    }

    private fun paramKey(entry: ParamEntry): Long {
        val id = entry.paramId.toLong()
        if (
            entry.values.size >= 2
            && (entry.paramId == ViperParams.PARAM_HP_EQ_BAND_LEVEL || entry.paramId == ViperParams.PARAM_SPK_EQ_BAND_LEVEL)
        ) {
            return (id shl 32) or (entry.values[0].toLong() and 0xFFFFFFFFL)
        }
        return id shl 32
    }

    private fun activeParamBuffer(): MappedByteBuffer? {
        return if (shmFxType(activeFxType) == 0) hpBuffer else spkBuffer
    }

    private fun activeCurrentParams(): LinkedHashMap<Long, ParamEntry> {
        return if (shmFxType(activeFxType) == 0) hpCurrentParams else spkCurrentParams
    }

    fun setActiveFxType(fxType: Int) {
        synchronized(writeLock) {
            FileLogger.i(
                "ConfigChannel",
                "setActiveFxType: $fxType (was $activeFxType) statusBuf=${if (statusBuffer != null) "OK" else "NULL"}"
            )
            activeFxType = fxType
            val shmType = shmFxType(fxType)
            val buf = statusBuffer
            if (buf != null) {
                buf.putInt(12, shmType)
                val currentSeq = buf.getInt(16)
                buf.putInt(16, currentSeq + 1)
                FileLogger.i("ConfigChannel", "  fxTypeSeq now=${currentSeq + 1}")
            }
        }
    }

    fun writeFullState(
        params: List<ParamEntry>,
        byteArrayParams: List<ByteArrayParam>? = null,
        fxType: Int = activeFxType
    ) {
        if (params.isEmpty() && byteArrayParams.isNullOrEmpty()) return
        ensureInitialized()
        synchronized(writeLock) {
            val shmType = shmFxType(fxType)
            val channel = if (shmType == 0) "HP" else "SPK"
            val targetBuffer = if (shmType == 0) hpBuffer else spkBuffer
            val targetParams = if (shmType == 0) hpCurrentParams else spkCurrentParams
            targetParams.clear()
            for (entry in params) {
                targetParams[paramKey(entry)] = entry
            }
            val ba = byteArrayParams ?: emptyList()
            FileLogger.i(
                "ConfigChannel",
                "writeFullState: channel=$channel intParams=${targetParams.size} byteArrays=${ba.size} buf=${if (targetBuffer != null) "OK" else "NULL"}"
            )
            for (b in ba) {
                FileLogger.i(
                    "ConfigChannel",
                    "  byteArray: paramId=${b.paramId} size=${b.data.size}"
                )
            }
            writeParamsToShm(targetParams.values.toList(), byteArrayParams, targetBuffer)
        }
    }

    fun writeParams(params: List<ParamEntry>) {
        if (params.isEmpty()) return
        ensureInitialized()
        synchronized(writeLock) {
            val currentParams = activeCurrentParams()
            for (entry in params) {
                currentParams[paramKey(entry)] = entry
            }
            writeParamsToShm(params, buffer = activeParamBuffer())
        }
    }

    fun writeParamsByteArray(paramId: Int, data: ByteArray, extraParams: List<ParamEntry>? = null) {
        ensureInitialized()
        synchronized(writeLock) {
            val buf = activeParamBuffer()
            if (buf != null) {
                val byteCount = data.size
                val paddedInts = (byteCount + 3) / 4
                var totalSize = 8 + paddedInts * 4
                val extras = extraParams ?: emptyList()
                for (entry in extras) {
                    totalSize += 8 + entry.values.size * 4
                }
                val paramCount = 1 + extras.size
                if (totalSize + 8 > PARAMS_REGION_SIZE) {
                    FileLogger.w("ConfigChannel", "Byte array too large: $totalSize")
                    return
                }

                buf.position(PARAMS_OFFSET)
                buf.putInt(paramCount)
                buf.putInt(totalSize)
                buf.putInt(paramId)
                buf.putInt(-byteCount)
                buf.put(data)
                val padding = paddedInts * 4 - byteCount
                repeat(padding) { buf.put(0) }
                for (entry in extras) {
                    buf.putInt(entry.paramId)
                    buf.putInt(entry.values.size)
                    for (v in entry.values) buf.putInt(v)
                }

                incrementParamsSeq(buf)
            } else {
                writeByteArrayViaSu(paramId, data)
            }
        }
    }

    private fun writeParamsToShm(
        params: List<ParamEntry>,
        byteArrayParams: List<ByteArrayParam>? = null,
        buffer: MappedByteBuffer? = null
    ) {
        val buf = buffer ?: activeParamBuffer()
        if (buf != null) {
            var dataSize = 0
            for (entry in params) {
                dataSize += 8 + entry.values.size * 4
            }
            val byteEntries = byteArrayParams ?: emptyList()
            for (ba in byteEntries) {
                val paddedInts = (ba.data.size + 3) / 4
                dataSize += 8 + paddedInts * 4
            }
            val totalCount = params.size + byteEntries.size

            if (dataSize + 8 > PARAMS_REGION_SIZE) {
                FileLogger.w("ConfigChannel", "Params too large: $dataSize > $PARAMS_REGION_SIZE")
                return
            }

            buf.position(PARAMS_OFFSET)
            buf.putInt(totalCount)
            buf.putInt(dataSize)
            for (entry in params) {
                buf.putInt(entry.paramId)
                buf.putInt(entry.values.size)
                for (v in entry.values) {
                    buf.putInt(v)
                }
            }
            for (ba in byteEntries) {
                val byteCount = ba.data.size
                val paddedInts = (byteCount + 3) / 4
                buf.putInt(ba.paramId)
                buf.putInt(-byteCount)
                buf.put(ba.data)
                val padding = paddedInts * 4 - byteCount
                repeat(padding) { buf.put(0) }
            }

            val prevSeq = buf.getInt(8)
            incrementParamsSeq(buf)
            FileLogger.i(
                "ConfigChannel",
                "writeParamsToShm: count=$totalCount dataSize=$dataSize seq=${prevSeq + 1}"
            )
        } else {
            FileLogger.w("ConfigChannel", "writeParamsToShm: buffer is NULL, using su fallback")
            writeViaSu(encodeParamsLegacy(params))
        }
    }

    private fun incrementParamsSeq(buf: MappedByteBuffer) {
        val currentSeq = buf.getInt(8)
        buf.putInt(8, currentSeq + 1)
    }

    fun readStatus(): FileDriverStatus? {
        ensureInitialized()
        try {
            val buf = statusBuffer
            if (buf != null) {
                val statusSeq = buf.getInt(8)
                if (statusSeq == 0) return cachedStatus
                if (statusSeq == lastStatusSeq) return cachedStatus
                lastStatusSeq = statusSeq
                cachedStatus = parseStatusFromShm(buf)
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
        val streaming = buf.getInt(base + 8) != 0
        val sampleRate = buf.getInt(base + 12)
        val versionCode = buf.getInt(base + 16)

        val nameBytes = ByteArray(64)
        buf.position(base + 20)
        buf.get(nameBytes)
        val nameEnd = nameBytes.indexOf(0.toByte())
        val versionName = if (nameEnd >= 0) String(nameBytes, 0, nameEnd) else String(nameBytes)

        val archBytes = ByteArray(32)
        buf.position(base + 84)
        buf.get(archBytes)
        val archEnd = archBytes.indexOf(0.toByte())
        val architecture = if (archEnd >= 0) String(archBytes, 0, archEnd) else String(archBytes)

        return FileDriverStatus(
            enabled = enabled,
            configured = configured,
            streaming = streaming,
            sampleRate = sampleRate,
            versionCode = versionCode,
            versionName = versionName,
            architecture = architecture
        )
    }

    private fun readStatusViaSu(): FileDriverStatus? {
        try {
            val statusRegionSize = 128
            val process = RootShell.exec(
                "xxd -p -l $statusRegionSize -s $STATUS_DATA_OFFSET $SHM_STATUS_PATH"
            )
            val hex = process.inputStream.bufferedReader().readText().trim().replace("\n", "")
            if (hex.length < 256) return null

            val bytes = ByteArray(128)
            for (i in 0 until 128) {
                bytes[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val enabled = buf.getInt(0) != 0
            val configured = buf.getInt(4) != 0
            val streaming = buf.getInt(8) != 0
            val sampleRate = buf.getInt(12)
            val versionCode = buf.getInt(16)

            val nameBytes = ByteArray(64)
            buf.position(20)
            buf.get(nameBytes)
            val nameEnd = nameBytes.indexOf(0.toByte())
            val versionName = if (nameEnd >= 0) String(nameBytes, 0, nameEnd) else String(nameBytes)

            val archBytes = ByteArray(32)
            buf.position(84)
            buf.get(archBytes)
            val archEnd = archBytes.indexOf(0.toByte())
            val architecture =
                if (archEnd >= 0) String(archBytes, 0, archEnd) else String(archBytes)

            val status = FileDriverStatus(
                enabled = enabled,
                configured = configured,
                streaming = streaming,
                sampleRate = sampleRate,
                versionCode = versionCode,
                versionName = versionName,
                architecture = architecture
            )
            cachedStatus = status
            return status
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "readStatusViaSu failed", e)
            return null
        }
    }

    private fun encodeParamsLegacy(params: List<ParamEntry>): ByteArray {
        val magic = 0x56345041
        val version = 1
        var dataSize = 12
        for (entry in params) {
            dataSize += 8 + entry.values.size * 4
        }
        val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        buf.putInt(version)
        buf.putInt(params.size)
        for (entry in params) {
            buf.putInt(entry.paramId)
            buf.putInt(entry.values.size)
            for (v in entry.values) {
                buf.putInt(v)
            }
        }
        return buf.array()
    }

    private fun writeViaSu(data: ByteArray) {
        try {
            val legacyPath = "/data/local/tmp/v4a/params.bin"
            val tmpPath = "$legacyPath.tmp"
            val su = RootShell.getSuPath()
            val process = Runtime.getRuntime().exec(arrayOf(su, "-c", "cat > $tmpPath"))
            process.outputStream.use { it.write(data) }
            process.waitFor()

            RootShell.exec("mv $tmpPath $legacyPath && chmod 666 $legacyPath")
        } catch (e: Exception) {
            FileLogger.e("ConfigChannel", "Failed to write via su fallback", e)
        }
    }

    private fun writeByteArrayViaSu(paramId: Int, data: ByteArray) {
        val magic = 0x56345041
        val version = 1
        val byteCount = data.size
        val paddedInts = (byteCount + 3) / 4
        val dataSize = 12 + 8 + paddedInts * 4
        val buf = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.putInt(magic)
        buf.putInt(version)
        buf.putInt(1)
        buf.putInt(paramId)
        buf.putInt(-byteCount)
        buf.put(data)
        val padding = paddedInts * 4 - byteCount
        repeat(padding) { buf.put(0) }
        writeViaSu(buf.array())
    }
}
