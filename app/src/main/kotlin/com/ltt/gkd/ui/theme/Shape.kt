package com.ltt.gkd.ui.theme // 声明包名

import androidx.compose.material3.Shapes // 导入 Material3 形状集合
import androidx.compose.foundation.shape.RoundedCornerShape // 导入圆角形状
import androidx.compose.ui.unit.dp // 导入 dp 单位

/**
 * LTT_GKD 圆角定义。
 *
 * 统一使用 Material3 推荐的圆角阶梯，比默认略圆一点，
 * 让应用整体看起来更柔和亲和。
 *
 * 圆角阶梯说明：
 * - extraSmall：极小圆角，用于紧凑型组件（如 Chip、小图标背景）。
 * - small：小圆角，用于卡片、列表项背景。
 * - medium：中等圆角，用于对话框、Surface 容器。
 * - large：大圆角，用于 BottomSheet、大卡片。
 * - extraLarge：超大圆角，用于 FAB、全屏弹层顶部。
 */
val LTTGKDShapes = Shapes( // 应用全局形状定义
    extraSmall = RoundedCornerShape(4.dp), // 极小圆角 4dp
    small = RoundedCornerShape(8.dp), // 小圆角 8dp
    medium = RoundedCornerShape(12.dp), // 中等圆角 12dp
    large = RoundedCornerShape(16.dp), // 大圆角 16dp
    extraLarge = RoundedCornerShape(28.dp) // 超大圆角 28dp
)
