package com.fundlistener.android

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.fundlistener.android.data.DataMigration
import com.fundlistener.android.di.androidAppModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.context.GlobalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FundApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fund_server_channel"
        const val NOTIFICATION_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

        // Notification channel — required for Foreground Service
        createNotificationChannel()

        // Start Koin DI with Android module
        startKoin {
            androidLogger()
            androidContext(this@FundApplication)
            modules(androidAppModule)
        }

        // Run data migration from old JVM SQLite DB
        CoroutineScope(Dispatchers.IO).launch {
            val migration = DataMigration(this@FundApplication, getKoin().get())
            migration.migrateIfNeeded()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "基金数据后台服务"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }
}

private fun getKoin() = GlobalContext.get()
