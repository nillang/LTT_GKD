package com.ltt.gkd.service // 包声明：本文件属于服务包 com.ltt.gkd.service

import android.accessibilityservice.AccessibilityService // 导入 AccessibilityService，无障碍服务基类
import android.content.Intent // 导入 Intent，用于 onUnbind 参数
import android.view.accessibility.AccessibilityEvent // 导入 AccessibilityEvent，系统无障碍事件
import com.ltt.gkd.App // 导入 App，获取全局应用实例
import com.ltt.gkd.accessibility.WindowEventProcessor // 导入 WindowEventProcessor，窗口事件处理器
import com.ltt.gkd.action.ActionExecutor // 导入 ActionExecutor，动作执行器
import com.ltt.gkd.gesture.GestureSimulator // 导入 GestureSimulator，手势模拟器
import com.ltt.gkd.ocr.OcrManager // 导入 OcrManager，OCR 管理器
import com.ltt.gkd.data.prefs.SettingsStore // 导入 SettingsStore，设置存储
import com.ltt.gkd.data.rule.RuleEngine // 导入 RuleEngine，规则引擎
import com.ltt.gkd.data.rule.RuleMatcher // 导入 RuleMatcher，规则匹配器
import com.ltt.gkd.data.rule.RuleRepository // 导入 RuleRepository，规则仓库
import com.ltt.gkd.util.Logger // 导入 Logger，日志工具
import kotlinx.coroutines.CoroutineScope // 导入 CoroutineScope，协程作用域
import kotlinx.coroutines.Dispatchers // 导入 Dispatchers，调度器
import kotlinx.coroutines.SupervisorJob // 导入 SupervisorJob，子任务异常隔离
import kotlinx.coroutines.cancel // 导入 cancel，取消作用域
import kotlinx.coroutines.flow.SharingStarted // 导入 SharingStarted，Flow 共享启动策略
import kotlinx.coroutines.flow.combine // 导入 combine，合并 Flow
import kotlinx.coroutines.flow.stateIn // 导入 stateIn，将 Flow 转为 StateFlow
import kotlinx.coroutines.launch // 导入 launch，启动协程

/**
 * 核心无障碍服务。
 *
 * - 接收系统事件 → 委托 [processor] 处理
 * - 服务自身的生命周期管理：连接时初始化组件，断开时清理
 * - 业务逻辑（规则匹配/OCR/手势）位于 [com.ltt.gkd.accessibility]、[com.ltt.gkd.action]、[com.ltt.gkd.gesture]、[com.ltt.gkd.ocr] 包
 */
class SkipAccessibilityService : AccessibilityService() { // 继承 AccessibilityService 实现

    companion object { // 伴生对象：持有静态 instance 引用
        /**
         * 当前运行中的服务实例引用。
         *
         * 由 [onServiceConnected] 置位、[onUnbind] 置空；用 @Volatile 保证多线程可见性。
         * 外部可通过此字段判断服务是否活跃。
         */
        @Volatile // 保证多线程可见性
        var instance: SkipAccessibilityService? = null // 静态服务实例引用
            private set // 外部只读
    }

