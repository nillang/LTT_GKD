package com.ltt.gkd  // 测试包名与被测应用一致

import androidx.test.ext.junit.runners.AndroidJUnit4  // AndroidJUnit4 测试 runner
import androidx.test.platform.app.InstrumentationRegistry  // InstrumentationRegistry，取目标上下文
import com.ltt.gkd.data.app.WhitelistStore  // 被测的白名单存储
import kotlinx.coroutines.delay  // delay suspend 函数，用于轮询等待
import kotlinx.coroutines.flow.first  // Flow.first 取首值，等待流条件满足
import kotlinx.coroutines.runBlocking  // runBlocking 驱动 suspend 函数
import org.junit.Assert.assertFalse  // assertFalse 断言
import org.junit.Assert.assertTrue  // assertTrue 断言
import org.junit.Before  // @Before 注解，测试前置初始化
import org.junit.Test  // @Test 注解，标记测试方法
import org.junit.runner.RunWith  // @RunWith 注解，指定 runner

/**
 * WhitelistStore 真机 Instrumented Test。
 *
 * 验证 add/remove/isWhitelisted/toggle 在真机上的行为。
 *
 * 用 runBlocking 调用 suspend 函数。
 * 由于 isWhitelisted 读内存 cache，cache 由 init 中 whitelist.collect 异步同步，
 * 测试用轮询等待 cache 更新完成后再断言。
 */
@RunWith(AndroidJUnit4::class)  // 指定使用 AndroidJUnit4 runner 在真机执行
class WhitelistStoreInstrumentedTest {  // 白名单存储真机测试类

    private lateinit var store: WhitelistStore  // 被测白名单存储实例

    @Before  // 标记测试前置方法
    fun setup() {  // 测试前置初始化
        val ctx = InstrumentationRegistry.getTargetContext()  // 取被测应用上下文
        store = WhitelistStore(ctx)  // 用目标上下文创建白名单存储
    }

    /**
     * 轮询等待 isWhitelisted 返回期望值。
     *
     * cache 由 IO 线程的 whitelist.collect 异步同步，add/remove 返回后 cache 可能尚未更新，
     * 故在超时窗口内重试读取 isWhitelisted。
     *
     * @param pkg 待检测的包名
     * @param expected 期望的 isWhitelisted 返回值
     * @param timeoutMs 超时毫秒数，默认 2000ms
     */
    private suspend fun awaitWhitelist(pkg: String, expected: Boolean, timeoutMs: Long = 2000L) {  // 等待 cache 同步
        val deadline = System.currentTimeMillis() + timeoutMs  // 计算超时截止时间
        while (System.currentTimeMillis() < deadline) {  // 循环直到超时
            if (store.isWhitelisted(pkg) == expected) return  // 满足期望则返回
            delay(20)  // 短暂延迟后重试，让 IO 线程有机会同步 cache
        }
        // 超时仍未满足，最后一次检查并抛出明确断言错误
        if (store.isWhitelisted(pkg) != expected) {  // 仍不满足期望
            throw AssertionError("包名 $pkg 期望 isWhitelisted=$expected 但超时未同步")  // 抛出断言错误
        }
    }

    @Test  // 标记测试方法
    fun add_添加后isWhitelisted返回true() = runBlocking {  // 测试 add 后 isWhitelisted 返回 true
        val pkg = "com.ltt.test.add"  // 唯一测试包名，避免与系统应用冲突
        store.add(pkg)  // 添加到白名单（suspend）
        awaitWhitelist(pkg, true)  // 轮询等待 cache 同步并返回 true
        assertTrue("添加后应在白名单内", store.isWhitelisted(pkg))  // 断言 isWhitelisted 返回 true
    }

    @Test  // 标记测试方法
    fun remove_移除后isWhitelisted返回false() = runBlocking {  // 测试 remove 后 isWhitelisted 返回 false
        val pkg = "com.ltt.test.remove"  // 唯一测试包名
        store.add(pkg)  // 先添加到白名单
        awaitWhitelist(pkg, true)  // 等待 add 生效
        store.remove(pkg)  // 从白名单移除（suspend）
        awaitWhitelist(pkg, false)  // 轮询等待 cache 同步并返回 false
        assertFalse("移除后应不在白名单内", store.isWhitelisted(pkg))  // 断言 isWhitelisted 返回 false
    }

    @Test  // 标记测试方法
    fun toggle_true时加入白名单() = runBlocking {  // 测试 toggle 传 true 时加入白名单
        val pkg = "com.ltt.test.toggle.true"  // 唯一测试包名
        store.toggle(pkg, true)  // toggle 传 true 表示加入白名单（suspend）
        awaitWhitelist(pkg, true)  // 轮询等待 cache 同步并返回 true
        assertTrue("toggle(true) 应加入白名单", store.isWhitelisted(pkg))  // 断言已加入白名单
    }

    @Test  // 标记测试方法
    fun toggle_false时从白名单移除() = runBlocking {  // 测试 toggle 传 false 时从白名单移除
        val pkg = "com.ltt.test.toggle.false"  // 唯一测试包名
        store.add(pkg)  // 先添加到白名单
        awaitWhitelist(pkg, true)  // 等待 add 生效
        store.toggle(pkg, false)  // toggle 传 false 表示从白名单移除（suspend）
        awaitWhitelist(pkg, false)  // 轮询等待 cache 同步并返回 false
        assertFalse("toggle(false) 应从白名单移除", store.isWhitelisted(pkg))  // 断言已从白名单移除
    }

    @Test  // 标记测试方法
    fun isWhitelisted_未添加的包名返回false() = runBlocking {  // 测试未添加的包名 isWhitelisted 返回 false
        val pkg = "com.ltt.test.never.added"  // 从未添加的包名
        // 轮询等待 cache 初始化完成（初始为 emptySet，isWhitelisted 返回 false 即满足）
        awaitWhitelist(pkg, false)  // 等待 cache 同步并返回 false
        assertFalse("未添加的包名应不在白名单内", store.isWhitelisted(pkg))  // 断言返回 false
    }
}
