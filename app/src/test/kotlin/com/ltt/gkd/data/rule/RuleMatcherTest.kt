package com.ltt.gkd.data.rule

import android.view.accessibility.AccessibilityNodeInfo
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * RuleMatcher 单元测试。
 *
 * 通过 mockk mock [AccessibilityNodeInfo]（Android final class），
 * 模拟控件树结构，验证 TEXT/DESC/ID/OCR 四种匹配类型。
 *
 * 约定：默认 mock childCount=0（叶子节点），让 NodeUtils.findFirst 仅检查根节点。
 */
class RuleMatcherTest {

    private lateinit var root: AccessibilityNodeInfo
    private lateinit var matcher: RuleMatcher

    @Before
    fun setup() {
        root = mockk(relaxed = true)
        // relaxed 模式下 childCount 默认返回 0，刚好是叶子节点
        matcher = RuleMatcher()
    }

    private fun rule(
        type: MatchType,
        text: List<String> = emptyList(),
        ids: List<String> = emptyList(),
        regex: Boolean = false,
        caseInsensitive: Boolean = true,
        enabled: Boolean = true
    ) = Rule(
        id = "test",
        name = "test",
        enabled = enabled,
        match = MatchTarget(
            type = type,
            text = text,
            ids = ids,
            regex = regex,
            caseInsensitive = caseInsensitive
        )
    )

    // ---------- null / 禁用 ----------

    @Test
    fun `null root 返回 null`() {
        val r = rule(MatchType.TEXT, text = listOf("跳过"))
        assertNull(matcher.match(null, r))
    }

    @Test
    fun `禁用规则返回 null`() {
        val r = rule(MatchType.TEXT, text = listOf("跳过"), enabled = false)
        assertNull(matcher.match(root, r))
    }

    // ---------- TEXT 匹配 ----------

    @Test
    fun `TEXT 类型匹配 text 属性`() {
        every { root.text } returns "点击跳过广告"
        every { root.contentDescription } returns null
        val r = rule(MatchType.TEXT, text = listOf("跳过广告"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型匹配 contentDescription（text 为 null 时回退）`() {
        every { root.text } returns null
        every { root.contentDescription } returns "跳过广告"
        val r = rule(MatchType.TEXT, text = listOf("跳过广告"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型大小写不敏感`() {
        every { root.text } returns "Click SKIP AD"
        every { root.contentDescription } returns null
        val r = rule(MatchType.TEXT, text = listOf("skip"), caseInsensitive = true)
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型关键词不匹配时返回 null`() {
        every { root.text } returns "关闭广告"
        every { root.contentDescription } returns null
        val r = rule(MatchType.TEXT, text = listOf("跳过"))
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型多个关键词任一命中`() {
        every { root.text } returns "关闭"
        every { root.contentDescription } returns null
        val r = rule(MatchType.TEXT, text = listOf("跳过", "关闭"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型空关键词列表返回 null`() {
        every { root.text } returns "跳过广告"
        val r = rule(MatchType.TEXT, text = emptyList())
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `TEXT 类型节点 text 和 desc 都为 null 时返回 null`() {
        every { root.text } returns null
        every { root.contentDescription } returns null
        val r = rule(MatchType.TEXT, text = listOf("跳过"))
        assertNull(matcher.match(root, r))
    }

    // ---------- DESC 匹配 ----------

    @Test
    fun `DESC 类型匹配 contentDescription`() {
        every { root.contentDescription } returns "跳过广告"
        val r = rule(MatchType.DESC, text = listOf("跳过广告"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `DESC 类型不匹配 text 属性（仅匹配 desc）`() {
        every { root.text } returns "跳过广告"
        every { root.contentDescription } returns null
        val r = rule(MatchType.DESC, text = listOf("跳过广告"))
        // text 命中也不应被 DESC 类型返回
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `DESC 类型关键词不匹配时返回 null`() {
        every { root.contentDescription } returns "其他内容"
        val r = rule(MatchType.DESC, text = listOf("跳过"))
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `DESC 类型空关键词返回 null`() {
        every { root.contentDescription } returns "跳过"
        val r = rule(MatchType.DESC, text = emptyList())
        assertNull(matcher.match(root, r))
    }

    // ---------- ID 匹配 ----------

    @Test
    fun `ID 类型精确匹配 viewIdResourceName`() {
        every { root.viewIdResourceName } returns "com.tencent.mm:id/skip_btn"
        val r = rule(MatchType.ID, ids = listOf("com.tencent.mm:id/skip_btn"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `ID 类型 endsWith 简写匹配`() {
        every { root.viewIdResourceName } returns "com.tencent.mm:id/skip_btn"
        // 只传短名 "skip_btn"，应通过 ":id/skip_btn" 后缀匹配
        val r = rule(MatchType.ID, ids = listOf("skip_btn"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `ID 类型多个 id 任一命中`() {
        every { root.viewIdResourceName } returns "com.x:id/close"
        val r = rule(MatchType.ID, ids = listOf("com.x:id/skip", "com.x:id/close"))
        assertSame(root, matcher.match(root, r))
    }

    @Test
    fun `ID 类型不匹配时返回 null`() {
        every { root.viewIdResourceName } returns "com.x:id/other"
        val r = rule(MatchType.ID, ids = listOf("com.x:id/skip"))
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `ID 类型节点 id 为 null 时返回 null`() {
        every { root.viewIdResourceName } returns null
        val r = rule(MatchType.ID, ids = listOf("skip"))
        assertNull(matcher.match(root, r))
    }

    @Test
    fun `ID 类型空 ids 列表返回 null`() {
        every { root.viewIdResourceName } returns "com.x:id/skip"
        val r = rule(MatchType.ID, ids = emptyList())
        assertNull(matcher.match(root, r))
    }

    // ---------- OCR ----------

    @Test
    fun `OCR 类型始终返回 null（由 OcrManager 单独处理）`() {
        val r = rule(MatchType.OCR)
        assertNull(matcher.match(root, r))
    }

    // ---------- 子节点遍历 ----------

    @Test
    fun `TEXT 匹配命中的是子节点而非根节点`() {
        val child = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { root.childCount } returns 1
        every { root.getChild(0) } returns child
        every { root.text } returns null
        every { root.contentDescription } returns null
        every { child.text } returns "跳过广告"
        every { child.contentDescription } returns null

        val r = rule(MatchType.TEXT, text = listOf("跳过"))
        assertSame(child, matcher.match(root, r))
    }

    @Test
    fun `所有节点都不匹配时返回 null`() {
        val child = mockk<AccessibilityNodeInfo>(relaxed = true)
        every { root.childCount } returns 1
        every { root.getChild(0) } returns child
        every { root.text } returns null
        every { root.contentDescription } returns null
        every { child.text } returns "其他内容"
        every { child.contentDescription } returns null

        val r = rule(MatchType.TEXT, text = listOf("跳过"))
        assertNull(matcher.match(root, r))
    }
}
