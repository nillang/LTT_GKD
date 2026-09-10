package com.ltt.gkd.ui.rule

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ltt.gkd.App
import com.ltt.gkd.data.rule.Rule
import com.ltt.gkd.data.rule.RuleRepository
import com.ltt.gkd.data.rule.RuleTemplate
import com.ltt.gkd.data.subscription.GistClient
import com.ltt.gkd.util.DeviceIdProvider
import com.ltt.gkd.util.Logger
import com.ltt.gkd.util.launchSafe
import com.ltt.gkd.ui.rule.RuleEditScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
class RuleEditActivity : ComponentActivity() {

    private val repo: RuleRepository by lazy { RuleRepository(this) }
    private val gist: GistClient by lazy { GistClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialId = intent.getStringExtra(EXTRA_RULE_ID).orEmpty()
        val initialPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val initialName = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty()
        val templateStr = intent.getStringExtra(EXTRA_TEMPLATE)
        val template: RuleTemplate? = templateStr?.let {
            runCatching { RuleTemplate.valueOf(it) }.getOrNull()
        }

        // 编辑模式：从本地文件同步按 ID 读取，避免主线程 reload 全量
        val initial: Rule = if (initialId.isNotEmpty()) {
            repo.findRuleByIdSync(initialId) ?: Rule(id = initialId, name = "")
        } else if (template != null) {
            template.toRule(packageName = initialPkg, appLabel = initialName)
        } else {
            Rule(
                id = if (initialPkg.isNotEmpty()) "${initialPkg}_splash" else "",
                name = initialName,
                packageName = initialPkg
            )
        }

        setContent {
            RuleEditScreen(
                initial = initial,
                initialTemplate = template,
                onSave = { rule ->
                    val ctx = this
                    launchSafe {
                        val ok = repo.saveLocalRule(rule)
                        val msg = if (ok) "已保存到本地：${rule.name}" else "保存失败，请查看日志"
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        if (ok) finish()
                    }
                },
                onUpload = { rule ->
                    val ctx = this
                    launchSafe {
                        val ok = uploadRule(rule)
                        val msg = if (ok) "已上传：${rule.name}" else "上传失败，检查 Token/网络/日志"
                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }

    /** 上传单条规则到 GitHub Gist。 */
    private suspend fun uploadRule(rule: Rule): Boolean = withContext(Dispatchers.IO) {
        val settings = App.get().settings
        val token = settings.githubToken.first()
        val gistId = settings.gistId.first()
        if (token.isEmpty()) {
            Logger.w("未配置 GitHub Token，无法上传")
            return@withContext false
        }
        // 作者标识
        val author = DeviceIdProvider(this@RuleEditActivity, settings).get()
        val ruleWithMeta = rule.copy(author = author, createdAt = System.currentTimeMillis())
        val ruleSet = com.ltt.gkd.data.rule.RuleSet(
            name = ruleWithMeta.name,
            version = 1,
            author = author,
            rules = listOf(ruleWithMeta)
        )
        val fileName = "${author}_${ruleWithMeta.id}.json"
        val (newGistId, ok) = gist.uploadRuleSet(
            token = token,
            gistId = gistId,
            fileName = fileName,
            ruleSet = ruleSet
        )
        // 首次上传后保存 gistId 到设置
        if (ok && gistId.isEmpty() && newGistId.isNotEmpty()) {
            settings.setGistId(newGistId)
        }
        // 上传成功后同时保存一份到本地（便于复用）
        if (ok) repo.saveLocalRule(ruleWithMeta)
        ok
    }

    companion object {
        private const val EXTRA_RULE_ID = "rule_id"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_APP_LABEL = "app_label"
        private const val EXTRA_TEMPLATE = "template"

        /** 编辑已有规则。 */
        fun start(ctx: Context, ruleId: String) {
            ctx.startActivity(
                Intent(ctx, RuleEditActivity::class.java).putExtra(EXTRA_RULE_ID, ruleId)
            )
        }

        /** 新建规则，可选从应用列表带入包名/应用名。 */
        fun start(ctx: Context, packageName: String = "", appLabel: String = "") {
            val intent = Intent(ctx, RuleEditActivity::class.java)
            if (packageName.isNotEmpty()) intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            if (appLabel.isNotEmpty()) intent.putExtra(EXTRA_APP_LABEL, appLabel)
            ctx.startActivity(intent)
        }

        /** 从模板新建规则。 */
        fun start(
            ctx: Context,
            template: RuleTemplate,
            packageName: String = "",
            appLabel: String = ""
        ) {
            val intent = Intent(ctx, RuleEditActivity::class.java)
                .putExtra(EXTRA_TEMPLATE, template.name)
            if (packageName.isNotEmpty()) intent.putExtra(EXTRA_PACKAGE_NAME, packageName)
            if (appLabel.isNotEmpty()) intent.putExtra(EXTRA_APP_LABEL, appLabel)
            ctx.startActivity(intent)
        }
    }
}
