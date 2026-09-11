package com.ltt.gkd.util

import com.ltt.gkd.data.rule.MatchTarget
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
/**
 * PatternUtils 单元测试。
 *
 * 覆盖：
 * - 非正则字面量匹配（含特殊字符转义）
 * - 正则模式匹配
 * - 大小写敏感/不敏感
 * - 正则编译失败的降级（mock Logger 避免打印日志）
 */
class PatternUtilsTest {

    @Test
    fun `非正则字面量匹配普通文本`() {
        val target = MatchTarget(regex = false, caseInsensitive = false)
        val pattern = PatternUtils.compile("跳过广告", target)
        assertTrue("应匹配包含文本", pattern.matcher("点击跳过广告").find())
        assertFalse("不应匹配其他文本", pattern.matcher("关闭广告").find())
    }

    @Test
    fun `非正则模式转义特殊字符`() {
        // 圆括号是正则特殊字符，非正则模式下应做字面量匹配
        val target = MatchTarget(regex = false, caseInsensitive = false)
        val pattern = PatternUtils.compile("(Skip)", target)
        assertTrue("圆括号应被转义为字面量", pattern.matcher("广告(Skip)").find())
        assertFalse("不应按正则分组语义匹配", pattern.matcher("Skip").find())
    }

    @Test
    fun `正则模式匹配`() {
        val target = MatchTarget(regex = true, caseInsensitive = false)
        // 跳过.* 中的 .* 是正则通配
        val pattern = PatternUtils.compile("跳过.*", target)
        assertTrue("正则应匹配任意结尾", pattern.matcher("跳过广告").find())
        assertTrue("正则应匹配仅跳过", pattern.matcher("跳过").find())
        assertFalse("不应匹配其他词", pattern.matcher("关闭").find())
    }

    @Test
    fun `大小写不敏感默认开启`() {
        val target = MatchTarget(regex = false, caseInsensitive = true)
        val pattern = PatternUtils.compile("SKIP", target)
        assertTrue("应忽略大小写匹配 skip", pattern.matcher("skip").find())
        assertTrue("应忽略大小写匹配 Skip", pattern.matcher("Skip").find())
    }

    @Test
    fun `大小写敏感时精确匹配`() {
        val target = MatchTarget(regex = false, caseInsensitive = false)
        val pattern = PatternUtils.compile("Skip", target)
        assertTrue("应匹配原样", pattern.matcher("Skip").find())
        assertFalse("不应匹配小写", pattern.matcher("skip").find())
    }

    @Test
    fun `正则编译失败时降级为字面量`() {
        // isReturnDefaultValues=true 让 android.util.Log.w 返回默认值而非抛异常
        val target = MatchTarget(regex = true, caseInsensitive = true)
        // 非法正则（未闭合的分组）
        val pattern = PatternUtils.compile("跳过(", target)
        // 降级后应做字面量匹配
        assertTrue("降级后应字面量匹配", pattern.matcher("跳过(").find())
        assertFalse("不应按正则匹配", pattern.matcher("跳过").find())
    }
}
