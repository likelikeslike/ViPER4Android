package com.llsl.viper4android.utils

import java.io.File
import java.util.concurrent.TimeUnit

object RootShell {

    private const val TAG = "RootShell"
    private const val SU_TIMEOUT_SEC = 10L

    @Volatile
    private var cachedSuPath: String? = null

    @Volatile
    private var suDetected = false

    fun exec(command: String, timeoutSec: Long = SU_TIMEOUT_SEC): Process {
        val su = getSuPath()
        val process = Runtime.getRuntime().exec(arrayOf(su, "-c", command))
        if (!process.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw RuntimeException("su command timed out after ${timeoutSec}s: $command")
        }
        return process
    }

    fun startShell(): Process {
        val su = getSuPath()
        return ProcessBuilder(su)
            .redirectErrorStream(true)
            .start()
    }

    fun getSuPath(): String {
        cachedSuPath?.let { return it }
        synchronized(this) {
            cachedSuPath?.let { return it }
            val path = detectSu()
            cachedSuPath = path
            suDetected = true
            return path
        }
    }

    fun isRootAvailable(): Boolean {
        return try {
            getSuPath()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun detectSu(): String {
        if (testSu("su")) {
            FileLogger.i(TAG, "su found via PATH")
            return "su"
        }

        val candidates = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system/bin/kp",
            "/sbin/su",
            "/debug_ramdisk/su",
            "/su/bin/su",
            "/data/adb/ksud",
            "/data/adb/ksu/bin/ksud",
            "/data/adb/apd",
            "/sbin/magisk",
            "/debug_ramdisk/magisk"
        )
        for (path in candidates) {
            if (File(path).exists() && testSu(path)) {
                FileLogger.i(TAG, "su found at $path")
                return path
            }
        }

        FileLogger.e(TAG, "No working su binary found")
        throw RuntimeException("Root not available: no working su binary found")
    }

    private fun testSu(path: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf(path, "-c", "id"))
            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return false
            }
            if (process.exitValue() != 0) return false
            val output = process.inputStream.bufferedReader().readText()
            output.contains("uid=0")
        } catch (_: Exception) {
            false
        }
    }

    fun copyFile(src: File, destPath: String) {
        val destFile = File(destPath)
        val destDir = destFile.parentFile
        val tmpPath = "$destPath.tmp"
        val tmpFile = File(tmpPath)
        try {
            if (destDir != null && destDir.canWrite()) {
                if (!destDir.exists()) destDir.mkdirs()
                src.copyTo(tmpFile, overwrite = true)
                tmpFile.renameTo(destFile)
                destFile.setReadable(true, false)
                FileLogger.i(TAG, "Direct copy OK: $destPath")
                return
            }
        } catch (_: Exception) {
            FileLogger.d(TAG, "Direct copy failed for $destPath, trying su")
        }
        val safeSrc = src.absolutePath.replace("'", "")
        val safeDest = destPath.replace("'", "")
        val safeTmp = tmpPath.replace("'", "")
        exec("cp '$safeSrc' '$safeTmp' && mv '$safeTmp' '$safeDest' && chmod 644 '$safeDest'")
        FileLogger.i(TAG, "su copy OK: $destPath")
    }
}
