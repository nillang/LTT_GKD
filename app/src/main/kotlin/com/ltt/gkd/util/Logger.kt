package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import android.content.Context // 导入 Context，访问 filesDir 等
import android.util.Log // 导入 Log，输出到 Logcat
import com.ltt.gkd.App // 导入 Application 单例，复用其 settings 实例
import kotlinx.coroutines.CoroutineScope // 导入 CoroutineScope，协程作用域
import kotlinx.coroutines.Dispatchers // 导入 Dispatchers，IO 调度器
import kotlinx.coroutines.SupervisorJob // 导入 SupervisorJob，子任务异常不波及兄弟
import kotlinx.coroutines.channels.Channel // 导入 Channel，串行化写入
import kotlinx.coroutines.flow.first // 导入 first，取 Flow 首值
import kotlinx.coroutines.launch // 导入 launch，启动协程
import kotlinx.coroutines.sync.Mutex // 导入 Mutex，文件写入互斥锁
import kotlinx.coroutines.sync.withLock // 导入 withLock，加锁执行代码块
import java.io.File // 导入 File，文件操作
import java.io.PrintWriter // 导入 PrintWriter，输出异常堆栈
import java.io.StringWriter // 导入 StringWriter，捕获堆栈字符串
import java.text.SimpleDateFormat // 导入 SimpleDateFormat，格式化时间
import java.util.Date // 导入 Date，时间封装
import java.util.Locale // 导入 Locale，使用 US 区域避免月份字符差异
import java.util.concurrent.atomic.AtomicInteger // 导入 AtomicInteger，自增序号

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

    private const val TAG = "LTT_GKD" // Logcat tag
    private const val MAX_MEM_BUFFER = 1000 // 内存缓冲最大条数
    private const val RETAIN_DAYS = 7 // 日志保留天数
    private const val LOG_DIR = "logs" // 日志子目录名
    private const val LOG_PREFIX = "ltt_" // 日志文件名前缀
    private const val LOG_SUFFIX = ".log" // 日志文件名后缀

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // 后台协程作用域，IO 调度器
    private val writeChannel = Channel<LogEntry>(capacity = Channel.UNLIMITED) // 写盘 channel，无界容量
    private val fileLock = Mutex() // 文件写入互斥锁

    @Volatile // 保证多线程可见性
    private var verbose: Boolean = false // 是否输出 V/D 级日志

    @Volatile // 保证多线程可见性
    private var initialized: Boolean = false // 是否已完成初始化

    /** 内存环形缓冲（旧条目从头删除）。UI 读取用。 */
    private val memBuffer = ArrayDeque<LogEntry>() // 内存日志缓冲队列

    /** 自增序号，便于 UI 排序。 */
    private val seq = AtomicInteger(0) // 自增序号生成器

    /** 初始化：启动后台写盘协程，并按设置加载 verbose 开关。 */
    fun init(context: Context) { // 入口：初始化 Logger
        if (initialized) return // 防止重复初始化
        initialized = true // 标记已初始化
        // 复用 App 中的 SettingsStore 单例，避免重复构造导致 DataStore 多实例崩溃
        val store = App.get().settings
        // 异步读取 verbose 开关，避免主线程阻塞；首次默认 false（不输出 V/D 级日志）
        scope.launch { verbose = store.logEnabled.first() } // 后台读取 verbose 设置
        // 后台消费 channel 写文件
        scope.launch { // 启动写盘消费者协程
            val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() } // 准备日志目录
            cleanupOldLogs(logDir) // 清理过期日志
            for (entry in writeChannel) { // 从 channel 取出每条日志
                writeToFile(context, entry) // 写入文件
                addToMemBuffer(entry) // 加入内存缓冲
            }
        }
    }

    /** 设置中开关变化时调用。 */
    fun refresh(enabled: Boolean) { // 入口：刷新 verbose 开关
        verbose = enabled // 更新内存中的开关
    }

    // -------- Public API --------

    fun v(msg: String, t: Throwable? = null) { // 入口：Verbose 级日志
        if (verbose) emit(Level.V, msg, t) // 仅在 verbose 时输出
    }

    fun d(msg: String, t: Throwable? = null) { // 入口：Debug 级日志
        if (verbose) emit(Level.D, msg, t) // 仅在 verbose 时输出
    }

    fun i(msg: String, t: Throwable? = null) { // 入口：Info 级日志
        emit(Level.I, msg, t) // 直接输出
    }

    fun w(msg: String, t: Throwable? = null) { // 入口：Warn 级日志
        emit(Level.W, msg, t) // 直接输出
    }

    fun e(msg: String, t: Throwable? = null) { // 入口：Error 级日志
        emit(Level.E, msg, t) // 直接输出
    }

    /** 读取内存缓冲的快照（UI 显示用）。 */
    fun snapshot(): List<LogEntry> = synchronized(memBuffer) { memBuffer.toList() } // 加锁返回缓冲快照

    /** 获取当前日志目录。 */
    fun logDir(context: Context): File = // 入口：获取日志目录
        File(context.filesDir, LOG_DIR).apply { mkdirs() } // 创建并返回目录

    /** 列出所有日志文件（按日期升序）。 */
    fun listLogFiles(context: Context): List<File> = // 入口：列出日志文件
        logDir(context).listFiles { f -> f.name.endsWith(LOG_SUFFIX) } // 仅取以 .log 结尾的文件
            ?.sortedBy { it.name } // 按文件名升序
            ?: emptyList() // 目录为空时返回空列表

    /**
     * 清空内存缓冲和全部日志文件。
     * 供 UI"清空日志"按钮调用；不影响 verbose 开关状态。
     */
    fun clear(context: Context) { // 入口：清空日志
        synchronized(memBuffer) { memBuffer.clear() } // 同步清空内存缓冲
        scope.launch { // 后台删除文件
            fileLock.withLock { // 加锁避免与写入冲突
                logDir(context).listFiles { f -> // 列出符合前缀+后缀的文件
                    f.name.startsWith(LOG_PREFIX) && f.name.endsWith(LOG_SUFFIX)
                }?.forEach { it.delete() } // 逐个删除
            }
        }
    }

    // -------- 内部 --------

    private fun emit(level: Level, msg: String, t: Throwable?) { // 内部：分发日志到 Logcat + channel
        // 1. 立刻输出到 Logcat
        when (level) { // 按级别调用对应 Log 方法
            Level.V -> Log.v(TAG, msg, t) // Verbose
            Level.D -> Log.d(TAG, msg, t) // Debug
            Level.I -> Log.i(TAG, msg, t) // Info
            Level.W -> Log.w(TAG, msg, t) // Warn
            Level.E -> Log.e(TAG, msg, t) // Error
        }
        // 2. 投递到 channel 异步落盘
        val entry = LogEntry( // 构造日志条目
            seq = seq.incrementAndGet(), // 自增序号
            timestamp = System.currentTimeMillis(), // 当前时间戳
            level = level, // 日志级别
            tag = TAG, // 日志 tag
            message = msg, // 日志正文
            throwable = t?.let { throwableToString(it) } // 异常堆栈字符串，无则 null
        )
        // tryEmit 失败说明 channel 满（罕见），丢掉这条日志避免阻塞调用方
        writeChannel.trySend(entry) // 非阻塞投递到写盘 channel
    }

    private fun throwableToString(t: Throwable): String { // 内部：异常转字符串
        val sw = StringWriter() // 字符串写入器
        t.printStackTrace(PrintWriter(sw)) // 打印堆栈到 StringWriter
        return sw.toString() // 返回堆栈字符串
    }

    private suspend fun writeToFile(context: Context, entry: LogEntry) { // 内部：写日志到文件
        fileLock.withLock { // 加锁避免并发写
            runCatching { // 容错执行
                val logDir = File(context.filesDir, LOG_DIR).apply { mkdirs() } // 准备日志目录
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(entry.timestamp)) // 日期字符串
                val file = File(logDir, "$LOG_PREFIX$dateStr$LOG_SUFFIX") // 按日期拼出文件名
                val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(entry.timestamp)) // 时间字符串
                val line = buildString { // 构造一行日志
                    append(timeStr) // 时间
                    append(' ').append(entry.level.tag) // 级别
                    append('/').append(entry.tag) // tag
                    append(": ").append(entry.message) // 正文
                    if (entry.throwable != null) { // 有异常堆栈
                        append('\n').append(entry.throwable) // 追加堆栈
                    }
                    append('\n') // 行末换行
                }
                file.appendText(line) // 追加写入文件
            }
        }
    }

    private fun cleanupOldLogs(logDir: File) { // 内部：清理过期日志
        val cutoff = System.currentTimeMillis() - RETAIN_DAYS.toLong() * 24L * 3600L * 1000L // 计算截止时间戳
        logDir.listFiles { f -> f.name.startsWith(LOG_PREFIX) && f.name.endsWith(LOG_SUFFIX) } // 列出日志文件
            ?.forEach { f -> // 逐个检查
                if (f.lastModified() < cutoff) f.delete() // 早于截止时间的删除
            }
    }

    private fun addToMemBuffer(entry: LogEntry) { // 内部：加入内存缓冲
        synchronized(memBuffer) { // 同步访问缓冲
            memBuffer.addLast(entry) // 加到尾部
            while (memBuffer.size > MAX_MEM_BUFFER) memBuffer.removeFirst() // 超出容量从头删除
        }
    }

    // -------- 数据类型 --------

    enum class Level(val tag: String) { // 日志级别枚举
        V("V"), D("D"), I("I"), W("W"), E("E") // 5 个级别及其短标记
    }

    data class LogEntry( // 日志条目数据类
        val seq: Int, // 序号
        val timestamp: Long, // 时间戳
        val level: Level, // 级别
        val tag: String, // tag
        val message: String, // 正文
        val throwable: String? // 异常堆栈字符串
    )
}
