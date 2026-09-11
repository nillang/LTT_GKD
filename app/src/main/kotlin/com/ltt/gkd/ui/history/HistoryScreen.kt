package com.ltt.gkd.ui.history // 声明包名

import androidx.compose.foundation.background // 导入背景修饰符
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
import androidx.compose.foundation.layout.width // 导入宽度
import androidx.compose.foundation.lazy.LazyColumn // 导入懒加载列表
import androidx.compose.foundation.lazy.items // 导入 items 函数
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角 Shape
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.automirrored.filled.ArrowBack // 导入返回箭头
import androidx.compose.material.icons.filled.DeleteOutline // 导入删除图标
import androidx.compose.material3.AlertDialog // 导入对话框
import androidx.compose.material3.ExperimentalMaterial3Api // 导入实验性 API
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.IconButton // 导入图标按钮
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.Scaffold // 导入骨架
import androidx.compose.material3.Text // 导入文本
import androidx.compose.material3.TextButton // 导入文本按钮
import androidx.compose.material3.TopAppBar // 导入顶部栏
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.collectAsState // 导入 collectAsState
import androidx.compose.runtime.getValue // 导入 getValue
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember
import androidx.compose.runtime.setValue // 导入 setValue
import androidx.compose.ui.Alignment // 导入对齐
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.graphics.Color // 导入颜色
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp
import com.ltt.gkd.data.history.SkipHistoryStore // 导入跳过历史存储
import com.ltt.gkd.data.history.SkipRecord // 导入单条跳过记录
import com.ltt.gkd.ui.theme.AccentAmber // 导入强调琥珀色（亮色）
import com.ltt.gkd.ui.theme.AccentBlue // 导入强调蓝色（亮色）
import com.ltt.gkd.ui.theme.AccentOrange // 导入强调橙色（亮色）
import com.ltt.gkd.ui.theme.AccentPurple // 导入强调紫色（亮色）
import com.ltt.gkd.ui.theme.BlueBadgeBg // 导入蓝色徽章背景（亮色）
import com.ltt.gkd.ui.theme.OrangeBadgeBg // 导入橙色徽章背景（亮色）
import com.ltt.gkd.ui.theme.PurpleBadgeBg // 导入紫色徽章背景（亮色）
import com.ltt.gkd.ui.theme.AmberBadgeBg // 导入琥珀色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkBlueBadgeBg // 导入深色蓝色徽章背景
import com.ltt.gkd.ui.theme.DarkOrangeBadgeBg // 导入深色橙色徽章背景
import com.ltt.gkd.ui.theme.DarkPurpleBadgeBg // 导入深色紫色徽章背景
import com.ltt.gkd.ui.theme.DarkAmberBadgeBg // 导入深色琥珀色徽章背景
import com.ltt.gkd.ui.theme.DarkAccentBlue // 导入深色模式蓝色前景
import com.ltt.gkd.ui.theme.DarkAccentOrange // 导入深色模式橙色前景
import com.ltt.gkd.ui.theme.DarkAccentPurple // 导入深色模式紫色前景
import com.ltt.gkd.ui.theme.DarkAccentAmber // 导入深色模式琥珀色前景
import androidx.compose.foundation.isSystemInDarkTheme // 导入深色主题判断函数
import com.ltt.gkd.util.launchSafe // 导入安全启动协程
import kotlinx.coroutines.flow.Flow // 导入 Flow
import java.text.SimpleDateFormat // 导入日期格式化
import java.util.Date // 导入日期类
import java.util.Locale // 导入区域设置
import kotlin.math.abs // 导入绝对值函数

