package com.ltt.gkd.view.log

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.ltt.gkd.model.util.Logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen() {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(Logger.snapshot()) }
    var files by remember { mutableStateOf(Logger.listLogFiles(ctx)) }

    LaunchedEffect(Unit) {
        // 进入后立即刷新一次
        entries = Logger.snapshot()
        files = Logger.listLogFiles(ctx)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("日志查看") }) }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Text(
                    "实时缓冲（最近 1000 条）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(entries) { entry ->
                        val ts = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(Date(entry.timestamp))
                        Text(
                            "$ts ${entry.level.tag} ${entry.message}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = when (entry.level) {
                                Logger.Level.E -> MaterialTheme.colorScheme.error
                                Logger.Level.W -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
                Text(
                    "历史文件（${files.size} 个，按天滚动）",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 12.dp)
                )
                files.forEach { f ->
                    Text(
                        "${f.name}  (${f.length() / 1024}KB)",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            entries = Logger.snapshot()
                            files = Logger.listLogFiles(ctx)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("刷新") }
                    Button(
                        onClick = { shareLatest(ctx, files) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.IosShare, contentDescription = null)
                        Text("导出最新")
                    }
                }
            }
        }
    }
}

/** 分享最新的日志文件（通过 FileProvider）。 */
private fun shareLatest(ctx: android.content.Context, files: List<File>) {
    val latest = files.lastOrNull() ?: return
    runCatching {
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.fileprovider",
            latest
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享日志 ${latest.name}"))
    }
}
