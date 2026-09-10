package com.ltt.gkd.model.subscription

import com.ltt.gkd.model.rule.RuleSet
import com.ltt.gkd.model.util.Logger
import com.ltt.gkd.model.util.globalMoshi
import com.ltt.gkd.model.util.globalAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

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
 */
class GistClient(
    private val baseUrl: String = DEFAULT_API_BASE
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    /**
     * 上传规则集到指定 Gist。
     *
     * @param token GitHub Personal Access Token（需 gist 权限）
     * @param gistId 目标 Gist ID（首次可空，空时创建新 Gist）
     * @param fileName Gist 内的文件名（如 `dev_abc_com.tencent.mm_splash.json`）
     * @param ruleSet 规则集内容
     * @return (gistId, ok) 元组；gistId 在首次创建时返回新 ID
     */
    suspend fun uploadRuleSet(
        token: String,
        gistId: String,
        fileName: String,
        ruleSet: RuleSet
    ): Pair<String, Boolean> = withContext(Dispatchers.IO) {
        require(token.isNotEmpty()) { "GitHub Token 不能为空" }
        val rsJson = globalAdapter<RuleSet>().toJson(ruleSet)
        val body = buildGistPayload(fileName, rsJson, gistId.isEmpty())
        val url = if (gistId.isEmpty()) baseUrl else "$baseUrl/$gistId"
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/vnd.github+json")
            .patch(body.toRequestBody(jsonMedia))  // PATCH 更新已有；创建时同一 URL POST
            .build()
        // 创建用 POST，更新用 PATCH
        val finalReq = if (gistId.isEmpty()) {
            req.newBuilder().method("POST", body.toRequestBody(jsonMedia)).build()
        } else req

        runCatching {
            client.newCall(finalReq).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.w("Gist 上传失败 HTTP ${resp.code}: ${resp.body?.string()}")
                    return@withContext gistId to false
                }
                val raw = resp.body?.string() ?: return@withContext gistId to false
                val json = JSONObject(raw)
                val newId = json.optString("id", gistId)
                Logger.i("Gist 上传成功: $newId / $fileName")
                newId to true
            }
        }.getOrElse {
            Logger.w("Gist 上传异常", it)
            gistId to false
        }
    }

    /**
     * 拉取指定 Gist 的所有规则文件。
     *
     * @param gistId Gist ID
     * @return 解析后的 (fileName -> RuleSet) 列表；按文件名排序
     */
    suspend fun fetchRuleSets(gistId: String): List<Pair<String, RuleSet>> = withContext(Dispatchers.IO) {
        if (gistId.isEmpty()) return@withContext emptyList()
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/$gistId")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Logger.w("Gist 拉取失败 HTTP ${resp.code}")
                    return@withContext emptyList()
                }
                val raw = resp.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(raw)
                val files = json.optJSONObject("files") ?: return@withContext emptyList()
                val out = ArrayList<Pair<String, RuleSet>>()
                val keys = files.keys()
                while (keys.hasNext()) {
                    val name = keys.next()
                    val fileObj = files.optJSONObject(name) ?: continue
                    val content = fileObj.optString("content", "")
                    if (content.isBlank()) continue
                    runCatching { globalAdapter<RuleSet>().fromJson(content) }
                        .getOrNull()
                        ?.let { out.add(name to it) }
                }
                out.sortedBy { it.first }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 拉取订阅数（Gist 的 comments 数近似）。
     * @return comments 数；失败返回 0
     */
    suspend fun fetchSubscriberCount(gistId: String): Int = withContext(Dispatchers.IO) {
        if (gistId.isEmpty()) return@withContext 0
        runCatching {
            val req = Request.Builder()
                .url("$baseUrl/$gistId")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext 0
                val json = JSONObject(resp.body?.string() ?: "{}")
                json.optInt("comments", 0)
            }
        }.getOrDefault(0)
    }

    /** 评论一次 Gist 表示"订阅"（增加 comments 计数）。 */
    suspend fun subscribe(gistId: String, token: String, comment: String = "subscribed"): Boolean =
        withContext(Dispatchers.IO) {
            if (gistId.isEmpty() || token.isEmpty()) return@withContext false
            runCatching {
                val body = JSONObject().put("body", comment).toString()
                val req = Request.Builder()
                    .url("$baseUrl/$gistId/comments")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .post(body.toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        Logger.i("已订阅 Gist $gistId")
                        true
                    } else {
                        Logger.w("订阅失败 HTTP ${resp.code}")
                        false
                    }
                }
            }.getOrDefault(false)
        }

    private fun buildGistPayload(fileName: String, content: String, isCreate: Boolean): String {
        val files = JSONObject().apply {
            put(fileName, JSONObject().put("content", content))
        }
        return JSONObject().apply {
            if (isCreate) {
                put("description", "LTT_GKD shared rules")
                put("public", false) // 私有 Gist，避免被随机爬
            }
            put("files", files)
        }.toString()
    }

    companion object {
        const val DEFAULT_API_BASE = "https://api.github.com/gists"
    }
}
