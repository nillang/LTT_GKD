package com.ltt.gkd.service // 包声明：本文件属于服务包 com.ltt.gkd.service

import android.app.Service // 导入 Service，前台服务基类
import android.content.Context // 导入 Context，方法参数用
import android.content.Intent // 导入 Intent，启动 Intent
import android.os.Build // 导入 Build，判断 SDK 版本以决定前台服务类型
import android.os.IBinder // 导入 IBinder，绑定返回值
import androidx.core.app.NotificationCompat // 导入 NotificationCompat，构造通知
import com.ltt.gkd.App // 导入 App，获取通知 channel id
import com.ltt.gkd.R // 导入 R，资源引用
import com.ltt.gkd.data.rule.RuleRepository // 导入 RuleRepository，规则仓库
import com.ltt.gkd.util.Logger // 导入 Logger，日志
import kotlinx.coroutines.CoroutineScope // 导入 CoroutineScope，协程作用域
import kotlinx.coroutines.Dispatchers // 导入 Dispatchers，IO 调度器
import kotlinx.coroutines.Job // 导入 Job，记录轮询任务
import kotlinx.coroutines.SupervisorJob // 导入 SupervisorJob，子任务隔离
import kotlinx.coroutines.cancel // 导入 cancel，取消作用域
import kotlinx.coroutines.delay // 导入 delay，等待下一轮
import kotlinx.coroutines.flow.first // 导入 first，取 Flow 首值
import kotlinx.coroutines.isActive // 导入 isActive，判断作用域是否仍活跃
import kotlinx.coroutines.launch // 导入 launch，启动协程
import okhttp3.Request // 导入 Request，构造 HTTP 请求
import java.io.File // 导入 File，写文件

/**
 * 规则订阅前台服务。
 *
 * - 周期由 SettingsStore.subscriptionIntervalHours 决定
 * - 流程：拉取远程 JSON → 写入 filesDir/rules/subscribed/ → 触发 RuleRepository.reload
 * - 用户在设置中关闭订阅时不应启动本服务
 */
class RuleSubscriptionService : Service() { // 继承 Service 实现

