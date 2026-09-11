package com.ltt.gkd.util // 包声明：本文件属于工具包 com.ltt.gkd.util

import android.annotation.SuppressLint // 导入 SuppressLint，用于抑制 HardwareIds 警告
import android.content.Context // 导入 Context，访问系统服务与 ContentResolver
import android.provider.Settings // 导入 Settings，读取 Settings.Secure.ANDROID_ID
import com.ltt.gkd.data.prefs.SettingsStore // 导入 SettingsStore，持久化存储
import kotlinx.coroutines.flow.first // 导入 first，从 Flow 取首值

/**
 * 设备 ID 提供器：用于上传规则时标识作者。
 *
 * 方案：
 * - 首选 Android ID（Settings.Secure.ANDROID_ID），无需权限
 * - 首次获取后存入 DataStore 持久化，避免 Android 8+ 在不同签名下 ANDROID_ID 变化的问题
 * - Android 8+ ANDROID_ID 在签名+用户+设备维度稳定，足够自用场景
 *
 * 隐私：Android ID 不属于强标识符，无需用户授权；不会上传除"作者标识"以外的信息
 *
 * @param context 用于访问 ContentResolver 读取 Android ID
 * @param settings 持久化存储，用于缓存设备 ID
 */
class DeviceIdProvider(private val context: Context, private val settings: SettingsStore) { // 构造：保存上下文与持久化存储

    /**
     * 获取持久化的设备 ID。
     *
     * 若 DataStore 中已有则直接返回；否则读 Android ID 后存入并返回。
     *
     * @return 持久化后的设备 ID 字符串
     */
    suspend fun get(): String { // 入口：获取持久化的设备 ID
        val cached = settings.deviceId.first() // 读取已缓存的设备 ID
        if (cached.isNotEmpty()) return cached // 已缓存非空直接返回
        val raw = readAndroidId().ifEmpty { fallbackUuid() } // 取不到 Android ID 则降级 UUID
        settings.setDeviceId(raw) // 写入持久化存储
        return raw // 返回设备 ID
    }

    /**
     * 读取系统 Android ID（Settings.Secure.ANDROID_ID）。
     *
     * 无需运行时权限；任何异常或取值为 null 都返回空字符串。
     *
     * @return Android ID 原始值；取不到返回空字符串
     */
    @SuppressLint("HardwareIds") // 抑制读取硬件标识符的 lint 警告（此处用途合规）
    private fun readAndroidId(): String = runCatching { // 入口：读取 Android ID
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "" // 通过 ContentResolver 读 ANDROID_ID，为 null 则返回空串
    }.getOrDefault("") // 异常时返回空串

    /**
     * 极端情况下 Android ID 取不到，生成一个持久化的 UUID（已在 settings 里存）。
     *
     * 取 32 位 UUID 去掉连字符后截取前 16 位，作为备用设备标识并持久化。
     *
     * @return 16 位十六进制字符串形式的设备标识
     */
    private suspend fun fallbackUuid(): String { // 入口：生成备用 UUID 标识
        val uuid = java.util.UUID.randomUUID().toString().replace("-", "").take(16) // 随机 UUID 去掉连字符取前 16 位
        settings.setDeviceId(uuid) // 持久化存储
        return uuid // 返回备用标识
    }
}
