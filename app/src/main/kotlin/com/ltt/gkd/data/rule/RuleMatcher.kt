package com.ltt.gkd.data.rule  // 声明包名，规则匹配器所在的包

import android.view.accessibility.AccessibilityNodeInfo  // 导入无障碍节点信息类，代表控件树节点
import com.ltt.gkd.util.NodeUtils  // 导入节点工具类，提供节点遍历能力
import com.ltt.gkd.util.PatternUtils  // 导入正则工具类，提供 Pattern 编译能力
import java.util.regex.Pattern  // 导入 Pattern，正则匹配用

/**
 * 在控件树中匹配规则。
 *
 * 匹配逻辑：
 * - [MatchType.TEXT]：节点 text 或 contentDescription 命中关键词或正则
 * - [MatchType.ID]：节点 viewIdResourceName 在 [MatchTarget.ids] 中
 * - [MatchType.DESC]：仅 contentDescription 命中
 * - [MatchType.OCR]：本类不处理，由 [com.ltt.gkd.ocr.OcrManager] 走截图路径
 */
class RuleMatcher {  // 规则匹配器类，在控件树中定位广告按钮

    /**
     * 单条规则匹配，返回命中的节点。
     *
     * @param root 控件树根节点（来自 AccessibilityService 的窗口快照）
     * @param rule 待匹配的规则
     * @return 命中的第一个节点；规则禁用、root 为空或 OCR 路径返回 null
     */
    fun match(root: AccessibilityNodeInfo?, rule: Rule): AccessibilityNodeInfo? {  // 单条规则匹配方法
        if (root == null || !rule.enabled) return null  // 根节点为空或规则被禁用，返回 null
        val target = rule.match  // 取出规则的匹配目标
        return when (target.type) {  // 按匹配类型分支
            MatchType.TEXT -> matchByText(root, target)  // 文本匹配
            MatchType.DESC -> matchByDesc(root, target)  // 描述匹配
            MatchType.ID -> matchById(root, target)  // ID 匹配
            MatchType.OCR -> null  // OCR 由 OcrManager 单独处理，本类返回 null
        }
    }

    /**
     * 按文本（text 优先，回退 contentDescription）匹配节点。
     *
     * @param root 控件树根节点
     * @param target 匹配目标（取 [MatchTarget.text] 列表编译为正则/字面量）
     * @return 第一个文本命中任一模式的节点；无模式或无命中返回 null
     */
    private fun matchByText(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {  // 文本匹配方法
        val patterns = target.text.map { compilePattern(it, target) }  // 将关键词列表编译为正则 Pattern 列表
        if (patterns.isEmpty()) return null  // 无模式，返回 null
        return NodeUtils.findFirst(root) { node ->  // 遍历控件树查找首个命中节点
            val text = node.text ?: node.contentDescription  // 优先取 text，回退 contentDescription
            text != null && textMatches(text, target, patterns)  // 文本非空且通过精度护栏匹配
        }
    }

    /**
     * 仅按 contentDescription 匹配节点。
     *
     * @param root 控件树根节点
     * @param target 匹配目标
     * @return 第一个 desc 命中任一模式的节点；无模式或无命中返回 null
     */
    private fun matchByDesc(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {  // 描述匹配方法
        val patterns = target.text.map { compilePattern(it, target) }  // 将关键词列表编译为正则 Pattern 列表
        if (patterns.isEmpty()) return null  // 无模式，返回 null
        return NodeUtils.findFirst(root) { node ->  // 遍历控件树查找首个命中节点
            val desc = node.contentDescription  // 取节点 contentDescription
            desc != null && textMatches(desc, target, patterns)  // desc 非空且通过精度护栏匹配
        }
    }

    /**
     * 文本匹配（带精度护栏），防止误命中：
     * - 正则模式（[MatchTarget.regex]=true）：沿用 `find()` 子串匹配，由规则作者自行负责。
     * - 字面量模式：
     *   1. **长度护栏**：广告按钮文案很短，超过 [MAX_LABEL_LEN] 的一律视为商品标题/正文，直接不匹配
     *      （避免"自营 福临门玉米油6.18L×2, 价格:134.9元"这类长文本被误命中）。
     *   2. **单字符精确**：像"×"这种单字符关键词，要求整段文本恰好等于它，
     *      避免把"国家补贴×超级补贴""6.18L×2"里的乘号当成关闭按钮。
     *   3. 多字符关键词仍用包含匹配（如"跳过广告 5"包含"跳过"）。
     *
     * @param raw 节点文本（text 或 contentDescription）
     * @param target 匹配目标（提供关键词列表、regex、大小写配置）
     * @param patterns 预编译的正则（仅 regex 模式使用）
     * @return 是否命中
     */
    private fun textMatches(raw: CharSequence, target: MatchTarget, patterns: List<Pattern>): Boolean {  // 带护栏的文本匹配
        val s = raw.toString().trim()  // 去空白
        if (s.isEmpty()) return false  // 空文本不匹配
        if (target.regex) return patterns.any { it.matcher(s).find() }  // 正则模式：沿用子串查找
        if (s.length > MAX_LABEL_LEN) return false  // 长度护栏：过长文本非按钮，跳过
        val ignoreCase = target.caseInsensitive  // 是否忽略大小写
        return target.text.any { kw ->  // 遍历关键词
            val k = kw.trim()  // 去空白
            when {  // 按关键词长度分支
                k.isEmpty() -> false  // 空关键词忽略
                k.length == 1 -> s.equals(k, ignoreCase = ignoreCase)  // 单字符：整段精确相等（防乘号误命中）
                else -> s.contains(k, ignoreCase = ignoreCase)  // 多字符：包含匹配
            }
        }
    }

    companion object {  // 静态常量
        /** 字面量匹配时节点文本的最大长度：广告按钮文案很短，超过则视为正文/标题不匹配。 */
        private const val MAX_LABEL_LEN = 16  // 按钮文案长度上限
    }

    /**
     * 按 viewIdResourceName 匹配节点。
     *
     * @param root 控件树根节点
     * @param target 匹配目标（取 [MatchTarget.ids] 列表）
     * @return 第一个 id 命中（全名或短名后缀）的节点；无 id 或无命中返回 null
     */
    private fun matchById(root: AccessibilityNodeInfo, target: MatchTarget): AccessibilityNodeInfo? {  // ID 匹配方法
        if (target.ids.isEmpty()) return null  // 无 id 列表，返回 null
        return NodeUtils.findFirst(root) { node ->  // 遍历控件树查找首个命中节点
            val id = node.viewIdResourceName  // 取节点 viewIdResourceName
            // 同时支持全名（com.pkg:id/foo）和短名（foo）两种写法
            id != null && target.ids.any { it == id || id.endsWith(":id/$it") }  // id 非空且匹配全名或短名后缀
        }
    }

    /**
     * 将原始模式字符串编译为正则 Pattern（委托 [PatternUtils] 处理大小写与正则开关）。
     *
     * @param raw 原始字符串
     * @param target 提供正则/大小写配置的目标
     */
    private fun compilePattern(raw: String, target: MatchTarget) =  // 编译正则模式方法
        PatternUtils.compile(raw, target)  // 委托 PatternUtils 处理大小写与正则开关
}
