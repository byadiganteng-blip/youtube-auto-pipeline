package com.universal.videoeditor

import android.app.AlertDialog
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class VideoEditorActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_editor)

        val btnTrim = findViewById<Button>(R.id.btnTrim)
        val btnCrop = findViewById<Button>(R.id.btnCrop)
        val btnRotate = findViewById<Button>(R.id.btnRotate)
        val btnFlip = findViewById<Button>(R.id.btnFlip)
        val btnSpeed = findViewById<Button>(R.id.btnSpeed)
        val btnVolume = findViewById<Button>(R.id.btnVolume)
        val btnFilter = findViewById<Button>(R.id.btnFilter)
        val btnExtractAudio = findViewById<Button>(R.id.btnExtractAudio)
        val btnCompress = findViewById<Button>(R.id.btnCompress)
        val btnConvertFormat = findViewById<Button>(R.id.btnConvertFormat)

        btnTrim.setOnClickListener { showInfo("✂️ Trim Video") }
        btnCrop.setOnClickListener { showInfo("🖼️ Crop Video") }
        btnRotate.setOnClickListener { showInfo("🔄 Rotate") }
        btnFlip.setOnClickListener { showInfo("🪞 Flip") }
        btnSpeed.setOnClickListener { showInfo("⚡ Speed Control") }
        btnVolume.setOnClickListener { showInfo("🔊 Volume Control") }
        btnFilter.setOnClickListener { showInfo("🎨 Video Filter") }
        btnExtractAudio.setOnClickListener { showInfo("🎵 Extract Audio") }
        btnCompress.setOnClickListener { showInfo("📦 Compress Video") }
        btnConvertFormat.setOnClickListener { showInfo("🔄 Convert Format") }
    }

    private fun showInfo(fitur: String) {
        AlertDialog.Builder(this)
            .setTitle(fitur)
            .setMessage(
                "Fitur $fitur akan diproses via server workflow.\n\n" +
                "Cara pakai:\n" +
                "1. Upload video ke server (atau pakai URL)\n" +
                "2. Pilih tool yang diinginkan\n" +
                "3. Server proses video\n" +
                "4. Hasil bisa didownload\n\n" +
                "Atau pakai aplikasi editor lain di HP untuk edit cepat."
            )
            .setPositiveButton("OK", null)
            .show()
    }
}
