package com.ltt.gkd.data.rule

/**
 * 规则引擎：根据当前包名/Activity 从 [RuleRepository] 中筛选可执行的规则。
 *
 * 节流逻辑由调用方（[com.ltt.gkd.accessibility.WindowEventProcessor]）维护。
 */
class RuleEngine(private val repo: RuleRepository) {

    private val throttleMap = HashMap<String, Long>() // ruleId -> lastTriggeredAt

    /**
     * 筛选当前窗口应处理的规则。
     *
     * @param packageName 当前前台包名
     * @param activity 当前 Activity（短名或全限定名都行）
     * @param now 当前时间戳（毫秒）
     * @return 按优先级降序排列、未触发节流的规则列表
     */
    fun candidates(packageName: String?, activity: String?, now: Long): List<Rule> {
        val all = repo.rules.value
        if (all.isEmpty() || packageName.isNullOrEmpty()) return emptyList()
        return all.filter { rule ->
            if (!rule.enabled) return@filter false
            // 包名匹配：空表示通用兜底
            val pkgMatch = rule.packageName.isEmpty() || rule.packageName == packageName
            if (!pkgMatch) return@filter false
            // Activity 匹配：空表示任意；用 endsWith 兼容短类名
            if (rule.activity != null && activity != null && !activity.endsWith(rule.activity)) {
                return@filter false
            }
            // 节流
            val last = throttleMap[rule.id] ?: 0L
            if (now - last < rule.throttleMs) return@filter false
            true
        }
    }

    /** 标记规则已触发，用于节流。 */
    fun markTriggered(ruleId: String, now: Long) {
        throttleMap[ruleId] = now
    }

    /** 重置所有节流状态（通常在应用切换时调用）。 */
    fun resetThrottle() {
        throttleMap.clear()
    }
}
