package com.ltt.gkd.action

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.gesture.GestureSimulator
import com.ltt.gkd.data.rule.ActionType
import com.ltt.gkd.data.rule.MatchAction
import com.ltt.gkd.util.Logger
import com.ltt.gkd.util.NodeUtils

/**
 * 执行规则命中的动作。
 *
 * 内部使用 [GestureSimulator] 统一调度点击。
 */
class ActionExecutor(
    private val service: AccessibilityService,
    private val gesture: GestureSimulator
) {

    /**
     * 执行动作。
     *
     * @param action 规则定义的动作
     * @param node 命中节点（CLICK_NODE / CLICK_COORD 必填）
     * @param coord 命中坐标（GESTURE_TAP 必填）
     */
    suspend fun execute(
        action: MatchAction,
        node: AccessibilityNodeInfo? = null,
        coord: android.graphics.Point? = null
    ): Boolean {
        if (action.delayMs > 0) {
            kotlinx.coroutines.delay(action.delayMs)
        }
        return when (action.type) {
            ActionType.CLICK_NODE -> {
                val ok = gesture.clickNodeOrCoord(node)
                Logger.i("动作 CLICK_NODE -> $ok")
                ok
            }
            ActionType.CLICK_COORD -> {
                val rect = NodeUtils.nodeCenter(node)
                if (rect == null) {
                    Logger.w("CLICK_COORD 但节点无坐标")
                    return false
                }
                gesture.tapAt(rect).also { Logger.i("动作 CLICK_COORD -> $it") }
            }
            ActionType.GESTURE_TAP -> {
                if (coord == null) {
                    Logger.w("GESTURE_TAP 但未提供坐标")
                    return false
                }
                gesture.tapAt(coord.x.toFloat(), coord.y.toFloat()).also {
                    Logger.i("动作 GESTURE_TAP($coord) -> $it")
                }
            }
            ActionType.BACK -> {
                Logger.i("动作 BACK")
                service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
        }
    }

}
