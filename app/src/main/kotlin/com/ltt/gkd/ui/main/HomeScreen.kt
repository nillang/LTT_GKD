package com.ltt.gkd.ui.main // 声明包名

import androidx.compose.animation.core.RepeatMode // 导入动画重复模式
import androidx.compose.animation.core.animateFloat // 导入浮点数动画
import androidx.compose.animation.core.infiniteRepeatable // 导入无限重复动画规格
import androidx.compose.animation.core.rememberInfiniteTransition // 导入无限过渡动画
import androidx.compose.animation.core.tween // 导入补间动画规格
import androidx.compose.foundation.background // 导入背景修饰符
import androidx.compose.foundation.border // 导入边框修饰符
import androidx.compose.foundation.clickable // 导入点击修饰符
import androidx.compose.foundation.layout.Arrangement // 导入排列方向
import androidx.compose.foundation.layout.Box // 导入 Box 容器
import androidx.compose.foundation.layout.Column // 导入 Column 纵向容器
import androidx.compose.foundation.layout.Row // 导入 Row 横向容器
import androidx.compose.foundation.layout.Spacer // 导入 Spacer 占位
import androidx.compose.foundation.layout.fillMaxSize // 导入填满尺寸修饰符
import androidx.compose.foundation.layout.fillMaxWidth // 导入填满宽度修饰符
import androidx.compose.foundation.layout.height // 导入高度修饰符
import androidx.compose.foundation.layout.padding // 导入内边距修饰符
import androidx.compose.foundation.layout.size // 导入尺寸修饰符
import androidx.compose.foundation.shape.CircleShape // 导入圆形 Shape
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角 Shape
import androidx.compose.material.icons.Icons // 导入图标集合
import androidx.compose.material.icons.filled.PowerSettingsNew // 导入电源图标
import androidx.compose.material.icons.filled.Refresh // 导入刷新图标
import androidx.compose.material.icons.filled.Visibility // 导入可见图标
import androidx.compose.material3.Icon // 导入 Icon 组件
import androidx.compose.material3.CenterAlignedTopAppBar // 导入居中顶部栏
import androidx.compose.material3.ExperimentalMaterial3Api // 导入 Material3 实验 API 注解
import androidx.compose.material3.MaterialTheme // 导入 MaterialTheme
import androidx.compose.material3.Surface // 导入 Surface 容器
import androidx.compose.material3.Text // 导入 Text 组件
import androidx.compose.material3.TopAppBarDefaults // 导入顶部栏默认值
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.runtime.getValue // 导入 getValue 委托
import androidx.compose.ui.Alignment // 导入对齐方式
import androidx.compose.ui.Modifier // 导入 Modifier
import androidx.compose.ui.draw.scale // 导入缩放修饰符
import androidx.compose.ui.graphics.Color // 导入颜色
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.unit.dp // 导入 dp 单位
import androidx.compose.ui.unit.sp // 导入 sp 单位

/**
 * 首页（UI v5）：居中超大电源按钮 + 底部累计跳过统计。
 *
 * - 服务开：primary 实心圆 + 脉冲扩散环
 * - 服务关：白底灰边圆
 *
 * @param serviceOn 无障碍服务是否运行中，决定按钮样式与底部按钮可用状态。
 * @param totalSkip 累计跳过次数，显示在底部大数字位置。
 * @param onToggleService 点击电源按钮回调，通常跳转系统无障碍设置页。
 * @param onViewHistory 点击"查看"回调，进入跳过记录页。
 * @param onResetTotal 点击"重置"回调，清零累计跳过次数。
 */
