package com.universal.videoeditor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
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

    private lateinit var tvStatus: TextView
    private lateinit var tvDurationValue: TextView
    private lateinit var tvDurationHint: TextView
    private lateinit var sbDuration: SeekBar
    private var videoDurationSec: Int = 0

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        LogTracker.i(this, "Main", "onCreate")

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
        spProcessMode.setSelection(1)
        spMethod.setSelection(0)
        spVideoSize.setSelection(1)
        spQuality.setSelection(0)
        spType.setSelection(0)

        sbDuration.max = 600
        sbDuration.progress = 60
        updateDurationLabel(60)

        sbDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val actual = if (progress < 10) 10 else progress
                updateDurationLabel(actual)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        etUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val url = etUrl.text.toString().trim()
                if (url.isNotEmpty() && url.startsWith("http")) detectDuration(url)
            }
        }

        requestPermissionsIfNeeded()

        findViewById<View>(R.id.menuInstructions).setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
        findViewById<View>(R.id.menuResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<View>(R.id.menuCredit).setOnClickListener {
            startActivity(Intent(this, CreditActivity::class.java))
        }

        btnProcess.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Tempel link video dulu ya 🙏", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // Cek izin overlay
            if (!canDrawOverlay()) {
                askOverlayPermission()
                return@setOnClickListener
            }
            val inputs = mapOf(
                "video_url" to url,
                "process_mode" to spProcessMode.selectedItem.toString(),
                "method" to spMethod.selectedItem.toString(),
                "video_size" to spVideoSize.selectedItem.toString(),
                "video_quality" to spQuality.selectedItem.toString(),
                "part_duration" to sbDuration.progress.coerceAtLeast(10).toString(),
                "upload_type" to spType.selectedItem.toString()
            )
            processVideo(inputs)
        }
    }

    private fun canDrawOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this)
        else true

    private fun askOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Izin Floating Window")
            .setMessage("Aktifkan izin 'Tampil di atas aplikasi lain' supaya progres bisa dilihat saat Anda buka app lain.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                val i = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName"))
                startActivity(i)
            }
            .setNegativeButton("Batal", null).show()
    }

    private fun updateDurationLabel(sec: Int) {
        val label = when {
            sec < 60 -> "${sec} dtk"
            sec % 60 == 0 -> "${sec / 60} mnt"
            else -> "${sec / 60}m ${sec % 60}s"
        }
        tvDurationValue.text = label
        if (videoDurationSec > 0) {
            val parts = (videoDurationSec + sec - 1) / sec
            tvDurationHint.text = "Video ${videoDurationSec}s → ~${parts} part"
        }
    }

    private fun detectDuration(url: String) {
        tvDurationHint.text = "Mendeteksi durasi…"
        lifecycleScope.launch {
            val r = ProcessRunner.probeVideoDuration(this@MainActivity, url)
            if (r != null && r > 0) {
                videoDurationSec = r
                tvDurationHint.text = "Video: ${r}s (${r/60}m ${r%60}s)"
                val suggest = (r / 10).coerceIn(10, 300)
                sbDuration.progress = suggest
                updateDurationLabel(suggest)
            } else {
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
        FloatingProgressService.show(this, pct, label, detail)
        tvStatus.text = "$pct% • $label"
    }

    private fun updateProgress(pct: Int, label: String, detail: String = "") {
        FloatingProgressService.update(this, pct, label, detail)
        tvStatus.text = "$pct% • $label"
    }

    private fun hideProgress() {
        FloatingProgressService.hide(this)
    }

    private fun processVideo(inputs: Map<String, String>) {
        LogTracker.i(this, "Main", "Process: $inputs")
        showProgress(5, "Memulai…", "Menghubungi server")
        lifecycleScope.launch {
            val r = ProcessRunner.startProcess(this@MainActivity, inputs)
            if (!r.ok) {
                hideProgress()
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("❌ Gagal Memulai")
                    .setMessage("Kode: ${r.code}\n\n${r.body.take(300)}")
                    .setPositiveButton("OK", null).show()
                return@launch
            }
            updateProgress(15, "Video dikirim", "Menunggu proses")

            for (i in 0 until 240) {
                delay(5000)
                val pct = minOf(15 + i * 2, 70)
                updateProgress(pct, "Memproses video…", "Langkah ${i+1} / 240")
                val run = ProcessRunner.latestRun(this@MainActivity) ?: continue
                if (run.optString("status") == "completed") {
                    val conclusion = run.optString("conclusion")
                    if (conclusion == "success") {
                        updateProgress(80, "Mengambil hasil…", "Menunggu artifact")
                        downloadResult(run.optLong("id", -1L))
                    } else {
                        hideProgress()
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("Proses Gagal")
                            .setMessage("Cek log di menu Instruksi.")
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
        updateProgress(85, "Mengunduh…", "Mohon tunggu")
        val (name, id, _) = useArts.first()
        val bytes = ProcessRunner.downloadArtifact(this, id)
        if (bytes == null) {
            hideProgress()
            Toast.makeText(this, "⚠️ Gagal unduh.", Toast.LENGTH_LONG).show()
            return
        }
        updateProgress(95, "Menyimpan…", "Downloads/${YadApp.DOWNLOAD_DIR}")
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
            updateProgress(100, "Selesai! 🎉", "Downloads/${YadApp.DOWNLOAD_DIR}")
            delay(2500)
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
            Toast.makeText(this, "⚠️ ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
