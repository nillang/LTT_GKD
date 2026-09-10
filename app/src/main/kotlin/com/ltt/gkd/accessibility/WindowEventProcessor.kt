package com.ltt.gkd.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ltt.gkd.action.ActionExecutor
import com.ltt.gkd.data.prefs.SettingsStore
import com.ltt.gkd.ocr.OcrManager
import com.ltt.gkd.data.rule.ActionType
import com.ltt.gkd.data.rule.MatchType
import com.ltt.gkd.data.rule.Rule
import com.ltt.gkd.data.rule.RuleEngine
import com.ltt.gkd.data.rule.RuleMatcher
import com.ltt.gkd.data.rule.RuleRepository
import com.ltt.gkd.util.Logger
import com.ltt.gkd.util.NodeUtils
import kotlinx.coroutines.flow.first
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
    private val settings: SettingsStore
) {

    private val lock = Mutex()
    @Volatile
    private var lastPkg: String? = null

    suspend fun handle(event: AccessibilityEvent) {
        lock.withLock {
            processEvent(event)
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
        try {
            for (rule in candidates) {
                if (rule.match.type == MatchType.OCR) continue // OCR 兜底稍后统一处理
                val hit = matcher.match(root, rule) ?: continue
                Logger.i("命中规则 ${rule.id} (${rule.name})")
                try {
                    val ok = executor.execute(rule.action, node = hit)
                    if (ok) {
                        engine.markTriggered(rule.id, System.currentTimeMillis())
                        onSkipSucceeded(pkg, rule)
                        return // 一个窗口一次只跳过一次，避免误触发
                    }
                } finally {
                    NodeUtils.safeRecycle(hit)
                }
            }

            // OCR 兜底
            val ocrEnabled = settings.ocrEnabled.first()
            if (!ocrEnabled) return
            val ocrCandidates = candidates.filter { it.match.type == MatchType.OCR }
            if (ocrCandidates.isEmpty()) return
            Logger.d("OCR 兜底启动 (${ocrCandidates.size} 条候选)")
            val ocrHit = ocr.matchAndClick(ocrCandidates, pkg)
            if (ocrHit != null) {
                engine.markTriggered(ocrHit.id, System.currentTimeMillis())
                onSkipSucceeded(pkg, ocrHit)
            }
        } finally {
            NodeUtils.safeRecycle(root)
        }
    }

    private suspend fun onSkipSucceeded(@Suppress("UNUSED_PARAMETER") pkg: String, rule: Rule) {
        settings.incrementTotalSkip()
        val enableNoti = settings.skipNotificationEnabled.first()
        if (enableNoti) {
            SkipNotifier.notify(service, rule.name)
        }
    }
}
