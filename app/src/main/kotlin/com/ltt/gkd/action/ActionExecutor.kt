package com.ltt.gkd.action // 包声明：本文件属于动作执行包 com.ltt.gkd.action

import android.accessibilityservice.AccessibilityService // 导入 AccessibilityService，用于全局返回动作
import android.view.accessibility.AccessibilityNodeInfo // 导入 AccessibilityNodeInfo，命中节点
import com.ltt.gkd.gesture.GestureSimulator // 导入 GestureSimulator，统一调度点击
import com.ltt.gkd.data.rule.ActionType // 导入 ActionType，动作类型枚举
import com.ltt.gkd.data.rule.MatchAction // 导入 MatchAction，规则动作配置
import com.ltt.gkd.util.Logger // 导入 Logger，日志
import com.ltt.gkd.util.NodeUtils // 导入 NodeUtils，取节点中心

/**
 * 执行规则命中的动作。
 *
 * 内部使用 [GestureSimulator] 统一调度点击。
 *
 * @param service 无障碍服务，用于执行返回等全局动作
 * @param gesture 手势模拟器，用于点击节点/坐标
 */
class ActionExecutor(
    private val service: AccessibilityService, // 无障碍服务
    private val gesture: GestureSimulator // 手势模拟器
) {

    /**
     * 执行动作。
     *
     * 先按 [MatchAction.delayMs] 等待（如规则要求延迟点击），再根据 [MatchAction.type] 分发：
     * - CLICK_NODE：在命中节点或可点击祖先上点击，失败则回退坐标手势
     * - CLICK_COORD：取节点中心坐标后用坐标手势点击
     * - GESTURE_TAP：直接用传入的坐标点击
     * - BACK：执行系统全局返回动作
     *
     * @param action 规则定义的动作
     * @param node 命中节点（CLICK_NODE / CLICK_COORD 必填）
     * @param coord 命中坐标（GESTURE_TAP 必填）
     * @return 动作是否成功执行
     */
    suspend fun execute(
        action: MatchAction, // 动作配置
        node: AccessibilityNodeInfo? = null, // 命中节点，默认 null
        coord: android.graphics.Point? = null // 命中坐标，默认 null
    ): Boolean { // 入口：执行动作
        // 动作前置延迟（毫秒），用于等待弹窗动画结束再点击
        if (action.delayMs > 0) { // 配置了延迟
            kotlinx.coroutines.delay(action.delayMs) // 按毫秒数等待
        }
        return when (action.type) { // 按动作类型分发
            ActionType.CLICK_NODE -> { // 节点点击
                // 节点点击：优先 performAction，失败回退坐标手势
                val ok = gesture.clickNodeOrCoord(node) // 调用节点点击兜底
                Logger.i("动作 CLICK_NODE -> $ok") // 记录结果
                ok // 返回结果
            }
            ActionType.CLICK_COORD -> { // 坐标点击
                // 坐标点击：基于节点中心坐标做手势点击
                val rect = NodeUtils.nodeCenter(node) // 取节点边界
                if (rect == null) { // 节点无边界
                    Logger.w("CLICK_COORD 但节点无坐标") // 打 warn 日志
                    return false // 返回失败
                }
                gesture.tapAt(rect).also { Logger.i("动作 CLICK_COORD -> $it") } // 调用坐标点击并记录
            }
            ActionType.GESTURE_TAP -> { // 纯坐标手势
                // 纯坐标手势点击（如 OCR 命中后传入的屏幕坐标）
                if (coord == null) { // 未提供坐标
                    Logger.w("GESTURE_TAP 但未提供坐标") // 打 warn 日志
                    return false // 返回失败
                }
                gesture.tapAt(coord.x.toFloat(), coord.y.toFloat()).also { // 调用坐标点击
                    Logger.i("动作 GESTURE_TAP($coord) -> $it") // 记录结果
                }
            }
            ActionType.BACK -> { // 系统级返回
                // 系统级返回：等价于按下返回键
                Logger.i("动作 BACK") // 记录动作
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) // 执行全局返回
            }
        }
    }

}
