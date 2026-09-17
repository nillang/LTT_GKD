package com.ltt.gkd.data.rule  // 声明包名，规则模板所在的包

/**
 * 规则模板：按广告场景预填字段，让用户在 RuleEditScreen 创建规则时有引导。
 *
 * 设计原则：
 * - 模板只决定"默认值"，用户仍可自由编辑
 * - 每个模板对应一类典型广告场景，覆盖 90% 实际需求
 * - 模板自带推荐匹配关键词组合（按主流广告 SDK 布局经验）
 */
enum class RuleTemplate {  // 规则模板枚举类，按广告场景预填字段
    /** 开屏广告：App 启动时全屏 3-5 秒，右上/右下角"跳过"按钮 */
    SPLASH,  // 开屏广告模板

    /** 插屏弹窗：页面切换或操作触发，"×"关闭按钮 */
    POPUP,  // 插屏弹窗模板

    /** Banner 横幅：页面顶部/底部条状，藏在小三角里 */
    BANNER,  // Banner 横幅模板

    /** 激励视频：看广告换奖励，倒计时结束才出现关闭按钮 */
    REWARD_VIDEO,  // 激励视频模板

    /** 通用兜底：跨 App 关键词匹配 */
    UNIVERSAL  // 通用兜底模板
}

/**
 * 模板元信息：用于 UI 列表展示。
 *
 * @property template 对应的模板枚举。
 * @property title UI 显示的标题（如"开屏广告"）。
 * @property description 简短说明该模板适用的广告场景。
 * @property recommendedPriority 推荐优先级（数字越大越优先匹配）。
 * @property recommendedThrottleMs 推荐节流间隔（毫秒），避免短时间内重复触发。
 */
data class TemplateMeta(  // 模板元信息数据类
    val template: RuleTemplate,  // 对应的模板枚举
    val title: String,  // UI 显示标题
    val description: String,  // 适用场景描述
    val recommendedPriority: Int,  // 推荐优先级
    val recommendedThrottleMs: Long  // 推荐节流间隔（毫秒）
)

/**
 * 根据模板生成预填字段的 [Rule]（id/name/packageName 由调用方提供，其余字段按模板场景预填）。
 *
 * @param packageName 应用包名，用于拼接规则 ID 前缀（可空）。
 * @param appLabel 应用显示名，用于拼接规则默认名称（可空）。
 * @return 已预填关键字、优先级、节流时间等字段的 Rule。
 */
fun RuleTemplate.toRule(  // 模板转 Rule 的扩展函数
    packageName: String = "",  // 应用包名，默认空
    appLabel: String = ""  // 应用显示名，默认空
): Rule {  // 返回预填好的 Rule
    // 不同模板使用不同的 ID 后缀，避免不同模板规则互相覆盖
    val suffix = when (this) {  // 按当前模板选择 ID 后缀
        RuleTemplate.SPLASH -> "splash"  // 开屏
        RuleTemplate.POPUP -> "popup"  // 弹窗
        RuleTemplate.BANNER -> "banner"  // Banner
        RuleTemplate.REWARD_VIDEO -> "reward"  // 激励视频
        RuleTemplate.UNIVERSAL -> "universal"  // 通用
    }
    val idHint = if (packageName.isEmpty()) "" else "${packageName}_$suffix"  // 包名非空则拼接 ID 提示
    val nameHint = if (appLabel.isEmpty()) "" else "$appLabel${titleSuffix()}"  // 应用名非空则拼接名称提示

    return when (this) {  // 按当前模板构造 Rule
        RuleTemplate.SPLASH -> Rule(  // 开屏广告规则
            id = idHint,  // 规则 ID
            name = nameHint,  // 规则名
            packageName = packageName,  // 包名
            priority = 100,  // 开屏优先级最高
            throttleMs = 5000L,  // 节流 5 秒
            match = MatchTarget(  // 匹配目标
                type = MatchType.TEXT,  // 文本匹配
                text = listOf("跳过", "跳过广告", "Skip", "skip"),  // 跳过关键词
                caseInsensitive = true  // 大小写不敏感
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)  // 点击节点
        )

        RuleTemplate.POPUP -> Rule(  // 插屏弹窗规则
            id = idHint,  // 规则 ID
            name = nameHint,  // 规则名
            packageName = packageName,  // 包名
            priority = 80,  // 弹窗优先级
            throttleMs = 3000L,  // 节流 3 秒
            match = MatchTarget(  // 匹配目标
                type = MatchType.DESC,  // 描述匹配：弹窗关闭按钮常在 contentDescription 中
                text = listOf("关闭", "关闭广告", "×", "不再提示"),  // 关闭关键词
                caseInsensitive = true  // 大小写不敏感
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)  // 点击节点
        )

        RuleTemplate.BANNER -> Rule(  // Banner 横幅规则
            id = idHint,  // 规则 ID
            name = nameHint,  // 规则名
            packageName = packageName,  // 包名
            priority = 60,  // Banner 优先级
            throttleMs = 3000L,  // 节流 3 秒
            match = MatchTarget(  // 匹配目标
                type = MatchType.TEXT,  // 文本匹配
                text = listOf("关闭", "不感兴趣", "×"),  // 关闭关键词
                caseInsensitive = true  // 大小写不敏感
            ),
            // Banner 通常关闭按钮很小且位置不固定，使用坐标点击更稳
            action = MatchAction(type = ActionType.CLICK_COORD)  // 点击坐标
        )

        RuleTemplate.REWARD_VIDEO -> Rule(  // 激励视频规则
            id = idHint,  // 规则 ID
            name = nameHint,  // 规则名
            packageName = packageName,  // 包名
            priority = 70,  // 激励视频优先级
            throttleMs = 30000L, // 激励视频通常 15-30 秒才出关闭按钮
            match = MatchTarget(  // 匹配目标
                type = MatchType.TEXT,  // 文本匹配
                text = listOf("关闭", "领取奖励", "跳过"),  // 关闭/领取关键词
                caseInsensitive = true  // 大小写不敏感
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)  // 点击节点
        )

        RuleTemplate.UNIVERSAL -> Rule(  // 通用兜底规则
            // 通用兜底规则使用时间戳保证 id 唯一
            id = "universal_${System.currentTimeMillis()}",  // 时间戳生成唯一 ID
            name = "通用兜底",  // 规则名固定
            packageName = "",  // 通用不限定包名
            priority = 1,  // 最低优先级作为兜底
            throttleMs = 5000L,  // 节流 5 秒
            match = MatchTarget(  // 匹配目标
                type = MatchType.TEXT,  // 文本匹配
                text = listOf("跳过", "Skip"),  // 通用跳过关键词
                caseInsensitive = true  // 大小写不敏感
            ),
            action = MatchAction(type = ActionType.CLICK_NODE)  // 点击节点
        )
    }
}

