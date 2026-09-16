package com.ltt.gkd // 声明应用包名，与目录结构对应

import android.app.Application // 导入 Application 基类，应用启动入口
import android.app.NotificationChannel // 导入通知渠道类，Android O+ 必需
import android.app.NotificationManager // 导入通知管理器，用于创建渠道
import android.os.Build // 导入 Build 类，用于判断系统版本
import android.os.Looper // 导入 Looper，用于判断是否主线程
import com.ltt.gkd.data.app.WhitelistStore // 导入应用白名单存储
import com.ltt.gkd.data.history.SkipHistoryStore // 导入跳过历史记录存储
import com.ltt.gkd.data.prefs.SettingsStore // 导入设置存储（DataStore 偏好）
import com.ltt.gkd.data.rule.RuleRepository // 导入规则仓库
import com.ltt.gkd.data.subscription.SubscriptionStore // 导入订阅源存储
import com.ltt.gkd.service.ServiceWatchdog // 导入服务看门狗（崩溃自恢复兜底）
import com.ltt.gkd.util.CrashGuard // 导入崩溃守护（主线程崩溃落盘 + 下次启动提示）
import com.ltt.gkd.util.Logger // 导入全局日志工具
import kotlinx.coroutines.CoroutineScope // 导入协程作用域
import kotlinx.coroutines.Dispatchers // 导入调度器，Default 用于 CPU 密集任务
import kotlinx.coroutines.SupervisorJob // 导入 SupervisorJob，子协程异常不传播
import kotlinx.coroutines.launch // 导入 launch，启动协程

/**
 * LTT_GKD 应用入口。
 *
 * 职责：
 * - 初始化全局单例：SettingsStore、WhitelistStore、SkipHistoryStore、RuleRepository、SubscriptionStore、Logger
 * - 注册通知渠道（服务保活 + 跳过事件）
 * - 提供 appScope 供全局协程使用
 *
 * 说明：[repo] 收敛为全局唯一实例，界面、无障碍服务、后台订阅 Worker 共用同一个仓库，
 * 这样后台同步写入的新规则能被运行中的服务即时看到（reload 后 StateFlow 对所有订阅者生效）。
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

    /** 全局规则仓库（三源合并），界面/服务/Worker 共用同一实例。 */
    lateinit var repo: RuleRepository // 声明延迟初始化的规则仓库属性
        private set // 私有 setter，外部只读

    /** 全局订阅源存储（多源）。 */
    lateinit var subscriptions: SubscriptionStore // 声明延迟初始化的订阅源存储属性
        private set // 私有 setter，外部只读

    override fun onCreate() { // 应用启动时回调
        super.onCreate() // 调用父类 onCreate 完成基础初始化
        instance = this // 保存全局单例引用
        // 初始化全局组件（顺序无严格依赖，但 Logger 需读 settings.logEnabled）
        settings = SettingsStore(this) // 初始化设置存储，传入 Context
        whitelist = WhitelistStore(this) // 初始化白名单存储
        history = SkipHistoryStore(this) // 初始化跳过历史存储
        repo = RuleRepository(this) // 初始化全局规则仓库
        subscriptions = SubscriptionStore(this) // 初始化订阅源存储
        Logger.init(this) // 初始化日志工具
        registerNotificationChannels() // 注册通知渠道
        setupCrashHandler() // 设置全局异常捕获，防止无障碍服务因未处理异常崩溃
        // 看门狗：服务崩溃/被系统回收后可能不再自动重绑（真机实测 ColorOS 上 Crashed 常驻甚至被移除），
        // 启动时立即检查一次 + 每 15 分钟周期检查，掉绑时发"点击重新开启"通知——G7 崩溃自恢复的最后兜底。
        ServiceWatchdog.ensureScheduled(this) // 排程看门狗周期任务（15 分钟）
        appScope.launch { // 启动即检查一次（用户打开小狐即可发现服务未运行）
            ServiceWatchdog.checkOnce(this@App) // 复用周期任务的检查逻辑
        }
    }

    /**
     * 设置全局未捕获异常处理器。
     *
     * 目的：当无障碍服务所在线程发生未捕获异常时，记录日志并吞掉异常，
     * 避免进程崩溃导致无障碍服务被系统停止。系统重启服务后可继续工作。
     * 仅处理非致命异常；主线程致命异常先同步落盘崩溃堆栈（CrashGuard，供排查 +
     * 下次启动弹窗提示），再交系统默认处理器（避免 ANR）。
     */
    private fun setupCrashHandler() { // 设置全局异常处理器
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler() // 保存系统默认处理器
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable -> // 设置全局处理器
            Logger.e("未捕获异常 [${thread.name}]: ${throwable.message}", throwable) // 记录异常到日志
            // 非主线程异常直接吞掉，让进程继续运行（无障碍服务不中断）
            if (thread != Looper.getMainLooper().thread) { // 非主线程
                // 子线程异常不杀进程，服务可继续工作
            } else { // 主线程异常
                // 先同步落盘崩溃堆栈：既解决"logcat 轮转后异常源无法定位"的排查难题，
                // 又作为下次启动"异常退出"的弹窗标记（见 CrashGuard.consumeCrash）。
                CrashGuard.markCrash(this, throwable) // 同步写崩溃标记
                // 主线程异常仍交系统默认处理器，避免界面卡住导致 ANR
                defaultHandler?.uncaughtException(thread, throwable) // 调用默认处理器
            }
        }
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
            nm.createNotificationChannel( // 创建看门狗渠道（服务掉绑恢复引导，需要用户注意）
                NotificationChannel(
                    CHANNEL_WATCHDOG, // 渠道 ID
                    getString(R.string.notification_channel_watchdog), // 渠道名称
                    NotificationManager.IMPORTANCE_HIGH // 高重要性，有提示音/横幅
                )
            )
        }
    }

    companion object { // 伴生对象，提供静态成员
        const val CHANNEL_SERVICE = "service" // 服务保活通知渠道 ID 常量
        const val CHANNEL_SKIP_EVENT = "skip_event" // 跳过事件通知渠道 ID 常量
        const val CHANNEL_WATCHDOG = "watchdog" // 服务看门狗通知渠道 ID 常量

        @Volatile // 保证多线程可见性
        private var instance: App? = null // 全局单例实例

        fun get(): App = instance ?: error("App not yet created") // 获取全局实例，未初始化则抛异常
    }
}
