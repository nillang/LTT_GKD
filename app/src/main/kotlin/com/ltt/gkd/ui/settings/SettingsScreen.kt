package com.ltt.gkd.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ltt.gkd.R
import com.ltt.gkd.data.prefs.SettingsStore
import com.ltt.gkd.util.Logger
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsStore,
    onOpenLogs: () -> Unit
) {
    val scope = rememberCoroutineScope()

    val log by settings.logEnabled.collectAsState(initial = false)
    val ocr by settings.ocrEnabled.collectAsState(initial = true)
    val sub by settings.subscriptionEnabled.collectAsState(initial = false)
    val subUrl by settings.subscriptionUrl.collectAsState(initial = "")
    val subInterval by settings.subscriptionIntervalHours.collectAsState(initial = 24)
    val skipNoti by settings.skipNotificationEnabled.collectAsState(initial = false)
    val ghToken by settings.githubToken.collectAsState(initial = "")
    val gistId by settings.gistId.collectAsState(initial = "")
    val deviceId by settings.deviceId.collectAsState(initial = "")

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.title_settings)) }) }
    ) { inner ->
        Surface(modifier = Modifier.padding(inner).fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SectionTitle("基础")
                ToggleRow(
                    title = stringResource(R.string.settings_ocr_title),
                    summary = stringResource(R.string.settings_ocr_summary),
                    checked = ocr,
                    onChange = { v -> scope.launch { settings.setOcr(v) } }
                )
                ToggleRow(
                    title = stringResource(R.string.settings_log_title),
                    summary = stringResource(R.string.settings_log_summary),
                    checked = log,
                    onChange = { v ->
                        scope.launch {
                            settings.setLog(v)
                            Logger.refresh(v)
                        }
                    }
                )
                Card(
                    Modifier.fillMaxWidth().clickable(onClick = onOpenLogs)
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Article, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("查看日志", Modifier.padding(start = 12.dp))
                    }
                }
                ToggleRow(
                    title = stringResource(R.string.settings_notification_title),
                    summary = stringResource(R.string.settings_notification_summary),
                    checked = skipNoti,
                    onChange = { v -> scope.launch { settings.setSkipNotification(v) } }
                )

                SectionTitle("共享规则（GitHub Gist）")
                if (deviceId.isNotEmpty()) {
                    Text(
                        "设备 ID：$deviceId",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                OutlinedTextField(
                    value = ghToken,
                    onValueChange = { v -> scope.launch { settings.setGithubToken(v) } },
                    label = { Text("Personal Access Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = gistId,
                    onValueChange = { v -> scope.launch { settings.setGistId(v) } },
                    label = { Text("Gist ID（首次留空，上传后自动填）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "Token 需 gist 权限，仅存本地不上传；Gist 设为私有",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline
                )

                SectionTitle("订阅")
                ToggleRow(
                    title = stringResource(R.string.settings_subscription_title),
                    summary = stringResource(R.string.settings_subscription_summary),
                    checked = sub,
                    onChange = { v -> scope.launch { settings.setSubscription(v) } }
                )
                if (sub) {
                    OutlinedTextField(
                        value = subUrl,
                        onValueChange = { v -> scope.launch { settings.setSubscriptionUrl(v) } },
                        label = { Text(stringResource(R.string.settings_subscription_url)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = subInterval.toString(),
                        onValueChange = { v ->
                            val n = v.toIntOrNull()
                            if (n != null && n > 0) {
                                scope.launch { settings.setSubscriptionInterval(n) }
                            }
                        },
                        label = { Text(stringResource(R.string.settings_subscription_interval)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 15.sp)
                Text(
                    summary,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
