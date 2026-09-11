package com.ltt.gkd  // 测试包名与被测应用一致

import androidx.test.ext.junit.runners.AndroidJUnit4  // AndroidJUnit4 测试 runner
import androidx.test.platform.app.InstrumentationRegistry  // InstrumentationRegistry，取目标上下文
import com.ltt.gkd.data.rule.Rule  // Rule 数据类，构造测试规则
import com.ltt.gkd.data.rule.RuleEngine  // 被测的规则引擎
import kotlinx.coroutines.CoroutineScope  // 协程作用域
import kotlinx.coroutines.Dispatchers  // 调度器，Dispatchers.Main
import kotlinx.coroutines.SupervisorJob  // SupervisorJob，子协程异常不传染
import kotlinx.coroutines.cancel  // CoroutineScope.cancel 扩展函数，取消作用域
import kotlinx.coroutines.flow.MutableStateFlow  // 可变 StateFlow，注入规则源
import kotlinx.coroutines.launch  // launch 扩展函数，启动子协程
import kotlinx.coroutines.runBlocking  // runBlocking 驱动 suspend 函数
import kotlinx.coroutines.test.UnconfinedTestDispatcher  // 不受限测试调度器，协程同步执行
import kotlinx.coroutines.test.resetMain  // 重置 Main 调度器
import kotlinx.coroutines.test.setMain  // 设置 Main 调度器
import org.junit.After  // @After 注解，测试后置清理
import org.junit.Assert.assertEquals  // assertEquals 断言
import org.junit.Assert.assertTrue  // assertTrue 断言
import org.junit.Before  // @Before 注解，测试前置初始化
import org.junit.Test  // @Test 注解，标记测试方法
import org.junit.runner.RunWith  // @RunWith 注解，指定 runner

/**
 * RuleEngine 真机 Instrumented Test。
 *
 * 验证 markTriggered/resetThrottle 节流逻辑在真机协程环境（Dispatchers.Main）下的行为，
 * 重点测试 throttleMs 窗口内重复触发是否被正确节流。
 *
 * 用 CoroutineScope + SupervisorJob + Dispatchers.Main 模拟真机协程环境。
 */
@RunWith(AndroidJUnit4::class)  // 指定使用 AndroidJUnit4 runner 在真机执行
class RuleEngineInstrumentedTest {  // 规则引擎真机测试类

    private lateinit var rulesFlow: MutableStateFlow<List<Rule>>  // 注入用的可变规则源 StateFlow
    private lateinit var engine: RuleEngine  // 被测规则引擎实例
    private lateinit var scope: CoroutineScope  // 真机模拟协程作用域（Main 调度器）

