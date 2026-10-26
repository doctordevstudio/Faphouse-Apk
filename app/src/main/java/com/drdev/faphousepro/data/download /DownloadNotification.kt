package com.drdev.faphousepro.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.drdev.faphousepro.ui.download.WatchDownloadActivity

object DownloadNotification {

    const val CHANNEL_ID = "fh_downloads"
    private const val CHANNEL_NAME = "Downloads"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "File download progress"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    fun buildProgress(
        ctx: Context,
        title: String,
        progress: Int,
        indeterminate: Boolean,
        contentText: String
    ): Notification {
        ensureChannel(ctx)
        val intent = Intent(ctx, WatchDownloadActivity::class.java).apply {
            putExtra(WatchDownloadActivity.EXTRA_MODE, WatchDownloadActivity.MODE_DOWNLOADS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(pi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun buildDone(ctx: Context, title: String): Notification {
        ensureChannel(ctx)
        val intent = Intent(ctx, WatchDownloadActivity::class.java).apply {
            putExtra(WatchDownloadActivity.EXTRA_MODE, WatchDownloadActivity.MODE_DOWNLOADS)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText("Download complete")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun notifyDone(ctx: Context, uniqueId: Int, title: String) {
        ensureChannel(ctx)
        val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
        mgr.notify(uniqueId, buildDone(ctx, title))
    }
}