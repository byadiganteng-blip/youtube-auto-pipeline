package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Admin Panel — Created by KARYADI, Coding by KARYADI
 */
class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val email = try { SecureConfig.getAdminEmail() } catch (e: Exception) { "admin@local" }

        safeTextView(R.id.tvAdminEmail)?.text = "Email: $email"
        safeTextView(R.id.tvAdminLogin)?.text =
            "Login: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"

        safeButton(R.id.btnPushUpdate)?.setOnClickListener {
            lifecycleScope.launch {
                toast("⏳ Triggering build...")
                try {
                    val (ok, msg) = AdminApi.triggerBuild("build-apk.yml", "main")
                    toast(if (ok) "✅ $msg" else "❌ $msg")
                } catch (e: Exception) {
                    toast("❌ ${e.message}")
                }
            }
        }

        safeButton(R.id.btnForceUpdate)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Force Update")
                .setMessage("Trigger build ulang?")
                .setPositiveButton("Ya") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val (ok, msg) = AdminApi.triggerBuild("build-apk.yml", "main")
                            toast(if (ok) "✅ Force update dipicu" else "❌ $msg")
                        } catch (e: Exception) {
                            toast("❌ ${e.message}")
                        }
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        safeButton(R.id.btnTambahFitur)?.setOnClickListener {
            try {
                startActivity(Intent(this, FeatureAdderActivity::class.java))
            } catch (e: Exception) {
                toast("Fitur belum siap: ${e.message}")
            }
        }

        safeButton(R.id.btnManageTokens)?.setOnClickListener { showTokenDialog() }

        safeButton(R.id.btnEditWorkflow)?.setOnClickListener {
            try {
                startActivity(Intent(this, TextEditorActivity::class.java).apply {
                    putExtra(TextEditorActivity.EXTRA_PATH, ".github/workflows/build-apk.yml")
                    putExtra(TextEditorActivity.EXTRA_TITLE, "Edit build-apk.yml")
                })
            } catch (e: Exception) {
                toast("Editor belum siap: ${e.message}")
            }
        }

        safeButton(R.id.btnViewLogs)?.setOnClickListener {
            try {
                startActivity(Intent(this, LogsActivity::class.java))
            } catch (e: Exception) {
                toast("Logs belum siap: ${e.message}")
            }
        }

        safeButton(R.id.btnViewDevices)?.setOnClickListener {
            val info = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                       "Android: ${android.os.Build.VERSION.RELEASE} " +
                       "(API ${android.os.Build.VERSION.SDK_INT})"
            AlertDialog.Builder(this)
                .setTitle("📱 Device Info")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }

        safeButton(R.id.btnBroadcast)?.setOnClickListener {
            try {
                startActivity(Intent(this, BroadcastActivity::class.java))
            } catch (e: Exception) {
                toast("Broadcast belum siap: ${e.message}")
            }
        }

        safeButton(R.id.btnLogout)?.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Keluar dari admin mode?")
                .setPositiveButton("Ya") { _, _ ->
                    try { SecureConfig.clearAdmin() } catch (_: Exception) {}
                    finish()
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    private fun safeTextView(id: Int): TextView? = try { findViewById(id) } catch (_: Exception) { null }
    private fun safeButton(id: Int): Button? = try { findViewById(id) } catch (_: Exception) { null }

    private fun showTokenDialog() {
        val current = try { SecureConfig.getGithubToken() } catch (_: Exception) { "" }
        val masked = if (current.length > 8)
            "${current.take(8)}...${current.takeLast(4)}" else "(kosong)"

        val input = EditText(this).apply { hint = "ghp_..." }

        AlertDialog.Builder(this)
            .setTitle("🔑 Manage Token")
            .setMessage("Token saat ini: $masked")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newTok = input.text.toString().trim()
                try {
                    if (newTok.isEmpty()) {
                        SecureConfig.clearGithubToken()
                        toast("Token dihapus")
                    } else {
                        SecureConfig.setGithubToken(newTok)
                        toast("Token disimpan")
                    }
                } catch (e: Exception) {
                    toast("Gagal: ${e.message}")
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun toast(m: String) =
        Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
