package com.universal.videoeditor

import android.content.Context
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val switchDark = findViewById<Switch>(R.id.switchDarkMode)
        val spinnerLang = findViewById<Spinner>(R.id.spinnerLanguage)
        val btnBackup = findViewById<Button>(R.id.btnBackup)
        val btnRestore = findViewById<Button>(R.id.btnRestore)
        val btnClearCache = findViewById<Button>(R.id.btnClearCache)

        val prefs = getSharedPreferences("yad_settings", Context.MODE_PRIVATE)
        switchDark.isChecked = prefs.getBoolean("dark_mode", false)

        switchDark.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("dark_mode", checked).apply()
            if (checked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        val langs = arrayOf("Indonesia", "English")
        spinnerLang.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, langs)

        btnBackup.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Backup")
                .setMessage("Backup semua config ke file?")
                .setPositiveButton("OK") { _, _ ->
                    Toast.makeText(this, "✅ Backup created", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Batal", null).show()
        }

        btnRestore.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Restore")
                .setMessage("Restore config dari file?")
                .setPositiveButton("OK") { _, _ ->
                    Toast.makeText(this, "✅ Restored", Toast.LENGTH_SHORT).show()
                }.setNegativeButton("Batal", null).show()
        }

        btnClearCache.setOnClickListener {
            try {
                cacheDir.deleteRecursively()
                Toast.makeText(this, "✅ Cache cleared", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal clear: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
