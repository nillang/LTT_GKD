package com.ltt.gkd

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ltt.gkd.model.prefs.SettingsStore
import com.ltt.gkd.model.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class App : Application() {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        Logger.init(this)
        registerNotificationChannels()
    }

    private fun registerNotificationChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    getString(R.string.notification_channel_service),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_SKIP_EVENT,
                    getString(R.string.notification_channel_skip_event),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
    }

    companion object {
        const val CHANNEL_SERVICE = "service"
        const val CHANNEL_SKIP_EVENT = "skip_event"

        @Volatile
        private var instance: App? = null

        fun get(): App = instance ?: error("App not yet created")
    }
}
