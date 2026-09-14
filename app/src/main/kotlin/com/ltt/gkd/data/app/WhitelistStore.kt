package com.ltt.gkd.data.app  // 声明包名，应用白名单存储所在的包

import android.content.Context  // 导入 Context 类，用于访问应用上下文
import android.content.pm.ApplicationInfo  // 导入 ApplicationInfo 类，用于读 flags
import android.content.pm.PackageManager  // 导入 PackageManager 类，用于查询应用
import androidx.datastore.core.DataStore  // 导入 DataStore 接口，用于持久化
import androidx.datastore.preferences.core.Preferences  // 导入 Preferences 数据类型
import androidx.datastore.preferences.core.edit  // 导入 edit 扩展，编辑 DataStore
import androidx.datastore.preferences.core.stringSetPreferencesKey  // 导入字符串集合键构造函数
import androidx.datastore.preferences.preferencesDataStore  // 导入顶层 preferencesDataStore 委托
import com.ltt.gkd.util.Logger  // 导入日志工具类
import kotlinx.coroutines.flow.StateFlow  // 导入 StateFlow，对外暴露状态
import kotlinx.coroutines.flow.map  // 导入 map 操作符，转换 Flow 数据
import kotlinx.coroutines.flow.stateIn  // 导入 stateIn，将 Flow 转 StateFlow
import kotlinx.coroutines.CoroutineScope  // 导入协程作用域
import kotlinx.coroutines.Dispatchers  // 导入协程调度器
import kotlinx.coroutines.SupervisorJob  // 导入 SupervisorJob，子协程异常不传染
import kotlinx.coroutines.launch  // 导入 launch，启动协程

/**
 * 全局 DataStore 委托（文件级别，整个应用共享一个实例）。
 * 必须定义在顶层，不能放在类内部——否则每次实例化 WhitelistStore
 * 都会创建新的 dataStore 属性，导致 "multiple DataStores active for the same file" 崩溃。
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "whitelist")  // 顶层 DataStore 委托，名为 "whitelist"

/**
 * 应用白名单存储：持久化"跳过广告时需排除的包名集合"。
 *
 * 设计：
 * - 白名单表示"不对该应用执行跳广告操作"（与李跳跳"默认关闭=白名单"语义一致）
 * - DataStore 持久化 Set<String>；启动时自动将系统应用和关键默认应用加入白名单
 * - 暴露 [whitelist] StateFlow，供 UI 和无障碍服务订阅
 * - 内存 HashSet 缓存保证 O(1) 查询，service 无需每次读 DataStore
 *
 * 默认自动加入白名单的系统/关键应用：
 * - FLAG_SYSTEM 标记的系统应用
 * - 常用默认应用：启动器、设置、电话、短信、相机、相册、浏览器、文件管理、时钟等
 */
