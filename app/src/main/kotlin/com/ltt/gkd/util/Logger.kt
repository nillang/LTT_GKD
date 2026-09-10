package com.ltt.gkd.util

import android.content.Context
import android.util.Log
import com.ltt.gkd.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 文件级滚动日志工具。
 *
 * 特性：
 * - 5 级日志（V/D/I/W/E），Logcat 同步输出 + 文件异步落盘
 * - 按天滚动：`filesDir/logs/ltt_YYYYMMDD.log`
 * - 自动清理 7 天前日志
 * - 内存环形缓冲（最近 1000 条）供 UI 实时查看
 * - 写入通过 Channel 串行化，避免多线程并发写文件
 *
 * 隐私：日志可能含包名等敏感信息，文件不上传；导出时由用户主动分享。
 */
object Logger {

    private const val TAG = "LTT_GKD"
    private const val MAX_MEM_BUFFER = 1000
    private const val RETAIN_DAYS = 7
    private const val LOG_DIR = "logs"
    private const val LOG_PREFIX = "ltt_"
    private const val LOG_SUFFIX = ".log"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeChannel = Channel<LogEntry>(capacity = Channel.UNLIMITED)
    private val fileLock = Mutex()

    @Volatile
    private var verbose: Boolean = false

    @Volatile
    private var initialized: Boolean = false

    /** 内存环形缓冲（旧条目从头删除）。UI 读取用。 */
    private val memBuffer = ArrayDeque<LogEntry>()

    /** 自增序号，便于 UI 排序。 */
    private val seq = AtomicInteger(0)

    /** 初始化：启动后台写盘协程，并按设置加载 verbose 开关。 */
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        val store = SettingsStore(context)
        // 异步读取 verbose 开关，避免主线程阻塞；首次默认 false（不输出 V/D 级日志）
        scope.launch { verbose = store.logEnabled.first() }
        // 后台消费 channel 写文件
        scope.launch {
            val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
            cleanupOldLogs(logDir)
            for (entry in writeChannel) {
                writeToFile(context, entry)
                addToMemBuffer(entry)
            }
        }
    }

    /** 设置中开关变化时调用。 */
    fun refresh(enabled: Boolean) {
        verbose = enabled
    }

    // -------- Public API --------

    fun v(msg: String, t: Throwable? = null) {
        if (verbose) emit(Level.V, msg, t)
    }

    fun d(msg: String, t: Throwable? = null) {
        if (verbose) emit(Level.D, msg, t)
    }

    fun i(msg: String, t: Throwable? = null) {
        emit(Level.I, msg, t)
    }

    fun w(msg: String, t: Throwable? = null) {
        emit(Level.W, msg, t)
    }

    fun e(msg: String, t: Throwable? = null) {
        emit(Level.E, msg, t)
    }

    /** 读取内存缓冲的快照（UI 显示用）。 */
    fun snapshot(): List<LogEntry> = synchronized(memBuffer) { memBuffer.toList() }

    /** 获取当前日志目录。 */
    fun logDir(context: Context): File =
        File(context.filesDir, LOG_DIR).apply { mkdirs() }

    /** 列出所有日志文件（按日期升序）。 */
    fun listLogFiles(context: Context): List<File> =
        logDir(context).listFiles { f -> f.name.endsWith(LOG_SUFFIX) }
            ?.sortedBy { it.name }
            ?: emptyList()

    // -------- 内部 --------

    private fun emit(level: Level, msg: String, t: Throwable?) {
        // 1. 立刻输出到 Logcat
        when (level) {
            Level.V -> Log.v(TAG, msg, t)
            Level.D -> Log.d(TAG, msg, t)
            Level.I -> Log.i(TAG, msg, t)
            Level.W -> Log.w(TAG, msg, t)
            Level.E -> Log.e(TAG, msg, t)
        }
        // 2. 投递到 channel 异步落盘
        val entry = LogEntry(
            seq = seq.incrementAndGet(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = TAG,
            message = msg,
            throwable = t?.let { throwableToString(it) }
        )
        // tryEmit 失败说明 channel 满（罕见），丢掉这条日志避免阻塞调用方
        writeChannel.trySend(entry)
    }

    private fun throwableToString(t: Throwable): String {
        val sw = StringWriter()
        t.printStackTrace(PrintWriter(sw))
        return sw.toString()
    }

    private suspend fun writeToFile(context: Context, entry: LogEntry) {
        fileLock.withLock {
            runCatching {
                val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() }
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(entry.timestamp))
                val file = File(logDir, "$LOG_PREFIX$dateStr$LOG_SUFFIX)
                val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp))
                val line = buildString {
                    append(timeStr)
                    append(' ').append(entry.level.tag)
                    append('/').append(entry.tag)
                    append(": ").append(entry.message)
                    if (entry.throwable != null) {
                        append('\n').append(entry.throwable)
                    }
                    append('\n')
                }
                file.appendText(line)
            }
        }
    }

    private fun cleanupOldLogs(logDir: File) {
        val cutoff = System.currentTimeMillis() - RETAIN_DAYS.toLong() * 24L * 3600L * 1000L
        logDir.listFiles { f -> f.name.startsWith(LOG_PREFIX) && f.name.endsWith(LOG_SUFFIX) }
            ?.forEach { f ->
                if (f.lastModified() < cutoff) f.delete()
            }
    }

    private fun addToMemBuffer(entry: LogEntry) {
        synchronized(memBuffer) {
            memBuffer.addLast(entry)
            while (memBuffer.size > MAX_MEM_BUFFER) memBuffer.removeFirst()
        }
    }

    // -------- 数据类型 --------

    enum class Level(val tag: String) {
        V("V"), D("D"), I("I"), W("W"), E("E")
    }

    data class LogEntry(
        val seq: Int,
        val timestamp: Long,
        val level: Level,
        val tag: String,
        val message: String,
        val throwable: String?
    )
}
