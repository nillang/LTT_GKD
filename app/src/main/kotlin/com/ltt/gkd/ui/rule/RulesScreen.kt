package com.ltt.gkd.ui.rule // 声明包名，对应规则界面目录

import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.clickable // 导入点击修饰符
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Box // 导入 Box 容器
import androidx.compose.foundation.layout.Column // 导入纵向容器
import androidx.compose.foundation.layout.Row // 导入横向容器
import androidx.compose.foundation.layout.Spacer // 导入占位
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度
import androidx.compose.foundation.layout.height // 导入高度
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.size // 导入尺寸
import androidx.compose.foundation.layout.width // 导入宽度
import androidx.compose.foundation.lazy.LazyColumn // 导入懒加载列表
import androidx.compose.foundation.lazy.items // 导入 LazyColumn 的 items 函数
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角 Shape
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile // 导入文件图标
import androidx.compose.material.icons.filled.Add // 导入加号图标
import androidx.compose.material.icons.filled.CloudDownload // 导入云下载图标
import androidx.compose.material.icons.filled.CloudUpload // 导入云上传图标
import androidx.compose.material.icons.filled.Delete // 导入删除图标
import androidx.compose.material.icons.filled.Folder // 导入文件夹图标
import androidx.compose.material.icons.filled.MoreVert // 导入三点菜单图标
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.DropdownMenu // 导入下拉菜单
import androidx.compose.material3.DropdownMenuItem // 导入下拉菜单项
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 Material3 API
import androidx.compose.material3.FloatingActionButton // 导入悬浮按钮
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedButton // 导入描边按钮
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Surface // 导入 Surface 容器
import androidx.compose.material3.Switch // 导入开关组件
import androidx.compose.material3.Tab // 导入 Tab 项
import androidx.compose.material3.TabRow // 导入 Tab 容器
import androidx.compose.material3.Text // 导入文本组件
import androidx.compose.material3.TopAppBar // 导入顶部应用栏
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.LaunchedEffect // 导入一次性副作用
import androidx.compose.runtime.collectAsState // 导入 Flow 收集为状态
import androidx.compose.runtime.getValue // 导入 getValue 委托
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember 保存状态
import androidx.compose.runtime.rememberCoroutineScope // 导入协程作用域
import androidx.compose.runtime.setValue // 导入 setValue 委托
import androidx.compose.ui.Alignment // 导入对齐
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.compose.ui.unit.sp // 导入 sp 单位
import com.ltt.gkd.data.prefs.SettingsStore // 导入设置存储
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleRepository // 导入规则仓库
import com.ltt.gkd.data.rule.RuleSource // 导入规则来源枚举
import com.ltt.gkd.ui.theme.AccentPurple // 导入主题强调紫色（亮色）
import com.ltt.gkd.ui.theme.PurpleBadgeBg // 导入主题紫色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkAccentPurple // 导入深色模式紫色前景
import com.ltt.gkd.ui.theme.DarkPurpleBadgeBg // 导入深色紫色徽章背景
import androidx.compose.foundation.isSystemInDarkTheme // 导入深色主题判断函数
import kotlinx.coroutines.launch // 导入协程启动

