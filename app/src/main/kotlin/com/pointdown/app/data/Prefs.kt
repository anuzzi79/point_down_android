package com.pointdown.app.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pd_prefs", Context.MODE_PRIVATE)

    var baseUrl: String?
        // ✅ Default pre-popolato con il tuo tenant Jira
        get() = sp.getString("baseUrl", "https://facilitygrid.atlassian.net")
        set(v) = sp.edit().putString("baseUrl", v).apply()

    var email: String?
        get() = sp.getString("email", null)
        set(v) = sp.edit().putString("email", v).apply()

    var token: String?
        get() = sp.getString("token", null)
        set(v) = sp.edit().putString("token", v).apply()

    var jql: String?
        get() = sp.getString("jql", null)
        set(v) = sp.edit().putString("jql", v).apply()

    var alarmTime: String?
        get() = sp.getString("alarmTime", "17:50")
        set(v) = sp.edit().putString("alarmTime", v).apply()

    // ✅ Preferenze per la “card di test”
    var forceTestCard: Boolean
        get() = sp.getBoolean("forceTestCard", true)
        set(v) = sp.edit().putBoolean("forceTestCard", v).apply()

    var testIssueKey: String?
        get() = sp.getString("testIssueKey", "FGC-9683")
        set(v) = sp.edit().putString("testIssueKey", v).apply()

    // ✅ Abilita/disabilita il lock cooperativo (default: ON)
    var enableQueueLock: Boolean
        get() = sp.getBoolean("enableQueueLock", true)
        set(v) = sp.edit().putBoolean("enableQueueLock", v).apply()

    fun getHourMinute(): Pair<Int, Int> {
        val t = alarmTime ?: "17:50"
        val m = Regex("(\\d{1,2}):(\\d{2})").find(t)

        return if (m != null) {
            val h = m.groupValues[1].toInt().coerceIn(0,23)
            val min = m.groupValues[2].toInt().coerceIn(0,59)
            h to min
        } else 17 to 50
    }

    fun isConfigured(): Boolean =
        !baseUrl.isNullOrBlank() && !email.isNullOrBlank() && !token.isNullOrBlank()
}
