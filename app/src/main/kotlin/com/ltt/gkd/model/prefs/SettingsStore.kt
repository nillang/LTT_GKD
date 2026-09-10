package com.ltt.gkd.model.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 全局设置存储（DataStore）。
 *
 * 注意：因为 [Logger] 在 [App.onCreate] 早期就要读 logEnabled，
 * 这里没有依赖注入；直接构造即可。
 */
class SettingsStore(private val context: Context) {

    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    val logEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LOG] ?: false }
    val ocrEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_OCR] ?: true }
    val subscriptionEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUB] ?: false }
    val subscriptionUrl: Flow<String> = context.dataStore.data.map { it[KEY_SUB_URL] ?: "" }
    val subscriptionIntervalHours: Flow<Int> = context.dataStore.data.map { it[KEY_SUB_INTERVAL] ?: 24 }
    val skipNotificationEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_NOTI_SKIP] ?: false }
    val totalSkipCount: Flow<Int> = context.dataStore.data.map { it[KEY_TOTAL_SKIP] ?: 0 }

    // 设备 ID（作者标识）
    val deviceId: Flow<String> = context.dataStore.data.map { it[KEY_DEVICE_ID] ?: "" }
    suspend fun setDeviceId(id: String) = context.dataStore.edit { it[KEY_DEVICE_ID] = id }

    // GitHub 上传相关
    val githubToken: Flow<String> = context.dataStore.data.map { it[KEY_GH_TOKEN] ?: "" }
    val gistId: Flow<String> = context.dataStore.data.map { it[KEY_GIST_ID] ?: "" }
    suspend fun setGithubToken(t: String) = context.dataStore.edit { it[KEY_GH_TOKEN] = t }
    suspend fun setGistId(id: String) = context.dataStore.edit { it[KEY_GIST_ID] = id }

    suspend fun setLog(enabled: Boolean) = context.dataStore.edit { it[KEY_LOG] = enabled }
    suspend fun setOcr(enabled: Boolean) = context.dataStore.edit { it[KEY_OCR] = enabled }
    suspend fun setSubscription(enabled: Boolean) = context.dataStore.edit { it[KEY_SUB] = enabled }
    suspend fun setSubscriptionUrl(url: String) = context.dataStore.edit { it[KEY_SUB_URL] = url }
    suspend fun setSubscriptionInterval(hours: Int) = context.dataStore.edit { it[KEY_SUB_INTERVAL] = hours }
    suspend fun setSkipNotification(enabled: Boolean) = context.dataStore.edit { it[KEY_NOTI_SKIP] = enabled }
    suspend fun incrementTotalSkip() = context.dataStore.edit {
        it[KEY_TOTAL_SKIP] = (it[KEY_TOTAL_SKIP] ?: 0) + 1
    }
    suspend fun resetTotalSkip() = context.dataStore.edit { it[KEY_TOTAL_SKIP] = 0 }

    companion object {
        private val KEY_LOG = booleanPreferencesKey("log_enabled")
        private val KEY_OCR = booleanPreferencesKey("ocr_enabled")
        private val KEY_SUB = booleanPreferencesKey("subscription_enabled")
        private val KEY_SUB_URL = stringPreferencesKey("subscription_url")
        private val KEY_SUB_INTERVAL = intPreferencesKey("subscription_interval_hours")
        private val KEY_NOTI_SKIP = booleanPreferencesKey("skip_notification_enabled")
        private val KEY_TOTAL_SKIP = intPreferencesKey("total_skip_count")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_GH_TOKEN = stringPreferencesKey("github_token")
        private val KEY_GIST_ID = stringPreferencesKey("gist_id")
    }
}
