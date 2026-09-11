package com.ltt.gkd // 声明应用包名，与目录结构对应

import android.app.Application // 导入 Application 基类，应用启动入口
import android.app.NotificationChannel // 导入通知渠道类，Android O+ 必需
import android.app.NotificationManager // 导入通知管理器，用于创建渠道
import android.os.Build // 导入 Build 类，用于判断系统版本
import com.ltt.gkd.data.app.WhitelistStore // 导入应用白名单存储
import com.ltt.gkd.data.history.SkipHistoryStore // 导入跳过历史记录存储
import com.ltt.gkd.data.prefs.SettingsStore // 导入设置存储（DataStore 偏好）
import com.ltt.gkd.util.Logger // 导入全局日志工具
import kotlinx.coroutines.CoroutineScope // 导入协程作用域
import kotlinx.coroutines.Dispatchers // 导入调度器，Default 用于 CPU 密集任务
import kotlinx.coroutines.SupervisorJob // 导入 SupervisorJob，子协程异常不传播

/**
 * LTT_GKD 应用入口。
 *
 * 职责：
 * - 初始化全局单例：SettingsStore、WhitelistStore、SkipHistoryStore、Logger
 * - 注册通知渠道（服务保活 + 跳过事件）
 * - 提供 appScope 供全局协程使用
 */
class App : Application() { // 继承 Application，作为整个应用的全局上下文

    /** 全局协程作用域（SupervisorJob 防止子协程异常导致全局取消）。 */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // 创建全局协程作用域，使用 SupervisorJob + Default 调度器

    /** 全局设置存储（DataStore 偏好）。 */
    lateinit var settings: SettingsStore // 声明延迟初始化的设置存储属性
        private set // 私有 setter，外部只读

    /** 应用白名单（不对该应用执行跳过）。 */
    lateinit var whitelist: WhitelistStore // 声明延迟初始化的白名单存储属性
        private set // 私有 setter，外部只读

    /** 跳过历史记录存储（JSONL 持久化 + 内存 StateFlow）。 */
    lateinit var history: SkipHistoryStore // 声明延迟初始化的跳过历史存储属性
        private set // 私有 setter，外部只读

    override fun onCreate() { // 应用启动时回调
        super.onCreate() // 调用父类 onCreate 完成基础初始化
        instance = this // 保存全局单例引用
        // 初始化全局组件（顺序无严格依赖，但 Logger 需读 settings.logEnabled）
        settings = SettingsStore(this) // 初始化设置存储，传入 Context
        whitelist = WhitelistStore(this) // 初始化白名单存储
        history = SkipHistoryStore(this) // 初始化跳过历史存储
        Logger.init(this) // 初始化日志工具
        registerNotificationChannels() // 注册通知渠道
    }

    /** 注册无障碍服务保活 + 跳过事件通知渠道（Android O+ 必需）。 */
    private fun registerNotificationChannels() { // 注册通知渠道方法
        val nm = getSystemService(NotificationManager::class.java) ?: return // 获取通知管理器，失败直接返回
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android O(8.0) 及以上才需注册渠道
            nm.createNotificationChannel( // 创建服务保活渠道
                NotificationChannel(
                    CHANNEL_SERVICE, // 渠道 ID
                    getString(R.string.notification_channel_service), // 渠道名称（用户可见）
                    NotificationManager.IMPORTANCE_LOW // 低重要性，静默通知
                )
            )
            nm.createNotificationChannel( // 创建跳过事件渠道
                NotificationChannel(
                    CHANNEL_SKIP_EVENT, // 渠道 ID
                    getString(R.string.notification_channel_skip_event), // 渠道名称
                    NotificationManager.IMPORTANCE_DEFAULT // 默认重要性，有声音
                )
            )
        }
    }

    companion object { // 伴生对象，提供静态成员
        const val CHANNEL_SERVICE = "service" // 服务保活通知渠道 ID 常量
        const val CHANNEL_SKIP_EVENT = "skip_event" // 跳过事件通知渠道 ID 常量

        @Volatile // 保证多线程可见性
        private var instance: App? = null // 全局单例实例

        fun get(): App = instance ?: error("App not yet created") // 获取全局实例，未初始化则抛异常
    }
}
