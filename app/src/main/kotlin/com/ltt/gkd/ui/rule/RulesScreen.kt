package com.ltt.gkd.ui.rule // 声明包名，对应规则界面目录

import android.widget.Toast // 导入 Toast，同步成功/失败提示
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.clickable // 导入点击修饰符
import androidx.compose.foundation.rememberScrollState // 导入滚动状态（订阅源列表）
import androidx.compose.foundation.verticalScroll // 导入纵向滚动修饰符（订阅源列表）
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Box // 导入 Box 容器
import androidx.compose.foundation.layout.Column // 导入纵向容器
import androidx.compose.foundation.layout.Row // 导入横向容器
import androidx.compose.foundation.layout.Spacer // 导入占位
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度
import androidx.compose.foundation.layout.height // 导入高度
import androidx.compose.foundation.layout.heightIn // 导入高度约束（订阅源列表限高）
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
import androidx.compose.material.icons.filled.ContentPaste // 导入粘贴图标（粘贴 JSON 导入）
import androidx.compose.material.icons.filled.Delete // 导入删除图标
import androidx.compose.material.icons.filled.Folder // 导入文件夹图标
import androidx.compose.material.icons.filled.MoreVert // 导入三点菜单图标
import androidx.compose.material.icons.filled.Person // 导入人物图标（作者展示）
import androidx.compose.material.icons.filled.Refresh // 导入刷新图标（单源同步）
import androidx.compose.material.icons.filled.Star // 导入星标图标（已分享标记）
import androidx.compose.material.icons.filled.Whatshot // 导入火焰图标（热门规则）
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.DropdownMenu // 导入下拉菜单
import androidx.compose.material3.DropdownMenuItem // 导入下拉菜单项
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 Material3 API
import androidx.compose.material3.FloatingActionButton // 导入悬浮按钮
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedTextField // 导入描边文本框（粘贴导入用）
import androidx.compose.material3.OutlinedButton // 导入描边按钮
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Surface // 导入 Surface 容器
import androidx.compose.material3.Switch // 导入开关组件
import androidx.compose.material3.Tab // 导入 Tab 项
import androidx.compose.material3.TabRow // 导入 Tab 容器
import androidx.compose.material3.Text // 导入文本组件
import androidx.compose.material3.TextButton // 导入文本按钮（全部同步）
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
import androidx.compose.ui.platform.LocalContext // 导入本地上下文，用于显示 Toast
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.text.style.TextOverflow // 导入文本溢出省略（订阅源链接）
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.compose.ui.unit.sp // 导入 sp 单位
import com.ltt.gkd.data.prefs.SettingsStore // 导入设置存储
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleGroup // 导入规则合集数据类（内置分组）
import com.ltt.gkd.data.rule.RuleRepository // 导入规则仓库
import com.ltt.gkd.data.rule.RuleSource // 导入规则来源枚举
import com.ltt.gkd.data.subscription.SourceType // 导入订阅源类型枚举
import com.ltt.gkd.data.subscription.SubscriptionSource // 导入订阅源数据类
import com.ltt.gkd.data.subscription.SubscriptionStore // 导入订阅源存储
import com.ltt.gkd.data.subscription.SubscriptionSyncer // 导入订阅同步器
import com.ltt.gkd.data.subscription.SubscriptionUrls // 导入订阅链接识别工具
import com.ltt.gkd.ui.theme.AccentPurple // 导入主题强调紫色（亮色）
import com.ltt.gkd.ui.theme.PurpleBadgeBg // 导入主题紫色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkAccentPurple // 导入深色模式紫色前景
import com.ltt.gkd.ui.theme.DarkPurpleBadgeBg // 导入深色紫色徽章背景
import com.ltt.gkd.ui.theme.AccentAmber // 导入主题强调琥珀色（亮色，热门徽章）
import com.ltt.gkd.ui.theme.AmberBadgeBg // 导入琥珀色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkAccentAmber // 导入深色模式琥珀色前景
import com.ltt.gkd.ui.theme.DarkAmberBadgeBg // 导入深色琥珀色徽章背景
import com.ltt.gkd.ui.theme.AccentBlue // 导入主题强调蓝色（亮色，已分享徽章）
import com.ltt.gkd.ui.theme.BlueBadgeBg // 导入蓝色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkAccentBlue // 导入深色模式蓝色前景
import com.ltt.gkd.ui.theme.DarkBlueBadgeBg // 导入深色蓝色徽章背景
import androidx.compose.foundation.isSystemInDarkTheme // 导入深色主题判断函数
import kotlinx.coroutines.launch // 导入协程启动

