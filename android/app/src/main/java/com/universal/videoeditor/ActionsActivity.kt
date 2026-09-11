package com.universal.videoeditor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.*
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ActionsActivity : AppCompatActivity() {
    companion object {
        const val OWNER = "byadiganteng-blip"
        const val REPO = "youtube-auto-pipeline"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).build()

    private lateinit var recycler: RecyclerView
    private lateinit var btnRefresh: Button
    private lateinit var progressBar: ProgressBar
    private var githubToken: String = ""
    private val runs = mutableListOf<WorkflowRun>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_actions)
        githubToken = intent.getStringExtra("token") ?: SecureConfig.getGithubToken()
        recycler = findViewById(R.id.recyclerRuns)
        btnRefresh = findViewById(R.id.btnRefresh)
        progressBar = findViewById(R.id.progressBar)
        recycler.layoutManager = LinearLayoutManager(this)
        btnRefresh.setOnClickListener { loadRuns() }
        loadRuns()
    }

    private fun loadRuns() {
        progressBar.visibility = View.VISIBLE
        val req = Request.Builder()
            .url("https://api.github.com/repos/$OWNER/$REPO/actions/runs?per_page=30")
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
                        runs.clear()
                        val arr = JSONObject(response.body?.string() ?: "{}").optJSONArray("workflow_runs")
                        if (arr != null) for (i in 0 until arr.length()) {
                            val r = arr.getJSONObject(i)
                            runs.add(WorkflowRun(r.getLong("id"), r.optString("name"),
                                r.optString("status"), r.optString("conclusion"),
                                r.optString("html_url"), r.optInt("run_number"),
                                r.optString("created_at")))
                        }
                        recycler.adapter = RunAdapter(runs) { run ->
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)))
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    data class WorkflowRun(val id: Long, val name: String, val status: String,
        val conclusion: String, val htmlUrl: String, val runNumber: Int, val createdAt: String)

    inner class RunAdapter(val items: List<WorkflowRun>, val onClick: (WorkflowRun) -> Unit) :
        RecyclerView.Adapter<RunAdapter.VH>() {
        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v.findViewById(R.id.cardRun)
            val tvIcon: TextView = v.findViewById(R.id.tvIcon)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_run, parent, false))
        override fun onBindViewHolder(holder: VH, position: Int) {
            val run = items[position]
            holder.tvName.text = "#${run.runNumber} ${run.name}"
            val (icon, color) = when {
                run.status == "in_progress" -> "⏳" to 0xFFFF9800.toInt()
                run.conclusion == "success" -> "✅" to 0xFF4CAF50.toInt()
                run.conclusion == "failure" -> "❌" to 0xFFF44336.toInt()
                run.conclusion == "cancelled" -> "🚫" to 0xFF757575.toInt()
                else -> "⏸" to 0xFF9E9E9E.toInt()
            }
            holder.tvIcon.text = icon
            holder.tvStatus.text = if (run.status == "completed") run.conclusion.uppercase() else run.status.uppercase()
            holder.tvStatus.setTextColor(color)
            try {
                val inp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inp.timeZone = TimeZone.getTimeZone("UTC")
                val d = inp.parse(run.createdAt)
                holder.tvTime.text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(d)
            } catch (e: Exception) { holder.tvTime.text = run.createdAt }
            holder.card.setOnClickListener { onClick(run) }
        }
        override fun getItemCount() = items.size
    }
}
