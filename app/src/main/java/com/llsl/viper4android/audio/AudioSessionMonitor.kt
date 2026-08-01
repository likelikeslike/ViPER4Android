package com.llsl.viper4android.audio

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.Handler
import android.os.Looper
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell

class AudioSessionMonitor(
    private val context: Context,
    private val onSessionOpen: (sessionId: Int, packageName: String) -> Unit,
    private val onSessionClose: (sessionId: Int) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activePiids = mutableMapOf<Int, SessionInfo>()
    private var registered = false
    private var pendingResolve = false

    private val isSystemPrivileged: Boolean by lazy {
        context.checkSelfPermission("android.permission.MODIFY_AUDIO_ROUTING") ==
            PackageManager.PERMISSION_GRANTED
    }

    private data class SessionInfo(
        val sessionId: Int,
        val packageName: String,
    )

    private val playbackCallback =
        object : AudioManager.AudioPlaybackCallback() {
            override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
                handlePlaybackChange()
            }
        }

    fun start() {
        if (registered) return
        FileLogger.i(TAG, "Starting playback monitor")
        audioManager.registerAudioPlaybackCallback(playbackCallback, mainHandler)
        registered = true
        resolveFromDumpsys()
    }

    fun stop() {
        if (!registered) return
        audioManager.unregisterAudioPlaybackCallback(playbackCallback)
        registered = false
        activePiids.clear()
        FileLogger.i(TAG, "Playback monitor stopped")
    }

    private fun handlePlaybackChange() {
        if (pendingResolve) return
        pendingResolve = true
        Thread {
            try {
                resolveFromDumpsys()
            } finally {
                pendingResolve = false
            }
        }.start()
    }

    private fun resolveFromDumpsys() {
        if (!isSystemPrivileged && !RootShell.isRootAvailable()) {
            FileLogger.w(TAG, "Root not available")
            return
        }

        val discovered =
            if (isSystemPrivileged) {
                queryActivePlayersSys()
            } else {
                queryActivePlayers()
            } ?: return

        mainHandler.post {
            val discoveredPiids = discovered.keys
            val currentPiids = activePiids.keys.toSet()

            val newPiids = discoveredPiids - currentPiids
            val gonePiids = currentPiids - discoveredPiids

            for (piid in newPiids) {
                val info = discovered[piid] ?: continue
                if (info.sessionId <= 0) continue
                val alreadyTracked = activePiids.values.any { it.sessionId == info.sessionId }
                activePiids[piid] = info
                if (!alreadyTracked) {
                    FileLogger.i(
                        TAG,
                        "New session piid=$piid session=${info.sessionId} (${info.packageName})",
                    )
                    onSessionOpen(info.sessionId, info.packageName)
                } else {
                    FileLogger.d(
                        TAG,
                        "Additional piid=$piid for existing session=${info.sessionId} (${info.packageName})",
                    )
                }
            }

            for (piid in gonePiids) {
                val info = activePiids.remove(piid) ?: continue
                if (info.sessionId > 0) {
                    val stillActive = activePiids.values.any { it.sessionId == info.sessionId }
                    FileLogger.i(
                        TAG,
                        "Piid ended piid=$piid session=${info.sessionId} (${info.packageName}) stillActive=$stillActive",
                    )
                    if (!stillActive) {
                        onSessionClose(info.sessionId)
                    }
                }
            }
        }
    }

    private fun queryActivePlayersSys(): Map<Int, SessionInfo>? =
        try {
            val playerSessions = mutableMapOf<Int, SessionInfo>()
            val configs = audioManager.activePlaybackConfigurations
            val configClass = AudioPlaybackConfiguration::class.java
            // Required when not using system internal sdk
            val isActiveMethod = configClass.getMethod("isActive")
            val getAudioSessionIdMethod = configClass.getMethod("getSessionId")
            val getClientUidMethod = configClass.getMethod("getClientUid")
            val getPlayerInterfaceIdMethod = configClass.getMethod("getPlayerInterfaceId")

            for (config in configs) {
                if (isActiveMethod.invoke(config) as? Boolean != true) continue

                val sid = getAudioSessionIdMethod.invoke(config) as? Int
                if (sid == null || sid <= 0) continue

                // Some player use LL API do not have piid, make sure to fake one from session id
                val piid = (getPlayerInterfaceIdMethod.invoke(config) as Int).let { if (it != 0) it else -sid }

                val uid = getClientUidMethod.invoke(config) as? Int
                if (uid == null || uid < 0) continue
                val pkg = context.packageManager.getPackagesForUid(uid)?.firstOrNull() ?: uid.toString()

                playerSessions[piid] = SessionInfo(sid, pkg)
            }
            playerSessions
        } catch (e: Exception) {
            FileLogger.e(TAG, "Framework active playback query failed", e)
            null
        }

    private fun queryActivePlayers(): Map<Int, SessionInfo>? =
        try {
            val process =
                RootShell.exec(
                    "dumpsys audio | grep -E 'state:started|new player'",
                    timeoutSec = DUMPSYS_TIMEOUT_SEC,
                )
            val text = process.inputStream.bufferedReader().readText()

            val playerSessions = mutableMapOf<Int, SessionInfo>()
            for (line in text.lineSequence()) {
                val trimmed = line.trim()
                val piid =
                    PIID_PATTERN
                        .find(trimmed)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: continue
                val sid =
                    SESSION_PATTERN
                        .find(trimmed)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: 0
                val pkg = PACKAGE_PATTERN.find(trimmed)?.groupValues?.get(1) ?: ""
                if (sid > 0 && (piid !in playerSessions || pkg.isNotEmpty())) {
                    playerSessions[piid] = SessionInfo(sid, pkg)
                }
            }

            val activePiidSet = mutableSetOf<Int>()
            for (line in text.lineSequence()) {
                if (!line.contains("state:started")) continue
                val piid =
                    PIID_PATTERN
                        .find(line)
                        ?.groupValues
                        ?.get(1)
                        ?.toIntOrNull() ?: continue
                activePiidSet.add(piid)
            }

            val result = mutableMapOf<Int, SessionInfo>()
            for (piid in activePiidSet) {
                val info = playerSessions[piid] ?: continue
                result[piid] = info
            }
            result
        } catch (e: Exception) {
            FileLogger.e(TAG, "dumpsys query failed", e)
            null
        }

    companion object {
        private const val TAG = "SessionMonitor"
        private const val DUMPSYS_TIMEOUT_SEC = 5L

        private val PIID_PATTERN = Regex("""piid:(\d+)""")
        private val SESSION_PATTERN = Regex("""session(?:Id)?:(\d+)""")
        private val PACKAGE_PATTERN = Regex("""package:(\S+)""")
    }
}
