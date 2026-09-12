package com.universal.videoeditor

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Actions Activity — Workflow runs list
 * Created by KARYADI, Coding by KARYADI
 */
class ActionsActivity : AppCompatActivity() {

    private lateinit var recycler: RecyclerView
    private lateinit var tvStatus: TextView
    private var tvEmpty: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            setContentView(R.layout.activity_actions)
        } catch (e: Exception) {
            Toast.makeText(this, "Layout error: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Safe findViewById — pakai resource id lookup, tidak akan crash kalau ID hilang
        recycler = safeFind(R.id.recyclerRuns, RecyclerView::class.java)
            ?: safeFind(R.id.recycler, RecyclerView::class.java)
            ?: return

        tvStatus = safeFind(R.id.tvStatus, TextView::class.java)
            ?: TextView(this).apply { text = "Ready" }

        try {
            recycler.layoutManager = LinearLayoutManager(this)
            loadRuns()
        } catch (e: Exception) {
            Toast.makeText(this, "Init error: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun <T : View> safeFind(id: Int, cls: Class<T>): T? {
        return try {
            findViewById<T>(id)
        } catch (_: Exception) {
            null
        }
    }

    private fun loadRuns() {
        // Placeholder — nanti diisi dengan AdminApi.listRuns()
        tvStatus.text = "Ready"
        Toast.makeText(this, "Actions — Created by KARYADI", Toast.LENGTH_SHORT).show()
    }
}
