package com.pointdown.app

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
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

class MainActivity : AppCompatActivity(), CoroutineScope {
    private lateinit var statusText: TextView
    private lateinit var recyclerUnified: RecyclerView
    private lateinit var saveBtnToolbar: Button

    private lateinit var footerSaveBtn: Button
    private lateinit var footerSaveExitBtn: Button
    private lateinit var footerExitBtn: Button

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    private var adapterUnified: IssueAdapter? = null
    private var jira: JiraClient? = null
    private var itemsUnified = mutableListOf<IssueItem>()

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    // === Animazione "pulsing" del tasto Save ===
    private var savePulseAnimator: ValueAnimator? = null
    private var baseSaveColor: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        statusText = findViewById(R.id.statusText)
        recyclerUnified = findViewById(R.id.recyclerUnified)
        saveBtnToolbar = findViewById(R.id.saveBtnToolbar)

        footerSaveBtn = findViewById(R.id.footerSaveBtn)
        footerSaveExitBtn = findViewById(R.id.footerSaveExitBtn)
        footerExitBtn = findViewById(R.id.footerExitBtn)

        recyclerUnified.layoutManager = LinearLayoutManager(this)
        adapterUnified = IssueAdapter(itemsUnified) { onDirtyChanged() }
        recyclerUnified.adapter = adapterUnified

        saveBtnToolbar.setOnClickListener { saveChanges(false) }
        footerSaveBtn.setOnClickListener { saveChanges(false) }
        footerSaveExitBtn.setOnClickListener { saveChanges(true) }
        footerExitBtn.setOnClickListener { finish() }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val fromNotif = intent.getBooleanExtra("from_notification", false)
        if (fromNotif) setStatus("🔔 Notificação recebida: carregando issues Jira…")

        loadData()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPulsingSaveButtons()
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
        AlarmScheduler.scheduleDaily(this, h, m, prefs.enableWeekendNotifications)

        jira = JiraClient(prefs.baseUrl!!, prefs.email!!, prefs.token!!)
        val jql = prefs.jql
        val enabledStatuses = prefs.getEnabledStatuses()

