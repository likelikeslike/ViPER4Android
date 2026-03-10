package com.llsl.viper4android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger

class AudioSessionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
        val packageName = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: ""

        val serviceIntent = Intent(context, ViperService::class.java).apply {
            putExtra(ViperService.EXTRA_AUDIO_SESSION, sessionId)
            putExtra(ViperService.EXTRA_PACKAGE_NAME, packageName)
        }

        when (intent.action) {
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                serviceIntent.action = ViperService.ACTION_SESSION_OPEN
            }

            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                serviceIntent.action = ViperService.ACTION_SESSION_CLOSE
            }

            else -> return
        }

        try {
            context.startForegroundService(serviceIntent)
        } catch (e: Exception) {
            FileLogger.e("AudioSessionReceiver", "Cannot start FGS from background", e)
        }
    }
}
