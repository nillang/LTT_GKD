package com.ltt.gkd.ui.settings // 声明包名

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
import androidx.compose.foundation.rememberScrollState // 导入滚动状态
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角 Shape
import androidx.compose.foundation.verticalScroll // 导入纵向滚动修饰符
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.automirrored.filled.Article // 导入文章图标
import androidx.compose.material.icons.filled.Checklist // 导入清单图标
import androidx.compose.material.icons.filled.Info // 导入信息图标
import androidx.compose.material.icons.filled.ExpandLess // 导入收起图标
import androidx.compose.material.icons.filled.ExpandMore // 导入展开图标
import androidx.compose.material.icons.filled.Notifications // 导入通知图标
import androidx.compose.material.icons.filled.PowerSettingsNew // 导入电源图标
import androidx.compose.material.icons.filled.QrCodeScanner // 导入扫码图标
import androidx.compose.material.icons.filled.Shield // 导入盾牌图标
import androidx.compose.material.icons.filled.Sync // 导入同步图标
import androidx.compose.material3.Icon // 导入图标组件
import androidx.compose.material3.MaterialTheme // 导入主题
import androidx.compose.material3.OutlinedTextField // 导入描边文本框
import androidx.compose.material3.Surface // 导入 Surface
import androidx.compose.material3.Switch // 导入开关
import androidx.compose.material3.Text // 导入文本
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.LaunchedEffect // 导入 LaunchedEffect
import androidx.compose.runtime.collectAsState // 导入 collectAsState
import androidx.compose.runtime.getValue // 导入 getValue
import androidx.compose.runtime.mutableStateOf // 导入可变状态
import androidx.compose.runtime.remember // 导入 remember
import androidx.compose.runtime.rememberCoroutineScope // 导入协程作用域
import androidx.compose.runtime.setValue // 导入 setValue
import androidx.compose.ui.Alignment // 导入对齐
import androidx.compose.ui.Modifier // 导入修饰符
import androidx.compose.ui.graphics.vector.ImageVector // 导入图标矢量
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.text.input.PasswordVisualTransformation // 导入密码掩码
import androidx.compose.ui.unit.dp // 导入 dp
import androidx.compose.ui.unit.sp // 导入 sp
import com.ltt.gkd.data.prefs.SettingsStore // 导入设置存储
import com.ltt.gkd.ui.theme.AccentBlue // 导入强调蓝色（亮色）
import com.ltt.gkd.ui.theme.AccentOrange // 导入强调橙色（亮色）
import com.ltt.gkd.ui.theme.BlueBadgeBg // 导入蓝色徽章背景（亮色）
import com.ltt.gkd.ui.theme.OrangeBadgeBg // 导入橙色徽章背景（亮色）
import com.ltt.gkd.ui.theme.DarkBlueBadgeBg // 导入深色蓝色徽章背景
import com.ltt.gkd.ui.theme.DarkOrangeBadgeBg // 导入深色橙色徽章背景
import com.ltt.gkd.ui.theme.DarkAccentBlue // 导入深色模式蓝色前景
import com.ltt.gkd.ui.theme.DarkAccentOrange // 导入深色模式橙色前景
import androidx.compose.foundation.isSystemInDarkTheme // 导入深色主题判断函数
import com.ltt.gkd.util.Logger // 导入日志工具
import kotlinx.coroutines.launch // 导入协程启动

/**
 * 设置（UI v5 ⑥）：基础设置卡片 + 订阅折叠卡片 + 贡献者 + 关于。
 *
 * @param settings 设置存储，提供各项开关与订阅配置的读写。
 * @param serviceOn 无障碍服务是否运行中，控制"无障碍服务"开关状态。
 * @param onOpenAccessibility 点击"无障碍服务"行回调，跳转系统无障碍设置。
 * @param onOpenLogs 点击"日志"行回调，打开日志查看页。
 * @param onOpenWhitelist 点击"应用白名单"行回调，打开应用列表页。
 */
