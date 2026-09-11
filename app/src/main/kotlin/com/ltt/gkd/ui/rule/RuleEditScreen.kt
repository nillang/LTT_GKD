package com.ltt.gkd.ui.rule // 声明包名

import android.app.Activity // 导入 Activity 基类
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Box // 导入 Box 容器
import androidx.compose.foundation.layout.Column // 导入纵向容器
import androidx.compose.foundation.layout.ExperimentalLayoutApi // 导入实验性布局 API
import androidx.compose.foundation.layout.FlowRow // 导入流式行
import androidx.compose.foundation.layout.Row // 导入横向容器
import androidx.compose.foundation.layout.Spacer // 导入占位
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度
import androidx.compose.foundation.layout.height // 导入高度
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.size // 导入尺寸
import androidx.compose.foundation.rememberScrollState // 导入可记忆滚动状态
import androidx.compose.foundation.shape.CircleShape // 导入圆形
import androidx.compose.foundation.text.KeyboardOptions // 导入键盘选项
import androidx.compose.foundation.verticalScroll // 导入纵向滚动修饰符
import androidx.compose.animation.AnimatedVisibility // 导入动画可见性
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 导入返回箭头
import androidx.compose.material.icons.filled.Check // 导入对勾图标
import androidx.compose.material.icons.filled.CloudUpload // 导入云上传图标
import androidx.compose.material.icons.filled.ExpandLess // 导入收起图标
import androidx.compose.material.icons.filled.ExpandMore // 导入展开图标
import androidx.compose.material3.Button // 导入按钮
import androidx.compose.material3.Card // 导入卡片
import androidx.compose.material3.CardDefaults // 导入卡片默认值
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 Material3 API
import androidx.compose.material3.FilterChip // 导入筛选 Chip
import androidx.compose.material3.FilterChipDefaults // 导入 Chip 默认值
import androidx.compose.material3.HorizontalDivider // 导入水平分隔线
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedButton // 导入描边按钮
import androidx.compose.material3.OutlinedTextField // 导入描边文本框
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Surface // 导入 Surface
import androidx.compose.material3.Switch // 导入开关
import androidx.compose.material3.Text // 导入文本
import androidx.compose.material3.TopAppBar // 导入顶部栏
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.getValue // 导入 getValue
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember
import androidx.compose.runtime.saveable.rememberSaveable // 导入可保存状态
import androidx.compose.runtime.setValue // 导入 setValue
import androidx.compose.ui.Alignment // 导入对齐
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.platform.LocalContext // 导入本地上下文
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.text.input.ImeAction // 导入软键盘动作
import androidx.compose.ui.text.input.KeyboardType // 导入键盘类型
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp
import com.ltt.gkd.data.rule.ALL_TEMPLATES // 导入全部模板
import com.ltt.gkd.data.rule.ActionType // 导入动作类型枚举
import com.ltt.gkd.data.rule.MatchAction // 导入匹配动作
import com.ltt.gkd.data.rule.MatchTarget // 导入匹配目标
import com.ltt.gkd.data.rule.MatchType // 导入匹配类型枚举
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleTemplate // 导入规则模板枚举
import com.ltt.gkd.data.rule.toRule // 导入模板转规则扩展

