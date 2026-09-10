package com.ltt.gkd.model.accessibility

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ltt.gkd.App
import com.ltt.gkd.R
import com.ltt.gkd.controller.MainActivity

/** 跳过事件通知器（轻量封装，避免在核心处理器中堆积通知代码）。 */
object SkipNotifier {

    fun notify(context: Context, ruleName: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, App.CHANNEL_SKIP_EVENT)
            .setContentTitle(context.getString(R.string.notification_skip_title))
            .setContentText(context.getString(R.string.notification_skip_text, ruleName))
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        // 用规则名 hashCode 当 ID，避免同一规则通知堆积
        runCatching { nm.notify(ruleName.hashCode(), n) }
    }
}
