package com.ltt.gkd.data.rule

import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.util.NodeUtils
import com.ltt.gkd.util.PatternUtils

/**
 * 在控件树中匹配规则。
 *
 * 匹配逻辑：
 * - [MatchType.TEXT]：节点 text 或 contentDescription 命中关键词或正则
 * - [MatchType.ID]：节点 viewIdResourceName 在 [MatchTarget.ids] 中
 * - [MatchType.DESC]：仅 contentDescription 命中
 * - [MatchType.OCR]：本类不处理，由 [com.ltt.gkd.ocr.OcrManager] 走截图路径
 */
class RuleMatcher {

    /** 单条规则匹配，返回命中的节点。 */
    fun match(root: AccessibilityNodeInfo?, rule: Rule): AccessibilityNodeInfo? {
        if (root == null || !rule.enabled) return null
        val target = rule.match
        return when (target.type) {
            MatchType.TEXT -> matchByText(root, target)
            MatchType.DESC -> matchByDesc(root, target)
            MatchType.ID -> matchById(root, target)
            MatchType.OCR -> null
        }
    }

    private fun matchByText(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {
        val patterns = target.text.map { compilePattern(it, target) }
        if (patterns.isEmpty()) return null
        return NodeUtils.findFirst(root) { node ->
            val text = node.text ?: node.contentDescription
            text != null && patterns.any { it.matcher(text).find() }
        }
    }

    private fun matchByDesc(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {
        val patterns = target.text.map { compilePattern(it, target) }
        if (patterns.isEmpty()) return null
        return NodeUtils.findFirst(root) { node ->
            val desc = node.contentDescription
            desc != null && patterns.any { it.matcher(desc).find() }
        }
    }

    private fun matchById(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {
        if (target.ids.isEmpty()) return null
        return NodeUtils.findFirst(root) { node ->
            val id = node.viewIdResourceName
            id != null && target.ids.any { it == id || id.endsWith(":id/$it") }
        }
    }

    private fun compilePattern(raw: String, target: MatchTarget) =
        PatternUtils.compile(raw, target)
}
