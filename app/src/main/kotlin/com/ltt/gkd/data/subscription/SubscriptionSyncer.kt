package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

import com.ltt.gkd.data.rule.RuleRepository  // 导入规则仓库（编排 reload 用）
import com.ltt.gkd.data.rule.RuleSet  // 导入规则集数据类
import com.ltt.gkd.data.rule.RuleSource  // 导入规则来源枚举
import com.ltt.gkd.util.HttpClientHolder  // 导入共享 OkHttpClient
import com.ltt.gkd.util.Logger  // 导入日志工具
import com.ltt.gkd.util.globalAdapter  // 导入全局 JSON 适配器扩展
import kotlinx.coroutines.Dispatchers  // 导入调度器
import kotlinx.coroutines.withContext  // 导入上下文切换
import okhttp3.Request  // 导入请求构造
import java.io.File  // 导入文件操作

/**
 * 订阅同步器：把一个订阅源的远程规则拉取下来并落地到 subscribed 目录。
 *
 * 统一手动同步与自动更新（WorkManager）的取数逻辑，消除"双轨"：
 * - [SourceType.GIST]：走 [GistClient.fetchRuleSets]（可含多份规则文件），并被动读取该 Gist 的
 *   评论数作为"订阅数/使用量"（只读，无需鉴权）。
 * - [SourceType.URL]：直接 HTTP GET，body 解析为一份 [RuleSet]。
 *
 * 落地约定：文件名统一加 `<sourceId>__` 前缀，删除某源时按前缀清理即可，互不干扰。
 * 本类只负责"取数 + 写文件"，不触发规则仓库 reload（由调用方在共享的 App.repo 上 reload，
 * 以便界面与无障碍服务同时看到更新）。
 *
 * @param gist Gist 客户端（可注入以便测试）
 */
