package com.rk.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.rk.resources.drawables
import com.rk.terminal.ui.activities.terminal.MainActivity

/**
 * Keeps the file server and hosted processes alive while the app is in the
 * background. Started from the server panel and stopped when nothing is
 * running.
 */
class HostService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) {
            FileServer.stop()
            HostedProcessManager.stopAll()
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        running = false
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, HostService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = buildString {
            if (FileServer.running) append("Файловый сервер включён")
            val active = HostedProcessManager.runtimes.values.count { it.running }
            if (active > 0) {
                if (isNotEmpty()) append(" · ")
                append("процессов: $active")
            }
            if (isEmpty()) append("Хостинг работает в фоне")
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MakTerm · хостинг")
            .setContentText(text)
            .setSmallIcon(drawables.terminal)
            .setContentIntent(openIntent)
            .addAction(0, "Остановить всё", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MakTerm hosting",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "File server and hosted processes"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "makterm_host_service"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP_ALL = "com.rk.server.STOP_ALL"

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            if (running) return
            running = true
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, HostService::class.java)
                )
            }.onFailure {
                // Allow a later retry if the system refused to start the
                // service (for example while the app is in the background).
                running = false
            }
        }

        fun stop(context: Context) {
            running = false
            runCatching { context.stopService(Intent(context, HostService::class.java)) }
        }

        fun refresh(context: Context) {
            if (!running) return
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager.notify(NOTIFICATION_ID, buildSimpleNotification(context))
            }
        }

        private fun buildSimpleNotification(context: Context): Notification {
            val openIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val active = HostedProcessManager.runtimes.values.count { it.running }
            val text = buildString {
                if (FileServer.running) append("Файловый сервер включён")
                if (active > 0) {
                    if (isNotEmpty()) append(" · ")
                    append("процессов: $active")
                }
                if (isEmpty()) append("Хостинг работает в фоне")
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle("MakTerm · хостинг")
                .setContentText(text)
                .setSmallIcon(drawables.terminal)
                .setContentIntent(openIntent)
                .setOngoing(true)
                .build()
        }
    }
}
