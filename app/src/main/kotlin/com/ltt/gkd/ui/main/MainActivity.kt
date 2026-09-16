package com.ltt.gkd.ui.main // 声明包名，对应主界面目录

import android.content.Intent // 导入 Intent，用于跳转 Activity
import android.net.Uri // 导入 Uri，用于文件选择返回的 URI
import android.os.Bundle // 导入 Bundle，用于保存/恢复状态
import android.provider.Settings // 导入系统 Settings，用于跳转无障碍设置页
import android.view.accessibility.AccessibilityManager // 导入无障碍管理器，检测服务启用状态
import android.widget.Toast // 导入 Toast，用于底部短提示
import androidx.activity.ComponentActivity // 导入 ComponentActivity，Jetpack Activity 基类
import androidx.activity.compose.BackHandler // 导入 BackHandler，拦截系统返回键
import androidx.activity.compose.rememberLauncherForActivityResult // 导入 Compose 中启动 Activity 结果的辅助函数
import androidx.activity.compose.setContent // 导入 setContent，挂载 Compose 树
import androidx.activity.result.contract.ActivityResultContracts // 导入标准 Activity 结果契约
import androidx.compose.foundation.layout.padding // 导入 padding 修饰符
import androidx.compose.material.icons.Icons // 导入 Material 图标集合
import androidx.compose.material.icons.automirrored.filled.List // 导入自动镜像的列表图标
import androidx.compose.material.icons.filled.Home // 导入首页图标
import androidx.compose.material.icons.filled.Settings // 导入设置图标
import androidx.compose.material3.Icon // 导入 Icon 组件
import androidx.compose.material3.MaterialTheme // 导入 MaterialTheme，访问颜色/字体/形状
import androidx.compose.material3.NavigationBar // 导入底部导航栏组件
import androidx.compose.material3.NavigationBarItem // 导入导航栏单项
import androidx.compose.material3.NavigationBarItemDefaults // 导入导航栏默认样式
import androidx.compose.material3.Scaffold // 导入 Scaffold，提供页面骨架
import androidx.compose.material3.Text // 导入 Text 组件
import androidx.compose.runtime.DisposableEffect // 导入 DisposableEffect，生命周期相关副作用
import androidx.compose.runtime.LaunchedEffect // 导入 LaunchedEffect，挂载时启动协程
import androidx.compose.runtime.collectAsState // 导入 collectAsState，把 Flow 收集为 Compose 状态
import androidx.compose.runtime.getValue // 导入 getValue 操作符重载，支持 by 委托
import androidx.compose.runtime.mutableIntStateOf // 导入可变 Int 状态
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember，跨重组保留状态
import androidx.compose.runtime.setValue // 导入 setValue 操作符重载
import androidx.compose.ui.Modifier // 导入 Modifier 修饰符
import androidx.lifecycle.Lifecycle // 导入生命周期枚举
import androidx.lifecycle.LifecycleEventObserver // 导入生命周期事件观察者
import androidx.lifecycle.compose.LocalLifecycleOwner // 导入当前生命周期所有者
import com.ltt.gkd.App // 导入应用入口类
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleRepository // 导入规则仓库
import com.ltt.gkd.data.rule.RuleSet // 导入规则集（序列化/反序列化用）
import com.ltt.gkd.data.subscription.SubscriptionScheduler // 导入订阅自动更新调度器
import com.ltt.gkd.service.SkipAccessibilityService // 导入跳过无障碍服务
import com.ltt.gkd.ui.app.AppListActivity // 导入应用列表 Activity
import com.ltt.gkd.ui.history.HistoryScreen // 导入跳过记录页 Composable
import com.ltt.gkd.ui.log.LogViewerActivity // 导入日志查看 Activity
import com.ltt.gkd.ui.rule.RuleEditActivity // 导入规则编辑 Activity
import com.ltt.gkd.ui.rule.RulesScreen // 导入规则管理 Composable
import com.ltt.gkd.ui.settings.SettingsScreen // 导入设置页 Composable
import com.ltt.gkd.ui.theme.LTTGKDTheme // 导入应用主题
import com.ltt.gkd.util.globalAdapter // 导入全局 JSON 适配器
import com.ltt.gkd.util.launchSafe // 导入安全启动协程的辅助函数
import kotlinx.coroutines.flow.combine // 导入 combine，合并多个 Flow
import kotlinx.coroutines.flow.first // 导入 Flow.first，取首个值
import kotlinx.coroutines.launch // 导入协程 launch

