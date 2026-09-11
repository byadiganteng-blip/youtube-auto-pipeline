package com.yadiganteng.pipeline

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FilesActivity : AppCompatActivity() {

    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private lateinit var recyclerArtifacts: RecyclerView
    private lateinit var recyclerReleases: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTab: TextView

    private var githubToken: String = ""
    private val artifacts = mutableListOf<GitHubFile>()
    private val releases = mutableListOf<GitHubFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        githubToken = intent.getStringExtra("token") ?: ""

        recyclerArtifacts = findViewById(R.id.recyclerArtifacts)
        recyclerReleases = findViewById(R.id.recyclerReleases)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        tvTab = findViewById(R.id.tvTab)

        recyclerArtifacts.layoutManager = LinearLayoutManager(this)
        recyclerReleases.layoutManager = LinearLayoutManager(this)

        btnRefresh.setOnClickListener { loadAll() }

        loadAll()
    }

    private fun loadAll() {
        progressBar.visibility = View.VISIBLE
        loadArtifacts()
        loadReleases()
    }

    // ==========================================
    // LOAD ARTIFACTS
    // ==========================================
    private fun loadArtifacts() {
        val url = "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts?per_page=20"
        val request = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@FilesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        try {
                            artifacts.clear()
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val arr = json.optJSONArray("artifacts")
                            if (arr != null) {
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    artifacts.add(GitHubFile(
                                        id = obj.getLong("id"),
                                        name = obj.getString("name"),
                                        size = obj.optLong("size_in_bytes", 0),
                                        downloadUrl = obj.optString("archive_download_url", ""),
                                        type = "artifact",
                                        createdAt = obj.optString("created_at", "")
                                    ))
                                }
                            }
                            recyclerArtifacts.adapter = FileAdapter(artifacts) { file -> onFileAction(file) }
                        } catch (e: Exception) {
                            Toast.makeText(this@FilesActivity, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    // ==========================================
    // LOAD RELEASES
    // ==========================================
    private fun loadReleases() {
        val url = "https://api.github.com/repos/$OWNER/$REPO/releases?per_page=20"
        val request = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        try {
                            releases.clear()
                            val arr = JSONArray(response.body?.string() ?: "[]")
                            for (i in 0 until arr.length()) {
                                val rel = arr.getJSONObject(i)
                                val relId = rel.getLong("id")
                                val tagName = rel.getString("tag_name")
                                val assets = rel.optJSONArray("assets")
                                if (assets != null) {
                                    for (j in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(j)
                                        releases.add(GitHubFile(
                                            id = asset.getLong("id"),
                                            name = "${tagName} / ${asset.getString("name")}",
                                            size = asset.optLong("size", 0),
                                            downloadUrl = asset.optString("browser_download_url", ""),
                                            type = "release",
                                            createdAt = rel.optString("published_at", ""),
                                            releaseId = relId,
                                            releaseTag = tagName
                                        ))
                                    }
                                } else {
                                    // Release tanpa asset
                                    releases.add(GitHubFile(
                                        id = relId,
                                        name = tagName,
                                        size = 0,
                                        downloadUrl = "",
                                        type = "release_only",
                                        createdAt = rel.optString("published_at", ""),
                                        releaseId = relId,
                                        releaseTag = tagName
                                    ))
                                }
                            }
                            recyclerReleases.adapter = FileAdapter(releases) { file -> onFileAction(file) }
                        } catch (e: Exception) {
                            Toast.makeText(this@FilesActivity, "Parse error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    // ==========================================
    // ACTION: DOWNLOAD / DELETE
    // ==========================================
    private fun onFileAction(file: GitHubFile) {
        val options = mutableListOf<String>()
        if (file.downloadUrl.isNotEmpty()) options.add("⬇️ Download")
        options.add("🗑️ Delete")

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "⬇️ Download" -> downloadFile(file)
                    "🗑️ Delete" -> confirmDelete(file)
                }
            }
            .show()
    }

    private fun downloadFile(file: GitHubFile) {
        if (file.downloadUrl.isEmpty()) {
            Toast.makeText(this, "Tidak ada file untuk di-download", Toast.LENGTH_LONG).show()
            return
        }

        val request = Request.Builder().url(file.downloadUrl)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/octet-stream").build()

        progressBar.visibility = View.VISIBLE

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@FilesActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) {
                        val fileName = file.name.replace("/", "_").replace(" ", "_")
                        val finalFileName = if (fileName.endsWith(".zip") || fileName.endsWith(".apk"))
                            fileName else "$fileName.zip"

                        val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        val request2 = DownloadManager.Request(Uri.parse(file.downloadUrl))
                            .setTitle(file.name)
                            .setDescription("Downloading...")
                            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, finalFileName)

                        // Set auth header
                        val cookies = ""  // Skip, atau pakai workaround lain
                        dm.enqueue(request2)

                        Toast.makeText(this@FilesActivity, "Download mulai: $finalFileName", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@FilesActivity, "Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun confirmDelete(file: GitHubFile) {
        AlertDialog.Builder(this)
            .setTitle("Hapus?")
            .setMessage("Yakin hapus:\n${file.name}")
            .setPositiveButton("Hapus") { _, _ -> deleteFile(file) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun deleteFile(file: GitHubFile) {
        val url = when (file.type) {
            "artifact" -> "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/${file.id}"
            "release" -> "https://api.github.com/repos/$OWNER/$REPO/releases/assets/${file.id}"
            "release_only" -> "https://api.github.com/repos/$OWNER/$REPO/releases/${file.id}"
            else -> return
        }

        val request = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json")
            .delete().build()

        progressBar.visibility = View.VISIBLE

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@FilesActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.code in listOf(204, 200, 202)) {
                        Toast.makeText(this@FilesActivity, "✅ Berhasil dihapus", Toast.LENGTH_LONG).show()
                        loadAll()
                    } else {
                        Toast.makeText(this@FilesActivity, "Error: ${response.code}\n${response.body?.string()}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    // ==========================================
    // DATA MODEL
    // ==========================================
    data class GitHubFile(
        val id: Long,
        val name: String,
        val size: Long,
        val downloadUrl: String,
        val type: String,  // "artifact" | "release" | "release_only"
        val createdAt: String,
        val releaseId: Long = 0,
        val releaseTag: String = ""
    )

    // ==========================================
    // ADAPTER
    // ==========================================
    inner class FileAdapter(
        private val items: List<GitHubFile>,
        private val onItemClick: (GitHubFile) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvName: TextView = view.findViewById(R.id.tvName)
            val tvInfo: TextView = view.findViewById(R.id.tvInfo)
            val btnMenu: ImageButton = view.findViewById(R.id.btnMenu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = items[position]
            holder.tvName.text = file.name
            val sizeMb = file.size / (1024.0 * 1024.0)
            holder.tvInfo.text = "${String.format("%.2f", sizeMb)} MB • ${file.createdAt}"
            holder.itemView.setOnClickListener { onItemClick(file) }
            holder.btnMenu.setOnClickListener { onItemClick(file) }
        }

        override fun getItemCount() = items.size
    }
}
