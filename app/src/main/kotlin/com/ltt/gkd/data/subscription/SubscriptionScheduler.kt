package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

import android.content.Context  // 导入 Context
import androidx.work.Constraints  // 导入工作约束
import androidx.work.ExistingPeriodicWorkPolicy  // 导入已存在周期任务的处理策略
import androidx.work.NetworkType  // 导入网络类型约束
import androidx.work.PeriodicWorkRequestBuilder  // 导入周期任务请求构造器
import androidx.work.WorkManager  // 导入 WorkManager 入口
import com.ltt.gkd.App  // 导入应用单例
import com.ltt.gkd.service.SubscriptionUpdateWorker  // 导入订阅更新 Worker
import com.ltt.gkd.util.Logger  // 导入日志工具
import kotlinx.coroutines.flow.first  // 导入 Flow.first
import java.util.concurrent.TimeUnit  // 导入时间单位

/**
 * 订阅自动更新调度器：根据"自动更新开关 + 间隔小时数"排程或取消 [SubscriptionUpdateWorker]。
 *
 * 用法：开关变化、间隔变化、应用启动时调用 [reschedule]。内部用唯一任务名 +
 * [ExistingPeriodicWorkPolicy.UPDATE]，保证重复调用只保留一份最新排程。
 */
object SubscriptionScheduler {  // 订阅调度器对象

    /** 唯一周期任务名（用于 enqueueUnique / cancelUnique）。 */
    const val WORK_NAME = "subscription_auto_update"  // 唯一任务名常量

    /**
     * 依据当前设置重新排程（或取消）自动更新。
     *
     * - 自动更新关闭：取消唯一任务。
     * - 自动更新开启：以用户设置的间隔（clamp 到 1~168 小时）排程周期任务，约束为"有网络"。
     *
     * @param context 上下文（用于取 WorkManager 与 App 设置）
     */
    suspend fun reschedule(context: Context) {  // 重新排程方法（挂起，需读设置 Flow）
        val settings = App.get().settings  // 设置存储
        val enabled = settings.subscriptionEnabled.first()  // 读自动更新开关
        val wm = WorkManager.getInstance(context)  // 取 WorkManager 实例
        if (!enabled) {  // 关闭
            wm.cancelUniqueWork(WORK_NAME)  // 取消唯一任务
            Logger.i("订阅自动更新已取消")  // 记录日志
            return  // 结束
        }
        val hours = settings.subscriptionIntervalHours.first().coerceIn(1, 168).toLong()  // 间隔小时，clamp 1~168
        val constraints = Constraints.Builder()  // 约束构造器
            .setRequiredNetworkType(NetworkType.CONNECTED)  // 需要网络连接
            .build()  // 构建约束
        val request = PeriodicWorkRequestBuilder<SubscriptionUpdateWorker>(hours, TimeUnit.HOURS)  // 周期任务请求
            .setConstraints(constraints)  // 附加约束
            .build()  // 构建请求
        wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)  // 排程（更新已有）
        Logger.i("订阅自动更新已排程：每 $hours 小时")  // 记录日志
    }
}
