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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Actions - Workflow runs list
 * Created by KARYADI, Coding by KARYADI
 */
class ActionsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_actions)
        } catch (e: Exception) {
            finish(); return
        }

        val recycler = findViewById<RecyclerView>(R.id.recyclerRuns)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)
        val progressBar = findViewById<ProgressBar>(R.id.progressBar)

        recycler.layoutManager = LinearLayoutManager(this)

        fun load() {
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch {
                val (ok, runs) = AdminApi.listRuns()
                progressBar.visibility = View.GONE
                if (ok) {
                    recycler.adapter = RunAdapter(runs) { run ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)))
                    }
                } else {
                    Toast.makeText(this@ActionsActivity, "Gagal load", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnRefresh.setOnClickListener { load() }
        load()
    }

    inner class RunAdapter(
        val items: List<AdminApi.WorkflowRun>,
        val onClick: (AdminApi.WorkflowRun) -> Unit
    ) : RecyclerView.Adapter<RunAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val card: CardView = v.findViewById(R.id.cardRun)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvStatus: TextView = v.findViewById(R.id.tvStatus)
            val tvTime: TextView = v.findViewById(R.id.tvTime)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_run, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val run = items[position]
            holder.tvName.text = "#${run.runNumber} ${run.name}"
            holder.tvStatus.text = if (run.status == "completed") run.conclusion.uppercase() else run.status.uppercase()
            try {
                val inp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                inp.timeZone = TimeZone.getTimeZone("UTC")
                val d = inp.parse(run.createdAt)
                holder.tvTime.text = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(d)
            } catch (_: Exception) {
                holder.tvTime.text = run.createdAt
            }
            holder.card.setOnClickListener { onClick(run) }
        }

        override fun getItemCount() = items.size
    }
}
