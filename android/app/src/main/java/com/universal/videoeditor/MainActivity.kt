package com.universal.videoeditor
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var loadingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvProgressLabel: TextView
    private lateinit var tvProgressDetail: TextView
    private lateinit var tvStatus: TextView

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        tvProgressDetail = findViewById(R.id.tvProgressDetail)
        tvStatus = findViewById(R.id.tvStatus)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etDur = findViewById<EditText>(R.id.etDuration)
        val spQ = findViewById<Spinner>(R.id.spQuality)
        val spW = findViewById<Spinner>(R.id.spWatermark)
        val spT = findViewById<Spinner>(R.id.spType)
        val btnProcess = findViewById<AppCompatButton>(R.id.btnProcess)

        // Spinner adapters
        fun mk(items: List<String>) = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item, items)
        spQ.adapter = mk(listOf("360p","480p","720p","1080p"))
        spW.adapter = mk(listOf("remove","skip"))
        spT.adapter = mk(listOf("video","reels"))
        spQ.setSelection(2) // 720p default

        // Request permissions on first launch
        requestPermissionsIfNeeded()

        // Menu
        findViewById<View>(R.id.menuInstructions).setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
        findViewById<View>(R.id.menuResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<View>(R.id.menuCredit).setOnClickListener {
            startActivity(Intent(this, CreditActivity::class.java))
        }

        // Process button
        btnProcess.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Tempel link video dulu ya 🙏", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dur = etDur.text.toString().ifBlank { "60" }
            processVideo(url, dur,
                spQ.selectedItem.toString(),
                spW.selectedItem.toString(),
                spT.selectedItem.toString())
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VIDEO")
                != PackageManager.PERMISSION_GRANTED)
                perms.add("android.permission.READ_MEDIA_VIDEO")
            if (ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_IMAGES")
                != PackageManager.PERMISSION_GRANTED)
                perms.add("android.permission.READ_MEDIA_IMAGES")
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
        }
    }

    private fun showProgress(pct: Int, label: String, detail: String = "") {
        loadingOverlay.visibility = View.VISIBLE
        progressBar.progress = pct
        tvProgress.text = "$pct%"
        tvProgressLabel.text = label
        tvProgressDetail.text = detail
    }

    private fun hideProgress() {
        loadingOverlay.visibility = View.GONE
    }

    private fun processVideo(url: String, dur: String, quality: String,
                             watermark: String, type: String) {
        showProgress(5, "Memulai…", "Menghubungi server")
        lifecycleScope.launch {
            val inputs = mapOf(
                "video_url" to url,
                "part_duration" to dur,
                "quality" to quality,
                "watermark_mode" to watermark,
                "upload_type" to type
            )
            val r = WorkflowHelper.startProcess(this@MainActivity, inputs)
            if (!r.ok) {
                hideProgress()
                Toast.makeText(this@MainActivity,
                    "❌ Gagal memulai proses (${r.code})", Toast.LENGTH_LONG).show()
                return@launch
            }
            showProgress(15, "Video dikirim", "Menunggu proses di server")
            tvStatus.text = "Sedang memproses…"

            // Poll status
            var pct = 15
            for (i in 0 until 120) {
                delay(5000)
                pct = minOf(15 + i * 3, 70)
                showProgress(pct, "Memproses video…", "Langkah ${i+1}")
                val run = WorkflowHelper.latestRun(this@MainActivity) ?: continue
                val status = run.optString("status", "")
                val conclusion = run.optString("conclusion", "")
                if (status == "completed") {
                    if (conclusion == "success") {
                        showProgress(80, "Mengambil hasil…", "Menunggu artifact")
                        val runId = run.optLong("id", -1L)
                        downloadResult(runId)
                    } else {
                        hideProgress()
                        tvStatus.text = "Gagal ❌"
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Proses Gagal")
                            .setMessage("Video tidak dapat diproses. Coba lagi dengan link yang berbeda.")
                            .setPositiveButton("OK", null).show()
                    }
                    return@launch
                }
            }
            hideProgress()
            Toast.makeText(this@MainActivity,
                "⏱️ Proses terlalu lama. Cek Hasil Video nanti.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun downloadResult(runId: Long) {
        val arts = WorkflowHelper.runArtifacts(this, runId)
        if (arts.isEmpty()) {
            delay(5000)
            val retry = WorkflowHelper.runArtifacts(this, runId)
            if (retry.isEmpty()) {
                hideProgress()
                Toast.makeText(this, "✅ Selesai! Cek di menu Hasil Video.", Toast.LENGTH_LONG).show()
                return
            }
            return
        }
        showProgress(85, "Mengunduh…", "Mohon tunggu")
        val (name, id, _) = arts.first()
        val bytes = WorkflowHelper.downloadArtifact(this, id)
        if (bytes == null) {
            hideProgress()
            Toast.makeText(this, "⚠️ Gagal unduh hasil. Coba cek Hasil Video.", Toast.LENGTH_LONG).show()
            return
        }

        // Extract zip
        showProgress(95, "Menyimpan…", "Ke folder Downloads/YadClipper")
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                YadApp.DOWNLOAD_DIR)
            if (!dir.exists()) dir.mkdirs()

            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(dir, entry.name)
                    outFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) {
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            showProgress(100, "Selesai! 🎉", "Video tersimpan di Downloads/YadClipper")
            delay(2000)
            hideProgress()
            tvStatus.text = "Terakhir: ${name.take(20)}…"

            AlertDialog.Builder(this)
                .setTitle("✅ Berhasil!")
                .setMessage("Video sudah tersimpan di:\nDownloads/YadClipper\n\nBuka menu Hasil Video untuk melihatnya.")
                .setPositiveButton("Lihat Hasil") { _, _ ->
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
                .setNegativeButton("Tutup", null)
                .show()
        } catch (e: Exception) {
            hideProgress()
            Toast.makeText(this, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
