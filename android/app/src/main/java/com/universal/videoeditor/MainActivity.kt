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
    private lateinit var tvProgress: TextView
    private lateinit var tvProgressLabel: TextView
    private lateinit var tvProgressDetail: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDurationValue: TextView
    private lateinit var tvDurationHint: TextView
    private lateinit var sbDuration: SeekBar

    private var videoDurationSec: Int = 0

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        LogTracker.i(this, "Main", "onCreate")

        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvProgress = findViewById(R.id.tvProgress)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        tvProgressDetail = findViewById(R.id.tvProgressDetail)
        tvStatus = findViewById(R.id.tvStatus)
        tvDurationValue = findViewById(R.id.tvDurationValue)
        tvDurationHint = findViewById(R.id.tvDurationHint)
        sbDuration = findViewById(R.id.sbDuration)

        val etUrl = findViewById<EditText>(R.id.etUrl)
        val spProcessMode = findViewById<Spinner>(R.id.spProcessMode)
        val spMethod = findViewById<Spinner>(R.id.spMethod)
        val spVideoSize = findViewById<Spinner>(R.id.spVideoSize)
        val spQuality = findViewById<Spinner>(R.id.spQuality)
        val spType = findViewById<Spinner>(R.id.spType)
        val btnProcess = findViewById<AppCompatButton>(R.id.btnProcess)

        fun mk(items: List<String>) = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)

        spProcessMode.adapter = mk(listOf("remove_watermark", "skip_watermark"))
        spMethod.adapter = mk(listOf("blur", "inpaint"))
        spVideoSize.adapter = mk(listOf("original", "yt_shorts", "tiktok", "ig_reels",
            "fb_reels", "whatsapp_status", "ig_feed_square", "ig_feed_portrait",
            "yt_landscape", "yt_4k", "fb_video", "twitter"))
        spQuality.adapter = mk(listOf("original", "144p", "240p", "360p", "480p",
            "720p", "1080p", "1440p", "2160p"))
        spType.adapter = mk(listOf("video", "reels"))

        spProcessMode.setSelection(0)
        spMethod.setSelection(0)
        spVideoSize.setSelection(0)
        spQuality.setSelection(0)
        spType.setSelection(0)

        // ═══ Duration slider ═══
        sbDuration.max = 600  // 10 menit max
        sbDuration.progress = 60  // default 60s
        updateDurationLabel(60)

        sbDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actual = if (progress < 10) 10 else progress
                updateDurationLabel(actual)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Auto-detect duration saat URL berubah
        etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = etUrl.text.toString().trim()
                if (url.isNotEmpty() && url.startsWith("http")) {
                    detectDuration(url)
                }
            }
        }

        requestPermissionsIfNeeded()

        findViewById<View>(R.id.menuInstructions).setOnClickListener {
            LogTracker.i(this, "Nav", "Buka Instruksi")
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
        findViewById<View>(R.id.menuResults).setOnClickListener {
            LogTracker.i(this, "Nav", "Buka Hasil")
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<View>(R.id.menuCredit).setOnClickListener {
            LogTracker.i(this, "Nav", "Buka Credit")
            startActivity(Intent(this, CreditActivity::class.java))
        }

        btnProcess.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Tempel link video dulu ya 🙏", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dur = sbDuration.progress.coerceAtLeast(10).toString()
            val inputs = mapOf(
                "video_url" to url,
                "process_mode" to spProcessMode.selectedItem.toString(),
                "method" to spMethod.selectedItem.toString(),
                "video_size" to spVideoSize.selectedItem.toString(),
                "video_quality" to spQuality.selectedItem.toString(),
                "part_duration" to dur,
                "upload_type" to spType.selectedItem.toString()
            )
            processVideo(inputs)
        }
    }

    private fun updateDurationLabel(sec: Int) {
        val label = when {
            sec < 60 -> "${sec} dtk"
            sec % 60 == 0 -> "${sec / 60} mnt"
            else -> "${sec / 60}m ${sec % 60}s"
        }
        tvDurationValue.text = label
        // Update hint dengan jumlah part yang dihasilkan
        if (videoDurationSec > 0) {
            val parts = (videoDurationSec + sec - 1) / sec
            tvDurationHint.text = "Video ${videoDurationSec}s → ~${parts} part"
        }
    }

    /**
     * Auto-detect durasi video dari URL.
     * Pakai yt-dlp untuk download metadata (atau fallback ke HEAD request).
     */
    private fun detectDuration(url: String) {
        tvDurationHint.text = "Mendeteksi durasi…"
        LogTracker.i(this, "Detect", "Detecting duration for $url")

        lifecycleScope.launch {
            try {
                // Coba pakai API GitHub untuk yt-dlp metadata via workflow
                // Fallback: kirim ke server dan cek via HEAD
                val r = ProcessRunner.probeVideoDuration(this@MainActivity, url)
                if (r != null && r > 0) {
                    videoDurationSec = r
                    LogTracker.i(this@MainActivity, "Detect", "Duration: ${r}s")
                    tvDurationHint.text = "Video: ${r}s (${r/60}m ${r%60}s)"
                    // Auto-set slider ke ~1/10 durasi atau 60s minimal
                    val suggest = (r / 10).coerceIn(10, 300)
                    sbDuration.progress = suggest
                    updateDurationLabel(suggest)
                } else {
                    tvDurationHint.text = "Video: tidak diketahui"
                }
            } catch (e: Exception) {
                LogTracker.e(this@MainActivity, "Detect", "Failed: ${e.message}")
                tvDurationHint.text = "Video: tidak diketahui"
            }
        }
    }

    private fun requestPermissionsIfNeeded() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
        }
    }

    private fun showProgress(pct: Int, label: String, detail: String = "") {
        loadingOverlay.visibility = View.VISIBLE
        tvProgress.text = "$pct%"
        tvProgressLabel.text = label
        tvProgressDetail.text = detail
    }

    private fun hideProgress() { loadingOverlay.visibility = View.GONE }

    private fun processVideo(inputs: Map<String, String>) {
        LogTracker.i(this, "Main", "Process: $inputs")
        showProgress(5, "Memulai…", "Menghubungi server")
        lifecycleScope.launch {
            val r = ProcessRunner.startProcess(this@MainActivity, inputs)
            if (!r.ok) {
                hideProgress()
                LogTracker.e(this@MainActivity, "Main", "Trigger failed: ${r.code}")
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("❌ Gagal Memulai")
                    .setMessage("Kode: ${r.code}\n\n${r.body.take(300)}\n\nCek log di menu Instruksi.")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            showProgress(15, "Video dikirim", "Menunggu proses")
            tvStatus.text = "Sedang memproses…"

            for (i in 0 until 240) {
                delay(5000)
                val pct = minOf(15 + i * 2, 70)
                showProgress(pct, "Memproses video…", "Langkah ${i+1} / 240")
                val run = ProcessRunner.latestRun(this@MainActivity) ?: continue
                if (run.optString("status") == "completed") {
                    val conclusion = run.optString("conclusion")
                    LogTracker.i(this@MainActivity, "Main", "Run completed: $conclusion")
                    if (conclusion == "success") {
                        showProgress(80, "Mengambil hasil…", "Menunggu artifact")
                        downloadResult(run.optLong("id", -1L))
                    } else {
                        hideProgress()
                        tvStatus.text = "Gagal ❌"
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Proses Gagal")
                            .setMessage("Video tidak dapat diproses. Cek log di menu Instruksi.")
                            .setPositiveButton("OK", null).show()
                    }
                    return@launch
                }
            }
            hideProgress()
            Toast.makeText(this@MainActivity, "⏱️ Proses terlalu lama.", Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun downloadResult(runId: Long) {
        val arts = ProcessRunner.runArtifacts(this, runId)
        val useArts = if (arts.isEmpty()) { delay(5000); ProcessRunner.runArtifacts(this, runId) } else arts
        if (useArts.isEmpty()) {
            hideProgress()
            Toast.makeText(this, "✅ Selesai! Cek Hasil Video.", Toast.LENGTH_LONG).show()
            return
        }
        showProgress(85, "Mengunduh…", "Mohon tunggu")
        val (name, id, _) = useArts.first()
        val bytes = ProcessRunner.downloadArtifact(this, id)
        if (bytes == null) {
            hideProgress()
            Toast.makeText(this, "⚠️ Gagal unduh hasil.", Toast.LENGTH_LONG).show()
            return
        }
        showProgress(95, "Menyimpan…", "Downloads/${YadApp.DOWNLOAD_DIR}")
        try {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), YadApp.DOWNLOAD_DIR)
            if (!dir.exists()) dir.mkdirs()
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val outFile = File(dir, entry.name)
                    outFile.parentFile?.mkdirs()
                    if (!entry.isDirectory) FileOutputStream(outFile).use { zis.copyTo(it) }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            LogTracker.i(this, "Main", "Saved to ${dir.absolutePath}")
            showProgress(100, "Selesai! 🎉", "Downloads/${YadApp.DOWNLOAD_DIR}")
            delay(2000)
            hideProgress()
            tvStatus.text = "Terakhir: ${name.take(20)}…"
            AlertDialog.Builder(this)
                .setTitle("✅ Berhasil!")
                .setMessage("Video tersimpan di:\nDownloads/${YadApp.DOWNLOAD_DIR}/")
                .setPositiveButton("Lihat Hasil") { _, _ ->
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
                .setNegativeButton("Tutup", null).show()
        } catch (e: Exception) {
            hideProgress()
            LogTracker.e(this, "Main", "Save failed: ${e.message}")
            Toast.makeText(this, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
