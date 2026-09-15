package com.ltt.gkd.accessibility // 包声明：本文件属于无障碍处理包 com.ltt.gkd.accessibility

import android.accessibilityservice.AccessibilityService // 导入 AccessibilityService，作为 service 字段类型
import android.view.accessibility.AccessibilityNodeInfo // 导入 AccessibilityNodeInfo，无障碍节点
import com.ltt.gkd.action.ActionExecutor // 导入 ActionExecutor，动作执行器
import com.ltt.gkd.data.app.WhitelistStore // 导入 WhitelistStore，白名单存储
import com.ltt.gkd.data.history.SkipHistoryStore // 导入 SkipHistoryStore，跳过历史
import com.ltt.gkd.data.prefs.SettingsStore // 导入 SettingsStore，设置存储
import com.ltt.gkd.ocr.OcrManager // 导入 OcrManager，OCR 管理器
import com.ltt.gkd.data.rule.ActionType // 导入 ActionType，动作类型枚举
import com.ltt.gkd.data.rule.MatchType // 导入 MatchType，匹配类型枚举（含 OCR）
import com.ltt.gkd.data.rule.Rule // 导入 Rule，规则数据类
import com.ltt.gkd.data.rule.RuleEngine // 导入 RuleEngine，规则引擎
import com.ltt.gkd.data.rule.RuleMatcher // 导入 RuleMatcher，规则匹配器
import com.ltt.gkd.data.rule.RuleRepository // 导入 RuleRepository，规则仓库
import com.ltt.gkd.util.Logger // 导入 Logger，日志
import com.ltt.gkd.util.NodeUtils // 导入 NodeUtils，节点工具
import kotlinx.coroutines.flow.first // 导入 first，取 Flow 首值
import kotlinx.coroutines.sync.Mutex // 导入 Mutex，事件串行化
import kotlinx.coroutines.sync.withLock // 导入 withLock，加锁执行
import java.util.concurrent.ConcurrentHashMap // 导入 ConcurrentHashMap，线程安全缓存

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
    private val service: AccessibilityService, // 无障碍服务，提供 rootInActiveWindow 等
    private val repo: RuleRepository, // 规则仓库
    private val engine: RuleEngine, // 规则引擎，提供候选与节流
    private val matcher: RuleMatcher, // 规则匹配器
    private val executor: ActionExecutor, // 动作执行器
    private val ocr: OcrManager, // OCR 管理器
    private val settings: SettingsStore, // 用户设置
    private val whitelist: WhitelistStore, // 应用白名单
    private val history: SkipHistoryStore // 跳过历史
) {

    private val lock = Mutex() // 串行化事件处理，避免并发触发误点
    @Volatile // 保证多线程可见性
    private var lastPkg: String? = null // 上次事件包名，用于检测应用切换

    /** 包名 → 应用名缓存（PackageManager 查询不便宜，跳过成功才查一次）。 */
    private val appLabelCache = ConcurrentHashMap<String, String>() // 应用名缓存

    suspend fun handle(pkg: String?, cls: String?) { // 入口：处理事件（接收同步快照，避免异步使用已被系统回收的 event）
        lock.withLock { // 加锁避免并发处理
            processEvent(pkg, cls) // 转交内部处理
        }
    }

    private suspend fun processEvent(pkgRaw: String?, cls: String?) { // 内部：实际处理逻辑
        val pkg = pkgRaw ?: return // 取事件来源包名，无则返回
        // 绝不处理本应用自身窗口：否则通用兜底规则会匹配到小狐自己界面上的"跳过/关闭/关闭服务"等文字，
        // 自动点击电源按钮（contentDescription="关闭服务"）→ 跳系统无障碍设置 → 返回后再次自点 → 无限跳转死循环。
        if (pkg == service.packageName) { // 事件来自本应用自身
            Logger.d("忽略本应用自身窗口事件") // 记录 debug
            return // 直接短路，不做任何匹配/点击
        }
        // 白名单检查：O(1) HashSet 查询，必须在候选筛选前短路
        if (whitelist.isWhitelisted(pkg)) { // 包名在白名单
            Logger.d("$pkg 在白名单内，跳过处理") // 记录 debug 日志
            return // 直接返回
        }
        // 应用切换时重置节流
        if (pkg != lastPkg) { // 包名变化代表切换应用
            engine.resetThrottle() // 重置规则引擎节流
            lastPkg = pkg // 更新上次包名
            Logger.d("切换到 $pkg") // 记录切换
        }
        val now = System.currentTimeMillis() // 当前时间戳
        val candidates = engine.candidates(pkg, cls, now) // 取出候选规则
        if (candidates.isEmpty()) return // 无候选直接返回

        val root = service.rootInActiveWindow ?: run { // 取当前窗口根节点
            Logger.d("rootInActiveWindow 为 null") // 记录 debug
            return // 取不到直接返回
        }

        // 节点匹配
        try { // 保证 root 在 finally 中被回收
            for (rule in candidates) { // 遍历候选规则
                if (rule.match.type == MatchType.OCR) continue // OCR 兜底稍后统一处理
                val hit = matcher.match(root, rule) ?: continue // 在控件树中匹配，无命中跳过
                Logger.i("命中规则 ${rule.id} (${rule.name})") // 记录命中
                // 先提取命中文本（节点稍后会被 recycle）
                val matchedText = (hit.text ?: hit.contentDescription)?.toString() // 取命中文本/描述
                try { // 保证 hit 在 finally 中被回收
                    val ok = executor.execute(rule.action, node = hit) // 执行规则动作
                    if (ok) { // 执行成功
                        engine.markTriggered(rule.id, System.currentTimeMillis()) // 标记规则已触发节流
                        onSkipSucceeded(pkg, rule, matchedText) // 记录跳过成功
                        return // 一个窗口一次只跳过一次，避免误触发
                    }
                } finally {
                    NodeUtils.safeRecycle(hit) // 回收命中节点
                }
            }

            // OCR 兜底
            val ocrEnabled = settings.ocrEnabled.first() // 读 OCR 开关
            if (!ocrEnabled) return // 未开启直接返回
            val ocrCandidates = candidates.filter { it.match.type == MatchType.OCR } // 取 OCR 类型候选
            if (ocrCandidates.isEmpty()) return // 无 OCR 候选直接返回
            Logger.d("OCR 兜底启动 (${ocrCandidates.size} 条候选)") // 记录 OCR 启动
            val ocrHit = ocr.matchAndClick(ocrCandidates, pkg) // 调用 OCR 匹配并点击
            if (ocrHit != null) { // OCR 命中
                engine.markTriggered(ocrHit.id, System.currentTimeMillis()) // 标记规则触发
                onSkipSucceeded(pkg, ocrHit, null) // 记录跳过成功
            }
        } finally {
            NodeUtils.safeRecycle(root) // 回收根节点
        }
    }

    private suspend fun onSkipSucceeded(pkg: String, rule: Rule, matchedText: String?) { // 内部：跳过成功后处理
        settings.incrementTotalSkip() // 累计跳过计数
        history.record(resolveAppName(pkg), rule, rule.action.type, matchedText) // 写入历史记录
        val enableNoti = settings.skipNotificationEnabled.first() // 读通知开关
        if (enableNoti) { // 通知开启
            SkipNotifier.notify(service, rule.name) // 发送跳过通知
        }
    }

    /** 解析包名对应的应用名（带缓存，查不到时回退包名）。 */
    private fun resolveAppName(pkg: String): String = appLabelCache.getOrPut(pkg) { // 内部：取应用名，带缓存
        runCatching { // 容错执行
            val pm = service.packageManager // 取 PackageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() // 查应用名并转字符串
        }.getOrDefault(pkg) // 失败时回退为包名
    }
}
