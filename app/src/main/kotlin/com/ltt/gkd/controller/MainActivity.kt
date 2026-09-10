package com.ltt.gkd.controller

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ltt.gkd.App
import com.ltt.gkd.controller.service.SkipAccessibilityService
import com.ltt.gkd.view.main.MainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Controller 负责观察服务状态
            var serviceOn by remember {
                mutableStateOf(SkipAccessibilityService.instance != null)
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, e ->
                    if (e == Lifecycle.Event.ON_RESUME) {
                        serviceOn = SkipAccessibilityService.instance != null
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            val app = App.get()
            MainScreen(
                serviceOn = serviceOn,
                totalSkipFlow = app.settings.totalSkipCount,
                onOpenService = {
                    if (!serviceOn) {
                        startActivity(
                            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                },
                onOpenRules = {
                    startActivity(Intent(this, RuleListActivity::class.java))
                },
                onOpenApps = {
                    startActivity(Intent(this, AppListActivity::class.java))
                },
                onOpenSettings = {
                    startActivity(Intent(this, SettingsActivity::class.java))
                },
                onResetTotal = {
                    app.appScope.launch { app.settings.resetTotalSkip() }
                }
            )
        }
    }
}
