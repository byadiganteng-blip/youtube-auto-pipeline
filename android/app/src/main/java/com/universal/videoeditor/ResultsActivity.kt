package com.universal.videoeditor
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.FileProvider
import java.io.File

class ResultsActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_results)
        LogTracker.i(this, "Results", "onCreate")
        val container = findViewById<LinearLayout>(R.id.listContainer)
        val tvEmpty = findViewById<TextView>(R.id.tvEmpty)
        findViewById<AppCompatButton>(R.id.btnRefresh).setOnClickListener {
            loadFiles(container, tvEmpty)
        }
        loadFiles(container, tvEmpty)
    }

    private fun loadFiles(container: LinearLayout, tvEmpty: TextView) {
        container.removeAllViews()
        container.addView(tvEmpty)
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), YadApp.DOWNLOAD_DIR)
        val files = dir.listFiles { f -> f.isFile && (f.name.endsWith(".mp4") || f.name.endsWith(".mkv") || f.name.endsWith(".webm") || f.name.endsWith(".mov")) } ?: emptyArray()
        if (files.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            LogTracker.d(this, "Results", "No videos in ${dir.absolutePath}")
            return
        }
        tvEmpty.visibility = View.GONE
        LogTracker.i(this, "Results", "Found ${files.size} videos")
        files.sortedByDescending { it.lastModified() }.forEach { f ->
            val card = layoutInflater.inflate(R.layout.item_video, container, false)
            card.findViewById<TextView>(R.id.tvName).text = f.name
            card.findViewById<TextView>(R.id.tvSize).text = "%.1f MB  •  %s".format(
                f.length() / 1024.0 / 1024.0,
                android.text.format.DateFormat.format("dd MMM yyyy, HH:mm", f.lastModified()))
            card.setOnClickListener {
                try {
                    val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    LogTracker.i(this, "Results", "Open video: ${f.name}")
                    startActivity(Intent.createChooser(intent, "Buka dengan"))
                } catch (e: Exception) {
                    LogTracker.e(this, "Results", "Open failed: ${e.message}")
                    Toast.makeText(this, "⚠️ ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            container.addView(card)
        }
    }
}
