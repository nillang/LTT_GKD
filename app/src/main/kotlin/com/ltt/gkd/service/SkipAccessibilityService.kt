package com.ltt.gkd.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.ltt.gkd.App
import com.ltt.gkd.accessibility.WindowEventProcessor
import com.ltt.gkd.action.ActionExecutor
import com.ltt.gkd.gesture.GestureSimulator
import com.ltt.gkd.ocr.OcrManager
import com.ltt.gkd.data.prefs.SettingsStore
import com.ltt.gkd.data.rule.RuleEngine
import com.ltt.gkd.data.rule.RuleMatcher
import com.ltt.gkd.data.rule.RuleRepository
import com.ltt.gkd.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 核心无障碍服务。
 *
 * - 接收系统事件 → 委托 [processor] 处理
 * - 服务自身的生命周期管理：连接时初始化组件，断开时清理
 * - 业务逻辑（规则匹配/OCR/手势）位于 [com.ltt.gkd.accessibility]、[com.ltt.gkd.action]、[com.ltt.gkd.gesture]、[com.ltt.gkd.ocr] 包
 */
class SkipAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: SkipAccessibilityService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var processor: WindowEventProcessor

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        val app = App.get()
        val repo = RuleRepository(this)
        val engine = RuleEngine(repo)
        val matcher = RuleMatcher()
        val gesture = GestureSimulator(this)
        val executor = ActionExecutor(this, gesture)
        val ocr = OcrManager(this, gesture)
        val settings: SettingsStore = app.settings

        processor = WindowEventProcessor(
            service = this,
            repo = repo,
            engine = engine,
            matcher = matcher,
            executor = executor,
            ocr = ocr,
            settings = settings
        )
        scope.launch {
            repo.reload()
            Logger.i("无障碍服务已连接，规则就绪")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || !::processor.isInitialized) return
        scope.launch { processor.handle(event) }
    }

    override fun onInterrupt() {
        Logger.w("无障碍服务被中断")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        runCatching { ocr.close() }
        scope.cancel()
        Logger.i("无障碍服务已断开")
        return super.onUnbind(intent)
    }
}
