package com.fundlistener.android

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fundlistener.client.RefreshScheduler
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.koin.core.context.GlobalContext
import org.koin.java.KoinJavaComponent.getKoin

/**
 * Foreground Service — Ktor 嵌入式服务器的 Android 宿主容器。
 *
 * S16确认方案:
 *   - onStartCommand 中 start(wait=false) 启动 Ktor，不阻塞调用线程
 *   - START_STICKY 确保被 kill 后自动重建
 *   - onDestroy 中 graceful shutdown
 * S21新增:
 *   - RefreshScheduler 交易时段 30s 定时刷新估值
 */
class FundServerService : Service() {

    private lateinit var server: ApplicationEngine
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var scheduler: RefreshScheduler? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Build foreground notification
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, FundApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(FundApplication.NOTIFICATION_ID, notification)

        // Start Ktor server (non-blocking)
        if (!::server.isInitialized) {
            server = embeddedServer(Netty, port = 8080) {
                configureServer()
            }
            server.start(wait = false)
        }

        // Start refresh scheduler
        if (scheduler == null) {
            val koin = GlobalContext.get()
            scheduler = RefreshScheduler(
                valuationService = koin.get(),
                fundRepository = koin.get(),
                fundService = koin.get()
            )
            scheduler!!.start(serviceScope)
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scheduler?.stop()
        serviceScope.cancel()
        if (::server.isInitialized) {
            server.stop(gracePeriodMillis = 1000, timeoutMillis = 3000)
        }
        super.onDestroy()
    }
}

fun Application.configureServer() {
    com.fundlistener.plugins.configureSerialization()
    com.fundlistener.plugins.configureRouting()
}