/**
 * 跳过记录页（UI v5 ②）。
 *
 * 顶部累计大数字 + 今日/本周/本月统计，下方最近跳过列表。
 *
 * @param history 跳过记录存储，提供记录流、清空与按时间段计数能力。
 * @param totalSkipFlow 累计跳过次数流，展示在顶部大数字位置。
 * @param onBack 点击返回按钮回调，回到首页。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用实验性 API
@Composable // 标记为 Composable
fun HistoryScreen( // 跳过记录主组件
    history: SkipHistoryStore, // 历史存储
    totalSkipFlow: Flow<Int>, // 累计跳过流
    onBack: () -> Unit // 返回回调
) {
    val records by history.records.collectAsState() // 收集记录流
    val total by totalSkipFlow.collectAsState(initial = 0) // 收集累计流，默认 0

    var showClearDialog by remember { mutableStateOf(false) } // 是否显示清空对话框

    val today = history.countSince(history.startOfToday()) // 今日跳过数
    val week = history.countSince(history.startOfWeek()) // 本周跳过数
    val month = history.countSince(history.startOfMonth()) // 本月跳过数

    if (showClearDialog) { // 显示清空对话框
        AlertDialog( // 对话框
            onDismissRequest = { showClearDialog = false }, // 关闭即清空状态
            title = { Text("清空跳过记录") }, // 标题
            text = { Text("将清空全部历史记录（累计跳过次数不变），确定继续吗？") }, // 内容
            confirmButton = { // 确认按钮
                TextButton(onClick = { // 点击清空
                    history.clear() // 清空记录
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
                title = { Text("跳过记录") }, // 标题
                navigationIcon = { // 返回按钮
                    IconButton(onClick = onBack) { // 点击触发返回
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") // 返回箭头
                    }
                },
                actions = { // 操作区
                    if (records.isNotEmpty()) { // 有记录才显示清空
                        IconButton(onClick = { showClearDialog = true }) { // 点击弹对话框
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "清空记录") // 删除图标
                        }
                    }
                }
            )
        }
    ) { inner -> // 内容区
        Column(Modifier.fillMaxSize().padding(inner)) { // 纵向容器
            // ---- 顶部统计 ----
            Column( // 统计列
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp), // 上下间距
                horizontalAlignment = Alignment.CenterHorizontally // 水平居中
            ) {
                Text( // 累计大数字
                    formatNumber(total), // 千分位格式化
                    fontSize = 42.sp, // 大字号
                    fontWeight = FontWeight.ExtraBold, // 特粗体
                    color = MaterialTheme.colorScheme.primary // 主色
                )
                Text( // 累计跳过标签
                    "累计跳过", // 文案
                    fontSize = 12.sp, // 字号
                    color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                )
                Row( // 时段统计行
                    Modifier.padding(top = 14.dp).fillMaxWidth(), // 上间距，占满
                    horizontalArrangement = Arrangement.Center // 居中
                ) {
                    PeriodStat(today, "今日") // 今日统计
                    StatDivider() // 分隔
                    PeriodStat(week, "本周") // 本周统计
                    StatDivider() // 分隔
                    PeriodStat(month, "本月") // 本月统计
                }
            }

            Spacer(Modifier.height(12.dp)) // 间距

            // ---- 7 天趋势柱状图 ----
            if (records.isNotEmpty()) { // 有记录才显示图表
                WeekTrendChart(history.dailyCounts7d()) // 7 天柱状图
                Spacer(Modifier.height(12.dp)) // 图表与列表间距
            }

            Text( // 列表标题
                "最近跳过", // 文案
                fontSize = 11.sp, // 字号
                fontWeight = FontWeight.SemiBold, // 半粗体
                color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp) // 左右间距
            )

            if (records.isEmpty()) { // 无记录
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { // 居中容器
                    Text( // 空态提示
                        "还没有跳过记录\n开启服务后自动记录", // 文案
                        color = MaterialTheme.colorScheme.outline, // 描边色
                        fontSize = 13.sp // 字号
                    )
                }
            } else { // 有记录
                LazyColumn( // 列表
                    Modifier.fillMaxSize().padding(horizontal = 14.dp), // 水平内边距
                    verticalArrangement = Arrangement.spacedBy(8.dp) // 项间距
                ) {
                    items(records, key = { it.timestamp.toString() + it.packageName }) { rec -> // 时间戳+包名作 key
                        HistoryItem(rec) // 单条记录卡片
                    }
                }
            }
        }
    }
}

/**
 * 时段统计项：数字 + 标签（今日/本周/本月）。
 *
 * @param num 该时段的跳过次数。
 * @param label 时段名称，显示在数字下方。
 */
@Composable // 标记为 Composable
private fun PeriodStat(num: Int, label: String) { // 时段统计项
    Column( // 纵向容器
        modifier = Modifier.padding(horizontal = 20.dp), // 水平间距
        horizontalAlignment = Alignment.CenterHorizontally // 居中
    ) {
        Text( // 数量
            num.toString(), // 转字符串
            fontSize = 18.sp, // 字号
            fontWeight = FontWeight.Bold, // 加粗
            color = MaterialTheme.colorScheme.onSurface // 主文字色
        )
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) // 标签
    }
}

/** 时段统计项之间的细分隔线（28dp 高、1dp 宽）。 */
@Composable // 标记为 Composable
private fun StatDivider() { // 时段分隔线
    Box( // 容器
        Modifier
            .height(28.dp) // 高度
            .width(1.dp) // 宽度
            .background(MaterialTheme.colorScheme.surfaceVariant) // 背景色
    )
}

/**
 * 单条跳过记录卡片：应用色块首字 + 应用名 + 场景徽章 + 操作描述 + 相对时间。
 *
 * @param rec 单条跳过记录数据。
 */
