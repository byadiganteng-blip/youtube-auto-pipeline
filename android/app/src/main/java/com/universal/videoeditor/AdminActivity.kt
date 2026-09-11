package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminActivity : AppCompatActivity() {

    private val workflowPath = ".github/workflows/build-apk.yml"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val email = SecureConfig.getAdminEmail()
        findViewById<TextView>(R.id.tvAdminEmail).text = "Email: $email"
        findViewById<TextView>(R.id.tvAdminLogin).text =
            "Login: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"

        // ---------- PUSH UPDATE ----------
        findViewById<Button>(R.id.btnPushUpdate).setOnClickListener {
            lifecycleScope.launch {
                toast("⏳ Triggering...")
                val (ok, msg) = AdminApi.triggerBuild("build-apk.yml", "main")
                toast(if (ok) "✅ $msg" else "❌ $msg")
            }
        }

        // ---------- FORCE UPDATE ----------
        findViewById<Button>(R.id.btnForceUpdate).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Force Update")
                .setMessage("Trigger build ulang & hapus cache?")
                .setPositiveButton("Ya") { _, _ ->
                    lifecycleScope.launch {
                        // Trigger workflow "pipeline.yml" (kalau ada) atau build-apk.yml
                        val (ok, msg) = AdminApi.triggerBuild("build-apk.yml", "main")
                        toast(if (ok) "✅ Force update dipicu" else "❌ $msg")
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        // ---------- TAMBAH FITUR ----------
        findViewById<Button>(R.id.btnTambahFitur).setOnClickListener {
            startActivity(Intent(this, FeatureAdderActivity::class.java))
        }

        // ---------- MANAGE TOKENS ----------
        findViewById<Button>(R.id.btnManageTokens).setOnClickListener {
            showTokenDialog()
        }

        // ---------- EDIT WORKFLOW ----------
        findViewById<Button>(R.id.btnEditWorkflow).setOnClickListener {
            startActivity(Intent(this, TextEditorActivity::class.java).apply {
                putExtra(TextEditorActivity.EXTRA_PATH, workflowPath)
                putExtra(TextEditorActivity.EXTRA_TITLE, "Edit build-apk.yml")
            })
        }

        // ---------- VIEW LOGS ----------
        findViewById<Button>(R.id.btnViewLogs).setOnClickListener {
            startActivity(Intent(this, LogsActivity::class.java))
        }

        // ---------- VIEW DEVICES ----------
        findViewById<Button>(R.id.btnViewDevices).setOnClickListener {
            // Tahap 2 — sementara tampilkan info device ini
            val info = "Device ID: ${android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID)}\n" +
                       "Model: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                       "Android: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
            AlertDialog.Builder(this)
                .setTitle("📱 Device Ini")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }

        // ---------- BROADCAST ----------
        findViewById<Button>(R.id.btnBroadcast).setOnClickListener {
            startActivity(Intent(this, BroadcastActivity::class.java))
        }

        // ---------- LOGOUT ----------
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Keluar dari admin mode?")
                .setPositiveButton("Ya") { _, _ ->
                    SecureConfig.clearAdmin()
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun showTokenDialog() {
        val current = SecureConfig.getGithubToken()
        val masked = if (current.length > 8) "${current.take(8)}...${current.takeLast(4)}" else "(kosong)"
        val input = android.widget.EditText(this).apply {
            hint = "ghp_xxxxxxxxxxxx"
            setText("")
        }
        AlertDialog.Builder(this)
            .setTitle("🔑 Manage Token")
            .setMessage("Token saat ini: $masked\n\nMasukkan token baru (kosongkan untuk hapus):")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newTok = input.text.toString().trim()
                if (newTok.isEmpty()) {
                    SecureConfig.clearGithubToken()
                    toast("Token dihapus")
                } else {
                    SecureConfig.setGithubToken(newTok)
                    toast("Token disimpan")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
