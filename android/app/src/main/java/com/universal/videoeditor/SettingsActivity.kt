package com.universal.videoeditor

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * Settings
 * Created by KARYADI, Coding by KARYADI
 */
class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_settings)
        } catch (e: Exception) {
            finish(); return
        }

        val etToken = findViewById<EditText>(R.id.etGithubToken)
        val btnSave = findViewById<Button>(R.id.btnSave)

        etToken.setText(SecureConfig.getGithubToken())

        btnSave.setOnClickListener {
            val t = etToken.text.toString().trim()
            if (t.isNotEmpty()) SecureConfig.setGithubToken(t)
            Toast.makeText(this, "Tersimpan", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
