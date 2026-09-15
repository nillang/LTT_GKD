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
    @Volatile // 保证多线程可见性
    private var pkgForegroundAt = 0L // 当前应用切到前台的时间戳，用于界定"开屏窗口"
    @Volatile // 保证多线程可见性
    private var skippedThisLaunch = false // 本次进入该应用是否已成功跳过（跳过后即关闭通用兜底，避免误点首页）

    /** 包名 → 应用名缓存（PackageManager 查询不便宜，跳过成功才查一次）。 */
    private val appLabelCache = ConcurrentHashMap<String, String>() // 应用名缓存

    companion object {  // 静态常量
        /**
         * 开屏窗口时长（毫秒）：所有"基于关键词"的规则只在应用切到前台后的该时间窗内生效。
         * 开屏广告一般在冷启动数秒内出现，窗口取较短值以最大限度避免在应用内页误点。
         */
        private const val SPLASH_WINDOW_MS = 6000L  // 开屏窗口时长（毫秒）
    }

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
        // 应用切换时重置节流，并记录进入前台的时间（用于界定开屏窗口）
        val now = System.currentTimeMillis() // 当前时间戳
        if (pkg != lastPkg) { // 包名变化代表切换应用
            engine.resetThrottle() // 重置规则引擎节流
            lastPkg = pkg // 更新上次包名
            pkgForegroundAt = now // 记录本次进入前台时间
            skippedThisLaunch = false // 新的一次进入，重置"已跳过"标记
            Logger.d("切换到 $pkg") // 记录切换
        }
        val candidates = engine.candidates(pkg, cls, now) // 取出候选规则
        if (candidates.isEmpty()) return // 无候选直接返回
        // 系统性收敛：所有"基于关键词"的规则(TEXT/DESC/OCR，含应用专用与通用)只在开屏窗口内生效——
        // 即刚切到前台 SPLASH_WINDOW_MS 内、且本次尚未成功跳过。离开启动阶段后不再匹配任何文字，
        // 避免在应用内页(淘宝"我的/订单"、京东首页等)把"关闭/已关闭/取消/×"等正常状态或功能文案误当广告点击。
        // 只有精确的 ID 规则(match.type==ID，直指特定广告控件)不受时间窗限制。
        val inSplashWindow = (now - pkgForegroundAt) <= SPLASH_WINDOW_MS && !skippedThisLaunch // 是否仍处于开屏窗口
        val effective = candidates.filter { it.match.type == MatchType.ID || inSplashWindow } // 关键词规则仅开屏窗口内保留
        if (effective.isEmpty()) return // 过滤后无候选直接返回

        val root = service.rootInActiveWindow ?: run { // 取当前窗口根节点
            Logger.d("rootInActiveWindow 为 null") // 记录 debug
            return // 取不到直接返回
        }
        // 关键一致性校验：事件来源包名（快照）必须与"当前活动窗口"的真实包名一致。
        // 服务重连/窗口切换瞬间，event.packageName 可能指向旧窗口，而 rootInActiveWindow 已是新窗口，
        // 二者错位会导致拿旧包名的规则去匹配新窗口的节点树（曾出现：拿微信/李跳跳的事件去匹配
        // 小狐自己界面的"服务已开启，自动跳过广告中"里的"跳过"，误点并卡死 UI）。此处用 root 真实包名兜底，不一致则跳过。
        val rootPkg = root.packageName?.toString() // 取活动窗口真实包名
        if (rootPkg != pkg) { // 事件包名与窗口包名不一致
            Logger.d("跳过错位事件：事件包名 $pkg 与窗口包名 ${rootPkg ?: "null"} 不一致") // 记录 debug
            return // 直接跳过，避免跨窗口误点
        }

        // 节点匹配
        try { // 保证 root 在 finally 中被回收
            for (rule in effective) { // 遍历候选规则（已按开屏窗口收敛通用规则）
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
            val ocrCandidates = effective.filter { it.match.type == MatchType.OCR } // 取 OCR 类型候选（同样受开屏窗口收敛）
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
        skippedThisLaunch = true // 本次进入已成功跳过：关闭通用兜底，避免随后在首页/信息流误点
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