@Composable // 标记为 Composable
fun SettingsScreen( // 设置主组件
    settings: SettingsStore, // 设置存储
    serviceOn: Boolean, // 服务状态
    onOpenAccessibility: () -> Unit, // 跳无障碍设置
    onOpenLogs: () -> Unit, // 打开日志
    onOpenWhitelist: () -> Unit // 打开白名单
) {
    val scope = rememberCoroutineScope() // 协程作用域
    val ocr by settings.ocrEnabled.collectAsState(initial = true) // OCR 兜底
    val sub by settings.subscriptionEnabled.collectAsState(initial = false) // 订阅开关
    val skipNoti by settings.skipNotificationEnabled.collectAsState(initial = false) // 跳过通知
    val deviceId by settings.deviceId.collectAsState(initial = "") // 设备 ID

    // ---- 文本输入框：用本地状态保证输入流畅，避免 Flow 异步回写导致光标跳开头 ----
    var localSubInterval by remember { mutableStateOf("24") } // 更新间隔本地状态
    var localGhToken by remember { mutableStateOf("") } // Token 本地状态
    var localGistId by remember { mutableStateOf("") } // Gist ID 本地状态
    // 首次加载从 Flow 同步初始值到本地状态
    LaunchedEffect(Unit) { // 组件首次挂载时执行
        settings.subscriptionIntervalHours.collect { localSubInterval = it.toString() } // 同步间隔
        settings.githubToken.collect { localGhToken = it } // 同步 Token
        settings.gistId.collect { localGistId = it } // 同步 Gist ID
    }

    // 订阅卡片展开状态
    var subExpanded by remember { mutableStateOf(false) } // 订阅卡片展开

    Column( // 滚动纵向容器
        Modifier
            .fillMaxSize() // 填满
            .background(MaterialTheme.colorScheme.background) // 背景色
            .verticalScroll(rememberScrollState()) // 滚动
            .padding(horizontal = 14.dp) // 水平内边距
            .padding(bottom = 20.dp), // 底部内边距
        verticalArrangement = Arrangement.spacedBy(14.dp) // 项间距
    ) {
        Spacer(Modifier.height(4.dp)) // 顶部留白
        Text( // 标题
            "设置", // 文案
            fontSize = 22.sp, // 字号
            fontWeight = FontWeight.Bold, // 加粗
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp) // 内边距
        )

        // ---------- 基础设置 ----------
        SectionTitle("基础设置") // 分区标题
        SettingsCard { // 卡片容器
            val dark = isSystemInDarkTheme() // 判断当前是否深色模式
            ToggleItem( // 无障碍服务
                icon = Icons.Filled.PowerSettingsNew, // 电源图标
                iconBg = MaterialTheme.colorScheme.primaryContainer, // 图标背景
                iconTint = MaterialTheme.colorScheme.primary, // 图标前景
                title = "无障碍服务", // 标题
                subtitle = if (serviceOn) "服务运行中" else "点击开启跳过服务", // 副标题
                checked = serviceOn, // 开关状态
                onCheckedChange = { onOpenAccessibility() } // 点击跳转
            )
            CardDivider() // 分隔线
            ToggleItem( // OCR 兜底
                icon = Icons.Filled.QrCodeScanner, // 扫码图标
                iconBg = if (dark) DarkBlueBadgeBg else BlueBadgeBg, // 蓝色徽章背景（深色适配）
                iconTint = if (dark) DarkAccentBlue else AccentBlue, // 蓝色前景（深色适配）
                title = "OCR 兜底", // 标题
                subtitle = "控件树找不到时截图识别", // 副标题
                checked = ocr, // 开关状态
                onCheckedChange = { v -> scope.launch { settings.setOcr(v) } } // 异步保存
            )
            CardDivider() // 分隔线
            // 日志：纯入口项，点击进入日志查看页（记录始终开启，无需开关）
            ClickItem( // 点击设置项
                icon = Icons.AutoMirrored.Filled.Article, // 文章图标
                iconBg = if (dark) DarkOrangeBadgeBg else OrangeBadgeBg, // 橙色背景（深色适配）
                iconTint = if (dark) DarkAccentOrange else AccentOrange, // 橙色前景（深色适配）
                title = "日志", // 标题
                subtitle = "查看运行日志", // 副标题
                onClick = onOpenLogs // 点击进入日志查看页
            )
            CardDivider() // 分隔线
            ClickItem( // 应用白名单项
                icon = Icons.Filled.Checklist, // 清单图标
                iconBg = MaterialTheme.colorScheme.secondaryContainer, // 次容器色
                iconTint = MaterialTheme.colorScheme.onSecondaryContainer, // 次容器前景
                title = "应用白名单", // 标题
                subtitle = "管理不执行跳过的应用", // 副标题
                onClick = onOpenWhitelist // 点击跳转
            )
            CardDivider() // 分隔线
            ToggleItem( // 跳过通知
                icon = Icons.Filled.Notifications, // 通知图标
                iconBg = MaterialTheme.colorScheme.primaryContainer, // 主容器色
                iconTint = MaterialTheme.colorScheme.primary, // 主色
                title = "跳过通知", // 标题
                subtitle = "成功跳过时在通知栏提示", // 副标题
                checked = skipNoti, // 开关状态
                onCheckedChange = { v -> scope.launch { settings.setSkipNotification(v) } } // 异步保存
            )
        }

        // ---------- 规则订阅（折叠卡片） ----------
        SectionTitle("规则订阅") // 分区标题
        SettingsCard { // 卡片容器
            Row( // 标题行（可点击展开）
                Modifier.fillMaxWidth().clickable { subExpanded = !subExpanded }.padding(14.dp), // 占满、点击切换、内边距
                verticalAlignment = Alignment.CenterVertically // 垂直居中
            ) {
                Icon( // 同步图标
                    Icons.Filled.Sync, contentDescription = null, // 无障碍描述留空
                    tint = MaterialTheme.colorScheme.primary // 主色
                )
                Spacer(Modifier.size(12.dp)) // 间距
                Column(Modifier.weight(1f)) { // 文本列
                    Text("自动更新", fontSize = 14.sp, fontWeight = FontWeight.Bold) // 标题
                    Text( // 副标题
                        if (sub) "自动更新：每${localSubInterval}小时" else "已关闭", // 状态文案
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                    )
                }
                Icon( // 展开/收起图标
                    if (subExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, // 切换图标
                    contentDescription = if (subExpanded) "收起" else "展开", // 无障碍描述
                    tint = MaterialTheme.colorScheme.outline // 描边色
                )
            }
            if (subExpanded) { // 展开时显示
                CardDivider() // 分隔线
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { // 内容列
                    // 提示：订阅源改到「规则 → 订阅」页统一管理
                    Surface( // 提示横幅
                        color = MaterialTheme.colorScheme.primaryContainer, // 主容器色
                        shape = RoundedCornerShape(10.dp), // 圆角
                        modifier = Modifier.fillMaxWidth() // 占满
                    ) {
                        Row( // 横向布局
                            Modifier.padding(10.dp), // 内边距
                            verticalAlignment = Alignment.CenterVertically, // 垂直居中
                            horizontalArrangement = Arrangement.spacedBy(8.dp) // 间距
                        ) {
                            Icon( // 信息图标
                                Icons.Filled.Info, contentDescription = null, // 无障碍描述留空
                                tint = MaterialTheme.colorScheme.primary, // 主色
                                modifier = Modifier.size(18.dp) // 尺寸
                            )
                            Text( // 引导文案
                                "订阅源请在「规则 → 订阅」页添加与管理；此处仅设置自动更新策略", // 文案
                                fontSize = 11.sp, // 字号
                                color = MaterialTheme.colorScheme.onPrimaryContainer // 主容器前景色
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { // 自动更新开关行
                        Column(Modifier.weight(1f)) { // 文本列
                            Text("自动更新", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) // 标题
                            Text("按间隔后台同步所有启用的订阅源", fontSize = 11.sp, // 副标题
                                color = MaterialTheme.colorScheme.onSurfaceVariant) // 次要色
                        }
                        Switch(checked = sub, onCheckedChange = { // 自动更新开关
                            scope.launch { settings.setSubscription(it) } // 异步保存
                        })
                    }
                    OutlinedTextField( // 更新间隔输入框
                        value = localSubInterval, // 本地状态值
                        onValueChange = { v -> // 输入回调
                            localSubInterval = v // 立即更新本地状态
                            v.toIntOrNull()?.takeIf { it > 0 }?.let { n -> // 仅正整数
                                scope.launch { settings.setSubscriptionInterval(n) } // 异步保存
                            }
                        },
                        label = { Text("更新间隔（小时）") }, // 标签
                        modifier = Modifier.fillMaxWidth(), // 占满
                        singleLine = true // 单行
                    )
                }
            }
        }

        // ---------- 贡献者 ----------
        SectionTitle("贡献者") // 分区标题
        SettingsCard { // 卡片容器
            Row(Modifier.fillMaxWidth().padding(14.dp), // 内边距
                verticalAlignment = Alignment.CenterVertically) { // 垂直居中
                Icon(Icons.Filled.Shield, contentDescription = null, // 盾牌图标
                    tint = MaterialTheme.colorScheme.primary) // 主色
                Spacer(Modifier.size(12.dp)) // 间距
                Column { // 文本列
                    Text("设备 ID", fontSize = 13.sp, fontWeight = FontWeight.Medium) // 标题
                    Text( // 副标题
                        deviceId.ifEmpty { "（上传规则时自动生成）" }, // 空时提示
                        fontSize = 11.sp, // 字号
                        color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
                    )
                }
            }
            CardDivider() // 分隔线
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { // Token 列
                Text("GitHub Token", fontSize = 11.sp, // 标签
                    color = MaterialTheme.colorScheme.onSurfaceVariant) // 次要色
                OutlinedTextField( // Token 输入框
                    value = localGhToken, // 本地状态值
                    onValueChange = { v -> // 输入回调
                        localGhToken = v // 立即更新本地状态
                        scope.launch { settings.setGithubToken(v) } // 异步保存
                    },
                    placeholder = { Text("ghp_xxxxxxxx") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满
                    singleLine = true, // 单行
                    visualTransformation = PasswordVisualTransformation() // 密码掩码
                )
            }
            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp), // 底部列
                verticalArrangement = Arrangement.spacedBy(4.dp)) { // 间距
                Text("Gist ID（上传后自动生成）", fontSize = 11.sp, // 标签
                    color = MaterialTheme.colorScheme.onSurfaceVariant) // 次要色
                OutlinedTextField( // Gist ID 输入框
                    value = localGistId, // 本地状态值
                    onValueChange = { v -> // 输入回调
                        localGistId = v // 立即更新本地状态
                        scope.launch { settings.setGistId(v) } // 异步保存
                    },
                    placeholder = { Text("自动生成") }, // 占位
                    modifier = Modifier.fillMaxWidth(), // 占满
                    singleLine = true // 单行
                )
            }
            Surface( // 安全提示卡片
                color = MaterialTheme.colorScheme.secondaryContainer, // 次容器色
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp) // 内边距
            ) {
                Text( // 安全说明
                    "Token 使用 Android Keystore + AES-GCM 加密，Gist 设为私有，仅本地存储", // 文案
                    fontSize = 10.sp, // 小字号
                    color = MaterialTheme.colorScheme.onSecondaryContainer, // 次容器前景
                    modifier = Modifier.padding(10.dp) // 内边距
                )
            }
        }

        // ---------- 关于 ----------
        SectionTitle("关于") // 分区标题
        Text( // 版本号
            "小狐 v1.0.0", // 文案
            fontSize = 13.sp, // 字号
            color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要色
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp) // 内边距
        )
    }
}

