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
import kotlinx.coroutines.CancellationException
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

    private var tvStatus: TextView? = null
    private var tvDurationValue: TextView? = null
    private var tvDurationHint: TextView? = null
    private var sbDuration: SeekBar? = null
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
        try {
            setContentView(R.layout.activity_main)
            LogTracker.i(this, "Main", "onCreate")

            createNotificationChannel()

            tvStatus = findViewById(R.id.tvStatus)
            tvDurationValue = findViewById(R.id.tvDurationValue)
            tvDurationHint = findViewById(R.id.tvDurationHint)
            sbDuration = findViewById(R.id.sbDuration)

            // Init ads
            try {
                StartIoAds.init(this)
                LogTracker.i(this, "Main", "StartIoAds.init called")
            } catch (e: Exception) {
                LogTracker.e(this, "Main", "Ads init failed: ${e.message}")
            }

            val etUrl = findViewById<EditText>(R.id.etUrl)
            val spProcessMode = findViewById<Spinner>(R.id.spProcessMode)
            val spMethod = findViewById<Spinner>(R.id.spMethod)
            val spVideoSize = findViewById<Spinner>(R.id.spVideoSize)
            val spQuality = findViewById<Spinner>(R.id.spQuality)
            val spType = findViewById<Spinner>(R.id.spType)
            val btnProcess = findViewById<AppCompatButton>(R.id.btnProcess)

            fun mk(items: List<String>) = ArrayAdapter(this,
                android.R.layout.simple_spinner_dropdown_item, items)

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

            sbDuration?.max = 600
            sbDuration?.progress = 60
            updateDurationLabel(60)

            sbDuration?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actual = if (progress < 10) 10 else progress
                    updateDurationLabel(actual)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            etUrl.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    try {
                        val url = etUrl.text.toString().trim()
                        if (url.isNotEmpty() && url.startsWith("http")) detectDuration(url)
                    } catch (_: Exception) {}
                }
            }

            requestPermissionsIfNeeded()
            requestNotificationPermission()

            // INTERSTITIAL INSTANT — 1s timeout
            this.window.decorView.postDelayed({
                try {
                    StartIoAds.requireInterstitialOnStart(this) {
                        LogTracker.i(this, "Ads", "Start interstitial done")
                    }
                } catch (_: Exception) {}
            }, 500)

            // Menu handlers
            findViewById<View>(R.id.menuInstructions)?.setOnClickListener {
                try { startActivity(Intent(this, InstructionsActivity::class.java)) } catch (_: Exception) {}
            }
            findViewById<View>(R.id.menuResults)?.setOnClickListener {
                try { startActivity(Intent(this, ResultsActivity::class.java)) } catch (_: Exception) {}
            }
            findViewById<View>(R.id.menuCredit)?.setOnClickListener {
                try { startActivity(Intent(this, CreditActivity::class.java)) } catch (_: Exception) {}
            }
            findViewById<View>(R.id.menuDonate)?.setOnClickListener {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.saweria_url))))
                } catch (_: Exception) {}
            }

            // Notif button
            val btnNotif = findViewById<View>(R.id.btnNotif)
            val notifDot = findViewById<View>(R.id.notifDot)
            lifecycleScope.launch {
                try {
                    val notifs = NotifFetcher.fetch(this@MainActivity)
                    if (notifs.isNotEmpty()) notifDot?.visibility = View.VISIBLE
                    btnNotif?.setOnClickListener {
                        try {
                            if (notifs.isEmpty()) {
                                Toast.makeText(this@MainActivity, "Tidak ada notifikasi", Toast.LENGTH_SHORT).show()
                            } else {
                                showNotifDialog(notifs)
                                notifDot?.visibility = View.GONE
                            }
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            btnProcess?.setOnClickListener {
                try {
                    if (running) {
                        AlertDialog.Builder(this)
                            .setTitle("Proses Berjalan")
                            .setMessage("Batalkan atau tunggu?")
                            .setPositiveButton("Tunggu", null)
                            .setNegativeButton("Batalkan") { _, _ -> cancelProcess() }
                            .show()
                        return@setOnClickListener
                    }
                    val url = etUrl.text.toString().trim()
                    if (url.isEmpty()) {
                        Toast.makeText(this, "Tempel link video dulu", Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (!canDrawOverlay()) {
                        askOverlayPermission()
                        return@setOnClickListener
                    }
                    val inputs = mapOf(
                        "video_url" to url,
                        "process_mode" to (spProcessMode.selectedItem?.toString() ?: "skip_watermark"),
                        "method" to (spMethod.selectedItem?.toString() ?: "blur"),
                        "video_size" to (spVideoSize.selectedItem?.toString() ?: "original"),
                        "video_quality" to (spQuality.selectedItem?.toString() ?: "original"),
                        "part_duration" to ((sbDuration?.progress ?: 60).coerceAtLeast(10)).toString(),
                        "upload_type" to (spType.selectedItem?.toString() ?: "video")
                    )

                    // INSTANT REWARDED — 1s timeout, auto-skip
                    StartIoAds.requireRewarded(
                        activity = this,
                        onEarned = {
                            LogTracker.i(this, "Ads", "Reward earned")
                            processVideo(inputs)
                        },
                        onFailed = { err ->
                            LogTracker.w(this, "Ads", "Reward skipped: $err")
                            // AUTO-SKIP → langsung proses
                            processVideo(inputs)
                        }
                    )
                } catch (e: Exception) {
                    LogTracker.e(this, "Main", "btnProcess err: ${e.message}")
                }
            }
        } catch (e: Exception) {
            LogTracker.e(this, "Main", "onCreate CRASH: ${e.message}")
            Toast.makeText(this, "Startup error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                    nm.createNotificationChannel(
                        NotificationChannel(CHANNEL_ID, "Cliper On",
                            NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
                }
            }
        } catch (_: Exception) {}
    }

    private fun requestNotificationPermission() {
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1002)
                }
            }
        } catch (_: Exception) {}
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
                .setContentTitle(if (success) "✅ Selesai" else "❌ Gagal")
                .setContentText(msg)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build()

            NotificationManagerCompat.from(this).notify(NOTIF_DONE_ID, notif)
            NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        } catch (_: Exception) {}
    }

    private fun cancelNotification() {
        try { NotificationManagerCompat.from(this).cancel(NOTIF_ID) } catch (_: Exception) {}
    }

    private fun cancelProcess() {
        try {
            pollJob?.cancel()
            pollJob = null
            running = false
            hideProgress()
            cancelNotification()
            tvStatus?.text = "Dibatalkan"
        } catch (_: Exception) {}
    }

    private fun showNotifDialog(notifs: List<NotifFetcher.Notif>) {
        try {
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
        } catch (_: Exception) {}
    }

    private fun canDrawOverlay(): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) Settings.canDrawOverlays(this) else true
    } catch (_: Exception) { true }

    private fun askOverlayPermission() {
        try {
            AlertDialog.Builder(this)
                .setTitle("Izin Floating Window")
                .setMessage("Aktifkan 'Tampil di atas aplikasi lain' supaya progress terlihat.")
                .setPositiveButton("Buka Pengaturan") { _, _ ->
                    try {
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:$packageName")))
                    } catch (_: Exception) {}
                }
                .setNegativeButton("Batal", null).show()
        } catch (_: Exception) {}
    }

    private fun updateDurationLabel(sec: Int) {
        try {
            val label = when {
                sec < 60 -> "${sec} dtk"
                sec % 60 == 0 -> "${sec / 60} mnt"
                else -> "${sec / 60}m ${sec % 60}s"
            }
            tvDurationValue?.text = label
            if (videoDurationSec > 0) {
                val parts = (videoDurationSec + sec - 1) / sec
                tvDurationHint?.text = "Video ${videoDurationSec}s → ~${parts} part"
            }
        } catch (_: Exception) {}
    }

    private fun detectDuration(url: String) {
        try {
            tvDurationHint?.text = "Mendeteksi durasi…"
            lifecycleScope.launch {
                try {
                    val r = ProcessRunner.probeVideoDuration(this@MainActivity, url)
                    if (r != null && r > 0) {
                        videoDurationSec = r
                        tvDurationHint?.text = "Video: ${r}s (${r/60}m ${r%60}s)"
                        val suggest = (r / 10).coerceIn(10, 300)
                        sbDuration?.progress = suggest
                        updateDurationLabel(suggest)
                    } else {
                        tvDurationHint?.text = "Video: tidak diketahui"
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
    }

    private fun requestPermissionsIfNeeded() {
        try {
            val perms = mutableListOf<String>()
            if (Build.VERSION.SDK_INT < 33) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
            if (perms.isNotEmpty()) ActivityCompat.requestPermissions(this, perms.toTypedArray(), 1001)
        } catch (_: Exception) {}
    }

    private fun showProgress(pct: Int, label: String, detail: String = "") {
        runOnUiThread {
            try {
                FloatingProgressService.show(this, pct, label, detail)
                tvStatus?.text = "$pct% • $label"
                sendProgressNotif(pct, label)
            } catch (_: Exception) {}
        }
    }

    private fun updateProgress(pct: Int, label: String, detail: String = "") {
        runOnUiThread {
            try {
                FloatingProgressService.update(this, pct, label, detail)
                tvStatus?.text = "$pct% • $label"
                sendProgressNotif(pct, label)
            } catch (_: Exception) {}
        }
    }

    private fun hideProgress() {
        runOnUiThread {
            try {
                FloatingProgressService.hide(this)
                cancelNotification()
            } catch (_: Exception) {}
        }
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
                    try {
                        AlertDialog.Builder(this@MainActivity)
                            .setTitle("❌ Gagal")
                            .setMessage("Kode: ${r.code}")
                            .setPositiveButton("OK", null).show()
                    } catch (_: Exception) {}
                    running = false
                    return@launch
                }

                // Save history
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

                showProgress(10, "Video dikirim", "Menunggu part 1…")
                LogTracker.i(this@MainActivity, "Main", "STEP: enter polling loop")

                // ═══ PER-PART STREAMING DOWNLOAD ═══
                val downloadedParts = mutableSetOf<Int>()
                val outputDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "${YadApp.DOWNLOAD_DIR}/$folderName"
                )
                if (!outputDir.exists()) outputDir.mkdirs()

                var lastPartCount = 0
                var workflowDone = false

                while (isActive) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val minutes = (elapsed / 60_000L).toInt()
                    val basePct = minOf(10 + minutes * 3, 50)

                    // Cek workflow status
                    val run = ProcessRunner.latestRun(this@MainActivity)
                    if (run != null) {
                        val runId = run.optLong("id", -1L)
                        val status = run.optString("status", "")
                        val conclusion = run.optString("conclusion", "")

                        // Ambil artifact yang tersedia SEKARANG
                        try {
                            val arts = ProcessRunner.runArtifacts(this@MainActivity, runId)
                            for (art in arts) {
                                // Deteksi part number
                                val partNum = ProcessRunner.partNumberFromName(art.name)
                                if (partNum > 0 && partNum !in downloadedParts) {
                                    // DOWNLOAD PART SEKARANG
                                    updateProgress(basePct, "Download part-$partNum…",
                                        "${downloadedParts.size} part selesai")
                                    LogTracker.i(this@MainActivity, "Main",
                                        "Downloading part-$partNum (${art.name})")

                                    val bytes = ProcessRunner.downloadArtifact(this@MainActivity, art.id)
                                    if (bytes != null && bytes.isNotEmpty()) {
                                        try {
                                            var extracted = 0
                                            ZipInputStream(bytes.inputStream()).use { zis ->
                                                var entry = zis.nextEntry
                                                while (entry != null) {
                                                    if (!entry.isDirectory) {
                                                        val rawName = entry.name.substringAfterLast("/")
                                                        val safeName = if (rawName.isBlank())
                                                            "video_part${String.format("%03d", partNum)}.mp4"
                                                            else rawName
                                                        val outFile = File(outputDir, safeName)
                                                        FileOutputStream(outFile).use { fos -> zis.copyTo(fos) }
                                                        extracted++
                                                        LogTracker.i(this@MainActivity, "Main",
                                                            "Saved part-$partNum: $safeName")
                                                    }
                                                    zis.closeEntry()
                                                    entry = zis.nextEntry
                                                }
                                            }

                                            if (extracted > 0) {
                                                downloadedParts.add(partNum)
                                                // Hapus artifact setelah download
                                                try { ProcessRunner.deleteArtifact(this@MainActivity, art.id) } catch (_: Exception) {}
                                                // Update history
                                                try { HistoryManager.markPartDownloaded(this@MainActivity, url, partNum) } catch (_: Exception) {}
                                                LogTracker.i(this@MainActivity, "Main",
                                                    "Part-$partNum DONE (total: ${downloadedParts.size})")
                                            }
                                        } catch (e: Exception) {
                                            LogTracker.e(this@MainActivity, "Main",
                                                "Extract part-$partNum failed: ${e.message}")
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}

                        // Update progress berdasarkan part yang didownload
                        val pct = minOf(10 + downloadedParts.size * 15, 90)
                        updateProgress(pct, "Memproses video…",
                            "${downloadedParts.size} part • ${minutes}m")

                        if (status == "completed") {
                            workflowDone = true
                            LogTracker.i(this@MainActivity, "Main", "Workflow done: $conclusion")

                            // Tunggu sisa artifact (kalau ada)
                            delay(3000)

                            if (conclusion == "success" || downloadedParts.isNotEmpty()) {
                                // Finalize
                                if (downloadedParts.isEmpty()) {
                                    // Coba download sisa artifact (bundle)
                                    try {
                                        val arts = ProcessRunner.runArtifacts(this@MainActivity, runId)
                                        for (art in arts) {
                                            if (art.sizeBytes > 100_000) {
                                                val bytes = ProcessRunner.downloadArtifact(this@MainActivity, art.id)
                                                if (bytes != null) {
                                                    ZipInputStream(bytes.inputStream()).use { zis ->
                                                        var entry = zis.nextEntry
                                                        while (entry != null) {
                                                            if (!entry.isDirectory) {
                                                                val outFile = File(outputDir, entry.name.substringAfterLast("/"))
                                                                FileOutputStream(outFile).use { zis.copyTo(it) }
                                                                downloadedParts.add(90 + downloadedParts.size)
                                                            }
                                                            zis.closeEntry()
                                                            entry = zis.nextEntry
                                                        }
                                                    }
                                                    try { ProcessRunner.deleteArtifact(this@MainActivity, art.id) } catch (_: Exception) {}
                                                }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                updateProgress(100, "Selesai! 🎉",
                                    "${downloadedParts.size} part tersimpan")
                                sendDoneNotif(true, "${downloadedParts.size} part siap")
                                delay(2000)
                                hideProgress()
                                tvStatus?.text = "Selesai: ${downloadedParts.size} part"

                                try { HistoryManager.markCompleted(this@MainActivity, url, downloadedParts.size) } catch (_: Exception) {}

                                try {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("✅ Selesai!")
                                        .setMessage("${downloadedParts.size} video tersimpan di:\nDownloads/${YadApp.DOWNLOAD_DIR}/$folderName/")
                                        .setPositiveButton("Lihat Hasil") { _, _ ->
                                            try { startActivity(Intent(this@MainActivity, ResultsActivity::class.java)) } catch (_: Exception) {}
                                        }
                                        .setNegativeButton("Tutup", null).show()
                                } catch (_: Exception) {}
                            } else {
                                hideProgress()
                                sendDoneNotif(false, "Gagal")
                                try {
                                    AlertDialog.Builder(this@MainActivity)
                                        .setTitle("Gagal")
                                        .setMessage("Cek log di menu Instruksi")
                                        .setPositiveButton("OK", null).show()
                                } catch (_: Exception) {}
                            }
                            return@launch
                        }
                    }

                    // Timeout 90 menit
                    if (elapsed > 90 * 60_000L) {
                        hideProgress()
                        sendDoneNotif(false, "Timeout")
                        return@launch
                    }

                    delay(3000)  // Polling tiap 3 detik
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogTracker.e(this@MainActivity, "Main", "CRASH: ${e.message}")
                try { hideProgress() } catch (_: Exception) {}
            } finally {
                running = false
                pollJob = null
            }
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
        try { LogTracker.i(this, "Main", "onDestroy") } catch (_: Exception) {}
    }
}
