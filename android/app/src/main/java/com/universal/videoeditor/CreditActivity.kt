package com.universal.videoeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class CreditActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_credit)
        LogTracker.i(this, "Credit", "onCreate — Credits to YsDev")

        findViewById<AppCompatButton>(R.id.btnSaweria).setOnClickListener {
            LogTracker.i(this, "Credit", "Buka Saweria")
            try {
                val url = getString(R.string.saweria_url)
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (e: Exception) {
                LogTracker.e(this, "Credit", "Saweria open failed: ${e.message}")
                Toast.makeText(this, "Tidak bisa buka browser", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
