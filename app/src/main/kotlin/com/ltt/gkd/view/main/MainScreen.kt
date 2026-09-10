package com.ltt.gkd.view.main

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltt.gkd.R
import kotlinx.coroutines.flow.Flow

/**
 * 主界面 Composable。
 *
 * View 层不直接处理业务逻辑：
 * - 服务状态、累计跳过数通过 [Flow] 由 Controller 注入
 * - 跨 Activity 跳转、跳系统设置都通过 lambda 回调由 Controller 决定
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    serviceOn: Boolean,
    totalSkipFlow: Flow<Int>,
    onOpenService: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetTotal: () -> Unit
) {
    val total by totalSkipFlow.collectAsState(initial = 0)

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusCard(serviceOn = serviceOn)
                Spacer(Modifier.height(16.dp))
                StatCard(total = total, onReset = onResetTotal)
                Spacer(Modifier.height(24.dp))
                ActionRow(
                    onOpenService = onOpenService,
                    onOpenRules = onOpenRules,
                    onOpenApps = onOpenApps,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun StatusCard(serviceOn: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (serviceOn) Color(0xFFE8F5E9) else Color(0xFFFFF3E0)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(
                        if (serviceOn) Color(0xFF4CAF50) else Color(0xFFFF9800),
                        CircleShape
                    )
            )
            Spacer(Modifier.size(12.dp))
            Text(
                if (serviceOn) stringResource(R.string.status_service_on)
                else stringResource(R.string.status_service_off),
                fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun StatCard(total: Int, onReset: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.stat_total_count, total),
                fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onReset) {
                Text(stringResource(R.string.stat_reset))
            }
        }
    }
}

@Composable
private fun ActionRow(
    onOpenService: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onOpenService, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_open_accessibility))
        }
        OutlinedButton(onClick = onOpenRules, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.List, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_open_rules))
        }
        OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Apps, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_open_apps))
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_open_settings))
        }
    }
}
