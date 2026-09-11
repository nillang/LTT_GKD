package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import com.ltt.gkd.data.rule.MatchTarget // 导入 MatchTarget，提供 regex/caseInsensitive 等匹配配置
import java.util.regex.Pattern // 导入 Pattern，编译后的正则模式对象

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
    fun compile(raw: String, target: MatchTarget): Pattern { // 入口：编译原始字符串为 Pattern
        val flags = if (target.caseInsensitive) Pattern.CASE_INSENSITIVE else 0 // 根据是否忽略大小写设置 flags
        return if (target.regex) { // 走正则编译分支
            try {
                Pattern.compile(raw, flags) // 按正则语法编译
            } catch (e: Exception) {
                Logger.w("正则编译失败，按字面量处理: $raw", e) // 正则编译失败时降级为字面量匹配，避免因规则错误导致整体不可用
                Pattern.compile(Pattern.quote(raw), flags) // 转义为字面量后重新编译
            }
        } else {
            Pattern.compile(Pattern.quote(raw), flags) // 非正则模式：仍用 Pattern.quote 转义，避免特殊字符
        }
    }
}
