package com.ltt.gkd.ui.rule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltt.gkd.R
import com.ltt.gkd.data.rule.Rule
import com.ltt.gkd.data.rule.RuleSource
import com.ltt.gkd.data.rule.RuleRepository
import kotlinx.coroutines.launch

/**
 * 规则管理界面。
 *
 * 三个 Tab：
 * - 本地：私人规则，按 createdAt 倒序
 * - 订阅：从远程拉取，按 subscribers 倒序
 * - 内置：随 APK 发布，只读
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleListScreen(
    repo: RuleRepository,
    onAddNew: () -> Unit,
    onPreviewBuiltIn: (String) -> String?,
    onSyncSubscribed: () -> Unit,
    onEditRule: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val local by repo.localRules.collectAsState()
    val subscribed by repo.subscribedRules.collectAsState()
    val builtIn by repo.builtInRules.collectAsState()
    val builtInFiles = remember { repo.listBuiltInRuleFiles() }
    var tabIndex by remember { mutableStateOf(0) }
    var previewName by remember { mutableStateOf<String?>(null) }
    var previewContent by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Rule?>() }

    androidx.compose.runtime.LaunchedEffect(Unit) { repo.reload() }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_rule_list)) }) },
        floatingActionButton = {
            if (tabIndex == 0) {
                FloatingActionButton(onClick = onAddNew) {
                    Icon(Icons.Filled.Add, contentDescription = "新建规则")
                }
            } else if (tabIndex == 1) {
                FloatingActionButton(onClick = onSyncSubscribed) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = "同步订阅")
                }
            }
        }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = tabIndex) {
                    Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }) {
                        Text("本地 (${local.size})", Modifier.padding(12.dp))
                    }
                    Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }) {
                        Text("订阅 (${subscribed.size})", Modifier.padding(12.dp))
                    }
                    Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }) {
                        Text("内置 (${builtIn.size})", Modifier.padding(12.dp))
                    }
                }
                when (tabIndex) {
                    0 -> RuleList(
                        rules = local,
                        emptyHint = "暂无本地规则，点右下角 + 新建",
                        onClick = { onEditRule(it.id) },
                        onDelete = { deleteTarget = it }
                    )
                    1 -> RuleList(
                        rules = subscribed,
                        emptyHint = "暂无订阅规则，点右下角同步按钮拉取",
                        onClick = { onEditRule(it.id) },
                        onDelete = null  // 订阅规则不能单独删，通过删除订阅文件
                    )
                    2 -> Column(
                        Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        builtInFiles.forEach { name ->
                            Card(Modifier.fillMaxWidth().clickable {
                                previewName = name
                                previewContent = onPreviewBuiltIn(name)
                            }) {
                                Row(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.Folder, contentDescription = null)
                                    Spacer(Modifier.size(12.dp))
                                    Text(name, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("内置规则（共 ${builtIn.size} 条）", color = MaterialTheme.colorScheme.outline)
                        builtIn.forEach { RuleCard(it) }
                    }
                }
            }
        }
    }

    // 预览弹窗
    previewContent?.let { content ->
        AlertDialog(
            onDismissRequest = { previewName = null; previewContent = null },
            title = { Text(previewName ?: "") },
            text = {
                Surface(modifier = Modifier.height(360.dp)) {
                    Text(
                        text = content ?: "(读取失败)",
                        fontSize = 11.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { previewName = null; previewContent = null }) {
                    Text("关闭")
                }
            }
        )
    }

    // 删除确认
    deleteTarget?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除本地规则？") },
            text = { Text("${rule.name} (${rule.id})") },
            confirmButton = {
                OutlinedButton(onClick = {
                    val target = rule
                    deleteTarget = null
                    scope.launch { repo.deleteLocalRule(target.id) }
                }) { Text("删除") }
            },
            dismissButton = {
                OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RuleList(
    rules: List<Rule>,
    emptyHint: String,
    onClick: (Rule) -> Unit,
    onDelete: ((Rule) -> Unit)?
) {
    if (rules.isEmpty()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Text(
                emptyHint,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(rules, key = { it.id }) { rule ->
            RuleCardWithActions(
                rule = rule,
                onClick = { onClick(rule) },
                onDelete = onDelete?.let { { onDelete(rule) } }
            )
        }
    }
}

@Composable
private fun RuleCardWithActions(
    rule: Rule,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, fontWeight = FontWeight.Medium)
                Text(
                    "id: ${rule.id}  pkg: ${rule.packageName.ifEmpty { "(通用)" }}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                val meta = buildString {
                    if (rule.source == RuleSource.SUBSCRIBED) {
                        append("订阅数: ${rule.subscribers}  ")
                    } else if (rule.source == RuleSource.LOCAL) {
                        if (rule.createdAt > 0) {
                            val dateStr = java.text.SimpleDateFormat(
                                "yyyy-MM-dd HH:mm", java.util.Locale.US
                            ).format(java.util.Date(rule.createdAt))
                            append("创建: $dateStr  ")
                        }
                    }
                    if (rule.author.isNotEmpty()) append("作者: ${rule.author.take(8)}")
                }
                if (meta.isNotEmpty()) {
                    Text(meta, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun RuleCard(rule: Rule) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(rule.name, fontWeight = FontWeight.Medium)
            Text(
                "id: ${rule.id}  pkg: ${rule.packageName.ifEmpty { "(通用)" }}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
