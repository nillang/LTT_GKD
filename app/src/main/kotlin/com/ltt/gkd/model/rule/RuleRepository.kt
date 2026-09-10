package com.ltt.gkd.model.rule

import android.content.Context
import com.ltt.gkd.model.util.Logger
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 规则仓库：管理三类规则来源。
 *
 * 规则源：
 * 1. BUILT_IN  → assets/rules/*.json（内置，只读，随 APK 发布）
 * 2. LOCAL     → filesDir/rules/local/*.json（本地，私人，按 createdAt 倒序）
 * 3. SUBSCRIBED → filesDir/rules/subscribed/*.json（订阅，共享，按 subscribers 倒序）
 *
 * 合并顺序（后者覆盖前者）：BUILT_IN → LOCAL → SUBSCRIBED
 * 同 ID 规则：订阅优先于本地，本地优先于内置（让用户能用更新版本覆盖内置默认值）
 */
class RuleRepository(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder().build()
    private val ruleSetAdapter = moshi.adapter<RuleSet>()

    private val localDir = File(context.filesDir, "rules/local").apply { mkdirs() }
    private val subscribedDir = File(context.filesDir, "rules/subscribed").apply { mkdirs() }

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    private val _localRules = MutableStateFlow<List<Rule>>(emptyList())
    val localRules: StateFlow<List<Rule>> = _localRules.asStateFlow()

    private val _subscribedRules = MutableStateFlow<List<Rule>>(emptyList())
    val subscribedRules: StateFlow<List<Rule>> = _subscribedRules.asStateFlow()

    private val _builtInRules = MutableStateFlow<List<Rule>>(emptyList())
    val builtInRules: StateFlow<List<Rule>> = _builtInRules.asStateFlow()

    suspend fun reload() = withContext(Dispatchers.IO) {
        // 1. 内置
        val builtInRs = readBuiltInRules()
        val builtInRules = builtInRs.flatMap { it.rules }.map { it.copy(source = RuleSource.BUILT_IN) }
        _builtInRules.value = builtInRules
        Logger.d("加载内置规则 ${builtInRules.size} 条")

        // 2. 本地（按 createdAt 倒序）
        val localRs = readRulesFromDir(localDir)
        val localRules = localRs.flatMap { it.rules }.map { it.copy(source = RuleSource.LOCAL) }
            .sortedByDescending { it.createdAt }
        _localRules.value = localRules
        Logger.d("加载本地规则 ${localRules.size} 条")

        // 3. 订阅（按 subscribers 倒序）
        val subscribedRs = readRulesFromDir(subscribedDir)
        val subscribedRules = subscribedRs.flatMap { it.rules }.map { it.copy(source = RuleSource.SUBSCRIBED) }
            .sortedByDescending { it.subscribers }
        _subscribedRules.value = subscribedRules
        Logger.d("加载订阅规则 ${subscribedRules.size} 条")

        // 合并（后者覆盖前者）：BUILT_IN → LOCAL → SUBSCRIBED
        val merged = LinkedHashMap<String, Rule>()
        builtInRules.forEach { merged[it.id] = it }
        localRules.forEach { merged[it.id] = it }
        subscribedRules.forEach { merged[it.id] = it }
        // 按 priority 降序输出（让高优先级规则先匹配）
        _rules.value = merged.values.sortedByDescending { it.priority }
        Logger.i("规则仓库就绪：合并后 ${merged.size} 条")
    }

    // ---------- 本地规则写入 ----------

    /** 保存单条本地规则到 filesDir/rules/local/manual_<id>.json。 */
    suspend fun saveLocalRule(rule: Rule): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val safeId = rule.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val fileName = "manual_$safeId.json"
            val rs = RuleSet(
                name = "本地规则",
                version = 1,
                author = rule.author,
                rules = listOf(rule)
            )
            File(localDir, fileName).writeText(ruleSetAdapter.toJson(rs))
            reload()
            Logger.i("本地规则已保存 -> $fileName")
            true
        }.getOrElse {
            Logger.w("保存本地规则失败", it)
            false
        }
    }

    /** 删除本地规则文件（按规则 ID）。 */
    suspend fun deleteLocalRule(ruleId: String): Boolean = withContext(Dispatchers.IO) {
        val safeId = ruleId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val file = File(localDir, "manual_$safeId.json")
        val ok = if (file.exists()) file.delete() else false
        if (ok) reload()
        ok
    }

    /** 列出本地规则文件名。 */
    fun listLocalRuleFiles(): List<String> =
        localDir.listFiles { f -> f.name.endsWith(".json") }
            ?.map { it.name }?.sorted() ?: emptyList()

    /** 列出订阅规则文件名。 */
    fun listSubscribedRuleFiles(): List<String> =
        subscribedDir.listFiles { f -> f.name.endsWith(".json") }
            ?.map { it.name }?.sorted() ?: emptyList()

    /** 列出内置规则文件名。 */
    fun listBuiltInRuleFiles(): List<String> =
        runCatching {
            context.assets.list("rules")?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()
        }.getOrDefault(emptyList())

    /** 读取内置规则原始 JSON（UI 预览用）。 */
    fun readBuiltInRaw(name: String): String? = runCatching {
        context.assets.open("rules/$name").bufferedReader().use { it.readText() }
    }.getOrNull()

    // ---------- 文件读取 ----------

    private fun readBuiltInRules(): List<RuleSet> {
        val names = runCatching { context.assets.list("rules") ?: emptyArray() }.getOrDefault(emptyArray())
        return names.filter { it.endsWith(".json") }.mapNotNull { name ->
            runCatching {
                context.assets.open("rules/$name").use {
                    ruleSetAdapter.fromJson(it.bufferedReader().readText())
                }
            }.getOrElse {
                Logger.w("读取内置规则 $name 失败", it); null
            }
        }
    }

    private fun readRulesFromDir(dir: File): List<RuleSet> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()
        return files.mapNotNull { file ->
            runCatching { ruleSetAdapter.fromJson(file.readText()) }
                .getOrElse {
                    Logger.w("读取规则 ${file.name} 失败", it); null
                }
        }
    }
}
