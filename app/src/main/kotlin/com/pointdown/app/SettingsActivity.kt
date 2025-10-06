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
        val testCardCheck = findViewById<CheckBox>(R.id.forceTestCardCheck)
        val testIssueKeyEdit = findViewById<EditText>(R.id.testIssueKeyEdit)
        val queueLockCheck = findViewById<CheckBox>(R.id.enableQueueLockCheck)
        val timePicker = findViewById<TimePicker>(R.id.timePicker)
        val status = findViewById<TextView>(R.id.statusTextSettings)
        val testBtn = findViewById<Button>(R.id.testBtn)
        val saveBtn = findViewById<Button>(R.id.saveBtn)
        val infoBtn = findViewById<ImageButton>(R.id.infoTokenBtn)

        baseUrl.setText(prefs.baseUrl)
        email.setText(prefs.email)
        token.setText(prefs.token)
        jql.setText(prefs.jql)

        testCardCheck.isChecked = prefs.forceTestCard
        testIssueKeyEdit.setText(prefs.testIssueKey ?: "FGC-9683")

        queueLockCheck.isChecked = prefs.enableQueueLock

        val (h0,m0) = prefs.getHourMinute()
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

        saveBtn.setOnClickListener {
            val bu = baseUrl.text.toString().trim()
            val em = email.text.toString().trim()
            val tk = token.text.toString().trim()
            val jq = jql.text.toString().trim()
            val force = testCardCheck.isChecked
            val testKey = testIssueKeyEdit.text.toString().trim().ifBlank { "FGC-9683" }
            val enableQueueLock = queueLockCheck.isChecked

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
            p.forceTestCard = force
            p.testIssueKey = testKey
            p.alarmTime = "%02d:%02d".format(h, m)
            p.enableQueueLock = enableQueueLock

            AlarmScheduler.scheduleDaily(this, h, m)
            status.text = getString(R.string.settings_saved_ok)
        }
    }
}
