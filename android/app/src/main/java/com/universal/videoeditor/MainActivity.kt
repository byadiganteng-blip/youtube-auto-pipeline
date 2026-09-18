package com.universal.videoeditor
import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
class MainActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        val etUrl = findViewById<EditText>(R.id.etUrl)
        val etDur = findViewById<EditText>(R.id.etDuration)
        val spQ = findViewById<Spinner>(R.id.spQuality)
        val spW = findViewById<Spinner>(R.id.spWatermark)
        val spT = findViewById<Spinner>(R.id.spType)
        val tv = findViewById<TextView>(R.id.tvStatus)
        val btnRun = findViewById<MaterialButton>(R.id.btnRun)
        spQ.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("144p","240p","360p","480p","720p","1080p","1440p","2160p"))
        spW.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("remove","skip"))
        spT.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("video","reels"))
        tv.text = "✅ Login: ${SecureConfig.user(this) ?: BuildConfig.GH_USER}"
        btnRun.setOnClickListener {
            val u = etUrl.text.toString().trim()
            if (u.isEmpty()) { Toast.makeText(this,"URL kosong",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            tv.text = "🚀 Triggering..."
            lifecycleScope.launch {
                val r = AdminApi.trigger(this@MainActivity, mapOf(
                    "video_url" to u,
                    "part_duration" to etDur.text.toString().ifBlank { "60" },
                    "quality" to spQ.selectedItem.toString(),
                    "watermark_mode" to spW.selectedItem.toString(),
                    "upload_type" to spT.selectedItem.toString()
                ))
                tv.text = if (r.ok) "✅ Triggered!" else "❌ ${r.code}"
            }
        }
        findViewById<MaterialButton>(R.id.btnAdmin).setOnClickListener { startActivity(Intent(this, AdminActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnActions).setOnClickListener { startActivity(Intent(this, ActionsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnFiles).setOnClickListener { startActivity(Intent(this, FilesActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnStatistics).setOnClickListener { startActivity(Intent(this, StatisticsActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnEditor).setOnClickListener { startActivity(Intent(this, VideoEditorActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnCredit).setOnClickListener { startActivity(Intent(this, CreditActivity::class.java)) }
        findViewById<MaterialButton>(R.id.btnInstructions).setOnClickListener { startActivity(Intent(this, InstructionsActivity::class.java)) }
    }
}
