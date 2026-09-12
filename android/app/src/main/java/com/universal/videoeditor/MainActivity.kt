package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

/**
 * Main Activity
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

        // Setup semua tombol menu
        val buttonMap = mapOf(
            "btnAdmin" to AdminActivity::class.java,
            "btnFiles" to FilesActivity::class.java,
            "btnActions" to ActionsActivity::class.java,
            "btnEditor" to VideoEditorActivity::class.java,
            "btnInstructions" to InstructionsActivity::class.java,
            "btnCredit" to CreditActivity::class.java,
            "btnStatistics" to StatisticsActivity::class.java,
            "btnSettings" to SettingsActivity::class.java,
        )

        for ((idName, activityClass) in buttonMap) {
            val resId = resources.getIdentifier(idName, "id", packageName)
            if (resId != 0) {
                try {
                    val btn = findViewById<Button>(resId)
                    btn?.setOnClickListener {
                        try {
                            startActivity(Intent(this, activityClass))
                        } catch (e: Exception) {
                            Toast.makeText(this,
                                "Buka ${activityClass.simpleName} gagal: ${e.message}",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (_: Exception) {
                    // Skip button yang tidak ada
                }
            }
        }

        // Check update (safe)
        try {
            val checker = UpdateChecker(this)
            checker.onUpdateAvailable()
        } catch (e: Exception) {
            // UpdateChecker gagal, skip
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
