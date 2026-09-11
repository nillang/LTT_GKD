package com.ltt.gkd.ui.app // 声明包名

import android.os.Bundle // 导入 Bundle
import androidx.activity.ComponentActivity // 导入 ComponentActivity
import androidx.activity.compose.setContent // 导入 setContent
import com.ltt.gkd.App // 导入应用入口类
import com.ltt.gkd.data.app.AppListRepository // 导入应用列表仓库
import com.ltt.gkd.ui.app.AppListScreen // 导入应用列表 Composable
import com.ltt.gkd.ui.theme.LTTGKDTheme // 导入应用主题

/**
 * 应用列表（白名单管理）Activity。
 *
 * 负责初始化 [AppListRepository] 与全局 [com.ltt.gkd.data.app.WhitelistStore]，
 * 并在 Compose 中承载 [AppListScreen] 界面。
 */
class AppListActivity : ComponentActivity() { // 应用列表 Activity

    override fun onCreate(savedInstanceState: Bundle?) { // 创建回调
        super.onCreate(savedInstanceState) // 调用父类
        // 初始化应用列表数据仓库
        val repo = AppListRepository(this) // 创建仓库
        // 取全局单例白名单存储
        val whitelist = App.get().whitelist // 取全局白名单
        setContent { LTTGKDTheme { AppListScreen(repo = repo, whitelist = whitelist) } } // 挂载 Compose
    }
}
