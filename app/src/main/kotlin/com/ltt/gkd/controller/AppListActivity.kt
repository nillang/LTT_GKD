package com.ltt.gkd.controller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ltt.gkd.model.app.AppListRepository
import com.ltt.gkd.view.app.AppListScreen

class AppListActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = AppListRepository(this)
        setContent { AppListScreen(repo = repo) }
    }
}