@OptIn(ExperimentalMaterial3Api::class) // 启用 Material3 实验 API
@Composable // 标记为 Composable 函数
fun HomeScreen( // 首页主组件
    serviceOn: Boolean, // 服务是否运行
    totalSkip: Int, // 累计跳过次数
    onToggleService: () -> Unit, // 点击电源按钮回调
    onViewHistory: () -> Unit, // 查看历史回调
    onResetTotal: () -> Unit // 重置累计回调
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) { // 全屏背景

        // ---- 顶部：小狐标题栏 ----
        CenterAlignedTopAppBar( // 居中标题栏
            title = { // 标题内容
                Text( // 应用名
                    "小狐", // 标题文字
                    fontSize = 18.sp, // 字号
                    fontWeight = FontWeight.Bold, // 加粗
                    color = MaterialTheme.colorScheme.primary // 主色
                )
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors( // 标题栏配色
                containerColor = MaterialTheme.colorScheme.surface // 背景与页面一致
            )
        )

        // ---- 居中：电源按钮 + 状态文字 ----
        Column( // 居中纵向容器
            modifier = Modifier.align(Alignment.Center), // 居中对齐到父 Box
            horizontalAlignment = Alignment.CenterHorizontally, // 子项水平居中
            verticalArrangement = Arrangement.spacedBy(14.dp) // 子项垂直间距
        ) {
            ServiceCircle(serviceOn = serviceOn, onClick = onToggleService) // 电源按钮组件
            Text( // 状态主标题
                if (serviceOn) "点击关闭" else "点击开启", // 服务状态文案
                fontSize = 22.sp, // 字号
                fontWeight = FontWeight.Bold, // 加粗
                color = MaterialTheme.colorScheme.onSurface // 文字色
            )
            Text( // 状态副标题
                if (serviceOn) "服务已开启，自动跳过广告中" else "点击开启无障碍服务\n开启后自动跳过广告", // 副标题文案
                fontSize = 12.sp, // 小号字
                color = MaterialTheme.colorScheme.onSurfaceVariant, // 次要文字色
                lineHeight = 18.sp // 行高
            )
        }

        // ---- 底部：累计跳过 + 查看/重置 ----
        Column( // 底部纵向容器
            modifier = Modifier // 修饰符链
                .align(Alignment.BottomCenter) // 对齐到底部中心
                .fillMaxWidth() // 占满宽度
                .padding(bottom = 24.dp), // 底部留白
            horizontalAlignment = Alignment.CenterHorizontally, // 子项水平居中
            verticalArrangement = Arrangement.spacedBy(10.dp) // 垂直间距
        ) {
            Text( // 累计大数字
                text = formatNumber(totalSkip), // 千分位格式化
                fontSize = 28.sp, // 大号字
                fontWeight = FontWeight.Bold, // 加粗
                color = if (serviceOn) MaterialTheme.colorScheme.primary // 服务开启用主色
                else MaterialTheme.colorScheme.outline // 否则用描边色
            )
            Text( // 累计跳过标签
                "累计跳过", // 文案
                fontSize = 11.sp, // 小号字
                color = MaterialTheme.colorScheme.onSurfaceVariant // 次要色
            )
            Spacer(Modifier.height(2.dp)) // 留一点间距
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { // 按钮横向容器
                MiniPillButton( // 查看按钮
                    text = "查看", // 文案
                    icon = Icons.Filled.Visibility, // 图标
                    enabled = serviceOn, // 服务开启时可用
                    filled = true, // 填充样式
                    onClick = onViewHistory // 点击进入历史
                )
                MiniPillButton( // 重置按钮
                    text = "重置", // 文案
                    icon = Icons.Filled.Refresh, // 图标
                    enabled = serviceOn, // 服务开启时可用
                    filled = false, // 描边样式
                    onClick = onResetTotal // 点击重置
                )
            }
        }
    }
}

/**
 * 居中电源按钮：服务开启时显示 primary 实心圆并叠加脉冲扩散环动画，
 * 关闭时显示白底灰边圆。
 *
 * @param serviceOn 服务是否运行中，决定圆的填充色与动画。
 * @param onClick 点击按钮回调，用于切换服务状态。
 */
