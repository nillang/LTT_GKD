package com.ltt.gkd.ui.theme // 声明包名

import android.os.Build // 导入 Build 用于判断系统版本
import androidx.compose.foundation.isSystemInDarkTheme // 导入判断深色主题函数
import androidx.compose.material3.MaterialTheme // 导入 MaterialTheme
import androidx.compose.material3.darkColorScheme // 导入深色配色方案构造器
import androidx.compose.material3.dynamicDarkColorScheme // 导入动态深色配色
import androidx.compose.material3.dynamicLightColorScheme // 导入动态亮色配色
import androidx.compose.material3.lightColorScheme // 导入亮色配色方案构造器
import androidx.compose.runtime.Composable // 导入 Composable 注解
import androidx.compose.ui.graphics.Color // 导入颜色
import androidx.compose.ui.platform.LocalContext // 导入本地上下文

/**
 * LTT_GKD 主主题入口（UI v5 深青色调色板）。
 *
 * 策略：
 * - 统一使用硬编码 DayNight 品牌配色（primary = #007070 深青绿，见开发文档 §4.1 / 约束 U1）
 * - 禁用 Android 12+ 的 Material You 动态取色：动态色会随壁纸改变而覆盖品牌色，
 *   导致真机上主题色与文档规定不符（此前默认开启即此问题），故 dynamicColor 默认关闭。
 */
@Composable // 标记为 Composable
fun LTTGKDTheme( // 主题入口函数
    darkTheme: Boolean = isSystemInDarkTheme(), // 是否深色主题，默认跟随系统
    dynamicColor: Boolean = false, // 动态取色默认关闭：保证品牌主题色 #007070 全局生效
    content: @Composable () -> Unit // 主题包裹的内容
) {
    val colorScheme = when { // 选择配色方案
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> { // 12+ 且开启动态
            val ctx = LocalContext.current // 当前上下文
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) // 按深浅取动态色
        }
        darkTheme -> DarkColorScheme // 深色用硬编码方案
        else -> LightColorScheme // 否则用亮色方案
    }

    MaterialTheme( // 应用主题
        colorScheme = colorScheme, // 配色
        typography = LTTGKDTypography, // 字体
        shapes = LTTGKDShapes, // 形状
        content = content // 内容
    )
}

// ---- DayNight 硬编码配色（对齐 v5 设计规范） ----

/** 亮色配色方案：深青绿为主色，配合浅青绿容器与橙色强调色。 */
private val LightColorScheme = lightColorScheme( // 亮色配色
    primary = Teal700,                          // 主色：深青绿（品牌色，用于开关/重要按钮）
    onPrimary = Color.White,                    // 主色上的文字/图标色
    primaryContainer = Teal200,                 // 主色容器：浅青绿，用于徽章/选中背景
    onPrimaryContainer = Color(0xFF002020),     // 主色容器上的文字色
    secondary = Teal600,                        // 次色：中青绿
    onSecondary = Color.White,                  // 次色上的文字色
    secondaryContainer = Teal50,                // 次色容器：极浅青绿
    onSecondaryContainer = Teal900,            // 次色容器上的文字色
    tertiary = AccentOrange,                    // 三级色：橙色（强调/警告）
    onTertiary = Color.White,                   // 三级色上的文字色
    tertiaryContainer = OrangeBadgeBg,          // 三级色容器：橙色徽章背景
    onTertiaryContainer = Color(0xFFBF360C),    // 三级色容器上的文字色
    error = Color(0xFFBA1A1A),                  // 错误色：红
    errorContainer = Color(0xFFFFDAD6),         // 错误色容器：浅红
    onError = Color.White,                      // 错误色上的文字色
    onErrorContainer = Color(0xFF410002),      // 错误色容器上的文字色
    background = SurfaceLight,                  // 页面背景色
    onBackground = OnSurfaceLight,              // 背景上的文字色
    surface = SurfaceLight,                     // 卡片/Surface 表面色
    onSurface = OnSurfaceLight,                 // Surface 上的文字色
    surfaceVariant = SurfaceVariantLight,       // Surface 变体：分隔线/次要背景
    onSurfaceVariant = OnSurfaceVariantLight,   // Surface 变体上的文字色
    outline = OutlineLight,                     // 描边色：未选中边框
    outlineVariant = SurfaceVariantLight        // 描边变体：弱化边框
)

/** 暗色配色方案：主色调亮以适配深色背景，容器使用深青色。 */
private val DarkColorScheme = darkColorScheme( // 深色配色
    primary = DarkTeal300,                      // 主色：亮青绿（暗色背景下提高对比）
    onPrimary = Color(0xFF003737),              // 主色上的文字色
    primaryContainer = Teal900,                 // 主色容器：深青绿
    onPrimaryContainer = DarkTeal200,           // 主色容器上的文字色
    secondary = DarkTeal200,                     // 次色：中亮青绿
    onSecondary = Color(0xFF003737),            // 次色上的文字色
    secondaryContainer = Color(0xFF00504E),     // 次色容器：深青
    onSecondaryContainer = Teal50,              // 次色容器上的文字色
    tertiary = Color(0xFFFFB59D),               // 三级色：浅橙
    onTertiary = Color(0xFF5F1400),            // 三级色上的文字色
    tertiaryContainer = Color(0xFF872100),      // 三级色容器：深橙
    onTertiaryContainer = Color(0xFFFFDBCF),    // 三级色容器上的文字色
    error = Color(0xFFFFB4AB),                  // 错误色：亮红
    errorContainer = Color(0xFF93000A),         // 错误色容器：深红
    onError = Color(0xFF690005),                // 错误色上的文字色
    onErrorContainer = Color(0xFFFFDAD6),      // 错误色容器上的文字色
    background = Color(0xFF191C1C),             // 页面背景色：深灰青
    onBackground = Color(0xFFE0E3E1),           // 背景上的文字色
    surface = Color(0xFF191C1C),                // Surface 表面色
    onSurface = Color(0xFFE0E3E1),              // Surface 上的文字色
    surfaceVariant = Color(0xFF3F4946),         // Surface 变体：分隔线/次要背景
    onSurfaceVariant = Color(0xFFBEC9C4),       // Surface 变体上的文字色
    outline = Color(0xFF89938F),                // 描边色
    outlineVariant = Color(0xFF3F4946)          // 描边变体
)