/**
 * 规则编辑界面（v5 步骤式）。
 *
 * 布局：
 * 1. 选择广告类型（仅新建模式，选中模板自动填充推荐参数）
 * 2. 基本信息（名称 / 包名 / 启用）
 * 3. 匹配与动作（关键词 / 处理方式 / 忽略大小写）
 * 4. 高级选项折叠（ID / 匹配类型 / ViewID / 正则 / 优先级 / 节流 / 延迟）
 * 底部固定保存栏。
 *
 * @param initial 初始规则（编辑模式或带模板新建时由 Activity 组装）
 * @param onSave 保存回调
 * @param onUpload 上传回调（null 时不显示上传按钮）
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
fun RuleEditScreen( // 规则编辑主组件
    initial: Rule = Rule(id = "", name = ""), // 初始规则，默认空规则
    initialTemplate: RuleTemplate? = null, // 初始模板
    onUpload: ((Rule) -> Unit)? = null, // 上传回调，可空
    onSave: (Rule) -> Unit // 保存回调
) {
    val context = LocalContext.current // 取当前上下文
    val isNew = initial.id.isEmpty() // 是否新建模式（ID 为空）

    // ---- 表单状态（rememberSaveable 保证旋转不丢失） ----
    var id by rememberSaveable(initial.id) { mutableStateOf(initial.id) } // 规则 ID 状态
    var name by rememberSaveable(initial.name) { mutableStateOf(initial.name) } // 规则名称
    var packageName by rememberSaveable(initial.packageName) { mutableStateOf(initial.packageName) } // 包名
    var activity by rememberSaveable(initial.activity ?: "") { // Activity 限定
        mutableStateOf(initial.activity ?: "") // 默认空字符串
    }
    var enabled by rememberSaveable(initial.enabled) { mutableStateOf(initial.enabled) } // 启用状态
    var priority by rememberSaveable(initial.priority) { // 优先级
        mutableStateOf(initial.priority.toString()) // 转字符串便于编辑
    }
    var throttleMs by rememberSaveable(initial.throttleMs) { // 节流毫秒
        mutableStateOf(initial.throttleMs.toString()) // 转字符串
    }
    var createdAt by rememberSaveable(initial.createdAt) { mutableStateOf(initial.createdAt) } // 创建时间戳

    var matchType by rememberSaveable(initial.match.type.name) { // 匹配类型
        mutableStateOf(initial.match.type) // 枚举值
    }
    var textCsv by rememberSaveable(initial.match.text.joinToString(",")) { // 匹配文本 CSV
        mutableStateOf(initial.match.text.joinToString(",")) // 列表转 CSV
    }
    var idsCsv by rememberSaveable(initial.match.ids.joinToString(",")) { // ViewID CSV
        mutableStateOf(initial.match.ids.joinToString(",")) // 列表转 CSV
    }
    var regex by rememberSaveable(initial.match.regex) { mutableStateOf(initial.match.regex) } // 正则开关
    var caseInsensitive by rememberSaveable(initial.match.caseInsensitive) { // 忽略大小写
        mutableStateOf(initial.match.caseInsensitive) // 布尔值
    }

    var actionType by rememberSaveable(initial.action.type.name) { // 动作类型
        mutableStateOf(initial.action.type) // 枚举值
    }
    var actionDelayMs by rememberSaveable(initial.action.delayMs) { // 动作延迟
        mutableStateOf(initial.action.delayMs.toString()) // 转字符串
    }

    // 新建模式下选中的广告类型模板
    var selectedTemplate by remember { mutableStateOf(initialTemplate) } // 选中模板
    // 高级选项折叠（编辑模式默认展开，方便看到规则 ID）
    var advancedExpanded by remember { mutableStateOf(!isNew) } // 是否展开高级选项

    /** 套用模板：填充推荐参数；ID/名称为空时按当前包名给建议。 */
    fun applyTemplate(tpl: RuleTemplate) { // 应用模板方法
        selectedTemplate = tpl // 记录选中的模板
        val tplRule = tpl.toRule(packageName = packageName, appLabel = name) // 由模板生成推荐规则
        if (id.isBlank()) id = tplRule.id // ID 为空用模板建议
        if (name.isBlank()) name = tplRule.name // 名称为空用模板建议
        priority = tplRule.priority.toString() // 套用推荐优先级
        throttleMs = tplRule.throttleMs.toString() // 套用推荐节流
        matchType = tplRule.match.type // 套用匹配类型
        textCsv = tplRule.match.text.joinToString(",") // 套用匹配文本
        idsCsv = tplRule.match.ids.joinToString(",") // 套用 ViewID
        regex = tplRule.match.regex // 套用正则开关
        caseInsensitive = tplRule.match.caseInsensitive // 套用忽略大小写
        actionType = tplRule.action.type // 套用动作类型
    }

    fun buildCurrent(): Rule = buildRule( // 组装当前表单为 Rule 对象
        id = id, name = name, packageName = packageName, // 基本信息
        activity = activity, enabled = enabled, // Activity 与启用
        priority = priority, throttleMs = throttleMs, // 优先级与节流
        createdAt = if (createdAt == 0L) System.currentTimeMillis() else createdAt, // 时间戳缺省取当前
        matchType = matchType, textCsv = textCsv, idsCsv = idsCsv, // 匹配相关
        regex = regex, caseInsensitive = caseInsensitive, // 正则与大小写
        actionType = actionType, actionDelayMs = actionDelayMs // 动作相关
    )

    Scaffold( // 骨架
        topBar = { // 顶部栏
            TopAppBar( // 顶部应用栏
                title = { Text(if (isNew) "新建规则" else "编辑规则") }, // 标题随模式
                navigationIcon = { // 返回按钮
                    IconButton(onClick = { (context as? Activity)?.finish() }) { // 调用 Activity.finish
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") // 返回箭头
                    }
                }
            )
        },
        bottomBar = { // 底部保存栏
            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) { // 带阴影的底部条
                Row( // 按钮横向容器
                    Modifier.fillMaxWidth().padding(12.dp), // 内边距
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // 间距
                ) {
                    Button( // 保存按钮
                        onClick = { // 点击保存
                            val rule = buildCurrent() // 组装规则
                            val err = validateRule(rule) // 校验
                            if (err != null) { // 校验失败
                                android.widget.Toast.makeText(context, err, // Toast 错误
                                    android.widget.Toast.LENGTH_SHORT).show()
                            } else { // 校验通过
                                onSave(rule) // 触发保存
                            }
                        },
                        modifier = Modifier.weight(1f), // 占等分宽度
                        enabled = id.isNotBlank() && name.isNotBlank() // ID 与名称非空才可点
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null) // 对勾图标
                        Spacer(Modifier.size(6.dp)) // 间距
                        Text("保存本地") // 按钮文案
                    }
                    if (onUpload != null) { // 有上传回调才显示
                        OutlinedButton( // 上传按钮
                            onClick = { // 点击上传
                                val rule = buildCurrent() // 组装规则
                                val err = validateRule(rule) // 校验
                                if (err != null) { // 校验失败
                                    android.widget.Toast.makeText(context, err, // Toast 错误
                                        android.widget.Toast.LENGTH_SHORT).show()
                                } else { // 校验通过
                                    onUpload(rule) // 触发上传
                                }
                            },
                            modifier = Modifier.weight(1f), // 占等分宽度
                            enabled = id.isNotBlank() && name.isNotBlank() // ID 与名称非空才可点
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null) // 云上传图标
                            Spacer(Modifier.size(6.dp)) // 间距
                            Text("上传共享") // 按钮文案
                        }
                    }
                }
            }
        }
    ) { inner -> // 内容区
        Column( // 纵向滚动容器
            Modifier
                .padding(inner) // 顶部栏内边距
                .fillMaxSize() // 填满
                .verticalScroll(rememberScrollState()) // 可滚动
                .padding(horizontal = 16.dp, vertical = 12.dp), // 内边距
            verticalArrangement = Arrangement.spacedBy(12.dp) // 间距
        ) {
            var stepNo = 0 // 步骤编号计数

            // ---- 步骤 ①：广告类型（仅新建模式） ----
            if (isNew) { // 新建才显示
                StepCard(number = ++stepNo, title = "选择广告类型") { // 步骤卡片
                    TemplateChips( // 模板 Chip 组
                        selected = selectedTemplate, // 当前选中
                        onPick = { applyTemplate(it) } // 选中后套用
                    )
                }
            }

            // ---- 步骤 ②：基本信息 ----
            StepCard(number = ++stepNo, title = "基本信息") { // 步骤卡片
                OutlinedTextField( // 规则名称输入框
                    value = name, // 当前值
                    onValueChange = { name = it }, // 更新
                    label = { Text("规则名称") }, // 标签
                    placeholder = { Text("如 微信开屏") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满宽度
                    singleLine = true, // 单行
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next) // 下一步键
                )
                Spacer(Modifier.height(10.dp)) // 间距
                OutlinedTextField( // 包名输入框
                    value = packageName, // 当前值
                    onValueChange = { packageName = it }, // 更新
                    label = { Text("目标包名（空 = 通用兜底）") }, // 标签
                    placeholder = { Text("如 com.tencent.mm") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满
                    singleLine = true, // 单行
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next) // 下一步
                )
                Spacer(Modifier.height(10.dp)) // 间距
                OutlinedTextField( // Activity 输入框
                    value = activity, // 当前值
                    onValueChange = { activity = it }, // 更新
                    label = { Text("限定 Activity（可选）") }, // 标签
                    placeholder = { Text("如 SplashActivity") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满
                    singleLine = true, // 单行
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next) // 下一步
                )
                Spacer(Modifier.height(4.dp)) // 间距
                ToggleRow( // 启用开关行
                    title = "启用此规则", // 主标题
                    summary = "禁用后不会参与匹配", // 副标题
                    checked = enabled, // 当前状态
                    onChange = { enabled = it } // 切换
                )
            }

            // ---- 步骤 ③：匹配与动作 ----
            StepCard(number = ++stepNo, title = "匹配文字与处理方式") { // 步骤卡片
                OutlinedTextField( // 匹配文本输入框
                    value = textCsv, // 当前值（CSV）
                    onValueChange = { textCsv = it }, // 更新
                    label = { Text("匹配文字（逗号分隔）") }, // 标签
                    placeholder = { Text("跳过, 跳过广告, Skip") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满
                    minLines = 2 // 至少 2 行
                )
                Spacer(Modifier.height(12.dp)) // 间距
                FieldLabel("处理方式") // 字段标签
                ChipRow { // 动作 Chip 行
                    ActionType.values().forEach { at -> // 遍历所有动作类型
                        FilterChip( // 筛选 Chip
                            selected = actionType == at, // 是否选中
                            onClick = { actionType = at }, // 点击切换
                            label = { Text(actionLabel(at)) } // 中文文案
                        )
                    }
                }
                Spacer(Modifier.height(8.dp)) // 间距
                ToggleRow( // 忽略大小写开关
                    title = "忽略大小写", // 主标题
                    summary = "推荐保持开启", // 副标题
                    checked = caseInsensitive, // 当前状态
                    onChange = { caseInsensitive = it } // 切换
                )
            }

            // ---- 高级选项（折叠） ----
            Card( // 高级选项卡片
                modifier = Modifier.fillMaxWidth(), // 占满
                colors = CardDefaults.cardColors( // 颜色
                    containerColor = MaterialTheme.colorScheme.surface // 用 surface 色
                ),
                onClick = { advancedExpanded = !advancedExpanded } // 点击切换展开
            ) {
                Row( // 标题行
                    Modifier.fillMaxWidth().padding(16.dp), // 内边距
                    verticalAlignment = Alignment.CenterVertically // 垂直居中
                ) {
                    Text( // 标题
                        "高级选项", // 文案
                        fontWeight = FontWeight.SemiBold, // 半粗体
                        fontSize = 15.sp, // 字号
                        modifier = Modifier.weight(1f) // 占满剩余
                    )
                    Icon( // 展开/收起图标
                        if (advancedExpanded) Icons.Filled.ExpandLess // 展开时显示收起
                        else Icons.Filled.ExpandMore, // 收起时显示展开
                        contentDescription = if (advancedExpanded) "收起" else "展开" // 无障碍描述
                    )
                }
                AnimatedVisibility(visible = advancedExpanded) { // 展开时可见
                    HorizontalDivider() // 分隔线
                    Column( // 高级选项内容列
                        Modifier.padding(16.dp), // 内边距
                        verticalArrangement = Arrangement.spacedBy(10.dp) // 间距
                    ) {
                        OutlinedTextField( // 规则 ID 输入框
                            value = id, // 当前值
                            onValueChange = { id = it }, // 更新
                            label = { Text("规则 ID（全局唯一）") }, // 标签
                            placeholder = { Text("如 com.tencent.mm_splash") }, // 占位
                            modifier = Modifier.fillMaxWidth(), // 占满
                            singleLine = true // 单行
                        )
                        FieldLabel("匹配类型") // 字段标签
                        ChipRow { // 匹配类型 Chip 行
                            MatchType.values().forEach { mt -> // 遍历所有匹配类型
                                FilterChip( // 筛选 Chip
                                    selected = matchType == mt, // 是否选中
                                    onClick = { matchType = mt }, // 点击切换
                                    label = { Text(matchLabel(mt)) } // 中文文案
                                )
                            }
                        }
                        OutlinedTextField( // ViewID 输入框
                            value = idsCsv, // 当前值（CSV）
                            onValueChange = { idsCsv = it }, // 更新
                            label = { Text("匹配 ViewID（逗号分隔，仅 ID 类型）") }, // 标签
                            placeholder = { Text("skip_btn, ad_skip") }, // 占位
                            modifier = Modifier.fillMaxWidth(), // 占满
                            singleLine = true // 单行
                        )
                        ToggleRow( // 正则开关
                            title = "正则模式", // 主标题
                            summary = "开启后匹配文字按正则解析", // 副标题
                            checked = regex, // 当前状态
                            onChange = { regex = it } // 切换
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { // 优先级与节流行
                            OutlinedTextField( // 优先级
                                value = priority, // 当前值
                                onValueChange = { v -> // 仅保留数字
                                    priority = v.filter { it.isDigit() }
                                },
                                label = { Text("优先级") }, // 标签
                                modifier = Modifier.weight(1f), // 等分
                                singleLine = true, // 单行
                                keyboardOptions = KeyboardOptions( // 数字键盘
                                    keyboardType = KeyboardType.Number
                                )
                            )
                            OutlinedTextField( // 节流
                                value = throttleMs, // 当前值
                                onValueChange = { v -> // 仅保留数字
                                    throttleMs = v.filter { it.isDigit() }
                                },
                                label = { Text("节流 (ms)") }, // 标签
                                modifier = Modifier.weight(1f), // 等分
                                singleLine = true, // 单行
                                keyboardOptions = KeyboardOptions( // 数字键盘
                                    keyboardType = KeyboardType.Number
                                )
                            )
                        }
                        OutlinedTextField( // 动作延迟输入框
                            value = actionDelayMs, // 当前值
                            onValueChange = { v -> // 仅保留数字
                                actionDelayMs = v.filter { it.isDigit() }
                            },
                            label = { Text("动作执行延迟 (ms)") }, // 标签
                            modifier = Modifier.fillMaxWidth(), // 占满
                            singleLine = true, // 单行
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // 数字键盘
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp)) // 底部间距
        }
    }
}

/** 带编号圆的步骤卡片。 */
@Composable // 标记为 Composable
private fun StepCard( // 步骤卡片组件
    number: Int, // 编号
    title: String, // 标题
    content: @Composable () -> Unit // 内容
) {
    Card( // 卡片容器
        modifier = Modifier.fillMaxWidth(), // 占满
        colors = CardDefaults.cardColors( // 颜色
            containerColor = MaterialTheme.colorScheme.surface // surface 色
        )
    ) {
        Column(Modifier.padding(16.dp)) { // 内部纵向布局
            Row(verticalAlignment = Alignment.CenterVertically) { // 标题行
                Surface( // 编号圆
                    shape = CircleShape, // 圆形
                    color = MaterialTheme.colorScheme.primary, // 主色背景
                    modifier = Modifier.size(26.dp) // 尺寸
                ) {
                    Box(contentAlignment = Alignment.Center) { // 居中
                        Text( // 编号文字
                            number.toString(), // 编号转字符串
                            color = MaterialTheme.colorScheme.onPrimary, // 主色上的文字色
                            fontWeight = FontWeight.Bold, // 加粗
                            fontSize = 14.sp // 字号
                        )
                    }
                }
                Spacer(Modifier.size(10.dp)) // 间距
                Text( // 步骤标题
                    title, // 文案
                    fontSize = 16.sp, // 字号
                    fontWeight = FontWeight.SemiBold // 半粗体
                )
            }
            Spacer(Modifier.height(14.dp)) // 间距
            content() // 渲染内容
        }
    }
}

/** 步骤①的广告类型 Chip 组（带模板描述）。 */
@OptIn(ExperimentalLayoutApi::class) // 启用实验性布局 API
@Composable // 标记为 Composable
private fun TemplateChips( // 模板 Chip 组
    selected: RuleTemplate?, // 当前选中
    onPick: (RuleTemplate) -> Unit // 选中回调
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { // 纵向容器
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { // 流式行
            ALL_TEMPLATES.forEach { meta -> // 遍历所有模板
                FilterChip( // 筛选 Chip
                    selected = selected == meta.template, // 是否选中
                    onClick = { onPick(meta.template) }, // 点击触发
                    label = { Text(meta.title) }, // 文案
                    colors = FilterChipDefaults.filterChipColors() // 默认颜色
                )
            }
        }
        val meta = ALL_TEMPLATES.firstOrNull { it.template == selected } // 当前选中模板的元数据
        if (meta != null) { // 有选中才显示描述
            Text( // 描述文字
                meta.description + "  推荐 优先级=${meta.recommendedPriority}，" + // 描述与推荐参数
                    "节流=${meta.recommendedThrottleMs}ms", // 推荐节流
                fontSize = 11.sp, // 小号字
                color = MaterialTheme.colorScheme.outline // 描边色
            )
        }
    }
}

@Composable // 标记为 Composable
private fun FieldLabel(text: String) { // 字段标签
    Text( // 文本
        text, // 文案
        fontSize = 13.sp, // 字号
        fontWeight = FontWeight.Medium, // 中粗体
        color = MaterialTheme.colorScheme.outline, // 描边色
        modifier = Modifier.padding(bottom = 2.dp) // 底部间距
    )
}

@OptIn(ExperimentalLayoutApi::class) // 启用实验性布局
@Composable // 标记为 Composable
private fun ChipRow(content: @Composable () -> Unit) { // Chip 行容器
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { // 流式行
        content() // 渲染内容
    }
}

@Composable // 标记为 Composable
private fun ToggleRow( // 开关行
    title: String, // 主标题
    summary: String, // 副标题
    checked: Boolean, // 当前状态
    onChange: (Boolean) -> Unit // 切换回调
) {
    Row( // 横向布局
        Modifier.fillMaxWidth(), // 占满
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        Column(Modifier.weight(1f)) { // 左侧文本列
            Text(title, fontSize = 15.sp) // 主标题
            Text(summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline) // 副标题
        }
        Switch(checked = checked, onCheckedChange = onChange) // 开关
    }
}

private fun actionLabel(at: ActionType): String = when (at) { // 动作类型转中文
    ActionType.CLICK_NODE -> "点击节点" // 点击节点
    ActionType.CLICK_COORD -> "点击坐标" // 点击坐标
    ActionType.BACK -> "返回键" // 返回键
    ActionType.GESTURE_TAP -> "手势点击" // 手势点击
}

private fun matchLabel(mt: MatchType): String = when (mt) { // 匹配类型转中文
    MatchType.TEXT -> "文本" // 文本
    MatchType.DESC -> "描述" // 描述
    MatchType.ID -> "ViewID" // ViewID
    MatchType.OCR -> "OCR" // OCR
}

/** 把表单字段组装成 Rule。 */
@Suppress("LongParameterList") // 抑制参数过多警告
private fun buildRule( // 组装规则函数
    id: String, name: String, packageName: String, activity: String, // 基本字段
    enabled: Boolean, priority: String, throttleMs: String, // 启用、优先级、节流
    createdAt: Long, // 时间戳
    matchType: MatchType, textCsv: String, idsCsv: String, // 匹配相关
    regex: Boolean, caseInsensitive: Boolean, // 正则与大小写
    actionType: ActionType, actionDelayMs: String // 动作相关
): Rule = Rule( // 返回 Rule 实例
    id = id.trim(), // 去空格
    name = name.trim(), // 去空格
    packageName = packageName.trim(), // 去空格
    activity = activity.trim().ifBlank { null }, // 空白转 null
    enabled = enabled, // 启用状态
    priority = priority.toIntOrNull() ?: 0, // 转整数失败取 0
    throttleMs = throttleMs.toLongOrNull() ?: 2000L, // 转长整失败取 2000
    createdAt = createdAt, // 时间戳
    match = MatchTarget( // 匹配目标
        type = matchType, // 匹配类型
        text = textCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }, // CSV 切分并去空白
        ids = idsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() }, // CSV 切分并去空白
        regex = regex, // 正则
        caseInsensitive = caseInsensitive // 忽略大小写
    ),
    action = MatchAction( // 匹配动作
        type = actionType, // 动作类型
        delayMs = actionDelayMs.toLongOrNull() ?: 0L // 延迟，失败取 0
    )
)

/**
 * 校验规则字段，返回首个错误消息（null = 通过）。
 */
private fun validateRule(rule: Rule): String? = when { // 校验函数
    rule.id.isBlank() -> "规则 ID 不能为空" // ID 为空
    rule.id.any { it.isWhitespace() } -> "规则 ID 不能包含空格" // ID 含空格
    rule.name.isBlank() -> "规则名称不能为空" // 名称为空
    rule.packageName.isNotEmpty() && !rule.packageName.contains('.') -> // 包名非空但无点
        "包名格式不正确（需包含 '.'，如 com.tencent.mm）"
    rule.match.type != MatchType.ID && rule.match.text.isEmpty() -> // 非 ID 类型但文本为空
        "匹配文本不能为空（${rule.match.type} 类型需至少一个关键词）"
    rule.match.type == MatchType.ID && rule.match.ids.isEmpty() -> // ID 类型但 ViewID 为空
        "ID 类型匹配需至少一个 ViewID"
    else -> null // 校验通过
}
