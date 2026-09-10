package com.ltt.gkd.util

import androidx.lifecycle.LifecycleOwner
import com.ltt.gkd.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 用 App 全局协程作用域启动任务。
 *
 * 适用场景：UI 层（Activity）调用 data 层 suspend 函数时使用。
 * - 避免在 Activity 内直接依赖 androidx.lifecycle.lifecycle-runtime-ktx 模块
 * - 用 App.appScope（SupervisorJob + Default 调度器），异常不会波及兄弟任务
 *
 * 注意：本扩展不绑定 Activity 生命周期，Activity 销毁后协程仍会完成；
 * 若任务需要随 Activity 销毁而取消，请改用 `lifecycleScope.launch`。
 */
fun LifecycleOwner.launchSafe(block: suspend CoroutineScope.() -> Unit) {
    App.get().appScope.launch(block = block)
}
