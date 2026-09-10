package com.ltt.gkd.gesture

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.util.Logger
import com.ltt.gkd.util.NodeUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 手势模拟器：执行节点点击 / 坐标点击。
 *
 * minSdk = 26（Android 8.0），dispatchGesture 自 API 24 起可用，无需版本判断。
 * 对于无法通过 [AccessibilityNodeInfo.performAction] 触发的节点，
 * 改用坐标手势更接近真人点击，规避部分 App 的"必须真实点击"检测。
 */
class GestureSimulator(private val service: AccessibilityService) {

    /** 点击节点中心坐标，使用 dispatchGesture。 */
    suspend fun tapAt(rect: Rect, holdMs: Long = 40L): Boolean {
        return tapAt(rect.exactCenterX(), rect.exactCenterY(), holdMs)
    }

    /** 点击屏幕坐标。 */
    suspend fun tapAt(x: Float, y: Float, holdMs: Long = 40L): Boolean {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, holdMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val ok = dispatchAndWait(gesture)
        // 给系统时间分发点击事件
        delay(60)
        return ok
    }

    /** 在节点或其可点击祖先上执行 performAction(ACTION_CLICK)。 */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val target = NodeUtils.findClickableAncestor(node) ?: node
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** 节点点击兜底：先 performAction，失败则回退坐标手势。 */
    suspend fun clickNodeOrCoord(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (clickNode(node)) {
            Logger.d("performAction(ACTION_CLICK) 成功")
            return true
        }
        val rect = NodeUtils.nodeCenter(node) ?: run {
            Logger.w("节点无可点击坐标")
            return false
        }
        Logger.d("performAction 失败，回退坐标手势")
        return tapAt(rect)
    }

    private suspend fun dispatchAndWait(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            val dispatched = service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(g: GestureDescription?) {
                        Logger.d("gesture completed")
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(g: GestureDescription?) {
                        Logger.w("gesture cancelled")
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null
            )
            if (!dispatched && cont.isActive) {
                Logger.w("dispatchGesture 被拒绝")
                cont.resume(false)
            }
        }
}
