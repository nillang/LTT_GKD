package com.ltt.gkd.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ltt.gkd.App
import com.ltt.gkd.ui.settings.SettingsScreen

class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SettingsScreen(
                settings = App.get().settings,
                onOpenLogs = { startActivity(android.content.Intent(this, LogViewerActivity::class.java)) }
            )
        }
    }
}
