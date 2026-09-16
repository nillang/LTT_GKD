package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

import com.ltt.gkd.data.rule.RuleSet  // 导入 RuleSet 数据类，规则集类型
import com.ltt.gkd.util.Logger  // 导入日志工具类
import com.ltt.gkd.util.globalMoshi  // 导入全局 Moshi 实例
import com.ltt.gkd.util.globalAdapter  // 导入全局适配器获取扩展函数
import kotlinx.coroutines.Dispatchers  // 导入协程调度器，用于切换 IO 线程
import kotlinx.coroutines.withContext  // 导入 withContext，切换协程上下文
import okhttp3.MediaType.Companion.toMediaType  // 导入 OkHttp MediaType 扩展函数
import okhttp3.Request  // 导入 OkHttp Request 类
import okhttp3.RequestBody.Companion.toRequestBody  // 导入 RequestBody 扩展函数
import org.json.JSONObject  // 导入 JSON 对象类，用于构造请求体

/**
 * GitHub Gist 客户端：用于规则的上传/拉取/订阅数查询。
 *
 * 设计：
 * - 一个 Gist = 多个 .json 文件，每个文件 = 一组 RuleSet
 * - 文件名建议：`<author>_<rule_id>.json`，便于追溯作者
 * - "订阅数"用 Gist 的 comments 数近似（用户评论表示已订阅）
 * - 上传需 Personal Access Token（仅 gist 权限）；拉取可匿名
 *
 * 安全：Token 仅存本地 DataStore，不进入日志；本类构造函数不接收 token，
 * 调用方注入避免泄露。
 *
 * @param baseUrl GitHub Gist API 基址，默认 [DEFAULT_API_BASE]，测试可替换为 mock 服务器
 */
