package com.universal.videoeditor

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton

class InstructionsActivity : AppCompatActivity() {

    private data class Step(val title: String, val desc: String, val emoji: String)

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_instructions)
        LogTracker.i(this, "Instr", "onCreate")

        val container = findViewById<LinearLayout>(R.id.stepsContainer)
        val steps = listOf(
            Step("Izinkan Akses Aplikasi",
                "Saat pertama kali dibuka, Cliper On akan meminta izin akses. Pilih Allow / Izinkan untuk semua (Notifikasi, Media Video, dan Penyimpanan).", "🔐"),
            Step("Tempel Link Video",
                "Buka menu utama, tempel link video yang ingin diproses di kolom Video URL. Mendukung YouTube, TikTok, Instagram, Facebook, dan lainnya.", "🔗"),
            Step("Atur Durasi per Part",
                "Isi durasi setiap bagian video dalam detik. Contoh: 60 = potong jadi 60 detik per part. Rekomendasi 60-180 detik untuk Reels/Shorts.", "⏱️"),
            Step("Pilih Kualitas Video",
                "Pilih resolusi output: 360p (hemat), 480p, 720p (seimbang), atau 1080p (HD). Semakin tinggi = file lebih besar.", "🎥"),
            Step("Pilih Mode Watermark",
                "remove = hapus watermark otomatis. skip = biarkan watermark tetap ada. Pilih sesuai kebutuhan.", "💧"),
            Step("Pilih Tipe Upload",
                "video = upload sebagai video biasa. reels = upload sebagai Reels/Shorts (vertikal).", "📤"),
            Step("Tekan PROSES VIDEO",
                "Klik tombol 🎬 PROSES VIDEO. Aplikasi akan otomatis mengirim ke server, memproses, dan mengunduh hasilnya.", "🚀"),
            Step("Tunggu Proses Selesai",
                "Progress akan muncul di layar. Persentase naik dari 5% → 100%. Jangan tutup aplikasi saat proses berjalan.", "⏳"),
            Step("Video Otomatis Tersimpan",
                "Setelah selesai, video akan otomatis tersimpan di folder Downloads/${YadApp.DOWNLOAD_DIR}/. Dialog konfirmasi akan muncul.", "💾"),
            Step("Buka Menu Hasil Video",
                "Buka menu 🎞️ Hasil Video untuk melihat semua video yang sudah jadi. Ketuk video untuk membukanya.", "🎬"),
            Step("Cek Log Jika Bermasalah",
                "Jika ada error, buka bagian Log Aplikasi di bawah. Log tersimpan otomatis di:\n/Android/data/${packageName}/files/logs/", "📋")
        )

        for ((i, step) in steps.withIndex()) {
            val item = layoutInflater.inflate(R.layout.item_instruction, container, false)
            item.findViewById<TextView>(R.id.tvNumber).text = "${step.emoji}"
            item.findViewById<TextView>(R.id.tvTitle).text = "Langkah ${i + 1}: ${step.title}"
            item.findViewById<TextView>(R.id.tvText).text = step.desc
            container.addView(item)
        }

        // Log viewer
        val tvLogPath = findViewById<TextView>(R.id.tvLogPath)
        val tvLogContent = findViewById<TextView>(R.id.tvLogContent)

        fun refreshLog() {
            tvLogPath.text = "📁 ${LogTracker.logPath(this)}"
            tvLogContent.text = LogTracker.readAll(this, maxLines = 500)
            LogTracker.d(this, "Instr", "Log refreshed")
        }
        refreshLog()

        findViewById<AppCompatButton>(R.id.btnRefreshLog).setOnClickListener {
            refreshLog()
            Toast.makeText(this, "🔄 Log di-refresh", Toast.LENGTH_SHORT).show()
        }

        findViewById<AppCompatButton>(R.id.btnClearLog).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Hapus Log?")
                .setMessage("Semua log akan dihapus. Yakin?")
                .setPositiveButton("Hapus") { _, _ ->
                    LogTracker.clear(this)
                    refreshLog()
                    Toast.makeText(this, "🗑️ Log dihapus", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Batal", null).show()
        }
    }
}
