@file:OptIn(kotlin.ExperimentalStdlibApi::class)  // 文件级 OptIn：允许使用实验性标准库 API

package com.ltt.gkd.data.rule  // 声明包名，规则仓库所在的包

import android.content.Context  // 导入 Context 类，用于访问应用上下文与资源
import com.ltt.gkd.util.Logger  // 导入日志工具类
import com.squareup.moshi.Moshi  // 导入 Moshi JSON 库的主类
import com.squareup.moshi.adapter  // 导入 Moshi 的扩展函数 adapter，用于生成/获取适配器
import kotlinx.coroutines.Dispatchers  // 导入协程调度器，用于切换 IO 线程
import kotlinx.coroutines.flow.MutableStateFlow  // 导入可变 StateFlow，用于内部状态更新
import kotlinx.coroutines.flow.StateFlow  // 导入只读 StateFlow，对外暴露
import kotlinx.coroutines.flow.asStateFlow  // 导入 asStateFlow 扩展，将 Mutable 转 readonly
import kotlinx.coroutines.withContext  // 导入 withContext，切换协程上下文
import java.io.File  // 导入 File 类，用于文件操作

/**
 * 规则仓库：管理三类规则来源。
 *
 * 规则源：
 * 1. BUILT_IN  → assets/rules/ 下的 .json（内置，只读，随 APK 发布）
 * 2. LOCAL     → filesDir/rules/local/ 下的 .json（本地，私人，按 createdAt 倒序）
 * 3. SUBSCRIBED → filesDir/rules/subscribed/ 下的 .json（订阅，共享，按 subscribers 倒序）
 *
 * 合并顺序（后者覆盖前者）：BUILT_IN → LOCAL → SUBSCRIBED
 * 同 ID 规则：订阅优先于本地，本地优先于内置（让用户能用更新版本覆盖内置默认值）
 */
open class RuleRepository(private val context: Context) {  // 规则仓库类，可被继承便于扩展

    companion object {  // 静态常量与工厂方法
        /** 本地规则子目录（相对于 filesDir）。 */
        const val LOCAL_DIR_PATH = "rules/local"  // 本地规则子目录路径常量
        /** 订阅规则子目录（相对于 filesDir）。 */
        const val SUBSCRIBED_DIR_PATH = "rules/subscribed"  // 订阅规则子目录路径常量
        /** 本地规则文件名前缀。 */
        const val LOCAL_FILE_PREFIX = "manual_"  // 本地规则文件名前缀常量
    }

    private val moshi: Moshi = Moshi.Builder().build()  // 构建 Moshi 实例
    private val ruleSetAdapter = moshi.adapter<RuleSet>()  // 获取 RuleSet 的 JSON 适配器

    private val localDir = File(context.filesDir, LOCAL_DIR_PATH).apply { mkdirs() }  // 本地规则目录，不存在则创建
    private val subscribedDir = File(context.filesDir, SUBSCRIBED_DIR_PATH).apply { mkdirs() }  // 订阅规则目录，不存在则创建

