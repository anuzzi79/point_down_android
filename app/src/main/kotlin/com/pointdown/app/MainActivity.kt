package com.pointdown.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.pointdown.app.alarm.AlarmScheduler
import com.pointdown.app.data.IssueItem
import com.pointdown.app.data.JiraClient
import com.pointdown.app.data.Prefs
import com.pointdown.app.ui.IssueAdapter
import kotlinx.coroutines.*
import org.json.JSONObject
import kotlin.coroutines.CoroutineContext
import kotlin.math.round

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var statusText: TextView
    private lateinit var recyclerMain: RecyclerView
    private lateinit var recyclerSpecial: RecyclerView
    private lateinit var specialTitle: TextView
    private lateinit var saveBtnToolbar: Button

    // Footer buttons
    private lateinit var footerSaveBtn: Button
    private lateinit var footerSaveExitBtn: Button
    private lateinit var footerExitBtn: Button

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    private var adapterMain: IssueAdapter? = null
    private var adapterSpecial: IssueAdapter? = null

    private var jira: JiraClient? = null
    private var itemsMain = mutableListOf<IssueItem>()
    private var itemsSpecial = mutableListOf<IssueItem>()

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        statusText = findViewById(R.id.statusText)
        recyclerMain = findViewById(R.id.recyclerMain)
        recyclerSpecial = findViewById(R.id.recyclerSpecial)
        specialTitle = findViewById(R.id.specialTitle)
        saveBtnToolbar = findViewById(R.id.saveBtnToolbar)

        // Footer
        footerSaveBtn = findViewById(R.id.footerSaveBtn)
        footerSaveExitBtn = findViewById(R.id.footerSaveExitBtn)
        footerExitBtn = findViewById(R.id.footerExitBtn)

        recyclerMain.layoutManager = LinearLayoutManager(this)
        recyclerSpecial.layoutManager = LinearLayoutManager(this)

        adapterMain = IssueAdapter(itemsMain) { onDirtyChanged() }
        adapterSpecial = IssueAdapter(itemsSpecial) { onDirtyChanged() }

        recyclerMain.adapter = adapterMain
        recyclerSpecial.adapter = adapterSpecial

        saveBtnToolbar.setOnClickListener { saveChanges(false) }
        footerSaveBtn.setOnClickListener { saveChanges(false) }
        footerSaveExitBtn.setOnClickListener { saveChanges(true) }
        footerExitBtn.setOnClickListener { finish() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val fromNotif = intent.getBooleanExtra("from_notification", false)
        if (fromNotif) {
            setStatus("🔔 Notificação recebida: carregando issues Jira…")
        }
        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> { loadData(); true }
            R.id.action_save -> { saveChanges(false); true }
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setStatus(msg: String) {
        statusText.text = msg
    }

    private fun loadData() {
        val prefs = Prefs(this)
        if (!prefs.isConfigured()) {
            setStatus("Configura Base URL / Email / Token em Settings.")
            return
        }

        setStatus(getString(R.string.status_loading))

        val (h, m) = prefs.getHourMinute()
        AlarmScheduler.scheduleDaily(this, h, m)

        jira = JiraClient(prefs.baseUrl!!, prefs.email!!, prefs.token!!)
        val jql = prefs.jql

        launch {
            try {
                val mainList: MutableList<IssueItem>
                val specialList: MutableList<IssueItem>

                withContext(Dispatchers.IO) {
                    val finalMain = jira!!.fetchCurrentSprintIssues(jql)
                    val spec = jira!!.fetchSpecialSprintIssues()

                    mainList = finalMain.toMutableList()
                    val dedupSpecial = spec.filter { s -> finalMain.none { it.key == s.key } }
                    specialList = dedupSpecial.toMutableList()
                }

                // Card di test opzionale
                val force = prefs.forceTestCard
                val forcedKey = (prefs.testIssueKey ?: "FGC-9683").ifBlank { "FGC-9683" }
                if (force) {
                    try {
                        val alreadyKeys: Set<String> = (mainList + specialList).map { it.key }.toSet()
                        if (!alreadyKeys.contains(forcedKey)) {
                            val forced = withContext(Dispatchers.IO) { jira!!.fetchIssueByKey(forcedKey) }
                            if (forced != null) {
                                mainList.add(0, forced)
                            }
                        }
                    } catch (_: Exception) { /* no-op */ }
                }

                itemsMain.clear(); itemsMain.addAll(mainList)
                itemsSpecial.clear(); itemsSpecial.addAll(specialList)

                adapterMain?.setData(ArrayList(itemsMain))

                if (itemsSpecial.isNotEmpty()) {
                    specialTitle.visibility = View.VISIBLE
                    recyclerSpecial.visibility = View.VISIBLE
                    adapterSpecial?.setData(ArrayList(itemsSpecial))
                } else {
                    specialTitle.visibility = View.GONE
                    recyclerSpecial.visibility = View.GONE
                }

                setStatus("📊 ${itemsMain.size} cards (main) + ${itemsSpecial.size} special.")
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no loadData", e)
                setStatus("❌ ${e.message}")
            }
        }
    }

    private fun saveChanges(exitAfter: Boolean) {
        val prefs = Prefs(this)
        val toSave = (itemsMain + itemsSpecial).filter { it.dirty && it.newSp != it.sp }
        if (toSave.isEmpty()) {
            setStatus("Nada para salvar.")
            if (exitAfter) finish()
            return
        }
        setStatus("💾 Salvando alterações…")
        launch {
            try {
                withContext(Dispatchers.IO) {
                    toSave.forEach { issue ->
                        val pts = issue.pts ?: issue.sp
                        val userNew = issue.newSp
                        val lova = pts - userNew

                        // Rilettura server + id
                        val (idNumFromSrv, pas) = jira!!.getCurrentSPAndId(issue.key)
                        if (issue.idNum == null && idNumFromSrv != null) issue.idNum = idNumFromSrv

                        fun clampHalfNonNeg(v: Double): Double {
                            val r = kotlin.math.round(v * 2.0) / 2.0
                            return if (r < 0.0) 0.0 else r
                        }

                        val np = if (pas == pts) clampHalfNonNeg(userNew)
                        else clampHalfNonNeg(pas - lova)

                        // === Corridoio / Lock cooperativo (se abilitato) ===
                        var myLock: JSONObject? = null
                        if (prefs.enableQueueLock) {
                            myLock = try {
                                jira!!.acquireLockOrWait(issue.key, issue.idNum)
                            } catch (e: Exception) {
                                // Non bloccare l'intera batch: passa oltre questa issue
                                Log.w("point_down", "lock failure on ${issue.key}: ${e.message}")
                                null
                            }
                        }

                        try {
                            jira!!.updateStoryPoints(issue.key, np)

                            // Baseline aggiornata subito
                            issue.sp = np
                            issue.newSp = np
                            issue.pts = np
                            issue.dirty = false
                        } finally {
                            // Rilascio condizionato
                            if (prefs.enableQueueLock) {
                                runCatching { jira!!.releaseLock(issue.key, issue.idNum, myLock) }
                            }
                        }
                    }
                }
                adapterMain?.notifyDataSetChanged()
                adapterSpecial?.notifyDataSetChanged()
                setStatus("✅ ${toSave.size} issue(s) atualizadas.")

                saveBtnToolbar.visibility = View.GONE
                if (exitAfter) {
                    finish()
                } else {
                    loadData() // refresh dal server, come nel modal Chrome
                }
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no saveChanges", e)
                setStatus("❌ Erro ao salvar: ${e.message}")
            }
        }
    }

    // Mostra il pulsante Save in toolbar con fade-in dopo edit (opzionale)
    private fun onDirtyChanged() {
        if (saveBtnToolbar.visibility != View.VISIBLE) {
            saveBtnToolbar.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 500
                fillAfter = true
            }
            saveBtnToolbar.startAnimation(fadeIn)
        }
    }
}