    // 服务级协程作用域，SupervisorJob 保证子任务异常不会波及兄弟任务
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default) // 服务作用域，Default 调度器
    // 窗口事件处理器：负责将系统事件分发到规则匹配/OCR/手势执行
    private lateinit var processor: WindowEventProcessor // 延迟初始化的事件处理器
    // OCR 管理器，惰性关闭；可能为 null（设备不支持时）
    private var ocr: OcrManager? = null // OCR 管理器引用

    /**
     * 服务连接成功时回调。
     *
     * 初始化所有业务组件：构造规则仓库、规则引擎、匹配器、手势执行器、OCR 管理器，
     * 装配到 [WindowEventProcessor]，并异步触发规则加载。
     */
    override fun onServiceConnected() { // 入口：服务连接成功回调
        super.onServiceConnected() // 调用父类初始化
        instance = this // 置位静态实例引用
        // 整体包一层 runCatching：任一组件初始化失败（如设备不支持 OCR）时仅记录日志，
        // 不让异常冒泡导致无障碍服务被系统停止。失败时 processor 保持未初始化，
        // onAccessibilityEvent 会因 ::processor.isInitialized 为 false 而安全忽略事件。
        runCatching { initComponents() } // 初始化业务组件
            .onFailure { Logger.e("无障碍服务初始化失败，跳过功能暂不可用", it) } // 失败记录 error 日志
    }

    /**
     * 初始化所有业务组件：构造规则仓库、规则引擎、匹配器、手势执行器、OCR 管理器，
     * 装配到 [WindowEventProcessor]，并异步触发规则加载。
     *
     * 抽成独立方法便于 [onServiceConnected] 用 runCatching 统一兜底。
     */
    private fun initComponents() { // 内部：装配业务组件
        val app = App.get() // 获取全局 App 实例
        val settings: SettingsStore = app.settings // 取出设置存储
        val repo = RuleRepository(this) // 构造规则仓库（依赖 Service Context）
        // 规则源 = 仓库规则 - 用户手动禁用的规则
        val rulesSource = combine(repo.rules, settings.disabledRuleIds) { rules, disabledIds -> // 合并规则流与禁用列表
            if (disabledIds.isEmpty()) rules else rules.filter { it.id !in disabledIds } // 禁用列表空则原样，否则过滤
        }.stateIn(scope, SharingStarted.Eagerly, emptyList()) // 转 StateFlow，Eagerly 立即启动
        val engine = RuleEngine(rulesSource) // 构造规则引擎
        val matcher = RuleMatcher() // 构造规则匹配器
        val gesture = GestureSimulator(this) // 构造手势模拟器
        val executor = ActionExecutor(this, gesture) // 构造动作执行器
        val ocrManager = OcrManager(this, gesture) // 构造 OCR 管理器
        ocr = ocrManager // 保存到字段
        val whitelist = app.whitelist // 取出白名单存储

        processor = WindowEventProcessor( // 装配事件处理器
            service = this, // 服务实例
            repo = repo, // 规则仓库
            engine = engine, // 规则引擎
            matcher = matcher, // 匹配器
            executor = executor, // 动作执行器
            ocr = ocrManager, // OCR 管理器
            settings = settings, // 设置
            whitelist = whitelist, // 白名单
            history = app.history // 跳过历史
        )
        scope.launch { // 启动后台协程加载规则
            repo.reload() // 异步加载本地 + 订阅的规则
            Logger.i("无障碍服务已连接，规则就绪") // 记录日志
        }
    }

    /**
     * 系统无障碍事件回调。
     *
     * 收到事件后转交 [processor] 异步处理；空事件或处理器未初始化时直接忽略。
     *
     * @param event 系统无障碍事件，可能为 null
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { // 入口：事件回调
        if (event == null || !::processor.isInitialized) return // 空事件或处理器未就绪直接返回
        // 单个事件处理异常就地兜底：记录 warn 后吞掉，避免异常冒泡触发全局崩溃处理器，
        // 保证一次异常窗口事件不影响后续事件的正常处理。
        scope.launch { // 在服务作用域中异步处理
            runCatching { processor.handle(event) } // 处理事件，捕获异常
                .onFailure { Logger.w("处理无障碍事件异常", it) } // 失败记录 warn 日志
        }
    }

    /**
     * 服务被系统中断时回调（如反馈服务冲突）。
     */
    override fun onInterrupt() { // 入口：系统中断回调
        Logger.w("无障碍服务被中断") // 记录 warn 日志
    }

    /**
     * 服务解绑时回调：清理 [instance] 引用、关闭 OCR、取消协程作用域。
     *
     * 若为系统异常断开（非用户主动关闭），尝试延迟重启服务。
     *
     * @param intent 触发解绑的 Intent
     * @return 是否调用父类解绑逻辑
     */
    override fun onUnbind(intent: Intent?): Boolean { // 入口：解绑回调
        instance = null // 清空静态引用
        runCatching { ocr?.close() } // 关闭 OCR 资源，忽略异常
        scope.cancel() // 取消服务作用域内所有协程
        Logger.i("无障碍服务已断开") // 记录日志
        // 仅当系统设置中无障碍开关仍处于开启状态（说明是崩溃/异常断开而非用户主动关闭）时，
        // 才尝试唤起进程，配合系统对已启用无障碍服务的自动重启机制恢复。
        // 用户主动关闭时开关已从系统设置移除，此处不再弹出主界面打扰用户。
        if (isServiceStillEnabledInSettings()) { // 仍启用 = 异常断开
            runCatching { // 尝试唤起进程
                val launchIntent = packageManager.getLaunchIntentForPackage(packageName) // 获取启动 Intent
                launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // 新任务栈
                if (launchIntent != null) startActivity(launchIntent) // 启动主 Activity 唤起进程
            }.onFailure { Logger.w("异常断开后唤起进程失败", it) } // 失败记录 warn
        }
        return super.onUnbind(intent) // 调用父类返回结果（传入原始 intent 参数）
    }

    /**
     * 判断系统设置中本无障碍服务是否仍处于"已启用"状态。
     *
     * 用于区分"用户主动关闭"与"异常断开"：主动关闭时系统会先把本服务从
     * [android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES] 中移除再回调解绑，
     * 此时返回 false；异常断开时开关仍在，返回 true。读取失败时保守返回 false（不打扰用户）。
     */
    private fun isServiceStillEnabledInSettings(): Boolean = runCatching { // 读取系统无障碍开关状态
        val enabled = android.provider.Settings.Secure.getString( // 读取安全设置字符串
            contentResolver, // 内容解析器
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES // 已启用无障碍服务键
        ) ?: "" // 为空时用空串
        enabled.contains("$packageName/") // 含本应用组件前缀即视为仍启用
    }.getOrDefault(false) // 读取异常时保守返回 false
}
