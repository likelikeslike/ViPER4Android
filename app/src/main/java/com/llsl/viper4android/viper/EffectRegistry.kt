package com.llsl.viper4android.viper

import android.util.SparseArray
import androidx.core.util.size
import com.llsl.viper4android.utils.FileLogger

class EffectRegistry {
    var aidlTypeUuid: Boolean = true

    private val lock = Any()
    private val sessions = SparseArray<ViperEffect>()
    private val sessionPackages = SparseArray<String>()
    private var globalEffect: ViperEffect? = null

    fun ensureGlobal(): ViperEffect? {
        synchronized(lock) {
            globalEffect?.let { return it }
            val typeUuid =
                if (aidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
            val effect = ViperEffect(0, typeUuid)
            if (!effect.create()) {
                FileLogger.e("EffectReg", "Failed to create global effect")
                return null
            }
            globalEffect = effect
            FileLogger.i("EffectReg", "Global effect created (aidlType=$aidlTypeUuid)")
        }
        return globalEffect
    }

    fun isSessionOpen(sessionId: Int): Boolean = synchronized(lock) { sessions.get(sessionId) != null }

    fun openSession(
        sessionId: Int,
        packageName: String,
    ): Pair<ViperEffect, Boolean>? {
        synchronized(lock) {
            sessions.get(sessionId)?.let { return it to true }
            val typeUuid =
                if (aidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
            val effect = ViperEffect(sessionId, typeUuid)
            if (!effect.create()) {
                FileLogger.e("EffectReg", "Failed to create effect for session $sessionId ($packageName)")
                return null
            }
            effect.enabled = true
            sessions.put(sessionId, effect)
            sessionPackages.put(sessionId, packageName)
            FileLogger.i("EffectReg", "Opened session $sessionId for $packageName")
            return effect to false
        }
    }

    fun closeSession(sessionId: Int) {
        synchronized(lock) {
            val effect = sessions.get(sessionId) ?: return
            effect.enabled = false
            effect.release()
            sessions.remove(sessionId)
            sessionPackages.remove(sessionId)
            FileLogger.i("EffectReg", "Closed session $sessionId")
        }
    }

    fun globalEffect(): ViperEffect? = synchronized(lock) { globalEffect }

    fun liveEffects(): List<ViperEffect> =
        synchronized(lock) {
            buildList {
                globalEffect?.let { add(it) }
                for (i in 0 until sessions.size) {
                    add(sessions.valueAt(i))
                }
            }
        }

    fun activeEffect(): ViperEffect? =
        synchronized(lock) {
            globalEffect?.let { if (it.isCreated) return it }
            for (i in 0 until sessions.size) {
                val effect = sessions.valueAt(i)
                if (effect.isCreated) return effect
            }
            null
        }

    fun getOpenSessions(): Map<Int, String> =
        synchronized(lock) {
            val map = mutableMapOf<Int, String>()
            for (i in 0 until sessions.size) {
                val sid = sessions.keyAt(i)
                map[sid] = sessionPackages.get(sid) ?: ""
            }
            map
        }

    fun releaseAll() {
        synchronized(lock) {
            for (i in 0 until sessions.size) {
                val effect = sessions.valueAt(i)
                effect.enabled = false
                effect.release()
            }
            sessions.clear()
            sessionPackages.clear()
        }
    }

    fun releaseGlobal() {
        synchronized(lock) {
            globalEffect?.let {
                it.enabled = false
                it.release()
            }
            globalEffect = null
        }
    }
}
