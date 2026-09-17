package com.ltt.gkd.service // 包声明：本文件属于服务包 com.ltt.gkd.service

import android.accessibilityservice.AccessibilityServiceInfo // 导入 AccessibilityServiceInfo，查询运行中无障碍服务
import android.view.accessibility.AccessibilityManager // 导入 AccessibilityManager，查询系统无障碍状态
import android.app.NotificationManager // 导入 NotificationManager，发送恢复引导通知
import android.app.PendingIntent // 导入 PendingIntent，通知点击跳转
import android.content.Context // 导入 Context
import android.content.Intent // 导入 Intent，跳转系统无障碍设置
import android.provider.Settings // 导入 Settings，跳转无障碍设置页
import androidx.core.app.NotificationCompat // 导入 NotificationCompat，构造通知
import androidx.work.CoroutineWorker // 导入协程 Worker
import androidx.work.ExistingPeriodicWorkPolicy // 导入周期任务复用策略
import androidx.work.PeriodicWorkRequestBuilder // 导入周期任务构造器
import androidx.work.WorkManager // 导入 WorkManager 入口
import androidx.work.WorkerParameters // 导入 Worker 参数
import com.ltt.gkd.App // 导入 App，取通知渠道 ID
import com.ltt.gkd.R // 导入 R，通知文案资源
import com.ltt.gkd.util.Logger // 导入日志工具
// SkipAccessibilityService 与本文件同包（com.ltt.gkd.service），无需 import
import kotlinx.coroutines.delay // 导入 delay，双重确认前的宽限等待
import java.util.concurrent.TimeUnit // 导入时间单位

/**
 * 无障碍服务看门狗。
 *
 * 背景：真机实测（OnePlus/ColorOS）发现服务崩溃或进程被系统回收后，系统可能不再自动重绑
 * （dumpsys accessibility 显示 Crashed services 常驻，甚至直接从启用列表移除），
 * 此后广告跳过完全失效且用户无感知——这正是"广告没跳过"的头号原因。
 * 应用侧无法强制重绑无障碍服务（需要系统级 WRITE_SECURE_SETTINGS），故看门狗的职责是：
 * **尽早发现"掉绑"并通过高优先级通知引导用户一键回到无障碍设置页重新开启**。
 *
 * 两个触发入口：
 * 1. [App] 启动时立即检查一次（用户打开小狐即可发现服务未运行，实时性最好）；
 * 2. [ensureScheduled] 排程的 15 分钟周期 Worker 兜底（覆盖进程长期驻留、服务中途掉绑的场景）。
 *
 * 判定"服务掉绑"（两个条件同时满足，且 3 秒后复确认仍满足才通知，避免绑定滞后的误报）：
 * 1. [SkipAccessibilityService.instance] 为 null（服务未连接）；
 * 2. AccessibilityManager 运行时已启用服务列表中不含本服务（真正没绑上）。
 *
 * 注意：不做"系统设置中是否启用"的前置过滤——真机实测（OnePlus/ColorOS）服务主线程崩溃后，
 * 系统会直接把服务从 ENABLED_ACCESSIBILITY_SERVICES 移除（用户没有任何感知），
 * 若按设置状态过滤会让这种最危险的掉绑静默漏报。主动关闭的用户点击通知到设置页，
 * 看到开关是关的，不会造成误解；骚扰代价远小于"跳过静默失效"的代价。
 */
object ServiceWatchdog { // 看门狗对象：检查逻辑 + 排程入口

    /** 唯一周期任务名。 */
    const val WORK_NAME = "service_watchdog" // 唯一任务名常量
    /** 恢复引导通知的固定 ID。 */
    private const val NOTIFY_ID = 2001 // 固定通知 ID 常量

    /**
     * 确保周期看门狗已排程（应用启动时调用）。
     * 周期取 WorkManager 下限 15 分钟；不加约束以保证弱电场景仍能检查。
     * KEEP 策略：重复调用不覆盖已有排程。
     */
    fun ensureScheduled(context: Context) { // 排程方法
        runCatching { // 全程兜底：WorkManager 未初始化（Robolectric 测试）、进程被杀后重建等场景均静默跳过
            val request = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(15, TimeUnit.MINUTES) // 15 分钟周期
                .build() // 构建请求
            WorkManager.getInstance(context).enqueueUniquePeriodicWork( // 排程唯一周期任务
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request // KEEP：已排程则不重排
            )
        }.onFailure { Logger.w("看门狗排程失败（可能 WorkManager 未初始化）", it) } // 失败记日志但不抛
    }

