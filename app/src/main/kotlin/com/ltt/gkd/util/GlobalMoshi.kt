@file:OptIn(kotlin.ExperimentalStdlibApi::class) // 文件级 OptIn：声明使用实验性标准库 API

package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import com.squareup.moshi.Moshi // 导入 Moshi 主类，JSON 序列化库入口
import com.squareup.moshi.adapter // 导入 reified 泛型 adapter 扩展函数

/**
 * 全局 Moshi 单例。
 *
 * Moshi 是 Square 出品的 JSON 序列化库，性能优于 Gson 且支持 KSP 代码生成适配器。
 * 此处使用默认配置构建，数据类的适配器已由 KSP codegen 在编译期生成，无需额外注册。
 *
 * 使用方式：通过 [globalAdapter] 获取指定类型的适配器进行 JSON 解析。
 */
val globalMoshi: Moshi = Moshi.Builder() // 开始构建全局 Moshi 实例
    .add(GeneratedAdaptersFactory) // 注册所有 KSP 生成的数据类适配器（Rule/RuleSet/MatchTarget 等）
    .build() // 完成构建

/**
 * 反射式获取 [globalMoshi] 中指定类型 [T] 的 Json 适配器。
 *
 * 通过 reified 泛型避免显式传递 [java.lang.reflect.Type]，调用形如 `globalAdapter<User>()`。
 * 内部使用 Moshi 的 `@JsonAdapter` 注解或反射查找对应 adapter。
 *
 * @param T 目标数据类型
 * @return [com.squareup.moshi.JsonAdapter] 实例，可用于 `fromJson` / `toJson`
 */
inline fun <reified T> globalAdapter() = globalMoshi.adapter<T>() // 内联函数：根据 reified 泛型从 globalMoshi 取出对应类型的 JsonAdapter
