package com.pointdown.app.data

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("pd_prefs", Context.MODE_PRIVATE)

    var baseUrl: String?
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

    // ✅ Lock cooperativo (default ON)
    var enableQueueLock: Boolean
        get() = sp.getBoolean("enableQueueLock", true)
        set(v) = sp.edit().putBoolean("enableQueueLock", v).apply()

    // ✅ Nuova preferenza: notifiche anche nel weekend (default OFF: si abilita dalla sezione Avançadas)
    var enableWeekendNotifications: Boolean
        get() = sp.getBoolean("enableWeekendNotifications", false)
        set(v) = sp.edit().putBoolean("enableWeekendNotifications", v).apply()

    // ============================================
    // ✅ NUOVO: Filtri di Status configurabili (parità con estensione)
    // Default: To Do=false, In Progress=true, Blocked=true, Need Reqs=true, Done=false
    // ============================================
    var stToDo: Boolean
        get() = sp.getBoolean("stToDo", false)
        set(v) = sp.edit().putBoolean("stToDo", v).apply()

    var stInProgress: Boolean
        get() = sp.getBoolean("stInProgress", true)
        set(v) = sp.edit().putBoolean("stInProgress", v).apply()

    var stBlocked: Boolean
        get() = sp.getBoolean("stBlocked", true)
        set(v) = sp.edit().putBoolean("stBlocked", v).apply()

    var stNeedReqs: Boolean
        get() = sp.getBoolean("stNeedReqs", true)
        set(v) = sp.edit().putBoolean("stNeedReqs", v).apply()

    var stDone: Boolean
        get() = sp.getBoolean("stDone", false)
        set(v) = sp.edit().putBoolean("stDone", v).apply()

    /** Restituisce la lista di status abilitati. Se nessuno selezionato, ritorna lista vuota → nessun card. */
    fun getEnabledStatuses(): List<String> {
        val list = mutableListOf<String>()
        if (stToDo) list.add("To Do")
        if (stInProgress) list.add("In Progress")
        if (stBlocked) list.add("Blocked")
        if (stNeedReqs) list.add("Need Reqs")
        if (stDone) list.add("Done")
        return list
    }

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