    // 服务级协程作用域，IO 调度器适配网络/磁盘操作
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) // 服务作用域，IO 调度器
    // 当前轮询任务引用，便于重启时取消
    private var worker: Job? = null // 当前轮询任务

    /**
     * 复用同一个 OkHttpClient，避免每次请求都重建连接池。
     * OkHttp 官方推荐单例使用：连接池/缓存/Dispatcher 都会复用。
     */
    private val httpClient get() = com.ltt.gkd.util.HttpClientHolder.client // 委托 HttpClientHolder 提供共享客户端

    /**
     * 服务创建时回调：立即启动前台通知，避免 Android 12+ 后台启动限制。
     */
    override fun onCreate() { // 入口：服务创建
        super.onCreate() // 父类初始化
        startForegroundIfNeeded() // 启动前台通知
    }

    /**
     * 启动命令回调：每次 startService 触发都会进入。
     *
     * 取消上一个轮询任务，再启动新任务，保证最新一次触发优先；返回 START_STICKY 让系统在内存不足被杀后尝试重建。
     *
     * @param intent 启动 Intent
     * @param flags 启动标志
     * @param startId 本次启动标识
     * @return 启动模式，START_STICKY 表示被杀后系统会重建服务
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { // 入口：每次启动
        startForegroundIfNeeded() // 确保前台通知已启动
        worker?.cancel() // 取消旧任务，保证最新触发优先
        worker = scope.launch { loop() } // 启动新轮询任务
        return START_STICKY // 被杀后系统尝试重建
    }

    /**
     * 绑定服务时回调，本服务不支持绑定，固定返回 null。
     */
    override fun onBind(intent: Intent?): IBinder? = null // 不支持绑定，返回 null

    /**
     * 服务销毁时回调：取消协程作用域以释放资源。
     */
    override fun onDestroy() { // 入口：服务销毁
        scope.cancel() // 取消作用域
        super.onDestroy() // 父类清理
    }

    /**
     * 启动前台服务并显示持续通知。
     *
     * Android 14（API 34，UPSIDE_DOWN_CAKE）+ 必须声明前台服务类型；
     * 低版本使用无类型重载。
     */
    private fun startForegroundIfNeeded() { // 内部：启动前台通知
        val n = NotificationCompat.Builder(this, App.CHANNEL_SERVICE) // 构造通知 builder，使用服务 channel
            .setContentTitle(getString(R.string.notification_channel_service)) // 通知标题
            .setSmallIcon(android.R.drawable.stat_sys_download_done) // 小图标
            .setOngoing(true) // 持续通知，用户无法滑动清除
            .build() // 构建通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+ 分支
            // Android 14+ 必须显式声明前台服务类型
            startForeground( // 启动前台服务（带类型）
                SERVICE_ID, // 通知 ID
                n, // 通知对象
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE // 特殊用途类型
            )
        } else { // 低版本分支
            startForeground(SERVICE_ID, n) // 启动前台服务（无类型）
        }
    }

    /**
     * 订阅轮询主循环。
     *
     * 每轮先检查开关与 URL，禁用则 stopSelf 退出；
     * 否则触发一次 [fetchOnce] 拉取远程规则，按用户配置的小时数等待后进入下一轮。
     */
    private suspend fun loop() { // 内部：轮询主循环
        val settings = App.get().settings // 取出设置存储
        val repo = RuleRepository(this) // 构造规则仓库
        while (scope.isActive) { // 作用域仍活跃则继续
            val enabled = settings.subscriptionEnabled.first() // 读取订阅开关
            val url = settings.subscriptionUrl.first() // 读取订阅 URL
            if (enabled && url.isNotEmpty()) { // 开启且 URL 非空
                runCatching { fetchOnce(this, url, repo) } // 拉取一次订阅，捕获异常
                    .onFailure { Logger.w("订阅更新失败", it) } // 失败时打 warn 日志
            } else { // 未启用或 URL 为空
                Logger.d("订阅未启用或 URL 为空，停止本服务") // 记录 debug 日志
                stopSelf() // 停止自身
                return // 退出循环
            }
            // 读取间隔小时数并限制在 1~168（一周）范围内，避免极端值
            val hours = settings.subscriptionIntervalHours.first().coerceIn(1, 168) // 取间隔小时数并 clamp
            delay(hours.toLong() * 60L * 60L * 1000L) // 小时 -> 毫秒后等待
        }
    }

    /**
     * 拉取一次订阅。
     *
     * 流程：HTTP GET → 校验响应码与 body → 清理旧订阅文件 → 写入新文件 → 触发仓库 reload。
     *
     * @param ctx 上下文
     * @param url 订阅 URL
     * @param repo 规则仓库，写入后调用其 reload 让规则仓库重新加载
     */
    private suspend fun fetchOnce(ctx: Context, url: String, repo: RuleRepository) { // 内部：拉取一次订阅
        Logger.i("拉取订阅: $url") // 记录开始拉取
        val req = Request.Builder().url(url).build() // 构造 GET 请求
        httpClient.newCall(req).execute().use { resp -> // 执行请求并自动关闭响应
            if (!resp.isSuccessful) { // 响应不成功
                Logger.w("订阅 HTTP ${resp.code}") // 打 warn 日志
                return // 直接返回
            }
            val body = resp.body?.string() ?: run { // 取响应 body 字符串
                Logger.w("订阅响应 body 为空，跳过本次写入") // 打 warn 日志
                return // 直接返回
            }
            val fileName = "subscription_${System.currentTimeMillis()}.json" // 用时间戳拼文件名
            // 覆盖上一次的订阅：先清理旧文件；写入 subscribed 子目录以匹配 RuleRepository
            val dir = repo.subscribedDirFile // 取订阅目录
            dir.listFiles { f -> f.name.startsWith("subscription_") }?.forEach { it.delete() } // 删除旧订阅文件
            File(dir, fileName).writeText(body) // 写入新内容
            repo.reload() // 触发仓库重新加载
            Logger.i("订阅更新完成 -> $fileName") // 记录完成
        }
    }

    companion object { // 伴生对象
        // 前台服务通知 ID
        const val SERVICE_ID = 1001 // 通知 ID 常量
    }
}
