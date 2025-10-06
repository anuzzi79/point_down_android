package com.pointdown.app.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pointdown.app.data.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == "android.intent.action.BOOT_COMPLETED") {
            val prefs = Prefs(context)
            val (h, m) = prefs.getHourMinute()
            AlarmScheduler.scheduleDaily(context, h, m, prefs.enableWeekendNotifications)
        }
    }
}
