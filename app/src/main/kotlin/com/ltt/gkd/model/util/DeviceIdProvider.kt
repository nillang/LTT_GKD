package com.ltt.gkd.model.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import com.ltt.gkd.model.prefs.SettingsStore
import kotlinx.coroutines.flow.first

/**
 * 设备 ID 提供器：用于上传规则时标识作者。
 *
 * 方案：
 * - 首选 Android ID（Settings.Secure.ANDROID_ID），无需权限
 * - 首次获取后存入 DataStore 持久化，避免 Android 8+ 在不同签名下 ANDROID_ID 变化的问题
 * - Android 8+ ANDROID_ID 在签名+用户+设备维度稳定，足够自用场景
 *
 * 隐私：Android ID 不属于强标识符，无需用户授权；不会上传除"作者标识"以外的信息
 */
class DeviceIdProvider(private val context: Context, private val settings: SettingsStore) {

    /**
     * 获取持久化的设备 ID。
     * 若 DataStore 中已有则直接返回；否则读 Android ID 后存入并返回。
     */
    suspend fun get(): String {
        val cached = settings.deviceId.first()
        if (cached.isNotEmpty()) return cached
        val raw = readAndroidId().ifEmpty { fallbackUuid() }
        settings.setDeviceId(raw)
        return raw
    }

    @SuppressLint("HardwareIds")
    private fun readAndroidId(): String = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }.getOrDefault("")

    /** 极端情况下 Android ID 取不到，生成一个持久化的 UUID（已在 settings 里存）。 */
    private suspend fun fallbackUuid(): String {
        val uuid = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        settings.setDeviceId(uuid)
        return uuid
    }
}
