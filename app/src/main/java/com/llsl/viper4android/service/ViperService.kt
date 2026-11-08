package com.llsl.viper4android.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.llsl.viper4android.R
import com.llsl.viper4android.SERVICE_CHANNEL_ID
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.AudioSessionMonitor
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.effect.deserializeEffectPrefs
import com.llsl.viper4android.effect.loadEffectStateFromPrefs
import com.llsl.viper4android.effect.saveEffectPrefs
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.WavDecoder
import com.llsl.viper4android.viper.AidlTransport
import com.llsl.viper4android.viper.BulkKind
import com.llsl.viper4android.viper.DriverStatus
import com.llsl.viper4android.viper.EffectRegistry
import com.llsl.viper4android.viper.HidlTransport
import com.llsl.viper4android.viper.ParamValue
import com.llsl.viper4android.viper.ViperParams
import com.llsl.viper4android.viper.ViperTransport
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import javax.inject.Inject

@AndroidEntryPoint
class ViperService : LifecycleService() {
    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.llsl.viper4android.service.START"
        const val ACTION_STOP = "com.llsl.viper4android.service.STOP"
        const val ACTION_TOGGLE_MASTER = "com.llsl.viper4android.service.TOGGLE_MASTER"
        const val EXTRA_MASTER_ENABLED = "com.llsl.viper4android.service.EXTRA_MASTER_ENABLED"

        fun startService(context: Context) {
            val intent =
                Intent(context, ViperService::class.java).apply {
                    action = ACTION_START
                }
            context.startForegroundService(intent)
        }

        fun toggleMaster(
            context: Context,
            enabled: Boolean,
        ) {
            val intent =
                Intent(context, ViperService::class.java).apply {
                    action = ACTION_TOGGLE_MASTER
                    putExtra(EXTRA_MASTER_ENABLED, enabled)
                }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: ViperService get() = this@ViperService
    }

    private val binder = LocalBinder()
    private val effectRegistry = EffectRegistry()
    private lateinit var transport: ViperTransport
    private var useAidlTypeUuid: Boolean = true
    private var globalMode: Boolean = false
    private var audioOutputDetector: AudioOutputDetector? = null
    private var sessionMonitor: AudioSessionMonitor? = null
    private var stateProvider: (() -> EffectState)? = null
    private var lastUiState: EffectState? = null

    @Volatile
    private var excludedApps: Set<String> = emptySet()

