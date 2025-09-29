package com.pointdown.app.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.*

object AlarmScheduler {

    fun scheduleDaily(context: Context, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, NotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (before(Calendar.getInstance())) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }
}
