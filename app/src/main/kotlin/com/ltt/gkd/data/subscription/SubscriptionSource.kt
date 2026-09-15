package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

import com.squareup.moshi.JsonClass  // 导入 Moshi 的 JsonClass 注解，用于自动生成 JSON 适配器

/**
 * 订阅源类型（由 URL 归一化时自动识别，无需用户手选）。
 *
 * - [GIST]：GitHub Gist（链接或 32 位十六进制 ID），走 Gist API，可含多份规则文件
 * - [URL]：任意指向 RuleSet JSON 的 http(s) 链接，直接 GET 解析
 */
enum class SourceType { GIST, URL }  // 订阅源类型枚举

/**
 * 单个订阅源。
 *
 * 一个源 = 一个链接（Gist ID/Gist 链接/任意 RuleSet JSON 的 URL）。
 * 订阅者只需粘贴链接即可，无需 GitHub 账号或 Token（仅"上传规则的作者"才需要 GitHub）。
 *
 * @param id 源唯一 ID（内部生成，用于规则文件前缀与去重）
 * @param name 用户可见名称
 * @param url 订阅链接（Gist 链接/ID 或普通 URL）
 * @param type 源类型（添加时按 [url] 自动识别）
 * @param enabled 是否启用（禁用后自动更新会跳过该源，但其已下载规则仍保留）
 * @param lastSyncAt 上次成功同步时间戳（毫秒），0 表示从未同步
 * @param lastRuleCount 上次同步得到的规则条数
 * @param lastError 上次同步的错误信息（成功则清空）
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成该数据类的 JSON 适配器
data class SubscriptionSource(  // 订阅源数据类
    val id: String,  // 源唯一 ID
    val name: String,  // 显示名称
    val url: String,  // 订阅链接
    val type: SourceType = SourceType.URL,  // 源类型（自动识别）
    val enabled: Boolean = true,  // 是否启用
    val lastSyncAt: Long = 0L,  // 上次成功同步时间戳
    val lastRuleCount: Int = 0,  // 上次同步规则条数
    val lastError: String = ""  // 上次同步错误信息
)