/**
 * 规则管理（UI v5 ③）：本地/订阅/内置三 Tab + 卡片开关 + 三点菜单 + FAB。
 *
 * @param repo 规则仓库，提供本地/订阅/内置规则流及增删读取能力。
 * @param settings 设置存储，用于读写规则启用/禁用集合。
 * @param onAddNew 点击"新增规则"FAB 回调，跳转规则编辑页新建。
 * @param onEditRule 点击规则卡片回调，参数为规则 ID，跳转编辑页修改。
 * @param subscriptions 订阅源存储（多源），订阅 Tab 的源管理（增删/开关/同步）依赖它。
 * @param onImport 选择导入文件回调，由调用方启动文件选择器。
 * @param onImportFromText 从粘贴的 RuleSet JSON 文本导入规则的回调。
 * @param onExport 选择导出文件回调，由调用方启动文件创建器。
 * @param onPreviewBuiltIn 预览内置规则文件回调，参数为文件名，返回文件内容或 null。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 Material3 API
@Composable // 标记为 Composable
fun RulesScreen( // 规则管理主组件
    repo: RuleRepository, // 规则仓库
    settings: SettingsStore, // 设置存储
    subscriptions: SubscriptionStore, // 订阅源存储（多源）
    onAddNew: () -> Unit, // 新增规则回调
    onEditRule: (String) -> Unit, // 编辑规则回调
    onImport: () -> Unit, // 导入回调
    onImportFromText: (String) -> Unit = {}, // 粘贴 JSON 代码导入回调
    onExport: () -> Unit, // 导出回调
    onPreviewBuiltIn: (String) -> String? // 预览内置规则回调
) {
    val scope = rememberCoroutineScope() // 协程作用域
    val context = LocalContext.current // 本地上下文，用于同步结果 Toast 提示
    val local by repo.localRules.collectAsState() // 本地规则列表
    val subscribed by repo.subscribedRules.collectAsState() // 订阅规则列表（已安装应用 + 通用兜底）
    val subscribedInactive by repo.subscribedInactive.collectAsState() // 未安装应用订阅规则列表（折叠展示）
    val builtIn by repo.builtInRules.collectAsState() // 内置规则列表
    val builtInGroups by repo.builtInGroups.collectAsState() // 内置规则合集分组（按合集展示）
    val disabledIds by settings.disabledRuleIds.collectAsState(initial = emptySet()) // 已禁用 ID 集合
    val sources by subscriptions.sources.collectAsState(initial = emptyList()) // 订阅源列表（多源）
    // 订阅规则的 id -> 使用量映射：本地"已分享"规则据此联动显示社区使用量（同步后生效）
    val subscribedUsageById = remember(subscribed) { subscribed.associate { it.id to it.subscribers } } // id→使用量
    val syncer = remember { SubscriptionSyncer() } // 订阅同步器（无状态，可复用）

    var tabIndex by remember { mutableStateOf(0) } // 当前 Tab 索引
    var menuOpen by remember { mutableStateOf(false) } // 三点菜单展开状态
    var deleteTarget by remember { mutableStateOf<Rule?>(null) } // 待删除规则
    var preview by remember { mutableStateOf<Pair<String, String?>?>(null) } // 内置规则预览内容
    var syncingAll by remember { mutableStateOf(false) } // 全部同步中状态
    var syncingId by remember { mutableStateOf<String?>(null) } // 单源同步中的源 ID
    var inactiveExpanded by remember { mutableStateOf(false) } // 未安装应用折叠区展开状态
    var addSourceOpen by remember { mutableStateOf(false) } // 添加订阅源对话框展开状态
    var addName by remember { mutableStateOf("") } // 添加订阅源：名称输入
    var addUrl by remember { mutableStateOf("") } // 添加订阅源：链接输入
    var pasteOpen by remember { mutableStateOf(false) } // 粘贴导入对话框展开状态
    var pasteText by remember { mutableStateOf("") } // 粘贴导入的 JSON 文本

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
                            // 粘贴导入：直接粘贴 RuleSet JSON 代码导入（与文件导入同格式）
                            DropdownMenuItem( // 粘贴导入菜单项
                                text = { Text("粘贴 JSON 导入") }, // 文案
                                onClick = { menuOpen = false; pasteText = ""; pasteOpen = true }, // 关闭菜单并打开粘贴对话框
                                leadingIcon = { Icon(Icons.Filled.ContentPaste, null) } // 粘贴图标
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
            // FAB 随当前 Tab 切换：本地 Tab 显示新增规则，订阅 Tab 显示添加订阅源，内置 Tab 无 FAB
            when (tabIndex) { // 按 Tab 显示不同 FAB
                0 -> FloatingActionButton(onClick = onAddNew) { // 新增按钮
                    Icon(Icons.Filled.Add, contentDescription = "新增规则") // 加号图标
                }
                1 -> FloatingActionButton( // 添加订阅源按钮
                    onClick = { // 点击添加
                        addName = "" // 清空名称
                        addUrl = "" // 清空链接
                        addSourceOpen = true // 打开添加对话框
                    }
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加订阅源") // 加号图标
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
                    onDelete = { deleteTarget = it }, // 删除目标
                    uploadedUsageById = subscribedUsageById // 已上传本地规则的社区使用量联动
                )
                1 -> Column(Modifier.fillMaxSize()) { // 订阅 Tab：源管理 + 订阅规则列表
                    SubscriptionSourceManager( // 订阅源管理区
                        sources = sources, // 源列表
                        syncingAll = syncingAll, // 全部同步中
                        syncingId = syncingId, // 单源同步中
                        onSyncAll = { // 全部同步
                            if (!syncingAll && sources.any { it.enabled }) { // 有空闲且有启用源
                                syncingAll = true // 标记同步中
                                scope.launch { // 异步执行
                                    val (ok, total) = syncer.syncAll(sources.filter { it.enabled }, subscriptions, repo) // 同步所有启用源
                                    syncingAll = false // 解除
                                    val msg = when { // 按结果生成提示文案
                                        ok == total && total > 0 -> "全部同步成功：$ok 个源" // 全部成功
                                        ok == 0 -> "同步失败，请检查网络与订阅链接" // 全部失败
                                        else -> "同步完成：成功 $ok / $total 个源" // 部分成功
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() // 主线程 Toast 提示结果
                                }
                            }
                        },
                        onSyncSource = { s -> // 单源同步
                            if (syncingId == null) { // 空闲时
                                syncingId = s.id // 标记该源同步中
                                scope.launch { // 异步执行
                                    val r = syncer.syncAndPersist(s, subscriptions, repo) // 同步并持久化
                                    syncingId = null // 解除
                                    val msg = if (r.success) "同步成功：${r.ruleCount} 条规则" else "同步失败：${r.error}" // 结果文案
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() // 主线程 Toast 提示结果
                                }
                            }
                        },
                        onToggleSource = { s, on -> scope.launch { subscriptions.setEnabled(s.id, on) } }, // 启用/禁用源
                        onDeleteSource = { s -> // 删除源
                            scope.launch { // 异步执行
                                subscriptions.delete(s.id) // 删除源记录
                                repo.subscribedDirFile.listFiles { f -> f.name.startsWith(s.id + "__") } // 该源下载的文件
                                    ?.forEach { it.delete() } // 逐个删除
                                repo.reload() // 重新加载合并规则
                            }
                        }
                    )
                    Box(Modifier.weight(1f)) { // 订阅规则列表占据剩余空间
                        RuleListContent( // 订阅规则列表
                            rules = subscribed, // 规则
                            disabledIds = disabledIds, // 禁用集合
                            emptyHint = "暂无订阅规则，添加订阅源后自动同步", // 空态提示
                            onToggle = { r, on -> scope.launch { settings.setRuleEnabled(r.id, on) } }, // 开关切换
                            onClick = { onEditRule(it.id) }, // 点击编辑
                            onDelete = null, // 订阅不允许删除
                            showUsage = true, // 展示作者与使用量（来源于 Gist 订阅数）
                            usageHeader = "共 ${subscribed.size} 条 · 使用量（订阅数）${formatCount(subscribed.maxOfOrNull { it.subscribers } ?: 0)}" // 使用量=订阅源的订阅数
                        )
                    }
                    InactiveSubscribedSection( // 未安装应用折叠区
                        inactive = subscribedInactive, // 未安装规则列表
                        expanded = inactiveExpanded, // 展开状态
                        onToggleExpanded = { inactiveExpanded = !inactiveExpanded } // 切换展开
                    )
                }
                2 -> BuiltInContent( // 内置规则列表（按合集分组）
                    groups = builtInGroups, // 内置合集分组
                    disabledIds = disabledIds, // 禁用集合
                    onToggle = { r, on -> scope.launch { settings.setRuleEnabled(r.id, on) } }, // 单条开关切换
                    onToggleGroup = { group, on -> // 整个合集一键启用/禁用
                        scope.launch { settings.setRulesEnabled(group.rules.map { it.id }, on) } // 批量写入
                    },
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

    // 粘贴导入弹窗：直接粘贴 RuleSet JSON 代码导入（与文件导入同一格式）
    if (pasteOpen) { // 展开时显示
        AlertDialog( // 对话框
            onDismissRequest = { pasteOpen = false }, // 关闭
            title = { Text("粘贴 JSON 导入") }, // 标题
            text = { // 内容区
                Column { // 纵向容器
                    Text( // 说明文案
                        "粘贴导出/分享的 RuleSet JSON 内容，与文件导入格式一致。", // 提示
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                    )
                    Spacer(Modifier.height(8.dp)) // 间距
                    OutlinedTextField( // 多行文本框
                        value = pasteText, // 绑定粘贴文本
                        onValueChange = { pasteText = it }, // 输入回调
                        placeholder = { Text("{\n  \"name\": ...,\n  \"rules\": [ ... ]\n}", fontSize = 11.sp) }, // 占位示例
                        modifier = Modifier.fillMaxWidth().height(200.dp), // 占满宽度并限定高度
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp) // 小字号便于查看 JSON
                    )
                }
            },
            confirmButton = { // 导入按钮
                OutlinedButton( // 描边按钮
                    onClick = { // 点击导入
                        onImportFromText(pasteText.trim()) // 触发粘贴导入
                        pasteOpen = false // 关闭对话框
                        pasteText = "" // 清空文本
                    },
                    enabled = pasteText.isNotBlank() // 非空才可点
                ) { Text("导入") } // 文案
            },
            dismissButton = { // 取消按钮
                OutlinedButton(onClick = { pasteOpen = false }) { Text("取消") } // 关闭对话框
            }
        )
    }

    // 添加订阅源弹窗：粘贴 Gist 链接/ID 或任意 RuleSet JSON 链接
    if (addSourceOpen) { // 展开时显示
        AlertDialog( // 对话框
            onDismissRequest = { addSourceOpen = false }, // 关闭
            title = { Text("添加订阅源") }, // 标题
            text = { // 内容区
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { // 纵向容器
                    OutlinedTextField( // 名称输入框
                        value = addName, // 绑定名称
                        onValueChange = { addName = it }, // 输入回调
                        label = { Text("名称（可选）") }, // 标签
                        placeholder = { Text("例如：我的规则") }, // 占位
                        modifier = Modifier.fillMaxWidth(), // 占满
                        singleLine = true // 单行
                    )
                    OutlinedTextField( // 链接输入框
                        value = addUrl, // 绑定链接
                        onValueChange = { addUrl = it }, // 输入回调
                        label = { Text("订阅链接") }, // 标签
                        placeholder = { Text("Gist 链接/ID，或任意 RuleSet JSON 链接") }, // 占位
                        modifier = Modifier.fillMaxWidth(), // 占满
                        singleLine = true // 单行
                    )
                }
            },
            confirmButton = { // 添加按钮
                OutlinedButton( // 描边按钮
                    onClick = { // 点击添加并立即同步
                        scope.launch { // 异步执行
                            val src = subscriptions.add(addName.trim(), addUrl.trim()) // 新增源（自动识别类型）
                            addSourceOpen = false // 关闭对话框
                            syncingId = src.id // 标记该源同步中
                            val r = syncer.syncAndPersist(src, subscriptions, repo) // 立即同步一次
                            syncingId = null // 解除
                            val msg = if (r.success) "添加成功，同步 ${r.ruleCount} 条规则" else "添加失败：${r.error}" // 结果文案
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() // 主线程 Toast 提示结果
                        }
                    },
                    enabled = SubscriptionUrls.isValid(addUrl) // 链接合法才可点
                ) { Text("添加并同步") } // 文案
            },
            dismissButton = { // 取消按钮
                OutlinedButton(onClick = { addSourceOpen = false }) { Text("取消") } // 关闭对话框
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
 * @param showUsage 是否在卡片上展示作者与使用量等社区数据（订阅 Tab 用）。
 * @param usageHeader 列表顶部的使用量汇总文案；为 null 时不显示汇总头。
 * @param uploadedUsageById 规则 ID → 社区使用量映射；供本地"已分享"规则联动展示（本地 Tab 用）。
 */
