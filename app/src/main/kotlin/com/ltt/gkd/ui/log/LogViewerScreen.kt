package com.ltt.gkd.ui.log // 声明包名

import android.app.Activity // 导入 Activity 基类
import android.content.Intent // 导入 Intent
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Column // 导入纵向容器
import androidx.compose.foundation.layout.Row // 导入横向容器
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度
import androidx.compose.foundation.layout.heightIn // 导入高度区间
import androidx.compose.foundation.layout.padding // 导入内边距
import androidx.compose.foundation.lazy.LazyColumn // 导入懒加载列表
import androidx.compose.foundation.lazy.items // 导入 items 函数
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 导入返回箭头
import androidx.compose.material.icons.filled.DeleteOutline // 导入删除图标
import androidx.compose.material.icons.filled.IosShare // 导入分享图标
import androidx.compose.material.icons.filled.Refresh // 导入刷新图标
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.Button // 导入按钮
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Surface // 导入 Surface
import androidx.compose.material3.Text // 导入文本
import androidx.compose.material3.TextButton // 导入文本按钮
import androidx.compose.material3.TopAppBar // 导入顶部栏
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.DisposableEffect // 导入生命周期副作用
import androidx.compose.runtime.LaunchedEffect // 导入一次性副作用
import androidx.compose.runtime.getValue // 导入 getValue
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember
import androidx.compose.runtime.setValue // 导入 setValue
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.platform.LocalContext // 导入本地上下文
import androidx.compose.ui.platform.LocalLifecycleOwner // 导入生命周期所有者
import androidx.compose.ui.text.font.FontFamily // 导入字体族
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp
import androidx.core.content.FileProvider // 导入 FileProvider
import com.ltt.gkd.util.Logger // 导入日志工具
import java.io.File // 导入文件
import java.text.SimpleDateFormat // 导入日期格式化
import java.util.Date // 导入日期
import java.util.Locale // 导入区域设置

