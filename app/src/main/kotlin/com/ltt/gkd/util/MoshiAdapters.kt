package com.ltt.gkd.util // 包声明：Moshi 适配器统一注册工具

import com.ltt.gkd.data.history.SkipRecord // 导入跳过记录数据类
import com.ltt.gkd.data.rule.MatchAction // 导入匹配动作数据类
import com.ltt.gkd.data.rule.MatchActionJsonAdapter // KSP 生成的 MatchAction 适配器
import com.ltt.gkd.data.rule.MatchTarget // 导入匹配目标数据类
import com.ltt.gkd.data.rule.MatchTargetJsonAdapter // KSP 生成的 MatchTarget 适配器
import com.ltt.gkd.data.rule.Rule // 导入规则数据类
import com.ltt.gkd.data.rule.RuleJsonAdapter // KSP 生成的 Rule 适配器
import com.ltt.gkd.data.rule.RuleSet // 导入规则集数据类
import com.ltt.gkd.data.rule.RuleSetJsonAdapter // KSP 生成的 RuleSet 适配器
import com.ltt.gkd.data.subscription.SubscriptionSource // 导入订阅源数据类
import com.ltt.gkd.data.subscription.SubscriptionSourceJsonAdapter // KSP 生成的 SubscriptionSource 适配器
import com.ltt.gkd.data.history.SkipRecordJsonAdapter // KSP 生成的 SkipRecord 适配器
import com.squareup.moshi.JsonAdapter // Moshi 适配器基类
import com.squareup.moshi.JsonAdapter.Factory // Moshi 适配器工厂接口
import com.squareup.moshi.Moshi // Moshi 主类
import java.lang.reflect.Type // Java 类型反射

/**
 * 统一的 Moshi 适配器工厂。
 *
 * 背景：moshi-kotlin-codegen（KSP）在编译期为每个 [@JsonClass(generateAdapter = true)] 数据类生成
 * 独立的 JsonAdapter 类（如 [RuleSetJsonAdapter]），但不会自动注册到 Moshi 实例。
 * Android APK 运行时 Moshi 可能通过某些内部机制发现它们，但 Robolectric 单元测试环境下不可靠，
 * 导致所有 JSON 解析在测试中静默失败（runCatching 吞掉异常）。
 *
 * 本工厂集中注册所有 KSP 生成的适配器，确保在任意环境（真机 / Robolectric / JVM）下都能正确解析。
 *
 * 用法：
 * ```kotlin
 * val moshi = Moshi.Builder().add(GeneratedAdaptersFactory).build()
 * ```
 */
object GeneratedAdaptersFactory : Factory { // Moshi 适配器工厂对象，实现 Factory 接口

    override fun create( // 核心方法：根据类型返回对应的 JsonAdapter
        type: Type, // 目标类型（由 Moshi 传入）
        annotations: Set<Annotation>, // 类型上的注解（当前所有类无自定义 Moshi 注解，忽略）
        moshi: Moshi // Moshi 实例（用于内部构造依赖类型的 adapter）
    ): JsonAdapter<*>? { // 返回对应适配器，未知类型返回 null 让 Moshi 继续查找
        return when (type) { // 按类型分发
            RuleSet::class.java -> RuleSetJsonAdapter(moshi) // RuleSet 规则集适配器
            Rule::class.java -> RuleJsonAdapter(moshi) // Rule 规则适配器
            MatchTarget::class.java -> MatchTargetJsonAdapter(moshi) // MatchTarget 匹配目标适配器
            MatchAction::class.java -> MatchActionJsonAdapter(moshi) // MatchAction 匹配动作适配器
            SubscriptionSource::class.java -> SubscriptionSourceJsonAdapter(moshi) // SubscriptionSource 订阅源适配器
            SkipRecord::class.java -> SkipRecordJsonAdapter(moshi) // SkipRecord 跳过记录适配器
            else -> null // 未知类型返回 null
        }
    }
}