        launch {
            try {
                val mainList: MutableList<IssueItem>
                val specialList: MutableList<IssueItem>

                withContext(Dispatchers.IO) {
                    val finalMain = jira!!.fetchCurrentSprintIssues(jql, enabledStatuses)
                    val spec = jira!!.fetchSpecialSprintIssues()

                    mainList = finalMain.toMutableList()
                    val dedupSpecial = spec.filter { s -> finalMain.none { it.key == s.key } }
                    specialList = dedupSpecial.toMutableList()
                }

                val force = prefs.forceTestCard
                val forcedKey = (prefs.testIssueKey ?: "FGC-9683").ifBlank { "FGC-9683" }
                if (force) {
                    try {
                        val alreadyKeys: Set<String> =
                            (mainList + specialList).map { it.key }.toSet()
                        if (!alreadyKeys.contains(forcedKey)) {
                            val forced = withContext(Dispatchers.IO) { jira!!.fetchIssueByKey(forcedKey) }
                            if (forced != null) mainList.add(0, forced)
                        }
                    } catch (_: Exception) { }
                }

                // ✅ Combina le due liste in una sola, con separatore testuale
                itemsUnified.clear()
                itemsUnified.addAll(mainList)
                if (specialList.isNotEmpty()) {
                    // Aggiunge elemento-separatore fittizio
                    itemsUnified.add(
                        IssueItem(
                            key = "---divider---",
                            summary = getString(R.string.special_title),
                            sp = 0.0,
                            browseUrl = "",
                            isSpecial = true
                        )
                    )
                    itemsUnified.addAll(specialList)
                }

                adapterUnified?.setData(ArrayList(itemsUnified))
                setStatus("📊 ${mainList.size} cards + ${specialList.size} especiais.")
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no loadData", e)
                setStatus("❌ ${e.message}")
            }
        }
    }

    private fun saveChanges(exitAfter: Boolean) {
        val prefs = Prefs(this)
        val toSave = itemsUnified.filter {
            it.key != "---divider---" && it.dirty && it.newSp != it.sp
        }
        if (toSave.isEmpty()) {
            setStatus("Nada para salvar.")
            // Non ci sono modifiche → stop pulsazione se stava andando
            stopPulsingSaveButtons()
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

                        val (idNumFromSrv, pas) = jira!!.getCurrentSPAndId(issue.key)
                        if (issue.idNum == null && idNumFromSrv != null) issue.idNum = idNumFromSrv

                        fun clampHalfNonNeg(v: Double): Double {
                            val r = kotlin.math.round(v * 2.0) / 2.0
                            return if (r < 0.0) 0.0 else r
                        }

                        val np = if (pas == pts) clampHalfNonNeg(userNew)
                        else clampHalfNonNeg(pas - lova)

                        var myLock: JSONObject? = null
                        if (prefs.enableQueueLock) {
                            myLock = try {
                                jira!!.acquireLockOrWait(issue.key, issue.idNum)
                            } catch (e: Exception) {
                                Log.w("point_down", "lock failure on ${issue.key}: ${e.message}")
                                null
                            }
                        }

                        try {
                            jira!!.updateStoryPoints(issue.key, np)
                            issue.sp = np
                            issue.newSp = np
                            issue.pts = np
                            issue.dirty = false
                        } finally {
                            if (prefs.enableQueueLock) {
                                runCatching { jira!!.releaseLock(issue.key, issue.idNum, myLock) }
                            }
                        }
                    }
                }

                adapterUnified?.notifyDataSetChanged()
                setStatus("✅ ${toSave.size} issue(s) atualizadas.")

                // Stop pulsazione e nascondi pulsante toolbar
                stopPulsingSaveButtons()
                saveBtnToolbar.visibility = View.GONE

                if (exitAfter) finish() else loadData()
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no saveChanges", e)
                setStatus("❌ Erro ao salvar: ${e.message}")
            }
        }
    }

    private fun onDirtyChanged() {
        // Mostra e fai un fade-in al primo sporco
        if (saveBtnToolbar.visibility != View.VISIBLE) {
            saveBtnToolbar.visibility = View.VISIBLE
            val fadeIn = AlphaAnimation(0f, 1f).apply {
                duration = 500
                fillAfter = true
            }
            saveBtnToolbar.startAnimation(fadeIn)
        }
        // Avvia/continua la pulsazione del bottone Save
        startPulsingSaveButtons()
    }

    // ================================
    // Pulsazione colore tasto "Save"
    // ================================
    private fun resolveBaseButtonColor(): Int {
        // 1) prova a usare il tint attuale del bottone toolbar (se presente)
        val tint = saveBtnToolbar.backgroundTintList?.defaultColor
        if (tint != null) return tint

        // 2) risolvi colorPrimary dal tema
        val tv = TypedValue()
        val hasColor = theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        if (hasColor) {
            return if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
        }

        // 3) fallback: un blu "material-like"
        return 0xFF2196F3.toInt()
    }

    private fun setSaveButtonsTint(color: Int) {
        val csl = ColorStateList.valueOf(color)
        saveBtnToolbar.backgroundTintList = csl
        // anche il bottone Save nel footer pulsa
        footerSaveBtn.backgroundTintList = csl
    }

    private fun startPulsingSaveButtons() {
        if (savePulseAnimator?.isRunning == true) return

        if (baseSaveColor == null) {
            baseSaveColor = resolveBaseButtonColor()
        }
        val start = baseSaveColor!!
        val lightGreen = 0xFF81C784.toInt() // verde chiaro

        // Durata 300ms per semionda, REVERSE per ottenere avanti/indietro → ~600ms ciclo completo
        savePulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), start, lightGreen).apply {
            duration = 300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val c = animator.animatedValue as Int
                setSaveButtonsTint(c)
            }
            start()
        }
    }

    private fun stopPulsingSaveButtons() {
        savePulseAnimator?.cancel()
        savePulseAnimator = null
        // ripristina il colore base
        baseSaveColor?.let { setSaveButtonsTint(it) }
    }
}
