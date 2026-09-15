package com.ltt.gkd.data.subscription  // 声明包名，与被测类一致

import org.junit.Assert.assertEquals  // 导入断言：相等
import org.junit.Assert.assertFalse  // 导入断言：假
import org.junit.Assert.assertNull  // 导入断言：空
import org.junit.Assert.assertTrue  // 导入断言：真
import org.junit.Test  // 导入 JUnit 测试注解

/**
 * [SubscriptionUrls] 单元测试：覆盖 Gist ID 抽取、类型识别与链接合法性校验。
 */
class SubscriptionUrlsTest {  // 订阅链接识别测试类

    private val id32 = "a".repeat(32)  // 32 位十六进制样例（全 a）

    @Test  // 测试注解
    fun `裸 32 位 ID 识别为 Gist`() {  // 测试：裸 ID
        assertEquals(id32, SubscriptionUrls.gistId(id32))  // 抽取应等于自身
        assertEquals(SourceType.GIST, SubscriptionUrls.detectType(id32))  // 类型为 Gist
        assertTrue(SubscriptionUrls.isValid(id32))  // 合法
    }

    @Test  // 测试注解
    fun `各种 Gist 链接都能抽取 ID`() {  // 测试：Gist 链接形态
        val urls = listOf(  // 多种形态
            "https://gist.github.com/user/$id32",  // 网页链接
            "https://api.github.com/gists/$id32",  // API 链接
            "https://gist.githubusercontent.com/user/$id32/raw/file.json"  // raw 链接
        )
        urls.forEach { u ->  // 遍历
            assertEquals(id32, SubscriptionUrls.gistId(u))  // 都能抽到 ID
            assertEquals(SourceType.GIST, SubscriptionUrls.detectType(u))  // 类型 Gist
            assertTrue(SubscriptionUrls.isValid(u))  // 合法
        }
    }

    @Test  // 测试注解
    fun `普通 URL 识别为 URL 类型`() {  // 测试：普通链接
        val u = "https://example.com/rules/my_rules.json"  // 普通 JSON 链接
        assertNull(SubscriptionUrls.gistId(u))  // 抽不到 Gist ID
        assertEquals(SourceType.URL, SubscriptionUrls.detectType(u))  // 类型为 URL
        assertTrue(SubscriptionUrls.isValid(u))  // 合法
    }

    @Test  // 测试注解
    fun `非法输入校验失败`() {  // 测试：非法输入
        assertFalse(SubscriptionUrls.isValid(""))  // 空串非法
        assertFalse(SubscriptionUrls.isValid("   "))  // 空白非法
        assertFalse(SubscriptionUrls.isValid("ftp://example.com/a.json"))  // 非 http(s) 且非 Gist ID
        assertFalse(SubscriptionUrls.isValid("example.com/a.json"))  // 缺少协议头
    }

    @Test  // 测试注解
    fun `短十六进制串不误判为 Gist`() {  // 测试：长度不足不误判
        val short = "abc123"  // 6 位，不足 32
        assertNull(SubscriptionUrls.gistId(short))  // 不应抽到 ID
        assertFalse(SubscriptionUrls.isValid(short))  // 既非合法 URL 也非 Gist
    }
}
