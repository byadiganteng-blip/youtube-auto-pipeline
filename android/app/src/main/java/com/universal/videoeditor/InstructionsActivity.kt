package com.universal.videoeditor

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_instructions)

        val tv = findViewById<TextView>(R.id.tvInstructions)
        tv.text = """
📌 LANGKAH PEMAKAIAN UNIVERSAL VIDEO EDITOR
━━━━━━━━━━━━━━━━━━━━━━

1️⃣ VIDEO PIPELINE
   • Paste URL video (YouTube/TikTok/FB/IG)
   • Pilih Process Mode, Video Size, Quality
   • Custom watermark (opsional)
   • Klik "RUN WORKFLOW"

2️⃣ VIDEO EDITOR (BARU)
   • Buka menu "Video Editor"
   • Pilih file video dari perangkat
   • Pilih tool: Trim, Crop, Rotate, Flip
   • Speed, Volume, Filter
   • Compress, Convert, Extract Audio

3️⃣ AUTO UPLOAD KE YOUTUBE
   • Set Auto Upload = true
   • Upload YouTube Token
   • Video otomatis di-upload setelah diproses

4️⃣ STATISTIK
   • Buka menu Statistics
   • Lihat total runs, success rate
   • Video di-upload, download count

5️⃣ SETTINGS
   • Dark Mode
   • Multi-Language
   • Backup & Restore
   • Clear Cache

━━━━━━━━━━━━━━━━━━━━━━
🎨 GAYA WATERMARK
━━━━━━━━━━━━━━━━━━━━━━
• minimal: putih transparan
• elegant: background gelap
• bold: teks tebal border merah
• neon: cyan glow
• gradient: semi-transparan

━━━━━━━━━━━━━━━━━━━━━━
❓ TROUBLESHOOTING
━━━━━━━━━━━━━━━━━━━━━━
• Izin storage: allow saat pertama
• Notifikasi: allow untuk progress
• Update: cek otomatis saat buka

━━━━━━━━━━━━━━━━━━━━━━
Created By KARYADI CODING KARYADI
Designed By KARYADI
        """.trimIndent()
    }
}
