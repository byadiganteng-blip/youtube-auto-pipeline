package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody

class AdminActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        val tvAdminInfo = findViewById<TextView>(R.id.tvAdminInfo)
        val tvLoginTime = findViewById<TextView>(R.id.tvLoginTime)
        val btnPushUpdate = findViewById<Button>(R.id.btnPushUpdate)
        val btnAddFeature = findViewById<Button>(R.id.btnAddFeature)
        val btnManageTokens = findViewById<Button>(R.id.btnManageTokens)
        val btnBroadcast = findViewById<Button>(R.id.btnBroadcast)
        val btnViewAllDevices = findViewById<Button>(R.id.btnViewAllDevices)
        val btnForceUpdate = findViewById<Button>(R.id.btnForceUpdate)
        val btnEditWorkflow = findViewById<Button>(R.id.btnEditWorkflow)
        val btnViewLogs = findViewById<Button>(R.id.btnViewLogs)
        val btnLogout = findViewById<Button>(R.id.btnLogout)

        tvAdminInfo.text = "👑 ADMIN MODE AKTIF\nEmail: ynuraini686@gmail.com"

        val lt = AdminManager.getLoginTime(this)
        if (lt > 0) {
            val df = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
            tvLoginTime.text = "Login: ${df.format(java.util.Date(lt))}"
        }

        btnPushUpdate.setOnClickListener {
            AlertDialog.Builder(this).setTitle("🚀 Push Update")
                .setMessage("Trigger build APK baru?")
                .setPositiveButton("Push") { _, _ -> triggerBuildApk() }
                .setNegativeButton("Batal", null).show()
        }

        btnForceUpdate.setOnClickListener {
            AlertDialog.Builder(this).setTitle("⚡ Force Update")
                .setMessage("Paksa semua device update?")
                .setPositiveButton("Force") { _, _ -> triggerBuildApk() }
                .setNegativeButton("Batal", null).show()
        }

        btnAddFeature.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/byadiganteng-blip/youtube-auto-pipeline/edit/main/.github/workflows/build-apk.yml")
            })
        }

        btnManageTokens.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/byadiganteng-blip/youtube-auto-pipeline/settings/secrets/actions")
            })
        }

        btnEditWorkflow.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply {
                data = android.net.Uri.parse("https://github.com/byadiganteng-blip/youtube-auto-pipeline/tree/main/.github/workflows")
            })
        }

        btnViewLogs.setOnClickListener {
            val i = Intent(this, ActionsActivity::class.java)
            i.putExtra("token", SecureConfig.getGithubToken())
            startActivity(i)
        }

        btnViewAllDevices.setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }

        btnBroadcast.setOnClickListener {
            Toast.makeText(this, "Coming soon", Toast.LENGTH_SHORT).show()
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Logout Admin")
                .setMessage("Keluar dari mode admin?")
                .setPositiveButton("Ya") { _, _ ->
                    AdminManager.logout(this); finish()
                }.setNegativeButton("Batal", null).show()
        }

        if (AdminManager.shouldAutoLogout(this)) {
            AdminManager.logout(this)
            Toast.makeText(this, "Session expired", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun triggerBuildApk() {
        Thread {
            try {
                val token = SecureConfig.getGithubToken()
                val url = "https://api.github.com/repos/byadiganteng-blip/youtube-auto-pipeline/actions/workflows/build-apk.yml/dispatches"
                val body = okhttp3.RequestBody.create(
                    okhttp3."application/json".toMediaTypeOrNull(), "{\"ref\":\"main\"}")
                val client = okhttp3.OkHttpClient()
                val request = okhttp3.Request.Builder().url(url)
                    .header("Authorization", "token $token")
                    .header("Accept", "application/vnd.github.v3+json")
                    .post(body).build()
                val response = client.newCall(request).execute()
                runOnUiThread {
                    Toast.makeText(this,
                        if (response.code == 204) "✅ Build triggered!" else "Error ${response.code}",
                        Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }.start()
    }
}
