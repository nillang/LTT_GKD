package com.ltt.gkd.data.subscription  // 包声明：与被测类同包，便于访问包内成员

import com.ltt.gkd.data.rule.Rule  // 导入规则数据类
import com.ltt.gkd.data.rule.RuleSet  // 导入规则集数据类
import com.ltt.gkd.data.rule.RuleSource  // 导入规则来源枚举
import com.ltt.gkd.util.globalAdapter  // 导入全局 JSON 适配器
import io.mockk.coEvery  // 导入 mockk 的挂起函数桩：coEvery
import io.mockk.mockk  // 导入 mockk 创建 mock 对象
import kotlinx.coroutines.test.runTest  // 导入协程测试运行器 runTest
import org.junit.After  // 导入 After 注解
import org.junit.Assert.assertEquals  // 导入断言：相等
import org.junit.Assert.assertFalse  // 导入断言：假
import org.junit.Assert.assertTrue  // 导入断言：真
import org.junit.Before  // 导入 Before 注解
import org.junit.Test  // 导入 Test 注解
import java.io.File  // 导入文件类
import java.nio.file.Files  // 导入 NIO Files（创建临时目录）

/**
 * [SubscriptionSyncer] 单元测试：覆盖订阅取数 + 落地的核心编排逻辑。
 *
 * 通过注入 mock 的 [GistClient]，不依赖真实网络即可验证：
 * - Gist 源：规则落地、文件名前缀、来源标记、订阅数透传
 * - 旧文件清理（按 sourceId 前缀）
 * - 空结果 / 异常时返回失败而非抛出
 */
class SubscriptionSyncerTest {  // 订阅同步器测试类

    private lateinit var gist: GistClient  // mock 的 Gist 客户端
    private lateinit var syncer: SubscriptionSyncer  // 被测同步器
    private lateinit var tmpDir: File  // 临时目录（模拟 subscribed 目录）

    private val gid = "a".repeat(32)  // 32 位十六进制 Gist ID 样例

    @Before  // 每个测试前执行
    fun setUp() {  // 初始化 mock 与同步器
        gist = mockk(relaxed = true)  // relaxed mock：未桩方法返回默认值，避免繁琐
        syncer = SubscriptionSyncer(gist)  // 注入 mock GistClient
        tmpDir = Files.createTempDirectory("subtest").toFile()  // 创建临时目录
    }

    @After  // 每个测试后执行
    fun tearDown() {  // 清理临时目录
        tmpDir.deleteRecursively()  // 递归删除
    }

    /** 构造一条最简规则，便于断言字段。 */
    private fun rule(id: String, name: String) = Rule(  // 构造规则
        id = id,  // ID
        name = name,  // 名称
        packageName = "com.example.app"  // 目标包名
    )

    /** 构造一个 GIST 类型的订阅源。 */
    private fun gistSource(id: String = "s1") = SubscriptionSource(  // 构造 Gist 源
        id = id,  // 源 ID（文件前缀用）
        name = "测试源",  // 名称
        url = gid,  // Gist ID 链接
        type = SourceType.GIST  // 类型 Gist
    )

    @Test  // 测试注解
    fun `Gist 源同步成功：落地文件、来源标记与订阅数`() = runTest {  // 测试：Gist 同步成功落地
        // 桩：拉取一份规则文件 + 订阅数 5
        coEvery { gist.fetchRuleSets(gid) } returns listOf(  // 拉取返回一份规则集
            "rules.json" to RuleSet(name = "测试", author = "dev", rules = listOf(  // 规则集
                rule("com.example.app_splash", "示例开屏")  // 一条规则
            ))
        )
        coEvery { gist.fetchSubscriberCount(gid) } returns 5  // 订阅数 5

        val result = syncer.sync(gistSource(), tmpDir)  // 执行同步

        assertTrue(result.success)  // 同步成功
        assertEquals(1, result.ruleCount)  // 1 条规则
        val file = File(tmpDir, "s1__rules.json")  // 期望落地文件名（前缀 s1__）
        assertTrue("落地文件应存在", file.exists())  // 文件应存在
        val rs = globalAdapter<RuleSet>().fromJson(file.readText())!!  // 解析落地 JSON
        assertEquals(RuleSource.SUBSCRIBED, rs.rules[0].source)  // 来源标记为订阅
        assertEquals(5, rs.rules[0].subscribers)  // 订阅数透传
        assertFalse(rs.rules[0].uploaded)  // 清除分享标记
    }

    @Test  // 测试注解
    fun `同步前清理该源旧文件`() = runTest {  // 测试：清理旧文件
        coEvery { gist.fetchRuleSets(gid) } returns listOf(  // 拉取返回新规则
            "rules.json" to RuleSet(name = "测试", rules = listOf(rule("a_splash", "A")))  // 新规则集
        )
        File(tmpDir, "s1__old.json").writeText("{}")  // 预置一个旧文件（同前缀）
        File(tmpDir, "s2__keep.json").writeText("{}")  // 预置另一个源的文件（不同前缀，不应被删）

        syncer.sync(gistSource(), tmpDir)  // 执行同步

        assertFalse("旧文件应被清理", File(tmpDir, "s1__old.json").exists())  // 旧文件已删
        assertTrue("其它源文件应保留", File(tmpDir, "s2__keep.json").exists())  // 其它源保留
        assertTrue("新文件已写入", File(tmpDir, "s1__rules.json").exists())  // 新文件存在
    }

    @Test  // 测试注解
    fun `拉取结果为空返回失败`() = runTest {  // 测试：空结果失败
        coEvery { gist.fetchRuleSets(gid) } returns emptyList()  // 拉取返回空

        val result = syncer.sync(gistSource(), tmpDir)  // 执行同步

        assertFalse(result.success)  // 失败
        assertEquals(0, result.ruleCount)  // 0 条
    }

    @Test  // 测试注解
    fun `Gist 客户端异常返回失败而非抛出`() = runTest {  // 测试：异常兜底
        coEvery { gist.fetchRuleSets(gid) } throws RuntimeException("网络错误")  // 拉取抛异常

        val result = syncer.sync(gistSource(), tmpDir)  // 执行同步（不应抛出）

        assertFalse(result.success)  // 失败
        assertTrue(result.error.isNotEmpty())  // 携带错误信息
    }

    @Test  // 测试注解
    fun `非法 Gist 链接无法抽取 ID 时返回失败`() = runTest {  // 测试：非法链接
        val bad = gistSource().copy(url = "not-a-gist")  // 链接不含 32 位 ID

        val result = syncer.sync(bad, tmpDir)  // 执行同步

        assertFalse(result.success)  // 失败（fetchGist 抽不到 ID 返回空）
    }
}
