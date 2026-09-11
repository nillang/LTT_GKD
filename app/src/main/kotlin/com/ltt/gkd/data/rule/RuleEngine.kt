package com.ltt.gkd.data.rule  // 声明包名，规则引擎所在的包

import kotlinx.coroutines.flow.StateFlow  // 导入协程 StateFlow，用于订阅可观察的状态

/**
 * 规则引擎：根据当前包名/Activity 从规则源中筛选可执行的规则。
 *
 * 节流逻辑由调用方（[com.ltt.gkd.accessibility.WindowEventProcessor]）维护。
 *
 * @param rulesSource 提供"当前生效规则列表"的源（通常是 [RuleRepository.rules]）。
 *                    抽成 [StateFlow] 而非直接依赖 [RuleRepository] 便于单元测试。
 */
class RuleEngine(private val rulesSource: StateFlow<List<Rule>>) {  // 规则引擎类，依赖规则列表 StateFlow

    private val throttleMap = HashMap<String, Long>() // ruleId -> lastTriggeredAt  // 节流映射表：规则 ID 到上次触发时间戳

    /**
     * 筛选当前窗口应处理的规则。
     *
     * @param packageName 当前前台包名
     * @param activity 当前 Activity（短名或全限定名都行）
     * @param now 当前时间戳（毫秒）
     * @return 按优先级降序排列、未触发节流的规则列表
     */
    fun candidates(packageName: String?, activity: String?, now: Long): List<Rule> {  // 候选规则筛选方法
        val all = rulesSource.value  // 取出当前生效的全部规则
        if (all.isEmpty() || packageName.isNullOrEmpty()) return emptyList()  // 无规则或包名为空，返回空列表
        return all.filter { rule ->  // 过滤规则列表
            if (!rule.enabled) return@filter false  // 规则被禁用，过滤掉
            // 包名匹配：空表示通用兜底
            val pkgMatch = rule.packageName.isEmpty() || rule.packageName == packageName  // 规则包名为空或与当前包名相同
            if (!pkgMatch) return@filter false  // 包名不匹配，过滤掉
            // Activity 匹配：规则限定 activity 时，传入必须非空且 endsWith 兼容短类名
            if (rule.activity != null && (activity == null || !activity.endsWith(rule.activity))) {  // 规则限定 Activity 且当前不匹配
                return@filter false  // 过滤掉
            }
            // 节流：仅在规则曾经触发过时检查窗口
            val last = throttleMap[rule.id]  // 取该规则上次触发时间戳
            if (last != null && now - last < rule.throttleMs) return@filter false  // 仍在节流窗口内，过滤掉
            true  // 通过所有过滤，保留该规则
        }.sortedByDescending { it.priority }  // 保证按优先级降序输出，不依赖上游排序
    }

    /**
     * 标记规则已触发，用于节流。
     *
     * @param ruleId 被触发规则的唯一 ID
     * @param now 触发时间戳（毫秒），写入 [throttleMap] 供下次 [candidates] 比较
     */
    fun markTriggered(ruleId: String, now: Long) {  // 标记规则触发时间方法
        throttleMap[ruleId] = now  // 更新该规则的上次触发时间戳
    }

    /**
     * 重置所有节流状态（通常在应用切换时调用）。
     *
     * 调用后所有规则会重新可触发，避免长时间停留导致规则永久失效。
     */
    fun resetThrottle() {  // 重置节流状态方法
        throttleMap.clear()  // 清空所有规则的上次触发时间戳
    }
}
