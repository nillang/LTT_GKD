package com.ltt.gkd.view.rule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltt.gkd.R
import com.ltt.gkd.model.rule.ActionType
import com.ltt.gkd.model.rule.MatchAction
import com.ltt.gkd.model.rule.MatchTarget
import com.ltt.gkd.model.rule.MatchType
import com.ltt.gkd.model.rule.Rule
import com.ltt.gkd.model.rule.RuleTemplate
import com.ltt.gkd.model.rule.ALL_TEMPLATES
import com.ltt.gkd.model.rule.TemplateMeta
import com.ltt.gkd.model.rule.toRule

/**
 * 规则编辑界面模板。
 *
 * View 只负责维护表单状态 + 通知 Controller 用户意图；
 * 保存/导出动作通过 [onSave] 回调由 Controller 决定如何持久化。
 *
 * @param initial 初始规则（用于编辑模式）；新建模式传 Rule 默认实例
 * @param onSave 用户点击"保存"时回调，参数为当前编辑中的 Rule
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditScreen(
    initial: Rule = Rule(id = "", name = ""),
    initialTemplate: RuleTemplate? = null,
    onUpload: ((Rule) -> Unit)? = null,
    onSave: (Rule) -> Unit
) {
    // ---- 表单状态 ----
    // 注意：rememberSaveable 保证屏幕旋转后状态不丢失
    var id by rememberSaveable(initial.id) { mutableStateOf(initial.id) }
    var name by rememberSaveable(initial.name) { mutableStateOf(initial.name) }
    var packageName by rememberSaveable(initial.packageName) { mutableStateOf(initial.packageName) }
    var activity by rememberSaveable(initial.activity ?: "") { mutableStateOf(initial.activity ?: "") }
    var enabled by rememberSaveable(initial.enabled) { mutableStateOf(initial.enabled) }
    var priority by rememberSaveable(initial.priority) { mutableStateOf(initial.priority.toString()) }
    var throttleMs by rememberSaveable(initial.throttleMs) { mutableStateOf(initial.throttleMs.toString()) }
    var createdAt by rememberSaveable(initial.createdAt) { mutableStateOf(initial.createdAt) }

    // 匹配目标
    var matchType by rememberSaveable(initial.match.type.name) {
        mutableStateOf(initial.match.type)
    }
    var textCsv by rememberSaveable(initial.match.text.joinToString(",")) {
        mutableStateOf(initial.match.text.joinToString(","))
    }
    var idsCsv by rememberSaveable(initial.match.ids.joinToString(",")) {
        mutableStateOf(initial.match.ids.joinToString(","))
    }
    var regex by rememberSaveable(initial.match.regex) { mutableStateOf(initial.match.regex) }
    var caseInsensitive by rememberSaveable(initial.match.caseInsensitive) {
        mutableStateOf(initial.match.caseInsensitive)
    }

    // 动作
    var actionType by rememberSaveable(initial.action.type.name) {
        mutableStateOf(initial.action.type)
    }
    var actionDelayMs by rememberSaveable(initial.action.delayMs) {
        mutableStateOf(initial.action.delayMs.toString())
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(if (initial.id.isEmpty()) "新建规则" else "编辑规则") })
        }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ---- 模板选择（仅新建模式显示） ----
                if (initial.id.isEmpty() && initialTemplate == null) {
                    SectionTitle("选择模板（可选）")
                    TemplateSelector(onPick = { tpl ->
                        val tplRule = tpl.toRule(packageName = packageName, appLabel = name)
                        id = tplRule.id
                        priority = tplRule.priority.toString()
                        throttleMs = tplRule.throttleMs.toString()
                        matchType = tplRule.match.type
                        textCsv = tplRule.match.text.joinToString(",")
                        regex = tplRule.match.regex
                        caseInsensitive = tplRule.match.caseInsensitive
                        actionType = tplRule.action.type
                    })
                    Spacer(Modifier.height(4.dp))
                }

                // ---- 基本信息 ----
                SectionTitle("基本信息")
                OutlinedTextField(
                    value = id,
                    onValueChange = { id = it },
                    label = { Text("规则 ID（必填，全局唯一）") },
                    placeholder = { Text("如 com.tencent.mm_splash") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("规则名称（必填）") },
                    placeholder = { Text("如 微信开屏") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("目标包名（空=通用兜底）") },
                    placeholder = { Text("如 com.tencent.mm") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = activity,
                    onValueChange = { activity = it },
                    label = { Text("限定 Activity（可选）") },
                    placeholder = { Text("如 SplashActivity") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ToggleRow(
                    title = "启用",
                    summary = "禁用后此规则不会被加载",
                    checked = enabled,
                    onChange = { enabled = it }
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = priority,
                        onValueChange = { priority = it.filter { c -> c.isDigit() } },
                        label = { Text("优先级") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = throttleMs,
                        onValueChange = { throttleMs = it.filter { c -> c.isDigit() } },
                        label = { Text("节流(ms)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // ---- 匹配目标 ----
                SectionTitle("匹配目标")
                ChipGroup(
                    label = "匹配类型",
                    options = MatchType.values().toList(),
                    selected = matchType,
                    onSelected = { matchType = it },
                    labelOf = { it.name }
                )
                OutlinedTextField(
                    value = textCsv,
                    onValueChange = { textCsv = it },
                    label = { Text("匹配文本（逗号分隔）") },
                    placeholder = { Text("跳过,跳过广告,Skip") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = idsCsv,
                    onValueChange = { idsCsv = it },
                    label = { Text("匹配 ViewID（逗号分隔，仅 ID 类型用）") },
                    placeholder = { Text("skip_btn, ad_skip") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                ToggleRow(
                    title = "正则模式",
                    summary = "开启后 text 按正则解析",
                    checked = regex,
                    onChange = { regex = it }
                )
                ToggleRow(
                    title = "忽略大小写",
                    summary = "推荐保持开启",
                    checked = caseInsensitive,
                    onChange = { caseInsensitive = it }
                )

                Spacer(Modifier.height(8.dp))

                // ---- 动作 ----
                SectionTitle("命中动作")
                ChipGroup(
                    label = "动作类型",
                    options = ActionType.values().toList(),
                    selected = actionType,
                    onSelected = { actionType = it },
                    labelOf = { it.name }
                )
                OutlinedTextField(
                    value = actionDelayMs,
                    onValueChange = { actionDelayMs = it.filter { c -> c.isDigit() } },
                    label = { Text("执行延迟(ms)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )

                Spacer(Modifier.height(16.dp))

                // ---- 保存按钮 + 上传按钮 ----
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val rule = buildRule(
                                id = id, name = name, packageName = packageName,
                                activity = activity, enabled = enabled,
                                priority = priority, throttleMs = throttleMs,
                                createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
                                matchType = matchType, textCsv = textCsv, idsCsv = idsCsv,
                                regex = regex, caseInsensitive = caseInsensitive,
                                actionType = actionType, actionDelayMs = actionDelayMs
                            )
                            onSave(rule)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = id.isNotBlank() && name.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("保存本地")
                    }
                    if (onUpload != null) {
                        OutlinedButton(
                            onClick = {
                                val rule = buildRule(
                                    id = id, name = name, packageName = packageName,
                                    activity = activity, enabled = enabled,
                                    priority = priority, throttleMs = throttleMs,
                                    createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt,
                                    matchType = matchType, textCsv = textCsv, idsCsv = idsCsv,
                                    regex = regex, caseInsensitive = caseInsensitive,
                                    actionType = actionType, actionDelayMs = actionDelayMs
                                )
                                onUpload(rule)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = id.isNotBlank() && name.isNotBlank()
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(Modifier.height(0.dp))
                            Text("上传共享")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp)
                Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> ChipGroup(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    labelOf: (T) -> String
) {
    Column {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { opt ->
                FilterChip(
                    selected = opt == selected,
                    onClick = { onSelected(opt) },
                    label = { Text(labelOf(opt)) }
                )
            }
        }
    }
}

/** 模板选择卡片列表。 */
@Composable
private fun TemplateSelector(onPick: (RuleTemplate) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ALL_TEMPLATES.forEach { meta ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(meta.template) }
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(meta.title, fontWeight = FontWeight.Medium)
                    Text(
                        meta.description,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        "推荐 priority=${meta.recommendedPriority}  throttle=${meta.recommendedThrottleMs}ms",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/** 把表单字段组装成 Rule（避免重复代码）。 */
@Suppress("LongParameterList")
private fun buildRule(
    id: String, name: String, packageName: String, activity: String,
    enabled: Boolean, priority: String, throttleMs: String,
    createdAt: Long,
    matchType: MatchType, textCsv: String, idsCsv: String,
    regex: Boolean, caseInsensitive: Boolean,
    actionType: ActionType, actionDelayMs: String
): Rule = Rule(
    id = id.trim(),
    name = name.trim(),
    packageName = packageName.trim(),
    activity = activity.trim().ifBlank { null },
    enabled = enabled,
    priority = priority.toIntOrNull() ?: 0,
    throttleMs = throttleMs.toLongOrNull() ?: 2000L,
    createdAt = createdAt,
    match = MatchTarget(
        type = matchType,
        text = textCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
        ids = idsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() },
        regex = regex,
        caseInsensitive = caseInsensitive
    ),
    action = MatchAction(
        type = actionType,
        delayMs = actionDelayMs.toLongOrNull() ?: 0L
    )
)