/**
 * 规则管理（UI v5 ③）：本地/订阅/内置三 Tab + 卡片开关 + 三点菜单 + FAB。
 *
 * @param repo 规则仓库，提供本地/订阅/内置规则流及增删读取能力。
 * @param settings 设置存储，用于读写规则启用/禁用集合。
 * @param onAddNew 点击"新增规则"FAB 回调，跳转规则编辑页新建。
 * @param onEditRule 点击规则卡片回调，参数为规则 ID，跳转编辑页修改。
 * @param onSyncSubscribed 点击"同步订阅"FAB 回调，拉取远程订阅规则。
 * @param onImport 选择导入文件回调，由调用方启动文件选择器。
 * @param onExport 选择导出文件回调，由调用方启动文件创建器。
 * @param onPreviewBuiltIn 预览内置规则文件回调，参数为文件名，返回文件内容或 null。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 Material3 API
@Composable // 标记为 Composable
fun RulesScreen( // 规则管理主组件
    repo: RuleRepository, // 规则仓库
    settings: SettingsStore, // 设置存储
    onAddNew: () -> Unit, // 新增规则回调
    onEditRule: (String) -> Unit, // 编辑规则回调
    onSyncSubscribed: () -> Unit, // 同步订阅回调
    onImport: () -> Unit, // 导入回调
    onExport: () -> Unit, // 导出回调
    onPreviewBuiltIn: (String) -> String? // 预览内置规则回调
) {
    val scope = rememberCoroutineScope() // 协程作用域
    val local by repo.localRules.collectAsState() // 本地规则列表
    val subscribed by repo.subscribedRules.collectAsState() // 订阅规则列表
    val builtIn by repo.builtInRules.collectAsState() // 内置规则列表
    val disabledIds by settings.disabledRuleIds.collectAsState(initial = emptySet()) // 已禁用 ID 集合
    val builtInFiles = remember { repo.listBuiltInRuleFiles() } // 内置规则文件名列表

    var tabIndex by remember { mutableStateOf(0) } // 当前 Tab 索引
    var menuOpen by remember { mutableStateOf(false) } // 三点菜单展开状态
    var deleteTarget by remember { mutableStateOf<Rule?>(null) } // 待删除规则
    var preview by remember { mutableStateOf<Pair<String, String?>?>(null) } // 内置规则预览内容

    LaunchedEffect(Unit) { repo.reload() } // 首次进入重新加载规则

    Scaffold( // 骨架
        topBar = { // 顶部栏
            TopAppBar( // 顶部应用栏
                title = { Text("规则管理") }, // 标题
                actions = { // 操作区
                    // 三点菜单：导入/导出规则文件
                    Box { // 包裹菜单与触发按钮
                        IconButton(onClick = { menuOpen = true }) { // 点击打开菜单
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多操作") // 三点图标
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) { // 下拉菜单
                            // 导入：从用户选择的 JSON 文件加载规则到本地
                            DropdownMenuItem( // 导入菜单项
                                text = { Text("导入规则") }, // 文案
                                onClick = { menuOpen = false; onImport() }, // 关闭并触发导入
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) } // 文件图标
                            )
                            // 导出：将本地规则打包为 JSON 写入用户选择的文件
                            DropdownMenuItem( // 导出菜单项
                                text = { Text("导出规则") }, // 文案
                                onClick = { menuOpen = false; onExport() }, // 关闭并触发导出
                                leadingIcon = { Icon(Icons.Filled.CloudUpload, null) } // 上传图标
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = { // 悬浮按钮
            // FAB 随当前 Tab 切换：本地 Tab 显示新增，订阅 Tab 显示同步，内置 Tab 无 FAB
            when (tabIndex) { // 按 Tab 显示不同 FAB
                0 -> FloatingActionButton(onClick = onAddNew) { // 新增按钮
                    Icon(Icons.Filled.Add, contentDescription = "新增规则") // 加号图标
                }
                1 -> FloatingActionButton(onClick = onSyncSubscribed) { // 同步按钮
                    Icon(Icons.Filled.CloudDownload, contentDescription = "同步订阅") // 云下载图标
                }
            }
        }
    ) { inner -> // 内容区，inner 为顶部栏占位
        Column(Modifier.fillMaxSize().padding(inner)) { // 纵向容器消化内边距
            TabRow(selectedTabIndex = tabIndex) { // Tab 容器
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, // 本地 Tab
                    text = { Text("本地 (${local.size})") }) // 文案带数量
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, // 订阅 Tab
                    text = { Text("订阅 (${subscribed.size})") }) // 文案带数量
                Tab(selected = tabIndex == 2, onClick = { tabIndex = 2 }, // 内置 Tab
                    text = { Text("内置 (${builtIn.size})") }) // 文案带数量
            }

            when (tabIndex) { // 按 Tab 显示不同列表
                0 -> RuleListContent( // 本地规则列表
                    rules = local, // 规则
                    disabledIds = disabledIds, // 禁用集合
                    emptyHint = "暂无本地规则，点右下角 + 新建", // 空态提示
                    onToggle = { r, on -> scope.launch { settings.setRuleEnabled(r.id, on) } }, // 开关切换
                    onClick = { onEditRule(it.id) }, // 点击编辑
                    onDelete = { deleteTarget = it } // 删除目标
                )
                1 -> RuleListContent( // 订阅规则列表
                    rules = subscribed, // 规则
                    disabledIds = disabledIds, // 禁用集合
                    emptyHint = "暂无订阅规则，点右下角同步按钮拉取", // 空态提示
                    onToggle = { r, on -> scope.launch { settings.setRuleEnabled(r.id, on) } }, // 开关切换
                    onClick = { onEditRule(it.id) }, // 点击编辑
                    onDelete = null // 订阅不允许删除
                )
                2 -> BuiltInContent( // 内置规则列表
                    files = builtInFiles, // 分类文件
                    rules = builtIn, // 内置规则
                    disabledIds = disabledIds, // 禁用集合
                    onToggle = { r, on -> scope.launch { settings.setRuleEnabled(r.id, on) } }, // 开关切换
                    onPreview = { name -> preview = name to onPreviewBuiltIn(name) } // 触发预览
                )
            }
        }
    }

    // 删除确认对话框：先清空 deleteTarget 再执行删除，避免重组期间状态丢失
    deleteTarget?.let { rule -> // 待删除规则非空时显示对话框
        AlertDialog( // 对话框
            onDismissRequest = { deleteTarget = null }, // 关闭即清空
            title = { Text("删除本地规则？") }, // 标题
            text = { Text("${rule.name}\n${rule.id}") }, // 内容显示名称与 ID
            confirmButton = { // 确认按钮
                OutlinedButton(onClick = { // 点击删除
                    val target = rule // 暂存目标
                    deleteTarget = null // 先清空状态
                    scope.launch { repo.deleteLocalRule(target.id) } // 异步删除
                }) { Text("删除") } // 按钮文案
            },
            dismissButton = { // 取消按钮
                OutlinedButton(onClick = { deleteTarget = null }) { Text("取消") } // 关闭对话框
            }
        )
    }

    // 内置规则文件预览弹窗：展示选中的内置规则文件 JSON 原文
    preview?.let { (name, content) -> // 有预览目标时显示
        AlertDialog( // 对话框
            onDismissRequest = { preview = null }, // 关闭即清空
            title = { Text(name) }, // 标题为文件名
            text = { // 内容区
                Surface(Modifier.height(360.dp)) { // 限定高度
                    Text(content ?: "(读取失败)", fontSize = 11.sp, modifier = Modifier.padding(8.dp)) // 显示内容或失败提示
                }
            },
            confirmButton = { // 关闭按钮
                OutlinedButton(onClick = { preview = null }) { Text("关闭") } // 关闭
            }
        )
    }
}

/**
 * 规则列表内容：空态提示或卡片列表，供本地/订阅 Tab 共用。
 *
 * @param rules 待展示的规则列表。
 * @param disabledIds 已禁用规则 ID 集合，用于计算卡片开关状态。
 * @param emptyHint 列表为空时居中显示的提示文案。
 * @param onToggle 规则开关切换回调，参数为规则与目标布尔值。
 * @param onClick 点击卡片回调（跳转编辑），参数为规则。
 * @param onDelete 删除按钮回调；为 null 时不显示删除按钮（订阅 Tab）。
 */