class GistClient(  // Gist 客户端类
    private val baseUrl: String = DEFAULT_API_BASE  // Gist API 基址，默认为 GitHub 官方
) {

    private val client get() = com.ltt.gkd.util.HttpClientHolder.client  // 取全局共享 OkHttpClient

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()  // JSON 媒体类型

    /**
     * 上传规则集到指定 Gist。
     *
     * @param token GitHub Personal Access Token（需 gist 权限）
     * @param gistId 目标 Gist ID（首次可空，空时创建新 Gist）
     * @param fileName Gist 内的文件名（如 `dev_abc_com.tencent.mm_splash.json`）
     * @param ruleSet 规则集内容
     * @return (gistId, ok) 元组；gistId 在首次创建时返回新 ID
     */
    suspend fun uploadRuleSet(  // 上传规则集方法
        token: String,  // GitHub Personal Access Token
        gistId: String,  // 目标 Gist ID，空字符串表示首次创建
        fileName: String,  // Gist 内文件名
        ruleSet: RuleSet  // 规则集内容
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {  // 返回 (gistId, 是否成功)，运行在 IO 线程
        require(token.isNotEmpty()) { "GitHub Token 不能为空" }  // 校验 token 非空
        val rsJson = globalAdapter<RuleSet>().toJson(ruleSet)  // 序列化规则集为 JSON 字符串
        val body = buildGistPayload(fileName, rsJson, gistId.isEmpty())  // 构造 Gist 请求体 JSON
        val url = if (gistId.isEmpty()) baseUrl else "$baseUrl/$gistId"  // 首次创建用 baseUrl，更新用 baseUrl/gistId
        val req = Request.Builder()  // 构造请求构建器
            .url(url)  // 设置 URL
            .header("Authorization", "Bearer $token")  // 设置 Bearer Token 鉴权头
            .header("Accept", "application/vnd.github+json")  // 设置 Accept 头
            .patch(body.toRequestBody(jsonMedia))  // 默认 PATCH 更新已有；创建时下面会改为 POST
            .build()  // 构建请求
        // 创建用 POST，更新用 PATCH
        val finalReq = if (gistId.isEmpty()) {  // 首次创建分支
            req.newBuilder().method("POST", body.toRequestBody(jsonMedia)).build()  // 改用 POST 创建新 Gist
        } else req  // 已存在则保持 PATCH

        runCatching {  // 捕获网络/解析异常
            client.newCall(finalReq).execute().use { resp ->  // 执行请求并自动关闭响应
                if (!resp.isSuccessful) {  // HTTP 非 2xx 失败
                    Logger.w("Gist 上传失败 HTTP ${resp.code}: ${resp.body?.string()}")  // 输出警告日志
                    return@runCatching gistId to false  // 返回原 gistId 与失败
                }
                val raw = resp.body?.string() ?: return@runCatching gistId to false  // 读取响应体，空则返回失败
                val json = JSONObject(raw)  // 解析响应 JSON
                val newId = json.optString("id", gistId)  // 取 Gist ID，缺失则用原值
                Logger.i("Gist 上传成功: $newId / $fileName")  // 输出信息日志
                newId to true  // 返回新 ID 与成功
            }
        }.getOrElse { e ->  // 异常分支
            Logger.w("Gist 上传异常", e)  // 输出警告日志
            gistId to false  // 返回原 gistId 与失败
        }
    }

    /**
     * 拉取指定 Gist 的所有规则文件。
     *
     * @param gistId Gist ID
     * @return 解析后的 (fileName -> RuleSet) 列表；按文件名排序
     */
    suspend fun fetchRuleSets(gistId: String): List<Pair<String, RuleSet>> = withContext(Dispatchers.IO) {  // 拉取 Gist 全部规则文件，运行在 IO 线程
        if (gistId.isEmpty()) return@withContext emptyList()  // gistId 为空直接返回空列表
        runCatching {  // 捕获网络/解析异常
            val req = Request.Builder()  // 构造 GET 请求
                .url("$baseUrl/$gistId")  // 设置 URL
                .header("Accept", "application/vnd.github+json")  // 设置 Accept 头
                .get()  // 使用 GET 方法
                .build()  // 构建请求
            client.newCall(req).execute().use { resp ->  // 执行请求并自动关闭响应
                if (!resp.isSuccessful) {  // HTTP 失败
                    Logger.w("Gist 拉取失败 HTTP ${resp.code}")  // 输出警告日志
                    return@runCatching emptyList()  // 返回空列表
                }
                val raw = resp.body?.string() ?: return@runCatching emptyList()  // 读取响应体，空则返回空
                val json = JSONObject(raw)  // 解析响应 JSON
                val files = json.optJSONObject("files") ?: return@runCatching emptyList()  // 取 files 对象，缺失返回空
                val out = ArrayList<Pair<String, RuleSet>>()  // 输出列表
                val keys = files.keys()  // 取 files 的所有 key 迭代器
                while (keys.hasNext()) {  // 遍历每个文件名
                    val name = keys.next()  // 文件名
                    val fileObj = files.optJSONObject(name) ?: continue  // 取该文件的 JSON 对象，缺失则跳过
                    val content = fileObj.optString("content", "")  // 取文件内容字符串
                    if (content.isBlank()) continue  // 内容为空跳过
                    runCatching { globalAdapter<RuleSet>().fromJson(content) }  // 解析为 RuleSet
                        .onFailure { Logger.w("Gist 文件 $name JSON 解析失败", it) }  // 解析失败输出警告
                        .getOrNull()  // 取结果或 null
                        ?.let { out.add(name to it) }  // 成功则加入输出列表
                }
                out.sortedBy { it.first }  // 按文件名排序后返回
            }
        }.getOrElse { e ->  // 异常分支
            Logger.w("Gist 拉取规则集失败", e)  // 输出警告日志
            emptyList()  // 返回空列表
        }
    }

    /**
     * 检查指定 Gist 中是否已存在同 ID 的规则（上传去重）。
     *
     * 规则：遍历 Gist 内所有文件，若文件名中包含该 ruleId 则视为已存在；
     * 已存在时进一步解析文件内容取出 author 字段返回，便于调用方判断是否同作者。
     *
     * @param gistId Gist ID，空字符串时直接返回 (false, null)
     * @param ruleId 规则 ID
     * @return (是否已存在, 已存在规则的作者名或 null)
     */
    suspend fun checkRuleExists(  // 检查同 ID 规则是否已存在方法
        gistId: String,  // Gist ID
        ruleId: String  // 规则 ID
    ): Pair<Boolean, String?> = withContext(Dispatchers.IO) {  // 返回 (是否已存在, 作者名或 null)，运行在 IO 线程
        if (gistId.isEmpty() || ruleId.isEmpty()) return@withContext false to null  // 参数缺失直接返回不存在
        runCatching {  // 捕获网络/解析异常
            val req = Request.Builder()  // 构造 GET 请求
                .url("$baseUrl/$gistId")  // 设置 URL
                .header("Accept", "application/vnd.github+json")  // 设置 Accept 头
                .get()  // 使用 GET 方法
                .build()  // 构建请求
            client.newCall(req).execute().use { resp ->  // 执行请求并自动关闭响应
                if (!resp.isSuccessful) return@runCatching false to null  // HTTP 失败返回不存在
                val raw = resp.body?.string() ?: return@runCatching false to null  // 读取响应体，空则返回不存在
                val json = JSONObject(raw)  // 解析响应 JSON
                val files = json.optJSONObject("files") ?: return@runCatching false to null  // 取 files 对象，缺失返回不存在
                val keys = files.keys()  // 取 files 的所有 key 迭代器
                while (keys.hasNext()) {  // 遍历每个文件名
                    val name = keys.next()  // 文件名
                    // 检查文件名是否包含该 ruleId
                    if (!name.contains(ruleId)) continue  // 不包含则跳过
                    val fileObj = files.optJSONObject(name) ?: continue  // 取该文件的 JSON 对象，缺失则跳过
                    val content = fileObj.optString("content", "")  // 取文件内容字符串
                    if (content.isBlank()) continue  // 内容为空跳过
                    // 解析内容取出 author 字段，解析失败时作者返回 null
                    val author = runCatching { globalAdapter<RuleSet>().fromJson(content) }  // 解析为 RuleSet
                        .onFailure { Logger.w("Gist 去重检查解析 $name 失败", it) }  // 解析失败输出警告
                        .getOrNull()  // 取结果或 null
                        ?.author  // 取 author 字段
                    return@runCatching true to author  // 已存在，返回作者名（可能为空串或 null）
                }
                false to null  // 遍历结束未命中，返回不存在
            }
        }.getOrElse { e ->  // 异常分支
            Logger.w("Gist 规则去重检查失败", e)  // 输出警告日志
            false to null  // 异常时返回不存在，允许后续上传流程继续
        }
    }

    /**
     * 拉取订阅数（Gist 的 comments 数近似）。
     * @return comments 数；失败返回 0
     */
    suspend fun fetchSubscriberCount(gistId: String): Int = withContext(Dispatchers.IO) {  // 拉取订阅数方法，运行在 IO 线程
        if (gistId.isEmpty()) return@withContext 0  // gistId 为空返回 0
        runCatching {  // 捕获异常
            val req = Request.Builder()  // 构造 GET 请求
                .url("$baseUrl/$gistId")  // 设置 URL
                .header("Accept", "application/vnd.github+json")  // 设置 Accept 头
                .get()  // 使用 GET 方法
                .build()  // 构建请求
            client.newCall(req).execute().use { resp ->  // 执行请求并自动关闭响应
                if (!resp.isSuccessful) return@runCatching 0  // HTTP 失败返回 0
                val json = JSONObject(resp.body?.string() ?: "{}")  // 读取响应并解析 JSON，空时用 "{}"
                json.optInt("comments", 0)  // 取 comments 字段，缺失返回 0
            }
        }.getOrElse { e ->  // 异常分支
            Logger.w("Gist 订阅数拉取失败", e)  // 输出警告日志
            0  // 返回 0
        }
    }

    /**
     * 评论一次 Gist 表示"订阅"（增加 comments 计数）。
     *
     * @param gistId 目标 Gist ID
     * @param token GitHub Personal Access Token
     * @param comment 评论内容，默认 "subscribed"
     * @return HTTP 成功返回 true；失败或异常返回 false
     */
    suspend fun subscribe(gistId: String, token: String, comment: String = "subscribed"): Boolean =  // 订阅方法（通过评论表示）
        withContext(Dispatchers.IO) {  // 运行在 IO 线程
            if (gistId.isEmpty() || token.isEmpty()) return@withContext false  // 参数缺失返回 false
            runCatching {  // 捕获异常
                val body = JSONObject().put("body", comment).toString()  // 构造评论请求体 JSON
                val req = Request.Builder()  // 构造请求构建器
                    .url("$baseUrl/$gistId/comments")  // 设置评论 URL
                    .header("Authorization", "Bearer $token")  // 设置 Bearer Token
                    .header("Accept", "application/vnd.github+json")  // 设置 Accept 头
                    .post(body.toRequestBody(jsonMedia))  // POST 提交评论
                    .build()  // 构建请求
                client.newCall(req).execute().use { resp ->  // 执行请求并自动关闭响应
                    if (resp.isSuccessful) {  // HTTP 成功
                        Logger.i("已订阅 Gist $gistId")  // 输出信息日志
                        true  // 返回成功
                    } else {  // HTTP 失败
                        Logger.w("订阅失败 HTTP ${resp.code}")  // 输出警告日志
                        false  // 返回失败
                    }
                }
            }.getOrElse { e ->  // 异常分支
                Logger.w("Gist 订阅请求异常", e)  // 输出警告日志
                false  // 返回失败
            }
        }

    /**
     * 构造 Gist API 请求体 JSON。
     *
     * @param fileName Gist 内的文件名
     * @param content 文件内容（RuleSet 序列化后的 JSON 字符串）
     * @param isCreate 是否为创建新 Gist（true 时附加 description 与 public=false）
     * @return 序列化后的请求体 JSON 字符串
     */
    private fun buildGistPayload(fileName: String, content: String, isCreate: Boolean): String {  // 构造 Gist 请求体方法
        val files = JSONObject().apply {  // files JSON 对象
            put(fileName, JSONObject().put("content", content))  // 在 files 下放入文件名→内容映射
        }
        return JSONObject().apply {  // 顶层请求体 JSON
            if (isCreate) {  // 创建新 Gist 分支
                put("description", "LTT_GKD shared rules")  // 描述
                put("public", true) // 公开 Gist：订阅端无 Token 匿名拉取（D1 零配置），私有 Gist 匿名会 404 导致共享链路断裂
            }
            put("files", files)  // 放入 files 字段
        }.toString()  // 序列化为字符串
    }

    companion object {  // 静态常量
        /** GitHub Gist REST API 默认基址。 */
        const val DEFAULT_API_BASE = "https://api.github.com/gists"  // GitHub Gist API 默认基址常量
    }
}
