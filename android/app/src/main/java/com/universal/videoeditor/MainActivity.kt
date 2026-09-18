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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvDurationValue: TextView
    private lateinit var tvDurationHint: TextView
    private lateinit var sbDuration: SeekBar
    private var videoDurationSec: Int = 0
    private var running = false

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

        // Menu handlers
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
        findViewById<View>(R.id.menuDonate).setOnClickListener {
            LogTracker.i(this, "Nav", "Buka Saweria")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.saweria_url))))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak bisa buka browser", Toast.LENGTH_SHORT).show()
            }
        }

        // ═══ NOTIFIKASI ═══
        val btnNotif = findViewById<View>(R.id.btnNotif)
        val notifDot = findViewById<View>(R.id.notifDot)
        lifecycleScope.launch {
            try {
                val notifs = NotifFetcher.fetch(this@MainActivity)
                if (notifs.isNotEmpty()) {
                    notifDot.visibility = View.VISIBLE
                    LogTracker.i(this@MainActivity, "Main", "Notif loaded: ${notifs.size}")
                }
                btnNotif.setOnClickListener {
                    if (notifs.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Tidak ada notifikasi baru", Toast.LENGTH_SHORT).show()
                    } else {
                        showNotifDialog(notifs)
                        notifDot.visibility = View.GONE
                    }
                }
            } catch (e: Exception) {
                LogTracker.e(this@MainActivity, "Main", "Notif error: ${e.message}")
            }
        }

        btnProcess.setOnClickListener {
            if (running) {
                Toast.makeText(this, "⏳ Proses masih berjalan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this, "Tempel link video dulu ya 🙏", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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

    // ═══════════════════════════════════════════════════════════
    // NOTIF DIALOG — method ini yang hilang sebelumnya
    // ═══════════════════════════════════════════════════════════
    private fun showNotifDialog(notifs: List<NotifFetcher.Notif>) {
        val messages = notifs.joinToString("\n\n") {
            "${it.icon}  ${it.title}\n${it.message}"
        }
        val firstLink = notifs.firstOrNull { it.link.isNotEmpty() }
        val builder = AlertDialog.Builder(this)
            .setTitle("🔔 Notifikasi")
            .setMessage(messages)
            .setPositiveButton("Tutup", null)

        if (firstLink != null) {
            builder.setNeutralButton(firstLink.linkLabel) { _, _ ->
                try {
                    LogTracker.i(this, "Main", "Notif link: ${firstLink.link}")
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(firstLink.link)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak bisa buka link", Toast.LENGTH_SHORT).show()
                }
            }
        }
        builder.show()
    }

    private fun canDrawOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun askOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Izin Floating Window")
            .setMessage("Aktifkan izin 'Tampil di atas aplikasi lain' supaya progres bisa dilihat.")
            .setPositiveButton("Buka Pengaturan") { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
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
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
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
        running = true
        LogTracker.i(this, "Main", "Process: $inputs")
        showProgress(5, "Memulai…", "Menghubungi server")
        lifecycleScope.launch {
            try {
                val r = ProcessRunner.startProcess(this@MainActivity, inputs)
                if (!r.ok) {
                    hideProgress()
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Gagal Memulai")
                        .setMessage("Kode: ${r.code}\n\n${r.body.take(300)}")
                        .setPositiveButton("OK", null).show()
                    return@launch
                }
                updateProgress(10, "Video dikirim", "Menunggu proses di server…")

                for (i in 0 until 360) {
                    delay(5000)
                    val pct = minOf(10 + i / 2, 75)
                    val step = "Menit ${(i * 5 / 60) + 1}"
                    updateProgress(pct, "Memproses video…", "$step • langkah ${i+1}")

                    val run = ProcessRunner.latestRun(this@MainActivity) ?: continue
                    if (run.optString("status") == "completed") {
                        val conclusion = run.optString("conclusion")
                        LogTracker.i(this@MainActivity, "Main", "Run completed: $conclusion")
                        if (conclusion == "success") {
                            updateProgress(80, "Mengambil hasil…", "Menunggu artifact")
                            downloadResult(run.optLong("id", -1L))
                        } else {
                            hideProgress()
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Proses Gagal")
                                .setMessage("Video tidak dapat diproses.\n\nCek menu Instruksi → Log.")
                                .setPositiveButton("OK", null).show()
                        }
                        return@launch
                    }
                }
                hideProgress()
                Toast.makeText(this@MainActivity, "⏱️ Timeout. Cek Hasil Video nanti.", Toast.LENGTH_LONG).show()
            } finally {
                running = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DOWNLOAD RESULT — dengan UNIQUE FOLDER per run
    // ═══════════════════════════════════════════════════════════
    private suspend fun downloadResult(runId: Long) {
        LogTracker.i(this, "Main", "downloadResult runId=$runId")

        // Retry artifact
        var arts: List<ProcessRunner.Artifact> = emptyList()
        for (attempt in 1..6) {
            arts = ProcessRunner.runArtifacts(this, runId)
            val valid = arts.filter { it.sizeBytes > 100_000 }
            if (valid.isNotEmpty()) { arts = valid; break }
            updateProgress(80 + attempt, "Menunggu artifact…", "Percobaan $attempt/6")
            delay(5000)
        }

        if (arts.isEmpty()) {
            hideProgress()
            AlertDialog.Builder(this)
                .setTitle("⚠️ Hasil Tidak Ditemukan")
                .setMessage("Workflow selesai tapi artifact tidak ada.\n\nCek menu Hasil atau log.")
                .setPositiveButton("OK", null).show()
            return
        }

        val target = arts.first()
        LogTracker.i(this, "Main", "Downloading: ${target.name} (${target.sizeBytes/1024} KB)")
        updateProgress(85, "Mengunduh…", "${target.name} (${target.sizeBytes/1024/1024} MB)")

        val bytes = ProcessRunner.downloadArtifact(this, target.id)
        if (bytes == null || bytes.isEmpty()) {
            hideProgress()
            AlertDialog.Builder(this)
                .setTitle("⚠️ Gagal Unduh")
                .setMessage("Artifact tidak dapat diunduh.")
                .setPositiveButton("OK", null).show()
            return
        }

        // ═══ UNIQUE FOLDER: YYYYMMDD_HHmmss ═══
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uniqueFolder = "clip_$ts"

        updateProgress(92, "Menyimpan…", "Downloads/${YadApp.DOWNLOAD_DIR}/$uniqueFolder")
        try {
            val baseDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                YadApp.DOWNLOAD_DIR
            )
            if (!baseDir.exists()) baseDir.mkdirs()

            // Folder unik per run
            val sessionDir = File(baseDir, uniqueFolder)
            if (!sessionDir.exists()) sessionDir.mkdirs()

            var extracted = 0
            var extractedMb = 0L
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        // Sanitasi nama + tambah prefix timestamp kalau kosong
                        val rawName = entry.name.substringAfterLast("/")
                        val safeName = if (rawName.isBlank()) {
                            "video_${System.currentTimeMillis()}.mp4"
                        } else rawName

                        val outFile = File(sessionDir, safeName)
                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                        extracted++
                        extractedMb += outFile.length()
                        LogTracker.i(this, "Main", "Saved: $uniqueFolder/$safeName (${outFile.length()/1024/1024} MB)")
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (extracted == 0) {
                hideProgress()
                AlertDialog.Builder(this)
                    .setTitle("⚠️ Tidak Ada Video")
                    .setMessage("Zip tidak berisi file video.")
                    .setPositiveButton("OK", null).show()
                return
            }

            LogTracker.i(this, "Main", "Extracted $extracted files → $uniqueFolder")
            updateProgress(100, "Selesai! 🎉", "$extracted file • ${extractedMb/1024/1024} MB")
            delay(2500)
            hideProgress()
            tvStatus.text = "Selesai: $extracted video"

            AlertDialog.Builder(this)
                .setTitle("✅ Berhasil!")
                .setMessage("$extracted video tersimpan di:\nDownloads/${YadApp.DOWNLOAD_DIR}/$uniqueFolder/\n\nFolder unik — video lama tidak terhapus.")
                .setPositiveButton("Lihat Hasil") { _, _ ->
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
                .setNegativeButton("Tutup", null).show()
        } catch (e: Exception) {
            hideProgress()
            LogTracker.e(this, "Main", "Extract failed: ${e.message}")
            AlertDialog.Builder(this)
                .setTitle("⚠️ Gagal Menyimpan")
                .setMessage("${e.message}")
                .setPositiveButton("OK", null).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LogTracker.i(this, "Main", "onDestroy")
    }
}