/** 分区标题：primary 色、12sp 粗体小标题，用于卡片上方分组提示。 */
@Composable // 标记为 Composable
private fun SectionTitle(text: String) { // 分区标题
    Text( // 文本
        text = text, // 文案
        fontSize = 12.sp, // 字号
        fontWeight = FontWeight.Bold, // 加粗
        color = MaterialTheme.colorScheme.primary, // 主色
        modifier = Modifier.padding(start = 4.dp) // 左间距
    )
}

/** 设置卡片容器：surface 色圆角（12dp）Surface，包裹一组设置项。 */
@Composable // 标记为 Composable
private fun SettingsCard(content: @Composable () -> Unit) { // 卡片容器
    Surface( // Surface 容器
        modifier = Modifier.fillMaxWidth(), // 占满
        shape = RoundedCornerShape(12.dp), // 圆角
        color = MaterialTheme.colorScheme.surface // surface 色
    ) {
        Column { content() } // 纵向包裹内容
    }
}

/** 卡片内分隔线：1dp 高、surfaceVariant 色，分隔卡片内的相邻设置项。 */
@Composable // 标记为 Composable
private fun CardDivider() { // 分隔线
    Box( // 容器
        Modifier
            .fillMaxWidth() // 占满
            .height(1.dp) // 高度
            .background(MaterialTheme.colorScheme.surfaceVariant) // 背景色
    )
}

