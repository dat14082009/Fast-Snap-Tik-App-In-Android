package com.stone.tiktoksave.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.stone.tiktoksave.R
import java.util.UUID

object Notifications {
    private const val CHANNEL_PROGRESS = "download_progress"
    private const val CHANNEL_RESULT = "download_result"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Tiến trình tải xuống", NotificationManager.IMPORTANCE_LOW)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULT, "Kết quả tải xuống", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    /** Thông báo tiến trình của foreground service; có nút Hủy. percent = null: chưa biết. */
    fun foregroundInfo(
        context: Context,
        workId: UUID,
        notificationId: Int,
        text: String,
        percent: Int?
    ): ForegroundInfo {
        val cancel = WorkManager.getInstance(context).createCancelPendingIntent(workId)
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Đang tải video")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(context))
            .addAction(0, "Hủy", cancel)
        if (percent != null && percent >= 0) {
            builder.setProgress(100, percent, false)
        } else {
            builder.setProgress(0, 0, true)
        }
        return ForegroundInfo(notificationId, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    /** Bấm thông báo: mở tệp vừa tải. Nút "Mở app": mở giao diện chính. */
    fun notifyDone(context: Context, id: Int, title: String, uri: Uri, mime: String) {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val pending = PendingIntent.getActivity(
            context, id, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Đã tải xong")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(0, "Mở app", openAppIntent(context))
            .build()
        post(context, id, notification)
    }

    fun notifyFailed(context: Context, id: Int, message: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Tải xuống thất bại")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context))
            .build()
        post(context, id, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        return PendingIntent.getActivity(
            context, 0, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // Chưa được cấp quyền thông báo: bỏ qua, việc tải vẫn hoàn tất.
        }
    }
}
