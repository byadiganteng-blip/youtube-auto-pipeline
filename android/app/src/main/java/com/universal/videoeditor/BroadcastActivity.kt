package com.universal.videoeditor

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class BroadcastActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broadcast)

        val et = findViewById<EditText>(R.id.etBroadcastMsg)
        findViewById<Button>(R.id.btnSendBroadcast).setOnClickListener {
            val msg = et.text.toString().trim()
            if (msg.isEmpty()) {
                Toast.makeText(this, "Pesan kosong", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            lifecycleScope.launch {
                val (ok, info) = AdminApi.broadcast(msg)
                Toast.makeText(this@BroadcastActivity,
                    if (ok) "✅ $info" else "❌ $info",
                    Toast.LENGTH_LONG).show()
                if (ok) finish()
            }
        }
    }
}
