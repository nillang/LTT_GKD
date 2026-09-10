package com.ltt.gkd.controller.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ltt.gkd.App
import com.ltt.gkd.R
import com.ltt.gkd.model.rule.RuleRepository
import com.ltt.gkd.model.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 规则订阅前台服务（Controller 层）。
 *
 * - 周期由 SettingsStore.subscriptionIntervalHours 决定
 * - 流程：拉取远程 JSON → 写入 filesDir/rules/subscribed/ → 触发 RuleRepository.reload
 * - 用户在设置中关闭订阅时不应启动本服务
 */
class RuleSubscriptionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    /**
     * 复用同一个 OkHttpClient，避免每次请求都重建连接池。
     * OkHttp 官方推荐单例使用：连接池/缓存/Dispatcher 都会复用。
     */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundIfNeeded()
        worker?.cancel()
        worker = scope.launch { loop() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startForegroundIfNeeded() {
        val n = NotificationCompat.Builder(this, App.CHANNEL_SERVICE)
            .setContentTitle(getString(R.string.notification_channel_service))
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                SERVICE_ID,
                n,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(SERVICE_ID, n)
        }
    }

    private suspend fun loop() {
        val settings = App.get().settings
        val repo = RuleRepository(this)
        while (scope.isActive) {
            val enabled = settings.subscriptionEnabled.first()
            val url = settings.subscriptionUrl.first()
            if (enabled && url.isNotEmpty()) {
                runCatching { fetchOnce(this, url, repo) }
                    .onFailure { Logger.w("订阅更新失败", it) }
            } else {
                Logger.d("订阅未启用或 URL 为空，停止本服务")
                stopSelf()
                return
            }
            val hours = settings.subscriptionIntervalHours.first().coerceIn(1, 168)
            delay(hours.toLong() * 60L * 60L * 1000L)
        }
    }

    private suspend fun fetchOnce(ctx: Context, url: String, repo: RuleRepository) {
        Logger.i("拉取订阅: $url")
        val req = Request.Builder().url(url).build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                Logger.w("订阅 HTTP ${resp.code}")
                return
            }
            val body = resp.body?.string() ?: return
            val fileName = "subscription_${System.currentTimeMillis()}.json"
            // 覆盖上一次的订阅：先清理旧文件；写入 subscribed 子目录以匹配 RuleRepository
            val dir = File(ctx.filesDir, "rules/subscribed").apply { mkdirs() }
            dir.listFiles { f -> f.name.startsWith("subscription_") }?.forEach { it.delete() }
            File(dir, fileName).writeText(body)
            repo.reload()
            Logger.i("订阅更新完成 -> $fileName")
        }
    }

    companion object {
        const val SERVICE_ID = 1001
    }
}
