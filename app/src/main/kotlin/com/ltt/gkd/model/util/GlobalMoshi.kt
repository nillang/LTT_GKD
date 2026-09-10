package com.ltt.gkd.model.util

import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter

/** 全局 Moshi 单例（KSP codegen 已生成各数据类 adapter）。 */
val globalMoshi: Moshi = Moshi.Builder().build()

/** 反射式适配器快捷获取。 */
inline fun <reified T> globalAdapter() = globalMoshi.adapter<T>()
