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
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var statusText: TextView
    private lateinit var recyclerMain: RecyclerView
    private lateinit var recyclerSpecial: RecyclerView
    private lateinit var specialTitle: TextView
    private lateinit var saveText: TextView   // 👈 scritta "Save"

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

        statusText = findViewById(R.id.statusText)
        recyclerMain = findViewById(R.id.recyclerMain)
        recyclerSpecial = findViewById(R.id.recyclerSpecial)
        specialTitle = findViewById(R.id.specialTitle)
        saveText = findViewById(R.id.saveText)

        recyclerMain.layoutManager = LinearLayoutManager(this)
        recyclerSpecial.layoutManager = LinearLayoutManager(this)

        adapterMain = IssueAdapter(itemsMain) { onDirtyChanged() }
        adapterSpecial = IssueAdapter(itemsSpecial) { onDirtyChanged() }

        recyclerMain.adapter = adapterMain
        recyclerSpecial.adapter = adapterSpecial

        saveText.setOnClickListener { saveChanges() }

        // permesso notifiche (solo Android 13+)
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
            R.id.action_save -> { saveChanges(); true } // fallback
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
                val (main, special) = withContext(Dispatchers.IO) {
                    val finalMain = jira!!.fetchCurrentSprintIssues(jql)
                    Log.e("point_down", "✅ JQL finale usato: ${jql ?: "default"}")
                    Log.e("point_down", "📊 Main JQL -> trovate ${finalMain.size} cards")

                    val spec = jira!!.fetchSpecialSprintIssues()
                    Log.e("point_down", "📊 Special JQL -> trovate ${spec.size} cards")

                    Pair(finalMain, spec.filter { s -> finalMain.none { it.key == s.key } })
                }

                itemsMain.clear(); itemsMain.addAll(main)
                itemsSpecial.clear(); itemsSpecial.addAll(special)

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

    private fun saveChanges() {
        val toSave = (itemsMain + itemsSpecial).filter { it.dirty && it.newSp != it.sp }
        if (toSave.isEmpty()) {
            setStatus("Nada para salvar.")
            return
        }
        setStatus("💾 Salvando alterações…")
        launch {
            try {
                withContext(Dispatchers.IO) {
                    toSave.forEach { jira!!.updateStoryPoints(it.key, it.newSp) }
                }
                toSave.forEach { it.sp = it.newSp; it.dirty = false }
                adapterMain?.notifyDataSetChanged()
                adapterSpecial?.notifyDataSetChanged()
                setStatus("✅ ${toSave.size} issue(s) atualizadas.")
                saveText.visibility = View.GONE
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no saveChanges", e)
                setStatus("❌ Erro ao salvar: ${e.message}")
            }
        }
    }

    // 🔥 Mostra il testo Save con fade-in
    private fun onDirtyChanged() {
        if (saveText.visibility != View.VISIBLE) {
            saveText.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 500
                fillAfter = true
            }
            saveText.startAnimation(fadeIn)
        }
    }
}
