package com.ltt.gkd.data.app  // 声明包名，应用列表仓库所在的包

import android.content.Context  // 导入 Context 类，用于访问应用上下文
import android.content.pm.ApplicationInfo  // 导入 ApplicationInfo 类，应用信息
import android.content.pm.PackageInfo  // 导入 PackageInfo 类，包信息（含版本号）
import android.content.pm.PackageManager  // 导入 PackageManager 类，用于查询应用
import android.graphics.drawable.Drawable  // 导入 Drawable 类，用于应用图标
import com.ltt.gkd.util.Logger  // 导入日志工具类
import kotlinx.coroutines.Dispatchers  // 导入协程调度器
import kotlinx.coroutines.withContext  // 导入 withContext，切换协程上下文

/**
 * 应用列表仓库。
 *
 * 提供查询设备已安装应用的能力，供"应用列表"界面与"按包名生成规则"使用。
 *
 * 权限：从 Android 11（API 30）起，[PackageManager.getInstalledApplications]
 * 默认仅返回与查询应用可见性的应用；本应用无需申请 QUERY_ALL_PACKAGES
 * 敏感权限，普通应用列表展示足够。
 *
 * 若需要更广覆盖（含所有系统应用），可在 Manifest 声明
 * `<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES" />`，
 * 但该权限会触发 Google Play 审查；自用版本可选。
 */
class AppListRepository(private val context: Context) {  // 应用列表仓库类

    private val pm: PackageManager  // PackageManager 懒加载属性
        get() = context.packageManager  // 从 Context 取 PackageManager

    /** 应用列表缓存（按 label 排序）。首次访问时填充，避免每次搜索都重新调用 PM。 */
    @Volatile  // 标记为 volatile 保证多线程可见性
    private var cache: List<AppInfo>? = null  // 应用列表缓存，空表示未加载

