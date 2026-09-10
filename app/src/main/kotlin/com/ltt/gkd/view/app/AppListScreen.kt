package com.ltt.gkd.view.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltt.gkd.R
import com.ltt.gkd.model.app.AppInfo
import com.ltt.gkd.model.app.AppListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable

/**
 * 应用列表界面。
 *
 * - 顶部搜索框（按应用名/包名过滤）
 * - 列表展示应用图标、应用名、包名、版本号
 * - 点击行：复制包名到剪贴板（用于规则编辑）
 *
 * 首次进入时异步加载列表，加载中显示 ProgressBar。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(repo: AppListRepository) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var keyword by remember { mutableStateOf("") }
    val allApps = remember { MutableStateFlow<List<AppInfo>>(emptyList()) }
    val apps by allApps.asStateFlow().collectAsState()
    var loading by remember { mutableStateOf(true) }

    // 首次加载全部应用
    LaunchedEffect(Unit) {
        loading = true
        val data = withContext(Dispatchers.IO) { repo.listAll() }
        allApps.value = data
        loading = false
    }

    // 关键字变化时重新搜索（去抖动由用户输入频率自然限制）
    LaunchedEffect(keyword) {
        if (keyword.isBlank()) {
            // 已加载全部，无需重复请求
            if (allApps.value.isEmpty()) {
                val data = withContext(Dispatchers.IO) { repo.listAll() }
                allApps.value = data
            }
        } else {
            val data = withContext(Dispatchers.IO) { repo.search(keyword) }
            allApps.value = data
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_app_list)) }) }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                SearchBox(
                    keyword = keyword,
                    onKeywordChange = { keyword = it },
                    onClear = { keyword = "" }
                )
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "加载中...",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else if (apps.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (keyword.isEmpty()) "未发现任何应用"
                            else "无匹配结果",
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item {
                            Text(
                                "共 ${apps.size} 个应用",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        items(apps, key = { it.packageName }) { info ->
                            AppItemRow(
                                info = info,
                                repo = repo,
                                onClick = {
                                    copyToClipboard(ctx, info.packageName)
                                    Toast.makeText(
                                        ctx,
                                        "已复制包名: ${info.packageName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBox(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("按应用名或包名搜索") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "清空")
                }
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Search
        ),
        singleLine = true
    )
}

@Composable
private fun AppItemRow(
    info: AppInfo,
    repo: AppListRepository,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val ctx = LocalContext.current
        val iconBitmap = remember(info.packageName) {
            loadIconBitmap(repo, info.packageName, 48)
        }
        if (iconBitmap != null) {
            androidx.compose.foundation.Image(
                bitmap = iconBitmap.asImageBitmap(),
                contentDescription = info.label,
                modifier = Modifier.size(40.dp)
            )
        } else {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                info.label,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                info.packageName,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                buildString {
                    append("v${info.versionName ?: "?"}")
                    if (info.isSystem) append(" · 系统")
                    if (!info.isEnabled) append(" · 已停用")
                },
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "复制包名")
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("package_name", text))
}

/** 把 Drawable 转为 Bitmap 供 Compose 显示。 */
private fun loadIconBitmap(repo: AppListRepository, pkg: String, sizePx: Int): Bitmap? {
    val drawable: Drawable = repo.loadIcon(pkg) ?: return null
    return when (drawable) {
        is BitmapDrawable -> drawable.bitmap.takeIf { !it.isRecycled }
        else -> {
            // 矢量 Drawable 等：用 Canvas 绘制到 Bitmap
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(canvas)
            bmp
        }
    }
}
