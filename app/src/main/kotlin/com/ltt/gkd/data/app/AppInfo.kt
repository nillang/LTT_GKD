package com.ltt.gkd.data.app  // 声明包名，应用信息数据类所在的包

/**
 * 已安装应用的轻量信息（用于 UI 展示和规则编辑）。
 *
 * 不持有 ApplicationInfo 引用，避免长生命周期持有。
 * 图标通过 [AppListRepository.loadIcon] 按需加载。
 *
 * @param packageName 应用包名（唯一标识）
 * @param label 应用显示名（用户可见名）
 * @param versionName 版本名字符串（如 "1.2.3"），可能为 null
 * @param versionCode 版本号（数字），用于比较版本新旧
 * @param isSystem 是否为系统应用（含 FLAG_SYSTEM）
 * @param isEnabled 应用是否启用（被禁用的应用通常不出现在启动器）
 * @param sourceDir APK 文件路径，用于按需加载图标/资源
 */
data class AppInfo(  // 已安装应用轻量信息数据类
    val packageName: String,  // 应用包名（唯一标识）
    val label: String,  // 应用显示名（用户可见名）
    val versionName: String?,  // 版本名字符串，可能为 null
    val versionCode: Long,  // 版本号（数字）
    val isSystem: Boolean,  // 是否为系统应用
    val isEnabled: Boolean,  // 应用是否启用
    val sourceDir: String?  // APK 文件路径
)
