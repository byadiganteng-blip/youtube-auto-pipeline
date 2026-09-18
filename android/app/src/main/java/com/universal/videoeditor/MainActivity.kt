package com.universal.videoeditor

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
    private var pollJob: Job? = null

    companion object {
        private const val CHANNEL_ID = "cliper_progress"
        private const val NOTIF_ID = 1001
        private const val NOTIF_DONE_ID = 1002
    }

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        LogTracker.i(this, "Main", "onCreate")

        createNotificationChannel()

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
        requestNotificationPermission()

        
            // ═══ WAJIB: Tampilkan interstitial saat buka app ═══
            // Tampil setelah 500ms biar layout siap
            this.window.decorView.postDelayed({
                try {
                    StartIoAds.requireInterstitialOnStart(this) {
                        LogTracker.i(this, "Ads", "Start interstitial closed")
                    }
                } catch (e: Exception) {
                    LogTracker.e(this, "Ads", "Start ad err: ${e.message}")
                }
            }, 500)


            // Menu handlers
        findViewById<View>(R.id.menuInstructions).setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
        findViewById<View>(R.id.menuResults).setOnClickListener {
            startActivity(Intent(this, ResultsActivity::class.java))
        }
        findViewById<View>(R.id.menuCredit).setOnClickListener {
            startActivity(Intent(this, CreditActivity::class.java))
        }
        findViewById<View>(R.id.menuDonate).setOnClickListener {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.saweria_url))))
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak bisa buka browser", Toast.LENGTH_SHORT).show()
            }
        }

        // Notif button
        val btnNotif = findViewById<View>(R.id.btnNotif)
        val notifDot = findViewById<View>(R.id.notifDot)
        lifecycleScope.launch {
            try {
                val notifs = NotifFetcher.fetch(this@MainActivity)
                if (notifs.isNotEmpty()) notifDot.visibility = View.VISIBLE
                btnNotif.setOnClickListener {
                    if (notifs.isEmpty()) {
                        Toast.makeText(this@MainActivity, "Tidak ada notifikasi", Toast.LENGTH_SHORT).show()
                    } else {
                        showNotifDialog(notifs)
                        notifDot.visibility = View.GONE
                    }
                }
            } catch (_: Exception) {}
        }

        btnProcess.setOnClickListener {
            if (running) {
                AlertDialog.Builder(this)
                    .setTitle("Proses Berjalan")
                    .setMessage("Proses sedang berjalan.\n\nBatalkan atau tunggu?")
                    .setPositiveButton("Tunggu", null)
                    .setNegativeButton("Batalkan") { _, _ -> cancelProcess() }
                    .show()
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
            // WAJIB rewarded sebelum proses
            showRewardedRequiredDialog { processVideo(inputs) }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // NOTIFICATION HELPERS
    // ═══════════════════════════════════════════════════════════
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID, "Cliper On Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Progress proses video"
                    setShowBadge(false)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
            }
        }
    }

    private fun sendProgressNotif(pct: Int, label: String) {
        try {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Cliper On • $pct%")
                .setContentText(label)
                .setProgress(100, pct, false)
                .setOngoing(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
        } catch (_: Exception) {}
    }

    private fun sendDoneNotif(success: Boolean, msg: String) {
        try {
            val intent = Intent(this, ResultsActivity::class.java)
            val pi = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0))

            val notif = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(if (success) android.R.drawable.stat_sys_download_done
                              else android.R.drawable.stat_notify_error)
                .setContentTitle(if (success) "✅ Cliper On Selesai" else "❌ Cliper On Gagal")
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIF_DONE_ID, notif)
            NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    private fun cancelNotification() {
        try {
            NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    private fun cancelProcess() {
        LogTracker.i(this, "Main", "User cancel")
        pollJob?.cancel()
        pollJob = null
        running = false
        hideProgress()
        cancelNotification()
        tvStatus.text = "Dibatalkan"
        Toast.makeText(this, "Proses dibatalkan", Toast.LENGTH_SHORT).show()
    }

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
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(firstLink.link)))
                } catch (_: Exception) {}
            }
        }
        builder.show()
    }

    private fun canDrawOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true

    private fun askOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Izin Floating Window")
            .setMessage("Aktifkan 'Tampil di atas aplikasi lain' supaya progress terlihat.")
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
        if (Build.VERSION.SDK_INT < 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
    }

    private fun showProgress(pct: Int, label: String, detail: String = "") {
        FloatingProgressService.show(this, pct, label, detail)
        tvStatus.text = "$pct% • $label"
        sendProgressNotif(pct, label)
    }

    private fun updateProgress(pct: Int, label: String, detail: String = "") {
        FloatingProgressService.update(this, pct, label, detail)
        tvStatus.text = "$pct% • $label"
        sendProgressNotif(pct, label)
    }

    private fun hideProgress() {
        runOnUiThread {
            try {
                FloatingProgressService.hide(this)
                cancelNotification()
            } catch (_: Exception) {}
        }
    }

    private fun pollingInterval(elapsedMs: Long): Long = when {
        elapsedMs < 3 * 60_000L -> 5_000L
        elapsedMs < 10 * 60_000L -> 15_000L
        elapsedMs < 20 * 60_000L -> 30_000L
        else -> 60_000L
    }

    private fun processVideo(inputs: Map<String, String>) {
        running = true
        LogTracker.i(this, "Main", "Process: $inputs")
        showProgress(5, "Memulai…", "Menghubungi server")
        val startTime = System.currentTimeMillis()

        pollJob = lifecycleScope.launch {
            try {
                val r = ProcessRunner.startProcess(this@MainActivity, inputs)
                if (!r.ok) {
                    hideProgress()
                    sendDoneNotif(false, "Gagal start (${r.code})")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("❌ Gagal Memulai")
                        .setMessage("Kode: ${r.code}\n\n${r.body.take(300)}")
                        .setPositiveButton("OK", null).show()
                    running = false
                    return@launch
                }

                // Simpan history
                val url = inputs["video_url"] ?: ""
                val folderTs = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val folderName = "clip_$folderTs"
                try {
                    HistoryManager.startProcess(this@MainActivity, url, -1L, folderName,
                        inputs["upload_type"] ?: "video",
                        inputs["video_quality"] ?: "original",
                        inputs["video_size"] ?: "original",
                        (inputs["part_duration"] ?: "60").toIntOrNull() ?: 60)
                } catch (_: Exception) {}

                showProgress(10, "Video dikirim", "Menunggu proses di server…")

                var lastStatus = ""
                var lastRunId = -1L

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val interval = pollingInterval(elapsed)
                    delay(interval)

                    val minutes = (elapsed / 60_000L).toInt()
                    val pct = minOf(10 + minutes * 2, 75)
                    val eta = estimateEta(elapsed)
                    updateProgress(pct, "Memproses video…", "Menit $minutes • ETA $eta")

                    val run = ProcessRunner.latestRun(this@MainActivity) ?: continue
                    val runId = run.optLong("id", -1L)
                    val status = run.optString("status", "")
                    val conclusion = run.optString("conclusion", "")

                    lastStatus = status
                    lastRunId = runId

                    if (status == "completed") {
                        LogTracker.i(this@MainActivity, "Main", "Run completed: $conclusion")
                        if (conclusion == "success") {
                            updateProgress(80, "Mengambil hasil…", "Menunggu artifact")
                            downloadResult(runId, url)
                        } else {
                            hideProgress()
                            sendDoneNotif(false, "Proses gagal")
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Proses Gagal")
                                .setMessage("Video tidak dapat diproses.\n\nCek menu Instruksi → Log.")
                                .setPositiveButton("OK", null).show()
                        }
                        return@launch
                    }

                    if (elapsed > 60 * 60_000L) {
                        hideProgress()
                        sendDoneNotif(false, "Timeout 60 menit")
                        Toast.makeText(this@MainActivity, "⏱️ Timeout 60 menit.", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                }
            } finally {
                running = false
                pollJob = null
            }
        }
    }

    private fun estimateEta(elapsed: Long): String {
        val avgSec = 90L
        val remainSec = maxOf(0L, avgSec - elapsed / 1000)
        return if (remainSec < 60) "${remainSec}s" else "${remainSec / 60}m"
    }

    // ═══════════════════════════════════════════════════════════
    // DOWNLOAD RESULT — dengan auto-delete artifact
    // ═══════════════════════════════════════════════════════════
    private suspend fun downloadResult(runId: Long, url: String) {
        LogTracker.i(this, "Main", "downloadResult runId=$runId")

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
            sendDoneNotif(false, "Artifact tidak ditemukan")
            AlertDialog.Builder(this)
                .setTitle("⚠️ Hasil Tidak Ditemukan")
                .setMessage("Workflow selesai tapi artifact tidak ada.")
                .setPositiveButton("OK", null).show()
            return
        }

        val target = arts.first()
        LogTracker.i(this, "Main", "Downloading: ${target.name} (${target.sizeBytes/1024} KB)")
        updateProgress(85, "Mengunduh…", "${target.name} (${target.sizeBytes/1024/1024} MB)")

        val bytes = ProcessRunner.downloadArtifact(this, target.id)
        if (bytes == null || bytes.isEmpty()) {
            hideProgress()
            sendDoneNotif(false, "Download gagal")
            AlertDialog.Builder(this)
                .setTitle("⚠️ Gagal Unduh")
                .setMessage("Artifact tidak dapat diunduh.")
                .setPositiveButton("OK", null).show()
            return
        }

        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val uniqueFolder = "clip_$ts"

        updateProgress(92, "Menyimpan…", "Downloads/${YadApp.DOWNLOAD_DIR}/$uniqueFolder")
        try {
            val baseDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                YadApp.DOWNLOAD_DIR
            )
            if (!baseDir.exists()) baseDir.mkdirs()
            val sessionDir = File(baseDir, uniqueFolder)
            if (!sessionDir.exists()) sessionDir.mkdirs()

            var extracted = 0
            var extractedMb = 0L
            ZipInputStream(bytes.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val rawName = entry.name.substringAfterLast("/")
                        val safeName = if (rawName.isBlank()) "video_${System.currentTimeMillis()}.mp4" else rawName
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
                sendDoneNotif(false, "Tidak ada video")
                return
            }

            // ═══ AUTO-DELETE artifact dari GitHub ═══
            updateProgress(98, "Membersihkan server…", "Hapus artifact di GitHub")
            val deleted = ProcessRunner.deleteArtifact(this, target.id)
            if (deleted) {
                LogTracker.i(this, "Main", "✅ Artifact deleted: ${target.name}")
            }
            try { ProcessRunner.cleanupRunArtifacts(this, runId) } catch (_: Exception) {}

            // Mark history selesai
            try { HistoryManager.markCompleted(this@MainActivity, url, extracted) } catch (_: Exception) {}

            updateProgress(100, "Selesai! 🎉", "$extracted file • ${extractedMb/1024/1024} MB")
            sendDoneNotif(true, "$extracted video siap di Downloads/CliperOn")
            delay(2500)
            hideProgress()
            tvStatus.text = "Selesai: $extracted video"

            AlertDialog.Builder(this)
                .setTitle("✅ Berhasil!")
                .setMessage("$extracted video tersimpan di:\nDownloads/${YadApp.DOWNLOAD_DIR}/$uniqueFolder/\n\nArtifact GitHub sudah dihapus otomatis.")
                .setPositiveButton("Lihat Hasil") { _, _ ->
                    startActivity(Intent(this, ResultsActivity::class.java))
                }
                .setNegativeButton("Tutup", null).show()
        } catch (e: Exception) {
            hideProgress()
            sendDoneNotif(false, "Gagal simpan: ${e.message}")
            AlertDialog.Builder(this)
                .setTitle("⚠️ Gagal Menyimpan")
                .setMessage("${e.message}")
                .setPositiveButton("OK", null).show()
        }
    }

    
    override fun onResume() {
        super.onResume()
        try { StartIoAds.onResume(this) } catch (_: Exception) {}
    }

    override fun onPause() {
        super.onPause()
        try { StartIoAds.onPause(this) } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        LogTracker.i(this, "Main", "onDestroy")
    }
}
