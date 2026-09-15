package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

/**
 * 订阅链接识别工具：把用户粘贴的链接归一化为 [SourceType]，并从 Gist 链接中抽取 Gist ID。
 *
 * 设计：订阅者只需粘贴链接，系统自动判断走 Gist API 还是普通 URL 直读，
 * 因此**订阅（消费）侧无需任何 GitHub 配置**。
 */
object SubscriptionUrls {  // 订阅链接识别工具对象

    /** Gist ID 是 32 位十六进制串；从任意链接中抽取第一段匹配即可覆盖各种 Gist URL 形态。 */
    private val GIST_ID = Regex("[a-fA-F0-9]{32}")  // 32 位十六进制正则

    /**
     * 从链接中抽取 Gist ID。
     *
     * 兼容：裸 ID、`gist.github.com/<user>/<id>`、`api.github.com/gists/<id>`、
     * `gist.githubusercontent.com/<user>/<id>/raw/...` 等形态。
     *
     * @param raw 用户输入的链接或 ID
     * @return 抽取到的 32 位 Gist ID；不是 Gist 则返回 null
     */
    fun gistId(raw: String): String? = GIST_ID.find(raw.trim())?.value  // 抽取首个 32 位十六进制串

    /**
     * 识别订阅源类型。
     *
     * @param raw 用户输入的链接或 ID
     * @return 能抽到 Gist ID 则为 [SourceType.GIST]，否则 [SourceType.URL]
     */
    fun detectType(raw: String): SourceType =  // 识别源类型
        if (gistId(raw) != null) SourceType.GIST else SourceType.URL  // 有 Gist ID 即 Gist，否则普通 URL

    /** 校验链接是否基本合法（Gist ID 或以 http(s) 开头）。 */
    fun isValid(raw: String): Boolean {  // 校验链接合法性
        val s = raw.trim()  // 去空白
        if (s.isEmpty()) return false  // 空
        if (gistId(s) != null) return true  // Gist ID/链接
        return s.startsWith("http://") || s.startsWith("https://")  // 普通 URL 需以 http(s) 开头
    }
}
