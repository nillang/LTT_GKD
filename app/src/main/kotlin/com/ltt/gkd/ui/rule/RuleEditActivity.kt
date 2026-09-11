package com.ltt.gkd.ui.rule // 声明包名

import android.content.Context // 导入 Context 基类
import android.content.Intent // 导入 Intent，用于跳转
import android.os.Bundle // 导入 Bundle，用于状态保存
import android.widget.Toast // 导入 Toast 提示
import androidx.activity.ComponentActivity // 导入 ComponentActivity
import androidx.activity.compose.setContent // 导入 setContent 挂载 Compose
import com.ltt.gkd.App // 导入应用入口类
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleRepository // 导入规则仓库
import com.ltt.gkd.data.rule.RuleTemplate // 导入规则模板枚举
import com.ltt.gkd.data.rule.toRule // 导入模板转规则扩展
import com.ltt.gkd.data.subscription.GistClient // 导入 Gist 客户端
import com.ltt.gkd.util.DeviceIdProvider // 导入设备 ID 提供者
import com.ltt.gkd.util.Logger // 导入日志工具
import com.ltt.gkd.util.launchSafe // 导入安全启动协程辅助
import com.ltt.gkd.ui.rule.RuleEditScreen // 导入规则编辑 Composable
import com.ltt.gkd.ui.theme.LTTGKDTheme // 导入应用主题
import kotlinx.coroutines.Dispatchers // 导入调度器
import kotlinx.coroutines.flow.first // 导入 Flow.first
import kotlinx.coroutines.launch // 导入协程启动
import kotlinx.coroutines.withContext // 导入切换上下文

/**
 * 规则编辑控制器。
 *
 * 用法：
 * - 新建：`RuleEditActivity.start(ctx)` 或 `RuleEditActivity.start(ctx, packageName=..., appLabel=...)`
 * - 编辑：`RuleEditActivity.start(ctx, ruleId=...)`
 * - 从模板新建：`RuleEditActivity.start(ctx, template=RuleTemplate.SPLASH, packageName=...)`
 *
 * 保存：本地存到 filesDir/rules/local/manual_<id>.json
 * 上传：通过 GistClient 上传到 GitHub Gist（需 Token）
 */
class RuleEditActivity : ComponentActivity() { // 规则编辑 Activity

    private val repo: RuleRepository by lazy { RuleRepository(this) } // 规则仓库，懒加载
    private val gist: GistClient by lazy { GistClient() } // Gist 客户端，懒加载

    /**
     * 解析 Intent 携带的初始化参数，决定进入"编辑模式"、"模板新建模式"还是"空白新建模式"，
     * 然后挂载 [RuleEditScreen]。
     */
    override fun onCreate(savedInstanceState: Bundle?) { // Activity 创建回调
        super.onCreate(savedInstanceState) // 调用父类

        // 读取 Intent 中可能携带的初始化字段
        val initialId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty() // 取规则 ID
        val initialPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty() // 取包名
        val initialName = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty() // 取应用名
        val templateStr = intent.getStringExtra(EXTRA_TEMPLATE) // 取模板字符串
        val template: RuleTemplate? = templateStr?.let { // 尝试转枚举
            runCatching { RuleTemplate.valueOf(it) }.getOrNull() // 失败返回 null
        }

        // 编辑模式：从本地文件同步按 ID 读取，避免主线程 reload 全量
        // 模板模式：调用 RuleTemplate.toRule 生成初始规则
        // 默认：构造一条空规则，包名带 _splash 后缀作为默认 id
        val initial: Rule = if (initialId.isNotEmpty()) { // 有 ID 表示编辑
            repo.findRuleByIdSync(initialId) ?: Rule(id = initialId, name = "") // 取不到则用空规则兜底
        } else if (template != null) { // 有模板
            template.toRule(packageName = initialPkg, appLabel = initialName) // 模板生成规则
        } else { // 默认空规则
            Rule(
                id = if (initialPkg.isNotEmpty()) "${initialPkg}_splash" else "", // 有包名拼默认 ID
                name = initialName, // 应用名
                packageName = initialPkg // 包名
            )
        }

        setContent { // 挂载 Compose
            LTTGKDTheme { // 应用主题
                RuleEditScreen( // 规则编辑组件
                    initial = initial, // 初始规则
                    initialTemplate = template, // 初始模板
                    onSave = { rule -> // 保存回调
                        val ctx = this // 当前 Activity
                        launchSafe { // 安全启动协程
                            // 保存到本地 filesDir/rules/local/manual_<id>.json
                            val ok = repo.saveLocalRule(rule) // 保存到本地
                            val msg = if (ok) "已保存到本地：${rule.name}" else "保存失败，请查看日志" // 结果文案
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() // Toast
                            if (ok) finish() // 保存成功则关闭
                        }
                    },
                    onUpload = { rule -> // 上传回调
                        val ctx = this // 当前 Activity
                        launchSafe { // 安全启动协程
                            // 通过 GistClient 上传到 GitHub Gist
                            val ok = uploadRule(rule) // 上传
                            val msg = if (ok) "已上传：${rule.name}" else "上传失败，检查 Token/网络/日志" // 结果文案
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() // Toast
                        }
                    }
                )
            }
        }
    }