@Composable // 标记为 Composable
private fun RuleListContent( // 规则列表内容
    rules: List<Rule>, // 规则列表
    disabledIds: Set<String>, // 禁用集合
    emptyHint: String, // 空态提示
    onToggle: (Rule, Boolean) -> Unit, // 开关切换
    onClick: (Rule) -> Unit, // 点击卡片
    onDelete: ((Rule) -> Unit)? // 删除回调，可为空
) {
    if (rules.isEmpty()) { // 列表为空
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { // 居中容器
            Text(emptyHint, color = MaterialTheme.colorScheme.outline, fontSize = 13.sp) // 提示文案
        }
        return // 直接返回
    }
    LazyColumn( // 懒加载列表
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), // 内边距
        verticalArrangement = Arrangement.spacedBy(8.dp) // 项间距
    ) {
        items(rules, key = { it.id }) { rule -> // 按规则 ID 作为 key
            RuleCard( // 单条规则卡片
                rule = rule, // 规则
                active = rule.enabled && rule.id !in disabledIds, // 是否激活
                onToggle = { on -> onToggle(rule, on) }, // 开关切换
                onClick = { onClick(rule) }, // 点击卡片
                onDelete = onDelete?.let { { it(rule) } } // 删除回调
            )
        }
    }
}