    /** 订阅目录（用于 RuleListActivity、RuleSubscriptionService 等外部写文件）。 */
    val subscribedDirFile: File get() = subscribedDir  // 暴露订阅目录的只读属性

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())  // 合并后规则的内部可变 StateFlow
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()  // 对外暴露合并后的规则只读 StateFlow

    private val _localRules = MutableStateFlow<List<Rule>>(emptyList())  // 本地规则的内部可变 StateFlow
    val localRules: StateFlow<List<Rule>> = _localRules.asStateFlow()  // 对外暴露本地规则的只读 StateFlow

    private val _subscribedRules = MutableStateFlow<List<Rule>>(emptyList())  // 订阅规则（已安装应用 + 通用兜底）的内部可变 StateFlow
    val subscribedRules: StateFlow<List<Rule>> = _subscribedRules.asStateFlow()  // 对外暴露订阅规则的只读 StateFlow

    private val _subscribedInactive = MutableStateFlow<List<Rule>>(emptyList())  // 未安装应用订阅规则的内部可变 StateFlow（仅展示，不参与匹配）
    val subscribedInactive: StateFlow<List<Rule>> = _subscribedInactive.asStateFlow()  // 对外暴露未安装订阅规则的只读 StateFlow

    private val _builtInRules = MutableStateFlow<List<Rule>>(emptyList())  // 内置规则的内部可变 StateFlow
    val builtInRules: StateFlow<List<Rule>> = _builtInRules.asStateFlow()  // 对外暴露内置规则的只读 StateFlow

    private val _builtInGroups = MutableStateFlow<List<RuleGroup>>(emptyList())  // 内置合集分组的内部可变 StateFlow
    val builtInGroups: StateFlow<List<RuleGroup>> = _builtInGroups.asStateFlow()  // 对外暴露内置合集分组的只读 StateFlow（UI 按合集展示）

    /**
     * 重新加载所有来源的规则并合并。
     *
     * 调用时机：应用启动、本地/订阅规则变更后、规则刷新按钮。
     * 执行流程：
     * 1. 读取 assets/rules/ 下内置规则
     * 2. 读取本地目录（按 createdAt 倒序）
     * 3. 读取订阅目录（按 subscribers 倒序）
     * 4. 同 ID 规则按 BUILT_IN → LOCAL → SUBSCRIBED 顺序覆盖
     * 5. 合并后按 priority 倒序输出到 [rules]
     */
    suspend fun reload() = withContext(Dispatchers.IO) {  // 重新加载并合并规则，运行在 IO 线程
        // 1. 内置（仅保留已安装应用的规则 + 通用兜底规则）
        val installedPkgs = runCatching {  // 获取已安装应用包名集合，失败则返回空集合（不过滤）
            context.packageManager.getInstalledPackages(0).map { it.packageName }.toSet()  // 取已安装包名集合
        }.getOrDefault(emptySet())  // 异常时返回空集合
        val builtInRs = readBuiltInRules()  // 读取所有内置规则集（文件名 → RuleSet）
        // 按合集（RuleSet）分组：每组仅保留通用兜底或已安装应用的规则，并标记为内置来源
        val builtInGroups = builtInRs.map { (fileName, rs) ->  // 遍历每个内置规则集
            RuleGroup(  // 构造合集分组
                name = rs.name,  // 合集名取自规则集名
                fileName = fileName,  // 来源文件名（预览用）
                rules = rs.rules  // 该合集的规则
                    .filter { it.packageName.isEmpty() || it.packageName in installedPkgs }  // 仅保留通用兜底或已安装应用
                    .map { it.copy(source = RuleSource.BUILT_IN) }  // 标记为内置来源
            )
        }.filter { it.rules.isNotEmpty() }  // 过滤掉没有可见规则的合集
        _builtInGroups.value = builtInGroups  // 更新内置合集分组 StateFlow
        val builtInRules = builtInGroups.flatMap { it.rules }  // 展平各合集规则，得到内置规则总表
        _builtInRules.value = builtInRules  // 更新内置规则 StateFlow
        Logger.d("加载内置规则 ${builtInRules.size} 条 / ${builtInGroups.size} 个合集（过滤未安装应用后）")  // 输出调试日志

        // 2. 本地（按 createdAt 倒序）
        val localRs = readRulesFromDir(localDir)  // 读取本地目录下所有规则集
        val localRules = localRs.flatMap { it.rules }.map { it.copy(source = RuleSource.LOCAL) }  // 展平并标记为本地来源
            .sortedByDescending { it.createdAt }  // 按创建时间倒序
        _localRules.value = localRules  // 更新本地规则 StateFlow
        Logger.d("加载本地规则 ${localRules.size} 条")  // 输出调试日志

        // 3. 订阅（按 subscribers 倒序）
        val subscribedRs = readRulesFromDir(subscribedDir)  // 读取订阅目录下所有规则集
        val allSubscribed = subscribedRs.flatMap { it.rules }.map { it.copy(source = RuleSource.SUBSCRIBED) }  // 展平并标记为订阅来源
            .sortedByDescending { it.subscribers }  // 按订阅数倒序
        // 按已安装应用过滤：已安装 + 通用兜底进入生效列表，未安装的归入待激活列表（新装应用后下次 reload 自动激活）
        val subscribedRules = allSubscribed.filter { it.packageName.isEmpty() || it.packageName in installedPkgs }  // 已安装应用或通用兜底
        _subscribedRules.value = subscribedRules  // 更新订阅规则 StateFlow
        _subscribedInactive.value = allSubscribed.filter { it.packageName.isNotEmpty() && it.packageName !in installedPkgs }  // 未安装应用
        Logger.d("加载订阅规则 ${subscribedRules.size} 条 / 未安装 ${_subscribedInactive.value.size} 条")  // 输出调试日志

        // 合并（后者覆盖前者）：BUILT_IN → LOCAL → SUBSCRIBED
        val merged = LinkedHashMap<String, Rule>()  // 合并用的有序 Map，按 ID 去重
        builtInRules.forEach { merged[it.id] = it }  // 先放入内置规则
        localRules.forEach { merged[it.id] = it }  // 本地规则覆盖同 ID 内置规则
        subscribedRules.forEach { merged[it.id] = it }  // 订阅规则覆盖同 ID 本地规则
        // 按 priority 降序输出（让高优先级规则先匹配）
        _rules.value = merged.values.sortedByDescending { it.priority }  // 合并后按优先级降序写入 rules StateFlow
        Logger.i("规则仓库就绪：合并后 ${merged.size} 条")  // 输出信息日志
    }

    // ---------- 本地规则写入 ----------

    /**
     * 保存单条本地规则到 filesDir/rules/local/manual_<id>.json。
     *
     * @param rule 待保存的规则（[Rule.id] 用于生成文件名，非法字符会被替换为 `_`）
     * @return 是否保存成功；失败会记录到日志
     */
    suspend fun saveLocalRule(rule: Rule): Boolean = withContext(Dispatchers.IO) {  // 保存本地规则方法，运行在 IO 线程
        runCatching {  // 捕获异常以保证失败不抛出
            val safeId = rule.id.replace(Regex("[^A-Za-z0-9._-]"), "_")  // 替换 ID 中非法字符为下划线
            val fileName = "${LOCAL_FILE_PREFIX}$safeId.json"  // 拼接本地规则文件名
            val rs = RuleSet(  // 构造 RuleSet 包装
                name = "本地规则",  // 规则集名称
                version = 1,  // 版本号
                author = rule.author,  // 作者
                rules = listOf(rule)  // 仅含该单条规则
            )
            File(localDir, fileName).writeText(ruleSetAdapter.toJson(rs))  // 写入 JSON 文件
            reload()  // 触发重新加载合并
            Logger.i("本地规则已保存 -> $fileName")  // 输出信息日志
            true  // 返回保存成功
        }.getOrElse {  // 异常时返回 false
            Logger.w("保存本地规则失败", it)  // 输出警告日志
            false  // 返回失败
        }
    }

    /**
     * 删除本地规则文件（按规则 ID）。
     *
     * @param ruleId 待删除规则的 ID
     * @return 文件存在且删除成功返回 true；否则 false
     */
    suspend fun deleteLocalRule(ruleId: String): Boolean = withContext(Dispatchers.IO) {  // 删除本地规则方法，运行在 IO 线程
        val safeId = ruleId.replace(Regex("[^A-Za-z0-9._-]"), "_")  // 替换 ID 中非法字符
        val file = File(localDir, "${LOCAL_FILE_PREFIX}$safeId.json")  // 定位规则文件
        val ok = if (file.exists()) file.delete() else false  // 文件存在则删除，否则视为失败
        if (ok) reload()  // 删除成功则触发重新加载
        ok  // 返回是否删除成功
    }

    /** 列出本地规则文件名。 */
    fun listLocalRuleFiles(): List<String> =  // 列出本地规则文件名方法
        localDir.listFiles { f -> f.name.endsWith(".json") }  // 列出本地目录下所有 .json 文件
            ?.map { it.name }?.sorted() ?: emptyList()  // 取文件名并按字母序排序，空目录返回空列表

    /** 列出订阅规则文件名。 */
    fun listSubscribedRuleFiles(): List<String> =  // 列出订阅规则文件名方法
        subscribedDir.listFiles { f -> f.name.endsWith(".json") }  // 列出订阅目录下所有 .json 文件
            ?.map { it.name }?.sorted() ?: emptyList()  // 取文件名并按字母序排序，空目录返回空列表

    /** 列出内置规则文件名。 */
    fun listBuiltInRuleFiles(): List<String> =  // 列出内置规则文件名方法
        runCatching {  // 捕获异常以兼容 assets 读取失败
            context.assets.list("rules")?.filter { it.endsWith(".json") }?.sorted() ?: emptyList()  // 列出 assets/rules 下 .json 文件并排序
        }.getOrDefault(emptyList())  // 异常时返回空列表

    /**
     * 读取内置规则原始 JSON（UI 预览用）。
     *
     * @param name assets/rules/ 下的文件名（含 .json 后缀）
     * @return 文件内容；文件不存在或读取失败返回 null
     */
    fun readBuiltInRaw(name: String): String? = runCatching {  // 读取内置规则原始 JSON 方法
        context.assets.open("rules/$name").bufferedReader().use { it.readText() }  // 打开 assets 文件并读取全文
    }.getOrNull()  // 失败返回 null

    /**
     * 同步从本地/订阅文件中按 ID 查找单条规则（用于编辑器初始化，避免 reload 全量）。
     * 不触发 StateFlow 更新。
     */
    fun findRuleByIdSync(ruleId: String): Rule? {  // 同步按 ID 查找规则方法
        if (ruleId.isEmpty()) return null  // ID 为空直接返回 null
        val safeId = ruleId.replace(Regex("[^A-Za-z0-9._-]"), "_")  // 替换 ID 非法字符
        // 优先本地
        val localFile = File(localDir, "${LOCAL_FILE_PREFIX}$safeId.json")  // 定位本地规则文件
        if (localFile.exists()) {  // 本地文件存在
            runCatching { ruleSetAdapter.fromJson(localFile.readText()) }  // 解析 JSON
                .getOrNull()?.rules?.firstOrNull { it.id == ruleId }?.let { return it }  // 找到匹配 ID 的规则则返回
        }
        // 退回到已加载 StateFlow
        return rules.value.firstOrNull { it.id == ruleId }  // 从合并后的 StateFlow 中查找
    }

    // ---------- 文件读取 ----------

    /**
     * 读取 assets/rules/ 目录下的所有内置规则文件。
     *
     * 单个文件解析失败不影响其它文件，错误会被记录到日志并跳过。
     *
     * @return 解析成功的 (文件名 → RuleSet) 列表，按文件名排序（顺序稳定）
     */
    private fun readBuiltInRules(): List<Pair<String, RuleSet>> {  // 读取所有内置规则集方法（保留文件名）
        val names = runCatching { context.assets.list("rules") ?: emptyArray() }.getOrDefault(emptyArray())  // 列出 assets/rules 目录下所有文件名
        return names.filter { it.endsWith(".json") }.sorted().mapNotNull { name ->  // 只处理 .json 文件，按文件名排序，跳过解析失败的
            val rs = runCatching {  // 单文件解析捕获异常
                context.assets.open("rules/$name").use {  // 打开 assets 输入流
                    ruleSetAdapter.fromJson(it.bufferedReader().readText())  // 读取并解析为 RuleSet
                }
            }.getOrElse {  // 解析失败处理
                Logger.w("读取内置规则 $name 失败", it); null  // 输出警告日志并返回 null 跳过
            }
            rs?.let { name to it }  // 成功则返回 文件名→RuleSet 对
        }
    }

    /**
     * 读取指定目录下的所有规则文件。
     *
     * @param dir 规则目录（local 或 subscribed）
     * @return 解析成功的 RuleSet 列表；目录不存在或无文件返回空列表
     */
    private fun readRulesFromDir(dir: File): List<RuleSet> {  // 从目录读取规则集方法
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return emptyList()  // 列出目录下所有 .json 文件
        return files.mapNotNull { file ->  // 逐文件解析，跳过失败项
            runCatching { ruleSetAdapter.fromJson(file.readText()) }  // 读取并解析文件
                .getOrElse {  // 解析失败处理
                    Logger.w("读取规则 ${file.name} 失败", it); null  // 输出警告日志并返回 null 跳过
                }
        }
    }
}
