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

    private lateinit var footerSaveBtn: Button
    private lateinit var footerSaveExitBtn: Button
    private lateinit var footerExitBtn: Button

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext = Dispatchers.Main + job

    private var adapterUnified: IssueAdapter? = null
    private var jira: JiraClient? = null

    // Liste “sorgente” separate (principale / special) e per SP>0 vs SP=0
    private var mainGt0 = mutableListOf<IssueItem>()
    private var mainEq0 = mutableListOf<IssueItem>()
    private var specialGt0 = mutableListOf<IssueItem>()
    private var specialEq0 = mutableListOf<IssueItem>()

    // Stato dei toggle (default: nascosti)
    private var showMainZeros = false
    private var showSpecialZeros = false

    private val itemsUnified = mutableListOf<IssueItem>()

    private val notifPerm = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {}

    // === Animazione "pulsing" del tasto Save (solo footer) ===
    private var savePulseAnimator: ValueAnimator? = null
    private var baseSaveColor: Int? = null
    private var siblingTintList: ColorStateList? = null

    // === NUOVO: Animazione "pulsing" del testo di status durante il salvataggio ===
    private var statusPulseAnimator: ValueAnimator? = null
    private val SAVING_SUBSTRING = "Salvando alterações" // trigger per il pulse del testo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = getString(R.string.app_name)

        statusText = findViewById(R.id.statusText)
        recyclerUnified = findViewById(R.id.recyclerUnified)

        footerSaveBtn = findViewById(R.id.footerSaveBtn)
        footerSaveExitBtn = findViewById(R.id.footerSaveExitBtn)
        footerExitBtn = findViewById(R.id.footerExitBtn)

        recyclerUnified.layoutManager = LinearLayoutManager(this)
        adapterUnified = IssueAdapter(
            itemsUnified,
            onDirtyChanged = { anyDirty -> onDirtyChanged(anyDirty) },
            onToggleClick = { which ->
                when (which) {
                    IssueAdapter.TOGGLE_MAIN -> {
                        showMainZeros = !showMainZeros
                        rebuildDisplayList()
                    }
                    IssueAdapter.TOGGLE_SPECIAL -> {
                        showSpecialZeros = !showSpecialZeros
                        rebuildDisplayList()
                    }
                }
            }
        )
        recyclerUnified.adapter = adapterUnified

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
        stopPulsingStatusText()
        job.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                stopPulsingSaveButtons()
                loadData()
                true
            }
            R.id.action_settings_gear -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setStatus(msg: String) {
        statusText.text = msg
        // Gestione pulsazione del testo "Salvando alterações"
        if (msg.contains(SAVING_SUBSTRING)) {
            startPulsingStatusText()
        } else {
            stopPulsingStatusText()
        }
    }

    // === Caricamento dati ===
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

        // resetta i toggle ad ogni reload esplicito
        showMainZeros = false
        showSpecialZeros = false

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

                val force = Prefs(this@MainActivity).forceTestCard
                val forcedKey = (Prefs(this@MainActivity).testIssueKey ?: "FGC-9683").ifBlank { "FGC-9683" }
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

                // Split per SP>0 vs SP=0
                fun split(list: List<IssueItem>): Pair<MutableList<IssueItem>, MutableList<IssueItem>> {
                    val gt0 = mutableListOf<IssueItem>()
                    val eq0 = mutableListOf<IssueItem>()
                    list.forEach { if ((it.sp) > 0.0) gt0.add(it) else eq0.add(it) }
                    return gt0 to eq0
                }

                val (mGt0, mEq0) = split(mainList)
                val (sGt0, sEq0) = split(specialList)
                mainGt0 = mGt0
                mainEq0 = mEq0
                specialGt0 = sGt0
                specialEq0 = sEq0

                rebuildDisplayList()
                setStatus("📊 ${mainList.size} cards + ${specialList.size} especiais.")
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no loadData", e)
                setStatus("❌ ${e.message}")
            }
        }
    }

    /** Ricostruisce la lista “piatta” per l’adapter rispettando i toggle. */
    private fun rebuildDisplayList() {
        itemsUnified.clear()

        // Sezione 1: principali
        itemsUnified.addAll(mainGt0)
        // Toggle per sezione principale
        itemsUnified.add(
            IssueItem(
                key = IssueAdapter.TOGGLE_MAIN,
                summary = if (showMainZeros) getString(R.string.hide_zeros) else getString(R.string.show_zeros),
                sp = 0.0,
                browseUrl = "",
                isSpecial = false
            )
        )
        if (showMainZeros && mainEq0.isNotEmpty()) {
            itemsUnified.addAll(mainEq0)
        }

        // Sezione 2: special
        if (specialGt0.isNotEmpty() || specialEq0.isNotEmpty()) {
            itemsUnified.add(
                IssueItem(
                    key = IssueAdapter.DIVIDER_KEY,
                    summary = getString(R.string.special_title),
                    sp = 0.0,
                    browseUrl = "",
                    isSpecial = true
                )
            )
            itemsUnified.addAll(specialGt0)
            // Toggle per sezione Special
            itemsUnified.add(
                IssueItem(
                    key = IssueAdapter.TOGGLE_SPECIAL,
                    summary = if (showSpecialZeros) getString(R.string.hide_zeros) else getString(R.string.show_zeros),
                    sp = 0.0,
                    browseUrl = "",
                    isSpecial = true
                )
            )
            if (showSpecialZeros && specialEq0.isNotEmpty()) {
                itemsUnified.addAll(specialEq0)
            }
        }

        adapterUnified?.setData(ArrayList(itemsUnified))
    }

    // === Salvataggio ===
    private fun saveChanges(exitAfter: Boolean) {
        // ➜ Appena si preme Save: smetti di pulsare il bottone
        stopPulsingSaveButtons()

        val toSave = itemsUnified.filter {
            it.key != IssueAdapter.DIVIDER_KEY &&
                    it.key != IssueAdapter.TOGGLE_MAIN &&
                    it.key != IssueAdapter.TOGGLE_SPECIAL &&
                    it.dirty && it.newSp != it.sp
        }
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

                        val (idNumFromSrv, pas) = jira!!.getCurrentSPAndId(issue.key)
                        if (issue.idNum == null && idNumFromSrv != null) issue.idNum = idNumFromSrv

                        fun clampHalfNonNeg(v: Double): Double {
                            val r = kotlin.math.round(v * 2.0) / 2.0
                            return if (r < 0.0) 0.0 else r
                        }

                        val np = if (pas == pts) clampHalfNonNeg(userNew)
                        else clampHalfNonNeg(pas - lova)

                        var myLock: JSONObject? = null
                        if (Prefs(this@MainActivity).enableQueueLock) {
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
                            if (Prefs(this@MainActivity).enableQueueLock) {
                                runCatching { jira!!.releaseLock(issue.key, issue.idNum, myLock) }
                            }
                        }
                    }
                }

                adapterUnified?.notifyDataSetChanged()
                setStatus("✅ ${toSave.size} issue(s) atualizadas.")
                if (exitAfter) finish() else loadData()
            } catch (e: Exception) {
                Log.e("point_down", "❌ Errore no saveChanges", e)
                setStatus("❌ Erro ao salvar: ${e.message}")
            }
        }
    }

    private fun onDirtyChanged(anyDirty: Boolean) {
        if (anyDirty) startPulsingSaveButtons() else stopPulsingSaveButtons()
    }

    // === Pulsazione colore tasto Save (footer) ===
    private fun resolveBaseButtonColor(): Int {
        footerSaveBtn.backgroundTintList?.defaultColor?.let { return it }
        val tv = TypedValue()
        val hasColor = theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        return if (hasColor) {
            if (tv.resourceId != 0) ContextCompat.getColor(this, tv.resourceId) else tv.data
        } else 0xFF2196F3.toInt()
    }

    private fun setSaveButtonsTint(color: Int) {
        val csl = ColorStateList.valueOf(color)
        footerSaveBtn.backgroundTintList = csl
    }

    private fun captureSiblingTintListIfNeeded() {
        if (siblingTintList == null) {
            siblingTintList =
                footerSaveExitBtn.backgroundTintList ?: footerExitBtn.backgroundTintList
        }
    }

    private fun startPulsingSaveButtons() {
        if (savePulseAnimator?.isRunning == true) return
        captureSiblingTintListIfNeeded()
        if (baseSaveColor == null) baseSaveColor = resolveBaseButtonColor()
        val start = baseSaveColor!!
        val lightGreen = 0xFF81C784.toInt()

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
        captureSiblingTintListIfNeeded()
        siblingTintList?.let { footerSaveBtn.backgroundTintList = it }
        footerSaveBtn.alpha = 1f
    }

    // === NUOVO: Pulsazione del testo "Salvando alterações…" tra verde e bianco ===
    private fun startPulsingStatusText() {
        if (statusPulseAnimator?.isRunning == true) return
        val green = 0xFF81C784.toInt()
        val white = 0xFFFFFFFF.toInt()

        statusPulseAnimator = ValueAnimator.ofObject(ArgbEvaluator(), white, green).apply {
            duration = 300L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val c = animator.animatedValue as Int
                statusText.setTextColor(c)
            }
            start()
        }
    }

    private fun stopPulsingStatusText() {
        statusPulseAnimator?.cancel()
        statusPulseAnimator = null
        // Ripristina il colore di default del testo di status (bianco)
        statusText.setTextColor(ContextCompat.getColor(this, android.R.color.white))
    }
}
