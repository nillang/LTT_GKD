package com.ltt.gkd.data.history  // 声明包名，跳过历史存储所在的包

import android.content.Context  // 导入 Context 类，用于访问应用上下文
import com.ltt.gkd.data.rule.ActionType  // 导入 ActionType 枚举，动作类型
import com.ltt.gkd.data.rule.Rule  // 导入 Rule 数据类
import com.ltt.gkd.util.Logger  // 导入日志工具类
import com.ltt.gkd.util.globalAdapter  // 导入全局 Moshi 适配器扩展
import com.squareup.moshi.JsonClass  // 导入 Moshi JsonClass 注解
import kotlinx.coroutines.CoroutineScope  // 导入协程作用域
import kotlinx.coroutines.Dispatchers  // 导入协程调度器
import kotlinx.coroutines.SupervisorJob  // 导入 SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow  // 导入可变 StateFlow
import kotlinx.coroutines.flow.StateFlow  // 导入只读 StateFlow
import kotlinx.coroutines.flow.asStateFlow  // 导入 asStateFlow 扩展
import kotlinx.coroutines.launch  // 导入 launch，启动协程
import kotlinx.coroutines.sync.Mutex  // 导入互斥锁
import kotlinx.coroutines.sync.withLock  // 导入 withLock 扩展
import java.io.File  // 导入 File 类
import java.text.SimpleDateFormat  // 导入日期格式化
import java.util.Calendar  // 导入 Calendar 类，用于时间计算
import java.util.Date  // 导入 Date 类
import java.util.Locale  // 导入区域设置

/**
 * 跳过历史记录。
 *
 * 每条记录对应一次成功跳过：哪个应用、什么场景（开屏/弹窗/Banner）、
 * 命中了什么关键词、执行了什么动作、什么时间。
 *
 * 持久化：filesDir/history/history.jsonl（JSON Lines，每行一条），
 * 内存保留最近 [MAX_RECORDS] 条，按时间倒序通过 [records] 暴露。
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成 JSON 适配器
data class SkipRecord(  // 跳过记录数据类
    val appName: String,  // 应用显示名
    val packageName: String,  // 应用包名
    val scene: String,        // SPLASH / POPUP / BANNER / OTHER
    val matchedText: String,  // 命中的关键词
    val action: String,  // 执行的动作名
    val timestamp: Long  // 跳过时间戳
)

/**
 * 跳过历史存储：文件持久化 + 内存 StateFlow。
 */
class SkipHistoryStore(private val context: Context) {  // 跳过历史存储类

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  // 内部协程作用域，IO 线程 + SupervisorJob
    private val writeLock = Mutex()  // 写文件互斥锁，避免并发写入
    private val adapter = globalAdapter<SkipRecord>()  // SkipRecord 的 Moshi 适配器

    private val historyDir = File(context.filesDir, HISTORY_DIR).apply { mkdirs() }  // 历史目录，不存在则创建
    private val historyFile = File(historyDir, HISTORY_FILE)  // 历史文件路径

    private val _records = MutableStateFlow<List<SkipRecord>>(emptyList())  // 内部可变 StateFlow
    /** 最近跳过记录，按时间倒序（最新在前）。 */
    val records: StateFlow<List<SkipRecord>> = _records.asStateFlow()  // 对外只读 StateFlow

    init {  // 初始化块
        scope.launch { load() }  // 启动协程加载历史文件
    }

    /** 记录一次成功跳过。应用名由调用方解析后传入（避免在无障碍线程查 PackageManager）；
     *  [pkg] 为实际事件包名：通用兜底规则的 rule.packageName 为空串，必须用实际包名落库，
     *  否则历史里"跳过了哪个 App"无法追溯（曾出现 appName=小蚕惠生活 而 packageName="" 的记录）。 */
    fun record(appName: String, rule: Rule, action: ActionType, matchedText: String? = null, pkg: String = "") {  // 记录一次跳过方法
        val rec = SkipRecord(  // 构造 SkipRecord
            appName = appName.ifEmpty { pkg.ifEmpty { rule.packageName } },  // 应用名为空则依次回退实际包名/规则包名
            packageName = pkg.ifEmpty { rule.packageName },  // 包名优先取实际事件包名，通用规则回退规则包名
            scene = inferScene(rule),  // 推断场景
            matchedText = matchedText ?: rule.match.text.firstOrNull().orEmpty(),  // 命中文本，缺省取规则首关键词
            action = action.name,  // 动作名
            timestamp = System.currentTimeMillis()  // 当前时间戳
        )
        scope.launch {  // 启动协程异步更新
            val updated = (listOf(rec) + _records.value).take(MAX_RECORDS)  // 新记录置顶并截断至最大数量
            _records.value = updated  // 更新 StateFlow
            appendToFile(rec)  // 追加到文件
        }
    }