@Composable // 标记为 Composable
private fun HistoryItem(rec: SkipRecord) { // 单条记录卡片
    val dark = isSystemInDarkTheme() // 判断当前是否深色模式
    // 场景徽章文案与配色：按 scene 字段映射到对应背景/前景色，深色模式使用暗色变体
    val (badgeText, badgeBg, badgeFg) = when (rec.scene) { // 解构文案与配色
        "SPLASH" -> if (dark) Triple("开屏", DarkBlueBadgeBg, DarkAccentBlue) else Triple("开屏", BlueBadgeBg, AccentBlue)       // 开屏广告：蓝色徽章
        "POPUP" -> if (dark) Triple("弹窗", DarkOrangeBadgeBg, DarkAccentOrange) else Triple("弹窗", OrangeBadgeBg, AccentOrange)    // 弹窗广告：橙色徽章
        "BANNER" -> if (dark) Triple("Banner", DarkPurpleBadgeBg, DarkAccentPurple) else Triple("Banner", PurpleBadgeBg, AccentPurple) // 横幅广告：紫色徽章
        else -> if (dark) Triple("其他", DarkAmberBadgeBg, DarkAccentAmber) else Triple("其他", AmberBadgeBg, AccentAmber)         // 其他场景：琥珀色兜底
    }
    Row( // 横向卡片
        Modifier
            .fillMaxWidth() // 占满宽度
            .background( // 背景
                MaterialTheme.colorScheme.surface, // surface 色
                RoundedCornerShape(12.dp) // 圆角
            )
            .padding(10.dp), // 内边距
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        // 应用色块首字
        Box( // 应用色块
            Modifier.size(32.dp).background(appColor(rec.packageName), RoundedCornerShape(8.dp)), // 按包名取色
            contentAlignment = Alignment.Center // 居中
        ) {
            Text( // 首字符
                rec.appName.take(1), // 取第一个字
                color = Color.White, // 白色
                fontSize = 13.sp, // 字号
                fontWeight = FontWeight.Bold // 加粗
            )
        }
        Spacer(Modifier.width(10.dp)) // 间距
        Column(Modifier.weight(1f)) { // 中间信息列
            Row(verticalAlignment = Alignment.CenterVertically) { // 应用名行
                Text(rec.appName, fontSize = 13.sp, fontWeight = FontWeight.Medium) // 应用名
                Spacer(Modifier.width(4.dp)) // 间距
                Text( // 场景徽章
                    badgeText, // 文案
                    fontSize = 9.sp, // 极小字号
                    color = badgeFg, // 前景色
                    modifier = Modifier // 修饰符链
                        .background(badgeBg, RoundedCornerShape(6.dp)) // 圆角背景
                        .padding(horizontal = 6.dp, vertical = 2.dp) // 内边距
                )
            }
            Text( // 操作描述
                buildActionDesc(rec), // 描述
                fontSize = 11.sp, // 字号
                color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
            )
        }
        Text( // 相对时间
            relativeTime(rec.timestamp), // 相对时间文案
            fontSize = 10.sp, // 字号
            color = MaterialTheme.colorScheme.outline // 描边色
        )
    }
}

private fun buildActionDesc(rec: SkipRecord): String { // 构造动作描述
    val action = when (rec.action) { // 动作类型转中文
        "CLICK_NODE" -> "点击" // 点击节点
        "CLICK_COORD" -> "坐标点击" // 坐标点击
        "BACK" -> "返回键" // 返回键
        "GESTURE_TAP" -> "手势点击" // 手势点击
        else -> "处理" // 兜底
    }
    val matched = rec.matchedText.ifEmpty { "跳过" } // 空匹配文本兜底
    return "匹配 \"$matched\" → $action" // 拼装描述
}

/** 按包名稳定取色（无应用图标时的占位色块）。 */
private fun appColor(pkg: String): Color { // 按包名取色
    val palette = listOf( // 调色板
        Color(0xFF000000), Color(0xFF07C160), Color(0xFFFF6B00), // 黑、微信绿、橙
        Color(0xFFFB7299), Color(0xFF1565C0), Color(0xFF7B1FA2), // B站粉、蓝、紫
        Color(0xFFE65100), Color(0xFF00897B), Color(0xFFC2185B) // 深橙、青绿、深粉
    )
    // 用包名哈希取模，保证同一应用每次显示颜色一致
    val idx = abs(pkg.hashCode()) % palette.size // 哈希取模
    return palette[idx] // 返回对应颜色
}

/**
 * 将时间戳格式化为相对时间文案（刚刚 / N分钟前 / N小时前 / MM-dd）。
 *
 * @param ts 事件时间戳（毫秒）。
 * @return 适合在列表右侧显示的简短时间文案。
 */
private fun relativeTime(ts: Long): String { // 相对时间格式化
    val diff = System.currentTimeMillis() - ts // 计算时间差
    return when { // 按差值分支
        diff < 60_000 -> "刚刚"                                          // 1 分钟内
        diff < 3_600_000 -> "${diff / 60_000}分钟前"                     // 1 小时内
        diff < 86_400_000 -> "${diff / 3_600_000}小时前"                 // 1 天内
        else -> SimpleDateFormat("MM-dd", Locale.US).format(Date(ts))   // 超过 1 天显示月-日
    }
}

/** 数字千分位格式化：1247 -> "1,247"。 */
private fun formatNumber(n: Int): String = // 千分位格式化
    n.toString().reversed().chunked(3).joinToString(",").reversed() // 反转→分块→逗号→再反转