class WhitelistStore(private val context: Context) {  // 白名单存储类

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)  // 内部协程作用域，IO 线程 + SupervisorJob

    /** 当前白名单（包名集合）。StateFlow 保证订阅者始终拿到最新值。 */
    val whitelist: StateFlow<Set<String>> = context.dataStore.data  // 从 DataStore 数据 Flow 开始
        .map { it[KEY_WHITELIST] ?: emptySet() }  // 取白名单集合，缺失则空集合
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptySet())  // 转为热流 StateFlow，立即启动，初始值空集合

    /** 内存快照（非协程场景快速查询，service 事件处理用）。 */
    @Volatile  // 标记 volatile 保证多线程可见
    private var cache: Set<String> = emptySet()  // 内存缓存快照

    init {  // 初始化块
        // 订阅 Flow 同步缓存
        scope.launch {  // 启动协程
            whitelist.collect { cache = it }  // 订阅 StateFlow，最新值同步到内存缓存
        }
        // 首次启动时自动初始化系统应用白名单
        scope.launch { autoAddSystemApps() }  // 启动协程执行默认应用初始化
    }

    /**
     * 判断某包名是否在白名单内（O(1)，供无障碍服务高频调用）。
     *
     * @param packageName 待检测的包名
     * @return 在白名单内返回 true
     */
    fun isWhitelisted(packageName: String): Boolean = cache.contains(packageName)  // 直接查内存缓存

    /**
     * 添加到白名单。
     *
     * @param packageName 待添加的包名，空字符串忽略
     */
    suspend fun add(packageName: String) {  // 添加到白名单方法
        if (packageName.isEmpty()) return  // 包名为空直接返回
        context.dataStore.edit { prefs ->  // 编辑 DataStore
            val set = prefs[KEY_WHITELIST]?.toMutableSet() ?: mutableSetOf()  // 取现有集合或新建
            if (set.add(packageName)) prefs[KEY_WHITELIST] = set  // 添加成功则写回
        }
    }

    /**
     * 从白名单移除。
     *
     * @param packageName 待移除的包名
     */
    suspend fun remove(packageName: String) {  // 从白名单移除方法
        context.dataStore.edit { prefs ->  // 编辑 DataStore
            val set = prefs[KEY_WHITELIST]?.toMutableSet() ?: mutableSetOf()  // 取现有集合或新建
            if (set.remove(packageName)) prefs[KEY_WHITELIST] = set  // 移除成功则写回
        }
    }

    /**
     * 批量切换：加入或移除。
     *
     * @param packageName 目标包名
     * @param whitelist true 表示加入白名单，false 表示移除
     */
    suspend fun toggle(packageName: String, whitelist: Boolean) {  // 切换白名单状态方法
        if (whitelist) add(packageName) else remove(packageName)  // 按参数分发到 add/remove
    }

    /**
     * 清空白名单（恢复到仅系统应用）。
     *
     * 调用后 DataStore 中白名单键被移除，随后调用 [autoAddSystemApps] 重新填充默认应用。
     */
    suspend fun reset() {  // 清空白名单方法
        context.dataStore.edit { it.remove(KEY_WHITELIST) }  // 移除白名单键
        autoAddSystemApps()  // 重新初始化默认应用
    }

    /**
     * 自动将系统应用和关键默认应用加入白名单（仅首次或 reset 时）。
     *
     * 通过遍历 PackageManager 已安装列表筛选 FLAG_SYSTEM / FLAG_UPDATED_SYSTEM_APP
     * 或包名以 [DEFAULT_KEY_APPS] 任一前缀开头的关键应用。
     */
    private suspend fun autoAddSystemApps() {  // 自动初始化系统/关键应用白名单方法
        runCatching {  // 捕获异常
            val pm: PackageManager = context.packageManager  // 取 PackageManager
            val flags = PackageManager.GET_META_DATA  // 查询标志
            val raw = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {  // Android 13+ 分支
                pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(flags.toLong()))  // 新 API
            } else {  // 旧版本分支
                @Suppress("DEPRECATION")  // 抑制旧 API 弃用警告
                pm.getInstalledApplications(flags)  // 旧 API
            }
            val defaultPkgs = raw.filter { info ->  // 筛选默认应加入白名单的包名
                (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||  // 系统应用
                    (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||  // 更新过的系统应用
                    DEFAULT_KEY_APPS.any { info.packageName.startsWith(it) }  // 或包名以关键应用前缀开头
            }.map { it.packageName }.toSet()  // 取包名集合

            if (defaultPkgs.isNotEmpty()) {  // 找到默认应用
                context.dataStore.edit { prefs ->  // 编辑 DataStore
                    val existing = prefs[KEY_WHITELIST]?.toMutableSet() ?: mutableSetOf()  // 取现有集合或新建
                    val merged = (existing + defaultPkgs).toMutableSet()  // 合并现有与默认
                    prefs[KEY_WHITELIST] = merged  // 写回
                }
                Logger.i("已自动将 ${defaultPkgs.size} 个系统/关键应用加入白名单")  // 输出信息日志
            }
        }.onFailure { Logger.w("自动初始化白名单失败", it) }  // 异常输出警告
    }

    companion object {  // 静态常量
        /** DataStore 中白名单集合的键名。 */
        private val KEY_WHITELIST = stringSetPreferencesKey("whitelist_set")  // 白名单集合键

        /** 关键默认应用前缀（即使非 FLAG_SYSTEM 也加入白名单）。 */
        private val DEFAULT_KEY_APPS = listOf(  // 关键默认应用包名前缀列表
            "com.android.settings",      // 设置
            "com.android.contacts",      // 通讯录
            "com.android.dialer",        // 电话
            "com.android.mms",           // 短信
            "com.android.gallery",      // 相册
            "com.android.camera",        // 相机
            "com.android.browser",       // 浏览器
            "com.android.documentsui",   // 文件管理
            "com.android.desktopclock",  // 时钟
            "com.android.calendar",      // 日历
            "com.android.launcher",      // 启动器
            "com.android.systemui",      // 系统UI
            "com.google.android.apps.maps", // 地图
            "com.google.android.apps.messaging", // Google 短信
        )
    }
}
