package com.ltt.gkd.ui.app // 声明包名

import android.content.ClipData // 导入剪贴板数据
import android.content.ClipboardManager // 导入剪贴板管理器
import android.content.Context // 导入 Context
import android.graphics.Bitmap // 导入位图
import android.graphics.Canvas // 导入画布
import android.graphics.drawable.BitmapDrawable // 导入位图 Drawable
import android.graphics.drawable.Drawable // 导入 Drawable 基类
import android.widget.Toast // 导入 Toast
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.clickable // 导入点击修饰符
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Box // 导入 Box
import androidx.compose.foundation.layout.Column // 导入纵向容器
import androidx.compose.foundation.layout.Row // 导入横向容器
import androidx.compose.foundation.layout.Spacer // 导入占位
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度
import androidx.compose.foundation.layout.height // 导入高度
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.layout.size // 导入尺寸
import androidx.compose.foundation.lazy.LazyColumn // 导入懒加载列表
import androidx.compose.foundation.lazy.items // 导入 items 函数
import androidx.compose.foundation.text.KeyboardOptions // 导入键盘选项
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.filled.Clear // 导入清空图标
import androidx.compose.material.icons.filled.ContentCopy // 导入复制图标
import androidx.compose.material.icons.filled.RestartAlt // 导入重置图标
import androidx.compose.material.icons.filled.Search // 导入搜索图标
import androidx.compose.material3.CircularProgressIndicator // 导入加载指示器
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API
import androidx.compose.material3.FilterChip // 导入筛选 Chip
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedTextField // 导入描边文本框
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Surface // 导入 Surface
import androidx.compose.material3.Switch // 导入开关
import androidx.compose.material3.Text // 导入文本
import androidx.compose.material3.TopAppBar // 导入顶部栏
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.TextButton // 导入文本按钮
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.LaunchedEffect // 导入一次性副作用
import androidx.compose.runtime.collectAsState // 导入 collectAsState
import androidx.compose.runtime.getValue // 导入 getValue
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember
import androidx.compose.runtime.rememberCoroutineScope // 导入协程作用域
import androidx.compose.runtime.setValue // 导入 setValue
import androidx.compose.ui.Alignment // 导入对齐
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.graphics.asImageBitmap // 导入 Bitmap 转 ImageBitmap
import androidx.compose.ui.platform.LocalContext // 导入本地上下文
import androidx.compose.ui.res.stringResource // 导入字符串资源
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.text.input.ImeAction // 导入软键盘动作
import androidx.compose.ui.text.input.KeyboardType // 导入键盘类型
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp
import com.ltt.gkd.R // 导入资源 ID
import com.ltt.gkd.data.app.AppInfo // 导入应用信息
import com.ltt.gkd.data.app.AppListRepository // 导入应用列表仓库
import com.ltt.gkd.data.app.WhitelistStore // 导入白名单存储
import kotlinx.coroutines.Dispatchers // 导入调度器
import kotlinx.coroutines.flow.MutableStateFlow // 导入可变状态流
import kotlinx.coroutines.flow.asStateFlow // 导入只读流转换
import kotlinx.coroutines.launch // 导入协程启动
import kotlinx.coroutines.withContext // 导入切换上下文

/** 顶部筛选模式。 */
private enum class FilterMode { ALL, WHITELISTED, UNWHITELISTED } // 全部/已白名单/待跳过三种筛选