/**
 * 内置规则 Tab 内容：分类文件列表（可点击预览）+ 内置规则卡片列表。
 *
 * @param files 内置规则分类文件名列表。
 * @param rules 内置规则列表。
 * @param disabledIds 已禁用规则 ID 集合。
 * @param onToggle 规则开关切换回调。
 * @param onPreview 点击分类文件回调，参数为文件名，触发预览弹窗。
 */
@Composable // 标记为 Composable
private fun BuiltInContent( // 内置规则内容
    files: List<String>, // 文件名列表
    rules: List<Rule>, // 规则列表
    disabledIds: Set<String>, // 禁用集合
    onToggle: (Rule, Boolean) -> Unit, // 开关切换
    onPreview: (String) -> Unit // 预览回调
) {
    LazyColumn( // 懒加载列表
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), // 内边距
        verticalArrangement = Arrangement.spacedBy(8.dp) // 项间距
    ) {
        item { // 第一项标题
            Text("分类文件（共 ${files.size} 个）", fontSize = 12.sp, // 文案带数量
                fontWeight = FontWeight.SemiBold, // 半粗体
                color = MaterialTheme.colorScheme.primary, // 主色
                modifier = Modifier.padding(bottom = 2.dp)) // 底部间距
        }
        items(files, key = { it }) { name -> // 文件名列表
            Surface( // 卡片容器
                modifier = Modifier.fillMaxWidth().clickable { onPreview(name) }, // 点击触发预览
                shape = RoundedCornerShape(12.dp), // 圆角
                color = MaterialTheme.colorScheme.surface, // 背景色
                tonalElevation = 0.dp, // 无色调提升
                shadowElevation = 0.dp // 无阴影
            ) {
                Row( // 横向行
                    Modifier.fillMaxWidth().padding(12.dp), // 内边距
                    verticalAlignment = Alignment.CenterVertically // 垂直居中
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, // 文件夹图标
                        tint = MaterialTheme.colorScheme.primary) // 主色
                    Spacer(Modifier.width(12.dp)) // 横向间距
                    Text(name, fontSize = 13.sp) // 文件名文本
                }
            }
        }
        item { // 内置规则标题
            Text("内置规则（共 ${rules.size} 条）", fontSize = 12.sp, // 文案带数量
                fontWeight = FontWeight.SemiBold, // 半粗体
                color = MaterialTheme.colorScheme.primary, // 主色
                modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) // 上下间距
        }
        items(rules, key = { "builtin_" + it.id }) { rule -> // 加前缀避免与本地 key 冲突
            RuleCard( // 规则卡片
                rule = rule, // 规则
                active = rule.enabled && rule.id !in disabledIds, // 是否激活
                onToggle = { on -> onToggle(rule, on) }, // 开关切换
                onClick = null, // 内置规则不可点击编辑
                onDelete = null // 内置规则不可删除
            )
        }
    }
}

/**
 * 单条规则卡片：标题 + 来源徽章 + 包名/优先级 + 删除按钮 + 开关。
 *
 * @param rule 规则数据。
 * @param active 是否激活（enabled 且未在禁用集合中），决定开关选中态。
 * @param onToggle 开关切换回调。
 * @param onClick 点击卡片回调；为 null 时卡片不可点击（内置 Tab）。
 * @param onDelete 删除按钮回调；为 null 时不显示删除按钮。
 */