@Composable // 标记为 Composable
private fun RuleListContent( // 规则列表内容
    rules: List<Rule>, // 规则列表
    disabledIds: Set<String>, // 禁用集合
    emptyHint: String, // 空态提示
    onToggle: (Rule, Boolean) -> Unit, // 开关切换
    onClick: (Rule) -> Unit, // 点击卡片
    onDelete: ((Rule) -> Unit)?, // 删除回调，可为空
    showUsage: Boolean = false, // 是否展示社区使用量数据
    usageHeader: String? = null, // 顶部使用量汇总文案
    uploadedUsageById: Map<String, Int> = emptyMap() // 已分享本地规则的使用量映射
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
        if (usageHeader != null) { // 有汇总头则作为首项渲染
            item(key = "usage_header") { // 汇总头项
                Text( // 汇总文案
                    usageHeader, // 文案
                    fontSize = 12.sp, // 字号
                    fontWeight = FontWeight.SemiBold, // 半粗体
                    color = MaterialTheme.colorScheme.primary, // 主色
                    modifier = Modifier.padding(bottom = 2.dp) // 底部间距
                )
            }
        }
        items(rules, key = { it.id }) { rule -> // 按规则 ID 作为 key
            RuleCard( // 单条规则卡片
                rule = rule, // 规则
                active = rule.enabled && rule.id !in disabledIds, // 是否激活
                onToggle = { on -> onToggle(rule, on) }, // 开关切换
                onClick = { onClick(rule) }, // 点击卡片
                onDelete = onDelete?.let { { it(rule) } }, // 删除回调
                showUsage = showUsage, // 是否展示使用量
                // 已分享的本地规则联动社区使用量：仅当 uploaded 为真时取值，否则 null
                uploadedUsage = if (rule.uploaded) uploadedUsageById[rule.id] else null // 已分享规则的使用量
            )
        }
    }
}

