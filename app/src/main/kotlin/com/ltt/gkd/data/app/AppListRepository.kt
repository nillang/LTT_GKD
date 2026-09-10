package com.ltt.gkd.data.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.ltt.gkd.util.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
class AppListRepository(private val context: Context) {

    private val pm: PackageManager
        get() = context.packageManager

    /** 应用列表缓存（按 label 排序）。首次访问时填充，避免每次搜索都重新调用 PM。 */
    @Volatile
    private var cache: List<AppInfo>? = null

    /** 加载所有可见的应用（含系统），首次调用后命中缓存。 */
    suspend fun listAll(): List<AppInfo> = withContext(Dispatchers.IO) {
        cache?.let { return@withContext it }
        runCatching {
            val flags = PackageManager.GET_META_DATA
            // API 33+ 接口签名变化；统一兼容调用
            val raw = invokeGetInstalledApplications(pm, flags)
            val list = raw.map { info -> toAppInfo(info) }
                .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            cache = list
            list
        }.getOrElse {
            Logger.w("读取应用列表失败", it)
            emptyList()
        }
    }

    /** 仅加载用户安装的应用（非系统应用）。 */
    suspend fun listUserApps(): List<AppInfo> = listAll().filter { !it.isSystem }

    /** 按关键字搜索（应用名或包名），基于缓存过滤，无需重新调 PM。 */
    suspend fun search(keyword: String): List<AppInfo> = withContext(Dispatchers.IO) {
        val all = listAll() // 命中缓存时是内存操作
        if (keyword.isBlank()) return@withContext all
        val lower = keyword.lowercase()
        all.filter {
            it.label.lowercase().contains(lower) ||
                it.packageName.lowercase().contains(lower)
        }
    }

    /** 强制刷新缓存（下拉刷新调用）。 */
    suspend fun refresh(): List<AppInfo> = withContext(Dispatchers.IO) {
        cache = null
        listAll()
    }

    /** 按包名加载单条记录。 */
    suspend fun get(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val info = pm.getApplicationInfo(packageName, 0)
            toAppInfo(info)
        }.getOrNull()
    }

    /** 按包名加载图标。 */
    fun loadIcon(packageName: String): Drawable? = runCatching {
        pm.getApplicationIcon(packageName)
    }.getOrNull()

    /** 仅查询应用名（轻量调用，用于规则列表显示 App 名）。 */
    fun getLabel(packageName: String): String? = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrNull()

    // ---------- 内部辅助 ----------

    private fun toAppInfo(info: ApplicationInfo): AppInfo {
        val pkgInfo: PackageInfo? = runCatching {
            // 用已加载的 ApplicationInfo 反查 PackageInfo 拿版本号
            invokeGetPackageInfo(pm, info.packageName, 0)
        }.getOrNull()
        return AppInfo(
            packageName = info.packageName,
            label = info.loadLabel(pm).toString(),
            versionName = pkgInfo?.versionName,
            versionCode = pkgInfo?.longVersionCode ?: 0L,
            isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            isEnabled = info.enabled,
            sourceDir = info.sourceDir
        )
    }

    /** Android 13+ 推荐使用 getInstalledApplications(PackageManager.PackageInfoFlags) 静态内部类 */
    @Suppress("DEPRECATION")
    private fun invokeGetInstalledApplications(pm: PackageManager, flags: Int): List<ApplicationInfo> {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(flags.toLong())
            )
        } else {
            pm.getInstalledApplications(flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun invokeGetPackageInfo(
        pm: PackageManager,
        packageName: String,
        flags: Int
    ): PackageInfo {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            pm.getPackageInfo(packageName, flags)
        }
    }
}
