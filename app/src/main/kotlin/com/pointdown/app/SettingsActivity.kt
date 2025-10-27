package com.pointdown.app

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pointdown.app.alarm.AlarmScheduler
import com.pointdown.app.data.Prefs
import kotlinx.coroutines.*
import android.content.Intent
import android.net.Uri
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class SettingsActivity : AppCompatActivity(), CoroutineScope {
    private val job = SupervisorJob()
    override val coroutineContext = Dispatchers.Main + job
    override fun onDestroy() { super.onDestroy(); job.cancel() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = Prefs(this)

        val baseUrl = findViewById<EditText>(R.id.baseUrlEdit)
        val email = findViewById<EditText>(R.id.emailEdit)
        val token = findViewById<EditText>(R.id.tokenEdit)
        val jql = findViewById<EditText>(R.id.jqlEdit)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val advancedBtn = findViewById<Button>(R.id.advancedBtn)
        val advancedSection = findViewById<LinearLayout>(R.id.advancedSection)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val testBtn = findViewById<Button>(R.id.testBtn)
        
        val qaCheck = findViewById<CheckBox>(R.id.qaCheck)
        val devCheck = findViewById<CheckBox>(R.id.devCheck)
        val squadModeBlock = findViewById<LinearLayout>(R.id.squadModeBlock)
        val squadModeCheck = findViewById<CheckBox>(R.id.squadModeCheck)
        val squadKeywordsEdit = findViewById<EditText>(R.id.squadKeywordsEdit)
        val addKeywordBtn = findViewById<ImageButton>(R.id.addKeywordBtn)
        val keywordsChipGroup = findViewById<ChipGroup>(R.id.keywordsChipGroup)
        val squadEpicInput = findViewById<EditText>(R.id.squadEpicInput)
        val addSquadEpicBtn = findViewById<ImageButton>(R.id.addSquadEpicBtn)
        val squadEpicChipGroup = findViewById<ChipGroup>(R.id.squadEpicChipGroup)

        // Advanced toggles and status filters
        val forceTestCardCheck = findViewById<CheckBox>(R.id.forceTestCardCheck)
        val enableQueueLockCheck = findViewById<CheckBox>(R.id.enableQueueLockCheck)
        val enableWeekendCheck = findViewById<CheckBox>(R.id.enableWeekendCheck)
        val stTodo = findViewById<CheckBox>(R.id.st_todo)
        val stInProgress = findViewById<CheckBox>(R.id.st_inprogress)
        val stBlocked = findViewById<CheckBox>(R.id.st_blocked)
        val stNeedReqs = findViewById<CheckBox>(R.id.st_needreqs)
        val stDone = findViewById<CheckBox>(R.id.st_done)
        val stCodeReview = findViewById<CheckBox>(R.id.st_codereview)
        val stTesting = findViewById<CheckBox>(R.id.st_testing)
        val stQA = findViewById<CheckBox>(R.id.st_qa)

        // === Profilo iniziale ===
        qaCheck.isChecked = prefs.profileType == "QA"
        devCheck.isChecked = prefs.profileType == "DEV"
        squadModeCheck.isChecked = prefs.enableSquadMode
        squadModeBlock.visibility = if (prefs.profileType == "DEV") View.VISIBLE else View.GONE

        qaCheck.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                devCheck.isChecked = false
                prefs.profileType = "QA"
                squadModeBlock.visibility = View.GONE
            }
        }
        
        devCheck.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                qaCheck.isChecked = false
                prefs.profileType = "DEV"
                squadModeBlock.visibility = View.VISIBLE
            } else {
                squadModeBlock.visibility = View.GONE
            }
        }

        squadModeCheck.setOnCheckedChangeListener { _, checked ->
            prefs.enableSquadMode = checked
            squadKeywordsEdit.visibility = if (checked) View.VISIBLE else View.GONE
            keywordsChipGroup.visibility = if (checked) View.VISIBLE else View.GONE
        }
        
        squadKeywordsEdit.visibility = if (prefs.enableSquadMode) View.VISIBLE else View.GONE
        keywordsChipGroup.visibility = if (prefs.enableSquadMode) View.VISIBLE else View.GONE

        fun renderKeywordChips(words: List<String>) {
            keywordsChipGroup.removeAllViews()
            words.forEach { w ->
                val chip = Chip(this).apply {
                    text = w
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        val updated = Prefs(this@SettingsActivity).getSquadKeywords().filter { it != w }
                        Prefs(this@SettingsActivity).setSquadKeywords(updated)
                        renderKeywordChips(updated)
                    }
                }
                keywordsChipGroup.addView(chip)
            }
        }

        renderKeywordChips(Prefs(this).getSquadKeywords())

        fun addKeywordFromInput() {
            val raw = squadKeywordsEdit.text.toString().trim()
            if (raw.isEmpty()) return
            val list = Prefs(this).getSquadKeywords().toMutableList()
            if (!list.contains(raw)) {
                list.add(raw)
                Prefs(this).setSquadKeywords(list)
                renderKeywordChips(list)
            }
            squadKeywordsEdit.setText("")
        }

        addKeywordBtn.setOnClickListener { addKeywordFromInput() }
        squadKeywordsEdit.setOnEditorActionListener { _, _, _ -> addKeywordFromInput(); true }

        fun renderCodeChips(codes: List<String>) {
            val grp = findViewById<ChipGroup>(R.id.searchCodesChipGroup)
            grp.removeAllViews()
            codes.forEach { num ->
                val chip = Chip(this).apply {
                    text = "FGC-$num"
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        val updated = Prefs(this@SettingsActivity).getSearchCodes().filter { it != num }
                        Prefs(this@SettingsActivity).setSearchCodes(updated)
                        renderCodeChips(updated)
                    }
                }
                grp.addView(chip)
            }
        }

        fun renderEpicChips(codes: List<String>) {
            squadEpicChipGroup.removeAllViews()
            codes.forEach { num ->
                val chip = Chip(this).apply {
                    text = "FGC-$num"
                    isCloseIconVisible = true
                    setOnCloseIconClickListener {
                        val updated = Prefs(this@SettingsActivity).getSquadEpics().filter { it != num }
                        Prefs(this@SettingsActivity).setSquadEpics(updated)
                        renderEpicChips(updated)
                    }
                }
                squadEpicChipGroup.addView(chip)
            }
        }

        renderCodeChips(Prefs(this).getSearchCodes())
        renderEpicChips(Prefs(this).getSquadEpics())

        findViewById<ImageButton>(R.id.addSearchCodeBtn).setOnClickListener {
            val raw = findViewById<EditText>(R.id.searchCodeInput).text.toString().trim()
            val onlyDigits = raw.replace("\\D+".toRegex(), "")
            if (onlyDigits.isEmpty()) return@setOnClickListener
            val list = Prefs(this).getSearchCodes().toMutableList()
            if (!list.contains(onlyDigits)) {
                list.add(onlyDigits)
                Prefs(this).setSearchCodes(list)
                renderCodeChips(list)
            }
            findViewById<EditText>(R.id.searchCodeInput).setText("")
        }

        addSquadEpicBtn.setOnClickListener {
            val raw = squadEpicInput.text.toString().trim()
            val onlyDigits = raw.replace("\\D+".toRegex(), "")
            if (onlyDigits.isEmpty()) return@setOnClickListener
            val list = Prefs(this).getSquadEpics().toMutableList()
            if (!list.contains(onlyDigits)) {
                list.add(onlyDigits)
                Prefs(this).setSquadEpics(list)
                renderEpicChips(list)
            }
            squadEpicInput.setText("")
        }

        // === Campi esistenti ===
        baseUrl.setText(prefs.baseUrl)
        email.setText(prefs.email)
        token.setText(prefs.token)
        jql.setText(prefs.jql)

        val (h0, m0) = prefs.getHourMinute()
        timePicker.setIs24HourView(true)
        if (Build.VERSION.SDK_INT >= 23) { timePicker.hour = h0; timePicker.minute = m0 }
        else { timePicker.currentHour = h0; timePicker.currentMinute = m0 }

        findViewById<ImageButton>(R.id.infoTokenBtn).setOnClickListener {
            val url = "https://youtu.be/X1F5LfCuq6I"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // === Advanced defaults ===
        forceTestCardCheck.isChecked = prefs.forceTestCard
        enableQueueLockCheck.isChecked = prefs.enableQueueLock
        enableWeekendCheck.isChecked = prefs.enableWeekendNotifications

        stTodo.isChecked = prefs.stToDo
        stInProgress.isChecked = prefs.stInProgress
        stBlocked.isChecked = prefs.stBlocked
        stNeedReqs.isChecked = prefs.stNeedReqs
        stDone.isChecked = prefs.stDone
        stCodeReview.isChecked = prefs.stCodeReview
        stTesting.isChecked = prefs.stTesting
        stQA.isChecked = prefs.stQA

        // Test connessione
        testBtn.setOnClickListener {
            val bu = baseUrl.text.toString().trim()
            val em = email.text.toString().trim()
            val tk = token.text.toString().trim()
            if (bu.isEmpty() || em.isEmpty() || tk.isEmpty()) {
                testBtn.text = getString(R.string.settings_fill_required)
                return@setOnClickListener
            }
            testBtn.text = getString(R.string.settings_testing)
            launch {
                try {
                    val ok = withContext(Dispatchers.IO) {
                        com.pointdown.app.data.JiraClient(bu, em, tk).testAuth()
                    }
                    testBtn.text = if (ok) getString(R.string.settings_conn_ok)
                    else getString(R.string.settings_auth_fail)
                } catch (e: Exception) {
                    testBtn.text = "❌ Erro: ${e.message}"
                }
            }
        }

        // Salvataggio
        saveBtn.setOnClickListener {
            val p = Prefs(this)
            p.baseUrl = baseUrl.text.toString().trim()
            p.email = email.text.toString().trim()
            p.token = token.text.toString().trim()
            p.jql = jql.text.toString().trim()

            val h = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
            val m = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute

            p.profileType = if (devCheck.isChecked) "DEV" else "QA"
            p.enableSquadMode = squadModeCheck.isChecked
            p.forceTestCard = forceTestCardCheck.isChecked
            p.enableQueueLock = enableQueueLockCheck.isChecked
            p.enableWeekendNotifications = enableWeekendCheck.isChecked

            // persist status
            p.stToDo = stTodo.isChecked
            p.stInProgress = stInProgress.isChecked
            p.stBlocked = stBlocked.isChecked
            p.stNeedReqs = stNeedReqs.isChecked
            p.stDone = stDone.isChecked
            p.stCodeReview = stCodeReview.isChecked
            p.stTesting = stTesting.isChecked
            p.stQA = stQA.isChecked

            p.alarmTime = "%02d:%02d".format(h, m)
            AlarmScheduler.scheduleDaily(this, h, m, enableWeekendCheck.isChecked)

            saveBtn.text = getString(R.string.settings_saved_ok)
        }

        advancedBtn.setOnClickListener {
            advancedSection.visibility =
                if (advancedSection.visibility == View.GONE) View.VISIBLE else View.GONE
        }
    }
}