/**
 * 应用列表界面（白名单版）。
 *
 * - 顶部搜索框 + FilterChip 三选一（全部 / 已白名单 / 待跳过）
 * - 每行显示：图标 + 名称 + 包名 + 版本 + Switch（白名单开关）
 * - 已白名单行用浅色背景 + 盾牌图标做视觉区分
 * - 系统应用自动白名单（用户可手动关闭）
 * - 长按/点击行：复制包名到剪贴板
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
fun AppListScreen( // 应用列表主组件
    repo: AppListRepository, // 应用仓库
    whitelist: WhitelistStore // 白名单存储
) {
    val ctx = LocalContext.current // 当前上下文
    val scope = rememberCoroutineScope() // 协程作用域

    var keyword by remember { mutableStateOf("") } // 搜索关键字
    var filter by remember { mutableStateOf(FilterMode.ALL) } // 当前筛选模式
    val allApps = remember { MutableStateFlow<List<AppInfo>>(emptyList()) } // 全部应用流
    val apps by allApps.asStateFlow().collectAsState() // 收集为状态
    val whitelistSet by whitelist.whitelist.collectAsState() // 收集白名单集合
    var loading by remember { mutableStateOf(true) } // 是否加载中
    var showResetDialog by remember { mutableStateOf(false) } // 是否显示重置对话框

    // 首次加载全部应用
    LaunchedEffect(Unit) { // 首次进入
        loading = true // 标记加载中
        val data = withContext(Dispatchers.IO) { repo.listAll() } // IO 线程加载
        allApps.value = data // 设置数据
        loading = false // 加载完成
    }

    // 关键字变化时去抖 250ms 后搜索
    LaunchedEffect(keyword) { // 关键字变化
        kotlinx.coroutines.delay(250) // 延迟去抖
        val data = if (keyword.isBlank()) { // 空关键字
            withContext(Dispatchers.IO) { repo.listAll() } // 加载全部
        } else { // 有关键字
            withContext(Dispatchers.IO) { repo.search(keyword) } // 搜索
        }
        allApps.value = data // 更新数据
    }

    // 根据 filter + whitelistSet 过滤
    val filtered = remember(apps, whitelistSet, filter, keyword) { // 依赖项变化重算
        when (filter) { // 按模式分支
            FilterMode.ALL -> apps // 全部
            FilterMode.WHITELISTED -> apps.filter { it.packageName in whitelistSet } // 仅白名单
            FilterMode.UNWHITELISTED -> apps.filter { it.packageName !in whitelistSet } // 非白名单
        }
    }

    Scaffold( // 骨架
        topBar = { // 顶部栏
            TopAppBar( // 顶部应用栏
                title = { Text(stringResource(R.string.title_app_list)) }, // 标题
                actions = { // 操作区
                    IconButton(onClick = { showResetDialog = true }) { // 重置按钮
                        Icon( // 重置图标
                            Icons.Filled.RestartAlt, // 重置图标
                            contentDescription = stringResource(R.string.app_list_whitelist_reset) // 无障碍描述
                        )
                    }
                }
            )
        }
    ) { inner -> // 内容区
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) { // Surface 容器
            Column(Modifier.fillMaxSize().padding(12.dp)) { // 内边距纵向容器
                SearchBox( // 搜索框
                    keyword = keyword, // 关键字
                    onKeywordChange = { keyword = it }, // 更新关键字
                    onClear = { keyword = "" } // 清空
                )
                Spacer(Modifier.height(8.dp)) // 间距
                FilterRow( // 筛选行
                    filter = filter, // 当前模式
                    onFilterChange = { filter = it } // 切换
                )
                Spacer(Modifier.height(4.dp)) // 间距
                Text( // 总数文案
                    stringResource(R.string.app_list_total, filtered.size), // 文案带数量
                    fontSize = 12.sp, // 字号
                    color = MaterialTheme.colorScheme.outline, // 描边色
                    modifier = Modifier.padding(vertical = 4.dp) // 上下间距
                )
                if (loading) { // 加载中
                    Box( // 加载容器
                        Modifier.fillMaxSize(), // 占满
                        contentAlignment = Alignment.Center // 居中
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { // 居中纵向
                            CircularProgressIndicator() // 圆形进度条
                            Spacer(Modifier.height(8.dp)) // 间距
                            Text("加载中...", color = MaterialTheme.colorScheme.outline) // 文案
                        }
                    }
                } else if (filtered.isEmpty()) { // 空列表
                    Box( // 空态容器
                        Modifier.fillMaxSize(), // 占满
                        contentAlignment = Alignment.Center // 居中
                    ) {
                        Text( // 空态文案
                            when { // 按场景
                                keyword.isNotEmpty() -> "无匹配结果" // 有搜索词
                                filter == FilterMode.WHITELISTED -> "暂无白名单应用" // 已白名单模式
                                filter == FilterMode.UNWHITELISTED -> "所有应用都在白名单" // 待跳过模式
                                else -> "未发现任何应用" // 兜底
                            },
                            color = MaterialTheme.colorScheme.outline // 描边色
                        )
                    }
                } else { // 有数据
                    LazyColumn( // 列表
                        modifier = Modifier.fillMaxSize(), // 占满
                        verticalArrangement = Arrangement.spacedBy(2.dp) // 项间距
                    ) {
                        items(filtered, key = { it.packageName }) { info -> // 按包名作 key
                            val isWhitelisted = info.packageName in whitelistSet // 是否在白名单
                            AppItemRow( // 单行
                                info = info, // 应用信息
                                repo = repo, // 仓库
                                isWhitelisted = isWhitelisted, // 白名单状态
                                onWhitelistToggle = { newState -> // 切换回调
                                    scope.launch { whitelist.toggle(info.packageName, newState) } // 异步切换
                                },
                                onClick = { // 点击行
                                    copyToClipboard(ctx, info.packageName) // 复制包名
                                    Toast.makeText( // Toast 提示
                                        ctx, // 上下文
                                        "已复制包名: ${info.packageName}", // 文案
                                        Toast.LENGTH_SHORT // 短时
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetDialog) { // 显示重置对话框
        AlertDialog( // 对话框
            onDismissRequest = { showResetDialog = false }, // 关闭即清空
            title = { Text("重置白名单") }, // 标题
            text = { Text("恢复默认（仅保留系统应用），确定？") }, // 内容
            confirmButton = { // 确认
                TextButton(onClick = { // 点击重置
                    scope.launch { whitelist.reset() } // 异步重置
                    showResetDialog = false // 关闭对话框
                    Toast.makeText(ctx, "已重置", Toast.LENGTH_SHORT).show() // 提示
                }) { Text("确定") } // 文案
            },
            dismissButton = { // 取消
                TextButton(onClick = { showResetDialog = false }) { Text("取消") } // 关闭
            }
        )
    }
}

/**
 * 顶部筛选行，提供 "全部 / 已白名单 / 待跳过" 三选一切换。
 *
 * @param filter 当前选中的筛选模式。
 * @param onFilterChange 选择新模式时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
private fun FilterRow( // 筛选行
    filter: FilterMode, // 当前模式
    onFilterChange: (FilterMode) -> Unit // 切换回调
) {
    Row( // 横向容器
        modifier = Modifier.fillMaxWidth(), // 占满
        horizontalArrangement = Arrangement.spacedBy(8.dp) // 间距
    ) {
        FilterChip( // 全部 Chip
            selected = filter == FilterMode.ALL, // 是否选中
            onClick = { onFilterChange(FilterMode.ALL) }, // 点击切换
            label = { Text(stringResource(R.string.app_list_all)) } // 文案
        )
        FilterChip( // 已白名单 Chip
            selected = filter == FilterMode.WHITELISTED, // 是否选中
            onClick = { onFilterChange(FilterMode.WHITELISTED) }, // 点击切换
            label = { Text(stringResource(R.string.app_list_whitelisted)) } // 文案
        )
        FilterChip( // 待跳过 Chip
            selected = filter == FilterMode.UNWHITELISTED, // 是否选中
            onClick = { onFilterChange(FilterMode.UNWHITELISTED) }, // 点击切换
            label = { Text(stringResource(R.string.app_list_unwhitelisted)) } // 文案
        )
    }
}

/**
 * 搜索输入框，带左侧搜索图标与右侧清空按钮。
 *
 * @param keyword 当前搜索关键字。
 * @param onKeywordChange 关键字变化回调。
 * @param onClear 点击清空按钮时的回调。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
private fun SearchBox( // 搜索框
    keyword: String, // 关键字
    onKeywordChange: (String) -> Unit, // 变化回调
    onClear: () -> Unit // 清空回调
) {
    OutlinedTextField( // 描边文本框
        value = keyword, // 当前值
        onValueChange = onKeywordChange, // 输入回调
        modifier = Modifier.fillMaxWidth(), // 占满
        placeholder = { Text("按应用名或包名搜索") }, // 占位
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) }, // 前置搜索图标
        trailingIcon = { // 后置清空按钮
            if (keyword.isNotEmpty()) { // 有内容才显示
                IconButton(onClick = onClear) { // 点击清空
                    Icon(Icons.Filled.Clear, contentDescription = "清空") // 清空图标
                }
            }
        },
        keyboardOptions = KeyboardOptions( // 键盘选项
            keyboardType = KeyboardType.Text, // 文本键盘
            imeAction = ImeAction.Search // 搜索键
        ),
        singleLine = true // 单行
    )
}

/**
 * 单个应用列表项。
 *
 * 显示图标 + 名称 + 包名 + 版本号，并附带白名单开关。
 * 整行点击可触发 [onClick]（通常用于复制包名）。
 *
 * @param info 应用元信息。
 * @param repo 用于加载应用图标的仓库。
 * @param isWhitelisted 当前应用是否在白名单中（决定背景色与 Switch 状态）。
 * @param onWhitelistToggle 白名单开关切换回调，参数为新状态。
 * @param onClick 整行点击回调。
 */