class SubscriptionSyncer(  // 订阅同步器类
    private val gist: GistClient = GistClient()  // Gist 客户端，默认官方
) {

    /**
     * 同步结果。
     *
     * @param success 是否成功
     * @param ruleCount 成功写入的规则条数
     * @param error 失败原因（成功时为空）
     */
    data class SyncResult(val success: Boolean, val ruleCount: Int, val error: String)  // 同步结果数据类

    /**
     * 同步单个订阅源到指定目录。
     *
     * @param source 订阅源
     * @param subscribedDir 订阅规则目录（一般为 `App.get().repo.subscribedDirFile`）
     * @return [SyncResult]；网络/解析异常都会被捕获并转为失败结果，不抛出
     */
    suspend fun sync(source: SubscriptionSource, subscribedDir: File): SyncResult = withContext(Dispatchers.IO) {  // 同步单源，运行在 IO 线程
        runCatching {  // 捕获网络/磁盘/解析异常
            val pairs: List<Pair<String, RuleSet>> = when (source.type) {  // 按类型取数，得到 (文件名 → 规则集)
                SourceType.GIST -> fetchGist(source)  // Gist 路径
                SourceType.URL -> fetchUrl(source)  // 普通 URL 路径
            }
            if (pairs.isEmpty()) return@runCatching SyncResult(false, 0, "订阅源无有效规则")  // 空结果视为失败
            // 先清理该源的旧文件（按前缀），再写入新文件，避免残留
            val prefix = source.id + "__"  // 源文件前缀
            subscribedDir.listFiles { f -> f.name.startsWith(prefix) }?.forEach { it.delete() }  // 删除旧文件
            var count = 0  // 规则计数
            pairs.forEach { (fileName, rs) ->  // 逐份写入
                val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")  // 文件名安全化
                File(subscribedDir, prefix + safeName).writeText(globalAdapter<RuleSet>().toJson(rs))  // 序列化写盘
                count += rs.rules.size  // 累加规则数
            }
            Logger.i("订阅源同步成功：${source.name}（$count 条）")  // 记录成功
            SyncResult(true, count, "")  // 返回成功
        }.getOrElse { e ->  // 异常分支
            Logger.w("订阅源同步失败：${source.name}", e)  // 记录警告
            SyncResult(false, 0, e.message ?: "同步异常")  // 返回失败
        }
    }

    /**
     * 同步单源并持久化结果（编排层，供 UI 与 Worker 共用）。
     *
     * 执行 [sync] → 回写 [SubscriptionStore.setSyncResult] → 成功则 [RuleRepository.reload]。
     *
     * @param source 订阅源
     * @param store 订阅源存储（回写上次同步时间/条数/错误）
     * @param repo 共享规则仓库（成功后 reload，使界面与服务同时生效）
     * @return [SyncResult]
     */
    suspend fun syncAndPersist(  // 同步单源并持久化
        source: SubscriptionSource,  // 订阅源
        store: SubscriptionStore,  // 订阅源存储
        repo: RuleRepository  // 规则仓库
    ): SyncResult {  // 返回同步结果
        val result = sync(source, repo.subscribedDirFile)  // 取数 + 写盘
        store.setSyncResult(source.id, result.ruleCount, result.error, result.success)  // 回写结果
        if (result.success) repo.reload()  // 成功则重新加载合并规则
        return result  // 返回结果
    }

    /**
     * 同步多个源并持久化（编排层）。成功的源统一在最后 reload 一次，避免多次重加载。
     *
     * @param sources 待同步的订阅源列表
     * @param store 订阅源存储
     * @param repo 共享规则仓库
     * @return (成功源数, 总源数)
     */
    suspend fun syncAll(  // 同步多源
        sources: List<SubscriptionSource>,  // 订阅源列表
        store: SubscriptionStore,  // 订阅源存储
        repo: RuleRepository  // 规则仓库
    ): Pair<Int, Int> {  // 返回 (成功数, 总数)
        var ok = 0  // 成功计数
        sources.forEach { source ->  // 遍历每个源
            val r = sync(source, repo.subscribedDirFile)  // 取数 + 写盘
            store.setSyncResult(source.id, r.ruleCount, r.error, r.success)  // 回写结果
            if (r.success) ok++  // 成功累加
        }
        if (ok > 0) repo.reload()  // 有成功则统一 reload 一次
        return ok to sources.size  // 返回成功数与总数
    }

    /** Gist 路径：拉取多份规则文件，并把 Gist 评论数作为订阅数写入每条规则。 */
    private suspend fun fetchGist(source: SubscriptionSource): List<Pair<String, RuleSet>> {  // 拉取 Gist
        val gid = SubscriptionUrls.gistId(source.url) ?: return emptyList()  // 抽取 Gist ID，失败返回空
        val list = gist.fetchRuleSets(gid)  // 拉取所有规则文件
        if (list.isEmpty()) return emptyList()  // 无规则返回空
        val subs = gist.fetchSubscriberCount(gid)  // 被动读取订阅数（评论数近似，只读免鉴权）
        return list.map { (fileName, rs) ->  // 标记来源与订阅数
            fileName to rs.copy(rules = rs.rules.map { r ->  // 逐条改写
                r.copy(source = RuleSource.SUBSCRIBED, subscribers = subs, uploaded = false)  // 订阅来源 + 订阅数 + 清除分享标记
            })
        }
    }

    /** 普通 URL 路径：GET 并把 body 解析为一份 RuleSet。 */
    private suspend fun fetchUrl(source: SubscriptionSource): List<Pair<String, RuleSet>> = withContext(Dispatchers.IO) {  // 拉取普通 URL
        val req = Request.Builder().url(source.url).build()  // 构造 GET 请求
        HttpClientHolder.client.newCall(req).execute().use { resp ->  // 执行并自动关闭响应
            if (!resp.isSuccessful) {  // HTTP 失败
                Logger.w("订阅 URL HTTP ${resp.code}: ${source.url}")  // 记录警告
                return@use emptyList()  // 返回空
            }
            val body = resp.body?.string()  // 读取响应体
            if (body.isNullOrBlank()) return@use emptyList()  // 空体返回空
            val rs = runCatching { globalAdapter<RuleSet>().fromJson(body) }  // 解析为 RuleSet
                .getOrElse { Logger.w("订阅 URL 解析失败: ${source.url}", it); null }  // 失败记日志返回 null
            if (rs == null) emptyList()  // 解析失败返回空
            else listOf("rules.json" to rs.copy(rules = rs.rules.map { r ->  // 成功则标记来源
                r.copy(source = RuleSource.SUBSCRIBED, uploaded = false)  // 订阅来源 + 清除分享标记
            }))
        }
    }
}
