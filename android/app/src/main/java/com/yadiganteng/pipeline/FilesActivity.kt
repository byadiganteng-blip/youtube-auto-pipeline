package com.yadiganteng.pipeline

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
        .readTimeout(120, TimeUnit.SECONDS).build()

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_files)
        githubToken = intent.getStringExtra("token") ?: SecureConfig.getGithubToken()
        createChannel()

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
        btnSelectMode.setOnClickListener { toggleSel() }
        btnDownloadSelected.setOnClickListener { downloadSel() }
        btnDeleteSelected.setOnClickListener { confirmDelSel() }
        loadAll()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val c = NotificationChannel(CHANNEL_ID, "Download", NotificationManager.IMPORTANCE_DEFAULT)
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(c)
        }
    }

    private fun toggleSel() {
        selectionMode = !selectionMode
        if (!selectionMode) { artifacts.forEach { it.selected = false }; releases.forEach { it.selected = false } }
        updateSelUI()
        (recyclerArtifacts.adapter as? FileAdapter)?.notifyDataSetChanged()
        (recyclerReleases.adapter as? FileAdapter)?.notifyDataSetChanged()
    }

    private fun updateSelUI() {
        val cnt = (artifacts + releases).count { it.selected }
        btnSelectMode.text = if (selectionMode) "✕ Cancel" else "☑ Select"
        btnDownloadSelected.visibility = if (selectionMode) View.VISIBLE else View.GONE
        btnDeleteSelected.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectedInfo.visibility = if (selectionMode) View.VISIBLE else View.GONE
        tvSelectedInfo.text = "$cnt item dipilih"
    }

    private fun loadAll() {
        progressBar.visibility = View.VISIBLE
        loadArtifacts(); loadReleases()
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
                            artifacts.add(GitHubFile(o.getLong("id"), o.getString("name"),
                                o.optLong("size_in_bytes", 0), o.optString("archive_download_url", ""),
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
                            val rid = r.getLong("id"); val tag = r.getString("tag_name")
                            val as_ = r.optJSONArray("assets")
                            if (as_ != null && as_.length() > 0) {
                                for (j in 0 until as_.length()) {
                                    val a = as_.getJSONObject(j)
                                    releases.add(GitHubFile(a.getLong("id"), "$tag / ${a.getString("name")}",
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

    private fun downloadSel() {
        val sel = (artifacts + releases).filter { it.selected }
        if (sel.isEmpty()) { Toast.makeText(this, "Tidak ada yang dipilih", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Download ${sel.size} item?")
            .setMessage(sel.joinToString("\n") { "• ${it.name}" })
            .setPositiveButton("Download") { _, _ -> batchDl(sel, 0) }
            .setNegativeButton("Batal", null).show()
    }

    private fun batchDl(list: List<GitHubFile>, i: Int) {
        if (i >= list.size) { Toast.makeText(this, "✅ Semua selesai", Toast.LENGTH_LONG).show(); return }
        dlFile(list[i]) { batchDl(list, i + 1) }
    }

    private fun dlFile(f: GitHubFile, onDone: (() -> Unit)? = null) {
        if (f.type == "release" && f.browserUrl.isNotEmpty()) { dlPublic(f.browserUrl, f.name, onDone); return }
        if (f.downloadUrl.isEmpty()) { onDone?.invoke(); return }
        val pd = ProgressDialog(this).apply {
            setTitle("Download ${f.name}"); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100); setCancelable(false); show()
        }
        val req = Request.Builder().url(f.downloadUrl)
            .header("Authorization", "token $githubToken")
            .header("Accept", "application/vnd.github.v3+json").build()
        client.newCall(req).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { runOnUiThread { pd.dismiss(); onDone?.invoke() } }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val b = response.body ?: run { pd.dismiss(); onDone?.invoke(); return }
                    val len = b.contentLength()
                    var n = f.name.replace("/", "_").replace(" ", "_")
                    if (!n.endsWith(".zip") && !n.endsWith(".apk") && !n.endsWith(".mp4")) n += ".zip"
                    val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!d.exists()) d.mkdirs()
                    val out = File(d, n); val ins = b.byteStream(); val outs = FileOutputStream(out)
                    val buf = ByteArray(8192); var r: Int; var tot = 0L
                    while (ins.read(buf).also { r = it } != -1) {
                        outs.write(buf, 0, r); tot += r
                        val p = if (len > 0) ((tot * 100) / len).toInt() else 0
                        runOnUiThread { pd.progress = p }
                    }
                    outs.close(); ins.close()
                    runOnUiThread {
                        pd.dismiss(); Toast.makeText(this@FilesActivity, "✅ $n", Toast.LENGTH_SHORT).show()
                        showNotif(n, out); onDone?.invoke()
                    }
                } catch (e: Exception) { runOnUiThread { pd.dismiss(); onDone?.invoke() } }
            }
        })
    }

    private fun dlPublic(url: String, name: String, onDone: (() -> Unit)? = null) {
        val pd = ProgressDialog(this).apply {
            setTitle("Download $name"); setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
            setMax(100); setCancelable(false); show()
        }
        client.newCall(Request.Builder().url(url).build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: java.io.IOException) { runOnUiThread { pd.dismiss(); onDone?.invoke() } }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val b = response.body ?: run { pd.dismiss(); onDone?.invoke(); return }
                    val len = b.contentLength()
                    val n = name.replace("/", "_").replace(" ", "_")
                    val d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!d.exists()) d.mkdirs()
                    val out = File(d, n); val ins = b.byteStream(); val outs = FileOutputStream(out)
                    val buf = ByteArray(8192); var r: Int; var tot = 0L
                    while (ins.read(buf).also { r = it } != -1) {
                        outs.write(buf, 0, r); tot += r
                        val p = if (len > 0) ((tot * 100) / len).toInt() else 0
                        runOnUiThread { pd.progress = p }
                    }
                    outs.close(); ins.close()
                    runOnUiThread { pd.dismiss(); showNotif(n, out); onDone?.invoke() }
                } catch (e: Exception) { runOnUiThread { pd.dismiss(); onDone?.invoke() } }
            }
        })
    }

    private fun showNotif(name: String, f: File) {
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download selesai").setContentText(name)
            .setAutoCancel(true).build()
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(System.currentTimeMillis().toInt(), n)
    }

    private fun confirmDel(f: GitHubFile) {
        AlertDialog.Builder(this).setTitle("Hapus?")
            .setMessage("Yakin hapus ${f.name}?")
            .setPositiveButton("Hapus") { _, _ -> delFile(f) }
            .setNegativeButton("Batal", null).show()
    }

    private fun confirmDelSel() {
        val sel = (artifacts + releases).filter { it.selected }
        if (sel.isEmpty()) { Toast.makeText(this, "Tidak ada", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this).setTitle("Hapus ${sel.size} item?")
            .setMessage("⚠️ Tidak bisa dibatalkan")
            .setPositiveButton("Hapus Semua") { _, _ -> batchDel(sel, 0) }
            .setNegativeButton("Batal", null).show()
    }

    private fun batchDel(list: List<GitHubFile>, i: Int) {
        if (i >= list.size) { Toast.makeText(this, "✅ Selesai", Toast.LENGTH_LONG).show(); loadAll(); return }
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
        val browserUrl: String = "", var selected: Boolean = false
    )

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
                holder.cb.visibility = View.VISIBLE; holder.cb.isChecked = f.selected
                holder.cb.setOnCheckedChangeListener { _, checked -> f.selected = checked; updateSelUI() }
            } else holder.cb.visibility = View.GONE
            holder.itemView.setOnClickListener {
                if (selectionMode) { f.selected = !f.selected; holder.cb.isChecked = f.selected; updateSelUI() }
                else onAction(f)
            }
            holder.btnMenu.setOnClickListener { if (!selectionMode) onAction(f) }
        }
        override fun getItemCount() = items.size
    }
}
