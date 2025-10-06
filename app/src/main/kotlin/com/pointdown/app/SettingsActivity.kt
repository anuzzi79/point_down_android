package com.pointdown.app

import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.pointdown.app.alarm.AlarmScheduler
import com.pointdown.app.data.JiraClient
import com.pointdown.app.data.Prefs
import kotlinx.coroutines.*

import android.content.Intent
import android.net.Uri
import android.view.View

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
        val status = findViewById<TextView>(R.id.statusTextSettings)

        val advancedBtn = findViewById<Button>(R.id.advancedBtn)
        val advancedSection = findViewById<LinearLayout>(R.id.advancedSection)
        val testCardCheck = findViewById<CheckBox>(R.id.forceTestCardCheck)
        val testIssueKeyEdit = findViewById<EditText>(R.id.testIssueKeyEdit)
        val queueLockCheck = findViewById<CheckBox>(R.id.enableQueueLockCheck)
        val weekendCheck = findViewById<CheckBox>(R.id.enableWeekendCheck)

        val testBtn = findViewById<Button>(R.id.testBtn)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val infoBtn = findViewById<ImageButton>(R.id.infoTokenBtn)

        // Campi base
        baseUrl.setText(prefs.baseUrl)
        email.setText(prefs.email)
        token.setText(prefs.token)
        jql.setText(prefs.jql)

        // Ora (24h) – blocco subito sotto JQL
        val (h0, m0) = prefs.getHourMinute()
        timePicker.setIs24HourView(true)
        if (Build.VERSION.SDK_INT >= 23) {
            timePicker.hour = h0; timePicker.minute = m0
        } else {
            timePicker.currentHour = h0; timePicker.currentMinute = m0
        }

        // 🔗 Aiuto: apre il video YouTube con le istruzioni del token
        infoBtn.setOnClickListener {
            val url = "https://youtu.be/X1F5LfCuq6I"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }

        // Bottone "Avançadas": toggle mostra/nascondi.
        // Alla PRIMA apertura imposta tutte le opzioni su ON e precompila la issue key di test.
        var advancedInitialized = false
        advancedBtn.setOnClickListener {
            if (advancedSection.visibility == View.VISIBLE) {
                advancedSection.visibility = View.GONE
            } else {
                advancedSection.visibility = View.VISIBLE
                if (!advancedInitialized) {
                    testCardCheck.isChecked = true
                    queueLockCheck.isChecked = true
                    weekendCheck.isChecked = true
                    if (testIssueKeyEdit.text.isNullOrBlank()) {
                        testIssueKeyEdit.setText(prefs.testIssueKey ?: "FGC-9683")
                    }
                    advancedInitialized = true
                }
            }
        }

        // Test connessione
        testBtn.setOnClickListener {
            status.text = getString(R.string.settings_testing)
            val bu = baseUrl.text.toString().trim()
            val em = email.text.toString().trim()
            val tk = token.text.toString().trim()
            if (bu.isEmpty() || em.isEmpty() || tk.isEmpty()) {
                status.text = getString(R.string.settings_fill_required)
                return@setOnClickListener
            }
            launch {
                try {
                    val ok = withContext(Dispatchers.IO) { JiraClient(bu, em, tk).testAuth() }
                    status.text = if (ok) getString(R.string.settings_conn_ok) else getString(R.string.settings_auth_fail)
                } catch (e: Exception) {
                    status.text = "❌ Erro: ${e.message}"
                }
            }
        }

        // Salvataggio
        saveBtn.setOnClickListener {
            val bu = baseUrl.text.toString().trim()
            val em = email.text.toString().trim()
            val tk = token.text.toString().trim()
            val jq = jql.text.toString().trim()

            if (bu.isEmpty() || em.isEmpty() || tk.isEmpty()) {
                status.text = getString(R.string.settings_required_missing)
                return@setOnClickListener
            }

            val h = if (Build.VERSION.SDK_INT >= 23) timePicker.hour else timePicker.currentHour
            val m = if (Build.VERSION.SDK_INT >= 23) timePicker.minute else timePicker.currentMinute

            val p = Prefs(this)
            p.baseUrl = bu
            p.email = em
            p.token = tk
            p.jql = jq

            // Opzioni avanzate
            p.forceTestCard = testCardCheck.isChecked
            p.testIssueKey = (testIssueKeyEdit.text?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: "FGC-9683")
            p.enableQueueLock = queueLockCheck.isChecked
            p.enableWeekendNotifications = weekendCheck.isChecked

            p.alarmTime = "%02d:%02d".format(h, m)

            AlarmScheduler.scheduleDaily(this, h, m, p.enableWeekendNotifications)
            status.text = getString(R.string.settings_saved_ok)
        }
    }
}
