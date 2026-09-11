package com.ltt.gkd.accessibility // 包声明：本文件属于无障碍处理包 com.ltt.gkd.accessibility

import android.app.NotificationManager // 导入 NotificationManager，发送通知
import android.app.PendingIntent // 导入 PendingIntent，点击跳转
import android.content.Context // 导入 Context，方法参数
import android.content.Intent // 导入 Intent，构造跳转意图
import androidx.core.app.NotificationCompat // 导入 NotificationCompat，构造通知
import com.ltt.gkd.App // 导入 App，取通知 channel
import com.ltt.gkd.R // 导入 R，资源引用
import com.ltt.gkd.ui.main.MainActivity // 导入 MainActivity，通知点击目标

/** 跳过事件通知器（轻量封装，避免在核心处理器中堆积通知代码）。 */
object SkipNotifier {

    /**
     * 发送一条"已跳过"通知。
     *
     * 构造一个点击后跳转 [MainActivity] 的 PendingIntent，并把规则名作为通知内容。
     * 使用规则名 hashCode 作为通知 ID，避免同一规则通知堆积。
     *
     * @param context 上下文
     * @param ruleName 命中的规则名，展示在通知正文中
     */
    fun notify(context: Context, ruleName: String) { // 入口：发送跳过通知
        val nm = context.getSystemService(NotificationManager::class.java) ?: return // 取 NotificationManager，拿不到直接返回
        val intent = Intent(context, MainActivity::class.java).apply { // 构造跳转 MainActivity 的 Intent
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP // 新任务并清栈顶
        }
        // FLAG_IMMUTABLE：Android 12+ 强制要求；FLAG_UPDATE_CURRENT：复用已有 PendingIntent 时刷新 extras
        val pi = PendingIntent.getActivity( // 构造 PendingIntent
            context, 0, intent, // context、requestCode、intent
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT // 不可变 + 复用时刷新
        )
        val n = NotificationCompat.Builder(context, App.CHANNEL_SKIP_EVENT) // 构造通知 builder，使用跳过事件 channel
            .setContentTitle(context.getString(R.string.notification_skip_title)) // 通知标题
            .setContentText(context.getString(R.string.notification_skip_text, ruleName)) // 通知正文（含规则名）
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel) // 小图标
            .setContentIntent(pi) // 点击事件
            .setAutoCancel(true) // 点击后自动消失
            .build() // 构建通知
        // 用规则名 hashCode 当 ID，避免同一规则通知堆积
        runCatching { nm.notify(ruleName.hashCode(), n) } // 发送通知，捕获异常避免崩溃
    }
}
