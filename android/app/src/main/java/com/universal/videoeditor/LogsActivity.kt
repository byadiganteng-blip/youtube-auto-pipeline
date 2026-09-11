package com.universal.videoeditor

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class LogsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_logs)
        val tvLogs = findViewById<TextView>(R.id.tvLogs)
        findViewById<Button>(R.id.btnRefreshLogs).setOnClickListener {
            tvLogs.text = "⏳ Loading..."
            lifecycleScope.launch {
                tvLogs.text = AdminApi.getLatestRunLogs()
            }
        }
        // Auto-load pertama kali
        tvLogs.text = "⏳ Loading..."
        lifecycleScope.launch { tvLogs.text = AdminApi.getLatestRunLogs() }
    }
}
