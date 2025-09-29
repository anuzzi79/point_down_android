package com.pointdown.app.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.pointdown.app.MainActivity
import com.pointdown.app.R
import com.pointdown.app.data.Prefs

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val prefs = Prefs(context)
        val (h,m) = prefs.getHourMinute()
        AlarmScheduler.scheduleDaily(context, h, m)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "pointdown_daily"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId,
                context.getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
            ch.description = context.getString(R.string.notif_channel_desc)
            nm.createNotificationChannel(ch)
        }

        // Intent che apre MainActivity e forza il caricamento delle card Jira
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("from_notification", true)
        }

        val contentPI = PendingIntent.getActivity(
            context, 201, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder) // sicura in tutti i device
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_text))
            .setContentIntent(contentPI)
            .setAutoCancel(true)
            .build()




        nm.notify(1001, notif)
    }
}
