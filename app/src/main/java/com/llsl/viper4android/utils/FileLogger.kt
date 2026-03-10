package com.llsl.viper4android.utils

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object FileLogger {

    private const val TAG = "ViPER4Android"
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024
    private const val LOG_FILE_NAME = "viper.log"
    private const val OLD_LOG_FILE_NAME = "viper.old.log"

    private val executor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "FileLogger").apply { isDaemon = true }
    }
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null
    private var outputStream: FileOutputStream? = null

    fun init(context: Context) {
        val dir = File(context.getExternalFilesDir(null), "Log")
        if (!dir.exists()) dir.mkdirs()
        logFile = File(dir, LOG_FILE_NAME)
        openLogFile()
    }

    private fun openLogFile() {
        val file = logFile ?: return
        if (!file.exists()) file.createNewFile()
        outputStream = FileOutputStream(file, true)
    }

    private fun rotateIfNeeded() {
        val file = logFile ?: return
        if (file.length() <= MAX_FILE_SIZE) return
        outputStream?.close()
        val oldFile = File(file.parentFile, OLD_LOG_FILE_NAME)
        if (oldFile.exists()) oldFile.delete()
        file.renameTo(oldFile)
        openLogFile()
    }

    private fun writeRaw(line: String) {
        executor.execute {
            try {
                rotateIfNeeded()
                outputStream?.write(line.toByteArray(Charsets.UTF_8))
                outputStream?.flush()
            } catch (_: Exception) {
            }
        }
    }

    private fun log(level: String, category: String, message: String) {
        val timestamp = dateFormatter.format(Date())
        writeRaw("$timestamp [$category][$level] $message\n")
    }

    fun d(category: String, message: String) {
        Log.d(TAG, message)
        log("DEBUG", category, message)
    }

    fun i(category: String, message: String) {
        Log.i(TAG, message)
        log("INFO", category, message)
    }

    fun w(category: String, message: String) {
        Log.w(TAG, message)
        log("WARN", category, message)
    }

    fun e(category: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
            log("ERROR", category, "$message: ${throwable.message}")
        } else {
            Log.e(TAG, message)
            log("ERROR", category, message)
        }
    }

    fun clearLogs() {
        executor.execute {
            try {
                outputStream?.close()
                outputStream = null
                val file = logFile ?: return@execute
                val oldFile = File(file.parentFile, OLD_LOG_FILE_NAME)
                if (oldFile.exists()) oldFile.delete()
                if (file.exists()) file.delete()
                openLogFile()
            } catch (_: Exception) {
            }
        }
    }

    fun getLogFile(): File? = logFile
}
