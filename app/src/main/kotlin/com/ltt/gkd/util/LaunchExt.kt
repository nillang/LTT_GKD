package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import androidx.lifecycle.LifecycleOwner // 导入 LifecycleOwner，作为扩展接收者，表示具有生命周期的组件
import com.ltt.gkd.App // 导入 App，用于获取全局应用实例及其协程作用域
import kotlinx.coroutines.CoroutineScope // 导入 CoroutineScope，作为挂起 lambda 的接收者
import kotlinx.coroutines.launch // 导入 launch，用于在协程作用域中启动新协程

/**
 * 用 App 全局协程作用域启动任务。
 *
 * 适用场景：UI 层（Activity）调用 data 层 suspend 函数时使用。
 * - 避免在 Activity 内直接依赖 androidx.lifecycle.lifecycle-runtime-ktx 模块
 * - 用 App.appScope（SupervisorJob + Default 调度器），异常不会波及兄弟任务
 *
 * 注意：本扩展不绑定 Activity 生命周期，Activity 销毁后协程仍会完成；
 * 若任务需要随 Activity 销毁而取消，请改用 `lifecycleScope.launch`。
 *
 * @param block 协程体，运行在 [App.appScope]（Default 调度器）中
 */
fun LifecycleOwner.launchSafe(block: suspend CoroutineScope.() -> Unit) { // 为 LifecycleOwner 定义扩展：使用全局作用域启动协程
    App.get().appScope.launch(block = block) // 使用全局作用域而非 lifecycleScope，任务不随 Activity 销毁而取消
}
