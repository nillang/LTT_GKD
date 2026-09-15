package com.ltt.gkd.data.subscription  // 声明包名，订阅模块所在的包

import android.content.Context  // 导入 Context，访问 DataStore
import androidx.datastore.core.DataStore  // 导入 DataStore 接口
import androidx.datastore.preferences.core.Preferences  // 导入 Preferences 数据类型
import androidx.datastore.preferences.core.edit  // 导入 edit 扩展
import androidx.datastore.preferences.core.stringPreferencesKey  // 导入字符串键构造
import androidx.datastore.preferences.preferencesDataStore  // 导入顶层 preferencesDataStore 委托
import com.ltt.gkd.util.Logger  // 导入日志工具
import com.ltt.gkd.util.globalMoshi  // 导入全局 Moshi 单例
import com.squareup.moshi.JsonAdapter  // 导入 JsonAdapter 类型
import com.squareup.moshi.Types  // 导入 Types，构造 List 的参数化类型
import kotlinx.coroutines.flow.Flow  // 导入 Flow
import kotlinx.coroutines.flow.map  // 导入 map 操作符

/**
 * 全局订阅源 DataStore 委托（文件级别，整个应用共享一个实例）。
 * 必须定义在顶层，不能放在类内部——否则会触发 "multiple DataStores active for the same file" 崩溃（见开发文档 §3.2）。
 */
private val Context.subsDataStore: DataStore<Preferences> by preferencesDataStore(name = "subscriptions")  // 顶层 DataStore 委托

/**
 * 订阅源存储：持久化"多个订阅源"的列表（DataStore，单键存 JSON 数组）。
 *
 * 职责：源的增删改查、启用/禁用、同步结果回写（上次同步时间/规则数/错误）。
 * 所有写操作都在一次 [edit] 内读-改-写整个列表，保证一致性。
 */
class SubscriptionStore(private val context: Context) {  // 订阅源存储类

    // List<SubscriptionSource> 的参数化适配器：reified adapter<T>() 会丢失泛型参数，故显式用 Types 构造
    private val listAdapter: JsonAdapter<List<SubscriptionSource>> =  // 列表 JSON 适配器
        globalMoshi.adapter(Types.newParameterizedType(List::class.java, SubscriptionSource::class.java))  // 构造 List<SubscriptionSource> 类型

    /** 订阅源列表 Flow（按添加顺序）。解析失败时回退空列表，保证不崩。 */
    val sources: Flow<List<SubscriptionSource>> = context.subsDataStore.data.map { prefs ->  // 源列表 Flow
        decode(prefs[KEY_SOURCES])  // 读取并解码
    }

    /** 解码 JSON 字符串为源列表；空或解析失败返回空列表。 */
    private fun decode(json: String?): List<SubscriptionSource> =  // 解码方法
        if (json.isNullOrEmpty()) emptyList()  // 空直接返回空列表
        else runCatching { listAdapter.fromJson(json) ?: emptyList() }  // 尝试解析
            .getOrElse { Logger.w("解析订阅源列表失败", it); emptyList() }  // 失败记日志并返回空

    /** 编码源列表为 JSON 字符串。 */
    private fun encode(list: List<SubscriptionSource>): String =  // 编码方法
        runCatching { listAdapter.toJson(list) }.getOrDefault("[]")  // 序列化，失败回退空数组

