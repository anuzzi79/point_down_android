package com.pointdown.app.data

data class IssueItem(
    val key: String,
    val summary: String?,
    var sp: Double,                 // valore attuale mostrato
    val browseUrl: String,
    var newSp: Double = sp,         // valore modificabile dall’utente
    var dirty: Boolean = false,     // flag “*modificado”
    var isSpecial: Boolean = false, // lista speciale (explorat*/regres*)
    var pts: Double? = sp,          // baseline locale (Chrome-like)
    var idNum: Long? = null         // ✅ ID numerico dell'issue (necessario per bulk property locking)
)
