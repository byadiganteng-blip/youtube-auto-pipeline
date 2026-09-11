package com.universal.videoeditor

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class StatisticsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val tvStats = findViewById<TextView>(R.id.tvStats)

        val prefs = getSharedPreferences("yad_stats", Context.MODE_PRIVATE)
        val totalRuns = prefs.getInt("total_runs", 0)
        val totalSuccess = prefs.getInt("total_success", 0)
        val totalFail = prefs.getInt("total_fail", 0)
        val totalVideos = prefs.getInt("total_videos_uploaded", 0)
        val totalDownloads = prefs.getInt("total_downloads", 0)

        val successRate = if (totalRuns > 0) (totalSuccess * 100 / totalRuns) else 0

        tvStats.text = """
📊 STATISTIK

━━━━━━━━━━━━━━━━━━━━

🚀 Total Workflow Runs
   $totalRuns

✅ Sukses
   $totalSuccess

❌ Gagal
   $totalFail

📈 Success Rate
   $successRate%

🎬 Video Di-upload
   $totalVideos

📥 Total Downloads
   $totalDownloads

━━━━━━━━━━━━━━━━━━━━

Last updated: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}
        """.trimIndent()
    }

    companion object {
        fun incrementRun(context: Context, success: Boolean) {
            val prefs = context.getSharedPreferences("yad_stats", Context.MODE_PRIVATE)
            val runs = prefs.getInt("total_runs", 0) + 1
            val s = prefs.getInt("total_success", 0) + if (success) 1 else 0
            val f = prefs.getInt("total_fail", 0) + if (!success) 1 else 0
            prefs.edit()
                .putInt("total_runs", runs)
                .putInt("total_success", s)
                .putInt("total_fail", f)
                .apply()
        }

        fun incrementVideos(context: Context, count: Int) {
            val prefs = context.getSharedPreferences("yad_stats", Context.MODE_PRIVATE)
            prefs.edit().putInt("total_videos_uploaded",
                prefs.getInt("total_videos_uploaded", 0) + count).apply()
        }

        fun incrementDownloads(context: Context, count: Int) {
            val prefs = context.getSharedPreferences("yad_stats", Context.MODE_PRIVATE)
            prefs.edit().putInt("total_downloads",
                prefs.getInt("total_downloads", 0) + count).apply()
        }
    }
}
