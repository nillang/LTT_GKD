package com.ltt.gkd.ui.theme // 声明包名

import androidx.compose.ui.graphics.Color // 导入 Compose 颜色

/**
 * LTT_GKD 品牌色（UI v5 设计稿）。
 *
 * 设计理念：深青绿（Teal）象征"自动跳过、清爽无广告"，
 * 取自 static/design/ui_design_v5.html 设计规范。
 */

// ---- 主品牌色（深青绿 Teal） ----
val Teal700 = Color(0xFF007070)   // primary，主色
val Teal600 = Color(0xFF00897B)   // primary-light，主色亮色变体
val Teal900 = Color(0xFF004D4D)   // primary-dark，主色深色变体
val Teal200 = Color(0xFF9CF1F0)   // primary-container，主色容器色
val Teal50 = Color(0xFFE0F5F4)    // primary-container-light，主色容器浅色

// ---- 中性色 ----
val SurfaceLight = Color(0xFFFAFFFE) // 亮色背景 surface 色
val SurfaceVariantLight = Color(0xFFDAE5E2) // 亮色 surface 变体
val OnSurfaceLight = Color(0xFF191C1B) // 亮色 surface 上的文字色
val OnSurfaceVariantLight = Color(0xFF3F4946) // 亮色 surface 变体上的文字色
val OutlineLight = Color(0xFF6F7976) // 亮色描边色

// ---- 强调色（标签/图标） ----
val AccentOrange = Color(0xFFE65100) // 强调橙色
val AccentBlue = Color(0xFF1565C0) // 强调蓝色
val AccentPurple = Color(0xFF7B1FA2) // 强调紫色
val AccentAmber = Color(0xFFFF8F00) // 强调琥珀色
val AccentPink = Color(0xFFE91E63) // 强调粉色

// 浅色背景底（徽章用）
val BlueBadgeBg = Color(0xFFE3F2FD) // 蓝色徽章背景
val OrangeBadgeBg = Color(0xFFFFF3E0) // 橙色徽章背景
val PurpleBadgeBg = Color(0xFFF3E5F5) // 紫色徽章背景
val AmberBadgeBg = Color(0xFFFFF8E1) // 琥珀色徽章背景

// ---- 深色模式 ----
val DarkTeal300 = Color(0xFF4DB6AC) // 深色模式主色变体
val DarkTeal200 = Color(0xFF80CBC4) // 深色模式主色容器

// ---- 深色模式徽章变体（降低背景明度、提高前景明度） ----
val DarkBlueBadgeBg = Color(0xFF0D2535)    // 深色蓝色徽章背景
val DarkOrangeBadgeBg = Color(0xFF33200A)  // 深色橙色徽章背景
val DarkPurpleBadgeBg = Color(0xFF2A0E35)  // 深色紫色徽章背景
val DarkAmberBadgeBg = Color(0xFF332600)   // 深色琥珀色徽章背景
val DarkAccentBlue = Color(0xFF7BAFEE)     // 深色模式蓝色前景
val DarkAccentOrange = Color(0xFFFF9866)   // 深色模式橙色前景
val DarkAccentPurple = Color(0xFFCE93D8)   // 深色模式紫色前景
val DarkAccentAmber = Color(0xFFFFD54F)    // 深色模式琥珀色前景
