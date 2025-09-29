package com.pointdown.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class JiraClient(private val baseUrl: String, email: String, token: String) {
    private val auth: String
    private val client: OkHttpClient

    companion object {
        private const val SP_FIELD_ID = "customfield_10022" // Story Points
        private val STATUSES = listOf("In Progress", "Blocked", "Need Reqs", "Done")
    }

    init {
        val enc = Base64.getEncoder().encodeToString("$email:$token".toByteArray())
        auth = "Basic $enc"
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        client = OkHttpClient.Builder().addInterceptor(log).build()
    }

    private fun headers(): okhttp3.Headers {
        return okhttp3.Headers.headersOf(
            "Authorization", auth,
            "Accept", "application/json",
            "Content-Type", "application/json"
        )
    }

    private fun jqlWithStatuses(base: String?): String {
        val clause = "status IN (${STATUSES.joinToString(",") { "\"$it\"" }})"
        val trimmed = base?.trim().orEmpty()
        return if (trimmed.isEmpty()) clause else "($trimmed) AND $clause"
    }

    suspend fun fetchIssuesByJql(finalJql: String): List<IssueItem> = withContext(Dispatchers.IO) {
        val all = mutableListOf<IssueItem>()
        var nextPageToken: String? = null

        do {
            val bodyJson = JSONObject().apply {
                put("jql", finalJql)
                put("fields", JSONArray(listOf("summary", SP_FIELD_ID)))
                put("maxResults", 100)
                if (nextPageToken != null) put("nextPageToken", nextPageToken)
            }

            val req = Request.Builder()
                .url("$baseUrl/rest/api/3/search/jql")
                .post(bodyJson.toString().toRequestBody("application/json".toMediaType()))
                .headers(headers())
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Falha /search/jql: ${resp.code} ${resp.message}")
                }

                val data = JSONObject(resp.body?.string() ?: "{}")
                val issues = data.optJSONArray("issues") ?: JSONArray()

                for (i in 0 until issues.length()) {
                    val obj = issues.getJSONObject(i)
                    val key = obj.getString("key")
                    val fields = obj.getJSONObject("fields")
                    val summary = fields.optString("summary", "(sem resumo)")
                    val sp = fields.optDouble(SP_FIELD_ID, 0.0)

                    all.add(
                        IssueItem(
                            key = key,
                            summary = summary,
                            sp = sp,
                            newSp = sp,
                            dirty = false,
                            browseUrl = "${baseUrl.trimEnd('/')}/browse/$key"
                        )
                    )
                }

                nextPageToken = if (data.optBoolean("isLast", true)) null
                else data.optString("nextPageToken", null).takeIf { !it.isNullOrBlank() }
            }
        } while (nextPageToken != null)

        return@withContext all.sortedByDescending { it.sp }
    }

    suspend fun testAuth(): Boolean = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/rest/api/3/myself")
            .get()
            .headers(headers())
            .build()

        client.newCall(req).execute().use { it.isSuccessful }
    }

    suspend fun fetchCurrentSprintIssues(userJql: String?): List<IssueItem> {
        val base = if (!userJql.isNullOrBlank()) userJql.trim()
        else "sprint in openSprints() AND assignee = currentUser() AND statusCategory != Done"

        val finalJql = jqlWithStatuses(base)
        return fetchIssuesByJql(finalJql)
    }

    suspend fun fetchSpecialSprintIssues(): List<IssueItem> {
        val specialBase =
            """sprint in openSprints() AND (summary ~ "explorat" OR summary ~ "regres" OR summary ~ "regress")"""
        val finalJql = jqlWithStatuses(specialBase)
        return fetchIssuesByJql(finalJql).map { it.copy(isSpecial = true) }
    }

    suspend fun updateStoryPoints(issueKey: String, newValue: Double): Boolean =
        withContext(Dispatchers.IO) {
            val rounded = (kotlin.math.round(newValue * 2.0) / 2.0).coerceAtLeast(0.0)
            val body = JSONObject().put("fields", JSONObject().put(SP_FIELD_ID, rounded)).toString()

            val req = Request.Builder()
                .url("$baseUrl/rest/api/3/issue/$issueKey")
                .put(body.toRequestBody("application/json".toMediaType()))
                .headers(headers())
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val t = resp.body?.string()
                    throw IllegalStateException("Falha ao salvar $issueKey: ${resp.code} ${t ?: ""}")
                }
                true
            }
        }
}
