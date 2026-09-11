package com.yadiganteng.pipeline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class FilesActivity : AppCompatActivity() {

    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
        const val CHANNEL_ID = "yad_download"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private lateinit var recyclerArtifacts: RecyclerView
    private lateinit var recyclerReleases: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar

    private var githubToken: String = ""
    private val artifacts = mutableListOf<GitHubFile>()
    private val releases = mutableListOf<GitHubFile>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        githubToken = intent.getStringExtra("token") ?: ""
        createNotificationChannel()

        recyclerArtifacts = findViewById(R.id.recyclerArtifacts)
        recyclerReleases = findViewById(R.id.recyclerReleases)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)

        recyclerArtifacts.layoutManager = LinearLayoutManager(this)
        recyclerReleases.layoutManager = LinearLayoutManager(this)

        btnRefresh.setOnClickListener { loadAll() }
        loadAll()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Download", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun loadAll() {
        progressBar.visibility = View.VISIBLE
        loadArtifacts()
        loadReleases()
    }

    private fun loadArtifacts() {
        val url = "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts?per_page=20"
        val request = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { progressBar.visibility = View.GONE }
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
                            recyclerArtifacts.adapter = FileAdapter(artifacts) { onFileAction(it) }
                        } catch (e: Exception) {
                            Toast.makeText(this@FilesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

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
                                if (assets != null && assets.length() > 0) {
                                    for (j in 0 until assets.length()) {
                                        val asset = assets.getJSONObject(j)
                                        releases.add(GitHubFile(
                                            id = asset.getLong("id"),
                                            name = "${tagName} / ${asset.getString("name")}",
                                            size = asset.optLong("size", 0),
                                            downloadUrl = asset.optString("url", ""),
                                            type = "release",
                                            createdAt = rel.optString("published_at", ""),
                                            releaseId = relId,
                                            releaseTag = tagName,
                                            browserUrl = asset.optString("browser_download_url", "")
                                        ))
                                    }
                                } else {
                                    releases.add(GitHubFile(
                                        id = relId, name = tagName, size = 0,
                                        downloadUrl = "", type = "release_only",
                                        createdAt = rel.optString("published_at", ""),
                                        releaseId = relId, releaseTag = tagName
                                    ))
                                }
                            }
                            recyclerReleases.adapter = FileAdapter(releases) { onFileAction(it) }
                        } catch (e: Exception) {
                            Toast.makeText(this@FilesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        })
    }

    private fun onFileAction(file: GitHubFile) {
        val options = mutableListOf<String>()
        if (file.downloadUrl.isNotEmpty() || file.browserUrl.isNotEmpty()) options.add("⬇️ Download")
        options.add("🗑️ Delete")

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    "⬇️ Download" -> downloadFile(file)
                    "🗑️ Delete" -> confirmDelete(file)
                }
            }.show()
    }

    private fun downloadFile(file: GitHubFile) {
        // Untuk release asset, pakai browser URL (public)
        if (file.type == "release" && file.browserUrl.isNotEmpty()) {
            downloadPublic(file.browserUrl, file.name)
            return
        }

        // Untuk artifact, butuh auth header
        if (file.downloadUrl.isEmpty()) {
            Toast.makeText(this, "Tidak ada URL download", Toast.LENGTH_LONG).show()
            return
        }

        val progressDialog = ProgressDialog(this).apply {
            setTitle("Download")
            setMessage("Mengunduh ${file.name}...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100)
            setCancelable(false)
            show()
        }

        val request = Request.Builder()
            .url(file.downloadUrl)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread {
                    progressDialog.dismiss()
                    Toast.makeText(this@FilesActivity, "Gagal: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@FilesActivity, "HTTP ${response.code}", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                try {
                    val body = response.body
                    if (body == null) {
                        runOnUiThread { progressDialog.dismiss() }
                        return
                    }

                    val contentLength = body.contentLength()
                    var fileName = file.name.replace("/", "_").replace(" ", "_")
                    if (!fileName.endsWith(".zip") && !fileName.endsWith(".apk") && !fileName.endsWith(".mp4")) {
                        fileName += ".zip"
                    }

                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val outputFile = File(dir, fileName)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(outputFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt() else 0
                        runOnUiThread { progressDialog.progress = progress }
                    }

                    outputStream.close()
                    inputStream.close()

                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@FilesActivity, "✅ Tersimpan: Downloads/$fileName", Toast.LENGTH_LONG).show()
                        showNotification(fileName, outputFile)
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@FilesActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun downloadPublic(url: String, name: String) {
        val progressDialog = ProgressDialog(this).apply {
            setTitle("Download")
            setMessage("Mengunduh...")
            setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100)
            setCancelable(false)
            show()
        }

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { progressDialog.dismiss() }
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body ?: return
                    val contentLength = body.contentLength()
                    var fileName = name.replace("/", "_").replace(" ", "_")
                    if (fileName.endsWith(".mp4")) fileName = fileName.substringBefore(".mp4") + ".mp4"

                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val outputFile = File(dir, fileName)

                    val inputStream = body.byteStream()
                    val outputStream = FileOutputStream(outputFile)
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        val progress = if (contentLength > 0) ((totalRead * 100) / contentLength).toInt() else 0
                        runOnUiThread { progressDialog.progress = progress }
                    }
                    outputStream.close()
                    inputStream.close()

                    runOnUiThread {
                        progressDialog.dismiss()
                        Toast.makeText(this@FilesActivity, "✅ Tersimpan: Downloads/$fileName", Toast.LENGTH_LONG).show()
                        showNotification(fileName, outputFile)
                    }
                } catch (e: Exception) {
                    runOnUiThread { progressDialog.dismiss() }
                }
            }
        })
    }

    private fun showNotification(fileName: String, file: File) {
        val intent = Intent(Intent.ACTION_VIEW)
        val uri = androidx.core.content.FileProvider.getUriForFile(
            this, "com.yadiganteng.pipeline.fileprovider", file)
        intent.setDataAndType(uri, "*/*")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        val pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download selesai")
            .setContentText(fileName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1, notification)
    }

    private fun confirmDelete(file: GitHubFile) {
        AlertDialog.Builder(this).setTitle("Hapus?")
            .setMessage("Yakin hapus:\n${file.name}")
            .setPositiveButton("Hapus") { _, _ -> deleteFile(file) }
            .setNegativeButton("Batal", null).show()
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
                        Toast.makeText(this@FilesActivity, "✅ Dihapus", Toast.LENGTH_LONG).show()
                        loadAll()
                    } else {
                        Toast.makeText(this@FilesActivity, "Error: ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    data class GitHubFile(
        val id: Long, val name: String, val size: Long,
        val downloadUrl: String, val type: String, val createdAt: String,
        val releaseId: Long = 0, val releaseTag: String = "",
        val browserUrl: String = ""
    )

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