@Composable // 标记为 Composable
private fun ServiceCircle(serviceOn: Boolean, onClick: () -> Unit) { // 电源按钮组件
    val colorScheme = MaterialTheme.colorScheme // 取当前颜色方案

    // 开启时的脉冲扩散环动画
    val transition = rememberInfiniteTransition(label = "pulse") // 创建无限过渡动画
    val pulseScale by transition.animateFloat( // 缩放动画值
        initialValue = 1f, targetValue = 1.18f, // 从 1 到 1.18
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), // 2s 重复重启
        label = "scale" // 标签
    )
    val pulseAlpha by transition.animateFloat( // 透明度动画值
        initialValue = 0.25f, targetValue = 0f, // 从 0.25 渐隐到 0
        animationSpec = infiniteRepeatable(tween(2000), RepeatMode.Restart), // 2s 重复
        label = "alpha" // 标签
    )

    Box(contentAlignment = Alignment.Center) { // 居中容器
        if (serviceOn) { // 服务开启才显示脉冲环
            Box( // 脉冲扩散环
                Modifier
                    .size(136.dp) // 比按钮略大
                    .scale(pulseScale) // 应用缩放动画
                    .border(2.dp, colorScheme.primary.copy(alpha = pulseAlpha), CircleShape) // 圆形描边，颜色随动画变透明
            )
        }
        Surface( // 圆形按钮主体
            shape = CircleShape, // 圆形
            color = if (serviceOn) colorScheme.primary else colorScheme.surface, // 开启用主色，关闭用 surface
            shadowElevation = if (serviceOn) 8.dp else 0.dp, // 开启时有阴影
            modifier = Modifier // 修饰符链
                .size(120.dp) // 按钮直径
                .then( // 条件叠加修饰符
                    if (serviceOn) Modifier // 开启时无附加
                    else Modifier.border(3.dp, colorScheme.outlineVariant, CircleShape) // 关闭时加描边
                )
                .clickable(onClick = onClick) // 点击触发回调
        ) {
            Box(contentAlignment = Alignment.Center) { // 按钮内容居中
                Column(horizontalAlignment = Alignment.CenterHorizontally) { // 纵向排列
                    Icon( // 电源图标
                        Icons.Filled.PowerSettingsNew, // 电源图标
                        contentDescription = if (serviceOn) "关闭服务" else "开启服务", // 无障碍描述
                        tint = if (serviceOn) Color.White else colorScheme.outline, // 开启白色，关闭灰色
                        modifier = Modifier.size(38.dp) // 图标大小
                    )
                    Text( // 状态文字
                        if (serviceOn) "运行中" else "已关闭", // 文案
                        fontSize = 11.sp, // 字号
                        fontWeight = FontWeight.SemiBold, // 半粗体
                        color = if (serviceOn) Color.White else colorScheme.outline, // 颜色随状态
                        modifier = Modifier.padding(top = 4.dp) // 顶部间距
                    )
                }
            }
        }
    }
}

/**
 * 小型胶囊按钮：图标 + 文本，支持填充/描边两种样式与禁用态。
 *
 * 用于首页底部"查看"与"重置"两个操作入口。
 *
 * @param text 按钮文字。
 * @param icon 按钮图标。
 * @param enabled 是否可点击；为 false 时透明无描边且不响应点击。
 * @param filled true 用 primaryContainer 填充，false 仅描边。
 * @param onClick 点击回调（仅在 enabled=true 时触发）。
 */
@Composable // 标记为 Composable
private fun MiniPillButton( // 胶囊按钮
    text: String, // 文本
    icon: androidx.compose.ui.graphics.vector.ImageVector, // 图标
    enabled: Boolean, // 是否启用
    filled: Boolean, // 是否填充
    onClick: () -> Unit // 点击回调
) {
    val scheme = MaterialTheme.colorScheme // 取当前颜色方案
    Row( // 横向布局
        modifier = Modifier // 修饰符链
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier) // 启用时可点击
            .background( // 背景色
                color = when { // 按状态选色
                    !enabled -> Color.Transparent // 禁用透明
                    filled -> scheme.primaryContainer // 填充用主容器色
                    else -> Color.Transparent // 否则透明
                },
                shape = RoundedCornerShape(16.dp) // 圆角胶囊
            )
            .then( // 描边
                if (!filled) Modifier.border( // 仅描边样式才加边框
                    1.5.dp, // 边框粗细
                    if (enabled) scheme.outline else scheme.outlineVariant, // 启用色与禁用色
                    RoundedCornerShape(16.dp) // 圆角
                ) else Modifier // 填充样式不附加
            )
            .padding(horizontal = 14.dp, vertical = 6.dp), // 内边距
        verticalAlignment = Alignment.CenterVertically, // 垂直居中
        horizontalArrangement = Arrangement.spacedBy(4.dp) // 水平间距
    ) {
        Icon( // 图标
            icon, contentDescription = null, // 无障碍描述留空
            modifier = Modifier.size(13.dp), // 图标尺寸
            tint = if (enabled) { // 启用色
                if (filled) scheme.onPrimaryContainer else scheme.outline // 填充色与描边色
            } else scheme.outlineVariant // 禁用色
        )
        Text( // 文本
            text, // 文案
            fontSize = 12.sp, // 字号
            fontWeight = FontWeight.Medium, // 中粗体
            color = if (enabled) { // 启用色
                if (filled) scheme.onPrimaryContainer else scheme.outline // 填充色与描边色
            } else scheme.outlineVariant // 禁用色
        )
    }
}

/**
 * 数字千分位格式化：1247 -> "1,247"。
 *
 * 实现思路：反转字符串后按 3 字符分块，再用逗号拼接，最后反转回来，
 * 避免长度非 3 的倍数时前置出现多余逗号的问题。
 *
 * @param n 待格式化的非负整数。
 * @return 带千分位逗号的字符串。
 */
private fun formatNumber(n: Int): String = // 千分位格式化函数
    n.toString().reversed().chunked(3).joinToString(",").reversed() // 反转→分块→逗号拼接→再反转