    /**
     * 上传单条规则到 GitHub Gist。
     *
     * 流程：
     * 1. 读取全局设置中的 GitHub Token 与 Gist ID。
     * 2. 通过 [DeviceIdProvider] 生成作者标识并补充时间戳。
     * 3. 包装成 [com.ltt.gkd.data.rule.RuleSet] 后调用 [GistClient.uploadRuleSet]。
     * 4. 首次上传得到的 gistId 会回写设置；上传成功后同时保存本地副本。
     *
     * @param rule 待上传的规则。
     * @return 是否上传成功。
     */
    private suspend fun uploadRule(rule: Rule): Boolean = withContext(Dispatchers.IO) { // 切到 IO 线程
        val settings = App.get().settings // 取全局设置
        val token = settings.githubToken.first() // 取 Token
        val gistId = settings.gistId.first() // 取 Gist ID
        if (token.isEmpty()) { // 无 Token
            Logger.w("未配置 GitHub Token，无法上传") // 日志警告
            return@withContext false // 返回失败
        }
        // 作者标识
        val author = DeviceIdProvider(this@RuleEditActivity, settings).get() // 生成设备 ID
        val ruleWithMeta = rule.copy(author = author, createdAt = System.currentTimeMillis()) // 补作者与时间
        val ruleSet = com.ltt.gkd.data.rule.RuleSet( // 组装 RuleSet
            name = ruleWithMeta.name, // 名称
            version = 1, // 版本
            author = author, // 作者
            rules = listOf(ruleWithMeta) // 规则列表
        )
        val fileName = "${author}_${ruleWithMeta.id}.json" // 文件名
        val (newGistId, ok) = gist.uploadRuleSet( // 上传
            token = token, // Token
            gistId = gistId, // Gist ID
            fileName = fileName, // 文件名
            ruleSet = ruleSet // 规则集
        )
        // 首次上传后保存 gistId 到设置
        if (ok && gistId.isEmpty() && newGistId.isNotEmpty()) { // 首次上传成功且原 Gist ID 为空
            settings.setGistId(newGistId) // 回写 Gist ID
        }
        // 上传成功后同时保存一份到本地（便于复用）
        if (ok) repo.saveLocalRule(ruleWithMeta) // 同步本地
        ok // 返回结果
    }

    companion object { // 伴生对象
        // Intent extra 键：规则 ID
        private const val EXTRA_RULE_ID = "rule_id" // 规则 ID 键
        // Intent extra 键：应用包名
        private const val EXTRA_PACKAGE_NAME = "package_name" // 包名键
        // Intent extra 键：应用显示名
        private const val EXTRA_APP_LABEL = "app_label" // 应用名键
        // Intent extra 键：模板枚举名
        private const val EXTRA_TEMPLATE = "template" // 模板键

        /**
         * 编辑已有规则。
         *
         * @param ctx 启动上下文。
         * @param ruleId 要编辑的规则 ID。
         */
        fun start(ctx: Context, ruleId: String) { // 编辑模式启动
            ctx.startActivity( // 启动 Activity
                Intent(ctx, RuleEditActivity::class.java).putExtra(EXTRA_RULE_ID, ruleId) // 携带规则 ID
            )
        }

        /**
         * 新建规则，可选从应用列表带入包名/应用名。
         *
         * @param ctx 启动上下文。
         * @param packageName 应用包名（可选）。
         * @param appLabel 应用显示名（可选）。
         */
        fun start(ctx: Context, packageName: String = "", appLabel: String = "") { // 新建模式启动
            val intent = Intent(ctx, RuleEditActivity::class.java) // 构造 Intent
            if (packageName.isNotEmpty()) intent.putExtra(EXTRA_PACKAGE_NAME, packageName) // 非空才放
            if (appLabel.isNotEmpty()) intent.putExtra(EXTRA_APP_LABEL, appLabel) // 非空才放
            ctx.startActivity(intent) // 启动
        }

        /**
         * 从模板新建规则。
         *
         * @param ctx 启动上下文。
         * @param template 使用的规则模板。
         * @param packageName 应用包名（可选）。
         * @param appLabel 应用显示名（可选）。
         */
        fun start( // 模板新建模式启动
            ctx: Context, // 上下文
            template: RuleTemplate, // 模板
            packageName: String = "", // 包名（可选）
            appLabel: String = "" // 应用名（可选）
        ) {
            val intent = Intent(ctx, RuleEditActivity::class.java) // 构造 Intent
                .putExtra(EXTRA_TEMPLATE, template.name) // 携带模板名
            if (packageName.isNotEmpty()) intent.putExtra(EXTRA_PACKAGE_NAME, packageName) // 非空才放
            if (appLabel.isNotEmpty()) intent.putExtra(EXTRA_APP_LABEL, appLabel) // 非空才放
            ctx.startActivity(intent) // 启动
        }
    }
}