/**
 * 应用主 Activity（单 Activity 架构）。
 *
 * 承载首页/规则/设置三个底部导航 Tab，以及跳过记录页的覆盖切换。
 * 通过 Compose 的 state 管理导航与页面切换，不引入 Navigation 组件，
 * 适合本应用体量小、页面简单的场景。无障碍服务状态在 ON_RESUME 时刷新。
 */
class MainActivity : ComponentActivity() { // 主 Activity，继承 ComponentActivity

    private val repo: RuleRepository get() = App.get().repo // 规则仓库，使用全局共享单例

    override fun onCreate(savedInstanceState: Bundle?) { // Activity 创建回调
        super.onCreate(savedInstanceState) // 调用父类 onCreate
        setContent { // 设置 Compose 内容
            LTTGKDTheme { // 应用主题包裹
                val app = App.get() // 获取全局应用实例

                // 服务运行状态（ON_RESUME 时刷新）
                // 检测逻辑：优先检查系统设置中无障碍服务是否启用，再检查服务实例是否运行
                // 这样可避免应用崩溃后服务重启期间误显示"已关闭"
                var serviceOn by remember { // 声明可变状态 serviceOn
                    mutableStateOf(isAccessibilityEnabled()) // 初始值用系统设置状态判断
                }
                val lifecycleOwner = LocalLifecycleOwner.current // 取当前生命周期所有者
                DisposableEffect(lifecycleOwner) { // 注册生命周期副作用
                    val observer = LifecycleEventObserver { _, e -> // 创建事件观察者
                        if (e == Lifecycle.Event.ON_RESUME) { // 当事件为 ON_RESUME
                            serviceOn = isAccessibilityEnabled() // 刷新服务运行状态
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer) // 注册观察者
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } // 离开时移除观察者
                }

                // 订阅自动更新排程：开关或间隔变化时重新排程（启动时也会执行一次，确保排程存在）
                LaunchedEffect(Unit) { // 组件挂载即开始观察
                    combine( // 合并自动更新开关与间隔
                        app.settings.subscriptionEnabled, // 开关 Flow
                        app.settings.subscriptionIntervalHours // 间隔 Flow
                    ) { enabled, hours -> enabled to hours } // 任一变化都触发
                        .collect { SubscriptionScheduler.reschedule(this@MainActivity) } // 重新排程/取消
                }

                var tab by remember { mutableIntStateOf(0) } // 当前选中的底部 Tab 索引
                var showHistory by remember { mutableStateOf(false) } // 是否覆盖显示跳过记录页

                // ---- 导入/导出文件选择 ----
                val exportLauncher = rememberLauncherForActivityResult( // 创建导出文件选择器
                    ActivityResultContracts.CreateDocument("application/json") // 契约：新建 JSON 文档
                ) { uri: Uri? -> // 选择结果回调
                    if (uri != null) exportRules(uri) // 非 null 则执行导出
                }
                val importLauncher = rememberLauncherForActivityResult( // 创建导入文件选择器
                    ActivityResultContracts.OpenDocument() // 契约：打开文档
                ) { uri: Uri? -> // 选择结果回调
                    if (uri != null) importRules(uri) // 非 null 则执行导入
                }

                if (showHistory) { // 显示跳过记录页
                    BackHandler { showHistory = false } // 拦截系统返回键/侧滑：关闭历史页回首页，而非退出 Activity
                    HistoryScreen( // 挂载跳过记录页
                        history = app.history, // 传入跳过历史存储
                        totalSkipFlow = app.settings.totalSkipCount, // 传入累计跳过流
                        onBack = { showHistory = false } // 返回即关闭跳过记录页
                    )
                } else { // 否则显示主框架
                    Scaffold( // 使用 Scaffold 搭建骨架
                        containerColor = MaterialTheme.colorScheme.surface, // 背景色用 surface
                        bottomBar = { // 底部导航栏
                            NavigationBar { // 导航栏容器
                                // Tab 0：首页（电源按钮 + 累计统计）
                                NavigationBarItem( // 首页 Tab
                                    selected = tab == 0, // 是否选中
                                    onClick = { tab = 0 }, // 点击切换
                                    icon = { Icon(Icons.Filled.Home, contentDescription = null) }, // 首页图标
                                    label = { Text("首页") }, // 标签文字
                                    colors = NavigationBarItemDefaults.colors( // 自定义颜色
                                        selectedIconColor = MaterialTheme.colorScheme.primary, // 选中图标色
                                        selectedTextColor = MaterialTheme.colorScheme.primary, // 选中文字色
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer // 指示色
                                    )
                                )
                                // Tab 1：规则（本地/订阅/内置管理）
                                NavigationBarItem( // 规则 Tab
                                    selected = tab == 1, // 是否选中
                                    onClick = { tab = 1 }, // 点击切换
                                    icon = { // 图标
                                        Icon(Icons.AutoMirrored.Filled.List, // 镜像列表图标
                                            contentDescription = null) // 无障碍描述留空
                                    },
                                    label = { Text("规则") }, // 标签文字
                                    colors = NavigationBarItemDefaults.colors( // 颜色
                                        selectedIconColor = MaterialTheme.colorScheme.primary, // 选中图标色
                                        selectedTextColor = MaterialTheme.colorScheme.primary, // 选中文字色
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer // 指示色
                                    )
                                )
                                // Tab 2：设置（基础/订阅/贡献者/关于）
                                NavigationBarItem( // 设置 Tab
                                    selected = tab == 2, // 是否选中
                                    onClick = { tab = 2 }, // 点击切换
                                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) }, // 设置图标
                                    label = { Text("设置") }, // 标签文字
                                    colors = NavigationBarItemDefaults.colors( // 颜色
                                        selectedIconColor = MaterialTheme.colorScheme.primary, // 选中图标色
                                        selectedTextColor = MaterialTheme.colorScheme.primary, // 选中文字色
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer // 指示色
                                    )
                                )
                            }
                        }
                    ) { inner -> // 内容区，inner 为底部栏占位
                        val totalSkip by app.settings.totalSkipCount // 收集累计跳过次数
                            .collectAsState(initial = 0) // 初始值为 0
                        androidx.compose.foundation.layout.Box(Modifier.padding(inner)) { // 包一层 Box 并消化内边距
                            when (tab) { // 按 Tab 切换页面
                                0 -> HomeScreen( // 首页
                                    serviceOn = serviceOn, // 服务运行状态
                                    totalSkip = totalSkip, // 累计跳过次数
                                    onToggleService = { // 点击电源按钮
                                        startActivity( // 跳转系统无障碍设置
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // 无障碍设置 Action
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 新任务栈标记
                                        )
                                    },
                                    onViewHistory = { showHistory = true }, // 查看历史按钮
                                    onResetTotal = { // 重置累计按钮
                                        app.appScope.launch { app.settings.resetTotalSkip() } // 异步重置
                                    }
                                )
                                1 -> RulesScreen( // 规则页
                                    repo = repo, // 规则仓库
                                    settings = app.settings, // 设置存储
                                    subscriptions = app.subscriptions, // 订阅源存储（多源）
                                    onAddNew = { RuleEditActivity.start(this@MainActivity) }, // 新增规则
                                    onEditRule = { id -> // 编辑规则回调
                                        RuleEditActivity.start(this@MainActivity, id) // 跳转编辑页
                                    },
                                    onImport = { // 导入回调
                                        importLauncher.launch(arrayOf("application/json", "text/plain")) // 启动选择器，仅 JSON/文本
                                    },
                                    onImportFromText = { json -> importRulesFromText(json) }, // 粘贴 JSON 代码导入
                                    onExport = { // 导出回调
                                        exportLauncher.launch("ltt_rules_export.json") // 启动创建文档
                                    },
                                    onPreviewBuiltIn = { name -> repo.readBuiltInRaw(name) } // 预览内置规则
                                )
                                2 -> SettingsScreen( // 设置页
                                    settings = app.settings, // 设置存储
                                    serviceOn = serviceOn, // 服务状态
                                    onOpenAccessibility = { // 开启无障碍
                                        startActivity( // 跳转无障碍设置
                                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) // 无障碍 Action
                                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 新任务栈
                                        )
                                    },
                                    onOpenLogs = { // 打开日志
                                        startActivity( // 跳转日志 Activity
                                            Intent(this@MainActivity, // 当前 Activity 作为上下文
                                                LogViewerActivity::class.java) // 目标类
                                        )
                                    },
                                    onOpenWhitelist = { // 打开白名单
                                        startActivity( // 跳转应用列表 Activity
                                            Intent(this@MainActivity, // 当前 Activity 上下文
                                                AppListActivity::class.java) // 目标类
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 导出本地规则到用户选择的文件。
     *
     * 将本地规则打包为 RuleSet 后序列化为 JSON，写入用户通过 SAF 选择的 URI。
     * 无本地规则时直接 Toast 提示。
     *
     * @param uri 用户通过 CreateDocument 选择的目标文件 URI。
     */
    private fun exportRules(uri: Uri) { // 导出规则到指定 URI
        launchSafe { // 安全启动协程
            val rules = repo.localRules.first() // 取本地规则首值（列表）
            if (rules.isEmpty()) { // 无本地规则
                toast("没有本地规则可导出"); return@launchSafe // 提示并中止
            }
            val rs = RuleSet(name = "小狐规则导出", rules = rules) // 打包为 RuleSet
            val json = globalAdapter<RuleSet>().toJson(rs) // 序列化为 JSON
            contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } // 写入目标文件
            toast("已导出 ${rules.size} 条规则") // 提示成功
        }
    }

    /**
     * 从用户选择的 JSON 文件导入规则。
     *
     * 读取文件内容 → 反序列化为 RuleSet → 逐条保存到本地仓库 → reload。
     * 格式错误或读取失败时 Toast 提示并中止。
     *
     * @param uri 用户通过 OpenDocument 选择的源文件 URI。
     */
    private fun importRules(uri: Uri) { // 从指定 URI 导入规则
        launchSafe { // 安全启动协程
            val json = contentResolver.openInputStream(uri)?.use { // 打开输入流
                it.bufferedReader().readText() // 读取全部文本
            } ?: return@launchSafe // 为空则中止
            importRulesFromJson(json) // 复用统一的 JSON 导入逻辑
        }
    }

    /**
     * 从粘贴的 JSON 代码导入规则（导入方式二：直接粘贴 RuleSet JSON 文本）。
     *
     * 与文件导入共用 [importRulesFromJson] 解析逻辑，格式即本应用导出/订阅所用的
     * `RuleSet` JSON，因此"文件"与"代码"两种来源天然兼容同一套序列化格式。
     *
     * @param json 用户粘贴的 RuleSet JSON 文本。
     */
    private fun importRulesFromText(json: String) { // 从粘贴文本导入规则
        launchSafe { importRulesFromJson(json) } // 安全启动协程并复用统一解析
    }

    /**
     * 解析 RuleSet JSON 并逐条保存到本地仓库（文件导入与粘贴导入共用）。
     *
     * 导入的规则统一置 `uploaded=false`：导入内容并非当前用户所分享，
     * 不应携带原作者的"已分享"标记，避免本地卡片误显示分享徽章与使用量联动。
     *
     * @param json RuleSet JSON 文本；解析失败时 Toast 提示并中止。
     */
    private suspend fun importRulesFromJson(json: String) { // 统一的 JSON 导入解析逻辑
        val rs = runCatching { globalAdapter<RuleSet>().fromJson(json) }.getOrNull() // 尝试反序列化为 RuleSet
        if (rs == null) { // 解析失败
            toast("格式错误，导入失败（需为 RuleSet JSON）"); return // 提示并中止
        }
        var imported = 0 // 已导入计数
        rs.rules.forEach { rule: Rule -> // 遍历每条规则
            if (repo.saveLocalRule(rule.copy(uploaded = false))) imported++ // 保存（清除已分享标记）成功则计数
        }
        repo.reload() // 触发仓库重新加载
        toast("成功导入 $imported 条规则") // 提示结果
    }

    private fun toast(msg: String) { // Toast 辅助函数
        // launchSafe 协程运行在 Dispatchers.Default，Toast 必须在主线程调用
        runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() } // 切换到主线程显示 Toast
    }

    /**
     * 检测无障碍服务是否已启用。
     *
     * 检查系统设置中的无障碍开关状态 + 服务实例是否运行。
     * 优先看系统设置：如果设置中已启用，即使服务因崩溃短暂未重启也视为"已开启"，
     * 避免用户误以为需要重新去设置里开关一次。
     */
    private fun isAccessibilityEnabled(): Boolean { // 检测无障碍服务启用状态
        // 1. 检查服务实例是否正在运行
        if (SkipAccessibilityService.instance != null) return true // 服务实例存在，直接返回 true
        // 2. 通过系统设置字符串检测（兼容 OPPO 等 ROM）
        val enabled = android.provider.Settings.Secure.getString( // 读取安全设置
            contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES // 已启用无障碍服务键
        ) ?: "" // 为空时用空串
        // 检查字符串中是否包含本应用的无障碍服务组件名
        if (enabled.contains("$packageName/")) return true // 包含包名即视为启用
        // 3. 兜底：通过 AccessibilityManager 检测
        val am = getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager // 取无障碍管理器
        val enabledServices = am?.getEnabledAccessibilityServiceList( // 获取已启用的无障碍服务列表
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK // 所有反馈类型
        ) ?: emptyList() // 为空时返回空列表
        return enabledServices.any { info -> // 只要有一个匹配本应用包名即视为启用
            info.resolveInfo.serviceInfo.packageName == packageName // 匹配包名
        }
    }
}
