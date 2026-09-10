package com.ltt.gkd.util

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 控件树相关工具：遍历、文本汇总、查找。
 *
 * 重要：调用方应在使用完 [AccessibilityNodeInfo] 后调用 [recycle]，
 * 但 Android 11+ 起 recycle 是 no-op，这里仍保留以便旧版本稳定。
 */
object NodeUtils {

    /**
     * 深度优先遍历，返回第一个使 [predicate] 为 true 的节点。
     *
     * 回收约定：除了命中节点本身（返回值），子树中其他节点都会被 [recycle]；
     * 命中节点的祖先在 Android 实现中独立于子节点对象，回收它们不影响命中节点可用性。
     * 调用方使用完返回值后应自行调用 [safeRecycle]。
     */
    fun findFirst(
        root: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (root == null) return null
        if (predicate(root)) return root
        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChild(i) ?: continue
            val hit = findFirst(child, predicate)
            // 命中节点不是 child 本身时，child 是祖先/兄弟，可安全回收
            if (hit !== child) {
                @Suppress("DEPRECATION")
                child.recycle()
            }
            if (hit != null) return hit
        }
        return null
    }

    /**
     * 深度优先遍历整棵控件树，对每个节点应用 [visitor]。
     * 用于需要遍历全部节点的场景（如 collectTexts）。
     */
    fun traverse(root: AccessibilityNodeInfo?, visitor: (AccessibilityNodeInfo) -> Unit) {
        if (root == null) return
        visitor(root)
        val count = root.childCount
        for (i in 0 until count) {
            val child = root.getChild(i) ?: continue
            traverse(child, visitor)
            @Suppress("DEPRECATION")
            child.recycle()
        }
    }

    /** 收集所有节点（包括自身）的可见文本/描述。 */
    fun collectTexts(root: AccessibilityNodeInfo?): List<CharSequence> {
        val out = ArrayList<CharSequence>()
        traverse(root) { node ->
            node.text?.let { if (it.isNotBlank()) out.add(it) }
            node.contentDescription?.let { if (it.isNotBlank()) out.add(it) }
        }
        return out
    }

    /** 在节点中递归查找包含任意 [keywords] 的可点击祖先（向上找 clickable parent）。 */
    fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        var depth = 0
        while (current != null && depth < 10) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    /** 在控件树中匹配第一个包含 [text] 的节点。 */
    fun findByText(root: AccessibilityNodeInfo?, text: CharSequence): AccessibilityNodeInfo? {
        if (root == null) return null
        val nodes = root.findAccessibilityNodeInfosByText(text.toString())
        return nodes?.firstOrNull()
    }

    /** 获取节点中心坐标（屏幕绝对坐标）。 */
    fun nodeCenter(node: AccessibilityNodeInfo?): android.graphics.Rect? {
        if (node == null) return null
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        return if (rect.width() > 0 && rect.height() > 0) rect else null
    }

    /** 安全 recycle。 */
    fun safeRecycle(node: AccessibilityNodeInfo?) {
        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) node?.recycle()
    }
}