/**
 * 开关设置项：图标徽章 + 标题/副标题 + 右侧 Switch。
 *
 * @param icon 左侧图标。
 * @param iconBg 图标徽章背景色。
 * @param iconTint 图标徽章前景色。
 * @param title 主标题。
 * @param subtitle 副标题（描述）。
 * @param checked 开关是否选中。
 * @param onCheckedChange 开关切换回调。
 */
@Composable // 标记为 Composable
private fun ToggleItem( // 开关设置项
    icon: ImageVector, // 图标
    iconBg: androidx.compose.ui.graphics.Color, // 背景色
    iconTint: androidx.compose.ui.graphics.Color, // 前景色
    title: String, // 标题
    subtitle: String, // 副标题
    checked: Boolean, // 状态
    onCheckedChange: (Boolean) -> Unit // 切换回调
) {
    Row( // 横向布局
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), // 内边距
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        IconBadge(icon, iconBg, iconTint) // 图标徽章
        Spacer(Modifier.size(10.dp)) // 间距
        Column(Modifier.weight(1f)) { // 文本列
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) // 标题
            Text(subtitle, fontSize = 11.sp, // 副标题
                color = MaterialTheme.colorScheme.onSurfaceVariant) // 次要色
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange) // 开关
    }
}

/**
 * 点击设置项：图标徽章 + 标题/副标题，整行可点击跳转。
 *
 * @param icon 左侧图标。
 * @param iconBg 图标徽章背景色。
 * @param iconTint 图标徽章前景色。
 * @param title 主标题。
 * @param subtitle 副标题（描述）。
 * @param onClick 整行点击回调。
 */
