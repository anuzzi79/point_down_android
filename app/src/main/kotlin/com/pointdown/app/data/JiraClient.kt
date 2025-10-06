package com.pointdown.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import kotlin.math.round

class JiraClient(private val baseUrl: String, email: String, token: String) {
    private val auth: String
    private val client: OkHttpClient

    companion object {
        private const val SP_FIELD_ID = "customfield_10022" // Story Points (ID fisso come da estensione)
        private val STATUSES = listOf("In Progress", "Blocked", "Need Reqs", "Done")

        // === Lock cooperativo (stessa semantica del Chrome extension) ===
        const val PD_LOCK_KEY = "point_down_lock"
        private const val PD_LOCK_TTL_MS: Long = 60_000
        private const val PD_LOCK_WAIT_TOTAL_MS: Long = 30_000
        private const val PD_LOCK_POLL_MS: Long = 900
        private const val PD_TASK_POLL_MS: Long = 800
        private const val PD_TASK_POLL_MAX: Int = 20
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
                    val idNum = obj.optString("id", null)?.toLongOrNull()
                    val fields = obj.optJSONObject("fields") ?: JSONObject()
                    val summary = fields.optString("summary", "(sem resumo)")
                    val sp = fields.optDouble(SP_FIELD_ID, 0.0)

                    all.add(
                        IssueItem(
                            key = key,
                            summary = summary,
                            sp = sp,
                            browseUrl = "${baseUrl.trimEnd('/')}/browse/$key",
                            newSp = sp,
                            dirty = false,
                            isSpecial = false,
                            pts = sp, // baseline “fotografata” al fetch
                            idNum = idNum
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

    /** ✅ Lettura puntuale del valore Story Points corrente + id numerico. */
    suspend fun getCurrentSPAndId(issueKey: String): Pair<Long?, Double> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/rest/api/3/issue/$issueKey?fields=$SP_FIELD_ID")
            .get()
            .headers(headers())
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                val t = resp.body?.string()
                throw IllegalStateException("Falha ao obter SP de $issueKey: ${resp.code} ${t ?: ""}")
            }
            val data = JSONObject(resp.body?.string() ?: "{}")
            val idNum = data.optString("id", null)?.toLongOrNull()
            val fields = data.optJSONObject("fields") ?: JSONObject()
            val sp = fields.optDouble(SP_FIELD_ID, 0.0)
            Pair(idNum, sp)
        }
    }

