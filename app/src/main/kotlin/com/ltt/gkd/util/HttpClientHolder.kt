package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import okhttp3.OkHttpClient // 导入 OkHttpClient，HTTP 客户端
import java.util.concurrent.TimeUnit // 导入 TimeUnit，超时时间单位

/**
 * 全局共享 OkHttpClient 单例。
 *
 * 复用连接池 + Dispatcher，避免多个模块各自创建客户端导致资源浪费。
 * - GistClient（GitHub Gist 订阅）
 * - RuleSubscriptionService（普通 HTTP 订阅）
 */
object HttpClientHolder {

    /**
     * 全局共享的 [OkHttpClient] 单例，首次访问时通过 lazy 初始化。
     *
     * 配置：连接/写入超时 15s、读取超时 30s，并在网络异常时自动重试。
     */
    val client: OkHttpClient by lazy { // 延迟初始化的共享客户端，首次访问时构建
        OkHttpClient.Builder() // 构造 OkHttpClient
            .connectTimeout(15, TimeUnit.SECONDS) // 连接超时
            .readTimeout(30, TimeUnit.SECONDS)   // 读取超时（订阅源可能较慢）
            .writeTimeout(15, TimeUnit.SECONDS) // 写入超时
            .retryOnConnectionFailure(true)      // 网络抖动时自动重试
            .build() // 构建客户端
    }
}
