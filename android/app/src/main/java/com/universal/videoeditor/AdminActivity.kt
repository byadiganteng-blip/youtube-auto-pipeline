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
 * Admin Panel
 * Created by KARYADI, Coding by KARYADI
 */
class AdminActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_admin)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout error", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        try {
            findViewById<TextView>(R.id.tvAdminEmail)?.text = "Email: ${SecureConfig.getAdminEmail()}"
            findViewById<TextView>(R.id.tvAdminLogin)?.text =
                "Login: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date())}"
        } catch (_: Exception) {}

        setupBtn("btnPushUpdate") {
            lifecycleScope.launch {
                val (ok, msg) = AdminApi.triggerBuild("build-apk.yml", "main")
                Toast.makeText(this@AdminActivity, if (ok) "OK: $msg" else "FAIL: $msg",
                    Toast.LENGTH_LONG).show()
            }
        }
        setupBtn("btnForceUpdate") {
            AlertDialog.Builder(this)
                .setTitle("Force Update")
                .setMessage("Trigger build ulang?")
                .setPositiveButton("Ya") { _, _ ->
                    lifecycleScope.launch {
                        AdminApi.triggerBuild("build-apk.yml", "main")
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
        setupBtn("btnManageTokens") { showTokenDialog() }
        setupBtn("btnViewLogs") {
            lifecycleScope.launch {
                val logs = AdminApi.getLatestLogs()
                AlertDialog.Builder(this)
                    .setTitle("Latest Logs")
                    .setMessage(logs)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        setupBtn("btnViewDevices") {
            val info = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\n" +
                       "Android: ${android.os.Build.VERSION.RELEASE}"
            AlertDialog.Builder(this)
                .setTitle("Device Info")
                .setMessage(info)
                .setPositiveButton("OK", null)
                .show()
        }
        setupBtn("btnLogout") {
            SecureConfig.clearAdmin()
            finish()
        }
    }

    private fun setupBtn(idName: String, action: () -> Unit) {
        try {
            val resId = resources.getIdentifier(idName, "id", packageName)
            if (resId != 0) {
                findViewById<Button>(resId)?.setOnClickListener { action() }
            }
        } catch (_: Exception) {}
    }

    private fun showTokenDialog() {
        val input = EditText(this)
        input.hint = "ghp_..."
        AlertDialog.Builder(this)
            .setTitle("Manage Token")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) SecureConfig.setGithubToken(t)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