    /** ✅ Fetch diretto per una issue (usata per la “card di test”). */
    suspend fun fetchIssueByKey(issueKey: String): IssueItem? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/rest/api/3/issue/$issueKey?fields=${"summary,$SP_FIELD_ID"}")
            .get()
            .headers(headers())
            .build()

        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@use null
            val data = JSONObject(resp.body?.string() ?: "{}")
            val key = data.optString("key", issueKey)
            val idNum = data.optString("id", null)?.toLongOrNull()
            val fields = data.optJSONObject("fields") ?: JSONObject()
            val summary = fields.optString("summary", "(sem resumo)")
            val sp = fields.optDouble(SP_FIELD_ID, 0.0)
            return@use IssueItem(
                key = key,
                summary = summary,
                sp = sp,
                browseUrl = "${baseUrl.trimEnd('/')}/browse/$key",
                newSp = sp,
                dirty = false,
                isSpecial = false,
                pts = sp,
                idNum = idNum
            )
        }
    }

    suspend fun updateStoryPoints(issueKey: String, newValue: Double): Boolean =
        withContext(Dispatchers.IO) {
            val rounded = (round(newValue * 2.0) / 2.0).coerceAtLeast(0.0)
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

    // =========================
    // === Locking utilities ===
    // =========================

    private fun nowIsoPlus(ms: Long): String =
        Date(System.currentTimeMillis() + ms).toInstant().toString()

    private fun randNonce(): String =
        "${System.currentTimeMillis().toString(36)}-${UUID.randomUUID().toString().take(8)}"

    private suspend fun pollTask(locationUrl: String): Boolean = withContext(Dispatchers.IO) {
        repeat(PD_TASK_POLL_MAX) {
            delay(PD_TASK_POLL_MS)
            val req = Request.Builder()
                .url(locationUrl)
                .get()
                .headers(okhttp3.Headers.headersOf("Authorization", auth, "Accept", "application/json"))
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val body = resp.body?.string().orEmpty()
                val task = runCatching { JSONObject(body) }.getOrNull()
                val st = task?.optString("status")
                    ?: task?.optString("state")
                    ?: task?.optString("currentStatus")
                    ?: task?.optString("elementType")
                    ?: ""
                if (st.contains("COMPLETE", true) || st.contains("SUCCESS", true)) return@withContext true
                if (st.contains("FAILED", true) || st.contains("ERROR", true)) return@withContext false
            }
        }
        false
    }

    private suspend fun bulkSetPropertyFiltered(propKey: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/rest/api/3/issue/properties/${propKey}")
                .put(body.toString().toRequestBody("application/json".toMediaType()))
                .headers(headers())
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in listOf(200, 202, 303)) {
                    val t = resp.body?.string()
                    throw IllegalStateException("Falha bulk set property: ${resp.code} ${t ?: ""}")
                }
                val loc = resp.header("location")
                if (loc.isNullOrBlank()) return@use true
                return@use pollTask(loc)
            }
        }

    private suspend fun bulkDeletePropertyFiltered(propKey: String, body: JSONObject): Boolean =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/rest/api/3/issue/properties/${propKey}")
                .delete(body.toString().toRequestBody("application/json".toMediaType()))
                .headers(headers())
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code !in listOf(200, 202, 204, 303)) {
                    val t = resp.body?.string()
                    throw IllegalStateException("Falha bulk delete property: ${resp.code} ${t ?: ""}")
                }
                val loc = resp.header("location")
                if (loc.isNullOrBlank()) return@use true
                return@use pollTask(loc)
            }
        }

    private suspend fun getIssueProperty(issueKey: String, propKey: String): JSONObject? =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/rest/api/3/issue/$issueKey/properties/$propKey")
                .get()
                .headers(headers())
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.code == 404) return@use null
                if (!resp.isSuccessful) {
                    val t = resp.body?.string()
                    throw IllegalStateException("Erro ao ler property: ${resp.code} ${t ?: ""}")
                }
                val obj = JSONObject(resp.body?.string().orEmpty())
                obj.opt("value") as? JSONObject
            }
        }

    /**
     * Tenta acquisire lock cooperativo su una issue:
     * 1) CAS create (hasProperty=false)
     * 2) se esiste ed è scaduto → takeover (hasProperty=true + currentValue)
     * 3) altrimenti attende con backoff fino a timeout
     *
     * @return l'oggetto JSON del *mio* lock (da usare in release) oppure null se disabilitato a livello app
     */
    suspend fun acquireLockOrWait(issueKey: String, idNum: Long?): JSONObject {
        require(idNum != null && idNum > 0) { "Issue ID numerico necessario per il lock ($issueKey)" }

        val myLock = JSONObject().apply {
            put("owner", auth) // basta identificare debolmente (email è codificata dentro, va bene)
            put("nonce", randNonce())
            put("expiresAt", nowIsoPlus(PD_LOCK_TTL_MS))
        }

        val started = System.currentTimeMillis()
        var attempt = 0

        while (System.currentTimeMillis() - started < PD_LOCK_WAIT_TOTAL_MS) {
            attempt++

            // 1) CAS create se property assente
            val created = runCatching {
                bulkSetPropertyFiltered(PD_LOCK_KEY, JSONObject().apply {
                    put("filter", JSONObject().apply {
                        put("entityIds", JSONArray().put(idNum))
                        put("hasProperty", false)
                    })
                    put("value", myLock)
                })
            }.getOrElse { false }

            if (created) {
                // verifica
                val v = getIssueProperty(issueKey, PD_LOCK_KEY)
                if (v != null && v.optString("nonce") == myLock.optString("nonce")) return myLock
            }

            // 2) property esistente → scaduta?
            val existing = getIssueProperty(issueKey, PD_LOCK_KEY)
            val expStr = existing?.optString("expiresAt")
            val exp = expStr?.let { runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            val isExpired = (exp == null) || (System.currentTimeMillis() > exp)

            if (existing != null && isExpired) {
                // takeover CAS: rimpiazza solo se currentValue == existing
                val takeover = runCatching {
                    bulkSetPropertyFiltered(PD_LOCK_KEY, JSONObject().apply {
                        put("filter", JSONObject().apply {
                            put("entityIds", JSONArray().put(idNum))
                            put("hasProperty", true)
                            put("currentValue", existing)
                        })
                        put("value", myLock)
                    })
                }.getOrElse { false }

                if (takeover) {
                    val v2 = getIssueProperty(issueKey, PD_LOCK_KEY)
                    if (v2 != null && v2.optString("nonce") == myLock.optString("nonce")) return myLock
                }
            }

            // 3) attesa + jitter
            delay(PD_LOCK_POLL_MS + (0..400).random().toLong())
        }

        throw IllegalStateException("Timeout aguardando fila para $issueKey")
    }

    suspend fun releaseLock(issueKey: String, idNum: Long?, myLock: JSONObject?) {
        if (idNum == null || myLock == null) return
        runCatching {
            bulkDeletePropertyFiltered(PD_LOCK_KEY, JSONObject().apply {
                put("entityIds", JSONArray().put(idNum))
                put("currentValue", myLock)
            })
        }
    }
}
