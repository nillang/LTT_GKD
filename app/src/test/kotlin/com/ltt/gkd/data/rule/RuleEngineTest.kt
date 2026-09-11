package com.ltt.gkd.data.rule

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * RuleEngine 单元测试。
 *
 * RuleEngine 接受 [StateFlow] 作为规则源，测试中直接用 [MutableStateFlow] 注入，
 * 无需 mock RuleRepository。
 *
 * 覆盖：
 * - 空 packageName / 空 rules 时返回空
 * - 禁用规则被过滤
 * - 包名匹配：精确 / 空=通用兜底
 * - Activity 匹配：endsWith 兼容短类名
 * - 节流窗口：未到时间过滤、超过返回、markTriggered 后立即过滤
 * - resetThrottle 清空节流
 * - 优先级降序输出
 */
class RuleEngineTest {

    private lateinit var rulesFlow: MutableStateFlow<List<Rule>>
    private lateinit var engine: RuleEngine

    @Before
    fun setup() {
        rulesFlow = MutableStateFlow(emptyList())
        engine = RuleEngine(rulesFlow)
    }

    private fun rule(
        id: String,
        packageName: String = "",
        activity: String? = null,
        enabled: Boolean = true,
        priority: Int = 0,
        throttleMs: Long = 2000L
    ) = Rule(
        id = id,
        name = id,
        packageName = packageName,
        activity = activity,
        enabled = enabled,
        priority = priority,
        throttleMs = throttleMs
    )

    // ---------- 空输入 ----------

    @Test
    fun `空 packageName 返回空列表`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x"))
        assertEquals(emptyList<Rule>(), engine.candidates(null, null, 0L))
        assertEquals(emptyList<Rule>(), engine.candidates("", null, 0L))
    }

    @Test
    fun `空规则列表返回空列表`() {
        rulesFlow.value = emptyList()
        assertEquals(emptyList<Rule>(), engine.candidates("com.x", null, 0L))
    }

    // ---------- enabled 过滤 ----------

    @Test
    fun `禁用规则被过滤`() {
        rulesFlow.value = listOf(
            rule("on", packageName = "com.x", enabled = true),
            rule("off", packageName = "com.x", enabled = false)
        )
        val result = engine.candidates("com.x", null, 0L)
        assertEquals(1, result.size)
        assertEquals("on", result[0].id)
    }

    // ---------- 包名匹配 ----------

    @Test
    fun `精确包名匹配`() {
        rulesFlow.value = listOf(
            rule("wx", packageName = "com.tencent.mm"),
            rule("bd", packageName = "com.baidu.search")
        )
        val result = engine.candidates("com.tencent.mm", null, 0L)
        assertEquals(1, result.size)
        assertEquals("wx", result[0].id)
    }

    @Test
    fun `空包名规则作为通用兜底`() {
        rulesFlow.value = listOf(
            rule("general", packageName = ""),  // 通用兜底
            rule("specific", packageName = "com.tencent.mm")
        )
        // 任意包名都应命中通用兜底
        val result = engine.candidates("com.unknown", null, 0L)
        assertEquals(1, result.size)
        assertEquals("general", result[0].id)
    }

    // ---------- Activity 匹配 ----------

    @Test
    fun `activity 为 null 时任意 Activity 都匹配`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", activity = null))
        val result = engine.candidates("com.x", "MainActivity", 0L)
        assertEquals(1, result.size)
    }

    @Test
    fun `activity 用 endsWith 兼容短类名`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", activity = "SplashActivity"))
        // 全限定名以短类名结尾，应匹配
        val result = engine.candidates("com.x", "com.x.SplashActivity", 0L)
        assertEquals(1, result.size)
    }

    @Test
    fun `activity 不匹配时被过滤`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", activity = "SplashActivity"))
        val result = engine.candidates("com.x", "MainActivity", 0L)
        assertTrue("activity 不匹配应被过滤", result.isEmpty())
    }

    @Test
    fun `规则 activity 非空但传入 activity 为 null 时不命中`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", activity = "SplashActivity"))
        val result = engine.candidates("com.x", null, 0L)
        assertTrue("规则限定 activity 但实际为 null 应不命中", result.isEmpty())
    }

    // ---------- 节流 ----------

    @Test
    fun `首次查询时未触发节流的规则全部返回`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", throttleMs = 5000L))
        val result = engine.candidates("com.x", null, 1000L)
        assertEquals(1, result.size)
    }

    @Test
    fun `节流窗口内不返回`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", throttleMs = 5000L))
        engine.markTriggered("r1", 1000L)
        // 距离上次触发 2000ms，节流窗口 5000ms 内
        val result = engine.candidates("com.x", null, 3000L)
        assertTrue("节流窗口内应被过滤", result.isEmpty())
    }

    @Test
    fun `节流窗口外重新返回`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", throttleMs = 5000L))
        engine.markTriggered("r1", 1000L)
        // 距离上次触发 6000ms，超过 5000ms 节流窗口
        val result = engine.candidates("com.x", null, 7000L)
        assertEquals("超过节流窗口应返回", 1, result.size)
    }

    @Test
    fun `resetThrottle 清空所有节流状态`() {
        rulesFlow.value = listOf(rule("r1", packageName = "com.x", throttleMs = 100_000L))
        engine.markTriggered("r1", 1000L)
        // 未重置时节流生效
        assertTrue(engine.candidates("com.x", null, 2000L).isEmpty())
        // 重置后立即可用
        engine.resetThrottle()
        assertEquals(1, engine.candidates("com.x", null, 2000L).size)
    }

    // ---------- 优先级 ----------

    @Test
    fun `结果按 priority 降序输出`() {
        rulesFlow.value = listOf(
            rule("low", packageName = "com.x", priority = 10),
            rule("high", packageName = "com.x", priority = 100),
            rule("mid", packageName = "com.x", priority = 50)
        )
        val result = engine.candidates("com.x", null, 0L)
        assertEquals(listOf("high", "mid", "low"), result.map { it.id })
    }

    @Test
    fun `多个匹配规则同时命中`() {
        rulesFlow.value = listOf(
            rule("splash", packageName = "com.x", activity = "SplashActivity", priority = 100),
            rule("popup", packageName = "com.x", priority = 80),  // 通用兜底（任意 activity）
            rule("banner", packageName = "com.x", priority = 60)
        )
        val result = engine.candidates("com.x", "com.x.SplashActivity", 0L)
        assertEquals(3, result.size)
        assertEquals(listOf("splash", "popup", "banner"), result.map { it.id })
    }
}
