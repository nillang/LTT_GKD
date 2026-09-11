package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import android.os.Build // 导入 Build，用于判断 SDK 版本以决定是否调用已废弃 API
import android.view.accessibility.AccessibilityNodeInfo // 导入 AccessibilityNodeInfo，无障碍节点信息类

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
     *
     * @param root 控件树根节点
     * @param predicate 节点命中判定函数，返回 true 表示该节点为目标
     * @return 第一个命中的节点；未命中返回 null
     */
    fun findFirst( // 入口：DFS 查找首个满足谓词的节点
        root: AccessibilityNodeInfo?, // 控件树根节点
        predicate: (AccessibilityNodeInfo) -> Boolean // 节点命中判定函数
    ): AccessibilityNodeInfo? {
        if (root == null) return null // 根节点为空直接返回 null
        if (predicate(root)) return root // 根节点即命中则返回根
        val count = root.childCount // 子节点数量
        for (i in 0 until count) { // 遍历所有子节点
            val child = root.getChild(i) ?: continue // 取第 i 个子节点，为 null 跳过
            val hit = findFirst(child, predicate) // 递归查找子树
            if (hit !== child) { // 命中节点不是 child 本身时，child 是祖先/兄弟，可安全回收
                @Suppress("DEPRECATION") // 抑制 recycle 已废弃的警告
                child.recycle() // 回收 child
            }
            if (hit != null) return hit // 命中则向上返回
        }
        return null // 全部遍历完未命中返回 null
    }

    /**
     * 深度优先遍历整棵控件树，对每个节点应用 [visitor]。
     *
     * 用于需要遍历全部节点的场景（如 [collectTexts]）。遍历过程中子节点会被回收。
     *
     * @param root 控件树根节点
     * @param visitor 对每个节点执行的操作（副作用）
     */
    fun traverse(root: AccessibilityNodeInfo?, visitor: (AccessibilityNodeInfo) -> Unit) { // 入口：遍历整棵树并应用 visitor
        if (root == null) return // 根为空直接返回
        visitor(root) // 对根节点应用 visitor
        val count = root.childCount // 子节点数量
        for (i in 0 until count) { // 遍历所有子节点
            val child = root.getChild(i) ?: continue // 取第 i 个子节点，为 null 跳过
            traverse(child, visitor) // 递归遍历子树
            @Suppress("DEPRECATION") // 抑制 recycle 已废弃的警告
            child.recycle() // 遍历完成后回收 child
        }
    }

    /**
     * 收集所有节点（包括自身）的可见文本/描述。
     *
     * 遍历子树时同时取出 [AccessibilityNodeInfo.getText] 与 [AccessibilityNodeInfo.getContentDescription]，
     * 空白文本会被过滤。
     *
     * @param root 控件树根节点
     * @return 节点文本/描述列表（顺序与遍历顺序一致）
     */
    fun collectTexts(root: AccessibilityNodeInfo?): List<CharSequence> { // 入口：收集所有节点的文本与描述
        val out = ArrayList<CharSequence>() // 输出列表
        traverse(root) { node -> // 遍历控件树
            node.text?.let { if (it.isNotBlank()) out.add(it) } // 节点 text 非空白则加入
            node.contentDescription?.let { if (it.isNotBlank()) out.add(it) } // 节点描述非空白则加入
        }
        return out // 返回收集到的文本列表
    }

    /**
     * 在节点中递归查找包含任意 [keywords] 的可点击祖先（向上找 clickable parent）。
     *
     * 从 [node] 向父节点逐层查找，返回第一个 [AccessibilityNodeInfo.isClickable] 为 true 的祖先。
     * 为防止回环或层级过深，最多向上查找 10 层。
     *
     * @param node 起始节点
     * @return 第一个可点击的祖先节点；不存在则返回 null
     */
    fun findClickableAncestor(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? { // 入口：向上查找可点击祖先
        var current = node // 当前节点指针
        var depth = 0 // 已向上查找层数
        while (current != null && depth < 10) { // 限制最多向上 10 层，防止节点树异常导致死循环
            if (current.isClickable) return current // 当前可点击则返回
            current = current.parent // 否则继续向上
            depth++ // 层数加一
        }
        return null // 超过限制仍未找到返回 null
    }

    /**
     * 在控件树中匹配第一个包含 [text] 的节点。
     *
     * 委托给系统 [AccessibilityNodeInfo.findAccessibilityNodeInfosByText]，
     * 不区分大小写，返回文本匹配列表中的第一个。
     *
     * @param root 控件树根节点
     * @param text 要查找的文本
     * @return 第一个匹配节点；无匹配返回 null
     */
    fun findByText(root: AccessibilityNodeInfo?, text: CharSequence): AccessibilityNodeInfo? { // 入口：按文本查找节点
        if (root == null) return null // 根为空直接返回 null
        val nodes = root.findAccessibilityNodeInfosByText(text.toString()) // 委托系统 API 查找文本匹配节点列表
        return nodes?.firstOrNull() // 返回首个匹配节点，无则 null
    }

    /**
     * 获取节点中心坐标（屏幕绝对坐标）。
     *
     * 读取 [AccessibilityNodeInfo.getBoundsInScreen]，并过滤掉宽高为 0 的不可见节点。
     * 返回的 [android.graphics.Rect] 为节点在屏幕上的边界矩形，调用方需自行计算中心点。
     *
     * @param node 目标节点
     * @return 节点屏幕边界矩形；节点为 null 或不可见返回 null
     */
    fun nodeCenter(node: AccessibilityNodeInfo?): android.graphics.Rect? { // 入口：取节点屏幕边界矩形
        if (node == null) return null // 节点为 null 直接返回 null
        val rect = android.graphics.Rect() // 创建矩形对象
        node.getBoundsInScreen(rect) // 填入屏幕坐标边界
        return if (rect.width() > 0 && rect.height() > 0) rect else null // 过滤掉尺寸为 0 的不可见节点
    }

    /**
     * 安全回收 [AccessibilityNodeInfo]。
     *
     * Android 11（API 30）起 recycle 已是 no-op；仅在更低版本执行实际回收，
     * 避免在 SDK 30+ 上调用已废弃 API。
     *
     * @param node 可为 null 的节点；为 null 时直接返回
     */
    fun safeRecycle(node: AccessibilityNodeInfo?) { // 入口：安全回收节点
        @Suppress("DEPRECATION") // 抑制 recycle 已废弃的警告
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) node?.recycle() // 仅在 Android 11 以下才真正调用 recycle
    }
}
