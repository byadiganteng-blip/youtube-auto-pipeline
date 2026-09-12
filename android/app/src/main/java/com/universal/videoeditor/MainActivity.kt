package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity - menu utama
 * Created by KARYADI, Coding by KARYADI
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Cek login admin
        if (!SecureConfig.isAdminLoggedIn()) {
            showLoginDialog()
        }

        // Setup tombol menu
        setupButton("btnFiles", FilesActivity::class.java)
        setupButton("btnActions", ActionsActivity::class.java)
        setupButton("btnAdmin", AdminActivity::class.java)
        setupButton("btnEditor", VideoEditorActivity::class.java)
        setupButton("btnInstructions", InstructionsActivity::class.java)
        setupButton("btnCredit", CreditActivity::class.java)
        setupButton("btnStatistics", StatisticsActivity::class.java)
        setupButton("btnSettings", SettingsActivity::class.java)

        // Tampilkan credit
        try {
            val tvCredit = findViewById<TextView>(R.id.tvCredit)
            tvCredit?.text = "Created by KARYADI, Coding by KARYADI"
        } catch (_: Exception) {}
    }

    private fun setupButton(idName: String, activityClass: Class<*>) {
        try {
            val resId = resources.getIdentifier(idName, "id", packageName)
            if (resId != 0) {
                findViewById<Button>(resId)?.setOnClickListener {
                    try {
                        startActivity(Intent(this, activityClass))
                    } catch (e: Exception) {
                        Toast.makeText(this,
                            "Buka ${activityClass.simpleName} gagal: ${e.message}",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun showLoginDialog() {
        try {
            val view = layoutInflater.inflate(R.layout.dialog_admin_login, null)
            val etEmail = view.findViewById<EditText>(R.id.etEmail)
            val etToken = view.findViewById<EditText>(R.id.etToken)

            AlertDialog.Builder(this)
                .setTitle("Login Admin")
                .setView(view)
                .setCancelable(false)
                .setPositiveButton("Login") { _, _ ->
                    val email = etEmail.text.toString().trim()
                    val token = etToken.text.toString().trim()
                    if (email.isNotEmpty() && token.isNotEmpty()) {
                        SecureConfig.setAdminEmail(email)
                        SecureConfig.setGithubToken(token)
                        Toast.makeText(this, "Login berhasil", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Email & token wajib diisi", Toast.LENGTH_SHORT).show()
                        showLoginDialog()
                    }
                }
                .show()
        } catch (e: Exception) {
            // Skip dialog kalau layout tidak ada
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Keluar?")
                .setMessage("Tutup aplikasi?")
                .setPositiveButton("Ya") { _, _ -> finish() }
                .setNegativeButton("Batal", null)
                .show()
        } catch (_: Exception) {
            super.onBackPressed()
        }
    }
}
