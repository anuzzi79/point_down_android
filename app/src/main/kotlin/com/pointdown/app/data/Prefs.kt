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

    var forceTestCard: Boolean
        get() = sp.getBoolean("forceTestCard", true)
        set(v) = sp.edit().putBoolean("forceTestCard", v).apply()

    var testIssueKey: String?
        get() = sp.getString("testIssueKey", "FGC-9683")
        set(v) = sp.edit().putString("testIssueKey", v).apply()

    var enableQueueLock: Boolean
        get() = sp.getBoolean("enableQueueLock", true)
        set(v) = sp.edit().putBoolean("enableQueueLock", v).apply()

    var enableWeekendNotifications: Boolean
        get() = sp.getBoolean("enableWeekendNotifications", false)
        set(v) = sp.edit().putBoolean("enableWeekendNotifications", v).apply()

    // ============================================
    // ✅ Filtri Status
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

    var stCodeReview: Boolean
        get() = sp.getBoolean("stCodeReview", true)
        set(v) = sp.edit().putBoolean("stCodeReview", v).apply()
    var stTesting: Boolean
        get() = sp.getBoolean("stTesting", true)
        set(v) = sp.edit().putBoolean("stTesting", v).apply()
    var stQA: Boolean
        get() = sp.getBoolean("stQA", true)
        set(v) = sp.edit().putBoolean("stQA", v).apply()

    fun getEnabledStatuses(): List<String> {
        val list = mutableListOf<String>()
        if (stToDo) list.add("To Do")
        if (stInProgress) list.add("In Progress")
        if (stBlocked) list.add("Blocked")
        if (stNeedReqs) list.add("Need Reqs")
        if (stDone) list.add("Done")
        if (stCodeReview) list.add("Code Review")
        if (stTesting) list.add("Testing")
        if (stQA) list.add("QA")
        return list
    }

    // ============================================
    // ✅ PROFILO QA / DEV + Squad Mode
    // ============================================
    var profileType: String
        get() = sp.getString("profileType", "QA") ?: "QA"
        set(v) = sp.edit().putString("profileType", v).apply()

    var enableSquadMode: Boolean
        get() = sp.getBoolean("enableSquadMode", false)
        set(v) = sp.edit().putBoolean("enableSquadMode", v).apply()

    // ✅ Squad keywords (stored as JSON array string)
    fun getSquadKeywords(): List<String> {
        val raw = sp.getString("squadKeywords", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setSquadKeywords(keywords: List<String>) {
        val arr = org.json.JSONArray()
        keywords.forEach { arr.put(it) }
        sp.edit().putString("squadKeywords", arr.toString()).apply()
    }

    fun getHourMinute(): Pair<Int, Int> {
        val t = alarmTime ?: "17:50"
        val m = Regex("(\\d{1,2}):(\\d{2})").find(t)
        return if (m != null) {
            val h = m.groupValues[1].toInt().coerceIn(0, 23)
            val min = m.groupValues[2].toInt().coerceIn(0, 59)
            h to min
        } else 17 to 50
    }

    fun isConfigured(): Boolean =
        !baseUrl.isNullOrBlank() && !email.isNullOrBlank() && !token.isNullOrBlank()

    // ============================================
    // ✅ Search codes (FGC-<digits>) and Squad Epics storage
    // ============================================
    fun getSearchCodes(): List<String> {
        val raw = sp.getString("searchCodes", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setSearchCodes(codes: List<String>) {
        val arr = org.json.JSONArray()
        codes.forEach { arr.put(it) }
        sp.edit().putString("searchCodes", arr.toString()).apply()
    }

    fun getSquadEpics(): List<String> {
        val raw = sp.getString("squadEpics", "[]") ?: "[]"
        return try {
            val arr = org.json.JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.optString(i))
                }
            }
        } catch (_: Exception) { emptyList() }
    }

    fun setSquadEpics(codes: List<String>) {
        val arr = org.json.JSONArray()
        codes.forEach { arr.put(it) }
        sp.edit().putString("squadEpics", arr.toString()).apply()
    }
}