@Composable // 标记为 Composable
private fun RuleCard( // 单条规则卡片
    rule: Rule, // 规则
    active: Boolean, // 是否激活
    onToggle: (Boolean) -> Unit, // 开关切换
    onClick: (() -> Unit)?, // 点击回调，可为空
    onDelete: (() -> Unit)? // 删除回调，可为空
) {
    Surface( // 卡片容器
        modifier = Modifier.fillMaxWidth().then( // 占满宽度
            if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier // 可点击则附加
        ),
        shape = RoundedCornerShape(12.dp), // 圆角
        color = MaterialTheme.colorScheme.surface // 背景色
    ) {
        Row( // 横向布局
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), // 内边距
            verticalAlignment = Alignment.CenterVertically // 垂直居中
        ) {
            Column(Modifier.weight(1f)) { // 左侧信息列占满
                Row(verticalAlignment = Alignment.CenterVertically) { // 标题行
                    Text( // 规则名
                        rule.name, // 名称
                        fontSize = 14.sp, // 字号
                        fontWeight = FontWeight.SemiBold, // 半粗体
                        modifier = Modifier.weight(1f, fill = false) // 不强制填满
                    )
                    Spacer(Modifier.width(6.dp)) // 间距
                    SourceBadge(rule.source) // 来源徽章
                }
                Spacer(Modifier.height(4.dp)) // 间距
                Row( // 副信息行
                    Modifier.fillMaxWidth(), // 占满宽度
                    horizontalArrangement = Arrangement.spacedBy(10.dp) // 间距
                ) {
                    Text( // 包名
                        rule.packageName.ifEmpty { "(通用兜底)" }, // 空包名显示兜底
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
                        modifier = Modifier.weight(1f, fill = false) // 不强制填满
                    )
                    Text( // 优先级
                        "优先级：${rule.priority}", // 文案带值
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                    )
                }
            }
            if (onDelete != null) { // 有删除回调
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { // 删除按钮
                    Icon( // 删除图标
                        Icons.Filled.Delete, // 删除图标
                        contentDescription = "删除", // 无障碍描述
                        tint = MaterialTheme.colorScheme.outline, // 灰色
                        modifier = Modifier.size(18.dp) // 图标尺寸
                    )
                }
            }
            Switch(checked = active, onCheckedChange = onToggle) // 启用开关
        }
    }
}

/**
 * 规则来源徽章：本地/订阅/内置三种来源对应不同底色与文案。
 *
 * @param source 规则来源枚举。
 */
@Composable // 标记为 Composable
private fun SourceBadge(source: RuleSource) { // 来源徽章
    val dark = isSystemInDarkTheme() // 判断当前是否深色模式
    // 按来源取背景/前景色，订阅徽章在深色模式使用暗色变体
    val (bg, fg) = when (source) { // 解构背景与前景色
        RuleSource.LOCAL -> MaterialTheme.colorScheme.primaryContainer to // 本地：主容器色
                MaterialTheme.colorScheme.onPrimaryContainer // 主容器前景色
        RuleSource.SUBSCRIBED -> (if (dark) DarkPurpleBadgeBg else PurpleBadgeBg) to (if (dark) DarkAccentPurple else AccentPurple) // 订阅：紫色徽章（深色适配）
        RuleSource.BUILT_IN -> MaterialTheme.colorScheme.surfaceVariant to // 内置：surface 变体
                MaterialTheme.colorScheme.onSurfaceVariant // surface 变体前景
    }
    // 来源对应中文文案
    val label = when (source) { // 取中文标签
        RuleSource.LOCAL -> "本地" // 本地
        RuleSource.SUBSCRIBED -> "订阅" // 订阅
        RuleSource.BUILT_IN -> "内置" // 内置
    }
    Text( // 徽章文本
        label, // 文案
        fontSize = 9.sp, // 极小字号
        color = fg, // 前景色
        modifier = Modifier // 修饰符链
            .background(bg, RoundedCornerShape(8.dp)) // 圆角背景
            .padding(horizontal = 7.dp, vertical = 2.dp) // 内边距
    )
}