@Composable // 标记为 Composable
private fun AppItemRow( // 单行
    info: AppInfo, // 应用信息
    repo: AppListRepository, // 仓库
    isWhitelisted: Boolean, // 是否在白名单
    onWhitelistToggle: (Boolean) -> Unit, // 白名单切换
    onClick: () -> Unit // 点击回调
) {
    // 已白名单应用使用浅色背景，待跳过应用使用主色容器半透明背景
    val bgColor = if (isWhitelisted) { // 已白名单
        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f) // 浅 surface 色
    } else { // 待跳过
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) // 半透明主容器色
    }
    Row( // 横向布局
        modifier = Modifier // 修饰符链
            .fillMaxWidth() // 占满
            .background(bgColor, MaterialTheme.shapes.small) // 背景与圆角
            .padding(vertical = 6.dp, horizontal = 4.dp), // 内边距
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        val ctx = LocalContext.current // 取上下文
        val iconBitmap = remember(info.packageName) { // 按包名记忆图标
            loadIconBitmap(repo, info.packageName, 48) // 加载图标位图
        }
        if (iconBitmap != null) { // 有图标
            androidx.compose.foundation.Image( // 显示图标
                bitmap = iconBitmap.asImageBitmap(), // 转 ImageBitmap
                contentDescription = info.label, // 无障碍描述
                modifier = Modifier.size(40.dp) // 尺寸
            )
        } else { // 无图标占位
            Box( // 占位容器
                modifier = Modifier.size(40.dp), // 尺寸
                contentAlignment = Alignment.Center // 居中
            ) {
                Text("?", color = MaterialTheme.colorScheme.outline) // 问号占位
            }
        }
        Spacer(Modifier.size(12.dp)) // 间距
        Column(Modifier.weight(1f)) { // 信息列
            Row(verticalAlignment = Alignment.CenterVertically) { // 名称行
                Text( // 应用名
                    info.label, // 应用名
                    fontSize = 15.sp, // 字号
                    fontWeight = FontWeight.Medium // 中粗体
                )
                if (info.isSystem) { // 系统应用标记
                    Text( // 系统标记
                        " 系统", // 文案
                        fontSize = 10.sp, // 小字号
                        color = MaterialTheme.colorScheme.outline // 描边色
                    )
                }
            }
            Text( // 包名
                info.packageName, // 包名
                fontSize = 11.sp, // 字号
                color = MaterialTheme.colorScheme.outline // 描边色
            )
            Text( // 版本与状态
                buildString { // 拼接字符串
                    append("v${info.versionName ?: "?"}") // 版本号
                    if (!info.isEnabled) append(" · 已停用") // 停用标记
                },
                fontSize = 10.sp, // 字号
                color = MaterialTheme.colorScheme.outline // 描边色
            )
        }
        // 白名单 Switch
        Switch( // 白名单开关
            checked = isWhitelisted, // 当前状态
            onCheckedChange = onWhitelistToggle, // 切换回调
            modifier = Modifier.padding(end = 4.dp) // 右间距
        )
    }
}