    private data class DecodedKernel(
        val samples: FloatArray,
        val rawBytes: ByteArray,
        val totalFloats: Int,
        val channelCount: Int,
        val crc: Int,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as DecodedKernel

            if (totalFloats != other.totalFloats) return false
            if (channelCount != other.channelCount) return false
            if (crc != other.crc) return false
            if (!samples.contentEquals(other.samples)) return false
            if (!rawBytes.contentEquals(other.rawBytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = totalFloats
            result = 31 * result + channelCount
            result = 31 * result + crc
            result = 31 * result + samples.contentHashCode()
            result = 31 * result + rawBytes.contentHashCode()
            return result
        }
    }

    private val decodedKernelCache =
        object : LinkedHashMap<String, DecodedKernel>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, DecodedKernel>?) = size > 3
        }

    private var bootMasterEnabled: Boolean = false
    private val masterEnabled: Boolean
        get() = stateProvider?.invoke()?.masterEnable ?: lastUiState?.masterEnable ?: bootMasterEnabled

    fun setStateProvider(provider: () -> EffectState) {
        stateProvider = provider
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        FileLogger.i("Service", "Service created")
        lifecycleScope.launch {
            ensureConfigLoaded()
            repository.getStringSetPreference(ViperRepository.PREF_EXCLUDED_APPS).collect { excludedApps = it }
        }
        lifecycleScope.launch {
            ensureConfigLoaded()
            startAudioOutputMonitor()
        }
    }

    private var configLoaded = false

    private suspend fun ensureConfigLoaded() {
        if (configLoaded) return
        useAidlTypeUuid = repository.aidlMode
        effectRegistry.aidlTypeUuid = useAidlTypeUuid
        transport =
            if (useAidlTypeUuid) {
                AidlTransport()
            } else {
                HidlTransport(
                    { effectRegistry.liveEffects() },
                    activeEffect = { effectRegistry.activeEffect() },
                )
            }.also { installBulkSideChannels(it) }
        globalMode = repository.getBooleanPreference(ViperRepository.PREF_GLOBAL_MODE).first()
        bootMasterEnabled = loadEffectStateFromPrefs(repository).masterEnable
        configLoaded = true
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initGlobalEffect() {
        effectRegistry.ensureGlobal()
    }

    private fun installBulkSideChannels(t: ViperTransport) {
        t.bulkSideChannels = { ddc, kernel, force ->
            if (ddc.isNotEmpty()) applyDdcDevice(ddc, force)
            if (kernel.isNotEmpty()) applyConvolverKernel(kernel, force)
        }
    }

    private fun applyState(
        state: EffectState,
        masterOn: Boolean,
    ) {
        if (!masterOn) {
            stopSessionMonitor()
            releaseAllSessions()
            effectRegistry.releaseGlobal()
            return
        }
        if (globalMode) {
            if (effectRegistry.globalEffect() == null) initGlobalEffect()
        } else {
            if (sessionMonitor == null) startSessionMonitor()
        }
        effectRegistry.liveEffects().forEach { it.enabled = true }
        transport.pushFullState(state)
    }

    private var currentServiceDeviceId: String? = null

    private fun startAudioOutputMonitor() {
        val detector = AudioOutputDetector(this)
        audioOutputDetector = detector
        lifecycleScope.launch {
            detector.activeDevice.collect { device ->
                if (device.id != currentServiceDeviceId) {
                    FileLogger.i(
                        "Service",
                        "Device changed: $currentServiceDeviceId -> ${device.id} (${device.name})",
                    )
                    currentServiceDeviceId = device.id
                    reapplyForDevice(device)
                }
            }
        }
    }

    private suspend fun reapplyForDevice(device: AudioDevice) {
        val saved = repository.getOrCreateDeviceSettings(device)
        val currentUiState = stateProvider?.invoke()?.takeIf { it.activeDeviceId == device.id }
        val effects =
            currentUiState
                ?: deserializeEffectPrefs(
                    JSONObject(saved.settingsJson),
                    EffectState(masterEnable = masterEnabled),
                )
        val state = effects.copy(activeDeviceId = device.id, activeDeviceName = saved.deviceName)
        lastUiState = state
        saveEffectPrefs(repository, state)
        repository.updateDeviceConnection(device.id, device.type, device.isHeadphone)
        applyState(state, masterEnabled)
    }

    private suspend fun dispatchFullStateToEffect() {
        if (!masterEnabled) return
        val state = loadEffectStateFromPrefs(repository)
        lastUiState = state
        transport.pushFullState(state)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> {
                FileLogger.i("Service", "Service started")
            }

            ACTION_STOP -> {
                releaseAllSessions()
                effectRegistry.releaseGlobal()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_TOGGLE_MASTER -> {
                val next = intent.getBooleanExtra(EXTRA_MASTER_ENABLED, false)
                lifecycleScope.launch {
                    ensureConfigLoaded()
                    bootMasterEnabled = next
                    val state = loadEffectStateFromPrefs(repository)
                    stateProvider = null
                    lastUiState = state
                    applyState(state, next)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSessionMonitor()
        audioOutputDetector?.stop()
        audioOutputDetector = null
        effectRegistry.releaseGlobal()
        releaseAllSessions()
        FileLogger.i("Service", "Service destroyed")
        super.onDestroy()
    }

    private fun startSessionMonitor() {
        stopSessionMonitor()
        val monitor =
            AudioSessionMonitor(
                context = this,
                onSessionOpen = { sessionId, pkg -> openSession(sessionId, pkg) },
                onSessionClose = { sessionId -> closeSession(sessionId) },
            )
        monitor.start()
        sessionMonitor = monitor
    }

    private fun stopSessionMonitor() {
        sessionMonitor?.stop()
        sessionMonitor = null
    }

    private fun openSession(
        sessionId: Int,
        packageName: String,
    ) {
        if (!masterEnabled) {
            FileLogger.d(
                "Service",
                "Master off: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (globalMode) {
            FileLogger.d(
                "Service",
                "Global mode: skipping per-app session $sessionId ($packageName)",
            )
            return
        }
        if (packageName in excludedApps) {
            FileLogger.i(
                "Service",
                "Excluded app: skipping session $sessionId ($packageName)",
            )
            return
        }
        if (effectRegistry.isSessionOpen(sessionId)) {
            FileLogger.w("Service", "Session $sessionId already open")
            return
        }

        effectRegistry.openSession(sessionId, packageName) ?: return

        lifecycleScope.launch {
            dispatchFullStateToEffect()
            FileLogger.i("Service", "Applied full state to session $sessionId")
        }
    }

    private fun closeSession(sessionId: Int) {
        effectRegistry.closeSession(sessionId)
    }

    private fun releaseAllSessions() {
        effectRegistry.releaseAll()
    }

    fun setParam(
        param: Int,
        value: ParamValue,
    ) = transport.set(param, value)

    fun dispatchFullState(state: EffectState) {
        lastUiState = state
        applyState(state, masterEnabled)
    }

    fun parseVdc(file: File): Pair<List<FloatArray>, List<FloatArray>>? {
        try {
            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null
            for (line in file.readLines()) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 =
                            trimmed
                                .removePrefix("SR_44100:")
                                .split(",")
                                .map { it.trim().toFloat() }
                                .toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 =
                            trimmed
                                .removePrefix("SR_48000:")
                                .split(",")
                                .map { it.trim().toFloat() }
                                .toFloatArray()
                    }
                }
            }

            val a = coeffs44100
            val b = coeffs48000
            if (a == null || b == null || a.isEmpty() || a.size != b.size || a.size % 5 != 0) {
                FileLogger.w(
                    "Service",
                    "DDC coefficient parse failure: ${file.name} 44100=${a?.size} 48000=${b?.size}",
                )
                return null
            }
            val sec44 = a.toList().chunked(5).map { it.toFloatArray() }
            val sec48 = b.toList().chunked(5).map { it.toFloatArray() }
            return sec44 to sec48
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to parse DDC: ${file.name}", e)
            return null
        }
    }

    fun applyConvolverKernel(
        fileName: String,
        force: Boolean = false,
    ) {
        if (!transport.shouldStream(BulkKind.CONVOLVER, fileName, force)) return
        val sendInts: (Int, Int, Int, Int) -> Unit =
            { p, a, b, c -> transport.set(p, ParamValue.Ints(intArrayOf(a, b, c))) }
        val sendFloats: (Int, FloatArray) -> Unit =
            { p, v -> transport.set(p, ParamValue.Floats(v)) }
        if (fileName.isEmpty()) {
            sendInts(ViperParams.PARAM_CONVOLVER_PREPARE_BUFFER, 0, 0, 1)
            return
        }
        val src = File(File(getExternalFilesDir(null), "Kernel"), fileName)
        if (!src.exists()) {
            FileLogger.w("Service", "Kernel file missing: $fileName")
            return
        }

        try {
            val decoded = decodedKernelCache[src.absolutePath]
            val samples: FloatArray
            val rawBytes: ByteArray
            val totalFloats: Int
            val channelCount: Int
            val crc: Int
            if (decoded != null) {
                samples = decoded.samples
                totalFloats = decoded.totalFloats
                channelCount = decoded.channelCount
                crc = decoded.crc
            } else {
                val d = WavDecoder.decode(src.readBytes())
                samples = d.samples
                totalFloats = d.samples.size
                channelCount = d.channels
                FileLogger.i("Service", "Kernel decoded: $fileName samples=$totalFloats ch=$channelCount")
                rawBytes =
                    ByteBuffer
                        .allocate(totalFloats * 4)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .also { for (f in samples) it.putFloat(f) }
                        .array()
                crc = CRC32().apply { update(rawBytes) }.value.toInt()
                decodedKernelCache[src.absolutePath] =
                    DecodedKernel(samples, rawBytes, totalFloats, channelCount, crc)
            }

            sendInts(ViperParams.PARAM_CONVOLVER_PREPARE_BUFFER, totalFloats, channelCount, 0)

            val maxFloatsPerChunk = 2046
            var offset = 0
            var chunkIndex = 0
            while (offset < totalFloats) {
                val floatsInChunk = minOf(totalFloats - offset, maxFloatsPerChunk)
                sendFloats(
                    ViperParams.PARAM_CONVOLVER_SET_BUFFER,
                    samples.copyOfRange(offset, offset + floatsInChunk),
                )
                offset += floatsInChunk
                chunkIndex++
            }

            val kernelId = fileName.hashCode()
            sendInts(ViperParams.PARAM_CONVOLVER_COMMIT_BUFFER, totalFloats, crc, kernelId)
            FileLogger.i("Service", "Kernel streamed: $fileName chunks=$chunkIndex crc=0x${crc.toUInt().toString(16)}")
            transport.markStreamed(BulkKind.CONVOLVER, fileName)
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to stream kernel: $fileName", e)
        }
    }

    fun applyDdcDevice(
        name: String,
        force: Boolean = false,
    ) {
        if (!transport.shouldStream(BulkKind.DDC, name, force)) return
        val sendFloats: (Int, FloatArray) -> Unit =
            { p, v -> transport.set(p, ParamValue.Floats(v)) }
        if (name.isEmpty()) {
            sendFloats(ViperParams.PARAM_DDC_COEFFICIENTS, FloatArray(0))
            return
        }
        val file = File(File(getExternalFilesDir(null), "DDC"), "$name.vdc")
        if (!file.exists()) {
            FileLogger.w("Service", "DDC file missing: $name")
            return
        }
        val parsed = parseVdc(file) ?: return
        val sec44100 = parsed.first
        val sec48000 = parsed.second
        val floatsPerRate = sec44100.size * 5
        val coeffs = FloatArray(floatsPerRate * 2)
        var i = 0
        for (s in sec44100) for (v in s) coeffs[i++] = v
        for (s in sec48000) for (v in s) coeffs[i++] = v
        sendFloats(ViperParams.PARAM_DDC_COEFFICIENTS, coeffs)
        transport.markStreamed(BulkKind.DDC, name)
    }

    fun probeDriverStatus(): DriverStatus? = transport.probeStatus()

    fun setGlobalMode(enabled: Boolean) {
        globalMode = enabled
        if (!masterEnabled) {
            applyState(EffectState(), false)
            return
        }
        if (enabled) {
            stopSessionMonitor()
            releaseAllSessions()
        } else {
            effectRegistry.releaseGlobal()
        }
        lifecycleScope.launch {
            applyState(loadEffectStateFromPrefs(repository), true)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent =
            launchIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }

        return NotificationCompat
            .Builder(this, SERVICE_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
