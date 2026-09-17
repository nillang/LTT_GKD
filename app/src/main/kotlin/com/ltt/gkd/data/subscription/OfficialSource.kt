package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

/**
 * 官方规则源配置（开发者维护的默认订阅源）。
 *
 * 设计：开发者（作者）维护一个官方规则库——本地 `official_rules/` 目录存放规则 JSON，
 * 定期上传到线上。APP 内置该源的默认订阅地址，用户首次启动自动添加并同步，开箱即用。
 *
 * 线上保存方式的演进（订阅机制不变，只改 [ADDRESS] 这一个值）：
 * - 当前：GitHub Gist（免费、多文件、订阅端匿名零配置），ADDRESS 填 32 位 Gist ID；
 * - 未来：开发者自购云服务器后，把规则 JSON 放到服务器静态目录，ADDRESS 改成 http(s):// 直链。
 * 两者均被 [SubscriptionUrls.detectType] 自动识别（GIST / URL）。
 *
 * 注意：用户上传自己规则的"个人 Gist"仍走既有「规则编辑页 → 上传」链路；此处仅指官方默认源。
 */
object OfficialSource {  // 官方规则源配置对象

    /** 官方源的显示名称。 */
    const val NAME = "官方规则库"  // 显示名称

    /**
     * 官方默认订阅地址。
     *
     * 推荐方案（免费 + 国内加速）：把规则 JSON 推到一个公开 GitHub 仓库后，
     * 用 jsDelivr 生成稳定直链，例如：
     * `https://cdn.jsdelivr.net/gh/nillang/LTT_GKD@main/official_rules/<文件名>.json`
     * 该直链会被 [SubscriptionUrls.detectType] 自动识别为 URL 类型，国内有 CDN 节点更稳。
     *
     * 备选方案：运行 `official_rules/upload.py` 把官方规则上传到公开 Gist，填 32 位 Gist ID。
     *
     * 当前为空串表示"尚未配置"，[isReady] 为 false，APP 不会播种；填入真实直链后即可开箱即用。
     */
    const val ADDRESS: String = ""  // TODO: 填入官方 jsDelivr 直链或 Gist ID

    /** 是否已配置官方源（ADDRESS 非空才播种，避免占位值导致同步失败）。 */
    val isReady: Boolean get() = ADDRESS.isNotBlank()  // 非空即就绪
}
