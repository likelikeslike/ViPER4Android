package com.llsl.viper4android.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var repository: ViperRepository

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val autoStart = runBlocking {
            repository.getBooleanPreference("auto_start", true).first()
        }
        if (!autoStart) return

        try {
            ViperService.startService(context)
        } catch (e: Exception) {
            FileLogger.e(
                "BootReceiver",
                "Cannot start FGS from boot, will start on next app open",
                e
            )
        }
    }
}
