package com.ltt.gkd.ui.theme // 声明包名

import androidx.compose.material3.Typography // 导入 Material3 字体排版
import androidx.compose.ui.text.TextStyle // 导入文本样式
import androidx.compose.ui.text.font.FontWeight // 导入字体粗细
import androidx.compose.ui.unit.sp // 导入 sp 单位

/**
 * LTT_GKD 字体排版。
 *
 * 使用 Material3 默认字体族，仅微调正文/标题字号使其更紧凑
 * （工具类应用，信息密度较高）。
 *
 * 排版阶梯说明：
 * - bodyMedium：正文默认字号（14sp），用于普通文本、列表项主文本。
 * - bodySmall：辅助正文字号（12sp），用于次要说明、副文本。
 * - titleMedium：中号标题（18sp），用于卡片标题、对话框标题。
 * - titleLarge：大号标题（22sp），用于 TopAppBar 主标题。
 * - labelMedium：中号标签（12sp），用于按钮、Chip 文本。
 * - labelSmall：小号标签（10sp），用于状态标签、徽标。
 */
val LTTGKDTypography = Typography( // 应用全局字体排版
    bodyMedium = TextStyle( // 中号正文
        fontSize = 14.sp, // 14sp
        fontWeight = FontWeight.Normal // 常规字重
    ),
    bodySmall = TextStyle( // 小号正文
        fontSize = 12.sp, // 12sp
        fontWeight = FontWeight.Normal // 常规字重
    ),
    titleMedium = TextStyle( // 中号标题
        fontSize = 18.sp, // 18sp
        fontWeight = FontWeight.Medium // 中等字重
    ),
    titleLarge = TextStyle( // 大号标题
        fontSize = 22.sp, // 22sp
        fontWeight = FontWeight.Medium // 中等字重
    ),
    labelMedium = TextStyle( // 中号标签
        fontSize = 12.sp, // 12sp
        fontWeight = FontWeight.Medium // 中等字重
    ),
    labelSmall = TextStyle( // 小号标签
        fontSize = 10.sp, // 10sp
        fontWeight = FontWeight.Normal // 常规字重
    )
)