    /**
     * 添加一个订阅源。
     *
     * 自动生成内部 ID（用于规则文件前缀），并按 URL 识别 [SourceType]。
     * 若已存在相同 URL 的源，则复用其 ID 更新名称（避免重复添加）。
     *
     * @param name 显示名称
     * @param url 订阅链接（Gist 链接/ID 或普通 URL）
     * @return 新增或更新后的订阅源
     */
    suspend fun add(name: String, url: String): SubscriptionSource {  // 添加订阅源方法
        val trimmed = url.trim()  // 去空白
        val type = SubscriptionUrls.detectType(trimmed)  // 识别类型
        var result: SubscriptionSource? = null  // 结果暂存
        mutate { list ->  // 读-改-写
            val existing = list.firstOrNull { it.url.trim() == trimmed }  // 查是否已存在同 URL
            val source = existing?.copy(name = name.ifBlank { existing.name }, type = type)  // 已存在则更新名称/类型
                ?: SubscriptionSource(  // 否则新建
                    id = newId(),  // 生成 ID
                    name = name.ifBlank { defaultName(trimmed, type) },  // 名称缺省用链接推断
                    url = trimmed,  // 链接
                    type = type  // 类型
                )
            if (existing != null) {  // 更新已有
                list[list.indexOf(existing)] = source  // 原位替换
            } else {  // 追加新源
                list.add(source)  // 加到末尾
            }
            result = source  // 记录结果
        }
        return result ?: SubscriptionSource(id = newId(), name = name, url = trimmed, type = type)  // 兜底返回
    }

    /** 更新（整体替换）一个订阅源。 */
    suspend fun update(source: SubscriptionSource) = mutate { list ->  // 更新源方法
        val i = list.indexOfFirst { it.id == source.id }  // 查下标
        if (i >= 0) list[i] = source  // 存在则替换
    }

    /** 按 ID 删除订阅源。 */
    suspend fun delete(id: String) = mutate { list ->  // 删除源方法
        list.removeAll { it.id == id }  // 移除同 ID
    }

    /** 启用/禁用某个源。 */
    suspend fun setEnabled(id: String, enabled: Boolean) = mutate { list ->  // 切换启用状态方法
        val i = list.indexOfFirst { it.id == id }  // 查下标
        if (i >= 0) list[i] = list[i].copy(enabled = enabled)  // 更新 enabled
    }

    /**
     * 回写同步结果（上次同步时间/规则数/错误）。
     *
     * @param id 源 ID
     * @param ruleCount 本次同步规则条数（失败时传 0 或保持原值由调用方决定）
     * @param error 错误信息；为空表示成功
     * @param success 是否成功（成功才更新 lastSyncAt 与 lastRuleCount）
     */
    suspend fun setSyncResult(id: String, ruleCount: Int, error: String, success: Boolean) = mutate { list ->  // 回写同步结果方法
        val i = list.indexOfFirst { it.id == id }  // 查下标
        if (i >= 0) {  // 找到源
            val s = list[i]  // 取出
            list[i] = if (success) {  // 成功
                s.copy(lastSyncAt = System.currentTimeMillis(), lastRuleCount = ruleCount, lastError = "")  // 更新时间/条数，清空错误
            } else {  // 失败
                s.copy(lastError = error)  // 仅记录错误
            }
        }
    }

    /** 统一的读-改-写：在一次 DataStore edit 内解码为可变列表、执行 [block]、再编码写回。 */
    private suspend fun mutate(block: (MutableList<SubscriptionSource>) -> Unit) {  // 读-改-写辅助方法
        context.subsDataStore.edit { prefs ->  // 单次编辑
            val list = decode(prefs[KEY_SOURCES]).toMutableList()  // 解码为可变列表
            block(list)  // 执行修改
            prefs[KEY_SOURCES] = encode(list)  // 编码写回
        }
    }

    /** 生成文件名安全的短 ID。 */
    private fun newId(): String = "s" + System.currentTimeMillis().toString(36) +  // 时间戳 36 进制
            (100..999).random().toString()  // 追加随机数降低同毫秒碰撞

    /** 依据链接推断缺省名称。 */
    private fun defaultName(url: String, type: SourceType): String = when (type) {  // 缺省名称
        SourceType.GIST -> "Gist " + (SubscriptionUrls.gistId(url)?.take(8) ?: "")  // Gist 用短 ID
        SourceType.URL -> url.substringAfterLast('/').ifBlank { "订阅源" }  // URL 用末段
    }

    companion object {  // 静态键
        private val KEY_SOURCES = stringPreferencesKey("subscription_sources")  // 订阅源列表 JSON 键
    }
}