/**
 * 把指定文本复制到系统剪贴板。
 *
 * @param ctx 上下文，用于获取剪贴板服务。
 * @param text 要复制的文本。
 */
private fun copyToClipboard(ctx: Context, text: String) { // 复制到剪贴板
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return // 取剪贴板服务
    cm.setPrimaryClip(ClipData.newPlainText("package_name", text)) // 设置主剪贴板
}

/**
 * 把 Drawable 转为 Bitmap 供 Compose 显示。
 *
 * @param repo 应用仓库，用于加载图标 Drawable。
 * @param pkg 应用包名。
 * @param sizePx 目标 Bitmap 边长（像素）。
 * @return 可用于 Compose 的 Bitmap；若加载失败返回 null。
 */
private fun loadIconBitmap(repo: AppListRepository, pkg: String, sizePx: Int): Bitmap? { // 加载图标位图
    val drawable: Drawable = repo.loadIcon(pkg) ?: return null // 取 Drawable，失败返回 null
    return when (drawable) { // 按类型分支
        is BitmapDrawable -> drawable.bitmap.takeIf { !it.isRecycled } // 已是 BitmapDrawable 直接取
        else -> { // 其他 Drawable 类型
            // 非 BitmapDrawable 时绘制到新建的 Bitmap 上
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888) // 创建位图
            val canvas = Canvas(bmp) // 创建画布
            drawable.setBounds(0, 0, sizePx, sizePx) // 设置绘制边界
            drawable.draw(canvas) // 绘制到画布
            bmp // 返回位图
        }
    }
}
