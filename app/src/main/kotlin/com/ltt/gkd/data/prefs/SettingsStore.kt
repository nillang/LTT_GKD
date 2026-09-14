package com.ltt.gkd.data.prefs  // 声明包名，设置存储所在的包

import android.content.Context  // 导入 Context 类，用于访问应用上下文
import androidx.datastore.core.DataStore  // 导入 DataStore 接口
import androidx.datastore.preferences.core.Preferences  // 导入 Preferences 数据类型
import androidx.datastore.preferences.core.booleanPreferencesKey  // 导入布尔键构造函数
import androidx.datastore.preferences.core.edit  // 导入 edit 扩展
import androidx.datastore.preferences.core.intPreferencesKey  // 导入整型键构造函数
import androidx.datastore.preferences.core.stringPreferencesKey  // 导入字符串键构造函数
import androidx.datastore.preferences.core.stringSetPreferencesKey  // 导入字符串集合键构造函数
import androidx.datastore.preferences.preferencesDataStore  // 导入顶层 preferencesDataStore 委托
import com.ltt.gkd.util.TokenCipher  // 导入 Token 加解密工具
import kotlinx.coroutines.flow.Flow  // 导入 Flow，冷流
import kotlinx.coroutines.flow.map  // 导入 map 操作符

/**
 * 全局 DataStore 委托（文件级别，整个应用共享一个实例）。
 * 必须定义在顶层，不能放在类内部——否则每次实例化 SettingsStore
 * 都会创建新的 dataStore 属性，导致 "multiple DataStores active for the same file" 崩溃。
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")  // 顶层 DataStore 委托，名为 "settings"

/**
 * 全局设置存储（DataStore）。
 *
 * 注意：因为 [Logger] 在 [App.onCreate] 早期就要读 logEnabled，
 * 这里没有依赖注入；直接构造即可。
 */
class SettingsStore(private val context: Context) {  // 设置存储类

