package com.ltt.gkd.service  // 声明包名，服务模块所在的包

import android.content.Context  // 导入 Context
import androidx.work.CoroutineWorker  // 导入协程 Worker 基类
import androidx.work.WorkerParameters  // 导入 Worker 参数
import com.ltt.gkd.App  // 导入应用单例
import com.ltt.gkd.data.subscription.SubscriptionSyncer  // 导入订阅同步器
import com.ltt.gkd.util.Logger  // 导入日志工具
import kotlinx.coroutines.flow.first  // 导入 Flow.first

/**
 * 订阅自动更新 Worker（WorkManager 周期任务）。
 *
 * 取代此前从未被启动的常驻前台服务：由系统按周期间隔调度，省电、无需常驻通知，
 * 且符合 Android 12+ 的后台限制。每次运行遍历所有"已启用"的订阅源做同步，
 * 写入共享的 [App.repo] 订阅目录后 reload，使界面与运行中的无障碍服务同时生效。
 *
 * 失败不重试到死：单源失败仅记录，不影响其它源；整体返回 success 以避免无意义退避。
 */
class SubscriptionUpdateWorker(  // 订阅更新 Worker 类
    context: Context,  // 上下文
    params: WorkerParameters  // Worker 参数
) : CoroutineWorker(context, params) {  // 继承 CoroutineWorker

    override suspend fun doWork(): Result {  // 入口：执行后台同步
        val app = runCatching { App.get() }.getOrNull() ?: return Result.success()  // App 未就绪直接返回
        val settings = app.settings  // 设置存储
        val store = app.subscriptions  // 订阅源存储
        val repo = app.repo  // 共享规则仓库
        // 全局自动更新开关关闭则跳过（调度本身也应在关闭时取消，这里再兜一层）
        if (!settings.subscriptionEnabled.first()) {  // 未开启自动更新
            Logger.d("自动更新未开启，Worker 跳过")  // 记录 debug
            return Result.success()  // 返回成功
        }
        val sources = store.sources.first().filter { it.enabled }  // 取已启用的源
        if (sources.isEmpty()) {  // 无源
            Logger.d("无启用的订阅源，Worker 跳过")  // 记录 debug
            return Result.success()  // 返回成功
        }
        val syncer = SubscriptionSyncer()  // 同步器
        val (ok, total) = syncer.syncAll(sources, store, repo)  // 同步所有启用源（内部回写结果并按需 reload）
        Logger.i("订阅自动更新完成：$ok/$total 个源成功")  // 记录完成
        return Result.success()  // 返回成功
    }
}
