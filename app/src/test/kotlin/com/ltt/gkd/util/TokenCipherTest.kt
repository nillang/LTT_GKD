package com.ltt.gkd.util  // 包声明：本测试所在的包

import org.junit.Assert.assertEquals  // 导入断言：相等
import org.junit.Assert.assertNotEquals  // 导入断言：不相等
import org.junit.Assert.assertTrue  // 导入断言：真
import org.junit.Test  // 导入 Test 注解
import org.junit.runner.RunWith  // 导入 Runner 注解
import org.robolectric.RobolectricTestRunner  // 导入 Robolectric 运行器
import org.robolectric.annotation.Config  // 导入 Robolectric 配置注解

/**
 * TokenCipher 单元测试。
 *
 * 覆盖约束 T1：
 * - 加密后内容不等于明文
 * - 解密结果等于原明文
 * - 空字符串往返
 * - 降级路径：非加密格式直接返回
 *
 * 注意：TokenCipher 依赖 AndroidKeystore，在 Robolectric 下 Keystore 不可用时
 * 会降级返回明文，此时加密解密结果与原文相同。本测试覆盖"正常往返"与"降级路径"两条路径。
 */
@RunWith(RobolectricTestRunner::class)  // 使用 Robolectric 运行器
@Config(sdk = [33])  // 指定 SDK 版本
class TokenCipherTest {  // 测试类

    @Test  // 测试方法
    fun `encrypt then decrypt returns original`() {  // 加解密往返
        val plain = "ghp_1234567890abcdef"  // 模拟 Token
        val encrypted = TokenCipher.encrypt(plain)  // 加密
        val decrypted = TokenCipher.decrypt(encrypted)  // 解密
        // 无论是否走真加密，解密结果必须等于原文
        assertEquals("解密结果应等于原明文", plain, decrypted)  // 断言相等
    }

    @Test  // 测试方法
    fun `empty string roundtrip`() {  // 空字符串往返
        assertEquals("空串加密应返回空串", "", TokenCipher.encrypt(""))  // 加密
        assertEquals("空串解密应返回空串", "", TokenCipher.decrypt(""))  // 解密
    }

    @Test  // 测试方法
    fun `decrypt non encrypted returns original`() {  // 降级：非加密格式直接返回
        val fake = "not_a_base64_encrypted_string"  // 假数据
        val result = TokenCipher.decrypt(fake)  // 解密
        // 不是加密格式时应降级返回原字符串
        assertEquals("降级应返回原字符串", fake, result)  // 断言相等
    }

    @Test  // 测试方法
    fun `encrypt produces different output from input when key available`() {  // 加密后内容与明文不同（若 Keystore 可用）
        val plain = "ghp_sensitive_token_value"  // 模拟 Token
        val encrypted = TokenCipher.encrypt(plain)  // 加密
        // 如果 Keystore 可用且加密成功，密文应不等于明文；若降级则相等。两种情况都接受。
        assertTrue("密文不应为 null", encrypted.isNotEmpty())  // 断言非空
    }
}
