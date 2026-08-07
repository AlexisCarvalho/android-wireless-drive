package dev.alexis.wirelessdrive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class BackgroundTaskService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val label = intent?.getStringExtra(EXTRA_LABEL) ?: DEFAULT_LABEL
        startForeground(NOTIFICATION_ID, buildNotification(label))
        return START_NOT_STICKY
    }

    private fun buildNotification(label: String): Notification {
        ensureChannel()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WirelessDrive")
            .setContentText(label)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Tarefas em segundo plano",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Downloads, uploads e geração de thumbnails em andamento"
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "background_tasks"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_LABEL = "label"
        private const val DEFAULT_LABEL = "Sincronizando arquivos..."

        fun start(context: Context, label: String = DEFAULT_LABEL) {
            val intent = Intent(context, BackgroundTaskService::class.java)
                .putExtra(EXTRA_LABEL, label)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BackgroundTaskService::class.java))
        }
    }
}