@Composable // 标记为 Composable
private fun ClickItem( // 点击设置项
    icon: ImageVector, // 图标
    iconBg: androidx.compose.ui.graphics.Color, // 背景色
    iconTint: androidx.compose.ui.graphics.Color, // 前景色
    title: String, // 标题
    subtitle: String, // 副标题
    onClick: () -> Unit // 点击回调
) {
    Row( // 横向布局
        Modifier.fillMaxWidth().clickable(onClick = onClick) // 整行点击
            .padding(horizontal = 14.dp, vertical = 12.dp), // 内边距
        verticalAlignment = Alignment.CenterVertically // 垂直居中
    ) {
        IconBadge(icon, iconBg, iconTint) // 图标徽章
        Spacer(Modifier.size(10.dp)) // 间距
        Column(Modifier.weight(1f)) { // 文本列
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium) // 标题
            Text(subtitle, fontSize = 11.sp, // 副标题
                color = MaterialTheme.colorScheme.onSurfaceVariant) // 次要色
        }
    }
}

/**
 * 图标徽章：30dp 圆角方块包裹 18dp 图标，用于设置项左侧视觉标识。
 *
 * @param icon 徽章内图标。
 * @param bg 徽章背景色。
 * @param tint 图标着色。
 */
@Composable // 标记为 Composable
private fun IconBadge( // 图标徽章
    icon: ImageVector, // 图标
    bg: androidx.compose.ui.graphics.Color, // 背景色
    tint: androidx.compose.ui.graphics.Color // 前景色
) {
    Box( // 容器
        Modifier.size(30.dp).background(bg, RoundedCornerShape(8.dp)), // 圆角背景
        contentAlignment = Alignment.Center // 居中
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp)) // 图标
    }
}
