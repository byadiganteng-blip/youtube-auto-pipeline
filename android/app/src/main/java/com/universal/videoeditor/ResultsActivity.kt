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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

        val baseDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            YadApp.DOWNLOAD_DIR
        )
        if (!baseDir.exists()) {
            tvEmpty.visibility = View.VISIBLE
            return
        }

        // Kumpulkan semua video dari subfolder + root
        val videos = mutableListOf<File>()
        baseDir.listFiles()?.forEach { f ->
            if (f.isDirectory) {
                f.listFiles { file -> file.isFile && isVideo(file) }?.let { videos.addAll(it) }
            } else if (isVideo(f)) {
                videos.add(f)
            }
        }

        if (videos.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            LogTracker.d(this, "Results", "No videos found")
            return
        }
        tvEmpty.visibility = View.GONE
        LogTracker.i(this, "Results", "Found ${videos.size} videos")

        videos.sortedByDescending { it.lastModified() }.forEach { f ->
            val card = layoutInflater.inflate(R.layout.item_video, container, false)
            card.findViewById<TextView>(R.id.tvName).text = f.name

            val dateStr = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(Date(f.lastModified()))
            val folder = f.parentFile?.name ?: ""
            val sizeMb = f.length() / 1024.0 / 1024.0
            card.findViewById<TextView>(R.id.tvSize).text =
                "%.1f MB  •  %s  •  %s".format(sizeMb, dateStr, folder)

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

    private fun isVideo(f: File): Boolean {
        val n = f.name.lowercase()
        return n.endsWith(".mp4") || n.endsWith(".mkv") || n.endsWith(".webm") || n.endsWith(".mov")
    }
}
