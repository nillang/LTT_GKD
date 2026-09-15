package com.ltt.gkd.data.rule  // 声明包名，规则数据模型所在的包

import com.squareup.moshi.JsonClass  // 导入 Moshi 的 JsonClass 注解，用于自动生成 JSON 适配器

/**
 * 规则来源。
 * - LOCAL：本地用户手写，私有
 * - SUBSCRIBED：从远程订阅拉取，共享
 * - BUILT_IN：内置规则（随 APK 发布）
 */
enum class RuleSource { LOCAL, SUBSCRIBED, BUILT_IN }  // 规则来源枚举：本地/订阅/内置

/**
 * 规则匹配目标：在控件树/截图中定位广告跳过按钮。
 *
 * - [TEXT]：匹配 [AccessibilityNodeInfo.getText] / [getContentDescription]
 * - [ID]：匹配 viewId（如 com.tencent.mm:id/skip_btn）
 * - [DESC]：仅匹配 contentDescription
 * - [OCR]：截图识别文字坐标（兜底）
 *
 * @param type 匹配方式（默认 TEXT）
 * @param text 关键词或正则表达式列表
 * @param ids 当 type=ID 时使用的 viewId 列表（全名或短名皆可）
 * @param regex 是否将 [text] 视为正则表达式
 * @param caseInsensitive 文本匹配是否大小写不敏感（默认 true）
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成该数据类的 JSON 适配器
data class MatchTarget(  // 匹配目标数据类，描述如何在控件树中定位广告跳过按钮
    val type: MatchType = MatchType.TEXT,  // 匹配方式，默认按文本匹配
    val text: List<String> = emptyList(),  // 关键词或正则表达式列表
    val ids: List<String> = emptyList(),  // 当 type=ID 时使用的 viewId 列表
    val regex: Boolean = false,  // 是否将 text 视为正则表达式
    val caseInsensitive: Boolean = true  // 文本匹配是否大小写不敏感
)

/**
 * 规则匹配类型。
 *
 * - [TEXT]：匹配节点文本或 contentDescription
 * - [ID]：匹配节点 viewIdResourceName
 * - [DESC]：仅匹配 contentDescription
 * - [OCR]：截图 OCR 识别（由 OcrManager 处理，RuleMatcher 不实现）
 */
enum class MatchType { TEXT, ID, DESC, OCR }  // 匹配类型枚举：文本/ID/描述/OCR

/**
 * 匹配后执行的动作。
 *
 * - [CLICK_NODE]：点击命中的节点本身（或其可点击祖先）
 * - [CLICK_COORD]：点击节点中心坐标（用于不可点击节点）
 * - [BACK]：按返回键（用于弹窗型广告）
 * - [GESTURE_TAP]：通过 dispatchGesture 在坐标处点击（OCR 路径）
 *
 * @param type 动作类型（默认 CLICK_NODE）
 * @param delayMs 命中到执行动作之间的延迟毫秒数（0 表示立即）
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成该数据类的 JSON 适配器
data class MatchAction(  // 匹配后执行的动作数据类
    val type: ActionType = ActionType.CLICK_NODE,  // 动作类型，默认点击节点
    val delayMs: Long = 0L  // 命中到执行动作之间的延迟毫秒数
)

/**
 * 动作类型枚举。
 *
 * - [CLICK_NODE]：点击命中节点本身（或冒泡到可点击祖先）
 * - [CLICK_COORD]：点击节点中心坐标（用于不可点击节点）
 * - [BACK]：模拟返回键（用于弹窗型广告）
 * - [GESTURE_TAP]：通过 dispatchGesture 在坐标处点击（OCR 路径）
 */
enum class ActionType { CLICK_NODE, CLICK_COORD, BACK, GESTURE_TAP }  // 动作类型枚举：点击节点/点击坐标/返回/手势点击

/**
 * 单条规则。
 *
 * @param id 全局唯一；建议格式 `<pkg>_<scene>`，如 `com.tencent.mm_splash`
 * @param packageName 目标包名；空表示通用兜底
 * @param activity 可选：限定 Activity（短类名或全限定名皆可）；空表示任意
 * @param enabled 是否启用
 * @param priority 优先级，越大越优先；同包多规则按 priority 降序匹配
 * @param throttleMs 同包同规则节流窗口，避免短时多次触发
 * @param author 作者标识（设备 ID 或用户名），用于上传时追溯
 * @param createdAt 创建时间戳（毫秒）；本地规则按此倒序排序
 * @param subscribers 订阅数；订阅规则按此倒序排序
 * @param source 规则来源（本地/订阅/内置），运行时不存 JSON，由文件路径决定
 * @param uploaded 该本地规则是否已被当前用户上传/分享到社区；用于在本地卡片展示
 *   "已分享"特殊标记，并与订阅同步回来的使用量联动显示（让上传更有成就感）
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成该数据类的 JSON 适配器
data class Rule(  // 单条规则数据类，描述如何识别并跳过某应用的广告
    val id: String,  // 规则全局唯一 ID
    val name: String,  // 规则显示名称
    val packageName: String = "",  // 目标应用包名，空表示通用兜底规则
    val activity: String? = null,  // 可选：限定 Activity，空表示任意
    val enabled: Boolean = true,  // 是否启用该规则
    val priority: Int = 0,  // 优先级，越大越优先匹配
    val throttleMs: Long = 2000L,  // 节流窗口毫秒数，避免短时多次触发
    val author: String = "",  // 作者标识，用于上传时追溯
    val createdAt: Long = 0L,  // 创建时间戳（毫秒）
    val subscribers: Int = 0,  // 订阅数
    val source: RuleSource = RuleSource.LOCAL,  // 规则来源（本地/订阅/内置）
    val uploaded: Boolean = false,  // 是否已被当前用户上传/分享到社区（本地卡片特殊标记用）
    val match: MatchTarget = MatchTarget(),  // 匹配目标，描述如何定位广告按钮
    val action: MatchAction = MatchAction()  // 命中后执行的动作
)

/**
 * 一组规则文件（按类别组织）。
 *
 * 持久化时一个文件对应一个 RuleSet，便于整体替换/订阅。
 *
 * @param name 规则集名称（用于 UI 展示）
 * @param version 版本号，用于升级迁移
 * @param author 作者标识
 * @param rules 该集合下的所有规则列表
 */
@JsonClass(generateAdapter = true)  // 标记 Moshi 自动生成该数据类的 JSON 适配器
data class RuleSet(  // 规则集数据类，一个文件对应一个 RuleSet
    val name: String,  // 规则集名称（用于 UI 展示）
    val version: Int = 1,  // 版本号，用于升级迁移
    val author: String = "",  // 作者标识
    val rules: List<Rule> = emptyList()  // 该集合下的所有规则列表
)

/**
 * 规则合集（仅运行时用于 UI 分组，不参与序列化）。
 *
 * 一个内置规则文件（[RuleSet]）即一个"合集"，如"社交资讯类""视频直播类"。
 * 内置 Tab 按合集分组展示，用户既可切换单条规则，也可一键启用/禁用整个合集。
 *
 * @param name 合集名称（取自 [RuleSet.name]，用于 UI 展示）
 * @param fileName 合集来源文件名（assets/rules 下的 .json，用于原文预览）
 * @param rules 该合集下（已按已安装应用过滤后的）规则列表
 */
data class RuleGroup(  // 规则合集数据类，运行时 UI 分组用
    val name: String,  // 合集名称
    val fileName: String = "",  // 来源文件名（预览用）
    val rules: List<Rule> = emptyList()  // 合集内规则列表
)