    /**
     * 加载所有可见的应用（含系统），首次调用后命中缓存。
     *
     * @return 按 isSystem 升序、label 字母序排列的应用列表；失败返回空列表
     */
    suspend fun listAll(): List<AppInfo> = withContext(Dispatchers.IO) {  // 加载全部应用方法，运行在 IO 线程
        cache?.let { return@withContext it }  // 命中缓存直接返回
        runCatching {  // 捕获异常
            val flags = PackageManager.GET_META_DATA  // 查询标志：带元数据
            // API 33+ 接口签名变化；统一兼容调用
            val raw = invokeGetInstalledApplications(pm, flags)  // 兼容调用获取已安装应用列表
            val list = raw.map { info -> toAppInfo(info) }  // 转换为 AppInfo 列表
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))  // 先按是否系统应用再按应用名小写排序
            cache = list  // 写入缓存
            list  // 返回列表
        }.getOrElse {  // 异常分支
            Logger.w("读取应用列表失败", it)  // 输出警告日志
            emptyList()  // 返回空列表
        }
    }

    /** 仅加载用户安装的应用（非系统应用）。 */
    suspend fun listUserApps(): List<AppInfo> = listAll().filter { !it.isSystem }  // 从全部应用过滤出非系统应用

    /**
     * 按关键字搜索（应用名或包名），基于缓存过滤，无需重新调 PM。
     *
     * @param keyword 搜索关键字（大小写不敏感），空字符串返回全部
     * @return 匹配 label 或 packageName 的应用列表
     */
    suspend fun search(keyword: String): List<AppInfo> = withContext(Dispatchers.IO) {  // 关键字搜索方法，运行在 IO 线程
        val all = listAll() // 命中缓存时是内存操作  // 取全部应用（命中缓存则内存操作）
        if (keyword.isBlank()) return@withContext all  // 关键字空白返回全部
        val lower = keyword.lowercase()  // 关键字转小写
        all.filter {  // 过滤匹配项
            it.label.lowercase().contains(lower) ||  // 应用名小写包含关键字
                it.packageName.lowercase().contains(lower)  // 或包名小写包含关键字
        }
    }

    /** 强制刷新缓存（下拉刷新调用）。 */
    suspend fun refresh(): List<AppInfo> = withContext(Dispatchers.IO) {  // 刷新缓存方法，运行在 IO 线程
        cache = null  // 清空缓存
        listAll()  // 重新加载
    }

    /**
     * 按包名加载单条记录。
     *
     * @param packageName 目标应用包名
     * @return 应用信息；包名不存在或异常返回 null
     */
    suspend fun get(packageName: String): AppInfo? = withContext(Dispatchers.IO) {  // 按包名加载单条应用，运行在 IO 线程
        runCatching {  // 捕获异常
            val info = pm.getApplicationInfo(packageName, 0)  // 查询 ApplicationInfo
            toAppInfo(info)  // 转换为 AppInfo
        }.getOrNull()  // 失败返回 null
    }

    /**
     * 按包名加载图标。
     *
     * @param packageName 目标应用包名
     * @return 应用图标 Drawable；加载失败返回 null
     */
    fun loadIcon(packageName: String): Drawable? = runCatching {  // 加载应用图标方法
        pm.getApplicationIcon(packageName)  // 从 PackageManager 取图标
    }.getOrNull()  // 失败返回 null

    /**
     * 仅查询应用名（轻量调用，用于规则列表显示 App 名）。
     *
     * @param packageName 目标应用包名
     * @return 应用显示名；查询失败返回 null
     */
    fun getLabel(packageName: String): String? = runCatching {  // 查询应用名方法
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()  // 取应用标签并转字符串
    }.getOrNull()  // 失败返回 null

    // ---------- 内部辅助 ----------

    /**
     * 将 ApplicationInfo 转换为 UI 友好的 [AppInfo]。
     *
     * @param info 系统返回的 ApplicationInfo
     * @return 转换后的轻量应用信息（含版本号、是否系统应用等）
     */
    private fun toAppInfo(info: ApplicationInfo): AppInfo {  // 转换为 AppInfo 方法
        val pkgInfo: PackageInfo? = runCatching {  // 捕获查询 PackageInfo 异常
            // 用已加载的 ApplicationInfo 反查 PackageInfo 拿版本号
            invokeGetPackageInfo(pm, info.packageName, 0)  // 兼容调用获取 PackageInfo
        }.getOrNull()  // 失败返回 null
        return AppInfo(  // 构造 AppInfo
            packageName = info.packageName,  // 包名
            label = info.loadLabel(pm).toString(),  // 应用显示名
            versionName = pkgInfo?.versionName,  // 版本名字符串
            versionCode = pkgInfo?.longVersionCode ?: 0L,  // 版本号，缺失为 0
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,  // 是否系统应用
            isEnabled = info.enabled,  // 是否启用
            sourceDir = info.sourceDir  // APK 路径
        )
    }

    /**
     * Android 13+ 推荐使用 getInstalledApplications(PackageManager.PackageInfoFlags) 静态内部类
     *
     * @param pm PackageManager 实例
     * @param flags 查询标志（如 GET_META_DATA）
     * @return 已安装应用列表
     */
    @Suppress("DEPRECATION")  // 抑制旧 API 弃用警告
    private fun invokeGetInstalledApplications(pm: PackageManager, flags: Int): List<ApplicationInfo> {  // 兼容调用获取已安装应用
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {  // Android 13+ 分支
            pm.getInstalledApplications(  // 新 API
                PackageManager.ApplicationInfoFlags.of(flags.toLong())  // 使用 ApplicationInfoFlags
            )
        } else {  // 旧版本分支
            pm.getInstalledApplications(flags)  // 旧 API（已弃用）
        }
    }

    /**
     * 兼容 Android 13+ 的 getPackageInfo 调用（使用 PackageInfoFlags 静态内部类）。
     *
     * @param pm PackageManager 实例
     * @param packageName 目标应用包名
     * @param flags 查询标志
     * @return PackageInfo；包名不存在会抛出 NameNotFoundException
     */
    @Suppress("DEPRECATION")  // 抑制旧 API 弃用警告
    private fun invokeGetPackageInfo(  // 兼容调用获取 PackageInfo
        pm: PackageManager,  // PackageManager 实例
        packageName: String,  // 目标包名
        flags: Int  // 查询标志
    ): PackageInfo {  // 返回 PackageInfo
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {  // Android 13+ 分支
            pm.getPackageInfo(  // 新 API
                packageName,  // 包名
                PackageManager.PackageInfoFlags.of(flags.toLong())  // 使用 PackageInfoFlags
            )
        } else {  // 旧版本分支
            pm.getPackageInfo(packageName, flags)  // 旧 API（已弃用）
        }
    }
}