    /**
     * 执行一次掉绑检查，确认掉绑则发送恢复引导通知。
     * 全程兜底不抛异常，看门狗自身绝不干扰业务。
     */
    suspend fun checkOnce(ctx: Context) { // 检查入口（挂起，含宽限等待）
        runCatching { // 全程兜底
            if (SkipAccessibilityService.instance != null) return // 服务在连，一切正常
            if (isServiceBoundRuntime(ctx)) return // 运行时列表里仍在（OPPO 上该接口不可靠，仅作正向佐证）
            delay(3_000L) // 宽限 3 秒：进程刚被拉起时服务绑定可能滞后，避免误报
            if (SkipAccessibilityService.instance != null) return // 复确认：已连上则不打扰
            if (isServiceBoundRuntime(ctx)) return // 复确认：运行时已恢复则不打扰
            Logger.w("看门狗：服务未运行（无论用户关闭或系统移除），发送恢复引导通知") // 记录掉绑事件
            sendRecoverNotification(ctx) // 发送引导通知
        }.onFailure { Logger.w("看门狗检查异常", it) } // 异常记录日志
    }

    /** AccessibilityManager 运行时已启用服务列表中是否包含本服务（真正绑定成功的标志）。 */
    private fun isServiceBoundRuntime(ctx: Context): Boolean = runCatching { // 异常时回退 false（按未绑定处理，更保守地提醒）
        val am = ctx.getSystemService(AccessibilityManager::class.java) ?: return@runCatching false // 取无障碍管理器
        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) // 取运行时已启用服务列表
            ?.any { it.resolveInfo?.serviceInfo?.packageName == ctx.packageName } // 任一项包名匹配即视为已绑定
            ?: false // 列表为空视为未绑定
    }.getOrDefault(false) // 异常回退 false

    /** 发送"服务未运行"高优先级通知，点击直达系统无障碍设置页。 */
    private fun sendRecoverNotification(ctx: Context) { // 发送恢复通知方法
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return // 取通知管理器
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply { // 直达系统无障碍设置页
            flags = Intent.FLAG_ACTIVITY_NEW_TASK // 新任务栈（Worker/后台上下文无界面，必须）
        }
        val pi = PendingIntent.getActivity( // 构造点击跳转
            ctx, 1001, intent, // requestCode 用固定值区分于跳过通知
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // Android 12+ 必须不可变
        )
        val n = NotificationCompat.Builder(ctx, App.CHANNEL_WATCHDOG) // 使用看门狗专属渠道
            .setContentTitle(ctx.getString(R.string.notification_watchdog_title)) // 标题
            .setContentText(ctx.getString(R.string.notification_watchdog_text)) // 正文
            .setStyle(NotificationCompat.BigTextStyle() // 大文本样式，完整展示说明
                .bigText(ctx.getString(R.string.notification_watchdog_text))) // 正文
            .setSmallIcon(android.R.drawable.stat_sys_warning) // 警告样式小图标
            .setContentIntent(pi) // 点击跳转无障碍设置
            .setAutoCancel(true) // 点击后自动消失
            .setPriority(NotificationCompat.PRIORITY_HIGH) // 高优先级（兼容 8.0 以下）
            .build() // 构建通知
        runCatching { nm.notify(NOTIFY_ID, n) } // 固定 ID 发送，覆盖旧通知不堆积
    }
}

/**
 * 周期看门狗 Worker：由 WorkManager 每 15 分钟拉起一次，复用 [ServiceWatchdog.checkOnce] 的检查逻辑。
 */
class ServiceWatchdogWorker( // 看门狗 Worker 类
    context: Context, // 上下文（WorkManager 注入）
    params: WorkerParameters // Worker 参数
) : CoroutineWorker(context, params) { // 继承协程 Worker

    override suspend fun doWork(): Result { // 入口：周期检查
        ServiceWatchdog.checkOnce(applicationContext) // 复用检查逻辑（内部已兜底不抛异常）
        return Result.success() // 无论检查结果如何都算任务成功（下个周期继续）
    }
}
