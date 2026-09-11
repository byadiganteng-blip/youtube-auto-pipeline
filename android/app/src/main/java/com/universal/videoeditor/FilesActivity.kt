package com.universal.videoeditor

import android.app.*
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
import androidx.core.app.NotificationManagerCompat
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
        const val CHANNEL_DOWNLOAD = "yad_download"
        const val CHANNEL_PROGRESS = "yad_progress"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private lateinit var recyclerArtifacts: RecyclerView
    private lateinit var recyclerReleases: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var btnSelectMode: Button
    private lateinit var btnDownloadSelected: Button
    private lateinit var btnDeleteSelected: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvSelectedInfo: TextView

    private var githubToken: String = ""
    private var selectionMode = false
    private val artifacts = mutableListOf<GitHubFile>()
    private val releases = mutableListOf<GitHubFile>()
    private var notifIdCounter = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)

        githubToken = intent.getStringExtra("token") ?: SecureConfig.getGithubToken()
        createChannels()

        recyclerArtifacts = findViewById(R.id.recyclerArtifacts)
        recyclerReleases = findViewById(R.id.recyclerReleases)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSelectMode = findViewById(R.id.btnSelectMode)
        btnDownloadSelected = findViewById(R.id.btnDownloadSelected)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)
        progressBar = findViewById(R.id.progressBar)
        tvSelectedInfo = findViewById(R.id.tvSelectedInfo)

        recyclerArtifacts.layoutManager = LinearLayoutManager(this)
        recyclerReleases.layoutManager = LinearLayoutManager(this)

        btnRefresh.setOnClickListener { loadAll() }
        btnSelectMode.setOnClickListener { toggleSelectionMode() }
        btnDownloadSelected.setOnClickListener { downloadSelected() }
        btnDeleteSelected.setOnClickListener { confirmDeleteSelected() }

        loadAll()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_DOWNLOAD, "Download Complete", NotificationManager.IMPORTANCE_DEFAULT))
                nm.createNotificationChannel(NotificationChannel(
                    CHANNEL_PROGRESS, "Download Progress", NotificationManager.IMPORTANCE_LOW))
            } catch (e: Exception) {}
        }
    }

    private fun toggleSelectionMode() {
        selectionMode = !selectionMode
        if (!selectionMode) {
            artifacts.forEach { it.selected = false }
            releases.forEach { it.selected = false }
        }
        updateSelectionUI()
        (recyclerArtifacts.adapter as? FileAdapter)?.notifyDataSetChanged()
        (recyclerReleases.adapter as? FileAdapter)?.notifyDataSetChanged()
    }

    private fun updateSelectionUI() {
        val cnt = (artifacts + releases).count { it.selected }
        btnSelectMode.text = if (selectionMode) "✕ Cancel" else "☑ Select"
        btnDownloadSelected.visibility = if (selectionMode) View.VISIBLE else View.GONE
        btnDeleteSelected.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectedInfo.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectedInfo.text = "$cnt item dipilih"
    }

    private fun loadAll() {
        progressBar.visibility = View.VISIBLE
        loadArtifacts()
        loadReleases()
    }

    private fun loadArtifacts() {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/actions/artifacts?per_page=30")
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                runOnUiThread { progressBar.visibility = View.GONE }
            }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful) try {
                        artifacts.clear()
                        val arr = JSONObject(response.body?.string() ?: "{}").optJSONArray("artifacts")
                        if (arr != null) for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            artifacts.add(GitHubFile(
                                o.getLong("id"), o.getString("name"),
                                o.optLong("size_in_bytes", 0),
                                o.optString("archive_download_url", ""),
                                "artifact", o.optString("created_at", "")))
                        }
                        recyclerArtifacts.adapter = FileAdapter(artifacts)
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun loadReleases() {
        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=30")
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {}
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) try {
                        releases.clear()
                        val arr = JSONArray(response.body?.string() ?: "[]")
                        for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            val rid = r.getLong("id")
                            val tag = r.getString("tag_name")
                            val assets = r.optJSONArray("assets")
                            if (assets != null && assets.length() > 0) {
                                for (j in 0 until assets.length()) {
                                    val a = assets.getJSONObject(j)
                                    releases.add(GitHubFile(
                                        a.getLong("id"), "$tag / ${a.getString("name")}",
                                        a.optLong("size", 0), a.optString("url", ""),
                                        "release", r.optString("published_at", ""),
                                        rid, tag, a.optString("browser_download_url", "")))
                                }
                            } else {
                                releases.add(GitHubFile(rid, tag, 0, "", "release_only",
                                    r.optString("published_at", ""), rid, tag))
                            }
                        }
                        recyclerReleases.adapter = FileAdapter(releases)
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun onAction(f: GitHubFile) {
        val opts = mutableListOf<String>()
        if (f.downloadUrl.isNotEmpty() || f.browserUrl.isNotEmpty()) opts.add("⬇️ Download")
        opts.add("🗑️ Delete")

        AlertDialog.Builder(this).setTitle(f.name).setItems(opts.toTypedArray()) { _, w ->
            when (opts[w]) {
                "⬇️ Download" -> dlFile(f)
                "🗑️ Delete" -> confirmDel(f)
            }
        }.show()
    }

    private fun downloadSelected() {
        val sel = (artifacts + releases).filter { it.selected }
        if (sel.isEmpty()) {
            Toast.makeText(this, "Tidak ada yang dipilih", Toast.LENGTH_SHORT).show(); return
        }
        AlertDialog.Builder(this).setTitle("Download ${sel.size} item?")
            .setMessage("Download berjalan di background.")
            .setPositiveButton("Download") { _, _ -> batchDl(sel, 0) }
            .setNegativeButton("Batal", null).show()
    }

    private fun batchDl(list: List<GitHubFile>, i: Int) {
        if (i >= list.size) {
            Toast.makeText(this, "✅ Semua selesai", Toast.LENGTH_LONG).show(); return
        }
        dlFile(list[i]) { batchDl(list, i + 1) }
    }

    private fun buildProgressNotif(title: String, text: String, progress: Int, indet: Boolean): Notification {
        val b = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW).setOngoing(true)
        if (indet) b.setProgress(0, 0, true) else b.setProgress(100, progress, false)
        return b.build()
    }

    private fun showNotifSafely(id: Int, n: Notification) {
        try { NotificationManagerCompat.from(this).notify(id, n) } catch (e: Exception) {}
    }

    private fun cancelNotif(id: Int) {
        try { NotificationManagerCompat.from(this).cancel(id) } catch (e: Exception) {}
    }

    private fun dlFile(f: GitHubFile, onDone: (() -> Unit)? = null) {
        if (f.type == "release" && f.browserUrl.isNotEmpty()) { dlPublic(f.browserUrl, f.name, onDone); return }
        if (f.downloadUrl.isEmpty()) { onDone?.invoke(); return }

        val notifId = notifIdCounter++
        showNotifSafely(notifId, buildProgressNotif("Downloading...", f.name, 0, true))

        val req = Request.Builder().url(f.downloadUrl)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                cancelNotif(notifId); runOnUiThread { onDone?.invoke() }
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body ?: run { cancelNotif(notifId); onDone?.invoke(); return }
                    val len = body.contentLength()
                    var name = f.name.replace("/", "_").replace(" ", "_")
                    if (!name.endsWith(".zip") && !name.endsWith(".apk") && !name.endsWith(".mp4")) name += ".zip"

                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val out = File(dir, name)
                    val ins = body.byteStream(); val outs = FileOutputStream(out)
                    val buf = ByteArray(16384); var r: Int; var total = 0L; var lastNotif = 0L

                    while (ins.read(buf).also { r = it } != -1) {
                        outs.write(buf, 0, r); total += r
                        val now = System.currentTimeMillis()
                        if (now - lastNotif > 500) {
                            lastNotif = now
                            val prog = if (len > 0) ((total * 100) / len).toInt() else 0
                            val mb = total / (1024.0 * 1024.0)
                            showNotifSafely(notifId, buildProgressNotif("Downloading...",
                                "$name • ${String.format("%.1f", mb)} MB", prog, false))
                        }
                    }
                    outs.close(); ins.close()
                    cancelNotif(notifId)
                    runOnUiThread {
                        Toast.makeText(this@FilesActivity, "✅ $name", Toast.LENGTH_SHORT).show()
                        showCompleteNotif(name, out)
                        onDone?.invoke()
                    }
                } catch (e: Exception) { cancelNotif(notifId); runOnUiThread { onDone?.invoke() } }
            }
        })
    }

    private fun dlPublic(url: String, name: String, onDone: (() -> Unit)? = null) {
        val notifId = notifIdCounter++
        showNotifSafely(notifId, buildProgressNotif("Downloading...", name, 0, true))
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) {
                cancelNotif(notifId); runOnUiThread { onDone?.invoke() }
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body ?: run { cancelNotif(notifId); onDone?.invoke(); return }
                    val len = body.contentLength()
                    val nm = name.replace("/", "_").replace(" ", "_")
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!dir.exists()) dir.mkdirs()
                    val out = File(dir, nm)
                    val ins = body.byteStream(); val outs = FileOutputStream(out)
                    val buf = ByteArray(16384); var r: Int; var total = 0L; var lastNotif = 0L

                    while (ins.read(buf).also { r = it } != -1) {
                        outs.write(buf, 0, r); total += r
                        val now = System.currentTimeMillis()
                        if (now - lastNotif > 500) {
                            lastNotif = now
                            val prog = if (len > 0) ((total * 100) / len).toInt() else 0
                            val mb = total / (1024.0 * 1024.0)
                            showNotifSafely(notifId, buildProgressNotif("Downloading...",
                                "$nm • ${String.format("%.1f", mb)} MB", prog, false))
                        }
                    }
                    outs.close(); ins.close()
                    cancelNotif(notifId)
                    runOnUiThread {
                        Toast.makeText(this@FilesActivity, "✅ $nm", Toast.LENGTH_SHORT).show()
                        showCompleteNotif(nm, out)
                        onDone?.invoke()
                    }
                } catch (e: Exception) { cancelNotif(notifId); runOnUiThread { onDone?.invoke() } }
            }
        })
    }

    private fun showCompleteNotif(name: String, file: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "com.universal.videoeditor.fileprovider", file)
            val pi = PendingIntent.getActivity(this, 0,
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

            val n = NotificationCompat.Builder(this, CHANNEL_DOWNLOAD)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download selesai").setContentText(name)
                .setContentIntent(pi).setAutoCancel(true).build()
            showNotifSafely(notifIdCounter++, n)
        } catch (e: Exception) {}
    }

    private fun confirmDel(f: GitHubFile) {
        AlertDialog.Builder(this).setTitle("Hapus?")
            .setMessage("Yakin hapus ${f.name}?")
            .setPositiveButton("Hapus") { _, _ -> delFile(f) }
            .setNegativeButton("Batal", null).show()
    }

    private fun confirmDeleteSelected() {
        val sel = (artifacts + releases).filter { it.selected }
        if (sel.isEmpty()) { Toast.makeText(this, "Tidak ada", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Hapus ${sel.size} item?")
            .setMessage("⚠️ Tidak bisa dibatalkan")
            .setPositiveButton("Hapus Semua") { _, _ -> batchDel(sel, 0) }
            .setNegativeButton("Batal", null).show()
    }

    private fun batchDel(list: List<GitHubFile>, i: Int) {
        if (i >= list.size) {
            Toast.makeText(this, "✅ Selesai", Toast.LENGTH_LONG).show()
            loadAll(); return
        }
        delFile(list[i]) { batchDel(list, i + 1) }
    }

    private fun delFile(f: GitHubFile, onDone: (() -> Unit)? = null) {
        val url = when (f.type) {
            "artifact" -> "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/${f.id}"
            "release" -> "https://api.github.com/repos/$OWNER/$REPO/releases/assets/${f.id}"
            "release_only" -> "https://api.github.com/repos/$OWNER/$REPO/releases/${f.id}"
            else -> return
        }
        val req = Request.Builder().url(url)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").delete().build()

        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { runOnUiThread { onDone?.invoke() } }
            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.code in listOf(204, 200, 202))
                        Toast.makeText(this@FilesActivity, "✅ ${f.name}", Toast.LENGTH_SHORT).show()
                    onDone?.invoke()
                }
            }
        })
    }

    data class GitHubFile(
        val id: Long, val name: String, val size: Long,
        val downloadUrl: String, val type: String, val createdAt: String,
        val releaseId: Long = 0, val releaseTag: String = "",
        val browserUrl: String = "", var selected: Boolean = false)

    inner class FileAdapter(private val items: List<GitHubFile>) :
        RecyclerView.Adapter<FileAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val cb: CheckBox = v.findViewById(R.id.cbSelect)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvInfo: TextView = v.findViewById(R.id.tvInfo)
            val btnMenu: ImageButton = v.findViewById(R.id.btnMenu)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_file, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val f = items[position]
            holder.tvName.text = f.name
            holder.tvInfo.text = "${String.format("%.2f", f.size / (1024.0 * 1024.0))} MB • ${f.createdAt}"
            if (selectionMode) {
                holder.cb.visibility = View.VISIBLE
                holder.cb.isChecked = f.selected
                holder.cb.setOnCheckedChangeListener { _, checked -> f.selected = checked; updateSelectionUI() }
            } else holder.cb.visibility = View.GONE

            holder.itemView.setOnClickListener {
                if (selectionMode) {
                    f.selected = !f.selected
                    holder.cb.isChecked = f.selected
                    updateSelectionUI()
                } else onAction(f)
            }
            holder.btnMenu.setOnClickListener { if (!selectionMode) onAction(f) }
        }
        override fun getItemCount() = items.size
    }
}