/**
 * 日志查看页（v5）。
 *
 * 顶部 TopAppBar 带返回 + 清空 + 导出。
 * 上半区：实时内存缓冲（最近 1000 条），按级别着色，可滚动。
 * 下半区：历史日志文件列表（按天滚动，7 天自动清理）。
 * 底部：刷新 + 导出最新按钮。
 * 进入页面后每 2 秒自动刷新内存缓冲。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
fun LogViewerScreen() { // 日志查看主组件
    val ctx = LocalContext.current // 当前上下文
    var entries by remember { mutableStateOf(Logger.snapshot()) } // 实时日志快照
    var files by remember { mutableStateOf(Logger.listLogFiles(ctx)) } // 历史日志文件
    var showClearDialog by remember { mutableStateOf(false) } // 清空对话框状态

    // 进入页面时立即刷新一次
    LaunchedEffect(Unit) { // 首次进入
        entries = Logger.snapshot() // 刷新日志
        files = Logger.listLogFiles(ctx) // 刷新文件
    }

    // ON_RESUME 时刷新（从设置页返回后能看到最新日志）
    val lifecycleOwner = LocalLifecycleOwner.current // 当前生命周期所有者
    DisposableEffect(lifecycleOwner) { // 生命周期副作用
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> // 事件观察者
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { // ON_RESUME 时
                entries = Logger.snapshot() // 刷新日志
                files = Logger.listLogFiles(ctx) // 刷新文件
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer) // 注册观察者
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) } // 离开时移除
    }

    // 每 2 秒自动刷新内存缓冲（不刷文件列表，避免 IO 频繁）
    LaunchedEffect(Unit) { // 一次性副作用
        while (true) { // 死循环
            kotlinx.coroutines.delay(2000) // 延迟 2 秒
            entries = Logger.snapshot() // 刷新内存日志
        }
    }

    if (showClearDialog) { // 显示清空对话框
        AlertDialog( // 对话框
            onDismissRequest = { showClearDialog = false }, // 关闭即清空
            title = { Text("清空日志") }, // 标题
            text = { Text("将清空内存缓冲和全部历史日志文件，确定继续吗？") }, // 内容
            confirmButton = { // 确认按钮
                TextButton(onClick = { // 点击清空
                    Logger.clear(ctx) // 清空日志
                    entries = Logger.snapshot() // 刷新内存
                    files = Logger.listLogFiles(ctx) // 刷新文件
                    showClearDialog = false // 关闭对话框
                }) { Text("清空") } // 文案
            },
            dismissButton = { // 取消按钮
                TextButton(onClick = { showClearDialog = false }) { Text("取消") } // 关闭
            }
        )
    }

    Scaffold( // 骨架
        topBar = { // 顶部栏
            TopAppBar( // 顶部应用栏
                title = { Text("日志查看") }, // 标题
                navigationIcon = { // 返回按钮
                    IconButton(onClick = { (ctx as? Activity)?.finish() }) { // 关闭 Activity
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") // 返回箭头
                    }
                },
                actions = { // 操作区
                    IconButton(onClick = { // 刷新按钮
                        entries = Logger.snapshot() // 刷新日志
                        files = Logger.listLogFiles(ctx) // 刷新文件
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新") // 刷新图标
                    }
                    if (entries.isNotEmpty() || files.isNotEmpty()) { // 有日志才显示清空
                        IconButton(onClick = { showClearDialog = true }) { // 点击弹对话框
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "清空日志") // 删除图标
                        }
                    }
                }
            )
        }
    ) { inner -> // 内容区
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) { // Surface 容器
            Column(Modifier.fillMaxSize().padding(12.dp)) { // 纵向容器
                // ---- 实时缓冲 ----
                Text( // 实时缓冲标题
                    "实时缓冲（最近 ${entries.size} 条）", // 文案带数量
                    fontSize = 13.sp, // 字号
                    color = MaterialTheme.colorScheme.outline // 描边色
                )
                LazyColumn( // 实时日志列表
                    Modifier.fillMaxWidth().weight(1f), // 占满并占剩余高度
                    verticalArrangement = Arrangement.spacedBy(2.dp) // 项间距
                ) {
                    items(entries, key = { it.seq }) { entry -> // 按序号作 key
                        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) // 日期格式化
                            .format(Date(entry.timestamp)) // 格式化时间戳
                        Text( // 日志行
                            "$ts ${entry.level.tag} ${entry.message}", // 时间+级别+消息
                            fontSize = 11.sp, // 字号
                            fontFamily = FontFamily.Monospace, // 等宽字体
                            color = when (entry.level) { // 按级别着色
                                Logger.Level.E -> MaterialTheme.colorScheme.error // 错误红色
                                Logger.Level.W -> MaterialTheme.colorScheme.tertiary // 警告三级色
                                else -> MaterialTheme.colorScheme.onSurface // 普通文字色
                            }
                        )
                    }
                }

                // ---- 历史文件 ----
                Text( // 历史文件标题
                    "历史文件（${files.size} 个，按天滚动）", // 文案带数量
                    fontSize = 13.sp, // 字号
                    color = MaterialTheme.colorScheme.outline, // 描边色
                    modifier = Modifier.padding(top = 12.dp) // 上间距
                )
                LazyColumn( // 历史文件列表
                    Modifier.fillMaxWidth().heightIn(max = 120.dp) // 占满宽度，限高 120
                ) {
                    items(files, key = { it.name }) { f -> // 按文件名作 key
                        Text( // 文件信息
                            "${f.name}  (${f.length() / 1024}KB)", // 文件名+大小
                            fontSize = 11.sp, // 字号
                            fontFamily = FontFamily.Monospace // 等宽字体
                        )
                    }
                }

                // ---- 底部操作 ----
                Row( // 底部按钮行
                    Modifier.fillMaxWidth().padding(top = 8.dp), // 内边距
                    horizontalArrangement = Arrangement.spacedBy(8.dp) // 间距
                ) {
                    Button( // 刷新按钮
                        onClick = { // 点击刷新
                            entries = Logger.snapshot() // 刷新日志
                            files = Logger.listLogFiles(ctx) // 刷新文件
                        },
                        modifier = Modifier.weight(1f) // 等分
                    ) { Text("刷新") } // 文案
                    Button( // 导出按钮
                        onClick = { shareLatest(ctx, files) }, // 分享最新日志
                        modifier = Modifier.weight(1f) // 等分
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = null) // 分享图标
                        Text("导出最新") // 文案
                    }
                }
            }
        }
    }
}

/**
 * 通过 FileProvider 分享最新日志文件。
 * 调用系统分享面板，用户可选择保存或发送。
 */
private fun shareLatest(ctx: android.content.Context, files: List<File>) { // 分享最新日志
    val latest = files.lastOrNull() ?: return // 取最后一个，无则返回
    runCatching { // 容错执行
        val uri = FileProvider.getUriForFile( // 通过 FileProvider 获取可分享 URI
            ctx, // 上下文
            "${ctx.packageName}.fileprovider", // authority
            latest // 目标文件
        )
        val intent = Intent(Intent.ACTION_SEND).apply { // 发送 Intent
            type = "text/plain" // 类型
            putExtra(Intent.EXTRA_STREAM, uri) // 文件 URI
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // 授予读权限
        }
        ctx.startActivity(Intent.createChooser(intent, "分享日志 ${latest.name}")) // 启动选择器
    }
}
