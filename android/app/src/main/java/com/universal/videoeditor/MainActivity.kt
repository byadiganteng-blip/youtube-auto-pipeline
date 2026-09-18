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

        // Custom dark adapter
        fun mkAdapter(items: List<String>) = ArrayAdapter(
            this, R.layout.spinner_item, items
        ).apply { setDropDownViewResource(R.layout.spinner_dropdown_item) }

        spQ.adapter = mkAdapter(listOf("144p","240p","360p","480p","720p","1080p","1440p","2160p"))
        spW.adapter = mkAdapter(listOf("remove","skip"))
        spT.adapter = mkAdapter(listOf("video","reels"))

        val u = SecureConfig.user(this) ?: BuildConfig.GH_USER
        tv.text = "✅ Login: $u"

        findViewById<MaterialButton>(R.id.btnRun).setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isEmpty()) {
                Toast.makeText(this,"URL kosong",Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            tv.text = "🚀 Triggering..."
            lifecycleScope.launch {
                val r = AdminApi.trigger(this@MainActivity, mapOf(
                    "video_url" to url,
                    "part_duration" to etDur.text.toString().ifBlank { "60" },
                    "quality" to spQ.selectedItem.toString(),
                    "watermark_mode" to spW.selectedItem.toString(),
                    "upload_type" to spT.selectedItem.toString()
                ))
                tv.text = if (r.ok) "✅ Triggered!" else "❌ ${r.code}"
            }
        }
        findViewById<MaterialButton>(R.id.btnAdmin).setOnClickListener {
            startActivity(Intent(this, AdminActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnActions).setOnClickListener {
            startActivity(Intent(this, ActionsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnFiles).setOnClickListener {
            startActivity(Intent(this, FilesActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnStatistics).setOnClickListener {
            startActivity(Intent(this, StatisticsActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnEditor).setOnClickListener {
            startActivity(Intent(this, VideoEditorActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnCredit).setOnClickListener {
            startActivity(Intent(this, CreditActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.btnInstructions).setOnClickListener {
            startActivity(Intent(this, InstructionsActivity::class.java))
        }
    }
}