    /** 日志开关：控制 Logger V/D 级输出（I/W/E 始终输出）。 */
    val logEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOG] ?: false }  // 日志开关 Flow，默认 false
    /** OCR 兜底开关：控件树找不到时截图识别。 */
    val ocrEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_OCR] ?: true }  // OCR 开关 Flow，默认 true
    /** 自动订阅更新开关。 */
    val subscriptionEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUB] ?: false }  // 订阅开关 Flow，默认 false
    /** 订阅 URL（Gist ID）。 */
    val subscriptionUrl: Flow<String> = context.dataStore.data.map { it[KEY_SUB_URL] ?: "" }  // 订阅 URL Flow，默认空
    /** 自动更新间隔（小时），默认 24h。 */
    val subscriptionIntervalHours: Flow<Int> = context.dataStore.data.map { it[KEY_SUB_INTERVAL] ?: 24 }  // 更新间隔 Flow，默认 24 小时
    /** 跳过成功后是否发送通知。 */
    val skipNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTI_SKIP] ?: false }  // 跳过通知开关 Flow，默认 false
    /** 累计跳过总次数（仅增不减，除非用户手动重置）。 */
    val totalSkipCount: Flow<Int> = context.dataStore.data.map { it[KEY_TOTAL_SKIP] ?: 0 }  // 累计跳过次数 Flow，默认 0

    /** 用户手动禁用的规则 ID 集合（适用于所有来源；未在集合内视为启用）。 */
    val disabledRuleIds: Flow<Set<String>> =  // 禁用规则 ID 集合 Flow
        context.dataStore.data.map { it[KEY_DISABLED_RULES] ?: emptySet() }  // 取集合，缺失则空集合

    /** 切换某条规则的启用状态。 */
    suspend fun setRuleEnabled(ruleId: String, enabled: Boolean) = context.dataStore.edit { prefs ->  // 切换规则启用状态方法
        val set = prefs[KEY_DISABLED_RULES]?.toMutableSet() ?: mutableSetOf()  // 取禁用集合或新建
        if (enabled) set.remove(ruleId) else set.add(ruleId)  // 启用则从禁用集合移除，禁用则加入
        prefs[KEY_DISABLED_RULES] = set  // 写回
    }

    /** 设备 ID（作者标识，上传规则时追溯）。 */
    val deviceId: Flow<String> = context.dataStore.data.map { it[KEY_DEVICE_ID] ?: "" }  // 设备 ID Flow，默认空
    suspend fun setDeviceId(id: String) = context.dataStore.edit { it[KEY_DEVICE_ID] = id }  // 设置设备 ID 方法

    // ---- GitHub 上传相关（Token 经 TokenCipher AES-GCM 加密存储） ----
    /** GitHub Personal Access Token（读取时自动解密）。 */
    val githubToken: Flow<String> = context.dataStore.data.map {  // GitHub Token Flow
        TokenCipher.decrypt(it[KEY_GH_TOKEN] ?: "")  // 读取时 AES-GCM 解密
    }
    /** Gist ID（首次上传后自动生成并保存）。 */
    val gistId: Flow<String> = context.dataStore.data.map { it[KEY_GIST_ID] ?: "" }  // Gist ID Flow，默认空
    suspend fun setGithubToken(t: String) = context.dataStore.edit {  // 设置 GitHub Token 方法
        it[KEY_GH_TOKEN] = TokenCipher.encrypt(t)  // 写入时 AES-GCM 加密
    }
    suspend fun setGistId(id: String) = context.dataStore.edit { it[KEY_GIST_ID] = id }  // 设置 Gist ID 方法

    // ---- 设置项写入 ----
    suspend fun setLog(enabled: Boolean) = context.dataStore.edit { it[KEY_LOG] = enabled }  // 设置日志开关
    suspend fun setOcr(enabled: Boolean) = context.dataStore.edit { it[KEY_OCR] = enabled }  // 设置 OCR 开关
    suspend fun setSubscription(enabled: Boolean) = context.dataStore.edit { it[KEY_SUB] = enabled }  // 设置订阅开关
    suspend fun setSubscriptionUrl(url: String) = context.dataStore.edit { it[KEY_SUB_URL] = url }  // 设置订阅 URL
    suspend fun setSubscriptionInterval(hours: Int) = context.dataStore.edit { it[KEY_SUB_INTERVAL] = hours }  // 设置更新间隔
    suspend fun setSkipNotification(enabled: Boolean) = context.dataStore.edit { it[KEY_NOTI_SKIP] = enabled }  // 设置跳过通知开关
    /** 跳过成功 +1（由 WindowEventProcessor 调用）。 */
    suspend fun incrementTotalSkip() = context.dataStore.edit {  // 跳过计数 +1 方法
        it[KEY_TOTAL_SKIP] = (it[KEY_TOTAL_SKIP] ?: 0) + 1  // 累加 1
    }
    /** 重置累计跳过计数为 0（用户手动触发）。 */
    suspend fun resetTotalSkip() = context.dataStore.edit { it[KEY_TOTAL_SKIP] = 0 }  // 重置跳过计数方法

    companion object {  // 静态键定义
        private val KEY_LOG = booleanPreferencesKey("log_enabled")  // 日志开关键
        private val KEY_OCR = booleanPreferencesKey("ocr_enabled")  // OCR 开关键
        private val KEY_SUB = booleanPreferencesKey("subscription_enabled")  // 订阅开关键
        private val KEY_SUB_URL = stringPreferencesKey("subscription_url")  // 订阅 URL 键
        private val KEY_SUB_INTERVAL = intPreferencesKey("subscription_interval_hours")  // 更新间隔键
        private val KEY_NOTI_SKIP = booleanPreferencesKey("skip_notification_enabled")  // 跳过通知开关键
        private val KEY_TOTAL_SKIP = intPreferencesKey("total_skip_count")  // 累计跳过次数键
        private val KEY_DISABLED_RULES = stringSetPreferencesKey("disabled_rule_ids")  // 禁用规则 ID 集合键
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")  // 设备 ID 键
        private val KEY_GH_TOKEN = stringPreferencesKey("github_token")  // GitHub Token 键（加密存储）
        private val KEY_GIST_ID = stringPreferencesKey("gist_id")  // Gist ID 键
    }
}
