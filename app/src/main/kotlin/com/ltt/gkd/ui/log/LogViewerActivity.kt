package com.ltt.gkd.ui.log // 声明包名

import android.os.Bundle // 导入 Bundle
import androidx.activity.ComponentActivity // 导入 ComponentActivity
import androidx.activity.compose.setContent // 导入 setContent
import com.ltt.gkd.ui.log.LogViewerScreen // 导入日志查看 Composable
import com.ltt.gkd.ui.theme.LTTGKDTheme // 导入应用主题

/**
 * 日志查看 Activity。
 *
 * 在 Compose 中挂载 [LogViewerScreen]，用于展示运行期间输出的日志内容。
 */
class LogViewerActivity : ComponentActivity() { // 日志查看 Activity
    /**
     * 初始化界面并加载日志查看页。
     */
    override fun onCreate(savedInstanceState: Bundle?) { // 创建回调
        super.onCreate(savedInstanceState) // 调用父类
        setContent { LTTGKDTheme { LogViewerScreen() } } // 挂载 Compose
    }
}
