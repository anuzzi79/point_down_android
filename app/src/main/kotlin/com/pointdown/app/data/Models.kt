package com.pointdown.app.data

data class IssueItem(
    val key: String,
    val summary: String?,
    var sp: Double,                 // cambiato in var
    val browseUrl: String,
    var newSp: Double = sp,
    var dirty: Boolean = false,
    var isSpecial: Boolean = false  // cambiato in var
)
