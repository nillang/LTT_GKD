package com.ltt.gkd.model.rule

import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.model.util.Logger
import com.ltt.gkd.model.util.NodeUtils
import java.util.regex.Pattern

/**
 * 在控件树中匹配规则。
 *
 * 匹配逻辑：
 * - [MatchType.TEXT]：节点 text 或 contentDescription 命中关键词或正则
 * - [MatchType.ID]：节点 viewIdResourceName 在 [MatchTarget.ids] 中
 * - [MatchType.DESC]：仅 contentDescription 命中
 * - [MatchType.OCR]：本类不处理，由 [com.ltt.gkd.model.ocr.OcrManager] 走截图路径
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
        var hit: AccessibilityNodeInfo? = null
        NodeUtils.traverse(root) { node ->
            if (hit != null) return@traverse
            val text = node.text ?: node.contentDescription
            if (text != null && patterns.any { it.matcher(text).find() }) hit = node
        }
        return hit
    }

    private fun matchByDesc(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {
        val patterns = target.text.map { compilePattern(it, target) }
        if (patterns.isEmpty()) return null
        var hit: AccessibilityNodeInfo? = null
        NodeUtils.traverse(root) { node ->
            if (hit != null) return@traverse
            val desc = node.contentDescription ?: return@traverse
            if (patterns.any { it.matcher(desc).find() }) hit = node
        }
        return hit
    }

    private fun matchById(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {
        if (target.ids.isEmpty()) return null
        var hit: AccessibilityNodeInfo? = null
        NodeUtils.traverse(root) { node ->
            if (hit != null) return@traverse
            val id = node.viewIdResourceName ?: return@traverse
            if (target.ids.any { it == id || id.endsWith(":id/$it") }) hit = node
        }
        return hit
    }

    private fun compilePattern(raw: String, target: MatchTarget): Pattern {
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