/**
 * 返回该模板对应的中文场景标签，用于拼接规则默认名称（如 "微信开屏"）。
 */
private fun RuleTemplate.titleSuffix(): String = when (this) {  // 模板对应中文场景标签
    RuleTemplate.SPLASH -> "开屏"  // 开屏
    RuleTemplate.POPUP -> "弹窗"  // 弹窗
    RuleTemplate.BANNER -> "Banner"  // Banner
    RuleTemplate.REWARD_VIDEO -> "激励视频"  // 激励视频
    RuleTemplate.UNIVERSAL -> "通用"  // 通用
}

/** 所有模板的元信息，供 UI 列表展示。 */
val ALL_TEMPLATES: List<TemplateMeta> = listOf(  // 全部模板元信息列表
    TemplateMeta(  // 开屏广告元信息
        template = RuleTemplate.SPLASH,  // 模板枚举
        title = "开屏广告",  // 标题
        description = "App 启动时全屏展示 3-5 秒，跳过按钮位置不固定",  // 描述
        recommendedPriority = 100,  // 推荐优先级
        recommendedThrottleMs = 5000L  // 推荐节流间隔
    ),
    TemplateMeta(  // 插屏弹窗元信息
        template = RuleTemplate.POPUP,  // 模板枚举
        title = "插屏弹窗",  // 标题
        description = "页面切换或操作触发的全屏弹窗，\"×\"关闭按钮常在右上角",  // 描述
        recommendedPriority = 80,  // 推荐优先级
        recommendedThrottleMs = 3000L  // 推荐节流间隔
    ),
    TemplateMeta(  // Banner 横幅元信息
        template = RuleTemplate.BANNER,  // 模板枚举
        title = "Banner 横幅",  // 标题
        description = "页面顶部/底部条状广告，关闭按钮藏在小三角里",  // 描述
        recommendedPriority = 60,  // 推荐优先级
        recommendedThrottleMs = 3000L  // 推荐节流间隔
    ),
    TemplateMeta(  // 激励视频元信息
        template = RuleTemplate.REWARD_VIDEO,  // 模板枚举
        title = "激励视频",  // 标题
        description = "看广告换奖励，倒计时结束才出现关闭按钮",  // 描述
        recommendedPriority = 70,  // 推荐优先级
        recommendedThrottleMs = 30000L  // 推荐节流间隔
    ),
    TemplateMeta(  // 通用兜底元信息
        template = RuleTemplate.UNIVERSAL,  // 模板枚举
        title = "通用兜底",  // 标题
        description = "跨 App 关键词匹配，作为最后兜底",  // 描述
        recommendedPriority = 1,  // 推荐优先级
        recommendedThrottleMs = 5000L  // 推荐节流间隔
    )
)
