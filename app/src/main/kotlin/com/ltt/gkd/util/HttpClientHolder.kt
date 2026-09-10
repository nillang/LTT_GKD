package com.ltt.gkd.util

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * 全局共享 OkHttpClient 单例。
 *
 * 复用连接池 + Dispatcher，避免多个模块各自创建客户端导致资源浪费。
 * - GistClient（GitHub Gist 订阅）
 * - RuleSubscriptionService（普通 HTTP 订阅）
 */
object HttpClientHolder {

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
