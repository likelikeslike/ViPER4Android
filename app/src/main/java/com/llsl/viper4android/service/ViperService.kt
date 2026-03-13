package com.llsl.viper4android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.util.size
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.ByteArrayParam
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.ui.screens.main.MainUiState
import com.llsl.viper4android.ui.screens.main.MainViewModel
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ViperService : LifecycleService() {

    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.llsl.viper4android.service.START"
        const val ACTION_STOP = "com.llsl.viper4android.service.STOP"
        const val ACTION_SESSION_OPEN = "com.llsl.viper4android.service.SESSION_OPEN"
        const val ACTION_SESSION_CLOSE = "com.llsl.viper4android.service.SESSION_CLOSE"
        const val EXTRA_AUDIO_SESSION = "android.media.extra.AUDIO_SESSION"
        const val EXTRA_PACKAGE_NAME = "android.media.extra.PACKAGE_NAME"

        fun startService(context: Context) {
            val intent = Intent(context, ViperService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: ViperService get() = this@ViperService
    }

    private val binder = LocalBinder()
    private val sessions = SparseArray<ViperEffect>()
    private var globalEffect: ViperEffect? = null
    private var useAidlTypeUuid: Boolean = true
    private var audioOutputDetector: AudioOutputDetector? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        FileLogger.i("Service", "Service created")
        lifecycleScope.launch {
            useAidlTypeUuid = repository.getBooleanPreference(MainViewModel.PREF_AIDL_MODE).first()
            initGlobalEffect()
            startAudioOutputMonitor()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initGlobalEffect() {
        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(0, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create global effect")
            return
        }
        globalEffect = effect
        FileLogger.i("Service", "Global effect created (aidlType=$useAidlTypeUuid)")
        lifecycleScope.launch {
            applyFullStateToEffect(effect)
            FileLogger.i("Service", "Global effect initialized with full state")
        }
    }

    private fun startAudioOutputMonitor() {
        val detector = AudioOutputDetector(this)
        audioOutputDetector = detector
        lifecycleScope.launch {
            var lastHeadphoneState = detector.isHeadphoneConnected.value
            detector.isHeadphoneConnected.collect { headphoneConnected ->
                if (headphoneConnected != lastHeadphoneState) {
                    lastHeadphoneState = headphoneConnected
                    FileLogger.i("Service", "Audio output changed: headphone=$headphoneConnected")
                    reapplyAllEffects()
                }
            }
        }
    }

    private fun reapplyAllEffects() {
        lifecycleScope.launch {
            var shmWritten = false
            globalEffect?.let {
                applyFullStateToEffect(it, skipShmWrite = false)
                shmWritten = true
            }
            for (i in 0 until sessions.size) {
                applyFullStateToEffect(sessions.valueAt(i), skipShmWrite = shmWritten)
                shmWritten = true
            }
        }
    }

    private suspend fun applyFullStateToEffect(effect: ViperEffect, skipShmWrite: Boolean = false) {
        val state = EffectDispatcher.loadFullStateFromPrefs(repository)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val headphoneConnected = AudioOutputDetector.isHeadphoneConnected(audioManager)
        val fxType =
            if (headphoneConnected) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
        val activeState = state.copy(fxType = fxType)
        val isMasterOn =
            if (fxType == ViperParams.FX_TYPE_SPEAKER) activeState.spkMasterEnabled else activeState.masterEnabled
        effect.enabled = isMasterOn
        if (useAidlTypeUuid) {
            if (!skipShmWrite) {
                FileLogger.d(
                    "Service",
                    "AIDL shm apply full state (master=$isMasterOn fxType=$fxType)"
                )
                dispatchFullStateViaFile(activeState, isMasterOn)
            }
            return
        }
        EffectDispatcher.dispatchFullState(effect, activeState, isMasterOn)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> FileLogger.i("Service", "Service started")
            ACTION_STOP -> {
                releaseAllSessions()
                globalEffect?.let { it.enabled = false; it.release() }
                globalEffect = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }

            ACTION_SESSION_OPEN -> {
                val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                if (sessionId >= 0) {
                    openSession(sessionId, packageName)
                }
            }

            ACTION_SESSION_CLOSE -> {
                val sessionId = intent.getIntExtra(EXTRA_AUDIO_SESSION, -1)
                if (sessionId >= 0) {
                    closeSession(sessionId)
                }
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        audioOutputDetector?.stop()
        audioOutputDetector = null
        globalEffect?.let { it.enabled = false; it.release() }
        globalEffect = null
        releaseAllSessions()
        FileLogger.i("Service", "Service destroyed")
        super.onDestroy()
    }

    private fun openSession(sessionId: Int, packageName: String) {
        if (sessions.get(sessionId) != null) {
            FileLogger.w("Service", "Session $sessionId already open")
            return
        }

        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(sessionId, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create effect for session $sessionId ($packageName)")
            return
        }

        sessions.put(sessionId, effect)
        FileLogger.i("Service", "Opened session $sessionId for $packageName")

        lifecycleScope.launch {
            applyFullStateToEffect(effect)
            FileLogger.i("Service", "Applied full state to session $sessionId")
        }
    }

    private fun closeSession(sessionId: Int) {
        val effect = sessions.get(sessionId) ?: return
        effect.enabled = false
        effect.release()
        sessions.remove(sessionId)
        FileLogger.i("Service", "Closed session $sessionId")
    }

    private fun releaseAllSessions() {
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = false
            effect.release()
        }
        sessions.clear()
    }

    fun dispatchParam(param: Int, value: Int) {
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm param=0x${param.toString(16)} value=$value")
            ConfigChannel.writeParams(listOf(ParamEntry(param, intArrayOf(value))))
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} value=$value")
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParam(param: Int, val1: Int, val2: Int, val3: Int) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm param=0x${param.toString(16)} v1=$val1 v2=$val2 v3=$val3"
            )
            ConfigChannel.writeParams(listOf(ParamEntry(param, intArrayOf(val1, val2, val3))))
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} v1=$val1 v2=$val2 v3=$val3")
        globalEffect?.setParameter(param, val1, val2, val3)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, val1, val2, val3)
        }
    }

    fun dispatchParam(param: Int, value: ByteArray, extraParams: List<ParamEntry>? = null) {
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm param=0x${param.toString(16)} bytes=${value.size}")
            ConfigChannel.writeParamsByteArray(param, value, extraParams)
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} bytes=${value.size}")
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParamsBatch(entries: List<ParamEntry>) {
        if (entries.isEmpty()) return
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm batch: ${entries.size} params")
            ConfigChannel.writeParams(entries)
            return
        }
        FileLogger.d("Service", "DSP batch: ${entries.size} params")
        for (entry in entries) {
            when (entry.values.size) {
                1 -> {
                    globalEffect?.setParameter(entry.paramId, entry.values[0])
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i).setParameter(entry.paramId, entry.values[0])
                    }
                }

                2 -> {
                    globalEffect?.setParameter(entry.paramId, entry.values[0], entry.values[1])
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i)
                            .setParameter(entry.paramId, entry.values[0], entry.values[1])
                    }
                }

                3 -> {
                    globalEffect?.setParameter(
                        entry.paramId,
                        entry.values[0],
                        entry.values[1],
                        entry.values[2]
                    )
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i).setParameter(
                            entry.paramId,
                            entry.values[0],
                            entry.values[1],
                            entry.values[2]
                        )
                    }
                }
            }
        }
    }

    fun dispatchEqBands(
        param: Int,
        bandsString: String,
        bandCountParam: Int = 0,
        bandCount: Int = 0
    ) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm EQ param=0x${param.toString(16)} bands=$bandsString bandCount=$bandCount"
            )
            val bands = bandsString.split(";").filter { it.isNotBlank() }
            val entries = mutableListOf<ParamEntry>()
            if (bandCountParam != 0) {
                entries.add(ParamEntry(bandCountParam, intArrayOf(bandCount)))
            }
            bands.forEachIndexed { index, bandStr ->
                val level = (bandStr.toFloatOrNull() ?: 0f) * 100
                entries.add(ParamEntry(param, intArrayOf(index, level.toInt())))
            }
            ConfigChannel.writeParams(entries)
            return
        }
        FileLogger.d("Service", "DSP EQ param=0x${param.toString(16)} bands=$bandsString")
        if (bandCountParam != 0) {
            globalEffect?.setParameter(bandCountParam, bandCount)
            for (i in 0 until sessions.size) {
                sessions.valueAt(i).setParameter(bandCountParam, bandCount)
            }
        }
        globalEffect?.let { EffectDispatcher.dispatchEqBands(it, param, bandsString) }
        for (i in 0 until sessions.size) {
            EffectDispatcher.dispatchEqBands(sessions.valueAt(i), param, bandsString)
        }
    }

    fun dispatchFullState(
        state: MainUiState,
        masterEnabled: Boolean,
        byteArrayParams: List<ByteArrayParam>? = null
    ) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm full state dispatch (master=$masterEnabled fxType=${state.fxType})"
            )
            dispatchFullStateViaFile(state, masterEnabled, byteArrayParams)
            return
        }
        globalEffect?.let { effect ->
            effect.enabled = masterEnabled
            EffectDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = masterEnabled
            EffectDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
    }

    private fun dispatchFullStateViaFile(
        state: MainUiState,
        masterEnabled: Boolean,
        byteArrayParams: List<ByteArrayParam>? = null
    ) {
        val params = mutableListOf<ParamEntry>()
        params.add(
            ParamEntry(
                ViperParams.PARAM_SET_UPDATE_STATUS,
                intArrayOf(if (masterEnabled) 1 else 0)
            )
        )
        if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) {
            collectHeadphoneParams(params, state)
        } else {
            collectSpeakerParams(params, state)
        }
        val finalByteArrays = byteArrayParams ?: prepareByteArraysForState(state)
        ConfigChannel.setActiveFxType(state.fxType)
        ConfigChannel.writeFullState(params, finalByteArrays, state.fxType)
    }

    private fun prepareByteArraysForState(state: MainUiState): List<ByteArrayParam>? {
        val result = mutableListOf<ByteArrayParam>()
        val ddcEnabled =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.spkDdcEnabled else state.ddcEnabled
        val ddcDevice =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.spkDdcDevice else state.ddcDevice
        if (ddcEnabled && ddcDevice.isNotEmpty()) {
            prepareDdcByteArray(ddcDevice, state.fxType)?.let { result.add(it) }
        }
        val convolverEnabled =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.spkConvolverEnabled else state.convolverEnabled
        val kernel =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.spkConvolverKernel else state.convolverKernel
        if (convolverEnabled && kernel.isNotEmpty()) {
            prepareConvolverByteArray(kernel, state.fxType)?.let { result.add(it) }
        }
        return result.ifEmpty { null }
    }

    private fun prepareDdcByteArray(name: String, fxType: Int): ByteArrayParam? {
        return try {
            val ddcDir = java.io.File(getExternalFilesDir(null), "DDC")
            val file = java.io.File(ddcDir, "$name.vdc")
            if (!file.exists()) return null
            val lines = file.readLines()
            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 = trimmed.removePrefix("SR_44100:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 = trimmed.removePrefix("SR_48000:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }
                }
            }
            if (coeffs44100 == null || coeffs48000 == null) return null
            if (coeffs44100.size != coeffs48000.size) return null
            if (coeffs44100.size % 5 != 0) return null
            val arrSize = coeffs44100.size
            val naturalSize = 4 + arrSize * 4 * 2
            val wireSize = when {
                naturalSize <= 256 -> 256
                naturalSize <= 1024 -> 1024
                else -> return null
            }
            val param = if (fxType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_DDC_COEFFICIENTS else ViperParams.PARAM_HP_DDC_COEFFICIENTS
            val buffer =
                java.nio.ByteBuffer.allocate(wireSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to prepare DDC: $name", e)
            null
        }
    }

    private fun prepareConvolverByteArray(fileName: String, fxType: Int): ByteArrayParam? {
        if (!useAidlTypeUuid) return null
        return try {
            val kernelDir = java.io.File(getExternalFilesDir(null), "Kernel")
            val file = java.io.File(kernelDir, fileName)
            if (!file.exists()) return null
            val safeName = fileName.replace("'", "")
            val subDir = if (fxType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            val param = if (fxType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = java.nio.ByteBuffer.allocate(256).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to prepare convolver: $fileName", e)
            null
        }
    }

    private fun collectHeadphoneParams(params: MutableList<ParamEntry>, state: MainUiState) {
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_OUTPUT_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_VOLUME_VALUES.getOrElse(state.outputVolume) { 100 })
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_CHANNEL_PAN, intArrayOf(state.channelPan)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_LIMITER,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.limiter) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_ENABLE,
                intArrayOf(if (state.agcEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_RATIO,
                intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agcStrength) { 50 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_MAX_SCALER,
                intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.agcMaxGain) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.agcOutputThreshold) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
                intArrayOf(if (state.fetEnabled) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
                intArrayOf(state.fetThreshold)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
                intArrayOf(state.fetRatio)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
                intArrayOf(if (state.fetAutoKnee) 100 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE, intArrayOf(state.fetKnee)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
                intArrayOf(state.fetKneeMulti)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
                intArrayOf(if (state.fetAutoGain) 100 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN, intArrayOf(state.fetGain)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
                intArrayOf(if (state.fetAutoAttack) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
                intArrayOf(state.fetAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
                intArrayOf(state.fetMaxAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
                intArrayOf(if (state.fetAutoRelease) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
                intArrayOf(state.fetRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
                intArrayOf(state.fetMaxRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
                intArrayOf(state.fetCrest)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
                intArrayOf(state.fetAdapt)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
                intArrayOf(if (state.fetNoClip) 100 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DDC_ENABLE,
                intArrayOf(if (state.ddcEnabled && state.ddcDevice.isNotEmpty()) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
                intArrayOf(if (state.vseEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
                intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.vseStrength) { 7600 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                intArrayOf((state.vseExciter * 5.6).toInt())
            )
        )

        params.add(ParamEntry(ViperParams.PARAM_HP_EQ_BAND_COUNT, intArrayOf(state.eqBandCount)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_EQ_ENABLE,
                intArrayOf(if (state.eqEnabled) 1 else 0)
            )
        )
        collectEqBandParams(params, ViperParams.PARAM_HP_EQ_BAND_LEVEL, state.eqBands)

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CONVOLVER_ENABLE,
                intArrayOf(if (state.convolverEnabled && state.convolverKernel.isNotEmpty()) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
                intArrayOf(state.convolverCrossChannel)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
                intArrayOf(if (state.fieldSurroundEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
                intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurroundWidening) { 0 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
                intArrayOf(state.fieldSurroundMidImage * 10 + 100)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
                intArrayOf(state.fieldSurroundDepth * 75 + 200)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
                intArrayOf(if (state.diffSurroundEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
                intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurroundDelay) { 500 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
                intArrayOf(if (state.vheEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
                intArrayOf(state.vheQuality)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ENABLE,
                intArrayOf(if (state.reverbEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_SIZE,
                intArrayOf(state.reverbRoomSize * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_WIDTH,
                intArrayOf(state.reverbWidth * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING,
                intArrayOf(state.reverbDampening)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL,
                intArrayOf(state.reverbWet)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
                intArrayOf(state.reverbDry)
            )
        )

        collectDynamicSystemParams(
            params,
            state.dynamicSystemEnabled,
            state.dynamicSystemStrength,
            state.dsXLow, state.dsXHigh,
            state.dsYLow, state.dsYHigh,
            state.dsSideGainLow, state.dsSideGainHigh,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
                intArrayOf(if (state.tubeSimulatorEnabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_ENABLE,
                intArrayOf(if (state.bassEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_BASS_MODE, intArrayOf(state.bassMode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_FREQUENCY,
                intArrayOf(state.bassFrequency + 15)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_BASS_GAIN, intArrayOf(state.bassGain * 50 + 50)))

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CLARITY_ENABLE,
                intArrayOf(if (state.clarityEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_CLARITY_MODE, intArrayOf(state.clarityMode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CLARITY_GAIN,
                intArrayOf(state.clarityGain * 50)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CURE_ENABLE,
                intArrayOf(if (state.cureEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_CURE_STRENGTH, intArrayOf(state.cureStrength)))

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_ANALOGX_ENABLE,
                intArrayOf(if (state.analogxEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_ANALOGX_MODE, intArrayOf(state.analogxMode)))
    }

    private fun collectSpeakerParams(params: MutableList<ParamEntry>, state: MainUiState) {
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_OUTPUT_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_VOLUME_VALUES.getOrElse(state.spkOutputVolume) { 100 })
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_SPK_CHANNEL_PAN, intArrayOf(state.spkChannelPan)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_LIMITER,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.spkLimiter) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_ENABLE,
                intArrayOf(if (state.spkAgcEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_RATIO,
                intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.spkAgcStrength) { 50 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_MAX_SCALER,
                intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.spkAgcMaxGain) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.spkAgcOutputThreshold) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
                intArrayOf(if (state.spkFetEnabled) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
                intArrayOf(state.spkFetThreshold)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
                intArrayOf(state.spkFetRatio)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
                intArrayOf(if (state.spkFetAutoKnee) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
                intArrayOf(state.spkFetKnee)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
                intArrayOf(state.spkFetKneeMulti)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
                intArrayOf(if (state.spkFetAutoGain) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
                intArrayOf(state.spkFetGain)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
                intArrayOf(if (state.spkFetAutoAttack) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
                intArrayOf(state.spkFetAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
                intArrayOf(state.spkFetMaxAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
                intArrayOf(if (state.spkFetAutoRelease) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
                intArrayOf(state.spkFetRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
                intArrayOf(state.spkFetMaxRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
                intArrayOf(state.spkFetCrest)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
                intArrayOf(state.spkFetAdapt)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
                intArrayOf(if (state.spkFetNoClip) 100 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CONVOLVER_ENABLE,
                intArrayOf(if (state.spkConvolverEnabled && state.spkConvolverKernel.isNotEmpty()) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
                intArrayOf(state.spkConvolverCrossChannel)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_EQ_BAND_COUNT,
                intArrayOf(state.spkEqBandCount)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_EQ_ENABLE,
                intArrayOf(if (state.spkEqEnabled) 1 else 0)
            )
        )
        collectEqBandParams(params, ViperParams.PARAM_SPK_EQ_BAND_LEVEL, state.spkEqBands)

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ENABLE,
                intArrayOf(if (state.spkReverbEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_SIZE,
                intArrayOf(state.spkReverbRoomSize * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH,
                intArrayOf(state.spkReverbWidth * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING,
                intArrayOf(state.spkReverbDampening)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL,
                intArrayOf(state.spkReverbWet)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
                intArrayOf(state.spkReverbDry)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DDC_ENABLE,
                intArrayOf(if (state.spkDdcEnabled && state.spkDdcDevice.isNotEmpty()) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
                intArrayOf(if (state.spkVseEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
                intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.spkVseStrength) { 7600 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                intArrayOf((state.spkVseExciter * 5.6).toInt())
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
                intArrayOf(if (state.spkFieldSurroundEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
                intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.spkFieldSurroundWidening) { 0 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
                intArrayOf(state.spkFieldSurroundMidImage * 10 + 100)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
                intArrayOf(state.spkFieldSurroundDepth * 75 + 200)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE,
                intArrayOf(if (state.speakerOptEnabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
                intArrayOf(if (state.spkDiffSurroundEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
                intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.spkDiffSurroundDelay) { 500 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
                intArrayOf(if (state.spkVheEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
                intArrayOf(state.spkVheQuality)
            )
        )

        collectDynamicSystemParams(
            params,
            state.spkDynamicSystemEnabled,
            state.spkDynamicSystemStrength,
            state.spkDsXLow, state.spkDsXHigh,
            state.spkDsYLow, state.spkDsYHigh,
            state.spkDsSideGainLow, state.spkDsSideGainHigh,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
                intArrayOf(if (state.spkTubeSimulatorEnabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_ENABLE,
                intArrayOf(if (state.spkBassEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_SPK_BASS_MODE, intArrayOf(state.spkBassMode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_FREQUENCY,
                intArrayOf(state.spkBassFrequency + 15)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_GAIN,
                intArrayOf(state.spkBassGain * 50 + 50)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CLARITY_ENABLE,
                intArrayOf(if (state.spkClarityEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_SPK_CLARITY_MODE, intArrayOf(state.spkClarityMode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CLARITY_GAIN,
                intArrayOf(state.spkClarityGain * 50)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CURE_ENABLE,
                intArrayOf(if (state.spkCureEnabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CURE_STRENGTH,
                intArrayOf(state.spkCureStrength)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_ANALOGX_ENABLE,
                intArrayOf(if (state.spkAnalogxEnabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_SPK_ANALOGX_MODE, intArrayOf(state.spkAnalogxMode)))
    }

    private fun collectEqBandParams(
        params: MutableList<ParamEntry>,
        param: Int,
        bandsString: String
    ) {
        val bands = bandsString.split(";").filter { it.isNotBlank() }
        for ((index, bandStr) in bands.withIndex()) {
            val level = (bandStr.toFloatOrNull() ?: 0f) * 100
            params.add(ParamEntry(param, intArrayOf(index, level.toInt())))
        }
    }

    private fun collectDynamicSystemParams(
        params: MutableList<ParamEntry>,
        enabled: Boolean,
        strength: Int,
        xLow: Int, xHigh: Int,
        yLow: Int, yHigh: Int,
        sideGainLow: Int, sideGainHigh: Int,
        paramEnable: Int,
        paramStrength: Int,
        paramXCoeffs: Int,
        paramYCoeffs: Int,
        paramSideGain: Int
    ) {
        params.add(ParamEntry(paramEnable, intArrayOf(if (enabled) 1 else 0)))
        params.add(ParamEntry(paramStrength, intArrayOf(strength * 20 + 100)))
        params.add(ParamEntry(paramXCoeffs, intArrayOf(xLow, xHigh)))
        params.add(ParamEntry(paramYCoeffs, intArrayOf(yLow, yHigh)))
        params.add(ParamEntry(paramSideGain, intArrayOf(sideGainLow, sideGainHigh)))
    }

    fun setEffectEnabled(enabled: Boolean) {
        globalEffect?.enabled = enabled
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).enabled = enabled
        }
    }

    fun getGlobalEffect(): ViperEffect? = globalEffect

    fun recreateGlobalEffect(aidlType: Boolean) {
        globalEffect?.let { it.enabled = false; it.release() }
        globalEffect = null
        useAidlTypeUuid = aidlType
        initGlobalEffect()
    }

    private fun createNotificationChannel() {
        val channelId = getString(R.string.notification_channel_id)
        val channelName = getString(R.string.notification_channel_name)
        val channelDescription = getString(R.string.notification_channel_description)
        val channel = NotificationChannel(
            channelId,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val channelId = getString(R.string.notification_channel_id)
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