    @Before  // 标记测试前置方法
    fun setup() {  // 测试前置初始化
        // 设置 Dispatchers.Main 为不受限测试调度器，让 runBlocking 内协程同步执行
        Dispatchers.setMain(UnconfinedTestDispatcher())  // 替换 Main 调度器为测试调度器
        rulesFlow = MutableStateFlow(emptyList())  // 初始化空规则流
        engine = RuleEngine(rulesFlow)  // 用规则流构造规则引擎
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)  // 创建 Main 线程协程作用域
    }

    @After  // 标记测试后置方法
    fun tearDown() {  // 测试后置清理
        scope.cancel()  // 取消协程作用域
        Dispatchers.resetMain()  // 恢复 Main 调度器
    }

    /** 构造一条测试规则，包名固定为 com.test.app，可自定义节流窗口 */
    private fun rule(id: String, throttleMs: Long = 2000L): Rule =  // 构造测试规则辅助方法
        Rule(id = id, name = id, packageName = "com.test.app", throttleMs = throttleMs)  // 简单测试规则

    @Test  // 标记测试方法
    fun markTriggered_节流窗口内重复触发被过滤() {  // 测试节流窗口内重复触发被过滤
        rulesFlow.value = listOf(rule("r1", throttleMs = 5000L))  // 注入节流 5 秒的规则
        // 第一次查询应命中（尚未触发）
        assertEquals(1, engine.candidates("com.test.app", null, 1000L).size)  // 首次查询返回 1 条
        // 标记触发，记录触发时间戳
        engine.markTriggered("r1", 1000L)  // 标记 r1 在 1000ms 触发
        // 在 5000ms 节流窗口内再次查询应被过滤
        val inWindow = engine.candidates("com.test.app", null, 3000L)  // 距上次触发 2000ms 时查询
        assertTrue("节流窗口内应被过滤", inWindow.isEmpty())  // 断言节流窗口内返回空列表
    }

    @Test  // 标记测试方法
    fun markTriggered_节流窗口外重新可触发() {  // 测试节流窗口外重新可触发
        rulesFlow.value = listOf(rule("r1", throttleMs = 5000L))  // 注入节流 5 秒的规则
        engine.markTriggered("r1", 1000L)  // 标记 r1 在 1000ms 触发
        // 距上次触发 6000ms，超过 5000ms 节流窗口
        val outWindow = engine.candidates("com.test.app", null, 7000L)  // 7000ms 时查询
        assertEquals("超过节流窗口应重新返回", 1, outWindow.size)  // 断言超过窗口返回 1 条
    }

    @Test  // 标记测试方法
    fun resetThrottle_清空所有节流状态() {  // 测试 resetThrottle 清空所有节流状态
        rulesFlow.value = listOf(rule("r1", throttleMs = 100_000L))  // 注入节流 100 秒的规则
        engine.markTriggered("r1", 1000L)  // 标记 r1 在 1000ms 触发
        // 未重置时节流生效
        assertTrue(engine.candidates("com.test.app", null, 2000L).isEmpty())  // 节流内查询为空
        // 重置节流状态
        engine.resetThrottle()  // 清空节流映射表
        // 重置后立即查询应返回
        assertEquals(1, engine.candidates("com.test.app", null, 2000L).size)  // 断言重置后返回 1 条
    }

    @Test  // 标记测试方法
    fun markTriggered_多次标记更新触发时间戳() {  // 测试多次标记更新触发时间戳
        rulesFlow.value = listOf(rule("r1", throttleMs = 5000L))  // 注入节流 5 秒的规则
        engine.markTriggered("r1", 1000L)  // 第一次标记触发时间 1000ms
        // 4000ms 时距上次触发 3000ms，仍在 5000ms 节流窗口内
        assertTrue(engine.candidates("com.test.app", null, 4000L).isEmpty())  // 4000ms 时被节流
        // 重新标记触发时间为 4000ms
        engine.markTriggered("r1", 4000L)  // 更新触发时间戳为 4000ms
        // 6000ms 时距新触发时间 2000ms，仍在节流窗口内
        assertTrue(engine.candidates("com.test.app", null, 6000L).isEmpty())  // 6000ms 仍被节流
        // 10000ms 时距新触发时间 6000ms，超过 5000ms 节流窗口
        assertEquals(1, engine.candidates("com.test.app", null, 10000L).size)  // 10000ms 重新返回
    }

    @Test  // 标记测试方法
    fun 节流逻辑_在真机协程环境下行为一致() = runBlocking {  // 测试真机协程环境下节流逻辑一致
        rulesFlow.value = listOf(rule("r1", throttleMs = 3000L))  // 注入节流 3 秒的规则
        // 在 Main 调度器作用域内更新规则并测试节流
        val job = scope.launch {  // 启动 Main 协程执行测试逻辑
            val now = System.currentTimeMillis()  // 取当前真机时间戳
            engine.markTriggered("r1", now)  // 标记当前触发
            // 节流窗口内查询应为空
            val inWindow = engine.candidates("com.test.app", null, now + 1000L)  // 1 秒后查询
            assertTrue("真机环境节流窗口内应被过滤", inWindow.isEmpty())  // 断言为空
            // 超过节流窗口后查询应返回
            val outWindow = engine.candidates("com.test.app", null, now + 4000L)  // 4 秒后查询
            assertEquals("真机环境超过节流窗口应返回", 1, outWindow.size)  // 断言返回 1 条
        }
        job.join()  // 等待 Main 协程执行完成
    }

    @Test  // 标记测试方法
    fun markTriggered_对未注册规则ID无副作用() {  // 测试对未注册规则 ID 调用 markTriggered 无副作用
        rulesFlow.value = listOf(rule("r1", throttleMs = 5000L))  // 只注入 r1
        // 对不存在的规则 ID 调用 markTriggered，应只写入映射表不影响 r1
        engine.markTriggered("unknown", 1000L)  // 标记不存在的规则 ID
        // r1 应仍可正常命中（其节流映射条目未被写入）
        assertEquals(1, engine.candidates("com.test.app", null, 1000L).size)  // r1 正常返回
    }
}
