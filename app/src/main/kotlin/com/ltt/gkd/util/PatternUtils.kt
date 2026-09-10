package com.ltt.gkd.util

import com.ltt.gkd.data.rule.MatchTarget
import java.util.regex.Pattern

/**
 * 正则编译工具：根据 [MatchTarget] 的 [MatchTarget.regex] / [MatchTarget.caseInsensitive]
 * 配置，把单个原始字符串编译为 [Pattern]。
 *
 * 抽出此工具避免 [com.ltt.gkd.data.rule.RuleMatcher] 和
 * [com.ltt.gkd.ocr.OcrManager] 重复实现。
 */
object PatternUtils {

    /**
     * 按 [target] 配置编译 [raw] 为正则模式。
     *
     * - regex=true：尝试按正则编译；编译失败时降级为字面量匹配，并打 warn 日志
     * - regex=false：用 [Pattern.quote] 转义所有特殊字符，做精确字面量匹配
     *
     * @param raw 原始字符串
     * @param target 提供 regex / caseInsensitive 配置
     */
    fun compile(raw: String, target: MatchTarget): Pattern {
        val flags = if (target.caseInsensitive) Pattern.CASE_INSENSITIVE else 0
        return if (target.regex) {
            try {
                Pattern.compile(raw, flags)
            } catch (e: Exception) {
                Logger.w("正则编译失败，按字面量处理: $raw", e)
                Pattern.compile(Pattern.quote(raw), flags)
            }
        } else {
            // 非正则模式：仍用 Pattern.quote 转义，避免特殊字符
            Pattern.compile(Pattern.quote(raw), flags)
        }
    }
}