    /** 清空历史。 */
    fun clear() {  // 清空历史方法
        scope.launch {  // 启动协程异步清空
            writeLock.withLock { runCatching { historyFile.writeText("") } }  // 加锁清空文件
            _records.value = emptyList()  // 清空 StateFlow
            Logger.i("跳过历史已清空")  // 输出信息日志
        }
    }

    /** 统计指定时间戳之后的记录数。 */
    fun countSince(sinceMillis: Long): Int =  // 统计指定时间后的记录数方法
        _records.value.count { it.timestamp >= sinceMillis }  // 计数满足条件的记录

    /**
     * 最近 7 天每天的跳过计数（含今天），返回 [(日期标签, 计数), ...]。
     * 日期标签格式为 "MM/dd"，索引 0 = 6 天前，索引 6 = 今天。
     */
    fun dailyCounts7d(): List<Pair<String, Int>> {  // 最近 7 天每日计数方法
        val cal = Calendar.getInstance().apply {  // 取今日 0 点
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis  // 今天 0 点时间戳
        val recs = _records.value  // 当前记录快照
        return (6 downTo 0).map { daysAgo ->  // 从 6 天前到今天
            val dayStart = todayStart - daysAgo * 24L * 3600 * 1000  // 那天 0 点
            val dayEnd = dayStart + 24L * 3600 * 1000  // 那天结束
            val label = SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(dayStart))  // 日期标签
            val count = recs.count { it.timestamp >= dayStart && it.timestamp < dayEnd }  // 那天的记录数
            label to count  // 标签与计数配对
        }
    }

    /** 今日 0 点时间戳。 */
    fun startOfToday(): Long = Calendar.getInstance().apply {  // 取今日 0 点时间戳
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)  // 时分置 0
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)  // 秒毫秒置 0
    }.timeInMillis  // 返回时间戳

    /** 本周开始时间戳（周一 0 点）。 */
    fun startOfWeek(): Long = Calendar.getInstance().apply {  // 取本周一 0 点时间戳
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)  // 时分置 0
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)  // 秒毫秒置 0
        add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) + 5) % 7)  // 回退到周一
    }.timeInMillis  // 返回时间戳

    /** 本月 1 号 0 点时间戳。 */
    fun startOfMonth(): Long = Calendar.getInstance().apply {  // 取本月 1 号 0 点时间戳
        set(Calendar.DAY_OF_MONTH, 1)  // 日置为 1
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)  // 时分置 0
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)  // 秒毫秒置 0
    }.timeInMillis  // 返回时间戳

    private suspend fun load() {  // 加载历史文件方法
        val list = runCatching {  // 捕获读取异常
            if (!historyFile.exists()) return@runCatching emptyList()  // 文件不存在返回空
            historyFile.useLines { seq ->  // 按行读取文件
                seq.mapNotNull { line ->  // 逐行解析
                    line.takeIf { it.isNotBlank() }?.let {  // 跳过空行
                        runCatching { adapter.fromJson(it) }.getOrNull()  // 解析为 SkipRecord，失败返回 null
                    }
                }.toList()  // 转列表
            }
        }.getOrElse {  // 异常分支
            Logger.w("加载跳过历史失败", it); emptyList()  // 输出警告并返回空列表
        }
        _records.value = list.sortedByDescending { it.timestamp }.take(MAX_RECORDS)  // 按时间倒序并截断
    }

    private suspend fun appendToFile(rec: SkipRecord) {  // 追加记录到文件方法
        writeLock.withLock {  // 加锁避免并发写
            runCatching {  // 捕获写入异常
                historyFile.appendText(adapter.toJson(rec) + "\n")  // 追加一行 JSON
                // 超量时重写文件（截断最旧记录）
                if (_records.value.size >= MAX_RECORDS) {  // 内存列表超量
                    historyFile.writeText(  // 重写文件
                        _records.value.reversed().joinToString("\n") { adapter.toJson(it) } + "\n"  // 按时间正序拼接
                    )
                }
            }.onFailure { Logger.w("写入跳过历史失败", it) }  // 异常输出警告
        }
    }

    companion object {  // 静态常量
        private const val HISTORY_DIR = "history"  // 历史目录名
        private const val HISTORY_FILE = "history.jsonl"  // 历史文件名
        private const val MAX_RECORDS = 1000  // 最大保留记录数

        /**
         * 按项目规则优先级约定推断场景：
         * 100=开屏（SPLASH），80=弹窗（POPUP），60=Banner，其余=OTHER。
         */
        fun inferScene(rule: Rule): String = when (rule.priority) {  // 按优先级推断场景
            100 -> "SPLASH"  // 开屏
            80 -> "POPUP"  // 弹窗
            60 -> "BANNER"  // Banner
            else -> "OTHER"  // 其他
        }
    }
}
