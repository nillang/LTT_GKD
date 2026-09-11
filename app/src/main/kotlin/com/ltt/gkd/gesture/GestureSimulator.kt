package com.ltt.gkd.gesture // 包声明：本文件属于手势包 com.ltt.gkd.gesture

import android.accessibilityservice.AccessibilityService // 导入 AccessibilityService，提供 dispatchGesture
import android.accessibilityservice.GestureDescription // 导入 GestureDescription，描述手势
import android.graphics.Path // 导入 Path，构造手势轨迹
import android.graphics.Rect // 导入 Rect，节点边界
import android.view.accessibility.AccessibilityNodeInfo // 导入 AccessibilityNodeInfo，节点信息
import com.ltt.gkd.util.Logger // 导入 Logger，日志
import com.ltt.gkd.util.NodeUtils // 导入 NodeUtils，取节点中心/可点击祖先
import kotlinx.coroutines.delay // 导入 delay，给系统分发时间
import kotlinx.coroutines.suspendCancellableCoroutine // 导入 suspendCancellableCoroutine，回调转挂起
import kotlin.coroutines.resume // 导入 resume，恢复协程

/**
 * 手势模拟器：执行节点点击 / 坐标点击。
 *
 * minSdk = 26（Android 8.0），dispatchGesture 自 API 24 起可用，无需版本判断。
 * 对于无法通过 [AccessibilityNodeInfo.performAction] 触发的节点，
 * 改用坐标手势更接近真人点击，规避部分 App 的"必须真实点击"检测。
 *
 * @param service 无障碍服务，提供 dispatchGesture 能力
 */
class GestureSimulator(private val service: AccessibilityService) { // 构造：保存服务实例

    /**
     * 点击节点中心坐标，使用 dispatchGesture。
     *
     * @param rect 节点边界矩形
     * @param holdMs 按下持续时间，默认 40ms 模拟快速点击
     * @return 手势是否分发并完成
     */
    suspend fun tapAt(rect: Rect, holdMs: Long = 40L): Boolean { // 入口：按矩形点击
        return tapAt(rect.exactCenterX(), rect.exactCenterY(), holdMs) // 转为坐标点击
    }

    /**
     * 点击屏幕坐标。
     *
     * 通过 [GestureDescription] + [AccessibilityService.dispatchGesture] 在指定坐标模拟一次按下-抬起。
     *
     * @param x 屏幕 X 坐标
     * @param y 屏幕 Y 坐标
     * @param holdMs 按下持续时间，默认 40ms
     * @return 手势是否分发并完成
     */
    suspend fun tapAt(x: Float, y: Float, holdMs: Long = 40L): Boolean { // 入口：按坐标点击
        val path = Path().apply { moveTo(x, y) } // 在 (x,y) 创建路径起点
        val stroke = GestureDescription.StrokeDescription(path, 0L, holdMs) // 描边：起点 0、持续 holdMs
        val gesture = GestureDescription.Builder().addStroke(stroke).build() // 构造手势
        val ok = dispatchAndWait(gesture) // 分发并等待结果
        // 给系统时间分发点击事件
        delay(60) // 等待 60ms
        return ok // 返回是否成功
    }

    /**
     * 在节点或其可点击祖先上执行 performAction(ACTION_CLICK)。
     *
     * 若 [node] 本身不可点击，则向上查找最近的可点击祖先执行点击。
     *
     * @param node 目标节点
     * @return 点击动作是否成功分发（不代表目标 App 真正响应）
     */
    fun clickNode(node: AccessibilityNodeInfo?): Boolean { // 入口：语义点击
        if (node == null) return false // 节点为空直接失败
        val target = NodeUtils.findClickableAncestor(node) ?: node // 找可点击祖先，无则用节点本身
        return target.performAction(AccessibilityNodeInfo.ACTION_CLICK) // 执行 ACTION_CLICK
    }

    /**
     * 节点点击兜底：先 performAction，失败则回退坐标手势。
     *
     * 优先用语义点击（[clickNode]），失败时再用坐标手势 [tapAt]，提高点击成功率。
     *
     * @param node 目标节点
     * @return 任一方式成功即返回 true；都失败返回 false
     */
    suspend fun clickNodeOrCoord(node: AccessibilityNodeInfo?): Boolean { // 入口：节点点击兜底
        if (node == null) return false // 节点为空直接失败
        if (clickNode(node)) { // 语义点击
            Logger.d("performAction(ACTION_CLICK) 成功") // 记录成功
            return true // 返回成功
        }
        val rect = NodeUtils.nodeCenter(node) ?: run { // 取节点边界
            Logger.w("节点无可点击坐标") // 打 warn 日志
            return false // 返回失败
        }
        Logger.d("performAction 失败，回退坐标手势") // 记录回退
        return tapAt(rect) // 调用坐标手势
    }

    /**
     * 分发手势并挂起等待结果回调。
     *
     * 将 [AccessibilityService.dispatchGesture] 的异步回调包装为挂起函数，
     * 通过 [suspendCancellableCoroutine] 恢复协程；手势完成/取消/被拒绝分别返回对应布尔值。
     *
     * @param gesture 已构造好的手势描述
     * @return true 表示手势已完成；false 表示被取消或被拒绝
     */
    private suspend fun dispatchAndWait(gesture: GestureDescription): Boolean = // 内部：分发手势并等待
        suspendCancellableCoroutine { cont -> // 把回调转挂起
            val dispatched = service.dispatchGesture( // 调用 dispatchGesture
                gesture, // 手势描述
                object : AccessibilityService.GestureResultCallback() { // 结果回调
                    override fun onCompleted(g: GestureDescription?) { // 完成
                        Logger.d("gesture completed") // 记录完成
                        if (cont.isActive) cont.resume(true) // 协程仍活跃则恢复返回 true
                    }

                    override fun onCancelled(g: GestureDescription?) { // 取消
                        Logger.w("gesture cancelled") // 记录取消
                        if (cont.isActive) cont.resume(false) // 协程仍活跃则恢复返回 false
                    }
                },
                null // handler，null 表示在主线程回调
            )
            if (!dispatched && cont.isActive) { // 系统拒绝分发且协程仍活跃
                // 分发被系统拒绝（如服务未连接），直接返回 false
                Logger.w("dispatchGesture 被拒绝") // 打 warn 日志
                cont.resume(false) // 恢复返回 false
            }
        }
}