/**
 * 内置规则 Tab 内容：按"合集"分组展示，每个合集可整体启用/禁用，也可切换单条规则。
 *
 * @param groups 内置规则合集分组（每个 [RuleGroup] 对应一个 assets/rules 文件）。
 * @param disabledIds 已禁用规则 ID 集合。
 * @param onToggle 单条规则开关切换回调。
 * @param onToggleGroup 整个合集一键启用/禁用回调。
 * @param onPreview 点击合集预览按钮回调，参数为合集来源文件名，触发原文预览弹窗。
 */
@Composable // 标记为 Composable
private fun BuiltInContent( // 内置规则内容（按合集分组）
    groups: List<RuleGroup>, // 合集分组列表
    disabledIds: Set<String>, // 禁用集合
    onToggle: (Rule, Boolean) -> Unit, // 单条开关切换
    onToggleGroup: (RuleGroup, Boolean) -> Unit, // 整组开关切换
    onPreview: (String) -> Unit // 预览回调
) {
    if (groups.isEmpty()) { // 无合集
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { // 居中容器
            Text("暂无内置规则合集", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp) // 提示文案
        }
        return // 直接返回
    }
    LazyColumn( // 懒加载列表
        Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp), // 内边距
        verticalArrangement = Arrangement.spacedBy(12.dp) // 合集间距
    ) {
        items(groups, key = { it.fileName }) { group -> // 每个合集一项
            val total = group.rules.size // 合集规则总数
            val enabledCount = group.rules.count { it.id !in disabledIds } // 已启用数量
            val allOn = enabledCount == total // 是否全部启用
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { // 合集纵向容器
                Surface( // 合集头部卡片
                    modifier = Modifier.fillMaxWidth(), // 占满宽度
                    shape = RoundedCornerShape(12.dp), // 圆角
                    color = MaterialTheme.colorScheme.surfaceVariant // 头部底色（区别于规则卡片）
                ) {
                    Row( // 头部横向布局
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), // 内边距
                        verticalAlignment = Alignment.CenterVertically // 垂直居中
                    ) {
                        Icon( // 合集图标
                            Icons.Filled.Folder, contentDescription = null, // 文件夹图标
                            tint = MaterialTheme.colorScheme.primary, // 主色
                            modifier = Modifier.size(20.dp) // 图标尺寸
                        )
                        Spacer(Modifier.width(10.dp)) // 间距
                        Column(Modifier.weight(1f)) { // 名称与计数列
                            Text( // 合集名
                                group.name, // 名称
                                fontSize = 14.sp, // 字号
                                fontWeight = FontWeight.SemiBold // 半粗体
                            )
                            Text( // 启用计数
                                "已启用 $enabledCount / $total 条", // 文案
                                fontSize = 11.sp, // 字号
                                color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                            )
                        }
                        IconButton( // 预览原文按钮
                            onClick = { onPreview(group.fileName) }, // 触发预览
                            modifier = Modifier.size(32.dp) // 按钮尺寸
                        ) {
                            Icon( // 文档图标
                                Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "预览原文", // 无障碍描述
                                tint = MaterialTheme.colorScheme.outline, // 灰色
                                modifier = Modifier.size(18.dp) // 图标尺寸
                            )
                        }
                        Switch( // 整组开关
                            checked = allOn, // 全部启用时选中
                            onCheckedChange = { onToggleGroup(group, it) } // 一键启用/禁用整个合集
                        )
                    }
                }
                group.rules.forEach { rule -> // 合集内每条规则
                    RuleCard( // 规则卡片
                        rule = rule, // 规则
                        active = rule.enabled && rule.id !in disabledIds, // 是否激活
                        onToggle = { on -> onToggle(rule, on) }, // 单条开关切换
                        onClick = null, // 内置规则不可点击编辑
                        onDelete = null // 内置规则不可删除
                    )
                }
            }
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
 * @param showUsage 是否展示社区数据（作者、使用量、热门徽章）。
 * @param uploadedUsage 本地"已分享"规则从订阅同步回来的社区使用量；null 表示尚未同步到。
 */
@Composable // 标记为 Composable
private fun RuleCard( // 单条规则卡片
    rule: Rule, // 规则
    active: Boolean, // 是否激活
    onToggle: (Boolean) -> Unit, // 开关切换
    onClick: (() -> Unit)?, // 点击回调，可为空
    onDelete: (() -> Unit)?, // 删除回调，可为空
    showUsage: Boolean = false, // 是否展示社区使用量数据
    uploadedUsage: Int? = null // 已分享本地规则同步回来的使用量
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
                    // 已分享徽章：本地规则被上传到社区时展示的特殊标记
                    if (rule.uploaded) { // 该本地规则已分享
                        UploadedBadge() // "已分享"徽章
                        Spacer(Modifier.width(4.dp)) // 与其它徽章间距
                    }
                    // 热门徽章：使用量达到阈值时展示，排在来源徽章之前
                    if (showUsage && rule.subscribers >= HOT_SUBSCRIBER_THRESHOLD) { // 达到热门阈值
                        HotBadge() // 热门徽章
                        Spacer(Modifier.width(4.dp)) // 与来源徽章间距
                    }
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
                if (rule.uploaded) { // 已分享本地规则：展示与订阅同步回来的使用量（Gist 订阅数）
                    val dark = isSystemInDarkTheme() // 深色模式判断
                    val hasUsage = uploadedUsage != null && uploadedUsage > 0 // 是否已有使用量
                    val emphasis = if (dark) DarkAccentBlue else AccentBlue // 强调蓝（深色适配）
                    Spacer(Modifier.height(4.dp)) // 与上一行间距
                    Row( // 使用量行
                        verticalAlignment = Alignment.CenterVertically, // 垂直居中
                        horizontalArrangement = Arrangement.spacedBy(6.dp) // 间距
                    ) {
                        Icon( // 使用量图标
                            Icons.Filled.Whatshot, contentDescription = null, // 无障碍描述留空
                            tint = if (hasUsage) emphasis else MaterialTheme.colorScheme.outline, // 有使用量时蓝色强调
                            modifier = Modifier.size(12.dp) // 图标尺寸
                        )
                        Text( // 使用量文案（鼓励用户上传分享）
                            when { // 按同步状态分支
                                uploadedUsage == null -> "已分享，同步后显示使用量" // 尚未同步到使用量
                                uploadedUsage > 0 -> "已被 ${formatCount(uploadedUsage)} 人使用 · 感谢分享" // 已被他人使用
                                else -> "已分享，暂无使用量" // 已同步但使用量为 0
                            },
                            fontSize = 11.sp, // 字号
                            color = if (hasUsage) emphasis else MaterialTheme.colorScheme.onSurfaceVariant, // 有使用量时蓝色强调
                            maxLines = 1 // 单行
                        )
                    }
                }
                if (showUsage) { // 展示社区数据行：作者 + 使用量
                    Spacer(Modifier.height(4.dp)) // 与上一行间距
                    Row( // 作者/使用量行
                        verticalAlignment = Alignment.CenterVertically, // 垂直居中
                        horizontalArrangement = Arrangement.spacedBy(6.dp) // 间距
                    ) {
                        Icon( // 作者图标
                            Icons.Filled.Person, contentDescription = null, // 无障碍描述留空
                            tint = MaterialTheme.colorScheme.outline, // 灰色
                            modifier = Modifier.size(12.dp) // 图标尺寸
                        )
                        Text( // 作者名
                            rule.author.ifEmpty { "匿名" }, // 空作者显示匿名
                            fontSize = 11.sp, // 字号
                            color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
                            maxLines = 1, // 单行
                            modifier = Modifier.weight(1f, fill = false) // 不强制填满
                        )
                        Icon( // 使用量图标
                            Icons.Filled.Whatshot, contentDescription = null, // 无障碍描述留空
                            tint = MaterialTheme.colorScheme.outline, // 灰色
                            modifier = Modifier.size(12.dp) // 图标尺寸
                        )
                        Text( // 使用量文案
                            "使用 ${formatCount(rule.subscribers)}", // 格式化后的使用量
                            fontSize = 11.sp, // 字号
                            color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                        )
                    }
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

/** 热门阈值：使用量（订阅数）达到该值的规则展示"热门"徽章。 */
private const val HOT_SUBSCRIBER_THRESHOLD = 10 // 热门规则的使用量阈值

/**
 * 将使用量数字格式化为紧凑中文样式，避免卡片上出现过长数字。
 *
 * - >= 10000：显示为 "X.Y万"（保留一位小数，整万时省略小数）
 * - >= 1000：显示为 "X.Yk"（保留一位小数，整千时省略小数）
 * - 其余：原样显示
 *
 * @param n 使用量（订阅数）
 * @return 格式化后的字符串
 */
private fun formatCount(n: Int): String = when { // 按数量级分支
    n >= 10_000 -> { // 万级
        val v = n / 10_000.0 // 换算为万
        if (v >= 100) "${v.toInt()}万" else trimDecimal(v) + "万" // 大于 100 万取整，否则保留一位小数
    }
    n >= 1_000 -> { // 千级
        val v = n / 1_000.0 // 换算为千
        trimDecimal(v) + "k" // 保留一位小数
    }
    else -> n.toString() // 小于 1000 原样显示
}

/** 保留一位小数，若小数为 0 则只保留整数部分（如 2.0 -> "2"，2.5 -> "2.5"）。 */
private fun trimDecimal(v: Double): String { // 裁剪多余小数
    val rounded = kotlin.math.round(v * 10) / 10 // 四舍五入到一位小数
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString() // 整数值去掉小数
}

/**
 * 热门徽章：火焰图标 + "热门"文案，使用琥珀色（深色模式自动切换暗色变体）。
 */
@Composable // 标记为 Composable
private fun HotBadge() { // 热门徽章
    val dark = isSystemInDarkTheme() // 判断深色模式
    val bg = if (dark) DarkAmberBadgeBg else AmberBadgeBg // 背景色（深色适配）
    val fg = if (dark) DarkAccentAmber else AccentAmber // 前景色（深色适配）
    Row( // 横向布局
        verticalAlignment = Alignment.CenterVertically, // 垂直居中
        modifier = Modifier // 修饰符链
            .background(bg, RoundedCornerShape(8.dp)) // 圆角背景
            .padding(horizontal = 6.dp, vertical = 2.dp) // 内边距
    ) {
        Icon( // 火焰图标
            Icons.Filled.Whatshot, contentDescription = null, // 无障碍描述留空
            tint = fg, // 前景色
            modifier = Modifier.size(10.dp) // 图标尺寸
        )
        Spacer(Modifier.width(2.dp)) // 图标与文字间距
        Text( // 徽章文本
            "热门", // 文案
            fontSize = 9.sp, // 极小字号
            color = fg // 前景色
        )
    }
}

/**
 * 已分享徽章：星标图标 + "已分享"文案，使用蓝色（深色模式自动切换暗色变体）。
 *
 * 用于标记当前用户已上传到社区的本地规则，配合卡片上的使用量联动，
 * 让"上传分享"这件事有可见的正反馈。
 */
@Composable // 标记为 Composable
private fun UploadedBadge() { // 已分享徽章
    val dark = isSystemInDarkTheme() // 判断深色模式
    val bg = if (dark) DarkBlueBadgeBg else BlueBadgeBg // 背景色（深色适配）
    val fg = if (dark) DarkAccentBlue else AccentBlue // 前景色（深色适配）
    Row( // 横向布局
        verticalAlignment = Alignment.CenterVertically, // 垂直居中
        modifier = Modifier // 修饰符链
            .background(bg, RoundedCornerShape(8.dp)) // 圆角背景
            .padding(horizontal = 6.dp, vertical = 2.dp) // 内边距
    ) {
        Icon( // 星标图标
            Icons.Filled.Star, contentDescription = null, // 无障碍描述留空
            tint = fg, // 前景色
            modifier = Modifier.size(10.dp) // 图标尺寸
        )
        Spacer(Modifier.width(2.dp)) // 图标与文字间距
        Text( // 徽章文本
            "已分享", // 文案
            fontSize = 9.sp, // 极小字号
            color = fg // 前景色
        )
    }
}

/**
 * 订阅源管理区：列出所有订阅源，支持整源启用/禁用、单源同步、删除，以及"全部同步"。
 *
 * @param sources 订阅源列表。
 * @param syncingAll 是否正在"全部同步"。
 * @param syncingId 正在单独同步的源 ID（null 表示无）。
 * @param onSyncAll "全部同步"回调。
 * @param onSyncSource 单源同步回调。
 * @param onToggleSource 源启用/禁用回调。
 * @param onDeleteSource 删除源回调。
 */
@Composable // 标记为 Composable
private fun SubscriptionSourceManager( // 订阅源管理区
    sources: List<SubscriptionSource>, // 源列表
    syncingAll: Boolean, // 全部同步中
    syncingId: String?, // 单源同步中的 ID
    onSyncAll: () -> Unit, // 全部同步回调
    onSyncSource: (SubscriptionSource) -> Unit, // 单源同步回调
    onToggleSource: (SubscriptionSource, Boolean) -> Unit, // 启用/禁用回调
    onDeleteSource: (SubscriptionSource) -> Unit // 删除回调
) {
    val busy = syncingAll || syncingId != null // 是否有同步进行中
    Surface( // 卡片容器
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 10.dp), // 占满 + 内边距
        shape = RoundedCornerShape(12.dp), // 圆角
        color = MaterialTheme.colorScheme.surface // 背景色
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { // 内容列
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { // 头部行
                Icon( // 订阅图标
                    Icons.Filled.CloudDownload, contentDescription = null, // 云下载图标
                    tint = MaterialTheme.colorScheme.primary, // 主色
                    modifier = Modifier.size(18.dp) // 尺寸
                )
                Spacer(Modifier.width(8.dp)) // 间距
                Text( // 标题
                    "订阅源 (${sources.size})", // 文案带数量
                    fontSize = 13.sp, // 字号
                    fontWeight = FontWeight.SemiBold, // 半粗体
                    modifier = Modifier.weight(1f) // 占满剩余
                )
                TextButton( // 全部同步按钮
                    onClick = onSyncAll, // 点击回调
                    enabled = !busy && sources.any { it.enabled } // 空闲且有启用源才可点
                ) {
                    if (syncingAll) { // 同步中显示加载圈
                        androidx.compose.material3.CircularProgressIndicator( // 加载圈
                            modifier = Modifier.size(14.dp), strokeWidth = 2.dp) // 尺寸与线宽
                    } else { // 否则显示文案
                        Text("全部同步", fontSize = 12.sp) // 文案
                    }
                }
            }
            if (sources.isEmpty()) { // 无源
                Text( // 引导文案
                    "还没有订阅源，点右下角 + 添加（Gist 链接/ID 或任意 RuleSet JSON 链接）", // 提示
                    fontSize = 11.sp, // 字号
                    color = MaterialTheme.colorScheme.outline // 次要色
                )
            } else { // 有源：可滚动列表（限高，避免挤占规则列表空间）
                Column( // 源列表容器
                    Modifier.fillMaxWidth().heightIn(max = 240.dp).verticalScroll(rememberScrollState()), // 限高 + 滚动
                    verticalArrangement = Arrangement.spacedBy(6.dp) // 项间距
                ) {
                    sources.forEach { s -> // 遍历每个源
                        Row( // 源行
                            Modifier.fillMaxWidth() // 占满
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)) // 背景
                                .padding(horizontal = 10.dp, vertical = 6.dp), // 内边距
                            verticalAlignment = Alignment.CenterVertically // 垂直居中
                        ) {
                            Column(Modifier.weight(1f)) { // 文本列
                                Row(verticalAlignment = Alignment.CenterVertically) { // 名称 + 类型徽章
                                    Text( // 源名称
                                        s.name, // 名称
                                        fontSize = 13.sp, // 字号
                                        fontWeight = FontWeight.SemiBold, // 半粗体
                                        maxLines = 1, // 单行
                                        modifier = Modifier.weight(1f, fill = false) // 不强制填满
                                    )
                                    Spacer(Modifier.width(6.dp)) // 间距
                                    Text( // 类型徽章
                                        if (s.type == SourceType.GIST) "Gist" else "链接", // 类型文案
                                        fontSize = 9.sp, // 极小字号
                                        color = MaterialTheme.colorScheme.primary, // 主色
                                        modifier = Modifier.background( // 圆角背景
                                            MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)
                                        ).padding(horizontal = 5.dp, vertical = 1.dp) // 内边距
                                    )
                                }
                                Text( // 链接
                                    s.url, // URL
                                    fontSize = 10.sp, // 字号
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
                                    maxLines = 1, // 单行
                                    overflow = TextOverflow.Ellipsis // 省略号
                                )
                                Text( // 同步状态
                                    when { // 按状态分支
                                        s.lastError.isNotEmpty() -> "同步失败：${s.lastError}" // 失败信息
                                        s.lastSyncAt > 0L -> "已同步 ${s.lastRuleCount} 条规则" // 成功信息
                                        else -> "尚未同步" // 未同步
                                    },
                                    fontSize = 10.sp, // 字号
                                    maxLines = 1, // 单行
                                    color = if (s.lastError.isNotEmpty()) MaterialTheme.colorScheme.error // 失败用错误色
                                    else MaterialTheme.colorScheme.onSurfaceVariant // 否则次要色
                                )
                            }
                            IconButton( // 单源同步按钮
                                onClick = { onSyncSource(s) }, // 点击同步
                                enabled = !busy, // 空闲才可点
                                modifier = Modifier.size(32.dp) // 按钮尺寸
                            ) {
                                if (syncingId == s.id) { // 该源同步中
                                    androidx.compose.material3.CircularProgressIndicator( // 加载圈
                                        modifier = Modifier.size(14.dp), strokeWidth = 2.dp) // 尺寸与线宽
                                } else { // 否则刷新图标
                                    Icon( // 刷新图标
                                        Icons.Filled.Refresh, contentDescription = "同步", // 无障碍描述
                                        tint = MaterialTheme.colorScheme.outline, // 灰色
                                        modifier = Modifier.size(18.dp) // 图标尺寸
                                    )
                                }
                            }
                            IconButton( // 删除按钮
                                onClick = { onDeleteSource(s) }, // 点击删除
                                modifier = Modifier.size(32.dp) // 按钮尺寸
                            ) {
                                Icon( // 删除图标
                                    Icons.Filled.Delete, contentDescription = "删除", // 无障碍描述
                                    tint = MaterialTheme.colorScheme.outline, // 灰色
                                    modifier = Modifier.size(18.dp) // 图标尺寸
                                )
                            }
                            Switch( // 启用开关
                                checked = s.enabled, // 当前状态
                                onCheckedChange = { onToggleSource(s, it) } // 切换回调
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 未安装应用的订阅规则折叠区。
 *
 * 设计：规则库全量下发，但部分应用用户尚未安装。这些规则的 packageName 不在当前
 * 已安装应用列表中，暂不参与匹配；折叠在此统一展示，等用户安装对应应用后，
 * 下次进入规则页重新 reload 会自动把这些规则移入"已安装"列表并生效。
 *
 * @param inactive 未安装应用的订阅规则列表。
 * @param expanded 折叠区是否展开。
 * @param onToggleExpanded 点击标题行切换展开状态的回调。
 */
@Composable // 标记为 Composable
private fun InactiveSubscribedSection( // 未安装应用折叠区
    inactive: List<Rule>, // 未安装规则列表
    expanded: Boolean, // 展开状态
    onToggleExpanded: () -> Unit // 切换展开回调
) {
    if (inactive.isEmpty()) return // 无未安装规则则不显示
    Surface( // 折叠卡片容器
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), // 外边距
        shape = RoundedCornerShape(10.dp), // 圆角
        color = MaterialTheme.colorScheme.surfaceVariant // 浅灰底，区分于已安装规则
    ) {
        Column { // 纵向布局
            Row( // 标题行（可点击展开）
                Modifier.fillMaxWidth().clickable(onClick = onToggleExpanded).padding(horizontal = 12.dp, vertical = 10.dp), // 占满、可点击、内边距
                verticalAlignment = Alignment.CenterVertically // 垂直居中
            ) {
                Text( // 标题文字
                    "未安装应用（${inactive.size} 条）", // 文案
                    fontSize = 12.sp, // 字号
                    fontWeight = FontWeight.Medium, // 中等粗细
                    color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
                    modifier = Modifier.weight(1f) // 占满剩余宽度
                )
                Text( // 展开箭头
                    if (expanded) "▾" else "▸", // 展开/收起箭头字符
                    color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                )
            }
            if (expanded) { // 展开时显示列表
                inactive.forEach { rule -> // 遍历每条未安装规则
                    Text( // 规则行（只读灰显，不可操作）
                        "${rule.name} · ${rule.packageName}", // 应用名 + 包名
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色（灰显）
                        maxLines = 1, // 单行
                        overflow = TextOverflow.Ellipsis, // 超出省略号
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp) // 内边距
                    )
                }
            }
        }
    }
}
