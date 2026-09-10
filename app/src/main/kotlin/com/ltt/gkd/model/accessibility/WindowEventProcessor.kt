package com.ltt.gkd.model.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.model.action.ActionExecutor
import com.ltt.gkd.model.prefs.SettingsStore
import com.ltt.gkd.model.ocr.OcrManager
import com.ltt.gkd.model.rule.ActionType
import com.ltt.gkd.model.rule.MatchType
import com.ltt.gkd.model.rule.Rule
import com.ltt.gkd.model.rule.RuleEngine
import com.ltt.gkd.model.rule.RuleMatcher
import com.ltt.gkd.model.rule.RuleRepository
import com.ltt.gkd.model.util.Logger
import com.ltt.gkd.model.util.NodeUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 窗口事件处理器。
 *
 * 处理流程：
 * 1. 解析事件来源包名与 Activity
 * 2. 从 [RuleEngine] 获取候选规则
 * 3. 对每条规则用 [RuleMatcher] 在控件树中查找命中节点
 * 4. 命中则委托 [ActionExecutor] 执行动作，并标记节流
 * 5. 若所有节点匹配规则未命中但开启了 OCR，触发 [OcrManager] 兜底
 */
class WindowEventProcessor(
    private val service: AccessibilityService,
    private val repo: RuleRepository,
    private val engine: RuleEngine,
    private val matcher: RuleMatcher,
    private val executor: ActionExecutor,
    private val ocr: OcrManager,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {

    private val lock = Mutex()
    @Volatile
    private var lastPkg: String? = null

    fun handle(event: AccessibilityEvent) {
        scope.launch {
            lock.withLock {
                processEvent(event)
            }
        }
    }

    private suspend fun processEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()
        // 应用切换时重置节流
        if (pkg != lastPkg) {
            engine.resetThrottle()
            lastPkg = pkg
            Logger.d("切换到 $pkg")
        }
        val now = System.currentTimeMillis()
        val candidates = engine.candidates(pkg, cls, now)
        if (candidates.isEmpty()) return

        val root = service.rootInActiveWindow ?: run {
            Logger.d("rootInActiveWindow 为 null")
            return
        }

        // 节点匹配
        for (rule in candidates) {
            if (rule.match.type == MatchType.OCR) continue // OCR 兜底稍后统一处理
            val hit = matcher.match(root, rule)
            if (hit != null) {
                Logger.i("命中规则 ${rule.id} (${rule.name})")
                val ok = executor.execute(rule.action, node = hit)
                if (ok) {
                    engine.markTriggered(rule.id, System.currentTimeMillis())
                    onSkipSucceeded(pkg, rule)
                    return // 一个窗口一次只跳过一次，避免误触发
                }
            }
        }

        // OCR 兜底
        val ocrEnabled = settings.ocrEnabled.first()
        if (!ocrEnabled) {
            NodeUtils.safeRecycle(root)
            return
        }
        val ocrCandidates = candidates.filter { it.match.type == MatchType.OCR }
        if (ocrCandidates.isEmpty()) {
            NodeUtils.safeRecycle(root)
            return
        }
        Logger.d("OCR 兜底启动 (${ocrCandidates.size} 条候选)")
        val ocrHit = ocr.matchAndClick(ocrCandidates, pkg)
        if (ocrHit != null) {
            engine.markTriggered(ocrHit.id, System.currentTimeMillis())
            onSkipSucceeded(pkg, ocrHit)
        }
        NodeUtils.safeRecycle(root)
    }

    private suspend fun onSkipSucceeded(@Suppress("UNUSED_PARAMETER") pkg: String, rule: Rule) {
        settings.incrementTotalSkip()
        val enableNoti = settings.skipNotificationEnabled.first()
        if (enableNoti) {
            SkipNotifier.notify(service, rule.name)
        }
    }
}
