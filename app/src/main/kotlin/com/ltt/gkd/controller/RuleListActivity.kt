package com.ltt.gkd.controller

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ltt.gkd.App
import com.ltt.gkd.model.rule.RuleRepository
import com.ltt.gkd.model.subscription.GistClient
import com.ltt.gkd.model.util.Logger
import com.ltt.gkd.view.rule.RuleListScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class RuleListActivity : ComponentActivity() {

    private val repo: RuleRepository by lazy { RuleRepository(this) }
    private val gist: GistClient by lazy { GistClient() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RuleListScreen(
                repo = repo,
                onAddNew = { RuleEditActivity.start(this) },
                onPreviewBuiltIn = { name -> repo.readBuiltInRaw(name) },
                onSyncSubscribed = {
                    launchSafe {
                        val ok = syncSubscribed()
                        Toast.makeText(
                            this,
                            if (ok) "已同步" else "同步失败，检查 Gist ID/Token/网络",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onEditRule = { id -> RuleEditActivity.start(this, id) }
            )
        }
    }

    /** 拉取订阅规则到 filesDir/rules/subscribed/。 */
    private suspend fun syncSubscribed(): Boolean {
        val settings = App.get().settings
        val gistId = settings.gistId.first()
        if (gistId.isEmpty()) {
            Logger.w("未配置 Gist ID，无法同步订阅")
            return false
        }
        val rsList = gist.fetchRuleSets(gistId)
        if (rsList.isEmpty()) return false
        // 一次拉取订阅数（同一 Gist 共享同一计数）
        val subs = gist.fetchSubscriberCount(gistId)
        val subDir = java.io.File(filesDir, "rules/subscribed").apply { mkdirs() }
        // 清空旧订阅后写入新的
        subDir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.delete() }
        rsList.forEach { (fileName, ruleSet) ->
            val ruleSetWithSubs = ruleSet.copy(
                rules = ruleSet.rules.map { rule ->
                    rule.copy(subscribers = subs, source = com.ltt.gkd.model.rule.RuleSource.SUBSCRIBED)
                }
            )
            val json = com.ltt.gkd.model.util.globalAdapter<com.ltt.gkd.model.rule.RuleSet>()
                .toJson(ruleSetWithSubs)
            java.io.File(subDir, fileName).writeText(json)
        }
        repo.reload()
        Logger.i("同步订阅完成：${rsList.size} 个文件，订阅数=$subs")
        return true
    }
}

private fun androidx.lifecycle.LifecycleOwner.launchSafe(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    com.ltt.gkd.App.get().appScope.launch(block = block)
}
