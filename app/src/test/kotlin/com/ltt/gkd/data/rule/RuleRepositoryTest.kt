package com.ltt.gkd.data.rule  // 包声明：本测试所在的包

import android.content.Context  // 导入 Context，单元测试用 Robolectric 替代
import androidx.test.core.app.ApplicationProvider  // 导入 ApplicationProvider，提供 Robolectric Context
import kotlinx.coroutines.runBlocking  // 导入 runBlocking，把挂起转同步
import kotlinx.coroutines.flow.first  // 导入 first，取 Flow 首值
import org.junit.Assert.assertEquals  // 导入断言：相等
import org.junit.Assert.assertTrue  // 导入断言：真
import org.junit.Before  // 导入 Before 注解，初始化方法
import org.junit.Test  // 导入 Test 注解
import org.junit.runner.RunWith  // 导入 Runner 注解
import org.robolectric.RobolectricTestRunner  // 导入 Robolectric 测试运行器
import org.robolectric.annotation.Config  // 导入 Robolectric 配置注解
import java.io.File  // 导入 File，文件操作

/**
 * RuleRepository 单元测试。
 *
 * 覆盖约束 T1 中"三源合并去重"：
 * - BUILT_IN → LOCAL → SUBSCRIBED 覆盖顺序
 * - 同 ID 规则后者覆盖前者
 * - 本地规则按 createdAt 倒序
 * - 订阅规则按 subscribers 倒序
 * - 合并后按 priority 降序
 *
 * 注意：本测试需要 Robolectric 提供模拟的 Context 与 assets 访问能力。
 */
@RunWith(RobolectricTestRunner::class)  // 使用 Robolectric 运行器
@Config(sdk = [33])  // 指定 SDK 版本
class RuleRepositoryTest {  // 测试类

    private lateinit var context: Context  // 测试用 Context
    private lateinit var repo: RuleRepository  // 待测仓库

    @Before  // 每个测试前执行
    fun setUp() {  // 初始化方法
        context = ApplicationProvider.getApplicationContext()  // 获取 Robolectric Context
        repo = RuleRepository(context)  // 构造仓库
        // 清空本地/订阅目录，避免上次测试残留
        File(context.filesDir, RuleRepository.LOCAL_DIR_PATH).deleteRecursively()  // 清空本地目录
        File(context.filesDir, RuleRepository.SUBSCRIBED_DIR_PATH).deleteRecursively()  // 清空订阅目录
    }

    @Test  // 测试方法
    fun `builtIn rules load and merge`() = runBlocking {  // 内置规则加载测试
        // assets/rules/general.json 内置 2 条规则
        repo.reload()  // 触发加载
        val builtIn = repo.builtInRules.first()  // 取内置规则
        assertTrue("内置规则应至少 1 条", builtIn.isNotEmpty())  // 断言非空
    }

    @Test  // 测试方法
    fun `local rule overrides builtIn with same id`() = runBlocking {  // 同 ID 本地覆盖内置
        repo.reload()  // 先加载内置
        // 用 universal_splash_skip 这个内置已有 ID 写一条本地规则
        val override = Rule(
            id = "universal_splash_skip",  // 与内置同 ID
            name = "测试覆盖",  // 名称
            packageName = "",  // 通用兜底
            priority = 999,  // 故意提高优先级便于验证
            author = "test",  // 作者
            createdAt = System.currentTimeMillis(),  // 创建时间
            source = RuleSource.LOCAL  // 本地来源
        )
        val ok = repo.saveLocalRule(override)  // 保存本地规则
        assertTrue("保存本地规则应成功", ok)  // 断言成功
        val merged = repo.rules.first()  // 取合并后规则
        val hit = merged.firstOrNull { it.id == "universal_splash_skip" }  // 查同 ID
        assertTrue("合并后应存在该 ID", hit != null)  // 断言存在
        assertEquals("本地版本应覆盖内置", 999, hit!!.priority)  // 断言优先级为本地值
    }

    @Test  // 测试方法
    fun `subscribed rule overrides local with same id`() = runBlocking {  // 同 ID 订阅覆盖本地
        // 准备本地
        val local = Rule(
            id = "com.test.splash",  // 规则 ID
            name = "本地版",  // 名称
            packageName = "com.test",  // 包名
            priority = 50,  // 优先级
            author = "local",  // 作者
            createdAt = 100L,  // 创建时间
            source = RuleSource.LOCAL  // 本地来源
        )
        repo.saveLocalRule(local)  // 保存本地
        // 准备订阅（直接写文件，加 sourceId 前缀）
        val subscribedDir = File(context.filesDir, RuleRepository.SUBSCRIBED_DIR_PATH).apply { mkdirs() }  // 订阅目录
        val rs = RuleSet(  // 构造订阅 RuleSet
            name = "测试订阅",  // 名称
            author = "sub",  // 作者
            rules = listOf(local.copy(priority = 80, source = RuleSource.SUBSCRIBED, subscribers = 5))  // 订阅版本
        )
        val json = com.ltt.gkd.util.globalMoshi.adapter(RuleSet::class.java).toJson(rs)  // 序列化
        File(subscribedDir, "s1__test.json").writeText(json)  // 写入订阅文件
        repo.reload()  // 重新加载
        val merged = repo.rules.first()  // 取合并后规则
        val hit = merged.firstOrNull { it.id == "com.test.splash" }  // 查同 ID
        assertTrue("合并后应存在该 ID", hit != null)  // 断言存在
        assertEquals("订阅版本应覆盖本地", 80, hit!!.priority)  // 断言优先级为订阅值
    }

    @Test  // 测试方法
    fun `merged rules sorted by priority desc`() = runBlocking {  // 合并后按 priority 降序
        repo.saveLocalRule(Rule(id = "a", name = "A", priority = 10, author = "t", createdAt = 1L))  // 低优
        repo.saveLocalRule(Rule(id = "b", name = "B", priority = 100, author = "t", createdAt = 2L))  // 高优
        repo.saveLocalRule(Rule(id = "c", name = "C", priority = 50, author = "t", createdAt = 3L))  // 中优
        repo.reload()  // 重新加载
        val merged = repo.rules.first()  // 取合并后规则
        // 找到本地的三条
        val localIds = merged.filter { it.id in listOf("a", "b", "c") }  // 过滤出测试规则
        assertEquals("应有 3 条", 3, localIds.size)  // 断言数量
        // 验证按 priority 降序
        assertEquals("第一条应 priority 最大", 100, localIds[0].priority)  // 断言最大
        assertEquals("最后一条应 priority 最小", 10, localIds[2].priority)  // 断言最小
    }
}
