package com.ltt.gkd.model.app

/**
 * 已安装应用的轻量信息（用于 UI 展示和规则编辑）。
 *
 * 不持有 ApplicationInfo 引用，避免长生命周期持有。
 * 图标通过 [AppListRepository.loadIcon] 按需加载。
 */
data class AppInfo(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val isSystem: Boolean,
    val isEnabled: Boolean,
    val sourceDir: String?
)
