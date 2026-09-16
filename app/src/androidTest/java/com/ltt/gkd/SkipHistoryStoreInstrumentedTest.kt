package com.ltt.gkd  // 测试包名与被测应用一致

import androidx.test.ext.junit.runners.AndroidJUnit4  // AndroidJUnit4 测试 runner
import androidx.test.platform.app.InstrumentationRegistry  // InstrumentationRegistry，取目标上下文
import com.ltt.gkd.data.history.SkipHistoryStore  // 被测的跳过历史存储
import com.ltt.gkd.data.rule.ActionType  // 动作类型枚举，构造记录时使用
import com.ltt.gkd.data.rule.Rule  // Rule 数据类，构造测试规则
import kotlinx.coroutines.flow.first  // Flow.first 取首值，等待流条件满足
import kotlinx.coroutines.runBlocking  // runBlocking 驱动 suspend 函数
import org.junit.After  // @After 注解，测试后置清理
import org.junit.Assert.assertEquals  // assertEquals 断言
import org.junit.Assert.assertTrue  // assertTrue 断言
import org.junit.Before  // @Before 注解，测试前置初始化
import org.junit.Test  // @Test 注解，标记测试方法
import org.junit.runner.RunWith  // @RunWith 注解，指定 runner
import java.io.File  // File 类，用于清理历史文件

/**
 * SkipHistoryStore 真机 Instrumented Test。
 *
 * 验证 record/clear/countSince/dailyCounts7d 在真机上的行为：
 * - 记录后 records 流是否更新
 * - clear 后是否为空
 * - countSince 统计指定时间后的记录数
 * - dailyCounts7d 返回最近 7 天每日计数
 *
 * 用 InstrumentationRegistry.getTargetContext() 创建实例，确保使用真机 filesDir。
 */
@RunWith(AndroidJUnit4::class)  // 指定使用 AndroidJUnit4 runner 在真机执行
class SkipHistoryStoreInstrumentedTest {  // 跳过历史存储真机测试类

    private lateinit var store: SkipHistoryStore  // 被测历史存储实例
    private lateinit var historyFile: File  // 历史文件路径，用于清理

    @Before  // 标记测试前置方法
    fun setup() {  // 测试前置初始化
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext  // 取被测应用上下文（新版 androidx.test 移除了 getTargetContext）
        // 清理可能残留的历史文件，保证每次测试起点一致
        historyFile = File(File(ctx.filesDir, "history"), "history.jsonl")  // 计算历史文件路径
        historyFile.delete()  // 删除残留历史文件
        store = SkipHistoryStore(ctx)  // 用目标上下文创建历史存储实例
    }

    @After  // 标记测试后置方法
    fun tearDown() {  // 测试后置清理
        historyFile.delete()  // 删除测试产生的历史文件，避免污染后续测试
    }

    /** 构造一条 SPLASH 场景测试规则（priority=100 → SPLASH） */
    private fun rule(id: String = "r1"): Rule =  // 构造测试规则辅助方法
        Rule(id = id, name = id, packageName = "com.test.app", priority = 100)  // SPLASH 场景规则

    @Test  // 标记测试方法
    fun record_记录后records流更新() = runBlocking {  // 测试记录后 records 流更新
        // 等待初始化加载完成（init 中 launch 的 load 协程）
        store.records.first { it.isEmpty() }  // 等待首次加载完成并返回空列表
        // 记录一次跳过
        store.record("测试应用", rule(), ActionType.CLICK_NODE, "跳过")  // 记录一次跳过
        // 等待 records 流更新（record 内部 launch 异步更新 StateFlow）
        val records = store.records.first { it.isNotEmpty() }  // 等待 records 非空
        assertEquals(1, records.size)  // 断言记录数为 1
        assertEquals("测试应用", records[0].appName)  // 断言应用名正确
        assertEquals("com.test.app", records[0].packageName)  // 断言包名正确
        assertEquals("SPLASH", records[0].scene)  // 断言场景为 SPLASH（priority=100）
        assertEquals("跳过", records[0].matchedText)  // 断言命中文本正确
        assertEquals("CLICK_NODE", records[0].action)  // 断言动作名正确
    }

    @Test  // 标记测试方法
    fun clear_清空后records为空() = runBlocking {  // 测试清空后 records 为空
        // 先记录一条
        store.record("测试应用", rule(), ActionType.CLICK_NODE, "跳过")  // 记录一次跳过
        store.records.first { it.isNotEmpty() }  // 等待 records 非空
        // 清空历史
        store.clear()  // 清空历史（内部 launch 异步清空）
        // 等待 records 流更新为空
        val records = store.records.first { it.isEmpty() }  // 等待 records 为空
        assertTrue("清空后 records 应为空", records.isEmpty())  // 断言为空
    }

    @Test  // 标记测试方法
    fun countSince_统计指定时间后的记录数() = runBlocking {  // 测试 countSince 统计指定时间后记录数
        val before = System.currentTimeMillis()  // 记录前的基准时间戳
        store.record("测试应用", rule(), ActionType.CLICK_NODE, "跳过")  // 记录一次跳过
        store.records.first { it.isNotEmpty() }  // 等待 records 非空
        // 统计基准时间之后的记录数
        val count = store.countSince(before)  // 统计基准时间后记录数
        assertEquals(1, count)  // 断言为 1
        // 统计未来时间后的记录数应为 0
        val futureCount = store.countSince(System.currentTimeMillis() + 10_000L)  // 统计未来时间后记录数
        assertEquals(0, futureCount)  // 断言为 0
    }

    @Test  // 标记测试方法
    fun dailyCounts7d_返回7天每日计数() = runBlocking {  // 测试 dailyCounts7d 返回 7 天每日计数
        store.record("测试应用", rule(), ActionType.CLICK_NODE, "跳过")  // 记录一次跳过
        store.records.first { it.isNotEmpty() }  // 等待 records 非空
        // 获取最近 7 天每日计数
        val counts = store.dailyCounts7d()  // 取 7 天每日计数列表
        assertEquals(7, counts.size)  // 断言列表长度为 7
        // 最后一天（今天，索引 6）应有 1 条记录
        val todayCount = counts.last().second  // 取今天的计数
        assertEquals(1, todayCount)  // 断言今天计数为 1
        // 前 6 天应为 0
        val pastDays = counts.take(6)  // 取前 6 天
        assertTrue("前 6 天计数应为 0", pastDays.all { it.second == 0 })  // 断言前 6 天都为 0
    }

    @Test  // 标记测试方法
    fun record_多条记录按时间倒序排列() = runBlocking {  // 测试多条记录按时间倒序排列
        store.records.first { it.isEmpty() }  // 等待首次加载完成
        // 连续记录 3 条
        store.record("应用1", rule("r1"), ActionType.CLICK_NODE, "跳过1")  // 记录第一条
        store.records.first { it.size == 1 }  // 等待第一条写入
        store.record("应用2", rule("r2"), ActionType.BACK, "关闭")  // 记录第二条
        store.records.first { it.size == 2 }  // 等待第二条写入
        store.record("应用3", rule("r3"), ActionType.CLICK_NODE, "跳过3")  // 记录第三条
        val records = store.records.first { it.size == 3 }  // 等待第三条写入
        // 按时间倒序，最新在前
        assertEquals("应用3", records[0].appName)  // 最新记录在前
        assertEquals("应用2", records[1].appName)  // 中间记录
        assertEquals("应用1", records[2].appName)  // 最旧记录在后
    }
}
