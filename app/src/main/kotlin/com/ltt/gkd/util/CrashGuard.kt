package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import android.content.Context // 导入 Context，定位 filesDir
import java.io.File // 导入 File，崩溃标记文件操作
import java.io.PrintWriter // 导入 PrintWriter，输出异常堆栈
import java.io.StringWriter // 导入 StringWriter，捕获堆栈字符串
import java.text.SimpleDateFormat // 导入 SimpleDateFormat，时间格式化
import java.util.Date // 导入 Date，时间戳
import java.util.Locale // 导入 Locale，避免地区字符差异

/**
 * 崩溃守护：主线程未捕获异常（进程即将被系统杀死）时同步落盘崩溃堆栈，
 * 并在下次启动时提供"是否崩溃过"的标记，供界面弹窗提示用户重新开启无障碍服务。
 *
 * 背景：无障碍服务崩溃/进程被杀后，ColorOS 等 ROM 可能不再自动重绑（见开发文档 §5.19），
 * 且 logcat 崩溃缓冲区会轮转导致堆栈丢失、异常源无法定位。因此这里：
 * 1. 崩溃瞬间**同步**把堆栈写入 `filesDir/crash.log`（进程即将退出，阻塞写盘无妨）；
 * 2. 下次启动读取并清除标记，若存在则说明上次是异常退出，触发"请重新开启"提示。
 *
 * 仅主线程异常会调用 [markCrash]（子线程异常被全局 handler 吞掉、进程不退出，不算崩溃）。
 */
object CrashGuard { // 崩溃守护对象

    private const val CRASH_FILE = "crash.log" // 崩溃堆栈落盘文件名

    /**
     * 记录一次主线程崩溃：同步把时间戳 + 堆栈追加写入 crash.log。
     * 必须在进程退出前调用，故用同步写（不切线程、不异步）。
     *
     * @param context 上下文（定位 filesDir）
     * @param t 未捕获的致命异常
     */
    fun markCrash(context: Context, t: Throwable) { // 记录崩溃
        runCatching { // 写盘失败也绝不抛异常（即将崩溃，能写就写）
            val file = File(context.filesDir, CRASH_FILE) // 崩溃标记文件
            val sw = StringWriter() // 堆栈字符串缓冲
            t.printStackTrace(PrintWriter(sw)) // 打印堆栈到缓冲
            val header = "==== ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ====" // 时间头
            file.appendText("$header\n$sw\n") // 同步追加写盘
        }
    }

    /**
     * 读取并清除崩溃标记。
     *
     * @param context 上下文
     * @return 上次崩溃的堆栈文本；若从未崩溃（或文件已被消费）返回 null
     */
    fun consumeCrash(context: Context): String? { // 消费崩溃标记
        val file = File(context.filesDir, CRASH_FILE) // 崩溃标记文件
        if (!file.exists()) return null // 无标记直接返回 null
        return runCatching { // 读/删失败时返回 null（不阻塞启动）
            val content = file.readText() // 读取堆栈
            file.delete() // 消费后删除，避免下次重复提示
            content.takeIf { it.isNotBlank() } // 空内容视为无
        }.getOrNull() // 异常返回 null
    }
}
