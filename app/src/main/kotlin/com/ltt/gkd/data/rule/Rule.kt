package com.ltt.gkd.data.rule

import com.squareup.moshi.JsonClass

/**
 * 规则来源。
 * - LOCAL：本地用户手写，私有
 * - SUBSCRIBED：从远程订阅拉取，共享
 * - BUILT_IN：内置规则（随 APK 发布）
 */
enum class RuleSource { LOCAL, SUBSCRIBED, BUILT_IN }

/**
 * 规则匹配目标：在控件树/截图中定位广告跳过按钮。
 *
 * - [TEXT]：匹配 [AccessibilityNodeInfo.getText] / [getContentDescription]
 * - [ID]：匹配 viewId（如 com.tencent.mm:id/skip_btn）
 * - [DESC]：仅匹配 contentDescription
 * - [OCR]：截图识别文字坐标（兜底）
 */
@JsonClass(generateAdapter = true)
data class MatchTarget(
    val type: MatchType = MatchType.TEXT,
    val text: List<String> = emptyList(),
    val ids: List<String> = emptyList(),
    val regex: Boolean = false,
    val caseInsensitive: Boolean = true
)

enum class MatchType { TEXT, ID, DESC, OCR }

/**
 * 匹配后执行的动作。
 *
 * - [CLICK_NODE]：点击命中的节点本身（或其可点击祖先）
 * - [CLICK_COORD]：点击节点中心坐标（用于不可点击节点）
 * - [BACK]：按返回键（用于弹窗型广告）
 * - [GESTURE_TAP]：通过 dispatchGesture 在坐标处点击（OCR 路径）
 */
@JsonClass(generateAdapter = true)
data class MatchAction(
    val type: ActionType = ActionType.CLICK_NODE,
    val delayMs: Long = 0L
)

enum class ActionType { CLICK_NODE, CLICK_COORD, BACK, GESTURE_TAP }

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
 */
@JsonClass(generateAdapter = true)
data class Rule(
    val id: String,
    val name: String,
    val packageName: String = "",
    val activity: String? = null,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val throttleMs: Long = 2000L,
    val author: String = "",
    val createdAt: Long = 0L,
    val subscribers: Int = 0,
    val source: RuleSource = RuleSource.LOCAL,
    val match: MatchTarget = MatchTarget(),
    val action: MatchAction = MatchAction()
)

/** 一组规则文件（按类别组织）。 */
@JsonClass(generateAdapter = true)
data class RuleSet(
    val name: String,
    val version: Int = 1,
    val author: String = "",
    val rules: List<Rule> = emptyList()
)
