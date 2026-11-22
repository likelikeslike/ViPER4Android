package com.llsl.viper4android.ui.screens.debug

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "DebugLogState"
private const val MAX_BUFFERED_ENTRIES = 2000
private const val COMMIT_INTERVAL_MS = 32L
private const val COMMIT_BATCH_LIMIT = 64
private const val RECENT_APP_MESSAGES_CAPACITY = 512
private const val FILE_LOGGER_TAG = "ViPER4Android"
private const val LOGCAT_TAGS =
    "ViPER:* AHAL_EffectImpl:* AHAL_EffectThread:* " +
        "AHAL_EffectContext:* FMQ_EventFlags:*"

@Stable
internal class DebugLogState {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ingestChannel = Channel<Pair<Long, LogEntry>>(capacity = Channel.UNLIMITED)
    private val recentAppMessages = LinkedHashSet<String>()
    private val recentAppMessagesLock = Any()

    @Volatile
    private var epoch: Long = 0L
    val visibleEntries: SnapshotStateList<LogEntry> = mutableStateListOf()
    var totalCount by mutableIntStateOf(0)
        private set
    var appStreamError by mutableStateOf<String?>(null)
        private set
    var driverStreamError by mutableStateOf<String?>(null)
        private set

    private var driverJob: Job? = null

    init {
        scope.launch { drainIngestChannel() }
    }

    fun start(includeFileHistory: Boolean) {
        FileLogger.setListener { line ->
            val entry = LogParser.parseApp(line)
            rememberAppMessage(entry.message)
            ingestChannel.trySend(epoch to entry)
        }
        if (includeFileHistory) {
            scope.launch { loadAppHistory() }
        }
        startDriverStream(useNowTimestamp = !includeFileHistory)
    }

    fun clear() {
        scope.launch {
            epoch++
            driverJob?.cancel()
            withContext(Dispatchers.IO) {
                FileLogger.clearLogs()
                flushDriverLogBuffer()
            }
            synchronized(recentAppMessagesLock) { recentAppMessages.clear() }
            withContext(Dispatchers.Main) {
                visibleEntries.clear()
                totalCount = 0
            }
            startDriverStream(useNowTimestamp = true)
        }
    }

    private fun flushDriverLogBuffer() {
        if (!RootShell.isRootAvailable()) return
        try {
            RootShell.exec("logcat -c").destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush driver log buffer", e)
        }
    }

    fun shutdown() {
        FileLogger.setListener(null)
        scope.cancel()
    }

    private fun startDriverStream(useNowTimestamp: Boolean) {
        driverJob = scope.launch { streamDriverLogs(useNowTimestamp) }
    }

    private suspend fun loadAppHistory() {
        withContext(Dispatchers.IO) {
            val file = FileLogger.getLogFile()
            if (file == null || !file.exists()) return@withContext
            val loadEpoch = epoch
            try {
                file.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank()) return@forEach
                        val entry = LogParser.parseApp(line)
                        rememberAppMessage(entry.message)
                        ingestChannel.trySend(loadEpoch to entry)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to load app log history", e)
                withContext(Dispatchers.Main) {
                    appStreamError = e.message ?: "I/O error"
                }
            }
        }
    }

    private suspend fun streamDriverLogs(useNowTimestamp: Boolean) {
        withContext(Dispatchers.IO) {
            if (!RootShell.isRootAvailable()) {
                withContext(Dispatchers.Main) {
                    driverStreamError = "logcat unavailable (root required)"
                }
                return@withContext
            }
            val streamEpoch = epoch
            var proc: Process? = null
            try {
                val tsArg =
                    if (useNowTimestamp) {
                        val fmt = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                        " -T '${fmt.format(Date())}'"
                    } else {
                        ""
                    }
                proc =
                    ProcessBuilder(RootShell.getSuPath())
                        .redirectErrorStream(true)
                        .start()
                val writer = proc.outputStream.bufferedWriter()
                writer.write("logcat -s $LOGCAT_TAGS -v time$tsArg\n")
                writer.flush()
                proc.inputStream.bufferedReader().use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        if (line.startsWith("---------")) continue
                        if (line.startsWith("logcat ")) continue
                        val entry = LogParser.parseDriver(line) ?: continue
                        if (isAppEcho(entry)) continue
                        ingestChannel.trySend(streamEpoch to entry)
                    }
                }
            } catch (e: IOException) {
                Log.e(TAG, "Driver log stream failed", e)
                withContext(Dispatchers.Main) {
                    driverStreamError = e.message ?: "logcat unavailable (root required)"
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Driver log stream denied", e)
                withContext(Dispatchers.Main) {
                    driverStreamError = e.message ?: "su access denied"
                }
            } finally {
                proc?.let {
                    try {
                        it.outputStream.close()
                    } catch (_: IOException) {
                    }
                    it.destroy()
                }
            }
        }
    }

    private fun rememberAppMessage(message: String) {
        synchronized(recentAppMessagesLock) {
            if (recentAppMessages.add(message) && recentAppMessages.size > RECENT_APP_MESSAGES_CAPACITY) {
                val it = recentAppMessages.iterator()
                it.next()
                it.remove()
            }
        }
    }

    private fun isAppEcho(entry: LogEntry): Boolean {
        if (entry.tag != FILE_LOGGER_TAG) return false
        return synchronized(recentAppMessagesLock) {
            recentAppMessages.contains(entry.message)
        }
    }

    private suspend fun drainIngestChannel() {
        val pending = ArrayList<LogEntry>(COMMIT_BATCH_LIMIT)
        while (scope.isActive) {
            val first = ingestChannel.receive()
            if (first.first == epoch) pending.add(first.second)
            while (pending.size < COMMIT_BATCH_LIMIT) {
                val next = ingestChannel.tryReceive().getOrNull() ?: break
                if (next.first == epoch) pending.add(next.second)
            }
            if (pending.isNotEmpty()) {
                commitBatch(pending)
                pending.clear()
            }
            delay(COMMIT_INTERVAL_MS.milliseconds)
        }
    }

    private suspend fun commitBatch(batch: List<LogEntry>) {
        withContext(Dispatchers.Main) {
            visibleEntries.addAll(batch)
            val overflow = visibleEntries.size - MAX_BUFFERED_ENTRIES
            if (overflow > 0) visibleEntries.removeRange(0, overflow)
            totalCount = visibleEntries.size
        }
    }
}
