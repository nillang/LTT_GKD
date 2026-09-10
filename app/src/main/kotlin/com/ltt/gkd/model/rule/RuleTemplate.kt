package com.ltt.gkd.model.rule

/**
 * 规则模板：按广告场景预填字段，让用户在 RuleEditScreen 创建规则时有引导。
 *
 * 设计原则：
 * - 模板只决定"默认值"，用户仍可自由编辑
 * - 每个模板对应一类典型广告场景，覆盖 90% 实际需求
 * - 模板自带推荐匹配关键词组合（按主流广告 SDK 布局经验）
 */
enum class RuleTemplate {
    /** 开屏广告：App 启动时全屏 3-5 秒，右上/右下角"跳过"按钮 */
    SPLASH,

    /** 插屏弹窗：页面切换或操作触发，"×"关闭按钮 */
    POPUP,

    /** Banner 横幅：页面顶部/底部条状，藏在小三角里 */
    BANNER,

    /** 激励视频：看广告换奖励，倒计时结束才出现关闭按钮 */
    REWARD_VIDEO,

    /** 通用兜底：跨 App 关键词匹配 */
    UNIVERSAL
}

/** 模板元信息：用于 UI 列表展示。 */
data class TemplateMeta(
    val template: RuleTemplate,
    val title: String,
    val description: String,
    val recommendedPriority: Int,
    val recommendedThrottleMs: Long
)

/** 模板预填的 Rule（id/name/packageName 留空，由用户填）。 */
fun RuleTemplate.toRule(
    packageName: String = "",
    appLabel: String = ""
): Rule {
    val suffix = when (this) {
        RuleTemplate.SPLASH -> "splash"
        RuleTemplate.POPUP -> "popup"
        RuleTemplate.BANNER -> "banner"
        RuleTemplate.REWARD_VIDEO -> "reward"
        RuleTemplate.UNIVERSAL -> "universal"
    }
    val idHint = if (packageName.isEmpty()) "" else "${packageName}_$suffix"
    val nameHint = if (appLabel.isEmpty()) "" else "$appLabel${titleSuffix()}"

    return when (this) {
        RuleTemplate.SPLASH -> Rule(
            id = idHint,
            name = nameHint,
            packageName = packageName,
            priority = 100,
            throttleMs = 5000L,
            match = MatchTarget(
                type = MatchType.TEXT,
                text = listOf("跳过", "跳过广告", "Skip", "skip"),
                caseInsensitive = true
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)
        )

        RuleTemplate.POPUP -> Rule(
            id = idHint,
            name = nameHint,
            packageName = packageName,
            priority = 80,
            throttleMs = 3000L,
            match = MatchTarget(
                type = MatchType.TEXT,
                text = listOf("关闭", "关闭广告", "×", "不再提示"),
                caseInsensitive = true
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)
        )

        RuleTemplate.BANNER -> Rule(
            id = idHint,
            name = nameHint,
            packageName = packageName,
            priority = 60,
            throttleMs = 3000L,
            match = MatchTarget(
                type = MatchType.TEXT,
                text = listOf("关闭", "不感兴趣", "×"),
                caseInsensitive = true
            ),
            action = MatchAction(type = ActionType.CLICK_COORD)
        )

        RuleTemplate.REWARD_VIDEO -> Rule(
            id = idHint,
            name = nameHint,
            packageName = packageName,
            priority = 70,
            throttleMs = 30000L, // 激励视频通常 15-30 秒才出关闭按钮
            match = MatchTarget(
                type = MatchType.TEXT,
                text = listOf("关闭", "领取奖励", "跳过"),
                caseInsensitive = true
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)
        )

        RuleTemplate.UNIVERSAL -> Rule(
            id = "universal_${System.currentTimeMillis()}",
            name = "通用兜底",
            packageName = "",
            priority = 1,
            throttleMs = 5000L,
            match = MatchTarget(
                type = MatchType.TEXT,
                text = listOf("跳过", "Skip"),
                caseInsensitive = true
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)
        )
    }
}

private fun RuleTemplate.titleSuffix(): String = when (this) {
    RuleTemplate.SPLASH -> "开屏"
    RuleTemplate.POPUP -> "弹窗"
    RuleTemplate.BANNER -> "Banner"
    RuleTemplate.REWARD_VIDEO -> "激励视频"
    RuleTemplate.UNIVERSAL -> "通用"
}

/** 所有模板的元信息，供 UI 列表展示。 */
val ALL_TEMPLATES: List<TemplateMeta> = listOf(
    TemplateMeta(
        template = RuleTemplate.SPLASH,
        title = "开屏广告",
        description = "App 启动时全屏展示 3-5 秒，跳过按钮位置不固定",
        recommendedPriority = 100,
        recommendedThrottleMs = 5000L
    ),
    TemplateMeta(
        template = RuleTemplate.POPUP,
        title = "插屏弹窗",
        description = "页面切换或操作触发的全屏弹窗，\"×\"关闭按钮常在右上角",
        recommendedPriority = 80,
        recommendedThrottleMs = 3000L
    ),
    TemplateMeta(
        template = RuleTemplate.BANNER,
        title = "Banner 横幅",
        description = "页面顶部/底部条状广告，关闭按钮藏在小三角里",
        recommendedPriority = 60,
        recommendedThrottleMs = 3000L
    ),
    TemplateMeta(
        template = RuleTemplate.REWARD_VIDEO,
        title = "激励视频",
        description = "看广告换奖励，倒计时结束才出现关闭按钮",
        recommendedPriority = 70,
        recommendedThrottleMs = 30000L
    ),
    TemplateMeta(
        template = RuleTemplate.UNIVERSAL,
        title = "通用兜底",
        description = "跨 App 关键词匹配，作为最后兜底",
        recommendedPriority = 1,
        recommendedThrottleMs = 5000L
    )
)
